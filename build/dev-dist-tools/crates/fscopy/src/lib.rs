//! The copy and path helpers of the dev-distribution tools.
//!
//! Every copy goes through [`clone_or_copy`]. An error names the paths and keeps the [`io::ErrorKind`] of the cause.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use filetime::FileTime;

#[cfg(test)]
mod tests;

/// Copies the regular file `source` to `destination`, which must not exist.
///
/// The function fails with [`io::ErrorKind::AlreadyExists`] when `destination` exists, also as a dangling link.
/// The check and the copy are two steps, so the caller must own the destination directory.
/// The function follows a link at `source`, and `destination` gets the permission bits of the source.
///
/// The copy is [`fs::copy`], which clones the file where the file system can:
///
/// - On macOS, `fs::copy` calls `fclonefileat` first. It uses `fcopyfile` only after `ENOTSUP`, `EEXIST` or
///   `EXDEV`, so a copy on APFS is a clone. The clone gives the destination the mode of the source without the
///   setuid and setgid bits. The clone and `fcopyfile` also copy the extended attributes and the ACL of the source.
/// - On Linux, `fs::copy` uses `copy_file_range`, which clones on Btrfs and XFS.
/// - On Windows, `fs::copy` uses `CopyFileExW`, which clones on ReFS and a Dev Drive.
///
/// The JDK `Files.copy` also clones on macOS. A byte copy in its place makes a full export of the distribution slow.
pub fn clone_or_copy(source: &Path, destination: &Path) -> io::Result<()> {
    match fs::symlink_metadata(destination) {
        Ok(_) => {
            return Err(io::Error::new(
                io::ErrorKind::AlreadyExists,
                format!("copy {} to {}: the destination exists", source.display(), destination.display()),
            ));
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(with_path(&error, destination)),
    }
    fs::copy(source, destination)
        .map(|_| ())
        .map_err(|error| with_paths(&error, "copy", source, destination))
}

/// Copies the regular file `source` to `destination` as Java `Files.copy(source, destination, COPY_ATTRIBUTES)` does.
///
/// [`clone_or_copy`] gives the copy the permission bits of the source, and on Windows the read-only attribute. Then the
/// function sets the modification time of the source. The function follows a link at `source`.
///
/// The function refuses a directory and every other file type that is not a regular file. It also refuses a mode with
/// a setuid, setgid or sticky bit. The composer copies only regular files, and no Bazel output has such a bit.
pub fn copy_with_attributes(source: &Path, destination: &Path) -> io::Result<()> {
    let metadata = fs::metadata(source).map_err(|error| with_path(&error, source))?;
    if !metadata.is_file() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            format!("not a regular file: {}", source.display()),
        ));
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;

        let mode = metadata.permissions().mode();
        if mode & 0o7000 != 0 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                format!(
                    "unsupported mode {:o} of {}: a setuid, setgid or sticky bit",
                    mode & 0o7777,
                    source.display()
                ),
            ));
        }
    }
    clone_or_copy(source, destination)?;
    set_modification_time(destination, FileTime::from_last_modification_time(&metadata)).map_err(|error| with_path(&error, destination))
}

/// Copies `source` to `destination` with [`clone_or_copy`], then sets the declared mode.
///
/// The mode is `mode & 0o777`, or [`conventional_mode`] of `executable` when `mode` is `None`.
/// On Windows the function sets only the read-only attribute: the file is read-only when the owner write bit is off.
pub fn copy_with_mode(source: &Path, destination: &Path, executable: bool, mode: Option<u32>) -> io::Result<()> {
    clone_or_copy(source, destination)?;
    let mode = mode.map_or(conventional_mode(executable), |mode| mode & 0o777);
    set_mode(destination, mode).map_err(|error| with_path(&error, destination))
}

/// Replaces the existing regular file `destination` with a copy of `source`.
///
/// The destination is usually an empty file that reserves the output path. The function removes it and calls
/// [`clone_or_copy`], so the copy can be a clone. The destination then has the permission bits of the source.
/// The function fails with [`io::ErrorKind::NotFound`] when `destination` does not exist, and with
/// [`io::ErrorKind::InvalidInput`] when `destination` is not a regular file.
pub fn replace_with_copy(source: &Path, destination: &Path) -> io::Result<()> {
    let metadata = fs::symlink_metadata(destination).map_err(|error| with_path(&error, destination))?;
    if !metadata.is_file() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            format!("not a regular file: {}", destination.display()),
        ));
    }
    fs::remove_file(destination).map_err(|error| with_path(&error, destination))?;
    clone_or_copy(source, destination)
}

