//! The collector side of the manifest: the inventory of the files that a component places. The metadata catalogue
//! names the inventory entry of each packed source.

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use component::json;
use component::manifest::{self, ComponentEntry, ComponentManifest, MANIFEST_VERSION};
use component::paths;
use filemeta::{Entry, EntryType};
use serde::Deserialize;

/// The classpath that a file of a component joins.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub(crate) enum Classpath {
    /// The file joins no classpath.
    #[default]
    None,
    /// A jar of the plugin classpath record.
    Plugin,
    /// A packed jar of the core classpath. The manifest lists it under `coreClassPath`.
    Core,
}

/// One file that the collector places in a component.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub(crate) struct SourcedFile {
    /// Where the bytes are, relative to the working directory of the action. The manifest states the same text.
    pub(crate) source: PathBuf,
    /// The destination in the distribution, in slash form.
    pub(crate) relative_path: String,
    pub(crate) executable: bool,
    /// The inventory entry of the source. With an entry, the collector reads nothing from the source.
    pub(crate) metadata: Option<Entry>,
    /// The mode of the source in its inventory.
    pub(crate) mode: Option<u32>,
    pub(crate) classpath: Classpath,
}

impl SourcedFile {
    pub(crate) fn new(source: impl Into<PathBuf>, relative_path: impl Into<String>) -> Self {
        Self {
            source: source.into(),
            relative_path: relative_path.into(),
            ..Self::default()
        }
    }

    /// The source as the manifest states it. Each source comes from JSON text, so it is UTF-8.
    fn source_text(&self) -> Result<String> {
        match self.source.to_str() {
            Some(text) => Ok(text.to_owned()),
            None => bail!("The path is not valid UTF-8: {}", self.source.display()),
        }
    }
}

/// One record of `--jars-file`: a packed jar, or the directory of a native tree, which [`attach_metadata`] replaces
/// with one file per inventory entry below it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum JarRecord {
    Jar(SourcedFile),
    Tree { source: String, relative_path: String },
}

/// The counters of the inventory span. `file_count` counts placements. `hashed_file_count` and `byte_count` count the
/// distinct sources that the inventory hashed and their bytes.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub(crate) struct InventoryStats {
    pub(crate) file_count: usize,
    pub(crate) hashed_file_count: usize,
    pub(crate) byte_count: u64,
}

/// The manifest fields that the collector options give.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub(crate) struct ManifestHeader {
    pub(crate) kind: String,
    pub(crate) platform_prefix: String,
    pub(crate) os: String,
    pub(crate) arch: String,
    /// The IDE main class, or `None` for a component that declares none.
    pub(crate) main_class: Option<String>,
    /// A plugin component holds one plugin.
    pub(crate) plugin_component: bool,
}

