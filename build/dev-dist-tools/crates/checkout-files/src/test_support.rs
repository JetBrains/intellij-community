// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The fixtures of the tests: an index writer and a checkout with a git directory written by hand. A test that compares
//! with `git ls-files` runs it when `git` is on `PATH`, and only checks the stated answer otherwise.

use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

/// The mode of a regular file in the index.
pub(crate) const FILE: u32 = 0o100_644;
/// The mode of a gitlink, the commit of a submodule.
pub(crate) const GITLINK: u32 = 0o160_000;

/// One entry of a written index.
pub(crate) struct IndexEntry<'a> {
    pub(crate) path: &'a str,
    pub(crate) mode: u32,
    /// 0 for a merged path, 1 to 3 for the stages of an unmerged path.
    pub(crate) stage: u16,
    /// Writes the extended flags with the intent-to-add bit, in version 3 and 4.
    pub(crate) extended: bool,
}

/// A merged regular file.
pub(crate) const fn file(path: &str) -> IndexEntry<'_> {
    IndexEntry {
        path,
        mode: FILE,
        stage: 0,
        extended: false,
    }
}

/// The bytes of an index of `version` with `entries` and `extensions`, and a zero checksum, as `index.skipHash` writes.
/// The caller sorts the entries by path and stage, as git does.
pub(crate) fn index_bytes(version: u32, entries: &[IndexEntry<'_>], extensions: &[(&[u8; 4], &[u8])]) -> Vec<u8> {
    let mut data = Vec::new();
    data.extend_from_slice(b"DIRC");
    data.extend_from_slice(&version.to_be_bytes());
    data.extend_from_slice(&u32::try_from(entries.len()).unwrap().to_be_bytes());
    let mut previous: &[u8] = &[];
    for entry in entries {
        let start = data.len();
        // ctime, mtime, dev and ino.
        data.extend_from_slice(&[0; 24]);
        data.extend_from_slice(&entry.mode.to_be_bytes());
        // uid, gid and size.
        data.extend_from_slice(&[0; 12]);
        // The object name.
        data.extend_from_slice(&[0x11; 20]);
        let path = entry.path.as_bytes();
        let mut flags = u16::try_from(path.len().min(0xfff)).unwrap() | (entry.stage << 12);
        if entry.extended {
            flags |= 0x4000;
        }
        data.extend_from_slice(&flags.to_be_bytes());
        if entry.extended {
            data.extend_from_slice(&0x2000_u16.to_be_bytes());
        }
        if version == 4 {
            let common = previous.iter().zip(path).take_while(|(left, right)| left == right).count();
            write_varint(&mut data, previous.len() - common);
            data.extend_from_slice(&path[common..]);
            data.push(0);
        } else {
            data.extend_from_slice(path);
            let length = data.len() - start;
            data.resize(start + (length + 8) / 8 * 8, 0);
        }
        previous = path;
    }
    for (signature, content) in extensions {
        data.extend_from_slice(*signature);
        data.extend_from_slice(&u32::try_from(content.len()).unwrap().to_be_bytes());
        data.extend_from_slice(content);
    }
    data.extend_from_slice(&[0; 20]);
    data
}

/// The offset varint of git, the inverse of the reader.
fn write_varint(data: &mut Vec<u8>, mut value: usize) {
    let mut bytes = vec![u8::try_from(value & 0x7f).unwrap()];
    value >>= 7;
    while value > 0 {
        value -= 1;
        bytes.push(0x80 | u8::try_from(value & 0x7f).unwrap());
        value >>= 7;
    }
    bytes.reverse();
    data.extend_from_slice(&bytes);
}

/// A temporary checkout with a `.git` directory written by hand: `HEAD`, `objects`, `refs` and `config`.
pub(crate) struct Checkout {
    _directory: tempfile::TempDir,
    root: PathBuf,
}

impl Checkout {
    /// A checkout whose `.git/config` has the `[core]` lines `core`, such as `"ignorecase = true"`.
    pub(crate) fn new(core: &str) -> Self {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path().join("checkout");
        write_git_dir(&root.join(".git"), core);
        Self {
            _directory: directory,
            root,
        }
    }

    pub(crate) fn root(&self) -> &Path {
        &self.root
    }

    /// The directory that holds the checkout, for a second checkout beside it.
    pub(crate) fn parent(&self) -> &Path {
        self.root.parent().unwrap()
    }

    /// Writes the file `path` and its parent directories.
    pub(crate) fn file(&self, path: &str) {
        write(&self.root.join(path), path);
    }

    pub(crate) fn write(&self, path: &str, content: &str) {
        write(&self.root.join(path), content);
    }

    /// Writes `.git/index`.
    pub(crate) fn index(&self, version: u32, entries: &[IndexEntry<'_>]) {
        fs::write(self.root.join(".git/index"), index_bytes(version, entries, &[])).unwrap();
    }

    /// Lists the checkout and checks the answer against `expected`, and against `git ls-files` when git is on `PATH`.
    #[track_caller]
    pub(crate) fn check(&self, roots: &[&str], expected: &[&str]) {
        check(&self.root, roots, expected);
    }
}

/// Writes a git directory that git accepts: `HEAD` of an unborn branch, `objects`, `refs` and a config.
pub(crate) fn write_git_dir(git_dir: &Path, core: &str) {
    fs::create_dir_all(git_dir.join("objects")).unwrap();
    fs::create_dir_all(git_dir.join("refs/heads")).unwrap();
    fs::write(git_dir.join("HEAD"), "ref: refs/heads/main\n").unwrap();
    fs::write(
        git_dir.join("config"),
        format!("[core]\n\trepositoryformatversion = 0\n\tbare = false\n\t{core}\n"),
    )
    .unwrap();
}

pub(crate) fn write(path: &Path, content: &str) {
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(path, content).unwrap();
}

/// See [`Checkout::check`].
#[track_caller]
pub(crate) fn check(checkout: &Path, roots: &[&str], expected: &[&str]) {
    let listed = crate::list(checkout, roots).unwrap();
    assert_eq!(listed, expected, "the listing of {roots:?}");
    if let Some(answer) = git_ls_files(checkout, roots) {
        assert_eq!(listed, answer, "the listing of {roots:?} differs from git ls-files");
    }
}

/// The answer of `git ls-files -z --cached --others --exclude-standard -- <roots>`, with the paths that do not exist
/// and the directories left out, sorted and unique. `None` when git is not on `PATH`.
pub(crate) fn git_ls_files(checkout: &Path, roots: &[&str]) -> Option<Vec<String>> {
    let pathspec: Vec<&str> = roots.iter().copied().filter(|root| !root.is_empty()).collect();
    let whole = pathspec.len() < roots.len();
    let mut command = git(checkout);
    command.args(["ls-files", "-z", "--cached", "--others", "--exclude-standard"]);
    if !whole {
        command.arg("--").args(&pathspec);
    }
    let output = run(&mut command)?;
    let stdout = String::from_utf8(output).unwrap();
    let mut paths: Vec<String> = stdout
        .split('\0')
        .filter(|path| !path.is_empty())
        .filter(|path| fs::symlink_metadata(checkout.join(path)).is_ok_and(|metadata| !metadata.is_dir()))
        .map(str::to_owned)
        .collect();
    paths.sort_unstable();
    paths.dedup();
    Some(paths)
}

/// A git command in `dir` without the system config and without the variables that move the repository. The global
/// config stays, because the listing reads the global exclude file too.
pub(crate) fn git(dir: &Path) -> Command {
    let mut command = Command::new("git");
    command
        .arg("-C")
        .arg(dir)
        .env("GIT_CONFIG_NOSYSTEM", "1")
        .env_remove("GIT_DIR")
        .env_remove("GIT_WORK_TREE")
        .env_remove("GIT_INDEX_FILE")
        .env_remove("GIT_COMMON_DIR");
    command
}

/// The stdout of a git command that succeeds, or `None` when git is not on `PATH`. A failure of git fails the test.
pub(crate) fn run(command: &mut Command) -> Option<Vec<u8>> {
    match command.output() {
        Ok(output) => {
            assert!(output.status.success(), "{command:?}: {}", String::from_utf8_lossy(&output.stderr));
            Some(output.stdout)
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            eprintln!("git is not on PATH, so the test checks only the stated answer");
            None
        }
        Err(error) => panic!("{command:?}: {error}"),
    }
}

/// The stderr of a git command that fails, or `None` when git is not on `PATH`. A success of git fails the test.
pub(crate) fn run_failing(command: &mut Command) -> Option<String> {
    match command.output() {
        Ok(output) => {
            assert!(!output.status.success(), "{command:?} succeeds");
            Some(String::from_utf8_lossy(&output.stderr).into_owned())
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => None,
        Err(error) => panic!("{command:?}: {error}"),
    }
}
