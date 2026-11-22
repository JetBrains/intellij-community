// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The git directory of a checkout: `.git` itself, or the directory that a `gitdir:` file names, as in a linked work
//! tree.

use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};

/// The git directory of one checkout and the common directory that it shares with the other work trees.
#[derive(Debug)]
pub(crate) struct GitDir {
    /// `.git`, or the directory of a linked work tree, such as `.git/worktrees/<name>` of the main checkout.
    pub(crate) git_dir: PathBuf,
    /// The directory that holds `config` and `info/exclude`: `commondir` of a linked work tree, or `git_dir`.
    pub(crate) common_dir: PathBuf,
    /// Whether the walk of the `ignore` crate finds `info/exclude`. It finds it through `.git` as a directory, or through
    /// a `gitdir:` file with an absolute path and a `commondir`.
    pub(crate) walk_finds_exclude: bool,
}

impl GitDir {
    /// Finds the git directory of `checkout` through `checkout/.git`.
    pub(crate) fn locate(checkout: &Path) -> Result<Self> {
        let dot_git = checkout.join(".git");
        let metadata = fs::metadata(&dot_git).with_context(|| format!("{} is not the root of a git checkout", checkout.display()))?;
        if metadata.is_dir() {
            return Ok(Self {
                common_dir: dot_git.clone(),
                git_dir: dot_git,
                walk_finds_exclude: true,
            });
        }
        let text = fs::read_to_string(&dot_git).with_context(|| format!("cannot read {}", dot_git.display()))?;
        let Some(target) = text.trim_end().strip_prefix("gitdir:") else {
            bail!("{} is a file that does not start with \"gitdir:\"", dot_git.display());
        };
        let target = Path::new(target.trim());
        let git_dir = checkout.join(target);
        if !git_dir.is_dir() {
            bail!(
                "{} names the git directory {}, which is not a directory",
                dot_git.display(),
                git_dir.display()
            );
        }
        let commondir = git_dir.join("commondir");
        let (common_dir, walk_finds_exclude) = match fs::read_to_string(&commondir) {
            Ok(text) => {
                let text = text.trim_end();
                let found = target.is_absolute() && (text.starts_with('.') || Path::new(text).is_absolute());
                (git_dir.join(text), found)
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => (git_dir.clone(), false),
            Err(error) => return Err(error).with_context(|| format!("cannot read {}", commondir.display())),
        };
        Ok(Self {
            git_dir,
            common_dir,
            walk_finds_exclude,
        })
    }

    /// The index of the work tree.
    pub(crate) fn index(&self) -> PathBuf {
        self.git_dir.join("index")
    }

    /// Refuses a checkout whose `info/exclude` has a pattern that the walk does not find. The `ignore` crate reads a
    /// relative `gitdir:` path from the current directory, and it reads no `info/exclude` without `commondir`.
    pub(crate) fn require_exclude_found(&self, checkout: &Path) -> Result<()> {
        if self.walk_finds_exclude {
            return Ok(());
        }
        let exclude = self.common_dir.join("info/exclude");
        let text = match fs::read_to_string(&exclude) {
            Ok(text) => text,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
            Err(error) => return Err(error).with_context(|| format!("cannot read {}", exclude.display())),
        };
        let has_pattern = text.lines().any(|line| !line.trim().is_empty() && !line.starts_with('#'));
        if has_pattern {
            bail!(
                "{} has patterns, but the walk does not read them: {} names its git directory by a relative path or \
                 without commondir; write the absolute path, as `git worktree add` does without worktree.useRelativePaths",
                exclude.display(),
                checkout.join(".git").display()
            );
        }
        Ok(())
    }
}
