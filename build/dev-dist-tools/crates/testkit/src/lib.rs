//! The helpers that the tests of the dev-dist tools share. Only `[dev-dependencies]` name this crate, so no tool links
//! it, and a change here reruns no Bazel action of a tool.

use std::fmt::{Debug, Display};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, MutexGuard, PoisonError};

/// Returns the `testdata/` directory of the crate under test.
///
/// Bazel sets `DDT_TESTDATA_DIR` relative to the start directory of the test. `cargo test` does not set it, so the
/// directory beside `Cargo.toml` answers through the run-time `CARGO_MANIFEST_DIR`. The Bazel process wrapper refuses a
/// binary that embeds the absolute source path, so the test cannot read `env!("CARGO_MANIFEST_DIR")`. A test that changes
/// the working directory reads a file through [`WorkingDirectory::testdata`].
pub fn testdata_dir() -> PathBuf {
    std::env::var_os("DDT_TESTDATA_DIR").map_or_else(
        || PathBuf::from(std::env::var_os("CARGO_MANIFEST_DIR").expect("cargo test sets CARGO_MANIFEST_DIR")).join("testdata"),
        PathBuf::from,
    )
}

/// Returns the path of `name` in [`testdata_dir`].
pub fn testdata(name: &str) -> PathBuf {
    testdata_dir().join(name)
}

/// A temporary directory without a symbolic link in its path, as `toRealPath` gives it. The drop deletes it.
pub struct TempDir {
    _directory: tempfile::TempDir,
    path: PathBuf,
}

impl TempDir {
    pub fn new() -> Self {
        let directory = tempfile::tempdir().expect("a temporary directory");
        let path = fscopy::resolve_links(directory.path()).expect("a real path");
        Self {
            _directory: directory,
            path,
        }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    /// The absolute path of this directory, as text.
    pub fn root(&self) -> String {
        self.path.to_str().expect("a UTF-8 path").to_owned()
    }

    /// The absolute path of `relative`, a path in slash form, in this directory. The text has native separators.
    pub fn join(&self, relative: &str) -> String {
        let native = if cfg!(windows) {
            relative.replace('/', "\\")
        } else {
            relative.to_owned()
        };
        self.path.join(native).to_str().expect("a UTF-8 path").to_owned()
    }
}

impl Default for TempDir {
    fn default() -> Self {
        Self::new()
    }
}

static WORKING_DIRECTORY_LOCK: Mutex<()> = Mutex::new(());

/// A fresh working directory for one test, for a tool that reads the relative paths of Bazel. The working directory
/// belongs to the process, so the tests hold it one at a time. The drop restores the previous working directory.
pub struct WorkingDirectory {
    previous: PathBuf,
    directory: TempDir,
    _lock: MutexGuard<'static, ()>,
}

impl WorkingDirectory {
    pub fn enter() -> Self {
        let lock = WORKING_DIRECTORY_LOCK.lock().unwrap_or_else(PoisonError::into_inner);
        let previous = std::env::current_dir().expect("the working directory");
        let directory = TempDir::new();
        std::env::set_current_dir(directory.path()).expect("a new working directory");
        Self {
            previous,
            directory,
            _lock: lock,
        }
    }

    pub fn path(&self) -> &Path {
        self.directory.path()
    }

    /// Returns the path of `name` in [`testdata_dir`]. Under Bazel that directory is relative to the previous working
    /// directory, so the path starts there.
    pub fn testdata(&self, name: &str) -> PathBuf {
        self.previous.join(testdata(name))
    }
}

impl Drop for WorkingDirectory {
    fn drop(&mut self) {
        let _ = std::env::set_current_dir(&self.previous);
    }
}

/// Writes a file and creates its parent directories.
pub fn write_file(path: impl AsRef<Path>, content: impl AsRef<[u8]>) {
    let path = path.as_ref();
    if let Some(parent) = path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        fs::create_dir_all(parent).unwrap_or_else(|error| panic!("{}: {error}", parent.display()));
    }
    fs::write(path, content).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
}

pub fn read_text(path: impl AsRef<Path>) -> String {
    let path = path.as_ref();
    fs::read_to_string(path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

pub fn read_bytes(path: impl AsRef<Path>) -> Vec<u8> {
    let path = path.as_ref();
    fs::read(path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// Fails unless nothing exists at `path`, not even a dangling symbolic link.
#[track_caller]
pub fn require_absent(path: impl AsRef<Path>) {
    let path = path.as_ref();
    assert!(fs::symlink_metadata(path).is_err(), "{} exists", path.display());
}

/// Fails unless `result` is an error whose text with its causes contains `message`.
#[track_caller]
pub fn require_error<T: Debug, E: Display>(result: Result<T, E>, message: &str) {
    match result {
        Ok(value) => panic!("expected an error with {message:?}, got {value:?}"),
        Err(error) => {
            let text = format!("{error:#}");
            assert!(text.contains(message), "error = {text:?}, expected a message with {message:?}");
        }
    }
}

/// Sets the permission bits of a file. It does nothing on Windows, where the tools set no bits.
pub fn set_mode(path: impl AsRef<Path>, mode: u32) {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;

        let path = path.as_ref();
        fs::set_permissions(path, fs::Permissions::from_mode(mode)).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
    }
    #[cfg(not(unix))]
    let _ = (path, mode);
}

/// Creates a symbolic link to a file.
pub fn file_symlink(target: impl AsRef<Path>, link: impl AsRef<Path>) {
    fscopy::symlink(target.as_ref(), link.as_ref(), false).unwrap_or_else(|error| panic!("{error}"));
}

/// Creates a symbolic link to a directory.
pub fn directory_symlink(target: impl AsRef<Path>, link: impl AsRef<Path>) {
    fscopy::symlink(target.as_ref(), link.as_ref(), true).unwrap_or_else(|error| panic!("{error}"));
}

/// The bytes of the reference vectors of the Kotlin content hash: byte `i` is the low byte of `31 * i + 7`.
#[expect(clippy::cast_possible_truncation, reason = "the vector keeps the low byte of each value")]
pub fn reference_bytes(size: usize) -> Vec<u8> {
    (0..size).map(|index| (index.wrapping_mul(31).wrapping_add(7)) as u8).collect()
}

#[cfg(test)]
mod tests;
