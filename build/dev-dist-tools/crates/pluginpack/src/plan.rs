//! The plan step: it validates the recipe against the catalogue and reads no file.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::path::{Path, PathBuf};

use javaglob::JavaGlob;
use planfile::LayoutFormat;
use planfile::contract::{
    Artifact, ArtifactKind, AssetKind, Catalogue, Filter, LayoutAsset, LayoutAssets, LayoutMapping, LayoutTransformKind, Operation,
    Producer, Recipe, Reference, Source, TREE_VERSION, VERSION,
};
use planfile::validate::{validate_link_graph, validated_assets};

use crate::error::{Error, Result, fail};

/// A validated plan. [`plan`] makes it without file system access, and [`Execution::write`] executes it.
#[derive(Clone, Debug)]
pub struct Execution {
    pub(crate) recipe: Recipe,
    pub(crate) artifacts: HashMap<String, Artifact>,
    /// The catalogue artifacts in their order: the complete input set of the action.
    pub(crate) inputs: Vec<Artifact>,
}

/// The kind of one layout input: a raw directory without a path, or a file.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum InputKind {
    File,
    Directory,
}

/// The identity of a destination: `distpath::path_identity`. It refuses a path that the inventory cannot hold, so a
/// refused name fails before any write.
pub(crate) fn identity(path: &str) -> Result<String> {
    distpath::path_identity(path).map_err(Error::refused)
}

/// Validates the recipe against the catalogue. The catalogue artifacts, in their order, become the execution inputs.
pub fn plan(recipe: &Recipe, catalogue: &Catalogue) -> Result<Execution> {
    if !(VERSION..=TREE_VERSION).contains(&recipe.version) || catalogue.version != VERSION {
        fail!("unsupported contract version; the recipe must use version 1 or 2, the catalogue version 1");
    }
    if !valid_id(&recipe.plugin) || !valid_id(&recipe.layout_signature) {
        fail!("invalid plugin identity or layout signature");
    }
    let has_trees = recipe.assets.iter().any(|asset| asset.kind == AssetKind::Tree);
    let assets = validated_assets(recipe.version, &recipe.assets, has_trees).map_err(Error::chain)?;
    // The artifact of an independent asset is the module name of its reused jar. The module output can be a catalogue
    // input of the same chain under that name, so the two namespaces are not compared.
    for asset in &recipe.assets {
        match asset.producer {
            Producer::Independent => {
                // A file is a reused jar. A tree is the native tree of a reused natives jar, see `validated_assets`.
                if !valid_id(&asset.artifact) {
                    fail!("independent asset {:?} requires an artifact ID", asset.destination);
                }
            }
            Producer::Remainder => {
                if !asset.artifact.is_empty() {
                    fail!("remainder asset {:?} must not name an independent artifact", asset.destination);
                }
            }
        }
    }
    for name in assets.keys() {
        let mut parent = distpath::dir(name);
        while parent != "." {
            if let Some(ancestor) = assets.get(&parent)
                && ancestor.kind == AssetKind::File
            {
                fail!("destination collision between {parent:?} and {name:?}");
            }
            parent = distpath::dir(&parent);
        }
    }
    if !catalogue.libraries.is_empty() {
        fail!("the catalogue names a library, but planfile expands each library into its files");
    }
    let mut execution = Execution {
        recipe: recipe.clone(),
        artifacts: HashMap::with_capacity(catalogue.artifacts.len()),
        inputs: Vec::with_capacity(catalogue.artifacts.len()),
    };
    let mut roots: HashSet<PathBuf> = HashSet::new();
    for artifact in &catalogue.artifacts {
        if execution.artifacts.contains_key(&artifact.id) || !valid_id(&artifact.id) {
            fail!("invalid or duplicate catalogue input {:?}", artifact.id);
        }
        if !clean_root(&artifact.root) {
            fail!("invalid root for {:?}", artifact.id);
        }
        let absolute = fscopy::absolute_path(Path::new(&artifact.root))
            .map_err(|error| Error::new(format!("invalid or duplicate root for {:?}: {error}", artifact.id)))?;
        if !roots.insert(absolute) {
            fail!("invalid or duplicate root for {:?}", artifact.id);
        }
        execution.artifacts.insert(artifact.id.clone(), artifact.clone());
        execution.inputs.push(artifact.clone());
    }
    let mut used = HashSet::new();
    let mut operations = HashSet::new();
    for operation in &recipe.operations {
        let destination = operation.destination();
        let key = identity(destination)?;
        let asset = match assets.get(&key) {
            Some(asset) if !operations.contains(&key) && asset.destination == destination && asset.producer == Producer::Remainder => {
                *asset
            }
            _ => fail!("conflicting or unowned remainder destination {destination:?}"),
        };
        execution
            .validate_operation(operation, &mut used)
            .map_err(|error| error.context(destination))?;
        let writes_tree = matches!(operation, Operation::CopyTree { .. } | Operation::LayoutTree { .. });
        if (asset.kind == AssetKind::Tree) != writes_tree {
            fail!("stale asset kind at {destination:?}");
        }
        operations.insert(key);
    }
    for asset in &recipe.assets {
        let key = identity(&asset.destination)?;
        if asset.producer == Producer::Remainder && !operations.contains(&key) {
            fail!("missing remainder operation for {:?}", asset.destination);
        }
    }
    if used.len() != execution.artifacts.len() {
        fail!("stale ownership: catalogue contains unused inputs");
    }
    Ok(execution)
}

