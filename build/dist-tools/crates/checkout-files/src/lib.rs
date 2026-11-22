// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The files of a git checkout, as `git ls-files --cached --others --exclude-standard -- <roots>` lists them, without the
//! `git` executable. `API.md` states the supported subset and the refusals.
//!
//! The answer is the union of two parts. The first part is the paths of the index under the roots. The second part is a
//! walk of the roots that leaves out each path that the `.gitignore` files, `info/exclude` and the global exclude file
//! ignore. A path is in the answer when it exists and is not a directory. A symbolic link stays a path of its own.

mod config;
mod gitdir;
mod index;
#[cfg(test)]
mod test_support;

use std::collections::HashSet;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex, PoisonError};

use anyhow::{Context, Result, bail};
use ignore::WalkState;

use crate::gitdir::GitDir;

/// Lists the files of the checkout `checkout` under `roots`, relative to the checkout, in slash form, sorted and unique.
///
/// A root is a path relative to the checkout in slash form. The root `""`, and also an empty `roots`, is the whole
/// checkout. The answer is that of `git ls-files -z --cached --others --exclude-standard -- <roots>`, with each path
/// that does not exist or that is a directory left out.
pub fn list(checkout: &Path, roots: &[&str]) -> Result<Vec<String>> {
    let roots = Arc::new(Roots::parse(roots)?);
    let git = GitDir::locate(checkout)?;
    git.require_exclude_found(checkout)?;
    let config = config::read(&git)?;
    check_ignore_file(&git.common_dir.join("info/exclude"))?;
    // The walk reads the same file: `core.excludesFile` of the global config, or `git/ignore` of the XDG directory.
    if let Some(global) = ignore::gitignore::gitconfig_excludes_path() {
        check_ignore_file(&global)?;
    }
    let index_file = git.index();
    let index = index::read(&index_file, |path| roots.contains(path), |path| roots.on_the_way(path))
        .with_context(|| format!("cannot read the git index {}", index_file.display()))?;
    let tracked = index.paths;
    let walked = walk(
        checkout,
        &roots,
        &Arc::new(IndexDirs {
            gitlinks: index.gitlinks,
            dirs: index.dirs,
        }),
        config.ignore_case,
    )?;

    // A tracked path that the walk found exists and is not a directory, so only the other tracked paths cost a stat.
    let walked_set: HashSet<&str> = walked.iter().map(String::as_str).collect();
    let mut listed = Vec::with_capacity(tracked.len());
    for path in &tracked {
        listed.push(walked_set.contains(path.as_str()) || is_listed(checkout, path)?);
    }
    let tracked_set: HashSet<&str> = tracked.iter().map(String::as_str).collect();
    // With `core.ignorecase`, git takes an untracked path that differs from a tracked path only in case for the tracked
    // path, so it lists only the spelling of the index.
    let tracked_folded: Option<HashSet<String>> = config
        .ignore_case
        .then(|| tracked.iter().map(|path| path.to_ascii_lowercase()).collect());
    let untracked: Vec<String> = walked
        .iter()
        .filter(|path| !tracked_set.contains(path.as_str()))
        .filter(|path| {
            tracked_folded
                .as_ref()
                .is_none_or(|folded| !folded.contains(&path.to_ascii_lowercase()))
        })
        .cloned()
        .collect();

    let mut files: Vec<String> = tracked
        .into_iter()
        .zip(listed)
        .filter_map(|(path, listed)| listed.then_some(path))
        .collect();
    files.extend(untracked);
    files.sort_unstable();
    files.dedup();
    Ok(files)
}

/// Returns the nearest directory from the real path of `dir` upwards that holds `.git`, a directory or a file. The real
/// path has no symbolic link and no `.` or `..` segment.
pub fn checkout_root(dir: &Path) -> Result<PathBuf> {
    locate(dir).map(|(root, _)| root)
}