/// The entries of the files, sorted by their destination. A destination is ASCII, so this is the Java string order.
///
/// A file with an inventory entry takes the hash, the type and the mode of that entry. The collector reads nothing
/// from its source. Any other file must be a regular file, and the inventory hashes it once per absolute source path.
/// Such a file keeps the mode that it states, if any.
pub(crate) fn inventory(files: &[SourcedFile]) -> Result<(Vec<ComponentEntry>, InventoryStats)> {
    // `filemeta::merge` accepts two equal entries at one destination, so a repeated destination needs its own check.
    // It runs first, so that a conflict reads no payload.
    let mut destinations = HashSet::with_capacity(files.len());
    for file in files {
        if !destinations.insert(file.relative_path.as_str()) {
            bail!("conflicting destination: {}", file.relative_path);
        }
    }
    let mut hashes: HashMap<PathBuf, i64> = HashMap::new();
    let mut entries = Vec::with_capacity(files.len());
    let mut byte_count = 0u64;
    for file in files {
        let relative_path = file.relative_path.clone();
        if let Some(metadata) = &file.metadata {
            let entry = match metadata.entry_type {
                EntryType::Directory => ComponentEntry::Directory {
                    relative_path,
                    mode: manifest::logical_component_mode(metadata.mode),
                },
                EntryType::Symlink => {
                    if file.executable {
                        bail!("symbolic link has an executable override: {}", file.relative_path);
                    }
                    let target = distpath::clean_link_target(&metadata.symlink_target);
                    ComponentEntry::Symlink {
                        relative_path,
                        hash: filemeta::hash_symlink_target(&target),
                        symlink_target: target,
                    }
                }
                EntryType::File => {
                    let executable = file.executable || metadata.executable;
                    let mode = file
                        .mode
                        .map(manifest::logical_component_mode)
                        .filter(|&mode| mode != manifest::conventional_mode(executable));
                    ComponentEntry::ComponentFile {
                        relative_path,
                        hash: metadata.hash,
                        executable,
                        source: file.source_text()?,
                        mode,
                    }
                }
            };
            entries.push(entry);
            continue;
        }
        let regular = std::fs::metadata(&file.source).ok().filter(std::fs::Metadata::is_file);
        let Some(source_metadata) = regular else {
            bail!(
                "source of '{}' is not a regular file: {}",
                file.relative_path,
                file.source.display()
            );
        };
        let absolute = paths::absolute_path(&file.source)?;
        let hash = if let Some(&hash) = hashes.get(&absolute) {
            hash
        } else {
            let hash = xxh3::hash_file(&absolute).with_context(|| absolute.display().to_string())?;
            hashes.insert(absolute, hash);
            byte_count += source_metadata.len();
            hash
        };
        // A copied tree file states the mode of its source. A conventional mode is not written, as above.
        let mode = file
            .mode
            .map(manifest::logical_component_mode)
            .filter(|&mode| mode != manifest::conventional_mode(file.executable));
        entries.push(ComponentEntry::ComponentFile {
            relative_path,
            hash,
            executable: file.executable,
            source: file.source_text()?,
            mode,
        });
    }
    // One check of all entries together: the paths, the modes, the spellings, the ancestors and the link graph.
    let metadata: Vec<Entry> = entries.iter().map(ComponentEntry::to_metadata).collect();
    filemeta::merge(&metadata)?;
    entries.sort_by(|first, second| first.relative_path().cmp(second.relative_path()));
    let stats = InventoryStats {
        file_count: entries.len(),
        hashed_file_count: hashes.len(),
        byte_count,
    };
    Ok((entries, stats))
}

/// The manifest of a component. The composer orders the core classpath of every component, so the manifest keeps the
/// record order of the core classpath jars.
pub(crate) fn build_manifest(header: &ManifestHeader, files: &[SourcedFile]) -> Result<(ComponentManifest, InventoryStats)> {
    let (entries, stats) = inventory(files)?;
    let manifest = ComponentManifest {
        version: MANIFEST_VERSION,
        kind: header.kind.clone(),
        platform_prefix: header.platform_prefix.clone(),
        os: header.os.clone(),
        arch: header.arch.clone(),
        plugin: header.plugin_component,
        main_class: header.main_class.clone().filter(|main_class| !main_class.is_empty()),
        core_class_path: files
            .iter()
            .filter(|file| file.classpath == Classpath::Core)
            .map(|file| file.relative_path.clone())
            .collect(),
        entries,
    };
    Ok((manifest, stats))
}