/// Reports whether a catalogue root is a clean, nonempty path without a NUL or a line end, as Go `path.Clean` states it.
fn clean_root(root: &str) -> bool {
    let slashed = if cfg!(windows) { root.replace('\\', "/") } else { root.to_owned() };
    !root.is_empty() && root != "." && distpath::clean(&slashed) == slashed && !root.contains(['\0', '\r', '\n'])
}

impl Execution {
    fn validate_operation(&self, operation: &Operation, used: &mut HashSet<String>) -> Result<()> {
        match operation {
            Operation::LayoutTree { layout, .. } => {
                if self.recipe.version < TREE_VERSION {
                    fail!("layout-tree requires version 2");
                }
                self.validate_layout(layout, LayoutFormat::Tree, used)
            }
            Operation::CopyTree { input, .. } => {
                if self.recipe.version < TREE_VERSION || !input.path.is_empty() {
                    fail!("copy-tree requires version 2 and one directory root");
                }
                match self.artifacts.get(&input.artifact) {
                    Some(artifact) if artifact.kind == ArtifactKind::Directory => {
                        used.insert(artifact.id.clone());
                        Ok(())
                    }
                    _ => fail!("copy-tree requires a declared directory artifact"),
                }
            }
            Operation::Jar { mode, sources, .. } => {
                validate_file_mode(*mode)?;
                for source in sources {
                    self.validate_source(source, used)?;
                }
                Ok(())
            }
            Operation::Copy { mode, input, .. } => {
                validate_file_mode(*mode)?;
                self.validate_reference(input, used)
            }
        }
    }

    fn validate_source(&self, source: &Source, used: &mut HashSet<String>) -> Result<()> {
        match source {
            Source::Layout(layout) => self.validate_layout(layout, LayoutFormat::Entries, used),
            Source::Archive { input, .. } => self.validate_reference(input, used),
            Source::Patch { entry, input, .. } => {
                distpath::validate_entry_name(entry).map_err(Error::refused)?;
                self.validate_reference(input, used)
            }
        }
    }

    fn validate_reference(&self, reference: &Reference, used: &mut HashSet<String>) -> Result<()> {
        let Some(artifact) = self.artifacts.get(&reference.artifact) else {
            fail!("unresolved input {:?}", reference.artifact);
        };
        if artifact.kind == ArtifactKind::File {
            if !reference.path.is_empty() {
                fail!("file input {:?} cannot have a relative path", artifact.id);
            }
        } else {
            distpath::validate_relative_path(&reference.path).map_err(Error::refused)?;
        }
        used.insert(artifact.id.clone());
        Ok(())
    }

    /// Applies the operation rules of the Kotlin generator to a layout payload without reading the file system. Every
    /// input must resolve. An input is a directory when it names a raw directory artifact without a path.
    fn validate_layout(&self, layout: &LayoutAssets, format: LayoutFormat, used: &mut HashSet<String>) -> Result<()> {
        if layout.assets.is_empty() {
            fail!("layout assets require at least one asset");
        }
        let mut kinds = Vec::with_capacity(layout.inputs.len());
        for reference in &layout.inputs {
            kinds.push(self.validate_layout_input(reference, used)?);
        }
        for asset in &layout.assets {
            validate_layout_asset(asset, format, &kinds)
                .map_err(|error| error.context(format_args!("layout asset {:?}", asset.destination)))?;
        }
        Ok(())
    }

    fn validate_layout_input(&self, reference: &Reference, used: &mut HashSet<String>) -> Result<InputKind> {
        let Some(artifact) = self.artifacts.get(&reference.artifact) else {
            fail!("unresolved layout input {:?}", reference.artifact);
        };
        if artifact.kind == ArtifactKind::Directory && reference.path.is_empty() {
            used.insert(artifact.id.clone());
            return Ok(InputKind::Directory);
        }
        self.validate_reference(reference, used)?;
        Ok(InputKind::File)
    }
}

/// The plan files state the modes 0644 and 0755 only, so the recipe states no other mode.
fn validate_file_mode(mode: u32) -> Result<()> {
    if mode != 0o644 && mode != 0o755 {
        fail!("unsupported file mode {mode:o}; the packer writes only the modes 644 and 755");
    }
    Ok(())
}

