//! The plugin component spec in its two shapes, and the files and the classpath record of each shape.
//!
//! `dev_plugin_component` (`dev_plugin_remainder.bzl`) writes the prepared shape: a remainder, its asset table and a
//! ready classpath record. `_dev_plugin` (`dev_plugin.bzl`) writes the packed shape: the final descriptor, the jars
//! that Bazel packed and the files that the plugin copies. A top-level `jars` key selects the packed shape, and then
//! the collector writes the classpath record itself.
//!
//! A prepared spec can name `refusedModules`: the content modules that the product mode of the component refuses. The
//! packer omitted every asset whose modules are all refused, so the collector drops the reused jars of those modules
//! from `independent` and expects no row for them. The packed shape lists only the placed jars, so it has no such key.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::path::Path;

use anyhow::{Context, bail};
use component::inventory::SourcedFile;
use filemeta::{Entry, EntryType};
use planfile::contract::{self, Asset, TREE_VERSION};
use serde::Deserialize;
use tracing::field::Empty;

/// The spec as the file states it. [`PluginComponentSpec::read`] checks that the keys form one of the two shapes.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
struct SpecFile {
    version: i64,
    plugin_directory: String,
    remainder: Option<Remainder>,
    assets: Option<String>,
    classpath: Option<String>,
    independent: Option<Vec<Independent>>,
    refused_modules: Option<Vec<String>>,
    descriptor: Option<String>,
    jars: Option<Vec<PackedJar>>,
    files: Option<Vec<PackedFile>>,
}

#[derive(Debug, Clone, Default, Deserialize)]
#[serde(deny_unknown_fields)]
pub(crate) struct Remainder {
    pub(crate) directory: String,
    pub(crate) metadata: String,
}

/// One reused jar. `artifact` is the module name of its `content_module_jar` target, the key that the asset rows of
/// the remainder use for the same jar.
#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
pub(crate) struct Independent {
    pub(crate) artifact: String,
    pub(crate) source: String,
    pub(crate) metadata: String,
    pub(crate) relative_path: String,
    /// The native tree that the natives jar writes for the platform of the component.
    pub(crate) native_tree: Option<NativeTreeSource>,
}

/// The directory of the native files of a reused jar and the metadata of the action that wrote it. The metadata names
/// the tree root by its directory name, [`NATIVE_TREE_ROOT`].
#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub(crate) struct NativeTreeSource {
    pub(crate) source: String,
    pub(crate) metadata: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub(crate) struct PackedJar {
    pub(crate) destination: String,
    pub(crate) source: String,
    pub(crate) metadata: String,
}

/// One file that a packed plugin copies. No packer writes metadata for it, so the inventory hashes the source.
#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub(crate) struct PackedFile {
    pub(crate) destination: String,
    pub(crate) source: String,
    pub(crate) executable: bool,
}

#[derive(Debug)]
pub(crate) struct PreparedSpec {
    pub(crate) version: u32,
    pub(crate) plugin_directory: String,
    pub(crate) remainder: Remainder,
    pub(crate) assets: String,
    pub(crate) classpath: String,
    pub(crate) independent: Vec<Independent>,
}

#[derive(Debug)]
pub(crate) struct PackedSpec {
    pub(crate) plugin_directory: String,
    pub(crate) descriptor: String,
    pub(crate) jars: Vec<PackedJar>,
    pub(crate) files: Vec<PackedFile>,
}

#[derive(Debug)]
pub(crate) enum PluginComponentSpec {
    Prepared(PreparedSpec),
    Packed(PackedSpec),
}

/// The paths that the collector writes. No input may overlap one of them.
pub(crate) struct Outputs<'a> {
    pub(crate) manifest: &'a str,
    pub(crate) classpath: &'a str,
    pub(crate) trace_file: Option<&'a str>,
}

impl PluginComponentSpec {
    /// Reads the spec and checks every declared path against the other paths and the outputs.
    pub(crate) fn read(file: &str, outputs: &Outputs<'_>) -> anyhow::Result<Self> {
        let spec: SpecFile = planfile::json::read(Path::new(file))?;
        if spec.jars.is_some() {
            Self::packed(file, spec, outputs)
        } else {
            Self::prepared(file, spec, outputs)
        }
    }

