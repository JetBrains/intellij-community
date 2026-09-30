# planfile API

The Rust port of the Go packages `internal/planfile`, the contract part of `internal/pluginpack` (`contract.go`), and
`internal/pluginclasspath`. The crate does no file system work except `read` and `json::read`.
The crate depends on `distpath`, `serde`, `serde_json` and `thiserror` only.

`Plan` and `Execution` of `pluginpack/plan.go` are not here. They belong to the `pluginpack` crate. `ValidateAssets` and
`ValidateLinkGraph` are in `planfile::validate`, because the packer and the collector both apply them.

## The subset rule

The crate ports only the shapes that the checked-in `*.dev-plan.json` files use. It refuses every other shape with
an error that names it. The table lists what the Go code supports and the port refuses.

`testdata/corpus/` holds a copy of each of these files, without the Starlark test fixture. The corpus test reads and
derives every copy, and it requires every accepted source kind and operation kind to occur. Its catalogue declares a
library for each library source and for each `@<repository>//:<name>` input of a layout-assets operation, as the
generated catalogue does. A plan author who needs a new shape updates the corpus and the contract together.

| Refused input | Error |
| --- | --- |
| an asset with `symlinkTarget`, `normalizeTreeModes` or `scope`, or of the kind `directory` | unknown field, or the kind |
| an asset mode other than 0644 and 0755 | the mode |
| a recipe asset that also states `inputs` | inputs and a recipe |
| a plan file of the retired version 3, or a version other than the one of its assets | stale execution version |
| a tree `native-tree:<module>` whose module has no reused natives jar | requires its reused natives jar |
| a jar source of the kind `zip`, or a kind with another filter than its one filter | the kind, or the filter |
| a source option other than `patch`, and a `file` source without `patch` and an entry | the entry and the options |
| `preparedManifest`, and the writer keys `rewriteBootClassPath` and `outputName` | unknown field |
| the writer key `directoryEntries` with `true`; `false`, `null` and an absent key pass | the destination and `directoryEntries` |
| a writer manifest other than `single-meaningful-source`, `keep` and `drop` | unknown variant |
| `preparationRoots`, `alwaysRun`, and every field of a Kotlin-executed operation | unknown field |
| an operation kind other than `layout-assets`, for example the retired `module-filter` | the kind |
| an operation manifest other than `keep` | the manifest |
| a layout format other than `tree` and `entries`, or a transform kind other than `archive-tree`, such as the removed `tree-map` and `gzip-xml-archive` | unknown variant |
| the `tree-map` fields `excludes` and `directoryExcludes` on a transform | unknown field |
| a preparation that reads the output of a preparation | no preparation chain |
| a plugin directory that is not `plugins/<name>` | the directory |
| a plugin classpath name that is not ASCII, or that holds NUL | the name |
| a refused module that no asset of the plan merges, an empty one, or one named twice | the module |

## Crate root: the plan file (Go `planfile.go`, `compile.go`)

