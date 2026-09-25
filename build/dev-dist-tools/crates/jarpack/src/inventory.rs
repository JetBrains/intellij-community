// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use filemeta::EntryType;

use crate::error::{Result, bail};
use crate::merge::MergeSpec;
use crate::natives::file_name;

/// The counters of one inventory, the tags of the `inventory packing output` span.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct InventoryReport {
    /// `fileCount`: all entries of the inventory.
    pub file_count: u64,
    /// `hashedFileCount`: the jar and each file of the tree.
    pub hashed_file_count: u64,
    /// `byteCount`: the size of the jar and of each file of the tree.
    pub byte_count: u64,
    /// `nativeFileCount`: the files of the tree. It is `Some` only when the spec writes a tree.
    pub native_file_count: Option<u64>,
}

/// Writes the metadata of what the spec packed to [`MergeSpec::metadata_file`]. That is the jar, and in natives mode
/// also the tree. The tree has its root directory by the name of the directory, and every file and directory under it.
///
/// The collector places the files of the tree from this inventory alone, so it holds the hash, the size and the mode of
/// each. The mode of a tree file is [`crate::NativeSpec::file_mode`], not the mode a stat returns: POSIX reads the same
/// bits back, and NTFS stores none.
pub fn write_inventory(spec: &MergeSpec) -> Result<InventoryReport> {
    let Some(metadata_file) = &spec.metadata_file else {
        bail!("{}: the spec names no metadata file", spec.output.display());
    };
    let jar = filemeta::inspect(&spec.output, &file_name(&spec.output))?;
    let mut hashed_file_count = 1;
    let mut byte_count = jar.size.unsigned_abs();
    let mut native_file_count = None;
    let mut entries = vec![jar];
    if let Some(native) = spec.native.as_ref()
        && let Some(tree) = &native.tree
    {
        let base = file_name(tree);
        let root = filemeta::inspect(tree, &base)?;
        let items = filemeta::inventory(tree)?;
        entries.push(root);
        let mut native_files = 0;
        for mut item in items {
            if item.entry_type == EntryType::File {
                let name = item.relative_path.rsplit('/').next().unwrap_or_default();
                item.mode = native.file_mode(name);
                item.executable = item.mode & 0o111 != 0;
                native_files += 1;
                hashed_file_count += 1;
                byte_count += item.size.unsigned_abs();
            }
            item.relative_path = format!("{base}/{}", item.relative_path);
            entries.push(item);
        }
        native_file_count = Some(native_files);
    }
    filemeta::write(metadata_file, &entries)?;
    Ok(InventoryReport {
        file_count: entries.len() as u64,
        hashed_file_count,
        byte_count,
        native_file_count,
    })
}