/// Returns the checkout root of `dir`, as [`checkout_root`] finds it, and the root of `dir` for [`list`]: its real path
/// relative to the checkout root, in slash form. The checkout root itself gives `""`.
pub fn locate(dir: &Path) -> Result<(PathBuf, String)> {
    let real = real_path(dir)?;
    for candidate in real.ancestors() {
        if fs::metadata(candidate.join(".git")).is_ok() {
            let relative = real.strip_prefix(candidate).map(slash_bytes).unwrap_or_default();
            let relative = String::from_utf8(relative).with_context(|| format!("the path {} is not UTF-8 text", real.display()))?;
            return Ok((candidate.to_path_buf(), relative));
        }
    }
    bail!(
        "{} is not in a git checkout: no directory from it upwards holds .git",
        real.display()
    )
}

/// The path of `dir` without symbolic links and without `.` and `..` segments.
///
/// It is written here and not taken from `fscopy::resolve_links`, because rules_rs finds a path crate only in the
/// dependencies of a workspace member, so each workspace that links BT would have to name `fscopy` too.
#[expect(
    clippy::disallowed_methods,
    reason = "the extended-length prefix that canonicalize gives on Windows is removed below"
)]
fn real_path(dir: &Path) -> Result<PathBuf> {
    let real = fs::canonicalize(dir).with_context(|| format!("cannot resolve the real path of {}", dir.display()))?;
    #[cfg(windows)]
    let real = without_verbatim_prefix(real);
    Ok(real)
}

/// `C:\x` for `\\?\C:\x`, and `\\server\share\x` for `\\?\UNC\server\share\x`. The tools build paths without the prefix,
/// and Rust std adds it again where a long path needs it.
#[cfg(windows)]
fn without_verbatim_prefix(path: PathBuf) -> PathBuf {
    use std::path::{Component, Prefix};
    let mut components = path.components();
    let mut plain = match components.next() {
        Some(Component::Prefix(prefix)) => match prefix.kind() {
            Prefix::VerbatimDisk(letter) => PathBuf::from(format!("{}:\\", char::from(letter))),
            Prefix::VerbatimUNC(server, share) => PathBuf::from(format!("\\\\{}\\{}\\", server.to_string_lossy(), share.to_string_lossy())),
            _ => return path,
        },
        _ => return path,
    };
    for component in components {
        if let Component::Normal(name) = component {
            plain.push(name);
        }
    }
    plain
}

/// Whether a path of the index exists and is not a directory. A symbolic link is a path of its own, also when it points
/// to a directory or to nothing.
fn is_listed(checkout: &Path, path: &str) -> Result<bool> {
    match fs::symlink_metadata(checkout.join(path)) {
        Ok(metadata) => Ok(!metadata.is_dir()),
        Err(error) if matches!(error.kind(), io::ErrorKind::NotFound | io::ErrorKind::NotADirectory) => Ok(false),
        Err(error) => Err(error).with_context(|| format!("cannot read the metadata of {path} in {}", checkout.display())),
    }
}

/// The directories of the index that the walk treats apart from the others.
#[derive(Debug)]
struct IndexDirs {
    gitlinks: HashSet<Vec<u8>>,
    dirs: HashSet<Vec<u8>>,
}

/// What a directory holds as `.git`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum DotGit {
    None,
    /// A `.git` directory with `HEAD`, or a `gitdir:` file whose target holds `HEAD`.
    Repository,
    /// Another `.git`, such as an empty directory or a file without `gitdir:`. Git takes the directory for a plain one.
    Other,
}

impl DotGit {
    fn of(dir: &Path) -> Self {
        let dot_git = dir.join(".git");
        let Ok(metadata) = fs::metadata(&dot_git) else {
            return if fs::symlink_metadata(&dot_git).is_ok() {
                Self::Other
            } else {
                Self::None
            };
        };
        let git_dir = if metadata.is_dir() {
            dot_git
        } else {
            let Some(target) = fs::read_to_string(&dot_git)
                .ok()
                .and_then(|text| text.trim_end().strip_prefix("gitdir:").map(|target| dir.join(target.trim())))
            else {
                return Self::Other;
            };
            target
        };
        if git_dir.join("HEAD").is_file() {
            Self::Repository
        } else {
            Self::Other
        }
    }
}

