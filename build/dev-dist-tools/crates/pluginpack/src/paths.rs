//! The host path checks of the executor. The slash-path rules are in `distpath`.

use std::fs;
use std::io;
use std::path::{Component, Path, PathBuf};

/// Converts a relative slash path into a host path below `root`.
pub(crate) fn host(root: &Path, relative: &str) -> PathBuf {
    let mut path = root.to_path_buf();
    for part in relative.split('/').filter(|part| !part.is_empty() && *part != ".") {
        path.push(part);
    }
    path
}

/// Go `filepath.Clean` of a host path: removes `.` components, a trailing separator, and each inner `..` with the
/// component before it. The lexical step comes before an `lstat`, so a trailing separator cannot follow a link.
pub(crate) fn clean_host(path: &Path) -> PathBuf {
    let mut components: Vec<Component<'_>> = Vec::new();
    for component in path.components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => match components.last() {
                Some(Component::Normal(_)) => {
                    components.pop();
                }
                Some(Component::RootDir | Component::Prefix(_)) => {}
                _ => components.push(component),
            },
            _ => components.push(component),
        }
    }
    if components.is_empty() {
        return PathBuf::from(".");
    }
    components.iter().collect()
}

/// Reports whether `file` is `root` or below it. Both paths are clean.
pub(crate) fn within(root: &Path, file: &Path) -> bool {
    file.strip_prefix(root).is_ok()
}

/// The identity of one file: the device and the inode on Unix, an open handle on Windows. It follows a link.
#[derive(Debug, PartialEq, Eq, Hash)]
pub(crate) struct FileId(#[cfg(unix)] (u64, u64), #[cfg(windows)] same_file::Handle);

/// Returns the [`FileId`] of the file that `path` names, as Go `os.Stat` and `os.SameFile` compare it.
pub(crate) fn file_id(path: &Path) -> io::Result<FileId> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt;
        let metadata = fs::metadata(path)?;
        Ok(FileId((metadata.dev(), metadata.ino())))
    }
    #[cfg(windows)]
    {
        same_file::Handle::from_path(path).map(FileId)
    }
}

/// Returns the [`FileId`] of an entry that a walk or an `lstat` found. On Unix it takes the numbers of the metadata.
#[cfg_attr(unix, expect(clippy::unnecessary_wraps, reason = "the Windows path can fail"))]
pub(crate) fn entry_id(path: &Path, metadata: &fs::Metadata) -> io::Result<FileId> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt;
        let _ = path;
        Ok(FileId((metadata.dev(), metadata.ino())))
    }
    #[cfg(windows)]
    {
        let _ = metadata;
        file_id(path)
    }
}

/// Creates one directory with mode 0755 before the umask, as Go `os.Mkdir(name, 0o755)` does.
pub(crate) fn create_directory(path: &Path) -> io::Result<()> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::DirBuilderExt;
        fs::DirBuilder::new().mode(0o755).create(path)
    }
    #[cfg(not(unix))]
    {
        fs::create_dir(path)
    }
}

/// Creates the link `link` with the text `target`. Windows gives a link the kind of its target, as Go `os.Symlink`
/// does, so the target must exist before the link.
pub(crate) fn create_symlink(target: &str, link: &Path) -> io::Result<()> {
    // Only Windows reads the kind, so only Windows probes the target.
    let target_is_directory =
        cfg!(windows) && fs::metadata(link.parent().unwrap_or(Path::new(".")).join(target)).is_ok_and(|metadata| metadata.is_dir());
    fscopy::symlink(Path::new(target), link, target_is_directory)
}

/// Reports whether the link target text is an absolute host path, as Go `filepath.IsAbs` does.
pub(crate) fn is_absolute_target(target: &str) -> bool {
    Path::new(target).is_absolute()
}

/// Keeps the text of a walk error. Its `Display` names the path and the I/O error, and its source is the same I/O error,
/// so `{:#}` of a plain conversion prints the I/O error twice.
pub(crate) fn walk_error(error: &walkdir::Error) -> anyhow::Error {
    anyhow::anyhow!("{error}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn host_paths_follow_the_go_rules() {
        assert_eq!(clean_host(Path::new("out/link/")), PathBuf::from("out/link"));
        assert_eq!(clean_host(Path::new("out/link/.")), PathBuf::from("out/link"));
        assert_eq!(clean_host(Path::new("/a/b/../c")), PathBuf::from("/a/c"));
    }
}
