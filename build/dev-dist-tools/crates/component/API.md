# `component` API

The component contract that the collector, the composer and the launcher share. Each line names one `pub` item of
`src/`, with its module path. The crate root also exports `Error`, `Result`, `ComponentManifest`, `ComponentEntry`
and `ComponentEntryType`.

The crate implements only the inputs that the repository produces. Every other input fails with an error that names
it. The collector writes the manifests. `intellij_dev_dist.bzl` writes the composition spec, the source bindings and
the metadata catalogue with `json.encode`. The composer writes the local layout.

A host path is a `&str` or a `String`, as in the Go tools. A function that opens a file takes a `&Path`, except
`spec::read_source_bindings` and the `ide_config` functions. They take the file as a host path. A list of names is a
`&[S]` with `S: AsRef<str>`.

These items of other crates are part of the contract:

- `distpath::validate_path` checks a path inside a distribution: relative, in slash form, ASCII without `<`, `>` and
  `&`. `filemeta::merge` checks a set of entries together.
- `fscopy::conventional_mode(executable)` is the mode of a file without a mode. `fscopy::set_distribution_file_mode`
  applies a mode.

## Errors (`error`)

- `Error`: `Message(String)`, `Io { path: String, source }`, `Json { path: String, source }`,
  `Metadata(filemeta::Error)` or `Planfile(planfile::Error)`.
- `Error::msg(message: impl Display)`, `Error::io(path, source)`, `Error::json(path, source)`: make an error.
- `component::fail!(format, arguments...)`: returns `Err(Error::Message(format!(...)))` from a function that returns
  `Result`.
- `Result<T>`: `std::result::Result<T, Error>`.

## JSON rules (`json`)

- `json::read(path: &Path) -> Result<T>`: reads and decodes a JSON file. The error names the file. The collector reads
  the metadata catalogue with it.

Every reader is plain serde. A type refuses an unknown key, and a repeated key of a struct fails. A key without
`Option` or a default is required, and `null` fails for it. An `Option` key takes `null`, and an absent `Option` key
is `None`. A quoted number or boolean fails, because no producer writes one. A repeated key inside a runfiles map
keeps its last value. A Starlark dict cannot repeat a key. `serde` also reads a JSON array in place of an object, in
the field order, where Go refuses it. No producer writes such an array.

## Paths (`paths`)

- `paths::SEPARATOR: char`: the native name separator.
- `paths::host_path(value) -> Result<String>`: checks a host path and gives it with native separators. It refuses
  NUL and an empty, `.` or `..` element. Bazel writes none of them.
- `paths::to_slash(value) -> Cow<str>`: changes each native separator to a slash.
- `paths::from_slash(value) -> Cow<str>`: changes each slash to a native separator.
- `paths::absolute_path(value) -> Result<String>`: the absolute path of a host path. A relative path starts at the
  working directory.
- `paths::real_path(value) -> Result<String>`: the absolute path of a host path with every symbolic link resolved.
  The path must exist.
- `paths::eval_symlinks(path) -> Result<String>`: an absolute path with every symbolic link resolved. The path must
  exist.
- `paths::parent(value) -> &str`: the parent directory of an absolute host path. The root is its own parent.
- `paths::resolve_relative(base, relative) -> String`: Java `base.resolve(relative)` for a relative path in slash
  form.
- `paths::compare_utf16(first, second) -> Ordering`: Java `String.compareTo`. The manifest entries and the
  fingerprint use this order.

## Component manifest v9 (`manifest`)

- `manifest::MANIFEST_VERSION: i32`: 9.
- `ComponentEntryType { ComponentFile, Directory, Symlink }`: the JSON `type`, which is `component-file`,
  `directory` or `symlink`. Another type fails.
