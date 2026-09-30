//! The collector side of the manifest: the inventory of the files that a component places. The metadata catalogue
//! names the inventory entry of each packed source.

use std::collections::{BTreeMap, HashMap};
use std::path::Path;

use filemeta::{Entry, EntryType};
use serde::Deserialize;

use crate::error::{Error, Result};
use crate::fail;
use crate::json;
use crate::manifest::{self, ComponentEntry, ComponentEntryType, ComponentManifest, MANIFEST_VERSION};
use crate::paths::{self, compare_utf16};

/// One file that the collector places in a component.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
#[expect(clippy::struct_excessive_bools, reason = "each field is a boolean column of the inventory record")]
pub struct SourcedFile {
    /// Where the bytes are, relative to the working directory of the action.
    pub source: String,
    /// The destination in the distribution, in slash form.
    pub relative_path: String,
    pub executable: bool,
    /// The inventory entry of the source. With an entry, the collector reads nothing from the source.
    pub metadata: Option<Entry>,
    /// The mode of the source in its inventory.
    pub mode: Option<u32>,
    /// Marks a file of the plugin classpath record.
    pub class_path: bool,
    /// Marks a packed jar of the core classpath. The manifest lists it under `coreClassPath`.
    pub core_class_path: bool,
    /// Marks a directory record, which [`attach_metadata`] replaces with one file per inventory entry below it.
    pub tree: bool,
}

impl SourcedFile {
    pub fn new(source: impl Into<String>, relative_path: impl Into<String>) -> Self {
        Self {
            source: source.into(),
            relative_path: relative_path.into(),
            ..Self::default()
        }
    }
}

/// The counters of the inventory span. `file_count` counts placements. `hashed_file_count` and `byte_count` count the
/// distinct sources that the inventory hashed and their bytes.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct InventoryStats {
    pub file_count: usize,
    pub hashed_file_count: usize,
    pub byte_count: u64,
}

/// The manifest fields that the collector options give.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ManifestHeader {
    pub kind: String,
    pub platform_prefix: String,
    pub os: String,
    pub arch: String,
    /// The IDE main class, or `None` for a component that declares none.
    pub main_class: Option<String>,
    /// A plugin component states the version and one plugin.
    pub plugin_component: bool,
}

/// The entries of the files, sorted in Java string order.
///
/// A file with an inventory entry takes the hash, the type and the mode of that entry. The collector reads nothing
/// from its source. Any other file must be a regular file, and the inventory hashes it once per absolute source path.
pub fn inventory(files: &[SourcedFile]) -> Result<(Vec<ComponentEntry>, InventoryStats)> {
    let mut hashes: HashMap<String, i64> = HashMap::new();
    let mut links: BTreeMap<String, String> = BTreeMap::new();
    let mut entries = Vec::with_capacity(files.len());
    let mut byte_count = 0u64;
    for file in files {
        if let Some(metadata) = &file.metadata {
            let mut metadata = metadata.clone();
            metadata.relative_path.clone_from(&file.relative_path);
            filemeta::merge(std::iter::once(&metadata))?;
            let mut entry = ComponentEntry {
                relative_path: file.relative_path.clone(),
                entry_type: ComponentEntryType::ComponentFile,
                hash: Some(metadata.hash),
                ..ComponentEntry::default()
            };
            match metadata.entry_type {
                EntryType::Directory => {
                    entry.entry_type = ComponentEntryType::Directory;
                    entry.hash = None;
                    entry.mode = Some(manifest::logical_component_mode(metadata.mode));
                }
                EntryType::Symlink => {
                    if file.executable {
                        fail!("symbolic link has an executable override: {}", file.relative_path);
                    }
                    let target = distpath::clean_link_target(&metadata.symlink_target);
                    entry.entry_type = ComponentEntryType::Symlink;
                    entry.hash = Some(filemeta::hash_symlink_target(&target));
                    links.insert(entry.relative_path.clone(), target.clone());
                    entry.symlink_target = Some(target);
                }
                EntryType::File => {
                    entry.executable = file.executable || metadata.executable;
                    entry.source = Some(file.source.clone());
                    if let Some(mode) = file.mode {
                        let mode = manifest::logical_component_mode(mode);
                        if mode != fscopy::conventional_mode(entry.executable) {
                            entry.mode = Some(mode);
                        }
                    }
                }
            }
            entries.push(entry);
            continue;
        }
        let regular = std::fs::metadata(&file.source).ok().filter(std::fs::Metadata::is_file);
        let Some(source_metadata) = regular else {
            fail!("source of '{}' is not a regular file: {}", file.relative_path, file.source);
        };
        let absolute = paths::absolute_path(&file.source)?;
        let hash = if let Some(&hash) = hashes.get(&absolute) {
            hash
        } else {
            let hash = filemeta::hash_file(Path::new(&absolute)).map_err(|error| Error::io(&absolute, error))?;
            hashes.insert(absolute, hash);
            byte_count += source_metadata.len();
            hash
        };
        entries.push(ComponentEntry {
            relative_path: file.relative_path.clone(),
            entry_type: ComponentEntryType::ComponentFile,
            hash: Some(hash),
            executable: file.executable,
            source: Some(file.source.clone()),
            ..ComponentEntry::default()
        });
    }
    distpath::validate_links(&links).map_err(Error::msg)?;
    entries.sort_by(|first, second| compare_utf16(&first.relative_path, &second.relative_path));
    let stats = InventoryStats {
        file_count: entries.len(),
        hashed_file_count: hashes.len(),
        byte_count,
    };
    Ok((entries, stats))
}

