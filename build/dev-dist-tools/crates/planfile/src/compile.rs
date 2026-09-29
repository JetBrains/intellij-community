//! The compilation of a plan file into the execution contract of one chain. The contract holds the recipe, the asset
//! rows, the plugin classpath record and the remainder catalogue.

use std::collections::{HashMap, HashSet};

use crate::contract::{
    self, Artifact, Catalogue, DISTRIBUTION_SCOPE, Filter, LayoutAssets, Library, Manifest, PLUGIN_SCOPE, Recipe, Reference,
    SCOPED_VERSION, Source, TREE_VERSION, VERSION,
};
use crate::plan::{Asset, DEFAULT_MODE, JarRecipe, LayoutFormat, ManifestPolicy, Operation, PlanFile, Preparation, module_jar_recipe};
use crate::{Error, classpath, fail};

/// The prefix of a module that holds only a library. Such a module is no meaningful jar source.
const LIBRARY_PREFIX: &str = "intellij.libraries.";

/// The input of the native tree of a reused `content_module_jar`: `native-tree:<module>`.
const NATIVE_TREE_INPUT_PREFIX: &str = "native-tree:";

/// The execution contract of one chain, derived from the plan file. The catalogue is the input catalogue without its
/// libraries. The derivation expands every library into its files, so the remainder input set holds only files and
/// directories.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Derivation {
    pub recipe: Recipe,
    pub assets: Vec<contract::Asset>,
    pub class_path: Vec<u8>,
    pub catalogue: Catalogue,
}

/// Compiles the plan file for the packer. `catalogue` is the Starlark input catalogue of the chain.
/// `plugin_directory` is `plugins/<name>`, and `descriptor` is the classpath descriptor in its final byte form.
/// `execution_version` is the version that the chain declares. It must equal the version of the file and of its assets.
///
/// `independent_modules` names the modules whose jar the chain reuses from a `content_module_jar` target. An asset in
/// the shape of such a module jar is independent, and its artifact is the module name. The generator has matched the
/// whole recipe against the target before it names the module.
///
/// `refused_modules` names the content modules that the product mode of the chain refuses. An asset whose every module
/// is refused is omitted, see [`omitted_assets`]: the packer does not write it, and no asset row, classpath jar or
/// operation names it. The catalogue of the derivation drops the inputs that only an omitted asset reads.
pub fn derive(
    file: &PlanFile,
    catalogue: &Catalogue,
    plugin_directory: &str,
    descriptor: &[u8],
    execution_version: u32,
    independent_modules: &[String],
    refused_modules: &[String],
) -> Result<Derivation, Error> {
    let Some(plugin_dir_name) = plugin_directory
        .strip_prefix("plugins/")
        .filter(|name| !matches!(*name, "" | "." | "..") && !name.contains(['/', '\\']))
    else {
        fail!("the plugin directory {plugin_directory:?} is not plugins/<name>");
    };
    let version = execution_version_of(&file.assets);
    if file.version != version || execution_version != version {
        fail!(
            "plugin {:?} has a stale execution version: file={} declared={execution_version} required={version}; regenerate the dev distribution declarations",
            file.plugin,
            file.version
        );
    }
    let plugin_context = |error: Error| error.context(format_args!("plugin {:?}", file.plugin));
    let omitted = omitted_assets(file, refused_modules).map_err(plugin_context)?;
    let mut compiler = Compiler::new(file, independent_modules, omitted);
    compiler.plan().map_err(plugin_context)?;
    compiler.bind_operations().map_err(plugin_context)?;
    compiler.index_catalogue(catalogue).map_err(plugin_context)?;
    compiler.resolve_operation_inputs().map_err(plugin_context)?;
    let assets = compiler.asset_rows();
    let operations = compiler.operations().map_err(plugin_context)?;
    let class_path = classpath::record(plugin_dir_name, descriptor, &compiler.class_path_jars())?;
    Ok(Derivation {
        recipe: Recipe {
            version,
            plugin: file.plugin.clone(),
            layout_signature: file.layout_signature.clone(),
            assets: assets.clone(),
            operations,
        },
        assets,
        class_path,
        catalogue: Catalogue {
            version: catalogue.version,
            artifacts: compiler.executed_artifacts(catalogue),
            libraries: Vec::new(),
        },
    })
}

