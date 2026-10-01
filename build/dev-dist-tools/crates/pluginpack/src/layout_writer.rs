//! The two writers of a layout payload: one tree under a root, or the numbered file entries of one jar.

use std::collections::{HashMap, HashSet};
use std::fs;
use std::io::{self, Write};
use std::path::PathBuf;

use anyhow::{Context as _, Result, bail};

use crate::paths;

/// The bytes of one layout file: bytes that an archive entry holds, or a regular file on disk, which the tree writer
/// clones.
pub(crate) enum Content {
    Bytes(Vec<u8>),
    File(PathBuf),
}

/// Receives the entries of one layout payload in write order.
pub(crate) trait LayoutWriter {
    fn directory(&mut self, name: &str, mode: u32) -> Result<()>;
    fn file(&mut self, name: &str, content: Content, mode: u32) -> Result<()>;
    fn symlink(&mut self, name: &str, target: &str) -> Result<()>;
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Claim {
    Directory,
    File,
    Symlink,
}

impl Claim {
    const fn name(self) -> &'static str {
        match self {
            Self::Directory => "directory",
            Self::File => "file",
            Self::Symlink => "symlink",
        }
    }
}

/// Writes one tree. The first claim of a path wins, and a claim of another kind fails. An implicit parent gets mode
/// 0755. Mode zero means 0755 for a directory and 0644 for a file.
pub(crate) struct TreeWriter {
    root: PathBuf,
    claimed: HashMap<String, Claim>,
    links: Vec<(String, String)>,
}

impl TreeWriter {
    pub(crate) fn new(root: PathBuf) -> Self {
        Self {
            root,
            claimed: HashMap::new(),
            links: Vec::new(),
        }
    }

    fn path(&self, name: &str) -> PathBuf {
        paths::host(&self.root, name)
    }

    /// Records the kind of a path. It reports whether this is the first claim.
    fn claim(&mut self, name: &str, kind: Claim) -> Result<bool> {
        distpath::validate_relative_path(name)?;
        match self.claimed.get(name) {
            None => {
                self.claimed.insert(name.to_owned(), kind);
                Ok(true)
            }
            Some(previous) if *previous != kind => {
                bail!("layout asset {name:?} conflicts with a {}", previous.name())
            }
            Some(_) => Ok(false),
        }
    }

    /// Creates the links after every other entry, so a link to a directory finds its target. Windows gives a link the
    /// kind of the target that exists when the link is created. No link resolves through another link.
    pub(crate) fn finish(&self) -> Result<()> {
        for (name, target) in &self.links {
            let link = self.path(name);
            paths::create_symlink(target, &link)?;
        }
        Ok(())
    }

    /// Creates the missing ancestors of `name`. The first existing ancestor must be a directory, not a link.
    fn create_parents(&self, name: &str) -> Result<()> {
        let mut missing = Vec::new();
        let mut parent = distpath::dir(name);
        while parent != "." {
            if self.claimed.get(&parent) == Some(&Claim::Symlink) {
                bail!("layout asset parent {parent:?} is not a directory");
            }
            match fs::symlink_metadata(self.path(&parent)) {
                Ok(metadata) => {
                    if !metadata.is_dir() {
                        bail!("layout asset parent {parent:?} is not a directory");
                    }
                    break;
                }
                Err(error) if error.kind() == io::ErrorKind::NotFound => {}
                Err(error) => return Err(error).with_context(|| self.path(&parent).display().to_string()),
            }
            let next = distpath::dir(&parent);
            missing.push(parent);
            parent = next;
        }
        for directory in missing.iter().rev() {
            let target = self.path(directory);
            paths::create_directory(&target).with_context(|| target.display().to_string())?;
            fscopy::set_mode(&target, 0o755)?;
        }
        Ok(())
    }
}

