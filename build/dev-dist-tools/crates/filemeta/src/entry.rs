use std::collections::btree_map;
use std::collections::hash_map;
use std::collections::{BTreeMap, HashMap};

use anyhow::{Result, bail};
use distpath::{identity, parent_of};
use serde::{Deserialize, Serialize};

use crate::inventory::hash_symlink_target;

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
    /// The path below the payload root, in slash form. See [`distpath::validate_path`].
    pub relative_path: String,
    pub entry_type: EntryType,
    /// The [`xxh3::hash_file`] of a file or the [`hash_symlink_target`] of a link. Zero for a directory.
    pub hash: i64,
    /// The size of a file in bytes, at most `i64::MAX` because the format holds a signed number. Zero for a directory
    /// and a link.
    pub size: u64,
    /// The permission bits, at most 0o777. Zero for a link.
    pub mode: u32,
    /// True when a file has an execute bit. Always false for a directory and a link.
    pub executable: bool,
    /// The target of a link, in slash form. Empty for a file and a directory.
    pub symlink_target: String,
}

/// The largest size that the format holds: the readers store the size in a signed 64-bit number.
const MAX_SIZE: u64 = i64::MAX.cast_unsigned();

pub(crate) fn validate_entry(entry: &Entry) -> Result<()> {
    distpath::validate_path(&entry.relative_path)?;
    let path = &entry.relative_path;
    let directory = entry.entry_type == EntryType::Directory;
    if entry.size > MAX_SIZE || entry.mode > 0o777 || entry.executable != (!directory && entry.mode & 0o111 != 0) {
        bail!("invalid size or mode for {path}");
    }
    match entry.entry_type {
        EntryType::Directory => {
            if entry.hash != 0 || entry.size != 0 || !entry.symlink_target.is_empty() {
                bail!("invalid directory metadata for {path}");
            }
        }
        EntryType::File => {
            if !entry.symlink_target.is_empty() {
                bail!("file metadata has a link target: {path}");
            }
        }
        EntryType::Symlink => {
            if entry.hash != hash_symlink_target(&entry.symlink_target) || entry.size != 0 || entry.mode != 0 {
                bail!("invalid symbolic link metadata for {path}");
            }
            distpath::validate_link_target(path, &entry.symlink_target)?;
        }
    }
    Ok(())
}

/// Checks the entries together and returns them sorted bytewise by path, with each path once.
///
/// Two equal entries for one path are one entry. The function rejects these sets of entries:
///
/// - two different entries for one path,
/// - two spellings of one [`distpath::path_identity`],
/// - an entry below an entry that is not a directory,
/// - an unsafe link graph, see [`distpath::validate_links`].
///
/// Pass `first.iter().chain(&second)` to merge several groups.
pub fn merge<'a>(entries: impl IntoIterator<Item = &'a Entry>) -> Result<Vec<Entry>> {
    let mut by_path: BTreeMap<&'a str, &'a Entry> = BTreeMap::new();
    let mut spellings: HashMap<String, &'a str> = HashMap::new();
    let mut links = BTreeMap::new();
    for entry in entries {
        validate_entry(entry)?;
        match by_path.entry(&entry.relative_path) {
            btree_map::Entry::Occupied(previous) => {
                if *previous.get() != entry {
                    bail!("conflicting metadata for {}", entry.relative_path);
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
                        bail!("conflicting destinations: {} and {prefix}", previous.get());
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
                bail!("conflicting destinations: {parent} contains {name}");
            }
            current = parent;
        }
    }
    distpath::validate_links(&links)?;
    Ok(by_path.into_values().cloned().collect())
}