/// The modules of one asset: the `module` sources of its recipe and the module of a reused native tree. A library jar,
/// a file copy, a layout tree and a prepared source have none.
fn asset_modules(asset: &Asset) -> Vec<&str> {
    if let Some(module) = native_tree_module(asset) {
        return vec![module];
    }
    let Some(recipe) = &asset.recipe else {
        return Vec::new();
    };
    let mut modules = Vec::new();
    for source in &recipe.sources {
        if source.kind == "module" {
            push_new(&mut modules, &source.input);
        }
    }
    modules
}

/// Whether each asset of `file`, in plan order, is omitted for a product mode that refuses `refused_modules`.
///
/// An asset is omitted when it has at least one module and every one of them is refused. An asset that merges a refused
/// module with a kept one stays whole: the run time refuses the module, and the class loader fence keeps its packages
/// out of reach. A refused module that no asset merges is a stale declaration and fails, as an unmatched independent
/// module does. The packer and the runtime layout tool both read this answer, so the two cannot drift.
pub fn omitted_assets(file: &PlanFile, refused_modules: &[String]) -> Result<Vec<bool>, Error> {
    let mut refused = HashSet::with_capacity(refused_modules.len());
    for module in refused_modules {
        if module.is_empty() {
            fail!("a refused module requires a name");
        }
        if !refused.insert(module.as_str()) {
            fail!("refused module {module:?} is named twice");
        }
    }
    let mut matched = HashSet::new();
    let mut omitted = Vec::with_capacity(file.assets.len());
    for asset in &file.assets {
        let modules = asset_modules(asset);
        for module in &modules {
            if refused.contains(module) {
                matched.insert(*module);
            }
        }
        omitted.push(!modules.is_empty() && modules.iter().all(|module| refused.contains(module)));
    }
    if let Some(module) = refused_modules.iter().find(|module| !matched.contains(module.as_str())) {
        fail!("refused module {module:?} matches no asset of the plan; regenerate the dev distribution declarations");
    }
    Ok(omitted)
}

/// `pluginPackingExecutionVersion`: 3 with a distribution asset, 2 with a tree, else 1. A native tree of the plugin
/// scope gives 2.
fn execution_version_of(assets: &[Asset]) -> u32 {
    if assets.iter().any(|asset| asset.scope == DISTRIBUTION_SCOPE) {
        SCOPED_VERSION
    } else if assets.iter().any(|asset| asset.kind == "tree") {
        TREE_VERSION
    } else {
        VERSION
    }
}

/// The module whose jar the recipe packs in the shape of a `content_module_jar` target. Such a recipe has the module
/// output first and then only library containers. It has the default writer and mode. The generator states the rest of
/// the equality, so this function does not compare the libraries.
fn reusable_module_jar(recipe: &JarRecipe, mode: u32) -> Option<&str> {
    let mut writer = recipe.writer.clone();
    writer.native_lib.clear();
    if mode != DEFAULT_MODE || writer != module_jar_recipe("").writer {
        return None;
    }
    let (owner, libraries) = recipe.sources.split_first()?;
    (owner.kind == "module" && libraries.iter().all(|source| source.kind == "library")).then_some(&owner.input)
}

/// The module whose reused natives jar writes the tree of the asset: a tree with the one input `native-tree:<module>`.
fn native_tree_module(asset: &Asset) -> Option<&str> {
    match asset.inputs.as_slice() {
        [input] if asset.kind == "tree" => input.strip_prefix(NATIVE_TREE_INPUT_PREFIX).filter(|module| !module.is_empty()),
        _ => None,
    }
}

fn valid_id(value: &str) -> bool {
    !value.is_empty() && value.trim() == value && !value.contains(['\0', '\r', '\n'])
}

fn push_new<'a>(values: &mut Vec<&'a str>, value: &'a str) {
    if !values.contains(&value) {
        values.push(value);
    }
}

struct PlannedAsset<'a> {
    asset: &'a Asset,
    /// The module of an independent asset, or `None` for a remainder asset.
    artifact: Option<&'a str>,
    /// Whether the product mode of the chain refuses every module of the asset, see [`omitted_assets`].
    omitted: bool,
}

impl PlannedAsset<'_> {
    /// Whether the packer writes the asset: a remainder asset that is not omitted.
    const fn executed(&self) -> bool {
        self.artifact.is_none() && !self.omitted
    }
}