/// Builds the manifest and writes it to `path`.
pub(crate) fn write_manifest(path: &Path, header: &ManifestHeader, files: &[SourcedFile]) -> Result<InventoryStats> {
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
pub(crate) struct MetadataRecord {
    pub(crate) source: String,
    pub(crate) metadata: String,
    pub(crate) relative_path: String,
    #[serde(default)]
    pub(crate) tree: bool,
}

/// The inventory of one native tree. Each entry has a path below the root of the tree. `metadata` names the inventory
/// file of the entries.
#[derive(Debug, Clone)]
struct TreeMetadata {
    metadata: PathBuf,
    entries: Vec<Entry>,
}

impl TreeMetadata {
    /// Places the regular files of the tree below `destination`, one file each. It places no directory, because the
    /// composer creates the parents of a file.
    fn files(&self, source: &str, destination: &str) -> Vec<SourcedFile> {
        let mut files = Vec::with_capacity(self.entries.len());
        for entry in self.entries.iter().filter(|entry| entry.entry_type == EntryType::File) {
            let source = format!("{source}/{}", entry.relative_path);
            let relative_path = format!("{destination}/{}", entry.relative_path);
            let mut metadata = entry.clone();
            metadata.relative_path.clone_from(&relative_path);
            files.push(SourcedFile {
                source: PathBuf::from(source),
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

/// Pairs every jar with its inventory entry, and replaces every tree record with the files that its inventory names.
/// The result is what the manifest lists, so a tree without a file contributes nothing.
pub(crate) fn attach_metadata(records: &[JarRecord], catalogue: &Path) -> Result<Vec<SourcedFile>> {
    let catalogue_records: Vec<MetadataRecord> = json::read(catalogue)?;
    let mut by_source: HashMap<PathBuf, Entry> = HashMap::new();
    let mut trees: HashMap<PathBuf, TreeMetadata> = HashMap::new();
    let mut cache: HashMap<PathBuf, HashMap<String, Entry>> = HashMap::new();
    for record in &catalogue_records {
        if record.source.is_empty() || record.metadata.is_empty() || distpath::validate_path(&record.relative_path).is_err() {
            bail!(
                "{}: metadata records require source, metadata and a safe relativePath",
                catalogue.display()
            );
        }
        let metadata_path = paths::absolute_path(&record.metadata)?;
        if !cache.contains_key(&metadata_path) {
            let inventory = filemeta::read(&metadata_path)?;
            let entries = inventory.into_iter().map(|entry| (entry.relative_path.clone(), entry)).collect();
            cache.insert(metadata_path.clone(), entries);
        }
        let entries = &cache[&metadata_path];
        let source = paths::absolute_path(&record.source)?;
        if (record.tree && by_source.contains_key(&source)) || (!record.tree && trees.contains_key(&source)) {
            bail!("conflicting metadata for source {}", record.source);
        }
        if record.tree {
            let tree = tree_entries(entries, record, &metadata_path)?;
            if trees.get(&source).is_some_and(|previous| previous.metadata != tree.metadata) {
                bail!("conflicting metadata for source {}", record.source);
            }
            trees.insert(source, tree);
            continue;
        }
        let Some(entry) = entries.get(&record.relative_path) else {
            bail!("metadata {} has no entry for {}", record.metadata, record.relative_path);
        };
        if by_source.get(&source).is_some_and(|previous| previous != entry) {
            bail!("conflicting metadata for source {}", record.source);
        }
        by_source.insert(source, entry.clone());
    }

    let mut used = HashSet::new();
    let mut attached = Vec::with_capacity(records.len());
    for record in records {
        let file = match record {
            JarRecord::Jar(file) => file,
            JarRecord::Tree {
                source: tree_source,
                relative_path,
            } => {
                let source = paths::absolute_path(tree_source)?;
                let Some(tree) = trees.get(&source) else {
                    if by_source.contains_key(&source) {
                        bail!("tree record {tree_source} has file metadata");
                    }
                    bail!("missing metadata for tree {tree_source}");
                };
                attached.extend(tree.files(tree_source, relative_path));
                used.insert(source);
                continue;
            }
        };
        let source = paths::absolute_path(&file.source)?;
        let Some(entry) = by_source.get(&source) else {
            if trees.contains_key(&source) {
                bail!("file record {} has tree metadata", file.source.display());
            }
            bail!("missing metadata for source {}", file.source.display());
        };
        let mut file = file.clone();
        file.metadata = Some(entry.clone());
        attached.push(file);
        used.insert(source);
    }
    let first_stale = |sources: &mut dyn Iterator<Item = &PathBuf>| {
        sources
            .filter(|source| !used.contains(*source))
            .min_by(|first, second| first.as_os_str().cmp(second.as_os_str()))
            .cloned()
    };
    if let Some(source) = first_stale(&mut by_source.keys()) {
        bail!("stale metadata ownership for source {}", source.display());
    }
    if let Some(source) = first_stale(&mut trees.keys()) {
        bail!("stale metadata ownership for tree {}", source.display());
    }
    Ok(attached)
}

/// The inventory of a tree. The root must be a directory entry, and every entry below it is a file or a directory.
/// The packer writes a native tree from archive entries, so a link there means that the producer changed.
fn tree_entries(entries: &HashMap<String, Entry>, record: &MetadataRecord, metadata_path: &Path) -> Result<TreeMetadata> {
    let root = entries.get(&record.relative_path);
    if root.is_none_or(|root| root.entry_type != EntryType::Directory) {
        bail!(
            "metadata {} has no directory entry for tree {}",
            record.metadata,
            record.relative_path
        );
    }
    let prefix = format!("{}/", record.relative_path);
    let mut tree = TreeMetadata {
        metadata: metadata_path.to_path_buf(),
        entries: Vec::new(),
    };
    #[expect(clippy::iter_over_hash_type, reason = "the entries are sorted below")]
    for entry in entries.values() {
        let Some(relative_path) = entry.relative_path.strip_prefix(&prefix) else {
            continue;
        };
        if entry.entry_type == EntryType::Symlink {
            bail!(
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