    fn prepared(file: &str, spec: SpecFile, outputs: &Outputs<'_>) -> anyhow::Result<Self> {
        let version = match u32::try_from(spec.version) {
            Ok(version) if (contract::VERSION..=TREE_VERSION).contains(&version) => version,
            _ => bail!("unsupported plugin component version: {}", spec.version),
        };
        validate_plugin_directory(&spec.plugin_directory)?;
        if spec.files.is_some() || spec.descriptor.is_some() {
            bail!("a prepared plugin component names no copied files and no descriptor");
        }
        let refused = refused_modules(spec.refused_modules.unwrap_or_default())?;
        let mut independent = spec.independent.unwrap_or_default();
        independent.retain(|artifact| !refused.contains(artifact.artifact.as_str()));
        let spec = PreparedSpec {
            version,
            plugin_directory: spec.plugin_directory,
            remainder: spec.remainder.unwrap_or_default(),
            assets: spec.assets.unwrap_or_default(),
            classpath: spec.classpath.unwrap_or_default(),
            independent,
        };
        let mut metadata = vec![file, &spec.assets, &spec.classpath, &spec.remainder.metadata];
        let mut payload = vec![spec.remainder.directory.as_str()];
        let mut identifiers = HashSet::new();
        for artifact in &spec.independent {
            if artifact.artifact.trim().is_empty() || !identifiers.insert(artifact.artifact.as_str()) {
                bail!("empty or duplicate independent artifact ID: {:?}", artifact.artifact);
            }
            filemeta::validate_path(&artifact.relative_path)?;
            metadata.push(&artifact.metadata);
            payload.push(&artifact.source);
            if let Some(tree) = &artifact.native_tree {
                metadata.push(&tree.metadata);
                payload.push(&tree.source);
            }
        }
        validate_artifact_paths(outputs, &metadata, &payload)?;
        for artifact in &spec.independent {
            if overlap(&artifact.source, &spec.remainder.directory)? {
                bail!("independent artifact {} overlaps the remainder", artifact.artifact);
            }
        }
        Ok(Self::Prepared(spec))
    }

    fn packed(file: &str, spec: SpecFile, outputs: &Outputs<'_>) -> anyhow::Result<Self> {
        if spec.version != i64::from(contract::VERSION) {
            bail!("unsupported packed plugin component version: {}", spec.version);
        }
        validate_plugin_directory(&spec.plugin_directory)?;
        if spec.remainder.is_some()
            || spec.assets.is_some()
            || spec.classpath.is_some()
            || spec.independent.is_some()
            || spec.refused_modules.is_some()
        {
            bail!("a packed plugin component names only its descriptor, jars and files");
        }
        let spec = PackedSpec {
            plugin_directory: spec.plugin_directory,
            descriptor: spec.descriptor.unwrap_or_default(),
            jars: spec.jars.unwrap_or_default(),
            files: spec.files.unwrap_or_default(),
        };
        if spec.jars.is_empty() {
            bail!("a packed plugin component requires at least one jar");
        }
        let mut metadata = vec![file, spec.descriptor.as_str()];
        let mut payload = Vec::with_capacity(spec.jars.len() + spec.files.len());
        let mut destinations = Vec::with_capacity(spec.jars.len() + spec.files.len());
        for jar in &spec.jars {
            destinations.push(jar.destination.as_str());
            metadata.push(&jar.metadata);
            payload.push(jar.source.as_str());
        }
        for copied in &spec.files {
            destinations.push(copied.destination.as_str());
            payload.push(copied.source.as_str());
        }
        validate_packed_destinations(&destinations)?;
        validate_artifact_paths(outputs, &metadata, &payload)?;
        Ok(Self::Packed(spec))
    }

