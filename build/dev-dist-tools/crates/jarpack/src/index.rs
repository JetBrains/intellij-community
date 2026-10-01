// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `__index__` entry. The class loader of the platform reads it instead of the central directory.

use std::cmp::Ordering;
use std::collections::{HashMap, HashSet};

use crate::MANIFEST_ENTRY_NAME;
use anyhow::{Result, bail};
use xxh3::{hash_bytes, hash_chars};

/// Collects the `__index__` entry. It follows `PackageIndexBuilder` and `IkvIndexBuilder` in `zip/src`. It puts the
/// directories into the index, and the writer writes no zip entry for them.
///
/// Two things here decide bytes, and each use site states them. One is the hash form of each field. The other is the
/// sort order of the arrays.
pub(crate) struct IndexBuilder {
    /// Each written file entry in write order, then the registered directories. The `__index__` entry is not here: it
    /// goes into the central directory after its own payload is written, so it cannot describe itself.
    pub(crate) entries: Vec<IkvEntry>,
    /// The name of each item of `entries`, as a range of `name_bytes`.
    names: Vec<(usize, usize)>,
    name_bytes: Vec<u8>,
    /// The Kotlin `ObjectLinkedOpenHashSet` is keyed by the hash alone, and a collision fails there. So it fails here
    /// too, and does not overwrite silently.
    by_key: HashMap<i64, usize>,

    pub(crate) class_packages: HashSet<i64>,
    pub(crate) resource_packages: HashSet<i64>,
    pub(crate) dirs_to_register: HashSet<String>,
    pub(crate) dir_order: Vec<String>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct IkvEntry {
    pub(crate) key: i64,
    pub(crate) offset: i64,
    pub(crate) size: i32,
}

impl IndexBuilder {
    pub(crate) fn new() -> Self {
        Self {
            entries: Vec::new(),
            names: Vec::new(),
            name_bytes: Vec::new(),
            by_key: HashMap::new(),
            class_packages: HashSet::new(),
            resource_packages: HashSet::new(),
            dirs_to_register: HashSet::new(),
            dir_order: Vec::new(),
        }
    }

    pub(crate) fn name(&self, position: usize) -> &[u8] {
        let (start, len) = self.names[position];
        &self.name_bytes[start..start + len]
    }

    pub(crate) fn add(&mut self, entry: IkvEntry, name: &[u8]) -> Result<()> {
        if let Some(&previous) = self.by_key.get(&entry.key) {
            bail!(
                "index key collision: {:?} and {:?} both hash to {}",
                String::from_utf8_lossy(self.name(previous)),
                String::from_utf8_lossy(name),
                entry.key
            );
        }
        self.by_key.insert(entry.key, self.entries.len());
        self.entries.push(entry);
        self.names.push((self.name_bytes.len(), name.len()));
        self.name_bytes.extend_from_slice(name);
        Ok(())
    }

    /// Records the package of an entry. The key is the hash of the *bytes*, and the package is the hash of the *chars*.
    /// See the `xxh3` crate for why those are different inputs to one function.
    pub(crate) fn add_file(&mut self, name: &str) {
        let package_hash = match name.rfind('/') {
            Some(slash) => hash_chars(&name[..slash]),
            None => 0,
        };
        if name.ends_with(".class") {
            // `AddDirEntriesMode.NONE` never registers a class directory, so there is nothing else to do.
            self.class_packages.insert(package_hash);
            return;
        }
        self.resource_packages.insert(package_hash);
        self.register_dirs(name);
    }

    /// Walks the ancestor directories of an entry, and stops at the first one that is already registered. Only the
    /// directories of non-class files are registered, because the runtime asks for those: stubs and file templates.
    pub(crate) fn register_dirs(&mut self, name: &str) {
        if name.ends_with("/package.html") || name == MANIFEST_ENTRY_NAME {
            return;
        }
        let Some(slash) = name.rfind('/') else {
            return;
        };
        let mut dir = &name[..slash];
        loop {
            if self.dirs_to_register.contains(dir) {
                return;
            }
            self.dirs_to_register.insert(dir.to_string());
            self.dir_order.push(dir.to_string());
            self.resource_packages.insert(hash_chars(dir));

            match dir.rfind('/') {
                Some(slash) => dir = &dir[..slash],
                None => return,
            }
        }
    }

