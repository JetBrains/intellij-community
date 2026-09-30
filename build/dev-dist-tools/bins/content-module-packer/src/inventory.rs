// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The inventory of one packed group: the jar, and in natives mode also the tree.

use std::fs;
use std::path::Path;

use filemeta::{Entry, EntryType};
use jarpack::{MergeReport, MergeSpec};

/// The counters of one inventory, the tags of the `inventory packing output` span.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct InventoryReport {
    /// `fileCount`: all entries of the inventory.
    pub(crate) file_count: u64,
    /// `hashedFileCount`: the jar and each file of the tree.
    pub(crate) hashed_file_count: u64,
    /// `byteCount`: the size of the jar and of each file of the tree.
    pub(crate) byte_count: u64,
    /// `nativeFileCount`: the files of the tree. It is `Some` only when the spec writes a tree.
    pub(crate) native_file_count: Option<u64>,
}

/// Writes the metadata of what the spec packed to [`MergeSpec::metadata_file`]. That is the jar, and in natives mode
/// also the tree. The tree has its root directory by the name of the directory, and every file and directory under it.
///
/// `merged` is the report of the merge that wrote the jar. It holds the size and the content hash of the jar, so the
/// jar is not read again. The files of the tree are read again and hashed.
///
/// The collector places the files of the tree from this inventory alone, so it holds the hash, the size and the mode of
/// each. The mode of a tree file is [`jarpack::NativeSpec::file_mode`], not the mode a stat returns: POSIX reads the
/// same bits back, and NTFS stores none.
pub(crate) fn write_inventory(spec: &MergeSpec, merged: &MergeReport) -> jarpack::Result<InventoryReport> {
    let Some(metadata_file) = &spec.metadata_file else {
        return Err(jarpack::Error::Invalid(format!(
            "{}: the spec names no metadata file",
            spec.output.display()
        )));
    };
    let jar = jar_entry(&spec.output, merged)?;
    let mut hashed_file_count = 1;
    let mut byte_count = merged.bytes_written;
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

/// Returns the entry of the jar at `output` from the report of its merge. Only the mode comes from the file, through one
/// stat that does not follow a link. The result equals `filemeta::inspect` of the jar.
fn jar_entry(output: &Path, merged: &MergeReport) -> jarpack::Result<Entry> {
    let metadata = fs::symlink_metadata(output).map_err(|error| jarpack::Error::Io {
        path: output.to_path_buf(),
        error,
    })?;
    if !metadata.is_file() {
        return Err(jarpack::Error::Invalid(format!(
            "the packed jar is not a regular file: {}",
            output.display()
        )));
    }
    let mode = filemeta::permissions(&metadata);
    Ok(Entry {
        relative_path: file_name(output),
        entry_type: EntryType::File,
        hash: merged.content_hash,
        size: i64::try_from(merged.bytes_written).expect("the writer refuses a jar of 4 GiB or more"),
        mode,
        executable: mode & 0o111 != 0,
        symlink_target: String::new(),
    })
}

fn file_name(path: &Path) -> String {
    path.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default()
}

#[cfg(test)]
mod tests;