    /// The files of the component and the classpath record of the plugin. The prepared shape ships the record, which
    /// the collector checks against the files. The packed shape has the collector write the record.
    pub(crate) fn collect(&self, parent: &tracing::Span) -> anyhow::Result<(Vec<SourcedFile>, Vec<u8>)> {
        match self {
            Self::Prepared(spec) => {
                let files = in_span(
                    &tracing::info_span!(parent: parent, "merge plugin component metadata", byteCount = 0i64, fileCount = Empty),
                    || collect_prepared(spec),
                )?;
                let classpath = std::fs::read(&spec.classpath).with_context(|| format!("read {}", spec.classpath))?;
                component::plugin_classpath::validate_component_record(&classpath, &spec.plugin_directory, &files)?;
                Ok((files, classpath))
            }
            Self::Packed(spec) => {
                let files = in_span(
                    &tracing::info_span!(parent: parent, "collect packed plugin jars", byteCount = 0i64, fileCount = Empty),
                    || collect_packed(spec),
                )?;
                let descriptor = std::fs::read(&spec.descriptor).with_context(|| format!("read {}", spec.descriptor))?;
                let classpath = component::plugin_classpath::component_record(&spec.plugin_directory, &descriptor, &files)?;
                Ok((files, classpath))
            }
        }
    }
}

/// Runs `collect` in `span`, records the file count, and marks the span as failed on an error.
fn in_span(span: &tracing::Span, collect: impl FnOnce() -> anyhow::Result<Vec<SourcedFile>>) -> anyhow::Result<Vec<SourcedFile>> {
    let result = collect();
    match &result {
        Ok(files) => {
            span.record("fileCount", trace::count(files.len()));
        }
        Err(error) => trace::fail(span, &format_args!("{error:#}")),
    }
    result
}

/// The refused modules of a prepared spec as a set. The rule writes the list of the descriptor leaf, so a repeated or
/// an empty name is a defect of the producer.
fn refused_modules(modules: Vec<String>) -> anyhow::Result<HashSet<String>> {
    let mut refused = HashSet::with_capacity(modules.len());
    for module in modules {
        if module.trim().is_empty() {
            bail!("a refused module requires a name");
        }
        if !refused.insert(module.clone()) {
            bail!("refused module {module:?} is named twice");
        }
    }
    Ok(refused)
}

fn validate_plugin_directory(plugin_directory: &str) -> anyhow::Result<()> {
    let valid = filemeta::validate_path(plugin_directory).is_ok()
        && plugin_directory.starts_with("plugins/")
        && plugin_directory.matches('/').count() == 1;
    if !valid {
        bail!("pluginDirectory must name plugins/<directory>: {plugin_directory:?}");
    }
    Ok(())
}

/// Checks that every declared path is safe, and that no metadata input, payload artifact or output overlaps another.
fn validate_artifact_paths(outputs: &Outputs<'_>, metadata: &[&str], payload: &[&str]) -> anyhow::Result<()> {
    let mut output_paths = vec![outputs.manifest, outputs.classpath];
    output_paths.extend(outputs.trace_file);
    for path in metadata.iter().chain(payload).chain(&output_paths) {
        validate_declared_artifact_path(path)?;
    }
    for source in metadata {
        for artifact in payload {
            if overlap(source, artifact)? {
                bail!("metadata input {source} overlaps payload artifact {artifact}");
            }
        }
    }
    for (index, destination) in output_paths.iter().enumerate() {
        for source in metadata.iter().chain(&output_paths[..index]) {
            if overlap(destination, source)? {
                bail!("output {destination} conflicts with metadata path {source}");
            }
        }
        for artifact in payload {
            if overlap(destination, artifact)? {
                bail!("output {destination} overlaps payload artifact {artifact}");
            }
        }
    }
    Ok(())
}

/// Accepts a path in slash form, relative or absolute, with no empty, `.` or `..` name. Bazel declares every path in
/// slash form, so a backslash is a defect.
fn validate_declared_artifact_path(path: &str) -> anyhow::Result<()> {
    let names = path.strip_prefix('/').unwrap_or(path);
    let invalid = path.trim().is_empty()
        || path.contains(['\\', '\0', '\r', '\n'])
        || names.split('/').any(|name| name.is_empty() || name == "." || name == "..");
    if invalid {
        bail!("invalid declared artifact path: {path:?}");
    }
    Ok(())
}