/// Sets the declared permission bits of a distribution file or directory.
///
/// The mode is `mode & 0o777`, or [`conventional_mode`] of `executable` when `mode` is `None`.
/// Java cannot set POSIX permissions on Windows and ignores them there, so the function does nothing on Windows.
pub fn set_distribution_file_mode(path: &Path, executable: bool, mode: Option<u32>) -> io::Result<()> {
    if cfg!(windows) {
        return Ok(());
    }
    let mode = mode.map_or(conventional_mode(executable), |mode| mode & 0o777);
    set_mode(path, mode).map_err(|error| with_path(&error, path))
}

/// Returns 0o755 for an executable file and 0o644 for all other files.
pub const fn conventional_mode(executable: bool) -> u32 {
    if executable { 0o755 } else { 0o644 }
}

/// Creates the directory `path` and its missing parents. Each directory that the function creates gets the mode 0755.
///
/// The mode does not depend on the umask, because the inventory of a payload records the mode of each directory.
/// [`fs::create_dir_all`] gives 0777 without the umask bits, so the umask 002 gives 0775. The Go tools called
/// `os.MkdirAll(path, 0o755)`. It gives the result of this function when the umask has no bit outside 022.
///
/// A directory that exists keeps its mode. On Windows, the function only creates the directories.
pub fn create_dirs_0755(path: &Path) -> io::Result<()> {
    #[cfg(unix)]
    let missing = {
        let mut missing = Vec::new();
        for directory in path.ancestors() {
            if directory.as_os_str().is_empty() || fs::exists(directory).map_err(|error| with_path(&error, directory))? {
                break;
            }
            missing.push(directory);
        }
        missing
    };
    fs::create_dir_all(path).map_err(|error| with_path(&error, path))?;
    #[cfg(unix)]
    for directory in missing {
        set_mode(directory, 0o755).map_err(|error| with_path(&error, directory))?;
    }
    Ok(())
}

/// Creates the symbolic link `link` with the text `target`, as Go `os.Symlink` does.
///
/// A relative target starts at the directory of the link. The target does not have to exist.
/// On Unix a link has no kind, so the function ignores `target_is_directory`.
///
/// On Windows a link is a directory link or a file link, and the system follows it only as that kind. So the function
/// creates a directory link when `target_is_directory` is true, and a file link in the other case. Go `os.Symlink`
/// read the kind from the target on the disk. The function also replaces each `/` in the target with `\`, as Go does.
///
/// An error has the text of the Go `os.LinkError`: `symlink <target> <link>: <cause>`.
pub fn symlink(target: &Path, link: &Path, target_is_directory: bool) -> io::Result<()> {
    #[cfg(unix)]
    let created = {
        let _ = target_is_directory;
        std::os::unix::fs::symlink(target, link)
    };
    #[cfg(windows)]
    let created = {
        let native = with_backslashes(target);
        if target_is_directory {
            std::os::windows::fs::symlink_dir(&native, link)
        } else {
            std::os::windows::fs::symlink_file(&native, link)
        }
    };
    created.map_err(|error| io::Error::new(error.kind(), format!("symlink {} {}: {error}", target.display(), link.display())))
}

/// Returns the absolute form of `path`, as Go `filepath.Abs` does.
///
/// The function joins a relative path to the current directory. On Unix it then removes `.` and `..` lexically.
/// On Windows it uses [`std::path::absolute`], which calls `GetFullPathNameW`, as Go does.
pub fn absolute_path(path: &Path) -> io::Result<PathBuf> {
    if cfg!(windows) {
        return std::path::absolute(path).map_err(|error| with_path(&error, path));
    }
    let joined = if path.is_absolute() {
        path.to_path_buf()
    } else {
        std::env::current_dir()?.join(path)
    };
    Ok(clean(&joined))
}

/// Returns Java `Path.toRealPath()`: the [`absolute_path`] of `path` with every link and junction resolved.
///
/// Use it to follow the staging link of Bazel to the real file. The lexical step comes first, so `link/..` is the
/// directory that holds `link`. On Windows the result has no `\\?\` prefix.
pub fn real_path(path: &Path) -> io::Result<PathBuf> {
    resolve_links(&absolute_path(path)?)
}