- `ComponentEntryType::as_str() -> &'static str`: the JSON text of the type.
- `ComponentManifest { version, kind, platform_prefix, os, arch, additional_modules, main_class, core_class_path,
  entries, plugin_count }`: one manifest, in the field order of the Go collector. `version: Option<i32>` is absent
  except for a plugin component. `main_class` is an `Option<String>`. `plugin_count: u32` is 0 or 1, and JSON omits
  0. `additional_modules` is always empty.
- `ComponentEntry { relative_path, entry_type, hash, executable, source, symlink_target, mode }`: one entry.
  `hash: Option<i64>`, `source: Option<String>`, `symlink_target: Option<String>` and `mode: Option<u32>`. JSON omits
  a `None`, an empty `source` or `symlink_target`, and a `false` `executable`.
- `ComponentManifest::platform_neutral() -> bool`: `os` and `arch` are empty.
- `ComponentManifest::effective_version() -> i32`: the version, or 9 when it is absent.
- `ComponentManifest::to_json() -> Vec<u8>`: the bytes of the Go collector: two-space indent, no HTML escape, no
  trailing newline.
- `ComponentEntry::to_metadata() -> filemeta::Entry`: the inventory entry for `filemeta::merge`. A file without a
  mode gets the conventional mode.
- `manifest::read_component_manifest(path) -> Result<ComponentManifest>`: decodes, then applies `validate_manifest`.
- `manifest::validate_manifest(manifest) -> Result<()>`: checks the version, a plugin count of at most 1, no
  additional modules, and `validate_entry_mode` for each entry. Each `core_class_path` value must be a
  `component-file` entry of the same manifest.
- `manifest::write_component_manifest(path, manifest) -> Result<()>`: writes the `to_json` bytes and creates the
  parent directory.
- `manifest::validate_entry_mode(entry) -> Result<()>`: the Kotlin `validateDevBuildEntryMode`. A directory has a
  mode and nothing else. A file with a mode has the executable flag of that mode.
- `manifest::logical_component_mode(mode: u32) -> u32`: maps `0o444` to `0o644` and `0o555` to `0o755`. It keeps
  every other mode.

## Plugin classpath (`plugin_classpath`)

The collector writes and checks the record of each plugin component. The composer joins the records.

- `plugin_classpath::PLUGIN_CLASSPATH: &str`: `plugins/plugin-classpath.txt`.
- `plugin_classpath::compose(prefix: &[u8], plugin_count: u16, parts) -> Vec<u8>`: the prefix, the big-endian count,
  then the parts in order.

## Core classpath (`classpath`)

- `classpath::order_core_classpath_entries(entries) -> Vec<String>`: the leading jars in a fixed order, then bytewise
  order. For an ASCII jar name, this is the Java order on every platform.
- `classpath::core_classpath_text(entries) -> String`: the entries joined by `\n`, without a trailing newline.

## Fingerprint v5 (`fingerprint`)

- `fingerprint::IDE_FINGERPRINT_VERSION: &str`: `v5`.
- `FingerprintEntry { relative_path, entry_type, hash, executable }`: the Kotlin `IdeFingerprintEntry`. `entry_type`
  is a `String`, and `hash` is an `i64`.
- `FingerprintEntry::new(relative_path, entry_type, hash, executable) -> FingerprintEntry`.
- `fingerprint::compute_ide_fingerprint(entries) -> String`: sorts the entries and returns `v5:<base36>`.
- `fingerprint::launch_metadata_hash(platform_prefix, os, arch, main_class, additional_modules) -> i64`: the
  `dev-launch-v1` hash of the values that `DevIdeConfig` gets.
- `fingerprint::compute_ide_fingerprint_from_components(components: &[&ComponentManifest], plugin_classpath_file:
  Option<&Path>, modules) -> Result<String>`: the fingerprint of a distribution. `modules` are the additional modules
  of the spec. The source of an entry does not enter the fingerprint.

## Composition spec v1 and source bindings (`spec`)

