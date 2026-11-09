//! The generation: `<root>/g/<key>/`, an immutable home of hard links into the store.
//!
//! The key is the fingerprint of the local launch and the home prefix. A generation that exists is complete, because
//! the tool builds it in a temporary directory and renames it into place after the marker `.complete`. Every start
//! touches `.used` and `.targets/<target>`, which the garbage collection reads.

use std::collections::{BTreeSet, HashSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::process;
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::{Context as _, Result, bail};
use component::layout::{LOCAL_LAYOUT_VERSION, LocalLayout};
use rayon::prelude::*;

use crate::lock::StoreLock;
use crate::spec::HomeSpec;
use crate::store::{BlobSource, Store, TEMPORARY_PREFIX};

/// The name of the generation directory in the root.
pub(crate) const GENERATIONS_DIR: &str = "g";
/// The marker of a complete generation.
pub(crate) const COMPLETE_FILE: &str = ".complete";
/// The file that each start touches.
pub(crate) const USED_FILE: &str = ".used";
/// The directory of the per-target markers that each start touches.
pub(crate) const TARGETS_DIR: &str = ".targets";
/// The prefix of a generation that the garbage collection deletes.
pub(crate) const TRASH_PREFIX: &str = ".trash-";

/// The runtime module repository of a home, and the property that names it.
const RUNTIME_MODULE_REPOSITORY_FILE: &str = "modules/module-descriptors.dat";
const RUNTIME_MODULE_REPOSITORY_PROPERTY: &str = "-Dintellij.platform.runtime.repository.path=";

/// The longest path that the native launcher and the IDE can open on Windows without the long-path prefix.
const WINDOWS_PATH_LIMIT: usize = 259;

/// A materialized home of a launch target.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct Materialized {
    pub(crate) home: PathBuf,
    pub(crate) launcher: PathBuf,
    pub(crate) vm_options_file: PathBuf,
    pub(crate) vm_options_variable: String,
    /// Whether this call built the generation. Then the store can hold garbage.
    pub(crate) built: bool,
}

/// Materializes the home of `spec` below `root`.
pub(crate) fn materialize(spec: &HomeSpec, root: &Path, store: &Store) -> Result<Materialized> {
    let key = generation_key(spec)?;
    let generations = root.join(GENERATIONS_DIR);
    fs::create_dir_all(&generations).with_context(|| format!("cannot create {}", generations.display()))?;
    let generation = generations.join(&key);
    let lock = StoreLock::shared(root)?;
    let built = if generation.join(COMPLETE_FILE).exists() {
        false
    } else {
        build(spec, store, &generation)?;
        true
    };
    mark_used(&generation, &spec.target)?;
    let home = home_of(&generation, &spec.home_prefix);
    let vm_options_file = write_vm_options(spec, &generation, &home)?;
    drop(lock);
    Ok(Materialized {
        launcher: home.join(&spec.launcher),
        home,
        vm_options_file,
        vm_options_variable: spec.vm_options_variable.clone(),
        built,
    })
}

/// The name of the generation: the fingerprint, with `-` for `:` because NTFS allows no `:`, and the home prefix.
pub(crate) fn generation_key(spec: &HomeSpec) -> Result<String> {
    let text = fs::read_to_string(&spec.fingerprint).with_context(|| format!("cannot read {}", spec.fingerprint.display()))?;
    let fingerprint = text.trim();
    if fingerprint.is_empty()
        || !fingerprint
            .bytes()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b':')
    {
        bail!("{} holds no fingerprint: {fingerprint:?}", spec.fingerprint.display());
    }
    let prefix = if spec.home_prefix.is_empty() {
        "flat"
    } else {
        spec.home_prefix.as_str()
    };
    Ok(format!("{}-{}", fingerprint.replace(':', "-"), prefix.replace('/', "-")))
}

fn home_of(generation: &Path, prefix: &str) -> PathBuf {
    if prefix.is_empty() {
        generation.to_path_buf()
    } else {
        generation.join(prefix)
    }
}

/// One file of the new generation.
struct FileJob<'a> {
    destination: PathBuf,
    source: PathBuf,
    hash: Option<i64>,
    mode: u32,
    path: &'a str,
}