/// The plan file, the ownership rows and the catalogue index of one derivation.
struct Compiler<'a> {
    file: &'a PlanFile,
    independent_modules: &'a [String],
    /// The omission of each asset of the file, in plan order.
    omitted: Vec<bool>,
    assets: Vec<PlannedAsset<'a>>,
    /// The preparations of the remainder assets in first use order.
    required: Vec<&'a Preparation>,
    producers: HashMap<&'a str, &'a Preparation>,
    /// The raw inputs of the remainder assets and of their preparations in first use order.
    required_raw: Vec<&'a str>,
    artifacts: HashMap<&'a str, &'a Artifact>,
    libraries: HashMap<&'a str, &'a Library>,
    /// The Go-executed operations by output. The catalogue resolution replaces each one with its resolved copy.
    go_executed: HashMap<&'a str, Operation>,
}

impl<'a> Compiler<'a> {
    fn new(file: &'a PlanFile, independent_modules: &'a [String], omitted: Vec<bool>) -> Self {
        Self {
            file,
            independent_modules,
            omitted,
            assets: Vec::new(),
            required: Vec::new(),
            producers: HashMap::new(),
            required_raw: Vec::new(),
            artifacts: HashMap::new(),
            libraries: HashMap::new(),
            go_executed: HashMap::new(),
        }
    }

    /// `planPluginPacking`: the asset rules, the ownership match and the required preparations.
    fn plan(&mut self) -> Result<(), Error> {
        let file = self.file;
        if file.plugin.is_empty() {
            fail!("a plugin plan requires a plugin");
        }
        for asset in &file.assets {
            validate_asset(asset)?;
        }
        let mut independent = HashSet::with_capacity(self.independent_modules.len());
        for module in self.independent_modules {
            if module.is_empty() {
                fail!("an independent module requires a name");
            }
            if !independent.insert(module.as_str()) {
                fail!("independent module {module:?} is named twice");
            }
        }
        // A reused natives jar leaves its native entries out, and the tree of its native files is the jar's own output.
        let mut native_jars = HashSet::new();
        for asset in &file.assets {
            if let Some(recipe) = &asset.recipe
                && !recipe.writer.native_lib.is_empty()
            {
                match reusable_module_jar(recipe, asset.mode) {
                    Some(module) if independent.contains(module) => {
                        native_jars.insert(module);
                    }
                    _ => fail!(
                        "{}: a jar with a native library must be a reused content_module_jar",
                        asset.destination
                    ),
                }
            }
        }
        let mut used = HashSet::new();
        for (asset, &omitted) in file.assets.iter().zip(&self.omitted) {
            // The native tree of a reused natives jar has the plugin scope or the distribution scope.
            if let Some(module) = native_tree_module(asset) {
                if !native_jars.contains(module) {
                    fail!(
                        "{}: the native tree of {module:?} requires its reused natives jar",
                        asset.destination
                    );
                }
                used.insert(module);
                self.assets.push(PlannedAsset {
                    asset,
                    artifact: Some(module),
                    omitted,
                });
                continue;
            }
            if asset.scope != PLUGIN_SCOPE {
                fail!(
                    "{}: only a reused native tree has the distribution scope; the remainder writes only plugin files",
                    asset.destination
                );
            }
            let artifact = asset
                .recipe
                .as_ref()
                .and_then(|recipe| reusable_module_jar(recipe, asset.mode))
                .filter(|module| independent.contains(module));
            if let Some(module) = artifact {
                used.insert(module);
            }
            self.assets.push(PlannedAsset { asset, artifact, omitted });
        }
        if let Some(module) = self.independent_modules.iter().find(|module| !used.contains(module.as_str())) {
            fail!("independent module {module:?} matches no module jar asset; regenerate the dev distribution declarations");
        }
        let mut seen = HashSet::with_capacity(file.preparations.len());
        for preparation in &file.preparations {
            if !seen.insert(preparation.id.as_str())
                || preparation.id.is_empty()
                || preparation.model_signature.is_empty()
                || preparation.inputs.iter().any(String::is_empty)
            {
                fail!("invalid or repeated preparation {:?}", preparation.id);
            }
            for output in &preparation.outputs {
                if output.is_empty() || self.producers.contains_key(output.as_str()) {
                    fail!("conflicting preparation output {output:?}");
                }
                self.producers.insert(output, preparation);
            }
        }
        for preparation in &file.preparations {
            if let Some(input) = preparation.inputs.iter().find(|input| self.producers.contains_key(input.as_str())) {
                fail!(
                    "preparation {:?} reads the output {input:?} of a preparation; the packer executes no preparation chain",
                    preparation.id
                );
            }
        }
        for asset in &file.assets {
            let Some(recipe) = &asset.recipe else {
                continue;
            };
            for source in &recipe.sources {
                if source.kind == "prepared" && !self.producers.contains_key(source.input.as_str()) {
                    fail!(
                        "prepared source {:?} of {:?} has no producer in the plan file",
                        source.input,
                        asset.destination
                    );
                }
            }
        }
        let mut required_ids = HashSet::new();
        for planned in self.assets.iter().filter(|planned| planned.artifact.is_none()) {
            let asset: &'a Asset = planned.asset;
            for input in &asset.inputs {
                let Some(&preparation) = self.producers.get(input.as_str()) else {
                    push_new(&mut self.required_raw, input);
                    continue;
                };
                if required_ids.insert(preparation.id.as_str()) {
                    self.required.push(preparation);
                    for dependency in &preparation.inputs {
                        push_new(&mut self.required_raw, dependency);
                    }
                }
            }
        }
        Ok(())
    }

