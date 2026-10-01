//! Helpers of the tests in this crate that need a real directory path, a mode, or an error text.

use std::fs;
use std::path::{Path, PathBuf};

/// A test directory without symbolic links in its path, as `toRealPath` would give it.
pub(crate) struct TempDir {
    _directory: tempfile::TempDir,
    path: PathBuf,
}

impl TempDir {
    pub(crate) fn new() -> Self {
        let directory = tempfile::tempdir().expect("a temporary directory");
        let path = fscopy::resolve_links(directory.path()).expect("a real path");
        Self {
            _directory: directory,
            path,
        }
    }

    pub(crate) fn path(&self) -> &Path {
        &self.path
    }

    /// The absolute path of this directory, as text.
    #[cfg(unix)]
    pub(crate) fn root(&self) -> String {
        self.path.to_str().expect("a UTF-8 path").to_owned()
    }
}

pub(crate) fn write_file(path: impl AsRef<Path>, content: impl AsRef<[u8]>) {
    let path = path.as_ref();
    fs::create_dir_all(path.parent().expect("a parent")).expect("the parent directory");
    fs::write(path, content).expect("the file");
}

pub(crate) fn read_text(path: impl AsRef<Path>) -> String {
    fs::read_to_string(path.as_ref()).unwrap_or_else(|error| panic!("{}: {error}", path.as_ref().display()))
}

/// Fails unless `result` is an error whose text contains `message`.
#[track_caller]
pub(crate) fn require_error<T: std::fmt::Debug, E: std::fmt::Display>(result: Result<T, E>, message: &str) {
    match result {
        Ok(value) => panic!("expected an error with {message:?}, got {value:?}"),
        Err(error) => {
            let text = format!("{error:#}");
            assert!(text.contains(message), "error = {text:?}, expected a message with {message:?}");
        }
    }
}

#[cfg(unix)]
pub(crate) fn set_mode(path: impl AsRef<Path>, mode: u32) {
    use std::os::unix::fs::PermissionsExt;
    fs::set_permissions(path.as_ref(), fs::Permissions::from_mode(mode)).expect("the mode");
}

#[cfg(unix)]
pub(crate) fn mode_of(path: impl AsRef<Path>) -> u32 {
    use std::os::unix::fs::PermissionsExt;
    fs::metadata(path.as_ref()).expect("the metadata").permissions().mode() & 0o7777
}
