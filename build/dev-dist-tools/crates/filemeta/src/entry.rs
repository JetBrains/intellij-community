use std::collections::btree_map;
use std::collections::hash_map;
use std::collections::{BTreeMap, HashMap};
use std::io;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::inventory::hash_symlink_target;
use crate::links::{identity, parent_of, validate_link_target, validate_links};

/// The type of an [`Entry`]. The JSON field `type` holds `file`, `directory` or `symlink`.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum EntryType {
    #[default]
    File,
    Directory,
    Symlink,
}

/// One file, directory or symbolic link of a payload directory.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Entry {
    /// The path below the payload root, in slash form. See [`validate_path`].
    pub relative_path: String,
    pub entry_type: EntryType,
    /// The [`hash_file`](crate::hash_file) of a file or the [`hash_symlink_target`] of a link. Zero for a directory.
    pub hash: i64,
    /// The size of a file in bytes. Zero for a directory and a link.
    pub size: i64,
    /// The permission bits, at most 0o777. Zero for a link.
    pub mode: u32,
    /// True when a file has an execute bit. Always false for a directory and a link.
    pub executable: bool,
    /// The target of a link, in slash form. Empty for a file and a directory.
    pub symlink_target: String,
}

/// An error of this crate.
#[derive(Debug, thiserror::Error)]
pub enum Error {
    /// A file system operation on `path` failed.
    #[error("{}: {}", .path.display(), .error)]
    Io { path: PathBuf, error: io::Error },
    /// The metadata, a path or a link graph is not valid. The text is the message of the Go original, or a text that
    /// names the unsupported input.
    #[error("{0}")]
    Invalid(String),
}

impl Error {
    pub(crate) fn io(path: &Path, error: io::Error) -> Self {
        Self::Io {
            path: path.to_path_buf(),
            error,
        }
    }
}

pub(crate) fn invalid(message: impl Into<String>) -> Error {
    Error::Invalid(message.into())
}

/// Checks that `name` is a relative path in slash form that cannot leave its root.
///
/// The path must not be empty, must not hold `\`, `:` or NUL, and must have no empty, `.` or `..` segment. Also, the
/// path must pass [`check_supported_text`].
pub fn validate_path(name: &str) -> Result<(), Error> {
    if name.is_empty() || name.contains(['\\', ':', '\0']) || name.split('/').any(|part| part.is_empty() || part == "." || part == "..") {
        return Err(invalid(format!("invalid relative path: {name:?}")));
    }
    check_supported_text(name)
}

/// Refuses a path or a link target that is not ASCII or that holds `<`, `>` or `&`.
///
/// No payload name in the repository has such a character. For all other text, `serde_json` writes the bytes of the
/// Go writer, and ASCII case folding gives the [`path_identity`](crate::path_identity) of the Go original.
pub(crate) fn check_supported_text(text: &str) -> Result<(), Error> {
    match text
        .chars()
        .find(|character| !character.is_ascii() || matches!(character, '<' | '>' | '&'))
    {
        Some(character) => Err(invalid(format!(
            "unsupported character {character:?} in {text:?}: the file metadata supports ASCII without <, > and &"
        ))),
        None => Ok(()),
    }
}

pub(crate) fn validate_entry(entry: &Entry) -> Result<(), Error> {
    validate_path(&entry.relative_path)?;
    let path = &entry.relative_path;
    let directory = entry.entry_type == EntryType::Directory;
    if entry.size < 0 || entry.mode > 0o777 || entry.executable != (!directory && entry.mode & 0o111 != 0) {
        return Err(invalid(format!("invalid size or mode for {path}")));
    }
    match entry.entry_type {
        EntryType::Directory => {
            if entry.hash != 0 || entry.size != 0 || !entry.symlink_target.is_empty() {
                return Err(invalid(format!("invalid directory metadata for {path}")));
            }
        }
        EntryType::File => {
            if !entry.symlink_target.is_empty() {
                return Err(invalid(format!("file metadata has a link target: {path}")));
            }
        }
        EntryType::Symlink => {
            if entry.hash != hash_symlink_target(&entry.symlink_target) || entry.size != 0 || entry.mode != 0 {
                return Err(invalid(format!("invalid symbolic link metadata for {path}")));
            }
            validate_link_target(path, &entry.symlink_target)?;
        }
    }
    Ok(())
}

/// Checks the entries together and returns them sorted bytewise by path, with each path once.
///
/// Two equal entries for one path are one entry. The function rejects these sets of entries:
///
/// - two different entries for one path,
/// - two spellings of one [`path_identity`](crate::path_identity),
/// - an entry below an entry that is not a directory,
/// - an unsafe link graph, see [`validate_links`].
///
/// Pass `first.iter().chain(&second)` to merge several groups.
pub fn merge<'a>(entries: impl IntoIterator<Item = &'a Entry>) -> Result<Vec<Entry>, Error> {
    let mut by_path: BTreeMap<&'a str, &'a Entry> = BTreeMap::new();
    let mut spellings: HashMap<String, &'a str> = HashMap::new();
    let mut links = BTreeMap::new();
    for entry in entries {
        validate_entry(entry)?;
        match by_path.entry(&entry.relative_path) {
            btree_map::Entry::Occupied(previous) => {
                if *previous.get() != entry {
                    return Err(invalid(format!("conflicting metadata for {}", entry.relative_path)));
                }
            }
            btree_map::Entry::Vacant(slot) => {
                slot.insert(entry);
            }
        }
        let mut prefix = entry.relative_path.as_str();
        loop {
            match spellings.entry(identity(prefix)) {
                hash_map::Entry::Occupied(previous) => {
                    if *previous.get() != prefix {
                        return Err(invalid(format!("conflicting destinations: {} and {prefix}", previous.get())));
                    }
                    // The parents of a known spelling are known too.
                    break;
                }
                hash_map::Entry::Vacant(slot) => {
                    slot.insert(prefix);
                }
            }
            match parent_of(prefix) {
                Some(parent) => prefix = parent,
                None => break,
            }
        }
        if entry.entry_type == EntryType::Symlink {
            links.insert(entry.relative_path.clone(), entry.symlink_target.clone());
        }
    }
    for name in by_path.keys() {
        let mut current = *name;
        while let Some(parent) = parent_of(current) {
            if let Some(ancestor) = by_path.get(parent)
                && ancestor.entry_type != EntryType::Directory
            {
                return Err(invalid(format!("conflicting destinations: {parent} contains {name}")));
            }
            current = parent;
        }
    }
    validate_links(&links)?;
    Ok(by_path.into_values().cloned().collect())
}