    /// Pairs every required preparation with its operation. It checks the operation against its definition and its
    /// consumers.
    fn bind_operations(&mut self) -> Result<(), Error> {
        let required: HashMap<&str, &Preparation> = self
            .required
            .iter()
            .map(|preparation| (preparation.id.as_str(), *preparation))
            .collect();
        let mut bound = HashSet::new();
        for operation in &self.file.operations {
            if !bound.insert(operation.id.as_str()) {
                fail!("duplicate preparation operation {:?}", operation.id);
            }
            let Some(definition) = required.get(operation.id.as_str()) else {
                fail!("unexpected preparation operation {:?}", operation.id);
            };
            let mut expected_inputs: Vec<&str> = Vec::new();
            for reference in &operation.inputs {
                push_new(&mut expected_inputs, &reference.artifact);
            }
            if definition.inputs != expected_inputs {
                fail!("preparation {:?} must declare exactly the inputs {expected_inputs:?}", operation.id);
            }
            if definition.outputs != [operation.output.as_str()] {
                fail!(
                    "preparation {:?} must declare exactly the output {:?}",
                    operation.id,
                    operation.output
                );
            }
            self.validate_consumers(operation)?;
            self.go_executed.insert(&operation.output, operation.clone());
        }
        if let Some(preparation) = self.required.iter().find(|preparation| !bound.contains(preparation.id.as_str())) {
            fail!("missing preparation operation {:?}", preparation.id);
        }
        Ok(())
    }

    /// Applies the consumer rules of the Kotlin generator. A tree output has one tree asset at its root. An entries
    /// output is a prepared jar source.
    fn validate_consumers(&self, operation: &Operation) -> Result<(), Error> {
        let mut consumers = Vec::new();
        for planned in &self.assets {
            if planned.asset.inputs.contains(&operation.output) {
                if planned.artifact.is_some() {
                    fail!(
                        "output {:?} of operation {:?} is consumed by an independent asset",
                        operation.output,
                        operation.id
                    );
                }
                consumers.push(planned.asset);
            }
        }
        let layout = &operation.layout_assets;
        if layout.format == LayoutFormat::Tree {
            if !matches!(consumers.as_slice(), [consumer] if consumer.kind == "tree" && consumer.destination == layout.root) {
                fail!(
                    "layout asset preparation {:?} requires one tree asset at {:?}",
                    operation.id,
                    layout.root
                );
            }
            return Ok(());
        }
        for consumer in consumers {
            let prepared = consumer.recipe.as_ref().is_some_and(|recipe| {
                recipe
                    .sources
                    .iter()
                    .any(|source| source.kind == "prepared" && source.input == operation.output)
            });
            if !prepared {
                fail!(
                    "output {:?} of operation {:?} requires a prepared jar source at {:?}",
                    operation.output,
                    operation.id,
                    consumer.destination
                );
            }
        }
        Ok(())
    }

