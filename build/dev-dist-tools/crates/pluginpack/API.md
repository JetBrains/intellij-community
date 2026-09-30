# pluginpack API

The plugin remainder packer: the port of the Go package `internal/pluginpack` without its contract part, which is in
`planfile::contract`. The binary `plugin-remainder-packer` is its only producer caller, and the collector calls the two
validation functions again on the produced table.

## The subset rule

The crate executes only the shapes that the checked-in `*.dev-plan.json` files use, and it refuses every other shape
with an error that names it. `planfile` refuses most unused shapes at decode time, so its typed recipe cannot state
them. The table lists what the Go packer supported and this crate refuses.

| Refused input | Error |
| --- | --- |
| an asset of the kind `directory` | `unknown asset kind "directory"` |
| a recipe of the retired version 3, or of a version other than 1 and 2 | `the recipe must use version 1 or 2` |
| an independent tree without an independent jar of the same artifact | `remainder or native tree ownership` |
| a jar or copy mode other than 0644 and 0755, also mode zero | `unsupported file mode` |
| a layout archive named `.tgz`, or any name other than `.zip`, `.jar`, `.zip.zst` and `.tar.gz` | `unsupported layout archive` |
| a `.zip.zst` with data after its one zstd frame | `the archive holds data after its zstd frame` |
| a zip with two central-directory records of one name | `the zip repeats the entry name` |
| a destination or a tree entry that is not ASCII, or that holds `&` | the `filemeta::path_identity` error |
| a tree link that resolves through another link, or a link target with an empty segment | the `filemeta::validate_links` error |
| a remainder entry at the name of an independent file or native tree, at a parent of it, or below it | `conflicting output destination` or `conflicting output directory` |
| a gzip resource source that is not a `.zip` or a `.jar` | `a gzip resource source is a zip or jar archive` |
| a gzip resource entry that is not an `.xml` file, or that is a link | `unexpected file` |

The Go distribution transport root `.distribution-root/` does not exist. Every asset is below the plugin directory,
and the remainder writes only plugin files.

## Public items

- `plan(recipe: &contract::Recipe, catalogue: &contract::Catalogue) -> Result<Execution>`: validates the recipe
  against the catalogue without file system access. The catalogue artifacts, in their order, are the inputs.
- `Execution::write(&self, output_directory: &Path, inventory_file: &Path) -> Result<()>`: writes the remainder into
  a stage beside the output, renames the stage over the output, and then writes the inventory (filemeta version 1).
  The output must be absent or an empty real directory, and the inventory must not exist. A failure publishes nothing.
- `validate_assets(version: u32, assets: &[contract::Asset], check_directory_spellings: bool) -> Result<()>`: the
  shared asset rules (Go `ValidateAssets`). The identity is `filemeta::path_identity`, so the Go `identity` parameter
  is gone. A tree is a remainder tree or the independent native tree of a reused natives jar, and it requires version
  2. A native tree requires an independent jar of the same artifact, and it lands below the plugin directory.
- `validate_link_graph(directories: &BTreeMap<String, bool>, links: &BTreeMap<String, String>) -> Result<()>`: the link
  graph of one tree (Go `ValidateLinkGraph`). `directories` names every node and marks each directory true, with `.`
  for the root. Call `filemeta::validate_links` first, as the Go collector did. That function refuses a target that
  resolves through another link, so this function does not check it again.
- `write_gzip_resources(archives: &[PathBuf], output: &Path) -> Result<()>`: writes `<output>/<entry>.gzip` for each
  `.xml` entry of each archive. The member holds the deflate stream of the zip entry. The first archive that holds a
  name wins, and a directory entry writes nothing.
- `Error`: one refusal or failure. `Display` and `Error::message()` give the Go error text. `Result<T>` is its alias.

## Archive readers

- `.zip` and `.jar`: the `zip` crate, in central-directory order. A creator platform 3 or 19 gives the Unix mode and the
  link type of an entry.
- `.zip.zst`: `ruzstd` decodes the zip into the scratch directory. The entries have mode zero and no links.
- `.tar.gz`: `flate2` and the `tar` crate, in stream order, the first gzip member only. A hard link is a file with the
  bytes of its target.

## Layout modes

- A plain copy of a file gets the declared mode of its asset. Mode zero keeps the source mode.
- A plain copy of a directory gives the declared mode to its regular files. The root and every directory get 0755.
  Mode zero keeps the source modes.

## Tests

Under `cargo test`, the tests read `testdata/` from `CARGO_MANIFEST_DIR`, and under Bazel from `DDT_TESTDATA_DIR`. The
three `testdata/kotlin-*-golden-*.txt` files are the frozen output of the deleted Kotlin preparer. Never edit them.

The corpus test plans every plan file in `testdata/corpus` of `planfile`. Under Bazel, the unit test gets these files
through the `test_data` attribute with the `planfile_testdata` filegroup.