/// Tells if one declared path is the other or holds it, by [`filemeta::path_identity`]. Two relative paths start at
/// the same working directory, so the check makes a path absolute only when the other one is.
fn overlap(first: &str, second: &str) -> anyhow::Result<bool> {
    let (first, second) = (source_identity_path(first, second)?, source_identity_path(second, first)?);
    let (first, second) = (filemeta::path_identity(&first)?, filemeta::path_identity(&second)?);
    Ok(first == second || first.starts_with(&format!("{second}/")) || second.starts_with(&format!("{first}/")))
}

fn source_identity_path(path: &str, other: &str) -> anyhow::Result<String> {
    if !path.starts_with('/') && !other.starts_with('/') {
        return Ok(path.to_owned());
    }
    let absolute = std::path::absolute(component::paths::from_slash(path).as_ref()).with_context(|| format!("make {path} absolute"))?;
    Ok(component::paths::to_slash(&absolute.to_string_lossy()).into_owned())
}

/// Refuses two destinations with one identity, two spellings of one directory, and a destination below another.
/// Every destination of a packed plugin names a file, so a destination that holds another is always a conflict.
fn validate_packed_destinations(destinations: &[&str]) -> anyhow::Result<()> {
    let mut owned = HashSet::with_capacity(destinations.len());
    let mut spellings: HashMap<String, &str> = HashMap::with_capacity(destinations.len());
    for destination in destinations {
        filemeta::validate_path(destination)?;
        let identity = filemeta::path_identity(destination)?;
        if !owned.insert(identity.clone()) {
            bail!("conflicting plugin destinations: {} and {destination}", spellings[&identity]);
        }
        let mut prefix = *destination;
        loop {
            let identity = filemeta::path_identity(prefix)?;
            if let Some(previous) = spellings.insert(identity, prefix)
                && previous != prefix
            {
                bail!("conflicting plugin destinations: {previous} and {prefix}");
            }
            match prefix.rsplit_once('/') {
                Some((parent, _)) => prefix = parent,
                None => break,
            }
        }
    }
    for destination in destinations {
        let mut current = *destination;
        while let Some((parent, _)) = current.rsplit_once('/') {
            if owned.contains(&filemeta::path_identity(parent)?) {
                bail!("conflicting plugin destinations: {parent} contains {destination}");
            }
            current = parent;
        }
    }
    Ok(())
}

fn kind(asset: &Asset) -> &str {
    if asset.kind.is_empty() { "file" } else { &asset.kind }
}

/// The destination in the distribution. Every asset is below the plugin directory, also the native tree of a reused
/// jar.
fn component_destination(plugin_directory: &str, destination: &str) -> String {
    format!("{plugin_directory}/{destination}")
}

/// Tells if a plugin-relative destination is `lib/<name>.jar`, the shape that the plugin classpath lists.
fn is_plugin_lib_jar(destination: &str) -> bool {
    destination.starts_with("lib/") && destination.matches('/').count() == 1 && destination.ends_with(".jar")
}

/// The directory name of the tree of a natives jar, and the key by which its metadata names the root.
const NATIVE_TREE_ROOT: &str = "native";

/// The inventory of the native tree of a reused jar: the root and every entry below it, keyed as the packer wrote
/// them. An empty tree has no entry, so it places nothing.
struct NativeTree {
    source: String,
    entries: Vec<Entry>,
}

fn read_native_tree(tree: &NativeTreeSource) -> anyhow::Result<NativeTree> {
    if tree.source.rsplit('/').next() != Some(NATIVE_TREE_ROOT) {
        bail!("native tree {} is not named {NATIVE_TREE_ROOT}", tree.source);
    }
    let inventory = filemeta::read(Path::new(&tree.metadata))?;
    let mut entries = tree_inventory(NATIVE_TREE_ROOT, &inventory)?;
    if entries.len() == 1 {
        entries.clear();
    }
    Ok(NativeTree {
        source: tree.source.clone(),
        entries,
    })
}