    /// Checks the input catalogue against the required raw inputs.
    fn index_catalogue(&mut self, catalogue: &'a Catalogue) -> Result<(), Error> {
        if catalogue.version != VERSION {
            fail!("unsupported artifact catalogue version {}", catalogue.version);
        }
        let mut raw: HashSet<&str> = HashSet::new();
        for artifact in &catalogue.artifacts {
            if self.artifacts.contains_key(artifact.id.as_str()) || !valid_id(&artifact.id) {
                fail!("invalid or duplicate artifact ID {:?}", artifact.id);
            }
            if artifact.kind != "file" && artifact.kind != "directory" {
                fail!("unknown artifact root kind {:?}", artifact.kind);
            }
            self.artifacts.insert(&artifact.id, artifact);
            raw.insert(&artifact.id);
        }
        for library in &catalogue.libraries {
            if self.libraries.contains_key(library.id.as_str())
                || !valid_id(&library.id)
                || raw.contains(library.id.as_str())
                || library.files.is_empty()
            {
                fail!("invalid or duplicate library {:?}", library.id);
            }
            let mut seen = HashSet::with_capacity(library.files.len());
            for reference in &library.files {
                let is_file = self
                    .artifacts
                    .get(reference.artifact.as_str())
                    .is_some_and(|artifact| artifact.kind == "file");
                if !is_file || !reference.path.is_empty() || !seen.insert(reference) {
                    fail!("library {:?} requires distinct file artifacts", library.id);
                }
                raw.insert(&reference.artifact);
            }
            self.libraries.insert(&library.id, library);
            raw.insert(&library.id);
        }
        for preparation in &self.file.preparations {
            if let Some(output) = preparation.outputs.iter().find(|output| raw.contains(output.as_str())) {
                fail!("preparation {:?} output {output:?} aliases a raw catalogue ID", preparation.id);
            }
        }
        let mut expected: Vec<&str> = Vec::new();
        for input in &self.required_raw {
            match self.libraries.get(input) {
                Some(library) => expected.extend(library.files.iter().map(|reference| reference.artifact.as_str())),
                None => expected.push(input),
            }
        }
        if let Some(id) = expected.iter().find(|id| !self.artifacts.contains_key(*id)) {
            fail!("stale preparation inputs: the catalogue lacks {id:?}");
        }
        let expected: HashSet<&str> = expected.into_iter().collect();
        if let Some(artifact) = catalogue.artifacts.iter().find(|artifact| !expected.contains(artifact.id.as_str())) {
            fail!(
                "stale preparation inputs: the catalogue artifact {:?} is not an input of the plan",
                artifact.id
            );
        }
        if let Some(library) = catalogue
            .libraries
            .iter()
            .find(|library| !self.required_raw.contains(&library.id.as_str()))
        {
            fail!(
                "stale preparation inputs: the catalogue library {:?} is not an input of the plan",
                library.id
            );
        }
        Ok(())
    }

    /// Replaces a library ID in the inputs of every layout-assets operation with the references of its member files.
    /// The plan names the version-free library, and the catalogue names its files. An input stands for every member in
    /// catalogue order, and the sources of each layout asset follow the expanded positions. The plan file keeps its
    /// text.
    fn resolve_operation_inputs(&mut self) -> Result<(), Error> {
        let file = self.file;
        for operation in &file.operations {
            if operation.inputs.is_empty() {
                continue;
            }
            let context = |error: Error| error.context(format_args!("operation {:?}", operation.id));
            let mut positions: Vec<Vec<usize>> = Vec::with_capacity(operation.inputs.len());
            let mut inputs = Vec::new();
            for reference in &operation.inputs {
                let members = self.resolve_reference(reference).map_err(context)?;
                positions.push((inputs.len()..inputs.len() + members.len()).collect());
                inputs.extend_from_slice(members);
            }
            let mut resolved = operation.clone();
            resolved.inputs = inputs;
            for asset in &mut resolved.layout_assets.assets {
                let mut sources = Vec::with_capacity(asset.sources.len());
                for &source in &asset.sources {
                    let Some(expanded) = positions.get(source) else {
                        fail!(
                            "operation {:?}: layout asset {:?} names the input {source}, which the operation lacks",
                            operation.id,
                            asset.destination
                        );
                    };
                    sources.extend_from_slice(expanded);
                }
                asset.sources = sources;
            }
            self.go_executed.insert(&operation.output, resolved);
        }
        Ok(())
    }

