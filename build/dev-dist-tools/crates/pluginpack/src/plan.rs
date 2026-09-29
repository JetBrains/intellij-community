//! The plan step: it validates the recipe against the catalogue and reads no file.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::Arc;

use javaglob::JavaGlob;
use planfile::LayoutFormat;
use planfile::contract::{
    Artifact, Asset, Catalogue, DISTRIBUTION_SCOPE, Filter, LayoutAsset, LayoutAssets, LayoutMapping, LayoutTransformKind, Operation,
    PLUGIN_SCOPE, Recipe, Reference, SCOPED_VERSION, Source, TREE_VERSION, VERSION,
};

use crate::error::{Error, Result, fail};
use crate::paths;

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

pub(crate) fn asset_kind(asset: &Asset) -> &str {
    if asset.kind.is_empty() { "file" } else { &asset.kind }
}

pub(crate) fn asset_scope(asset: &Asset) -> &str {
    if asset.scope.is_empty() { PLUGIN_SCOPE } else { &asset.scope }
}

/// The identity of a destination: `filemeta::path_identity`. It refuses a path that the inventory cannot hold, so a
/// refused name fails before any write.
pub(crate) fn identity(path: &str) -> Result<String> {
    Ok(filemeta::path_identity(path)?)
}

/// Applies the shared asset rules to one asset table. The rules cover the scope, the version, the plugin root target,
/// and the relative path. They also cover the kind, the trees, the distribution assets, and the destination collisions.
/// The directory-spellings check runs only when `check_directory_spellings` is true.
///
/// The packer calls it in [`plan`] before it writes. The collector calls it again on the produced table in a second
/// process, because the collector does not trust the producer.
pub fn validate_assets(version: u32, assets: &[Asset], check_directory_spellings: bool) -> Result<()> {
    validated_assets(version, assets, check_directory_spellings).map(|_| ())
}

/// [`validate_assets`] with the result: each asset keyed by its scope and the identity of its destination.
fn validated_assets(version: u32, assets: &[Asset], check_directory_spellings: bool) -> Result<BTreeMap<(&str, String), &Asset>> {
    let mut validated = BTreeMap::new();
    let mut spellings: HashMap<(&str, String), String> = HashMap::new();
    let mut has_distribution_assets = false;
    // The modules of the reused jars. An independent tree of the plugin scope is the native tree of one of them.
    let reused_jars: HashSet<&str> = (assets.iter())
        .filter(|asset| asset.producer == "independent" && asset_kind(asset) == "file")
        .map(|asset| asset.artifact.as_str())
        .collect();
    for asset in assets {
        let kind = asset_kind(asset);
        let scope = asset_scope(asset);
        if scope != PLUGIN_SCOPE && scope != DISTRIBUTION_SCOPE {
            fail!("unknown asset scope {:?}", asset.scope);
        }
        if scope == DISTRIBUTION_SCOPE && version != SCOPED_VERSION {
            fail!("distribution asset {:?} requires version 3", asset.destination);
        }
        // The remainder writes only plugin files. Only the native tree of a reused natives jar has the other scope.
        if scope == DISTRIBUTION_SCOPE && (asset.producer != "independent" || kind != "tree") {
            fail!(
                "distribution asset {:?} must be the native tree of a reused natives jar; the remainder writes only plugin files",
                asset.destination
            );
        }
        has_distribution_assets |= scope == DISTRIBUTION_SCOPE;
        if asset.destination.is_empty() && (kind != "tree" || scope != PLUGIN_SCOPE) {
            fail!("only a declared tree can target the plugin root");
        }
        if !asset.destination.is_empty() {
            validate_relative_path(&asset.destination)?;
        }
        if validated.insert((scope, identity(&asset.destination)?), asset).is_some() {
            fail!("destination collision at {:?}", asset.destination);
        }
        if kind != "file" && kind != "tree" {
            fail!("unknown asset kind {:?}; the packer writes only file and tree assets", asset.kind);
        }
        // A remainder tree, or the native tree of a reused natives jar. The native tree is in the plugin or at the
        // distribution root.
        let owned_tree = asset.producer == "remainder"
            || asset.producer == "independent" && (scope == DISTRIBUTION_SCOPE || reused_jars.contains(asset.artifact.as_str()));
        if kind == "tree" && (version < TREE_VERSION || !owned_tree || asset.class_path != Some(false)) {
            fail!(
                "tree {:?} requires version 2 or 3, remainder or native tree ownership, and classPath false",
                asset.destination
            );
        }
        if scope == DISTRIBUTION_SCOPE && asset.class_path != Some(false) {
            fail!("distribution asset {:?} requires classPath false", asset.destination);
        }
        if check_directory_spellings {
            let mut prefix = asset.destination.clone();
            while !prefix.is_empty() && prefix != "." {
                let spelling = (scope, identity(&prefix)?);
                if let Some(previous) = spellings.get(&spelling)
                    && *previous != prefix
                {
                    fail!("conflicting directory spellings {previous:?} and {prefix:?}");
                }
                let parent = paths::dir(&prefix);
                spellings.insert(spelling, prefix);
                prefix = parent;
            }
        }
    }
    if version == SCOPED_VERSION && !has_distribution_assets {
        fail!("version 3 requires a distribution asset");
    }
    Ok(validated)
}