/// The entries of `inventory` at and below `root`, an empty root for the plugin root. The collector checks the link
/// graph of the packer a second time, because it does not trust the producer.
fn tree_inventory(root: &str, inventory: &[Entry]) -> anyhow::Result<Vec<Entry>> {
    let mut owned = Vec::new();
    let mut directories: BTreeMap<String, bool> = BTreeMap::new();
    let mut links: BTreeMap<String, String> = BTreeMap::new();
    if root.is_empty() {
        directories.insert(".".to_owned(), true);
    }
    let prefix = format!("{root}/");
    for entry in inventory {
        let name = if root.is_empty() {
            entry.relative_path.clone()
        } else if entry.relative_path == root {
            ".".to_owned()
        } else if let Some(name) = entry.relative_path.strip_prefix(&prefix) {
            name.to_owned()
        } else {
            continue;
        };
        owned.push(entry.clone());
        directories.insert(name.clone(), entry.entry_type == EntryType::Directory);
        if entry.entry_type == EntryType::Symlink {
            links.insert(name, entry.symlink_target.clone());
        }
    }
    if !root.is_empty() && directories.get(".") != Some(&true) {
        if owned.is_empty() {
            return Ok(owned);
        }
        bail!("tree {root} requires root directory metadata");
    }
    filemeta::validate_links(&links)?;
    pluginpack::validate_link_graph(&directories, &links)?;
    Ok(owned)
}

/// Applies the shared asset rules and two rules that only the collector holds. Only a tree of the producer
/// `independent` names an artifact. No tree lies at or below a file asset.
pub(crate) fn validate_assets(version: u32, assets: &[Asset]) -> anyhow::Result<()> {
    pluginpack::validate_assets(version, assets, true)?;
    for asset in assets {
        if kind(asset) == "tree" && !asset.artifact.is_empty() && asset.producer != "independent" {
            bail!("tree {} must not name an independent artifact", asset.destination);
        }
    }
    for (tree_index, tree) in assets.iter().enumerate() {
        if kind(tree) != "tree" {
            continue;
        }
        let tree_identity = filemeta::path_identity(&tree.destination)?;
        for (asset_index, asset) in assets.iter().enumerate() {
            if asset_index == tree_index || kind(asset) != "file" {
                continue;
            }
            let asset_identity = filemeta::path_identity(&asset.destination)?;
            if tree_identity == asset_identity || tree_identity.starts_with(&format!("{asset_identity}/")) {
                bail!("asset {} overlaps tree {}", asset.destination, tree.destination);
            }
        }
    }
    Ok(())
}

/// The number of entries in an inventory file as it states them. [`filemeta::read`] merges two equal entries into
/// one, and the remainder must not state an entry twice.
fn stated_entry_count(metadata: &str) -> anyhow::Result<usize> {
    #[derive(Deserialize)]
    struct Inventory {
        entries: Vec<serde::de::IgnoredAny>,
    }
    let inventory: Inventory = planfile::json::read(Path::new(metadata))?;
    Ok(inventory.entries.len())
}