- `spec::COMPOSITION_SPEC_VERSION: i32`: 1.
- `CompositionSpec { version, expected_fragments, additional_modules, components, plugin_classpath_prefix,
  source_runfiles, source_directory_runfiles, source_bindings }`: the spec that `--composition-spec` names.
  `plugin_classpath_prefix` and `source_bindings` are `Option<String>`. `source_runfiles` is an
  `Option<BTreeMap<String, String>>`, and `None` requests a full distribution. `source_directory_runfiles` is a
  `BTreeMap<String, String>`.
- `CompositionComponent { manifest, plugin_classpath_part }`: one component of the spec. `plugin_classpath_part` is
  an `Option<String>`.
- `spec::read_composition_spec(path) -> Result<CompositionSpec>`: decodes, then checks the version and that there is
  a component.
- `SourceKind { File, Directory }`: the type of a staged artifact. `symlink` fails.
- `BoundSource { path, directory, kind }`: one artifact that Bazel staged, or one member of a directory artifact.
  `directory: Option<String>` is the physical root of the directory artifact, and `None` is a file artifact.
- `ComponentSources { sources: HashMap<String, BoundSource> }`: the staged sources of one component, keyed by
  absolute path.
- `ComponentSources::resolve(source) -> Result<String>`: the physical file of a staged source. It fails when the
  source is not the declared artifact.
- `spec::read_source_bindings(file: &str, components: &[CompositionComponent]) -> Result<HashMap<String,
  ComponentSources>>`: reads the source bindings JSONL. The map key is the manifest path of the component.

## Composition (`compose`)

- `compose::RESERVED_FILES: [&str; 4]`: `core-classpath.txt`, `fingerprint.txt`, `local-layout.json` and
  `plugins/plugin-classpath.txt`. The composer writes them, and no component can provide one.
- `DevBuildComponent { manifest, plugin_classpath_part: Option<PathBuf>, source_bindings: Option<ComponentSources> }`:
  one component to compose. `plugin_classpath_part` is the absolute path of its plugin records.
- `DevBuildComponent::new(manifest) -> DevBuildComponent`: a component without plugin records and without source
  bindings.
- `ComposeOptions { plugin_classpath_prefix: Option<PathBuf>, expected_fragments, additional_modules, source_runfiles,
  source_directory_runfiles }`: the optional arguments. Both runfiles maps are `Option<BTreeMap<String, String>>`.
  `source_runfiles: None` requests a full distribution. The keys of both maps are absolute, as `absolute_keys` gives
  them.
- `ComposedBuild { platform_prefix, main_class, additional_modules, core_class_path, fingerprint }`: the values of the
  IDE config, the core classpath and the fingerprint.
- `compose::validate_components(components, expected_fragments) -> Result<()>`: checks that the components form one
  distribution. It reads no file.
- `compose::validate_destinations(manifests: &[&ComponentManifest]) -> Result<()>`: checks every destination
  together, before the composer writes a file.
- `compose::compose_components(components, target: &Path, options, merge) -> Result<ComposedBuild>`: see the contract
  below.
- `compose::write_plugin_classpath(components, target: &Path, prefix: Option<&Path>) -> Result<Option<PathBuf>>`:
  writes `plugins/plugin-classpath.txt`. The count is the sum of the plugin counts. The result is `None` when no
  component has records.
- `compose::absolute_keys(map) -> Result<BTreeMap<String, String>>`: the map with absolute keys. Two keys with one
  absolute path fail.
- `compose::distinct(values) -> Vec<String>`: the first occurrence of each value, in order.

### The contract of `compose_components` and its `merge` step

`merge` has the type `FnOnce(&[DevBuildComponent], &Path) -> Result<()>`. The composer binary supplies it.

Before `merge` runs, `compose_components` has done these steps, in this order. A failure stops the composition before
it creates `target`.

1. `validate_components`: there is a component. Each manifest passes `validate_manifest`. One main class, one product
   and one target platform apply to all components. A component with a plugin count has a plugin classpath part. The
   kinds are unique, and they are the expected fragments when the spec names some.
