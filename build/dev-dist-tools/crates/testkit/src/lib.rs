//! The helpers that the tests of the dev-dist tools share. Only `[dev-dependencies]` name this crate, so no tool links
//! it, and a change here reruns no Bazel action of a tool.

use std::ffi::{OsStr, OsString};
use std::fmt::{Debug, Display};
use std::fs;
use std::io::Write;
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

/// Fails unless `result` is an error whose text with its causes contains `message`, as [`require_error`] does. A failure
/// names the case `case` of a table.
#[track_caller]
pub fn require_case_error<T, E: Display>(case: &str, result: Result<T, E>, message: &str) {
    match result {
        Ok(_) => panic!("{case}: expected an error with {message:?}"),
        Err(error) => {
            let text = format!("{error:#}");
            assert!(text.contains(message), "{case}: expected {message:?}, got {text:?}");
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

/// Returns the permission bits of an entry, as the disk holds them. A test reads them to check what a tool wrote. On
/// Windows, which stores no POSIX mode, the function returns 0o755 for a directory and 0o644 for all other entries.
pub fn permission_bits(metadata: &fs::Metadata) -> u32 {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;

        metadata.permissions().mode() & 0o777
    }
    #[cfg(not(unix))]
    {
        if metadata.is_dir() { 0o755 } else { 0o644 }
    }
}

/// Creates a symbolic link to a file.
pub fn file_symlink(target: impl AsRef<Path>, link: impl AsRef<Path>) {
    fscopy::symlink(target.as_ref(), link.as_ref(), false).unwrap_or_else(|error| panic!("{error}"));
}

/// Creates a symbolic link to a directory.
pub fn directory_symlink(target: impl AsRef<Path>, link: impl AsRef<Path>) {
    fscopy::symlink(target.as_ref(), link.as_ref(), true).unwrap_or_else(|error| panic!("{error}"));
}

/// The text of a plan file of the plugin `demo` with the default variant. `assets` is the text of the asset list, and
/// each of `sections` is one more top-level member, such as `"operations": []`.
pub fn plan_text(version: u32, assets: &str, sections: &[&str]) -> String {
    let mut text = format!(r#"{{"version": {version}, "plugin": "demo", "variant": "", "assets": [{assets}]"#);
    for section in sections {
        text.push_str(", ");
        text.push_str(section);
    }
    text.push('}');
    text
}

/// The bytes of the reference vectors of the Kotlin content hash: byte `i` is the low byte of `31 * i + 7`.
#[expect(clippy::cast_possible_truncation, reason = "the vector keeps the low byte of each value")]
pub fn reference_bytes(size: usize) -> Vec<u8> {
    (0..size).map(|index| (index.wrapping_mul(31).wrapping_add(7)) as u8).collect()
}

/// The Kotlin content hash of [`reference_bytes`] for each size of the reference vectors. The sizes cross the borders of
/// the short inputs, of the 240-byte input and of the block of 256 KiB.
#[expect(
    clippy::unreadable_literal,
    reason = "the hashes are copied from the output of the former Kotlin tool"
)]
pub const REFERENCE_CONTENT_HASHES: [(usize, i64); 10] = [
    (0, 3244421341483603138),
    (1, -2399747073602280719),
    (3, -737883702129266468),
    (240, 2788469911834355041),
    (241, -4155630063455057979),
    (262143, 9078738661776034622),
    (262144, -1692254647099917537),
    (262145, -2541306581069977202),
    (524288, 3157545227256347297),
    (524301, 8144707773225287728),
];

/// The outcome of one run of a tool: the exit code and the text on stderr. A dev-dist tool writes nothing to stdout.
#[derive(Debug)]
pub struct Run {
    pub code: u8,
    pub errors: String,
}

impl Run {
    /// Fails the test unless the run failed with an error that holds `message`.
    #[track_caller]
    pub fn assert_error(&self, message: &str) {
        assert!(
            self.code != 0 && self.errors.contains(message),
            "exit {}, errors {:?}, expected {message:?}",
            self.code,
            self.errors
        );
    }

    #[track_caller]
    pub fn assert_success(&self) {
        assert_eq!(self.code, 0, "errors: {}", self.errors);
    }
}

/// Calls the `run` function of a tool with `args`, as `main` calls it, and keeps the text that it writes to stderr.
pub fn run_tool(run: impl FnOnce(Vec<OsString>, &mut dyn Write) -> u8, args: &[impl AsRef<OsStr>]) -> Run {
    let mut errors = Vec::new();
    let code = run(args.iter().map(|arg| arg.as_ref().to_owned()).collect(), &mut errors);
    Run {
        code,
        errors: String::from_utf8(errors).expect("UTF-8 on stderr"),
    }
}

/// The variable that holds the setup command of a child test run.
const CHILD_SETUP: &str = "TESTKIT_CHILD_SETUP";

/// The prefix of the line that [`report_child_output`] prints.
const CHILD_OUTPUT: &str = "testkit child output: ";

/// Runs the test `name` again in a child process for each shell command of `setups`, such as `umask 002`, and fails
/// unless each child run passes. `name` is the path of the test in its crate, as `--exact` takes it.
///
/// The parent gets `None`, and its test stops. A child gets its setup command, and its test runs the body under it.
#[track_caller]
pub fn rerun_under_shell(name: &str, setups: &[&str]) -> Option<String> {
    if let Some(setup) = child_setup() {
        return Some(setup);
    }
    run_children(name, setups);
    None
}

/// Runs the test `name` again in a child process for each shell command of `setups`, as [`rerun_under_shell`] does.
/// It also fails unless every child reports the same bytes with [`report_child_output`], so a test compares the output
/// of a tool under two umasks.
#[track_caller]
pub fn rerun_under_shell_with_equal_output(name: &str, setups: &[&str]) -> Option<String> {
    if let Some(setup) = child_setup() {
        return Some(setup);
    }
    let outputs = run_children(name, setups);
    for (setup, output) in setups.iter().zip(&outputs).skip(1) {
        assert!(
            output == &outputs[0],
            "the output under `{setup}` differs from the output under `{}`:\n{output}\n{}",
            setups[0],
            outputs[0]
        );
    }
    assert!(
        outputs.iter().all(|output| !output.is_empty()),
        "a child reported no output: {outputs:?}"
    );
    None
}

/// Prints the bytes that a child run of [`rerun_under_shell_with_equal_output`] produced, with each byte outside
/// printable ASCII escaped. The text is exact, and the parent compares the text of every child.
pub fn report_child_output(bytes: &[u8]) {
    println!("{CHILD_OUTPUT}{}", bytes.escape_ascii());
}

/// The setup command of a child run, or `None` in the parent.
fn child_setup() -> Option<String> {
    std::env::var_os(CHILD_SETUP).map(|setup| setup.into_string().expect("a UTF-8 setup command"))
}

/// Runs the test `name` in a child process for each setup command, and returns the reported output of each child, one
/// line per report.
///
/// The parent holds the lock of [`WorkingDirectory`], so each child starts in the working directory of the process and
/// not in the directory of another test. A caller must not hold a [`WorkingDirectory`].
#[track_caller]
fn run_children(name: &str, setups: &[&str]) -> Vec<String> {
    let _lock = WORKING_DIRECTORY_LOCK.lock().unwrap_or_else(PoisonError::into_inner);
    let mut outputs = Vec::with_capacity(setups.len());
    for setup in setups {
        let output = std::process::Command::new("/bin/sh")
            .arg("-c")
            .arg(format!(r#"{setup} && exec "$0" --exact "$1" --nocapture"#))
            .arg(std::env::current_exe().expect("the test binary"))
            .arg(name)
            .env(CHILD_SETUP, setup)
            .output()
            .expect("a child test run");
        let stdout = String::from_utf8_lossy(&output.stdout);
        assert!(
            output.status.success() && stdout.contains(" 1 passed;"),
            "the run under `{setup}` failed:\n{stdout}\n{}",
            String::from_utf8_lossy(&output.stderr)
        );
        let reported: Vec<&str> = stdout.lines().filter_map(|line| line.strip_prefix(CHILD_OUTPUT)).collect();
        outputs.push(reported.join("\n"));
    }
    outputs
}

#[cfg(test)]
mod tests;