/// The files of a prepared plugin from the metadata alone. The collector reads no payload.
fn collect_prepared(spec: &PreparedSpec) -> anyhow::Result<Vec<SourcedFile>> {
    let assets: Vec<Asset> = planfile::json::read(Path::new(&spec.assets))?;
    validate_assets(spec.version, &assets)?;
    let remainder = filemeta::read(Path::new(&spec.remainder.metadata))?;
    if stated_entry_count(&spec.remainder.metadata)? != remainder.len() {
        bail!("duplicate remainder inventory entries");
    }
    let mut remaining: HashMap<&str, &Entry> = remainder.iter().map(|entry| (entry.relative_path.as_str(), entry)).collect();

    let mut independent: HashMap<&str, SourcedFile> = HashMap::with_capacity(spec.independent.len());
    let mut native_trees: HashMap<&str, NativeTree> = HashMap::new();
    let mut by_source: HashMap<String, Entry> = HashMap::new();
    for artifact in &spec.independent {
        let entries = filemeta::read(Path::new(&artifact.metadata))?;
        let [entry] = entries.as_slice() else {
            bail!(
                "independent artifact {} requires metadata for exactly one regular file {}",
                artifact.artifact,
                artifact.relative_path
            );
        };
        if entry.relative_path != artifact.relative_path || entry.entry_type != EntryType::File {
            bail!(
                "independent artifact {} requires metadata for exactly one regular file {}",
                artifact.artifact,
                artifact.relative_path
            );
        }
        let identity = filemeta::path_identity(&source_identity_path(&artifact.source, "")?)?;
        if by_source.get(&identity).is_some_and(|previous| previous != entry) {
            bail!("conflicting metadata for independent source {}", artifact.source);
        }
        by_source.insert(identity, entry.clone());
        independent.insert(
            &artifact.artifact,
            SourcedFile {
                metadata: Some(entry.clone()),
                ..SourcedFile::new(&artifact.source, "")
            },
        );
        if let Some(tree) = &artifact.native_tree {
            let tree = read_native_tree(tree).with_context(|| format!("independent artifact {}", artifact.artifact))?;
            native_trees.insert(&artifact.artifact, tree);
        }
    }

    // The remainder writes only plugin files, so a remainder asset is at its destination in the remainder directory.
    let mut claimed: HashMap<usize, Entry> = HashMap::new();
    for (index, asset) in assets.iter().enumerate() {
        if kind(asset) == "tree" || asset.producer != "remainder" {
            continue;
        }
        match remaining.remove(asset.destination.as_str()) {
            Some(entry) if asset.artifact.is_empty() && entry.entry_type != EntryType::Directory => {
                claimed.insert(index, entry.clone());
            }
            _ => bail!("stale remainder ownership for {}", asset.destination),
        }
    }

    // The most specific tree takes its entries first.
    let mut tree_indexes: Vec<usize> = (0..assets.len())
        .filter(|&index| kind(&assets[index]) == "tree" && assets[index].producer == "remainder")
        .collect();
    tree_indexes.sort_by_key(|&index| std::cmp::Reverse(assets[index].destination.len()));
    let mut tree_entries: HashMap<usize, Vec<Entry>> = HashMap::with_capacity(tree_indexes.len());
    for index in tree_indexes {
        let available: Vec<Entry> = remainder
            .iter()
            .filter(|entry| remaining.contains_key(entry.relative_path.as_str()))
            .cloned()
            .collect();
        let owned = tree_inventory(&assets[index].destination, &available)?;
        for entry in &owned {
            remaining.remove(entry.relative_path.as_str());
        }
        tree_entries.insert(index, owned);
    }

    let mut files = Vec::new();
    let mut entries = Vec::with_capacity(assets.len());
    let mut used = HashSet::new();
    let mut used_trees = HashSet::new();
    let mut place = |file: SourcedFile, mut entry: Entry| {
        entry.relative_path.clone_from(&file.relative_path);
        entries.push(entry);
        files.push(file);
    };
    for (index, asset) in assets.iter().enumerate() {
        if kind(asset) == "tree" && asset.producer == "independent" {
            let tree = native_trees.get(asset.artifact.as_str());
            let Some(tree) = tree.filter(|_| used_trees.insert(asset.artifact.as_str())) else {
                bail!("missing or repeated native tree of {} for {}", asset.artifact, asset.destination);
            };
            for entry in &tree.entries {
                let (source, destination) = match entry.relative_path.strip_prefix(&format!("{NATIVE_TREE_ROOT}/")) {
                    Some(relative) => (
                        format!("{}/{relative}", tree.source),
                        component_destination(&spec.plugin_directory, &format!("{}/{relative}", asset.destination)),
                    ),
                    None => (
                        tree.source.clone(),
                        component_destination(&spec.plugin_directory, &asset.destination),
                    ),
                };
                place(tree_file(source, destination, entry), entry.clone());
            }
            continue;
        }
        if kind(asset) == "tree" {
            for entry in tree_entries.get(&index).into_iter().flatten() {
                let source = format!("{}/{}", spec.remainder.directory, entry.relative_path);
                let destination = format!("{}/{}", spec.plugin_directory, entry.relative_path);
                place(tree_file(source, destination, entry), entry.clone());
            }
            continue;
        }
        let mut file = match asset.producer.as_str() {
            "remainder" => {
                let Some(entry) = claimed.get(&index) else {
                    bail!("stale remainder ownership for {}", asset.destination);
                };
                SourcedFile {
                    metadata: Some(entry.clone()),
                    ..SourcedFile::new(format!("{}/{}", spec.remainder.directory, asset.destination), "")
                }
            }
            "independent" => {
                let Some(file) = independent.get(asset.artifact.as_str()) else {
                    bail!("missing independent artifact {} for {}", asset.artifact, asset.destination);
                };
                used.insert(asset.artifact.as_str());
                file.clone()
            }
            producer => bail!("unknown asset producer {producer:?}"),
        };
        file.relative_path = component_destination(&spec.plugin_directory, &asset.destination);
        file.class_path = asset.class_path.unwrap_or(true) && is_plugin_lib_jar(&asset.destination);
        let entry = file.metadata.clone().expect("every asset file has metadata");
        if entry.entry_type != EntryType::Symlink {
            file.mode = Some(entry.mode);
        }
        place(file, entry);
    }
    if used_trees.len() != native_trees.len() {
        bail!(
            "stale plugin ownership: {} unused native trees",
            native_trees.len() - used_trees.len()
        );
    }
    if !remaining.is_empty() || used.len() != independent.len() {
        bail!(
            "stale plugin ownership: {} unclaimed remainder outputs, {} unused independent artifacts",
            remaining.len(),
            independent.len() - used.len()
        );
    }
    let mut destinations: HashMap<String, &str> = HashMap::with_capacity(entries.len());
    for entry in &entries {
        if let Some(previous) = destinations.insert(filemeta::path_identity(&entry.relative_path)?, &entry.relative_path) {
            bail!("conflicting plugin destinations: {previous} and {}", entry.relative_path);
        }
    }
    filemeta::merge(&entries)?;
    Ok(files)
}