2. `validate_destinations`: each destination is a valid distribution path, and one component provides it. No
   component provides a reserved file. `filemeta::merge` checks all entries together. Each path identity has one
   spelling, and only a directory has an entry below it. Each link is relative and stays inside the distribution. A
   link target has no empty segment, and it does not go through another link, so the links need no creation order.
3. `target` is absent or an empty directory. The function creates it.

Then, for a full distribution only, the function calls `merge` once with the components in spec order and `target`.
`merge` must:

- For each component in order, create each entry at `target` joined with its `relative_path`, and create the missing
  parent directories.
- For a directory entry, accept a directory that already exists, and fail when a file or a link is there.
- For a file or a link entry, fail when anything exists at the destination. It must never replace a file. The checks
  above make this a defect, not an input.
- For a file entry, check the `source` and copy its bytes. `paths::real_path` and `ComponentSources::resolve` refuse an
  unsafe or an unbound source. Then apply `executable` and `mode` with `fscopy::set_distribution_file_mode`.
- For a link entry, create the link with the `symlink_target` text.
- After all components, apply the mode of each directory entry, the deepest first.
- Write no reserved file.

When `merge` fails, the function returns its error, and `target` can hold a part of the distribution. Launch metadata
never calls `merge`.

After `merge`, the function writes the plugin classpath, writes `local-layout.json` for launch metadata, and computes
the fingerprint. The caller writes `core-classpath.txt` and `fingerprint.txt` into `target` from `ComposedBuild`, and
the IDE config with `ide_config::write_dev_ide_config`.

## Local layout (`layout`)

- `layout::LOCAL_LAYOUT_FILE`: `local-layout.json`.
- `layout::LOCAL_LAYOUT_VERSION: u32`: 1.
- `layout::CORE_CLASSPATH_FILE`: `core-classpath.txt`.
- `layout::FINGERPRINT_FILE`: `fingerprint.txt`.
- `LocalLayout { version: u32, files: Vec<LocalLayoutFile>, metadata: Vec<String> }`: the layout. `metadata` names the
  files that the composer writes beside the layout.
- `LocalLayoutFile { path, runfile, symlink_target, executable, mode, kind }`: one file, which is a runfile, a
  symbolic link or a directory. `runfile` and `symlink_target` are `Option<String>`, `mode` is an `Option<u32>`, and
  `kind` is an `Option<LocalFileKind>`. `serde_json` writes the Kotlin bytes: every key, `null` included, and `kind`
  only for a directory.
- `LocalFileKind { Directory }`: the JSON `kind`, which is `directory`.
- `LocalLayoutFile::is_directory() -> bool`: `kind` is `Directory`.
- `layout::read_local_layout(path) -> Result<LocalLayout>`: decodes the layout by the JSON rules. The launcher checks
  the version and the files before it links a local home.
- `layout::encode_local_layout(components: &[&ComponentManifest], source_runfiles, has_plugin_classpath: bool,
  source_directory_runfiles: Option<&BTreeMap<String, String>>) -> Result<Vec<u8>>`: applies
  `validate_destinations`, then gives the bytes.
- `layout::write_local_layout(components, target: &Path, source_runfiles, has_plugin_classpath,
  source_directory_runfiles) -> Result<()>`: writes the layout into `target`, which must exist.
- `layout::resolve_source_runfile(source, files, directories, name) -> Result<String>`: the runfile of one source,
  from an exact file declaration or from the deepest declared directory. `name` is the entry that the error names.

## Dev IDE config (`ide_config`)

- `ide_config::dev_ide_config_text(config_file: &str, home: &str, main_class: &str, platform_prefix: &str,
  additional_modules) -> String`: the text that `DevIdeConfig` reads. The text names the home relative to the config
  file when the home is below the directory of the config file.
- `ide_config::write_dev_ide_config(config_file, home, main_class, platform_prefix, additional_modules) -> Result<()>`:
  writes the text and creates the parent directory.