fn build(spec: &HomeSpec, store: &Store, generation: &Path) -> Result<()> {
    let layout = read_layout(&spec.layout)?;
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |duration| duration.as_nanos());
    let name = generation.file_name().and_then(|name| name.to_str()).unwrap_or("generation");
    let staging = generation.with_file_name(format!("{name}{TEMPORARY_PREFIX}{}-{nanos}", process::id()));
    fs::create_dir(&staging).with_context(|| format!("cannot create {}", staging.display()))?;
    let result = fill(spec, store, &layout, generation, &staging);
    if let Err(error) = result {
        let _ = fs::remove_dir_all(&staging);
        return Err(error);
    }
    match fs::rename(&staging, generation) {
        Ok(()) => Ok(()),
        Err(_) if generation.join(COMPLETE_FILE).exists() => {
            // Another start published the same generation first.
            let _ = fs::remove_dir_all(&staging);
            Ok(())
        }
        Err(error) => {
            let _ = fs::remove_dir_all(&staging);
            Err(error).with_context(|| format!("cannot rename {} to {}", staging.display(), generation.display()))
        }
    }
}

fn fill(spec: &HomeSpec, store: &Store, layout: &LocalLayout, generation: &Path, staging: &Path) -> Result<()> {
    let home = home_of(staging, &spec.home_prefix);
    let final_home = home_of(generation, &spec.home_prefix);
    let mut directories = BTreeSet::new();
    directories.insert(home.clone());
    let mut listed = HashSet::new();
    let mut jobs = Vec::new();
    let mut symlinks = Vec::new();
    let mut modes = Vec::new();
    for file in &layout.files {
        distpath::validate_path(&file.path).with_context(|| format!("the layout {} names {:?}", spec.layout.display(), file.path))?;
        listed.insert(file.path.as_str());
        let destination = home.join(&file.path);
        check_path_length(&final_home.join(&file.path), cfg!(windows).then_some(WINDOWS_PATH_LIMIT))?;
        if file.is_directory() {
            directories.insert(destination.clone());
            if let Some(mode) = file.mode {
                modes.push((destination, mode));
            }
            continue;
        }
        add_parents(&mut directories, &home, &destination);
        if let Some(target) = &file.symlink_target {
            symlinks.push((destination, target.as_str()));
            continue;
        }
        if file.runfile.is_none() {
            bail!(
                "the layout {} names neither a runfile nor a link for {}",
                spec.layout.display(),
                file.path
            );
        }
        let Some(hash) = file.hash else {
            bail!(
                "the layout {} has no hash for {}. Build the target again",
                spec.layout.display(),
                file.path
            );
        };
        jobs.push(FileJob {
            source: spec.source(&file.path)?,
            destination,
            hash: Some(hash),
            mode: file.mode.unwrap_or_else(|| fscopy::conventional_mode(file.executable)),
            path: &file.path,
        });
    }
    // A file of the spec that the layout does not list is a metadata file, such as `fingerprint.txt`.
    for (path, source) in &spec.files {
        if listed.contains(path.as_str()) {
            continue;
        }
        let destination = home.join(path);
        check_path_length(&final_home.join(path), cfg!(windows).then_some(WINDOWS_PATH_LIMIT))?;
        add_parents(&mut directories, &home, &destination);
        jobs.push(FileJob {
            destination,
            source: source.clone(),
            hash: None,
            mode: fscopy::conventional_mode(false),
            path,
        });
    }
    for directory in &directories {
        fs::create_dir_all(directory).with_context(|| format!("cannot create {}", directory.display()))?;
    }
    jobs.par_iter().try_for_each(|job| {
        store
            .link(
                &BlobSource {
                    source: &job.source,
                    hash: job.hash,
                    mode: job.mode,
                },
                &job.destination,
            )
            .with_context(|| format!("cannot place {}", job.path))
    })?;
    for (link, target) in symlinks {
        let target_is_directory = link.parent().is_some_and(|parent| parent.join(target).is_dir());
        fscopy::symlink(Path::new(target), &link, target_is_directory).with_context(|| {
            if cfg!(windows) {
                format!(
                    "cannot create the link {}. Windows creates a link only with Developer Mode on",
                    link.display()
                )
            } else {
                format!("cannot create the link {}", link.display())
            }
        })?;
    }
    // The modes of the directories come last, so a mode without the write bit does not stop the links.
    for (directory, mode) in modes.iter().rev() {
        fscopy::set_mode(directory, *mode).with_context(|| format!("cannot set the mode of {}", directory.display()))?;
    }
    let complete = staging.join(COMPLETE_FILE);
    fs::write(&complete, "").with_context(|| format!("cannot write {}", complete.display()))
}