/// Whether the walk enters or lists `relative`, the slash form of a path below the checkout. A path that is not on the
/// way to a root is left out before its name is read as text. A directory with a repository in `.git` is a nested
/// repository or a submodule, and git lists none of its files, so the walk leaves it out too. The listing refuses a
/// directory with `.git` that git enters: one with an index path below it, and one whose `.git` is no repository.
fn enters(roots: &Roots, index: &IndexDirs, relative: &[u8], is_dir: bool, dot_git: impl FnOnce() -> DotGit) -> Result<bool> {
    if !roots.on_the_way(relative) {
        return Ok(false);
    }
    if !is_dir {
        return Ok(true);
    }
    if index.gitlinks.contains(relative) {
        return Ok(false);
    }
    let dot_git = dot_git();
    if dot_git == DotGit::None {
        return Ok(true);
    }
    let name = String::from_utf8_lossy(relative);
    if index.dirs.contains(relative) {
        bail!("the directory {name} holds .git and also tracked paths of the index, which the listing does not read");
    }
    if dot_git == DotGit::Other {
        bail!("the directory {name} holds a .git that is no repository, which git walks into and the listing does not read");
    }
    Ok(false)
}

/// The untracked part: one walk from the checkout that enters only the roots and the directories on the way to them, so
/// that the ignore rules of each such directory apply, as they do in git.
fn walk(checkout: &Path, roots: &Arc<Roots>, index: &Arc<IndexDirs>, ignore_case: bool) -> Result<Vec<String>> {
    let mut builder = ignore::WalkBuilder::new(checkout);
    builder
        .hidden(false)
        .ignore(false)
        .parents(true)
        .git_ignore(true)
        .git_exclude(true)
        .git_global(true)
        .require_git(true)
        .follow_links(false)
        .ignore_case_insensitive(ignore_case)
        .current_dir(checkout.to_path_buf());
    let failure: Arc<Mutex<Option<anyhow::Error>>> = Arc::new(Mutex::new(None));
    let filter_checkout = checkout.to_path_buf();
    let filter_roots = Arc::clone(roots);
    let filter_index = Arc::clone(index);
    let filter_failure = Arc::clone(&failure);
    builder.filter_entry(move |entry| {
        if entry.depth() == 0 {
            return true;
        }
        if entry.file_name() == ".git" {
            return false;
        }
        let Ok(relative) = entry.path().strip_prefix(&filter_checkout) else {
            return true;
        };
        let is_dir = entry.file_type().is_some_and(|kind| kind.is_dir());
        match enters(&filter_roots, &filter_index, &slash_bytes(relative), is_dir, || {
            DotGit::of(entry.path())
        }) {
            Ok(enters) => enters,
            Err(error) => {
                filter_failure.lock().unwrap_or_else(PoisonError::into_inner).get_or_insert(error);
                false
            }
        }
    });

    let files = Mutex::new(Vec::new());
    builder.build_parallel().run(|| {
        let files = &files;
        let failure = &failure;
        Box::new(move |entry| match listed_path(checkout, roots, entry) {
            Ok(Some(path)) => {
                files.lock().unwrap_or_else(PoisonError::into_inner).push(path);
                WalkState::Continue
            }
            Ok(None) => WalkState::Continue,
            Err(error) => {
                failure.lock().unwrap_or_else(PoisonError::into_inner).get_or_insert(error);
                WalkState::Quit
            }
        })
    });
    let failure = failure.lock().unwrap_or_else(PoisonError::into_inner).take();
    if let Some(error) = failure {
        return Err(error);
    }
    Ok(files.into_inner().unwrap_or_else(PoisonError::into_inner))
}

