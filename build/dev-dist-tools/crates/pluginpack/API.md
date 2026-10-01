# pluginpack API

The plugin remainder packer: the port of the Go package `internal/pluginpack` without its contract part, which is in
`planfile::contract`. The binary `plugin-remainder-packer` is its only caller. The asset rules and the link-graph rules
of its plan step are in `planfile::validate`, because the collector applies them again to the produced table.

## The subset rule

The crate executes only the shapes that the checked-in `*.dev-plan.json` files use, and it refuses every other shape
with an error that names it. `planfile` refuses most unused shapes at decode time, so its typed recipe cannot state
them. The table lists what the Go packer supported and this crate refuses.

| Refused input | Error |
| --- | --- |
| a recipe of the retired version 3, or of a version other than 1 and 2 | `the recipe must use version 1 or 2` |
| a jar or copy mode other than 0644 and 0755, also mode zero | `unsupported file mode` |
| a layout archive named `.tgz`, or any name other than `.zip`, `.jar`, `.zip.zst` and `.tar.gz` | `unsupported layout archive` |
| a `.zip.zst` with data after its one zstd frame | `the archive holds data after its zstd frame` |
| a zip with two central-directory records of one name | `the zip repeats the entry name` |
| a destination or a tree entry that is not ASCII, or that holds `&` | the `distpath::path_identity` error |
| a tree link that resolves through another link, or a link target with an empty segment | the `distpath::validate_links` error |
| a remainder entry at the name of an independent file or native tree, at a parent of it, or below it | `conflicting output destination` or `conflicting output directory` |
| a gzip resource source that is not a `.zip` or a `.jar` | `a gzip resource source is a zip or jar archive` |
| a gzip resource entry that is not an `.xml` file, or that is a link | `unexpected file` |
| a jar writer with `directoryEntries` | the `planfile` error ``unknown field `directoryEntries` `` |

A jar operation writes no directory record. A directory of a non-class file is an index row of `__index__`.

The Go distribution transport root `.distribution-root/` does not exist. Every asset is below the plugin directory,
and the remainder writes only plugin files.

## Errors

Every function returns `anyhow::Result`. `{:#}` prints the text of the Go error with its context, and a refusal of
`distpath` or `planfile::validate` keeps its text. The doc comments of `plan`, `Execution::write` and
`write_gzip_resources` state their contracts.

## Archive readers

- `.zip` and `.jar`: the `zip` crate, in central-directory order. A creator platform 3 or 19 gives the Unix mode and the
  link type of an entry.
- `.zip.zst`: `ruzstd` decodes the zip into the scratch directory. The entries have mode zero and no links.
- `.tar.gz`: `flate2` and the `tar` crate, in stream order, the first gzip member only. A hard link is a file with the
  bytes of its target.

A transform without mappings reads the archive once. With mappings, a first visit selects the mapping. A single visit
that meets a hard link scans the archive once more for every target, and reads it again up to the link.

## Layout modes

- A plain copy of a file gets the declared mode of its asset. Mode zero keeps the source mode.
- A plain copy of a directory gives the declared mode to its regular files. The root and every directory get 0755.
  Mode zero keeps the source modes.

## Tests

Under `cargo test`, the tests read `testdata/` from `CARGO_MANIFEST_DIR`, and under Bazel from `DDT_TESTDATA_DIR`. The
three `testdata/kotlin-*-golden-*.txt` files are the frozen output of the deleted Kotlin preparer. Never edit them.

The corpus test plans every plan file in `testdata/corpus` of `planfile`. Under Bazel, the unit test gets these files
through the `test_data` attribute with the `planfile_testdata` filegroup.
