# `component` API

The component contract that the collector, the composer and the launcher share. Each line names one `pub` item of
`src/`, with its module path. The crate root also exports `Error`, `Result`, `ComponentManifest`, `ComponentEntry`
and `ComponentEntryType`.

The crate implements only the inputs that the repository produces. Every other input fails with an error that names
it. The collector writes the manifests, and the composer reads them. `intellij_dev_dist.bzl` writes the metadata
catalogue with `json.encode`, and the collector reads it with `json::read`. The composer writes the local layout, and
the launcher reads it.

A host path is a `&str` or a `String`, as in the Go tools. A function that opens a file takes a `&Path`. A list of
names is a `&[S]` with `S: AsRef<str>`.

`filemeta::merge` checks a set of entries together, and `ComponentEntry::to_metadata` gives the entry of a manifest
entry. It is part of the contract.

## Errors (`error`)

- `Error`: `Message(String)`, `Io { path: String, source }`, `Json { path: String, source }` or
  `Planfile(planfile::Error)`. A caller keeps a `filemeta` error as a `Message` with the `{:#}` text.
- `Error::msg(message: impl Display)`, `Error::io(path, source)`, `Error::json(path, source)`: make an error.
- `component::fail!(format, arguments...)`: returns `Err(Error::Message(format!(...)))` from a function that returns
  `Result`.
- `Result<T>`: `std::result::Result<T, Error>`.

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
- `paths::host_path(value) -> Result<String>`: checks a host path and gives it with native separators. It refuses
  NUL and an empty, `.` or `..` element. Bazel writes none of them.
- `paths::to_slash(value) -> Cow<str>`: changes each native separator to a slash.
- `paths::from_slash(value) -> Cow<str>`: changes each slash to a native separator.
- `paths::absolute_path(value) -> Result<String>`: the absolute path of a host path. A relative path starts at the
  working directory.
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