/// The path of a walked entry that the listing keeps: a file, a symbolic link or another entry that is not a directory,
/// at or below a root. A directory adds nothing, but its `.gitignore` must hold only patterns that the walk reads as git
/// does.
fn listed_path(checkout: &Path, roots: &Roots, entry: Result<ignore::DirEntry, ignore::Error>) -> Result<Option<String>> {
    let entry = entry.with_context(|| format!("cannot walk {}", checkout.display()))?;
    if let Some(error) = entry.error() {
        bail!("cannot read the ignore files of {}: {error}", entry.path().display());
    }
    if entry.depth() == 0 || entry.file_type().is_none_or(|kind| kind.is_dir()) {
        check_ignore_file(&entry.path().join(".gitignore"))?;
        return Ok(None);
    }
    let relative = entry.path().strip_prefix(checkout).map(slash_bytes).unwrap_or_default();
    if !roots.contains(&relative) {
        return Ok(None);
    }
    match String::from_utf8(relative) {
        Ok(relative) => Ok(Some(relative)),
        Err(_) => bail!("the path {} is not UTF-8 text", entry.path().display()),
    }
}

/// Refuses an ignore file with a pattern that holds `{`. Git reads a brace as itself, and the walk reads `{a,b}` as a
/// choice of `a` and `b`. An absent file passes.
fn check_ignore_file(file: &Path) -> Result<()> {
    let text = match fs::read(file) {
        Ok(text) => text,
        Err(error) if matches!(error.kind(), io::ErrorKind::NotFound | io::ErrorKind::NotADirectory) => return Ok(()),
        Err(error) => return Err(error).with_context(|| format!("cannot read {}", file.display())),
    };
    for (number, line) in text.split(|&byte| byte == b'\n').enumerate() {
        if line.first() != Some(&b'#') && line.contains(&b'{') {
            bail!(
                "{}:{}: the pattern {:?} holds {{, which git reads as a brace and the walk reads as a choice",
                file.display(),
                number + 1,
                String::from_utf8_lossy(line).trim_end()
            );
        }
    }
    Ok(())
}

/// The slash form of a relative path: its names, joined with `/`, as the bytes of the platform encoding.
fn slash_bytes(relative: &Path) -> Vec<u8> {
    let mut bytes = Vec::new();
    for component in relative.components() {
        if !bytes.is_empty() {
            bytes.push(b'/');
        }
        bytes.extend_from_slice(component.as_os_str().as_encoded_bytes());
    }
    bytes
}

/// The roots of a listing. An empty list is the whole checkout.
#[derive(Debug)]
struct Roots(Vec<String>);

impl Roots {
    /// Accepts a relative slash path with no empty, `.` or `..` segment, without `\`, and without the pathspec magic of
    /// git: a leading `:`, `*`, `?` or `[`.
    fn parse(roots: &[&str]) -> Result<Self> {
        let mut parsed = Vec::with_capacity(roots.len());
        for &root in roots {
            if root.is_empty() {
                parsed.push(String::new());
                continue;
            }
            let refused = root.starts_with(':')
                || root.contains(['\\', '*', '?', '['])
                || root.split('/').any(|segment| matches!(segment, "" | "." | ".."));
            if refused {
                bail!(
                    "the root {root:?} is not a relative slash path without an empty, . or .. segment, \\, a leading : or the glob characters *?["
                );
            }
            parsed.push(root.to_owned());
        }
        if parsed.iter().any(String::is_empty) {
            parsed.clear();
        }
        Ok(Self(parsed))
    }

    /// Whether `path` is a root or a path below a root.
    fn contains(&self, path: &[u8]) -> bool {
        self.0.is_empty() || self.0.iter().any(|root| is_below(path, root.as_bytes()))
    }

    /// Whether the walk enters `path`: a root, a path below a root, or a directory above a root.
    fn on_the_way(&self, path: &[u8]) -> bool {
        self.0.is_empty()
            || self
                .0
                .iter()
                .any(|root| is_below(path, root.as_bytes()) || is_below(root.as_bytes(), path))
    }
}

/// Whether `path` is `root` or a path below `root`.
fn is_below(path: &[u8], root: &[u8]) -> bool {
    path.strip_prefix(root).is_some_and(|rest| rest.is_empty() || rest[0] == b'/')
}

#[cfg(test)]
mod tests;