    /// The references of the member files of a library in catalogue order, or the reference itself when it names no
    /// library.
    fn resolve_reference<'r>(&self, reference: &'r Reference) -> Result<&'r [Reference], Error>
    where
        'a: 'r,
    {
        let Some(library) = self.libraries.get(reference.artifact.as_str()) else {
            return Ok(std::slice::from_ref(reference));
        };
        if !reference.path.is_empty() {
            fail!(
                "library {:?} is not a directory: the input names the path {:?} in it",
                reference.artifact,
                reference.path
            );
        }
        Ok(&library.files)
    }

    fn library(&self, id: &str) -> Result<&'a Library, Error> {
        match self.libraries.get(id) {
            Some(library) => Ok(library),
            None => fail!("unknown library {id:?}"),
        }
    }

    /// The catalogue artifacts that an executed operation reads, in catalogue order. The Starlark catalogue lists every
    /// input of the plan, an input of an omitted asset too. The plan step requires every artifact of its catalogue to be
    /// used, so the artifacts that only an omitted asset reads leave the catalogue of the derivation.
    fn executed_artifacts(&self, catalogue: &Catalogue) -> Vec<Artifact> {
        let mut raw: Vec<&str> = Vec::new();
        for planned in self.assets.iter().filter(|planned| planned.executed()) {
            for input in &planned.asset.inputs {
                match self.producers.get(input.as_str()) {
                    Some(preparation) => preparation.inputs.iter().for_each(|dependency| push_new(&mut raw, dependency)),
                    None => push_new(&mut raw, input),
                }
            }
        }
        let mut expected: HashSet<&str> = HashSet::new();
        for input in raw {
            match self.libraries.get(input) {
                Some(library) => expected.extend(library.files.iter().map(|reference| reference.artifact.as_str())),
                None => {
                    expected.insert(input);
                }
            }
        }
        catalogue
            .artifacts
            .iter()
            .filter(|artifact| expected.contains(artifact.id.as_str()))
            .cloned()
            .collect()
    }

    /// `deriveDevPluginExecutionAssets`: the producer of every asset in plan order. A default stays empty, the way the
    /// Kotlin encoder omits it.
    fn asset_rows(&self) -> Vec<contract::Asset> {
        self.assets
            .iter()
            .filter(|planned| !planned.omitted)
            .map(|planned| {
                let asset = planned.asset;
                let producer = if planned.artifact.is_some() { "independent" } else { "remainder" };
                contract::Asset {
                    destination: asset.destination.clone(),
                    producer: producer.to_owned(),
                    artifact: planned.artifact.unwrap_or_default().to_owned(),
                    kind: if asset.kind == "file" { String::new() } else { asset.kind.clone() },
                    class_path: (!asset.class_path).then_some(false),
                    scope: if asset.scope == PLUGIN_SCOPE {
                        String::new()
                    } else {
                        asset.scope.clone()
                    },
                }
            })
            .collect()
    }

    /// The classpath jars directly under `lib/`, in plan order. Only a tree has the distribution scope.
    fn class_path_jars(&self) -> Vec<&'a str> {
        self.assets
            .iter()
            .filter(|planned| !planned.omitted)
            .map(|planned| planned.asset)
            .filter(|asset| {
                let destination = asset.destination.as_str();
                asset.class_path
                    && asset.kind == "file"
                    && destination.starts_with("lib/")
                    && destination.matches('/').count() == 1
                    && destination.ends_with(".jar")
            })
            .map(|asset| asset.destination.as_str())
            .collect()
    }

    /// Compiles one remainder operation per remainder asset.
    fn operations(&self) -> Result<Vec<contract::Operation>, Error> {
        let mut operations = Vec::new();
        for planned in self.assets.iter().filter(|planned| planned.executed()) {
            let asset = planned.asset;
            let destination = asset.destination.clone();
            let operation = if asset.kind == "tree" {
                let input = asset.inputs[0].as_str();
                if let Some(operation) = self.go_executed.get(input) {
                    contract::Operation::LayoutTree {
                        destination,
                        layout: LayoutAssets {
                            inputs: operation.inputs.clone(),
                            assets: operation.layout_assets.assets.clone(),
                        },
                    }
                } else {
                    if self.artifacts.get(input).is_none_or(|artifact| artifact.kind != "directory") {
                        fail!("tree {:?} requires a directory artifact", asset.destination);
                    }
                    contract::Operation::CopyTree {
                        destination,
                        input: Reference::artifact(input),
                    }
                }
            } else if let Some(recipe) = &asset.recipe {
                contract::Operation::Jar {
                    destination,
                    mode: asset.mode,
                    sources: self.compile_sources(recipe).map_err(|error| error.context(&asset.destination))?,
                    merge_entities: recipe.writer.merge_entities,
                    directory_entries: recipe.writer.directory_entries,
                }
            } else {
                let [input] = asset.inputs.as_slice() else {
                    fail!("completed asset {:?} requires one declared file", asset.destination);
                };
                contract::Operation::Copy {
                    destination,
                    mode: asset.mode,
                    input: Reference::artifact(input.as_str()),
                }
            };
            operations.push(operation);
        }
        Ok(operations)
    }

    /// The Kotlin `compileSources`: it turns the recipe sources into the sources the packer reads. A prepared source of
    /// a Go-executed operation becomes the layout source.
    fn compile_sources(&self, recipe: &JarRecipe) -> Result<Vec<Source>, Error> {
        let mut meaningful = 0usize;
        for source in &recipe.sources {
            meaningful += match source.kind.as_str() {
                "prepared" if recipe.writer.manifest == ManifestPolicy::SingleMeaningfulSource => {
                    fail!("prepared sources require an explicit manifest policy")
                }
                "module" if source.input.starts_with(LIBRARY_PREFIX) => 0,
                "library" => self.library(&source.input)?.files.len(),
                _ => 1,
            };
        }
        let manifest = match recipe.writer.manifest {
            ManifestPolicy::SingleMeaningfulSource if meaningful == 1 => Manifest::Keep,
            ManifestPolicy::SingleMeaningfulSource | ManifestPolicy::Drop => Manifest::Drop,
            ManifestPolicy::Keep => Manifest::Keep,
        };
        let mut sources = Vec::with_capacity(recipe.sources.len());
        for source in &recipe.sources {
            let input = Reference::artifact(source.input.as_str());
            match source.kind.as_str() {
                "prepared" => {
                    let Some(operation) = self.go_executed.get(source.input.as_str()) else {
                        fail!("prepared source {:?} has no Go-executed producer in the plan file", source.input);
                    };
                    sources.push(go_executed_source(operation)?);
                }
                "library" => {
                    for reference in &self.library(&source.input)?.files {
                        sources.push(self.archive_source(reference.clone(), Filter::Library, manifest)?);
                    }
                }
                "file" => sources.push(Source::Patch {
                    entry: source.entry.clone(),
                    input,
                    manifest,
                }),
                kind => {
                    if self.libraries.contains_key(source.input.as_str()) {
                        fail!(
                            "archive source {:?} names a library; a library source merges its members",
                            source.input
                        );
                    }
                    let filter = if kind == "module" { Filter::Module } else { Filter::Library };
                    sources.push(self.archive_source(input, filter, manifest)?);
                }
            }
        }
        Ok(sources)
    }

    fn archive_source(&self, input: Reference, filter: Filter, manifest: Manifest) -> Result<Source, Error> {
        if !self.artifacts.contains_key(input.artifact.as_str()) {
            fail!("unresolved input {:?}", input.artifact);
        }
        Ok(Source::Archive { input, filter, manifest })
    }
}

fn validate_asset(asset: &Asset) -> Result<(), Error> {
    let destination = asset.destination.as_str();
    if destination.is_empty() && !(asset.kind == "tree" && asset.scope == PLUGIN_SCOPE) {
        fail!("only a declared tree can target the plugin root");
    }
    if asset.inputs.iter().any(String::is_empty) {
        fail!("asset {destination:?} has an empty input");
    }
    if asset.kind == "tree" && (asset.inputs.len() != 1 || asset.recipe.is_some() || asset.class_path || asset.mode != DEFAULT_MODE) {
        fail!("plugin tree {destination:?} requires one directory input, no jar recipe, no classpath, and no mode override");
    }
    Ok(())
}

/// The jar source that the packer executes in place of the prepared output of a Go-executed operation.
fn go_executed_source(operation: &Operation) -> Result<Source, Error> {
    let layout = &operation.layout_assets;
    if layout.format != LayoutFormat::Entries {
        fail!("prepared source {:?} requires a layout-assets entries operation", operation.output);
    }
    Ok(Source::Layout(LayoutAssets {
        inputs: operation.inputs.clone(),
        assets: layout.assets.clone(),
    }))
}