/// A file of a tree. A link keeps no mode, so the inventory records its target only.
fn tree_file(source: String, relative_path: String, entry: &Entry) -> SourcedFile {
    SourcedFile {
        source,
        mode: (entry.entry_type != EntryType::Symlink).then_some(entry.mode),
        metadata: Some(entry.clone()),
        ..SourcedFile::new("", relative_path)
    }
}

/// The jars of a packed plugin in their declared order, then its copied files. It reads only the metadata of the jars.
/// A copied file has no metadata, so the inventory hashes its source.
fn collect_packed(spec: &PackedSpec) -> anyhow::Result<Vec<SourcedFile>> {
    let mut files = Vec::with_capacity(spec.jars.len() + spec.files.len());
    let mut entries = Vec::with_capacity(spec.jars.len());
    for jar in &spec.jars {
        let mut entry = read_packed_jar_metadata(jar)?;
        let file = SourcedFile {
            mode: Some(entry.mode),
            metadata: Some(entry.clone()),
            class_path: is_plugin_lib_jar(&jar.destination),
            ..SourcedFile::new(&jar.source, format!("{}/{}", spec.plugin_directory, jar.destination))
        };
        entry.relative_path.clone_from(&file.relative_path);
        entries.push(entry);
        files.push(file);
    }
    filemeta::merge(&entries)?;
    for copied in &spec.files {
        files.push(SourcedFile {
            executable: copied.executable,
            ..SourcedFile::new(&copied.source, format!("{}/{}", spec.plugin_directory, copied.destination))
        });
    }
    Ok(files)
}

/// Reads the one-file inventory that the packer wrote beside the jar. The packer names the entry after the jar file,
/// so the collector refuses a metadata file of another jar.
fn read_packed_jar_metadata(jar: &PackedJar) -> anyhow::Result<Entry> {
    let entries = filemeta::read(Path::new(&jar.metadata))?;
    match entries.as_slice() {
        [entry] if entry.entry_type == EntryType::File && Some(entry.relative_path.as_str()) == jar.source.rsplit('/').next() => {
            Ok(entry.clone())
        }
        _ => bail!("packed jar {} requires metadata for exactly one regular file", jar.source),
    }
}