impl LayoutWriter for TreeWriter {
    fn directory(&mut self, name: &str, mode: u32) -> Result<()> {
        let mode = if mode == 0 { 0o755 } else { mode };
        if name.is_empty() {
            return Ok(fscopy::set_mode(&self.root, mode)?);
        }
        let first = self.claim(name, Claim::Directory)?;
        let target = self.path(name);
        match fs::symlink_metadata(&target) {
            Ok(metadata) => {
                if !metadata.is_dir() {
                    bail!("layout asset {name:?} conflicts with a file");
                }
                if first {
                    fscopy::set_mode(&target, mode)?;
                }
                return Ok(());
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => return Err(error).with_context(|| target.display().to_string()),
        }
        self.create_parents(name)?;
        paths::create_directory(&target).with_context(|| target.display().to_string())?;
        Ok(fscopy::set_mode(&target, mode)?)
    }

    fn file(&mut self, name: &str, content: Content, mode: u32) -> Result<()> {
        if !self.claim(name, Claim::File)? {
            return Ok(());
        }
        self.create_parents(name)?;
        let target = self.path(name);
        match content {
            Content::Bytes(bytes) => {
                let mut output = fs::File::create_new(&target).with_context(|| target.display().to_string())?;
                output.write_all(&bytes).with_context(|| target.display().to_string())?;
            }
            Content::File(source) => fscopy::clone_or_copy(&source, &target)?,
        }
        Ok(fscopy::set_mode(&target, if mode == 0 { 0o644 } else { mode })?)
    }

    fn symlink(&mut self, name: &str, target: &str) -> Result<()> {
        let target = normalize_layout_link_target(target);
        validate_layout_link(name, &target)?;
        if !self.claim(name, Claim::Symlink)? {
            return Ok(());
        }
        self.create_parents(name)?;
        self.links.push((name.to_owned(), target));
        Ok(())
    }
}

/// Spells a link target the way `java.nio.file.Path.of` does, because the Kotlin writer created every link through it.
/// Repeated slashes collapse into one, and a trailing slash is removed. The components `.` and `..` stay as they are. A
/// target of slashes only becomes `/`.
pub(crate) fn normalize_layout_link_target(target: &str) -> String {
    if !target.contains("//") && !target.ends_with('/') {
        return target.to_owned();
    }
    let trimmed = target.trim_end_matches('/');
    if trimmed.is_empty() {
        return "/".to_owned();
    }
    let mut normalized = String::with_capacity(trimmed.len());
    let mut previous = '\0';
    for character in trimmed.chars() {
        if character == '/' && previous == '/' {
            continue;
        }
        normalized.push(character);
        previous = character;
    }
    normalized
}

/// Accepts a relative link whose resolved target stays inside the tree.
fn validate_layout_link(name: &str, target: &str) -> Result<()> {
    if target.is_empty() || target.starts_with('/') || target.contains(['\\', ':', '\0', '\r', '\n']) {
        bail!("layout asset link {name:?} escapes its tree: {target}");
    }
    let resolved = distpath::join(&distpath::dir(name), target);
    if resolved == ".." || resolved.starts_with("../") {
        bail!("layout asset link {name:?} escapes its tree: {target}");
    }
    Ok(())
}

/// Writes the file entries of one jar source. The first destination wins, a directory is a no-op, and a link fails.
/// Entry bytes go into numbered files. A regular file on disk is its own entry file.
pub(crate) struct EntriesWriter {
    root: PathBuf,
    names: HashSet<String>,
    entries: Vec<(String, PathBuf)>,
}

impl EntriesWriter {
    pub(crate) fn new(root: PathBuf) -> Self {
        Self {
            root,
            names: HashSet::new(),
            entries: Vec::new(),
        }
    }

    /// The entry names with their files, in write order.
    pub(crate) fn into_entries(self) -> Vec<(String, PathBuf)> {
        self.entries
    }
}

impl LayoutWriter for EntriesWriter {
    fn directory(&mut self, _name: &str, _mode: u32) -> Result<()> {
        Ok(())
    }

    fn file(&mut self, name: &str, content: Content, _mode: u32) -> Result<()> {
        distpath::validate_relative_path(name)?;
        if !self.names.insert(name.to_owned()) {
            return Ok(());
        }
        let file = match content {
            Content::Bytes(bytes) => {
                let file = self.root.join(self.entries.len().to_string());
                fs::write(&file, bytes).with_context(|| file.display().to_string())?;
                file
            }
            Content::File(source) => source,
        };
        self.entries.push((name.to_owned(), file));
        Ok(())
    }

    fn symlink(&mut self, name: &str, _target: &str) -> Result<()> {
        bail!("a jar layout asset cannot contain the symbolic link {name:?}")
    }
}