- `Error`: one refusal or I/O failure. `Display` gives the message. `Error::message(&self) -> &str`.
- `DEFAULT_MODE: u32 = 0o644`, `EXECUTABLE_MODE: u32 = 0o755`: the two asset modes.
- `PlanFile { version: u32, plugin, layout_signature, assets: Vec<Asset>, preparations: Vec<Preparation>, operations: Vec<Operation> }`: one decoded plan file in its full form.
- `Asset { destination, inputs: Vec<String>, recipe: Option<JarRecipe>, mode: u32, kind, class_path: bool }`: one plan asset below the plugin directory. `kind` is `file` or `tree`. The native tree of a reused natives jar is a tree next to its jar. The inputs of a jar asset are the inputs of its recipe sources.
- `JarRecipe { sources: Vec<JarSource>, writer: JarWriter }`: the canonical recipe of one jar.
- `JarSource { input, kind, entry }`: one ordered jar source. `kind` is `module`, `library`, `archive`, `file` or `prepared`. Only a `file` source has an entry, and the jar writer patches that file into the jar.
- `JarWriter { manifest: ManifestPolicy, merge_entities: bool, native_lib }`: the writer options. An empty `native_lib` means none. A plan jar has no directory entries.
- `ManifestPolicy::{SingleMeaningfulSource, Keep, Drop}`: the manifest policy of a jar writer. The default is `SingleMeaningfulSource`.
- `Preparation { id, inputs, outputs, model_signature }`: one preparation definition.
- `Operation { id, kind, inputs: Vec<contract::Reference>, output, layout_assets: LayoutAssetPreparation }`: one preparation operation. `kind` is `layout-assets`, the one kind. The packer keeps the manifest of every operation output.
- `LayoutAssetPreparation { format: LayoutFormat, root, assets: Vec<contract::LayoutAsset> }`: the `layoutAssets` payload.
- `LayoutFormat::{Tree, Entries}`: a tree under the root, or the entries of one jar.
- `read(path: &Path) -> Result<PlanFile, Error>`: reads, decodes and expands one plan file. Errors start with the path.
- `from_slice(data: &[u8]) -> Result<PlanFile, Error>`: the same over bytes.
- `module_jar_recipe(module: &str) -> JarRecipe`: the recipe of a module's own jar.
- `module_jar_asset(module: &str) -> Asset`: the asset of a module's own jar at `lib/modules/<module>.jar`.
- `Derivation { recipe: contract::Recipe, assets: Vec<contract::Asset>, class_path: Vec<u8>, catalogue: contract::Catalogue }`: the execution contract of one chain. `catalogue` keeps the artifacts and drops the libraries.
- `derive(file: &PlanFile, catalogue: &contract::Catalogue, plugin_directory: &str, descriptor: &[u8], execution_version: u32, independent_modules: &[String], refused_modules: &[String]) -> Result<Derivation, Error>`: compiles the plan file for the packer (Go `planfile.Derive`).
  It does not plan the recipe. The caller passes `recipe` and `catalogue` to the `pluginpack` plan step.
  Where Go reports the first problem in map order, `derive` reports the first one in plan or catalogue order.
  `refused_modules` names the content modules that the product mode of the chain refuses. An asset that `omitted_assets` marks is omitted: it has no row, no classpath jar and no operation, and `catalogue` of the derivation drops the inputs that only an omitted asset reads. The Starlark catalogue still lists them.
- `omitted_assets(file: &PlanFile, refused_modules: &[String]) -> Result<Vec<bool>, Error>`: whether each asset of the file, in plan order, is omitted for the refused modules.
  The modules of an asset are its `module` sources and the module of a reused native tree. A `prepared` source has none. An asset with at least one module, all of them refused, is omitted. An asset that merges a refused module with a kept one stays whole, and an asset without a module is never omitted. The packer and the runtime layout tool both read this answer.

The plan-file types derive `Clone`, `Debug`, `PartialEq`, `Eq`. Only `read` and `from_slice` make them from JSON.

## `planfile::contract` (Go `pluginpack/contract.go`)

The recipe stays in the process, so it has no JSON form. The asset rows go to `assets.json`, and Starlark writes the
catalogue.

- `VERSION: u32 = 1`, `TREE_VERSION: u32 = 2`: the execution versions. Version 2 has a tree, and a reused native tree is such a tree. The retired version 3 had the distribution scope. The remainder writes only plugin files, so no transport root exists.
- `Recipe { version: u32, plugin, layout_signature, assets: Vec<Asset>, operations: Vec<Operation> }`.
- `Asset { destination, producer, artifact, kind, class_path: Option<bool> }`: one row of `assets.json` (`Serialize`, `Deserialize`). `producer` is `remainder` or `independent`. An empty `kind` is `file`. A row has no scope, because every asset is below the plugin directory. `serde_json::to_vec(&rows)` writes the bytes of Go `json.Marshal`, because no plan file holds `<`, `>`, `&`, U+2028 or U+2029.
- `Catalogue { version: u32, artifacts: Vec<Artifact>, libraries: Vec<Library> }` (`Deserialize`).
- `Artifact { id, kind, root }`: `kind` is `file` or `directory`.
- `Library { id, files: Vec<Reference> }`.
- `Reference { artifact, path }`: `path` is empty for a whole artifact. It is `Hash` and `Eq`.
  `Reference::artifact(id: impl Into<String>) -> Reference` makes the reference of a whole artifact.
- `enum Operation`: one remainder operation at its destination in the plugin directory. `Operation::destination(&self) -> &str`.
  - `Jar { destination, mode: u32, sources: Vec<Source>, merge_entities: bool }`: the packer writes no directory entries into the jar.
  - `Copy { destination, mode: u32, input: Reference }`: one declared file.
  - `CopyTree { destination, input: Reference }`: one declared directory with its source modes.
  - `LayoutTree { destination, layout: LayoutAssets }`: the layout assets under the destination, with their source modes.