pub(crate) fn validate_layout_asset(asset: &LayoutAsset, format: LayoutFormat, kinds: &[InputKind]) -> Result<()> {
    if asset.mode > 0o777 {
        fail!("invalid mode {:o}", asset.mode);
    }
    if let Some(index) = asset.sources.iter().find(|index| **index >= kinds.len()) {
        fail!("invalid source index {index}");
    }
    let sources_are = |kind: InputKind| asset.sources.iter().all(|index| kinds[*index] == kind);
    let kind = asset.transform.as_ref().map(|transform| transform.kind);
    if asset.destination.is_empty() {
        // An entry asset can write its output root when each entry brings its own relative path. An extracted archive
        // and a copied directory do that.
        let expands = kind.is_some() || asset.sources.len() == 1 && sources_are(InputKind::Directory);
        if format != LayoutFormat::Tree && !expands {
            fail!("only a tree, an extracted archive, or a copied directory can use its output root");
        }
    } else {
        distpath::validate_relative_path(&asset.destination).map_err(Error::refused)?;
    }
    let Some(transform) = &asset.transform else {
        if asset.sources.len() != 1 {
            fail!("a plain copy requires one source");
        }
        return Ok(());
    };
    compile_includes(&transform.includes)?;
    compile_globs(&transform.executables, "invalid executable pattern")?;
    for mapping in &transform.mappings {
        if !mapping.destination.is_empty() {
            distpath::validate_relative_path(&mapping.destination).map_err(Error::refused)?;
        }
        JavaGlob::compile(mapping_pattern(mapping)).map_err(|error| Error::new(format!("invalid mapping pattern: {error}")))?;
    }
    match transform.kind {
        LayoutTransformKind::ArchiveTree => {
            if asset.sources.len() != 1 || !sources_are(InputKind::File) {
                fail!("archive-tree requires one archive file");
            }
        }
    }
    Ok(())
}

pub(crate) fn mapping_pattern(mapping: &LayoutMapping) -> &str {
    if mapping.pattern.is_empty() { "**" } else { &mapping.pattern }
}

pub(crate) fn compile_globs(patterns: &[String], context: &str) -> Result<Vec<JavaGlob>> {
    patterns
        .iter()
        .map(|pattern| JavaGlob::compile(pattern).map_err(|error| Error::new(format!("{context}: {error}"))))
        .collect()
}

/// One include pattern. A leading `!` in the pattern text makes it an exclude.
pub(crate) struct IncludeRule {
    matcher: JavaGlob,
    exclude: bool,
}

pub(crate) fn compile_includes(patterns: &[String]) -> Result<Vec<IncludeRule>> {
    let mut rules = Vec::with_capacity(patterns.len());
    for pattern in patterns {
        let glob = pattern.strip_prefix('!');
        let exclude = glob.is_some();
        let glob = glob.unwrap_or(pattern);
        if glob.is_empty() {
            fail!("invalid include: empty pattern {pattern:?}");
        }
        let matcher = JavaGlob::compile(glob).map_err(|error| Error::new(format!("invalid include: {error}")))?;
        rules.push(IncludeRule { matcher, exclude });
    }
    Ok(rules)
}

/// Applies the include rules to one relative path. The last matching rule decides. Without a match, the entry is
/// written when every rule excludes, and dropped otherwise. No rule at all writes every entry.
pub(crate) fn includes_entry(rules: &[IncludeRule], name: &str) -> bool {
    let mut included = rules.iter().all(|rule| rule.exclude);
    for rule in rules {
        if rule.matcher.matches(name) {
            included = !rule.exclude;
        }
    }
    included
}

/// Selects the entries of one archive source. The manifest policy stays with jarpack.
pub(crate) const fn source_filter(filter: Filter) -> jarpack::EntryFilter {
    match filter {
        Filter::Module => jarpack::EntryFilter::ModuleOutput,
        Filter::Library => jarpack::EntryFilter::Library,
    }
}

pub(crate) fn valid_id(value: &str) -> bool {
    !value.is_empty() && value.trim() == value && !value.contains(['\0', '\r', '\n'])
}

/// Checks the links of the plugin. `nodes` holds each file and directory of the plugin with its kind. The graph check
/// is [`validate_link_graph`], which the collector applies again to the produced inventory.
pub(crate) fn validate_plugin_links(nodes: &[(String, bool)], links: &BTreeMap<String, String>) -> Result<()> {
    if links.is_empty() {
        return Ok(());
    }
    distpath::validate_links(links).map_err(Error::refused)?;
    let mut directories = BTreeMap::from([(".".to_owned(), true)]);
    for (destination, directory) in nodes {
        directories.insert(destination.clone(), *directory);
        let mut parent = distpath::dir(destination);
        while parent != "." {
            directories.insert(parent.clone(), true);
            parent = distpath::dir(&parent);
        }
    }
    validate_link_graph(&directories, links).map_err(Error::chain)
}
