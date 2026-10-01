# planfile API

The Rust port of the Go packages `internal/planfile`, the contract part of `internal/pluginpack` (`contract.go`), and
`internal/pluginclasspath`. The crate does no file system work except `read` and `json::read`.
The crate depends on `anyhow`, `distpath`, `serde` and `serde_json` only. Every function that can fail returns
`anyhow::Result`. A refusal is one message that names the plan element, and a caller adds its context, so `{:#}` prints
`<context>: <message>` as the Go `fmt.Errorf("%s: %w")` did.

`Plan` and `Execution` of `pluginpack/plan.go` are not here. They belong to the `pluginpack` crate. `ValidateAssets` and
`ValidateLinkGraph` are in `planfile::validate`, because the packer and the collector both apply them.

Rustdoc states each public item: the plan file types, `read`, `derive` and `omitted_assets` at the crate root, and the
modules `contract`, `validate`, `json` and `classpath`.

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
| `preparedManifest`, and the writer keys `rewriteBootClassPath`, `outputName` and `directoryEntries` | unknown field |
| a writer manifest other than `single-meaningful-source`, `keep` and `drop` | unknown variant |
| `preparationRoots`, `alwaysRun`, and every field of a Kotlin-executed operation | unknown field |
| `layoutSignature`, because a plan file carries no layout signature | unknown field |
| an operation kind other than `layout-assets`, for example the retired `module-filter` | the kind |
| an operation manifest other than `keep` | the manifest |
| a layout format other than `tree` and `entries`, or a transform kind other than `archive-tree`, such as the removed `tree-map` and `gzip-xml-archive` | unknown variant |
| the `tree-map` fields `excludes` and `directoryExcludes` on a transform | unknown field |
| a preparation that reads the output of a preparation | no preparation chain |
| a plugin directory that is not `plugins/<name>` | the directory |
| a plugin classpath name that is not ASCII, or that holds NUL | the name |
| a refused module that no asset of the plan merges, an empty one, or one named twice | the module |

## The rows and the catalogue (`planfile::contract`)

The recipe stays in the process, so it has no JSON form. The asset rows go to `assets.json`, and Starlark writes the
catalogue. The bytes of `assets.json` are frozen:

- `serde_json::to_vec(&rows)` writes the bytes of Go `json.Marshal`, because no plan file holds `<`, `>`, `&`, U+2028 or
  U+2029.
- A row of the kind `file` has no `kind` key, as the Go writer omitted an empty kind. A row of the kind `tree` has the
  key, and every row has its producer.
- A row has no scope, because every asset is below the plugin directory.

The readers of the rows and of the catalogue refuse a value that the enums do not hold, with the text of the Go check.
The collector and the remainder packer read these files, so both refuse with one text.

| Refused input | Error |
| --- | --- |
| an asset row of the kind `directory`, or of a kind other than `file`, `tree` and the empty text | `unknown asset kind`, for example `unknown asset kind "directory"` |
| an asset row with a producer other than `remainder` and `independent`, or without a producer | `unknown asset producer`, or `missing field` |
| a catalogue artifact with a kind other than `file` and `directory`, or without a kind | `unknown artifact root kind`, or `missing field` |

## The asset and link-graph rules (`planfile::validate`)

The rules read no file. The remainder packer applies them in its plan step. The collector applies them again to the
produced table and inventory in a second process, because it does not trust the producer. A refusal names the asset or
the link.

| Refused input | Error |
| --- | --- |
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

A caller checks a link graph with `distpath::validate_links` first, as the Go collector did. That function refuses a
target that resolves through another link, so `validate_link_graph` does not check it again.

## JSON (`planfile::json`)

`json::read` and `json::from_slice` port Go `pluginpack.ReadJSON`. Both are `serde_json::from_slice`. With a
`deny_unknown_fields` type, it refuses an unknown key, a repeated key, trailing data and invalid UTF-8. A key must match
its field exactly. `null` for an `Option` field is the same as an absent key, as for a Go pointer. `null` for any other
field is an error. Errors state the line and the column. `json::read` adds the path as the context of an error.