/// Validates the recipe against the catalogue. The catalogue artifacts, in their order, become the execution inputs.
pub fn plan(recipe: &Recipe, catalogue: &Catalogue) -> Result<Execution> {
    if !(VERSION..=SCOPED_VERSION).contains(&recipe.version) || catalogue.version != VERSION {
        fail!("unsupported contract version; the recipe must use version 1, 2, or 3, the catalogue version 1");
    }
    if !valid_id(&recipe.plugin) || !valid_id(&recipe.layout_signature) {
        fail!("invalid plugin identity or layout signature");
    }
    let has_trees = recipe.assets.iter().any(|asset| asset_kind(asset) == "tree");
    let assets = validated_assets(recipe.version, &recipe.assets, has_trees)?;
    // The artifact of an independent asset is the module name of its reused jar. The module output can be a catalogue
    // input of the same chain under that name, so the two namespaces are not compared.
    for asset in &recipe.assets {
        match asset.producer.as_str() {
            "independent" => {
                // A file is a reused jar. A tree is the native tree of a reused natives jar, see `validated_assets`.
                if !valid_id(&asset.artifact) {
                    fail!("independent asset {:?} requires an artifact ID", asset.destination);
                }
            }
            "remainder" => {
                if !asset.artifact.is_empty() {
                    fail!("remainder asset {:?} must not name an independent artifact", asset.destination);
                }
            }
            producer => fail!("unknown producer {producer:?}"),
        }
    }
    for (scope, name) in assets.keys() {
        let mut parent = paths::dir(name);
        while parent != "." {
            if let Some(ancestor) = assets.get(&(*scope, parent.clone()))
                && asset_kind(ancestor) == "file"
            {
                fail!("destination collision between {parent:?} and {name:?}");
            }
            parent = paths::dir(&parent);
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
        if artifact.kind != "file" && artifact.kind != "directory" {
            fail!("unknown root kind {:?}", artifact.kind);
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
        let key = (PLUGIN_SCOPE, identity(destination)?);
        let asset = match assets.get(&key) {
            Some(asset)
                if !operations.contains(&key)
                    && asset.destination == destination
                    && asset_scope(asset) == PLUGIN_SCOPE
                    && asset.producer == "remainder" =>
            {
                *asset
            }
            _ => fail!("conflicting or unowned remainder destination {destination:?}"),
        };
        execution
            .validate_operation(operation, &mut used)
            .map_err(|error| error.context(destination))?;
        let writes_tree = matches!(operation, Operation::CopyTree { .. } | Operation::LayoutTree { .. });
        if (asset_kind(asset) == "tree") != writes_tree {
            fail!("stale asset kind at {destination:?}");
        }
        operations.insert(key);
    }
    for asset in &recipe.assets {
        let key = (asset_scope(asset), identity(&asset.destination)?);
        if asset.producer == "remainder" && !operations.contains(&key) {
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
    !root.is_empty() && root != "." && paths::clean(&slashed) == slashed && !root.contains(['\0', '\r', '\n'])
}

impl Execution {
    fn validate_operation(&self, operation: &Operation, used: &mut HashSet<String>) -> Result<()> {
        match operation {
            Operation::LayoutTree { layout, .. } => {
                if self.recipe.version < TREE_VERSION {
                    fail!("layout-tree requires version 2 or 3");
                }
                self.validate_layout(layout, LayoutFormat::Tree, used)
            }
            Operation::CopyTree { input, .. } => {
                if self.recipe.version < TREE_VERSION || !input.path.is_empty() {
                    fail!("copy-tree requires version 2 or 3 and one directory root");
                }
                match self.artifacts.get(&input.artifact) {
                    Some(artifact) if artifact.kind == "directory" => {
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
                jarpack::validate_entry_name(entry)?;
                self.validate_reference(input, used)
            }
        }
    }

    fn validate_reference(&self, reference: &Reference, used: &mut HashSet<String>) -> Result<()> {
        let Some(artifact) = self.artifacts.get(&reference.artifact) else {
            fail!("unresolved input {:?}", reference.artifact);
        };
        if artifact.kind == "file" {
            if !reference.path.is_empty() {
                fail!("file input {:?} cannot have a relative path", artifact.id);
            }
        } else {
            validate_relative_path(&reference.path)?;
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
        if artifact.kind == "directory" && reference.path.is_empty() {
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
        validate_relative_path(&asset.destination)?;
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
            validate_relative_path(&mapping.destination)?;
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
pub(crate) fn source_filter(filter: Filter) -> jarpack::Filter {
    let base: fn(&str) -> bool = match filter {
        Filter::Module => jarpack::module_output_name_filter,
        Filter::Library => jarpack::library_name_filter,
    };
    Arc::new(base)
}

pub(crate) fn valid_id(value: &str) -> bool {
    !value.is_empty() && value.trim() == value && !value.contains(['\0', '\r', '\n'])
}

/// Accepts a relative slash path that is a safe jar entry name and a portable file name on every host.
pub(crate) fn validate_relative_path(value: &str) -> Result<()> {
    if jarpack::validate_entry_name(value).is_err() {
        fail!("unsafe relative path {value:?}");
    }
    for component in value.split('/') {
        if component.trim_end_matches(['.', ' ']) != component
            || component.contains(['<', '>', '"', '|', '?', '*'])
            || component.chars().any(|character| (character as u32) < 0x20)
        {
            fail!("unsafe path component {component:?}");
        }
        let upper = component.to_uppercase();
        let base = upper.split('.').next().unwrap_or_default();
        let numbered = base.len() == 4 && (base.starts_with("COM") || base.starts_with("LPT")) && matches!(base.as_bytes()[3], b'1'..=b'9');
        if matches!(base, "CON" | "PRN" | "AUX" | "NUL") || numbered {
            fail!("reserved path component {component:?}");
        }
    }
    Ok(())
}

/// Checks the link graph of one directory tree. `directories` names every node and marks each directory true, and
/// `links` names each link with its target. It refuses a link through a non-directory, a link that escapes the root,
/// and a target absent from the tree. It also refuses two names that differ only in case, a parent that is not a
/// directory, and a directory cycle through links.
///
/// The caller must run `filemeta::validate_links` first, because that function refuses a target that resolves through
/// another link. The packer calls this function on the links of each tree. The collector calls it again on the
/// produced inventory in a second process, because the collector does not trust the producer.
pub fn validate_link_graph(directories: &BTreeMap<String, bool>, links: &BTreeMap<String, String>) -> Result<()> {
    let mut casing: HashMap<String, &str> = HashMap::with_capacity(directories.len());
    for name in directories.keys() {
        if let Some(previous) = casing.insert(name.to_lowercase(), name)
            && previous != name
        {
            fail!("ambiguous path casing in link graph: {previous:?} and {name:?}");
        }
    }
    let is_directory = |name: &str| directories.get(name).copied().unwrap_or(false);
    let mut edges: BTreeMap<String, Vec<String>> = BTreeMap::new();
    for (name, directory) in directories {
        if name == "." {
            continue;
        }
        let parent = paths::dir(name);
        if !is_directory(&parent) {
            fail!("missing directory {parent:?} in link graph");
        }
        if *directory {
            edges.entry(parent).or_default().push(name.clone());
        }
    }
    for (link, target_text) in links {
        let mut current = paths::dir(link);
        for component in target_text.split('/') {
            if !is_directory(&current) {
                fail!("symlink {link:?} traverses a non-directory {current:?}");
            }
            match component {
                "" | "." => continue,
                ".." => {
                    if current == "." {
                        fail!("symlink {link:?} escapes the plugin through {target_text:?}");
                    }
                    current = paths::dir(&current);
                    continue;
                }
                _ => {}
            }
            current = paths::join(&current, component);
            if !directories.contains_key(&current) {
                fail!("unresolved symlink target {target_text:?} at {current:?}");
            }
        }
        if is_directory(&current) {
            edges.entry(paths::dir(link)).or_default().push(current);
        }
    }
    let mut states: HashMap<&str, u8> = HashMap::new();
    visit_directory(".", &edges, &mut states)
}

fn visit_directory<'e>(directory: &'e str, edges: &'e BTreeMap<String, Vec<String>>, states: &mut HashMap<&'e str, u8>) -> Result<()> {
    match states.get(directory) {
        Some(1) => fail!("symlink directory cycle at {directory:?}"),
        Some(_) => return Ok(()),
        None => {}
    }
    states.insert(directory, 1);
    for child in edges.get(directory).into_iter().flatten() {
        visit_directory(child, edges, states)?;
    }
    states.insert(directory, 2);
    Ok(())
}

/// Checks the links of one scope. `nodes` holds each file and directory of the scope with its kind.
pub(crate) fn validate_scoped_links(nodes: &[(String, bool)], links: &BTreeMap<String, String>) -> Result<()> {
    if links.is_empty() {
        return Ok(());
    }
    filemeta::validate_links(links)?;
    let mut directories = BTreeMap::from([(".".to_owned(), true)]);
    for (destination, directory) in nodes {
        directories.insert(destination.clone(), *directory);
        let mut parent = paths::dir(destination);
        while parent != "." {
            directories.insert(parent.clone(), true);
            parent = paths::dir(&parent);
        }
    }
    validate_link_graph(&directories, links)
}
