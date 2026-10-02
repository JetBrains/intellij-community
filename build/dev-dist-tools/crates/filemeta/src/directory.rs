use std::fs;
use std::io;
use std::path::Path;

/// Creates the directory `path` and its missing parents. Each directory that the function creates gets the mode 0755.
///
/// The mode does not depend on the umask, because the inventory of a payload records the mode of each directory.
/// [`fs::create_dir_all`] gives 0777 without the umask bits, so the umask 002 gives 0775. The former tool created
/// each directory with 0755 under the umask. It gave the result of this function when the umask had no bit outside 022.
///
/// A directory that exists keeps its mode. On Windows, the function only creates the directories. An error names the
/// directory and keeps the [`io::ErrorKind`] of the cause.
pub fn create_dir_all_0755(path: &Path) -> io::Result<()> {
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
        use std::os::unix::fs::PermissionsExt;

        fs::set_permissions(directory, fs::Permissions::from_mode(0o755)).map_err(|error| with_path(&error, directory))?;
    }
    Ok(())
}

fn with_path(error: &io::Error, path: &Path) -> io::Error {
    io::Error::new(error.kind(), format!("{}: {error}", path.display()))
}
