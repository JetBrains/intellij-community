//! The `out/dev-data` link: the dev data of a checkout lives in a dev-data root outside the workspace.

use std::collections::BTreeSet;
use std::io::{self, Write};
use std::path::{Path, PathBuf};

use sha2::{Digest, Sha256};

use crate::process;

/// The workspace-relative path that every dev launcher, run configuration and tool names for the dev data of the IDEs
/// that it starts.
pub(crate) const DEV_DATA_LINK: &str = "out/dev-data";

/// The file in a dev-data root that names the real path of the checkout that owns the root.
pub(crate) const WORKSPACE_MARKER: &str = ".workspace";

/// Replaces the default parent of the dev-data roots, as `--output_user_root` does for Bazel.
pub(crate) const DEV_DATA_ROOT_VARIABLE: &str = "INTELLIJ_DEV_DATA_ROOT";

/// The dev-data root of the checkout at `workspace`, a real path, or `None` where the dev data stays in the workspace.
/// `os` is a value of `std::env::consts::OS`.
pub(crate) fn dev_data_root(workspace: &Path, getenv: &dyn Fn(&str) -> String, os: &str) -> Option<PathBuf> {
    if os == "windows" {
        return None;
    }
    let mut parent = PathBuf::from(getenv(DEV_DATA_ROOT_VARIABLE));
    if parent.as_os_str().is_empty() {
        let home = getenv("HOME");
        if os != "macos" || home.is_empty() {
            return None;
        }
        parent = [home.as_str(), "Library", "Caches", "JetBrains", "MonorepoDevData"]
            .iter()
            .collect();
    }
    let digest = Sha256::digest(component::paths::to_slash(&workspace.to_string_lossy()).as_bytes());
    let hash: String = digest[..4].iter().map(|byte| format!("{byte:02x}")).collect();
    let name = workspace.file_name().map(|name| name.to_string_lossy()).unwrap_or_default();
    Some(parent.join(format!("{name}-{hash}")))
}

/// Makes `out/dev-data` in `workspace` a symbolic link to the dev-data root of the checkout. It moves the dev data of
/// an older launch out of the workspace first. It never fails a launch. When it cannot make the link, it writes a
/// warning, and the IDE uses the directory in the workspace.
pub(crate) fn ensure_dev_data(workspace: &Path, getenv: &dyn Fn(&str) -> String, warnings: &mut dyn Write) {
    let Ok(real_workspace) = fscopy::resolve_links(workspace) else {
        return;
    };
    let Some(root) = dev_data_root(&real_workspace, getenv, std::env::consts::OS) else {
        return;
    };
    let link = workspace.join(DEV_DATA_LINK);
    // Two launches can race for the same link. The loser sees the entry appear or disappear and looks again.
    let mut result = Ok(());
    for _ in 0..3 {
        result = link_dev_data(&link, &real_workspace, &root, warnings);
        if !matches!(&result, Err(error) if matches!(error.kind(), io::ErrorKind::AlreadyExists | io::ErrorKind::DirectoryNotEmpty | io::ErrorKind::NotFound))
        {
            break;
        }
    }
    if let Err(error) = result {
        let _ = writeln!(warnings, "WARNING: the dev data stays in {}: {error}", link.display());
    }
}

fn link_dev_data(link: &Path, workspace: &Path, root: &Path, warnings: &mut dyn Write) -> io::Result<()> {
    let out = link.parent().expect("the link has a parent");
    if let Ok(out) = fscopy::resolve_links(out)
        && !out.starts_with(workspace)
    {
        // The user keeps `out` outside the workspace already, so the writes of the IDE do not reach the file watcher
        // of Bazel.
        return Ok(());
    }
    let metadata = match std::fs::symlink_metadata(link) {
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            return create_dev_data_link(link, workspace, root, warnings);
        }
        result => result.map_err(|error| context(&error, "lstat", link))?,
    };
    if metadata.file_type().is_symlink() {
        // The link decides, not the formula, so a moved checkout keeps its data.
        let target = std::fs::read_link(link).map_err(|error| context(&error, "readlink", link))?;
        let target = out.join(target);
        // A cache cleaner can remove the target. The IDE then starts with an empty config, as on a first launch.
        create_dir_all(&target)?;
        return write_workspace_marker(&target, workspace);
    }
    if !metadata.is_dir() {
        return Err(keep(format!("{} is not a directory", link.display())));
    }
    move_dev_data(link, workspace, root)
}

fn create_dev_data_link(link: &Path, workspace: &Path, root: &Path, warnings: &mut dyn Write) -> io::Result<()> {
    let created = matches!(std::fs::metadata(root), Err(error) if error.kind() == io::ErrorKind::NotFound);
    create_dir_all(root)?;
    write_workspace_marker(root, workspace)?;
    create_dir_all(link.parent().expect("the link has a parent"))?;
    fscopy::symlink(root, link, true)?;
    if created {
        report_orphan_roots(root.parent().expect("a root has a parent"), root, warnings);
    }
    Ok(())
}