/// The manifest of a component. The composer orders the core classpath of every component, so the manifest keeps the
/// record order of the core classpath jars.
pub fn build_manifest(header: &ManifestHeader, files: &[SourcedFile]) -> Result<(ComponentManifest, InventoryStats)> {
    let (entries, stats) = inventory(files)?;
    let manifest = ComponentManifest {
        version: header.plugin_component.then_some(MANIFEST_VERSION),
        kind: header.kind.clone(),
        platform_prefix: header.platform_prefix.clone(),
        os: header.os.clone(),
        arch: header.arch.clone(),
        additional_modules: Vec::new(),
        main_class: header.main_class.clone().filter(|main_class| !main_class.is_empty()),
        core_class_path: files
            .iter()
            .filter(|file| file.core_class_path)
            .map(|file| file.relative_path.clone())
            .collect(),
        entries,
        plugin_count: u32::from(header.plugin_component),
    };
    Ok((manifest, stats))
}

/// Builds the manifest and writes it to `path`.
pub fn write_manifest(path: &Path, header: &ManifestHeader, files: &[SourcedFile]) -> Result<InventoryStats> {
    let (manifest, stats) = build_manifest(header, files)?;
    manifest::write_component_manifest(path, &manifest)?;
    Ok(stats)
}

/// One record of the metadata catalogue. A file record's `relative_path` is the key of its inventory entry. A tree
/// record's `relative_path` is the key of the tree's root directory. The inventory lists the files below that root,
/// and the collector places them without reading the tree.
///
/// `intellij_dev_dist.bzl` writes every key, except `tree` for a file record.
#[derive(Debug, Clone, Default, PartialEq, Eq, Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct MetadataRecord {
    pub source: String,
    pub metadata: String,
    pub relative_path: String,
    #[serde(default)]
    pub tree: bool,
}

/// The inventory of one native tree. Each entry has a path below the root of the tree. `metadata` names the inventory
/// file of the entries.
#[derive(Debug, Clone)]
struct TreeMetadata {
    metadata: String,
    entries: Vec<Entry>,
}

impl TreeMetadata {
    /// Places the regular files of the tree below the destination of `record`, one file each. It places no
    /// directory, because the composer creates the parents of a file.
    fn files(&self, record: &SourcedFile) -> Vec<SourcedFile> {
        let mut files = Vec::with_capacity(self.entries.len());
        for entry in self.entries.iter().filter(|entry| entry.entry_type == EntryType::File) {
            let source = format!("{}/{}", record.source, entry.relative_path);
            let relative_path = format!("{}/{}", record.relative_path, entry.relative_path);
            let mut metadata = entry.clone();
            metadata.relative_path.clone_from(&relative_path);
            files.push(SourcedFile {
                source,
                relative_path,
                executable: entry.executable,
                mode: Some(entry.mode),
                metadata: Some(metadata),
                ..SourcedFile::default()
            });
        }
        files
    }
}