- `enum Source`: one jar source.
  - `Archive { input: Reference, filter: Filter, manifest: Manifest }`: the entries of one archive through the filter.
  - `Patch { entry, input: Reference, manifest: Manifest }`: one file at the entry name. The jar writer patches it.
  - `Layout(LayoutAssets)`: the entries of the layout assets. It keeps their manifests.
- `Filter::{Module, Library}`, `Manifest::{Keep, Drop}`.
- `LayoutAssets { inputs: Vec<Reference>, assets: Vec<LayoutAsset> }`.
- `LayoutAsset { destination, sources: Vec<usize>, transform: Option<LayoutTransform>, mode: u32 }` (`Deserialize`). No transform is a plain copy. Mode zero keeps the source mode.
- `LayoutTransform { kind: LayoutTransformKind, strip_components: u32, mappings: Vec<LayoutMapping>, includes, executables: Vec<String> }` (`Deserialize`).
- `LayoutTransformKind::{ArchiveTree}`. A tree needs no transform: a plain copy places it.
- `LayoutMapping { pattern, strip_components: u32, destination }` (`Deserialize`). An empty pattern is `**`.

## `planfile::validate` (Go `ValidateAssets` and `ValidateLinkGraph` of `pluginpack/plan.go`)

The rules read no file. The remainder packer applies them in its plan step. The collector applies them again to the
produced table and inventory in a second process, because it does not trust the producer. A refusal names the asset or
the link.

| Refused input | Error |
| --- | --- |
| an asset row of the kind `directory`, or of a kind other than `file` and `tree` | `unknown asset kind`, for example `unknown asset kind "directory"` |
| a tree in a table of version 1 | `requires version 2` |
| an independent tree without an independent jar of the same artifact | `remainder or native tree ownership` |
| a tree with a `classPath` other than `false` | `and classPath false` |
| a file asset at the plugin root | `only a declared tree can target the plugin root` |
| two destinations with one `distpath::path_identity` | `destination collision` |
| two spellings of one parent directory, when the caller asks for the check | `conflicting directory spellings` |
| a destination that `distpath::validate_relative_path` refuses | the `distpath` error |
| two link-graph names that differ only in case | `ambiguous path casing in link graph` |
| a link-graph node below a name that is not a directory | `missing directory` |
| a link that goes through a file, above the root, or to a missing name | `traverses a non-directory`, `escapes the plugin`, `unresolved symlink target` |
| a directory cycle through links | `symlink directory cycle` |

- `asset_kind(asset: &contract::Asset) -> &str`: the kind of a row. An empty kind is `file`.
- `validate_assets(version: u32, assets: &[contract::Asset], check_directory_spellings: bool) -> Result<(), Error>`: the
  shared asset rules. The identity is `distpath::path_identity`, so the Go `identity` parameter is gone. A tree is a
  remainder tree or the independent native tree of a reused natives jar, and it requires version 2. A native tree
  requires an independent jar of the same artifact, and it lands below the plugin directory.
- `validated_assets(version, assets, check_directory_spellings) -> Result<BTreeMap<String, &contract::Asset>, Error>`:
  the same, with each asset keyed by the identity of its destination. The plan step of `pluginpack` reads the map.
- `validate_link_graph(directories: &BTreeMap<String, bool>, links: &BTreeMap<String, String>) -> Result<(), Error>`:
  the link graph of one tree. `directories` names every node and marks each directory true, with `.` for the root.
  Call `distpath::validate_links` first, as the Go collector did. That function refuses a target that resolves through
  another link, so this function does not check it again.

## `planfile::json` (Go `pluginpack.ReadJSON`)

- `read<T: DeserializeOwned>(path: &Path) -> Result<T, Error>`: Go `ReadJSON`. Errors start with the path.
- `from_slice<T: DeserializeOwned>(data: &[u8]) -> Result<T, Error>`: the same over bytes.

Both are `serde_json::from_slice`. With a `deny_unknown_fields` type, it refuses an unknown key, a repeated key, trailing
data and invalid UTF-8. A key must match its field exactly. `null` for an `Option` field is the same as an absent key,
as for a Go pointer. `null` for any other field is an error. Errors state the line and the column.

## `planfile::classpath` (Go `internal/pluginclasspath`)

- `record<S: AsRef<str>>(plugin_dir_name: &str, descriptor: &[u8], jars: &[S]) -> Result<Vec<u8>, Error>`: one record of `plugins/plugin-classpath.txt`. It refuses a name that is not ASCII.