/// Moves the directory `link` to `root` and replaces it with a link. A root that holds entries already gets the
/// entries that it lacks. The dev data stays when a dev IDE runs from it, when an entry exists on both sides, or when
/// the root is on another volume.
fn move_dev_data(link: &Path, workspace: &Path, root: &Path) -> io::Result<()> {
    if let Some(row) = live_dev_ide_row(link) {
        return Err(keep(format!(
            "a dev IDE runs from {}. Stop it to move the dev data out of the workspace",
            link.join(row).display()
        )));
    }
    if same_file::is_same_file(link, root).unwrap_or(false) {
        // A concurrent launch has moved the directory and made the link since this launch looked.
        return Ok(());
    }
    create_dir_all(root.parent().expect("a root has a parent"))?;
    let entries = match dev_data_entries(root) {
        Ok(entries) => entries,
        Err(error) if error.kind() == io::ErrorKind::NotFound => BTreeSet::new(),
        Err(error) => return Err(error),
    };
    if entries.is_empty() {
        // A root with only its marker is empty. A rename replaces an empty directory but not one with a file in it.
        let marker = root.join(WORKSPACE_MARKER);
        match std::fs::remove_file(&marker) {
            Err(error) if error.kind() != io::ErrorKind::NotFound => return Err(context(&error, "remove", &marker)),
            _ => {}
        }
        std::fs::rename(link, root).map_err(|error| rename_error(&error, link, root))?;
    } else {
        let legacy = dev_data_entries(link)?;
        let both: Vec<&str> = legacy.intersection(&entries).map(String::as_str).collect();
        if !both.is_empty() {
            return Err(keep(format!(
                "{} and {} both hold {}. Merge them by hand",
                link.display(),
                root.display(),
                both.join(", ")
            )));
        }
        for name in &legacy {
            match std::fs::rename(link.join(name), root.join(name)) {
                Err(error) if error.kind() != io::ErrorKind::NotFound => return Err(rename_error(&error, link, root)),
                _ => {}
            }
        }
        std::fs::remove_dir(link).map_err(|error| context(&error, "remove", link))?;
    }
    write_workspace_marker(root, workspace)?;
    fscopy::symlink(root, link, true)
}

/// The names of the entries in `directory` except [`WORKSPACE_MARKER`].
fn dev_data_entries(directory: &Path) -> io::Result<BTreeSet<String>> {
    let mut names = BTreeSet::new();
    for entry in std::fs::read_dir(directory).map_err(|error| context(&error, "open", directory))? {
        let name = entry
            .map_err(|error| context(&error, "read", directory))?
            .file_name()
            .to_string_lossy()
            .into_owned();
        if name != WORKSPACE_MARKER {
            names.insert(name);
        }
    }
    Ok(names)
}

fn rename_error(error: &io::Error, link: &Path, root: &Path) -> io::Error {
    if error.kind() == io::ErrorKind::CrossesDevices {
        let (link, root) = (link.display(), root.display());
        return keep(format!(
            "{root} is on another volume than {link}. Stop the dev IDEs, then run: mv {link} {root} && ln -s {root} {link}"
        ));
    }
    context(error, "rename", link)
}

/// The name of a row in `dev_data` whose IDE still runs. An IDE writes its process ID to `config/.lock`
/// (`DirectoryLock`), and a launcher links its home at `homes/<pid>`.
fn live_dev_ide_row(dev_data: &Path) -> Option<String> {
    let live = |pid: &str| pid.parse::<u32>().is_ok_and(|pid| pid != std::process::id() && process::runs(pid));
    for row in std::fs::read_dir(dev_data).ok()?.flatten() {
        if !row.file_type().is_ok_and(|file_type| file_type.is_dir()) {
            continue;
        }
        let row_directory = row.path();
        if let Ok(lock) = std::fs::read_to_string(row_directory.join("config").join(".lock"))
            && live(lock.trim())
        {
            return Some(row.file_name().to_string_lossy().into_owned());
        }
        let Ok(homes) = std::fs::read_dir(row_directory.join("homes")) else {
            continue;
        };
        if homes.flatten().any(|home| home.file_name().to_str().is_some_and(live)) {
            return Some(row.file_name().to_string_lossy().into_owned());
        }
    }
    None
}

fn write_workspace_marker(root: &Path, workspace: &Path) -> io::Result<()> {
    let marker = root.join(WORKSPACE_MARKER);
    let content = format!("{}\n", workspace.display());
    if std::fs::read_to_string(&marker).is_ok_and(|current| current == content) {
        return Ok(());
    }
    std::fs::write(&marker, content).map_err(|error| context(&error, "write", &marker))
}

/// Names each root in `parent` whose checkout no longer exists. It removes nothing: a moved checkout that has not
/// launched since the move still names its old path.
fn report_orphan_roots(parent: &Path, current: &Path, warnings: &mut dyn Write) {
    let Ok(entries) = std::fs::read_dir(parent) else {
        return;
    };
    for entry in entries.flatten() {
        let root = entry.path();
        if !entry.file_type().is_ok_and(|file_type| file_type.is_dir()) || root == current {
            continue;
        }
        let Ok(marker) = std::fs::read_to_string(root.join(WORKSPACE_MARKER)) else {
            continue;
        };
        let workspace = Path::new(marker.trim());
        // A checkout whose parent is missing can be on a volume that is not mounted.
        let parent_exists = workspace.parent().is_some_and(Path::exists);
        if workspace.as_os_str().is_empty() || workspace.exists() || !parent_exists {
            continue;
        }
        let _ = writeln!(
            warnings,
            "NOTE: the dev data {} belongs to the checkout {}, which no longer exists. To free the space, run: rm -rf {}",
            root.display(),
            workspace.display(),
            root.display()
        );
    }
}

/// A reason to keep the dev data in the workspace for this launch. It is a warning, not a failure.
fn keep(message: String) -> io::Error {
    io::Error::other(message)
}

/// Puts the operation and the path in front of the message and keeps the kind of the error.
fn context(error: &io::Error, operation: &str, path: &Path) -> io::Error {
    io::Error::new(error.kind(), format!("{operation} {}: {error}", path.display()))
}

fn create_dir_all(path: &Path) -> io::Result<()> {
    std::fs::create_dir_all(path).map_err(|error| context(&error, "mkdir", path))
}

#[cfg(test)]
#[cfg(unix)]
pub(crate) mod tests;