/// Returns `path` with every link and junction resolved, as Go `filepath.EvalSymlinks` does.
///
/// The result is absolute. On Unix, a `..` after a link goes to the parent of the link target. On Windows, the system
/// removes a `..` lexically before it follows a link, so `link\..` is the directory that holds `link`.
///
/// On macOS, each name of the result has the case that the file system stores. The Go original kept the case of the
/// input.
///
/// On Windows, [`fs::canonicalize`] asks `GetFinalPathNameByHandleW` for the final path, which supports a long path
/// and follows a junction. The function then removes the `\\?\` prefix. It does not use `dunce::canonicalize`.
/// For a path longer than `MAX_PATH`, `dunce` keeps the prefix, and the Go original removes it from every path.
pub fn resolve_links(path: &Path) -> io::Result<PathBuf> {
    let resolved = fs::canonicalize(path).map_err(|error| with_path(&error, path))?;
    if cfg!(windows) {
        Ok(strip_extended_path_prefix(resolved))
    } else {
        Ok(resolved)
    }
}

/// Removes the extended-length prefix that `GetFinalPathNameByHandleW` adds.
///
/// `\\?\UNC\server\share` becomes `\\server\share`, and `\\?\C:\x` becomes `C:\x`. Every other path stays the same.
/// The result then compares equal to the paths that the tools build. Rust std adds the prefix again where a long path
/// needs it.
fn strip_extended_path_prefix(path: PathBuf) -> PathBuf {
    let Some(text) = path.to_str() else {
        return path;
    };
    if let Some(rest) = text.strip_prefix(r"\\?\UNC\") {
        return PathBuf::from(format!(r"\\{rest}"));
    }
    if let Some(rest) = text.strip_prefix(r"\\?\") {
        return PathBuf::from(rest);
    }
    path
}

/// Removes `.` and `..` lexically, as Go `filepath.Clean` does for an absolute Unix path.
///
/// `Path::components` already drops a `.` that is not the first component, and an absolute path has no first `.`.
/// A `..` at the root stays at the root, because `PathBuf::pop` does not remove the root.
fn clean(path: &Path) -> PathBuf {
    let mut result = PathBuf::new();
    for component in path.components() {
        if component == std::path::Component::ParentDir {
            result.pop();
        } else {
            result.push(component);
        }
    }
    result
}

/// Returns `path` with each `/` replaced by `\`. The UTF-16 round trip keeps every other unit.
#[cfg(windows)]
fn with_backslashes(path: &Path) -> PathBuf {
    use std::ffi::OsString;
    use std::os::windows::ffi::{OsStrExt, OsStringExt};

    const SLASH: u16 = b'/' as u16;
    const BACKSLASH: u16 = b'\\' as u16;
    let units: Vec<u16> = path
        .as_os_str()
        .encode_wide()
        .map(|unit| if unit == SLASH { BACKSLASH } else { unit })
        .collect();
    PathBuf::from(OsString::from_wide(&units))
}

#[cfg(not(windows))]
fn set_modification_time(path: &Path, time: FileTime) -> io::Result<()> {
    filetime::set_file_mtime(path, time)
}

/// Sets the modification time through a handle that has only the right to write the attributes, as Go `os.Chtimes`
/// does.
///
/// `CopyFileExW` gives the copy the read-only attribute of the source. A handle with write access cannot open a
/// read-only file, and `filetime` asks for write access.
#[cfg(windows)]
fn set_modification_time(path: &Path, time: FileTime) -> io::Result<()> {
    use std::os::windows::fs::OpenOptionsExt;

    const FILE_WRITE_ATTRIBUTES: u32 = 0x0100;
    let file = fs::OpenOptions::new().access_mode(FILE_WRITE_ATTRIBUTES).open(path)?;
    filetime::set_file_handle_times(&file, None, Some(time))
}

#[cfg(unix)]
fn set_mode(path: &Path, mode: u32) -> io::Result<()> {
    use std::os::unix::fs::PermissionsExt;

    fs::set_permissions(path, fs::Permissions::from_mode(mode))
}

#[cfg(not(unix))]
fn set_mode(path: &Path, mode: u32) -> io::Result<()> {
    let mut permissions = fs::metadata(path)?.permissions();
    permissions.set_readonly(mode & 0o200 == 0);
    fs::set_permissions(path, permissions)
}

fn with_path(error: &io::Error, path: &Path) -> io::Error {
    io::Error::new(error.kind(), format!("{}: {error}", path.display()))
}

fn with_paths(error: &io::Error, operation: &str, source: &Path, destination: &Path) -> io::Error {
    io::Error::new(
        error.kind(),
        format!("{operation} {} to {}: {error}", source.display(), destination.display()),
    )
}