fn add_parents(directories: &mut BTreeSet<PathBuf>, home: &Path, destination: &Path) {
    let mut parent = destination.parent();
    while let Some(directory) = parent {
        if directory == home || !directories.insert(directory.to_path_buf()) {
            break;
        }
        parent = directory.parent();
    }
}

/// Refuses a path longer than `limit`, which is `Some` only on Windows.
pub(crate) fn check_path_length(path: &Path, limit: Option<usize>) -> Result<()> {
    if let Some(limit) = limit {
        let length = path.as_os_str().len();
        if length > limit {
            bail!(
                "the home path {} has {length} characters, more than {limit}. Set INTELLIJ_DEV_HOME_ROOT to a shorter directory",
                path.display()
            );
        }
    }
    Ok(())
}

fn read_layout(path: &Path) -> Result<LocalLayout> {
    let text = fs::read_to_string(path).with_context(|| format!("cannot read {}", path.display()))?;
    let layout: LocalLayout = serde_json::from_str(&text).with_context(|| format!("{} is not a local layout", path.display()))?;
    if layout.version != LOCAL_LAYOUT_VERSION {
        bail!("{} has the version {}, not {LOCAL_LAYOUT_VERSION}", path.display(), layout.version);
    }
    Ok(layout)
}

/// Touches `.used` and `.targets/<target>` of the generation.
fn mark_used(generation: &Path, target: &str) -> Result<()> {
    let targets = generation.join(TARGETS_DIR);
    fs::create_dir_all(&targets).with_context(|| format!("cannot create {}", targets.display()))?;
    for file in [generation.join(USED_FILE), targets.join(target_marker(target))] {
        fs::write(&file, "").with_context(|| format!("cannot write {}", file.display()))?;
    }
    Ok(())
}

/// The file name of the marker of `target`: the label with `_` for each character that a file name cannot hold.
pub(crate) fn target_marker(target: &str) -> String {
    target
        .chars()
        .map(|char| {
            if char.is_ascii_alphanumeric() || matches!(char, '.' | '-' | '_') {
                char
            } else {
                '_'
            }
        })
        .collect()
}

/// Writes the vmoptions file of the generation, `vmoptions-<hash of the text>`, once, and returns its path.
///
/// The text is the vmoptions file of the target, and with `runtimeModuleRepository` the property that names the
/// runtime module repository of the home. The name is the content, because two checkouts share one generation and
/// their flags name their own data directories.
fn write_vm_options(spec: &HomeSpec, generation: &Path, home: &Path) -> Result<PathBuf> {
    let mut text = fs::read_to_string(&spec.vm_options).with_context(|| format!("cannot read {}", spec.vm_options.display()))?;
    if spec.runtime_module_repository {
        let repository = home.join(RUNTIME_MODULE_REPOSITORY_FILE);
        if !repository.is_file() {
            bail!("the target asks for the runtime module repository, and the home has no {RUNTIME_MODULE_REPOSITORY_FILE}");
        }
        if !text.is_empty() && !text.ends_with('\n') {
            text.push('\n');
        }
        text.push_str(RUNTIME_MODULE_REPOSITORY_PROPERTY);
        text.push_str(&repository.display().to_string());
        text.push('\n');
    }
    let file = generation.join(format!("vmoptions-{:016x}", xxh3::hash_bytes(text.as_bytes())));
    if file.exists() {
        return Ok(file);
    }
    let temporary = generation.join(format!("{TEMPORARY_PREFIX}vmoptions-{}", process::id()));
    fs::write(&temporary, &text).with_context(|| format!("cannot write {}", temporary.display()))?;
    let result = match fs::hard_link(&temporary, &file) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == io::ErrorKind::AlreadyExists => Ok(()),
        Err(error) => Err(error).with_context(|| format!("cannot publish {}", file.display())),
    };
    let _ = fs::remove_file(&temporary);
    result.map(|()| file)
}

#[cfg(test)]
mod tests;
