# `component` API

The component contract that the collector, the composer and the launcher share. Each line names one `pub` item of
`src/`, with its module path. The crate root also exports `ComponentManifest` and `ComponentEntry`.

The crate implements only the inputs that the repository produces. Every other input fails with an error that names
it. The collector writes the manifests, and the composer reads them. `intellij_dev_dist.bzl` writes the metadata
catalogue with `json.encode`, and the collector reads it with `json::read`. The composer writes the local layout, and
the launcher reads it.

A host path is a `Path` or a `PathBuf`. A path inside a distribution is a `str` in slash form. A list of names is a
`&[S]` with `S: AsRef<str>`.

`filemeta::merge` checks a set of entries together, and `ComponentEntry::to_metadata` gives the entry of a manifest
entry. It is part of the contract.

## Errors

Every function returns `anyhow::Result`. An I/O or JSON error has the path as its context, so `{:#}` prints
`<path>: <error>`. A refusal is one message, and the reader of a manifest adds the path of the file as its context.

## JSON rules (`json`)

- `json::read(path: &Path) -> Result<T>`: reads and decodes a JSON file. The error names the file. The collector reads
  the metadata catalogue with it, and the composer reads the composition spec.

Every reader is plain serde. A type refuses an unknown key, and a repeated key of a struct fails. A key without
`Option` or a default is required, and `null` fails for it. An `Option` key takes `null`, and an absent `Option` key
is `None`. A quoted number or boolean fails, because no producer writes one. A repeated key inside a runfiles map
keeps its last value. A Starlark dict cannot repeat a key. `serde` also reads a JSON array in place of an object, in
the field order, where Go refuses it. No producer writes such an array.

## Paths (`paths`)

- `paths::SEPARATOR: char`: the native name separator.
- `paths::host_path(value: &str) -> Result<PathBuf>`: checks the text of a host path and gives the path with native
  separators. It refuses NUL and an empty, `.` or `..` element. Bazel writes none of them.
- `paths::to_slash(value) -> Cow<str>`: changes each native separator to a slash.
- `paths::from_slash(value) -> Cow<str>`: changes each slash to a native separator.
- `paths::absolute_path(value: impl AsRef<Path>) -> Result<PathBuf>`: the absolute path of a `host_path`. A relative
  path starts at the working directory. A path that is not UTF-8 fails.

## Component manifest v10 (`manifest`)

Only the composer reads a manifest. Every path of a manifest is ASCII after `distpath::validate_path`, so `str::cmp`
gives the order of Java `String.compareTo` for the entries and the fingerprint.

- `manifest::MANIFEST_VERSION: i32`: 10.
- `ComponentManifest { version, kind, platform_prefix, os, arch, plugin, main_class, core_class_path, entries }`: one
  manifest. JSON states every key. `main_class` is an `Option<String>`. `plugin: bool` is true for a plugin
  component.
- `ComponentEntry`: one entry, with the JSON `type` first. `ComponentFile { relative_path, hash, executable, source,
  mode }` is `component-file`. JSON omits a `false` `executable` and a `None` `mode`. `Directory { relative_path,
  mode }` is `directory`, and `Symlink { relative_path, hash, symlink_target }` is `symlink`. Another type, a missing
  key and a key of another type fail.
- `ComponentEntry::relative_path() -> &str`, `type_name() -> &'static str`, `hash() -> i64` (0 for a directory),
  `executable() -> bool`: the fields that the fingerprint reads.
- `ComponentManifest::platform_neutral() -> bool`: `os` and `arch` are empty.
- `ComponentManifest::to_json() -> Vec<u8>`: two-space indent, no trailing newline.
- `ComponentEntry::to_metadata() -> filemeta::Entry`: the inventory entry for `filemeta::merge`. A file without a
  mode gets the conventional mode.
- `manifest::conventional_mode(executable: bool) -> u32`: 0o755 or 0o644. A file without a mode has this mode.
- `manifest::read_component_manifest(path) -> Result<ComponentManifest>`: checks the version first, then decodes and
  applies `validate_manifest`. A manifest of another version fails with its version, and a manifest without a version
  is version 9.
- `manifest::validate_manifest(manifest) -> Result<()>`: checks the version and `validate_entry_mode` for each
  entry. Each `core_class_path` value must be a `component-file` entry of the same manifest.
- `manifest::write_component_manifest(path, manifest) -> Result<()>`: writes the `to_json` bytes and creates the
  parent directory.
- `manifest::validate_entry_mode(entry) -> Result<()>`: the value rules of the Kotlin `validateDevBuildEntryMode`. A
  directory mode is at most 0o777. A file with a mode has the executable flag of that mode.
- `manifest::logical_component_mode(mode: u32) -> u32`: maps `0o444` to `0o644` and `0o555` to `0o755`. It keeps
  every other mode.

## Plugin classpath (`plugin_classpath`)

The collector writes and checks the record of each plugin component. The composer joins the records into this file.

- `plugin_classpath::PLUGIN_CLASSPATH: &str`: `plugins/plugin-classpath.txt`.

## Core classpath (`classpath`)

- `classpath::order_core_classpath_entries(entries) -> Vec<String>`: the leading jars in a fixed order, then bytewise
  order. For an ASCII jar name, this is the Java order on every platform.
- `classpath::core_classpath_text(entries) -> String`: the entries joined by `\n`, without a trailing newline.

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