/// Pairs every file with its inventory entry, and replaces every tree record with the files that its inventory names.
/// The result is what the manifest lists, so a tree without a file contributes nothing.
pub fn attach_metadata(files: &[SourcedFile], catalogue: &Path) -> Result<Vec<SourcedFile>> {
    let records: Vec<MetadataRecord> = json::read(catalogue)?;
    let mut by_source: HashMap<String, Entry> = HashMap::new();
    let mut trees: HashMap<String, TreeMetadata> = HashMap::new();
    let mut cache: HashMap<String, HashMap<String, Entry>> = HashMap::new();
    for record in &records {
        if record.source.is_empty() || record.metadata.is_empty() || distpath::validate_path(&record.relative_path).is_err() {
            fail!(
                "{}: metadata records require source, metadata and a safe relativePath",
                catalogue.display()
            );
        }
        let metadata_path = paths::absolute_path(&record.metadata)?;
        if !cache.contains_key(&metadata_path) {
            let inventory = filemeta::read(Path::new(&metadata_path))?;
            let entries = inventory.into_iter().map(|entry| (entry.relative_path.clone(), entry)).collect();
            cache.insert(metadata_path.clone(), entries);
        }
        let entries = &cache[&metadata_path];
        let source = paths::absolute_path(&record.source)?;
        if (record.tree && by_source.contains_key(&source)) || (!record.tree && trees.contains_key(&source)) {
            fail!("conflicting metadata for source {}", record.source);
        }
        if record.tree {
            let tree = tree_entries(entries, record, &metadata_path)?;
            if trees.get(&source).is_some_and(|previous| previous.metadata != tree.metadata) {
                fail!("conflicting metadata for source {}", record.source);
            }
            trees.insert(source, tree);
            continue;
        }
        let Some(entry) = entries.get(&record.relative_path) else {
            fail!("metadata {} has no entry for {}", record.metadata, record.relative_path);
        };
        if by_source.get(&source).is_some_and(|previous| previous != entry) {
            fail!("conflicting metadata for source {}", record.source);
        }
        by_source.insert(source, entry.clone());
    }

    let mut used = std::collections::HashSet::new();
    let mut attached = Vec::with_capacity(files.len());
    for file in files {
        let source = paths::absolute_path(&file.source)?;
        if file.tree {
            let Some(tree) = trees.get(&source) else {
                if by_source.contains_key(&source) {
                    fail!("tree record {} has file metadata", file.source);
                }
                fail!("missing metadata for tree {}", file.source);
            };
            attached.extend(tree.files(file));
            used.insert(source);
            continue;
        }
        let Some(entry) = by_source.get(&source) else {
            if trees.contains_key(&source) {
                fail!("file record {} has tree metadata", file.source);
            }
            fail!("missing metadata for source {}", file.source);
        };
        let mut file = file.clone();
        file.metadata = Some(entry.clone());
        attached.push(file);
        used.insert(source);
    }
    let mut stale: Vec<&String> = by_source.keys().filter(|source| !used.contains(*source)).collect();
    stale.sort();
    if let Some(source) = stale.first() {
        fail!("stale metadata ownership for source {source}");
    }
    let mut stale: Vec<&String> = trees.keys().filter(|source| !used.contains(*source)).collect();
    stale.sort();
    if let Some(source) = stale.first() {
        fail!("stale metadata ownership for tree {source}");
    }
    Ok(attached)
}

/// The inventory of a tree. The root must be a directory entry, and every entry below it is a file or a directory.
/// The packer writes a native tree from archive entries, so a link there means that the producer changed.
fn tree_entries(entries: &HashMap<String, Entry>, record: &MetadataRecord, metadata_path: &str) -> Result<TreeMetadata> {
    let root = entries.get(&record.relative_path);
    if root.is_none_or(|root| root.entry_type != EntryType::Directory) {
        fail!(
            "metadata {} has no directory entry for tree {}",
            record.metadata,
            record.relative_path
        );
    }
    let prefix = format!("{}/", record.relative_path);
    let mut tree = TreeMetadata {
        metadata: metadata_path.to_owned(),
        entries: Vec::new(),
    };
    #[expect(clippy::iter_over_hash_type, reason = "the entries are sorted below")]
    for entry in entries.values() {
        let Some(relative_path) = entry.relative_path.strip_prefix(&prefix) else {
            continue;
        };
        if entry.entry_type == EntryType::Symlink {
            fail!(
                "metadata {} lists a symbolic link in tree {}: {}",
                record.metadata,
                record.relative_path,
                entry.relative_path
            );
        }
        let mut entry = entry.clone();
        entry.relative_path = relative_path.to_owned();
        tree.entries.push(entry);
    }
    tree.entries.sort_by(|first, second| first.relative_path.cmp(&second.relative_path));
    Ok(tree)
}

#[cfg(test)]
mod tests;