    /// Returns the registered directories in Java string order. It also adds the empty package when there is a resource
    /// package, so that a request for the top-level directory resolves.
    pub(crate) fn sorted_directories(&mut self) -> Vec<String> {
        if !self.resource_packages.is_empty() {
            self.resource_packages.insert(0);
        }
        let mut dirs = self.dir_order.clone();
        // `Arrays.sort` over `String` is UTF-16 code-unit order. The byte order disagrees with it for a name outside
        // the BMP.
        dirs.sort_by(|a, b| compare_java_string(a, b));
        dirs
    }

    /// Adds the directory records. It must run after every file entry is written, so that the file entries fill the
    /// head of the index in write order. It is `writePackageIndex` for `AddDirEntriesMode.NONE`.
    pub(crate) fn finish(&mut self) -> Result<()> {
        for dir in self.sorted_directories() {
            // The size -1 packs to 0xffffffff, and that is how the reader tells a directory from a real entry.
            self.add(
                IkvEntry {
                    key: hash_bytes(dir.as_bytes()),
                    offset: 0,
                    size: -1,
                },
                dir.as_bytes(),
            )?;
        }
        Ok(())
    }

    /// `IkvIndexBuilder.dataSize()`: the key and value pairs, the count, and the unused "has size" byte.
    pub(crate) const fn entry_table_size(&self) -> usize {
        self.entries.len() * 16 + 4 + 1
    }

    pub(crate) fn payload_size(&self) -> usize {
        let name_size: usize = self.names.iter().map(|&(_, len)| len + 2).sum();
        // The 8 bytes are the two package counts, or one zero long when both sets are empty.
        self.entry_table_size() + 8 + (self.class_packages.len() + self.resource_packages.len()) * 8 + name_size
    }

    /// Serialises the index as `IkvIndexBuilder.write` and `writeIndex` do, all little-endian.
    #[expect(
        clippy::cast_possible_truncation,
        clippy::cast_sign_loss,
        reason = "the format stores the keys and offsets as unsigned bits, and the counts in the widths the reader expects"
    )]
    pub(crate) fn payload(&self) -> Vec<u8> {
        let mut out = Vec::with_capacity(self.payload_size());
        for entry in &self.entries {
            out.extend_from_slice(&(entry.key as u64).to_le_bytes());
            out.extend_from_slice(&(((entry.offset as u64) << 32) | u64::from(entry.size as u32)).to_le_bytes());
        }
        out.extend_from_slice(&(self.entries.len() as u32).to_le_bytes());
        out.push(1);

        let classes = sorted_signed(&self.class_packages);
        let resources = sorted_signed(&self.resource_packages);
        if classes.is_empty() && resources.is_empty() {
            out.extend_from_slice(&0u64.to_le_bytes());
        } else {
            out.extend_from_slice(&(classes.len() as u32).to_le_bytes());
            out.extend_from_slice(&(resources.len() as u32).to_le_bytes());
            for hash in classes.iter().chain(&resources) {
                out.extend_from_slice(&hash.to_le_bytes());
            }
        }

        for &(_, len) in &self.names {
            out.extend_from_slice(&(len as u16).to_le_bytes());
        }
        out.extend_from_slice(&self.name_bytes);
        out
    }
}

/// Sorts as the Java `long[]` sort does, signed. Half of the hashes are negative, so the sign changes the order.
fn sorted_signed(set: &HashSet<i64>) -> Vec<i64> {
    let mut out: Vec<i64> = set.iter().copied().collect();
    out.sort_unstable();
    out
}

/// Orders two strings as `java.lang.String.compareTo` does: by UTF-16 code unit. For the BMP this is the order of the
/// chars. A supplementary character is a surrogate pair from 0xD800 to 0xDBFF. So it sorts *below* U+E000 to U+FFFF,
/// and the order of the chars puts it above.
pub(crate) fn compare_java_string(a: &str, b: &str) -> Ordering {
    a.encode_utf16().cmp(b.encode_utf16())
}

#[cfg(test)]
mod tests;
