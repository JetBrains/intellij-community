# jarpack API

The packer core behind `PackContentModuleJar`. It merges source jars and single files into one STORED jar with a
generated `__index__`. It can also write the native files of one library as a tree. The output bytes are frozen. The six
golden digests in `src/tests/golden.rs` and the `./build/dev-dist.cmd jars` gate hold them.

It is the port of the Go packages `internal/jarpack` and `internal/nativelib`. The public items are at the crate root,
except the items of `jarpack::nativelib`, `jarpack::reader` and `jarpack::writer`, and rustdoc states each signature.
The packer binary writes the inventory of a spec. The descriptor writer reads the declared library jars with
`reader::Jar`.

## Supported subset

The port takes only the inputs that a producer writes. The producers are `pack_jar` in `content_module_jar.bzl`, which
`content_module_jar`, `dev_dist_platform_jar` and `dev_plugin` call, the recipe replay of `build/dev-dist`, and the
plan files through pluginpack. Each other input fails with an error that names it.

| Input | Accepted | Refused |
| --- | --- | --- |
| `keep-manifest=`, `merge-entities=`, `reject-native-entries=` | `true` | `false` and every other value |
| `source-manifest=` | no value. No producer writes a manifest policy into a flag file. | every value, as an unknown option |
| A flag-file path | a path without a `.` or `..` component | a path with one. The Go parser cleaned it. |
| `file=<entry name>=<path>` | a nonempty name and path. The recipe replay writes it for a single-file source. | no `=` after the name, or an empty part |
| `trace-file=` | one path, in any number of groups | two different paths. A run writes one trace. |
| `jar-name=` | one nonempty file name per group, without `/` and `\` | an empty value, a value with a separator, or a second line in the group |
| A source path | a nonempty path | an empty path |
| The module manifests of one jar | one | a second one. The refusal names the jar and both sources. |
| The `Boot-Class-Path` main attribute of a module manifest | the file name of the jar, or no attribute | every other value. The refusal names the jar, the source and the value. |
| A module manifest | UTF-8 text | other bytes |
| `META-INF/listOfEntities.txt` with `merge_entities` | UTF-8 text | other bytes. The Go trim stopped at the first bad byte. |
| A native entry in `nativelib::select` | ASCII | other names. The family match folds ASCII case only. |
| The native tree before the pack | an empty directory | an absent directory, or a directory that holds an entry |

The packer writes no directory record, and it refuses `directory-entries=` as an unknown option. A directory of a
non-class file is an index row of `__index__`.

The one-shot mode with many `output=` groups stays, because the profiling method of the README uses it. No Starlark rule
writes a `file=` line. The recipe replay of `build/dev-dist` writes one, so the form stays.

The types state the rest of the grammar. A `Source` is a `Jar` with an `EntryFilter` or a `File` with an entry name, so
a jar without a filter or a patch of a jar cannot occur. A `NativeSpec` has a `NativeTree` with the family and the
architecture, or no tree, so a platform without a tree cannot occur. The Go port checked these mixes at run time.

## Manifests

A jar keeps the manifest of its module. The `META-INF/MANIFEST.MF` of a `module=` source survives the merge, whatever
`keep-manifest=` and the `ManifestMode` of the source say. A library source and a file source keep their manifest by
their `ManifestMode`, and by `keep-manifest=` when they have none. A producer writes `keep-manifest=true` when a library
is the one meaningful source of the jar, and never for a module output. pluginpack gives `ManifestMode::Drop` to each module source of a jar with two sources, and the
module manifest survives all the same. A file source is never a module manifest, so the two manifest refusals do not
apply to it. No entry changes its content in the merge.

A module manifest with a `Boot-Class-Path` must name the distribution name of the jar, `MergeSpec::jar_name()`. The
`jar-name=` line states it, because the output file of a content-module jar is `<target>.production.jar`, and the
distribution names the jar `<module>.jar`. A group without the line, and a `MergeSpec` of pluginpack, take the output
file name. The name is not in the bytes of the jar. It is also the `jar` tag of the `pack jar` span and the start of the
duplicate line.

The `Boot-Class-Path` check reads the main section of the manifest, up to the first empty line. A line that starts with
a space continues the line before it. The attribute name matches without regard to ASCII case, as the JAR specification
states.

## Packing

- The packer packs one spec in two steps. It calls `spec.pack(&options)` first, and it writes the inventory of the
  spec second when the spec names a metadata file. The `pack jar` span holds the first step, and its
  `inventory packing output` child span holds the second.
- The caller creates the parent of the jar and the root of the native tree before the pack. The packer creates them
  with `filemeta::create_dir_all_0755`. pluginpack reserves each destination of its stage before the merge, so the
  parent exists. A missing parent fails at the create call of the jar with `<path>: <io::Error>`.
- `MergeSpec::pack` prints nothing. The caller prints `duplicate_line(&spec.jar_name(), &report.duplicates)` to stderr.
  The line is `<jar>: N duplicate entries, first source wins: a, b`, with at most 10 names, and it has no line end.
  The Go binary printed it before an inventory error, so the caller prints it between the two steps.
- `MergeSpec` and `MergeReport` are `Send + Sync`, so a caller can pack many specs on parallel threads.
- The merge hashes the bytes of the jar as they go to the file. So `MergeReport::content_hash` is the
  `xxh3::hash_file` value of the jar, and the packer does not read the jar again for its inventory. The hasher sits
  between the write buffer and the file, so the buffer stays in front of the file. The merge does not hash the files of
  a native tree.
- `MergeOptions::verify_crc` is the one switch of the CRC check. It is a setting of the run, so the caller passes the
  same options to every group. pluginpack calls `MergeSpec::merge` with the default options.

## Recipes

`parse_flag_file` returns a `FlagFile`: one `MergeSpec` per `output=` line, and the `trace-file=` destination of the
run. `trace-file=` is a line inside a group, because a packing action passes no other argument. The packer takes the
destination from the command line first.

With `validate_entry_names`, the merge checks the name of each entry with `distpath::validate_entry_name` and keeps
its text. The natives mode checks the name of each tree file the same way.

`resolve_path` joins a relative value to `base_dir` and keeps an absolute one. It refuses a value with a `.` or `..`
component, because the recipe checks compare the paths as they are written. The packer applies it to `--trace-file=`.

`EntryFilter::ModuleOutput` is `module_output_name_filter`, and `EntryFilter::Library` is `library_name_filter`. The
plan contract of pluginpack names the same two filters. The Go `Source.EntryOverrides` has no port. Only natives mode
set it, and natives mode now reserves its names inside the merge.

## Natives

`jarpack::nativelib` is the port of `internal/nativelib`. The Go `ValidFamily` has no port, because every `Family`
value is valid. `NativeTree::file_mode` gives the mode of each tree file, and the packer records it in the inventory.

The merge creates each directory below the tree root with the mode 0755 under any umask. The packer creates the root
and the parent of the jar with the same mode. The Go `os.MkdirAll(path, 0o755)` gave the same mode under the umask 022
or 002, but 0700 under the umask 077. A directory that exists keeps its mode. The merge links no `filemeta`, because
that crate would put the inventory crates into each tool that reads a jar.

## Low level

The reader and the writer are written by hand, because a crate would break the frozen bytes. The `zip` crate keys the
records of a source jar by name. So it drops the second record of a name, and the merge must report that record. A general zip
writer writes the version, the flags and the times that the distribution jar keeps at zero.

`Writer::close` consumes the writer and returns the underlying writer and the size of the jar. After a failed call the
state of the writer is not defined, so the caller drops it. A writer dropped before `close` discards its buffer, as the
Go writer did. So a failed merge leaves no plausible tail in the output file.

## Errors

Every function that can fail returns `anyhow::Result`. A refusal is one message. Its text is the text of the Go error,
or a text that names the refused input when the refusal has no Go counterpart. The merge adds the path of a source or
of the jar as context, so `{:#}` prints `<context>: <error>`, as the Go `fmt.Errorf("%s: %w")` did.

An I/O error has the path as its context and reads `<path>: <io::Error>`, for example
`x.jar: No such file or directory (os error 2)`. The Go text was `open x.jar: no such file or directory`. No gate reads
the text. `downcast_ref::<io::Error>()` gives the `io::ErrorKind`.

A `Writer` does not know the path of its output, so its I/O error has no path. The merge adds the jar path to an I/O
error of the writer, for example `intellij.example.jar: No space left on device (os error 28)`. The Go text was
`write intellij.example.jar: no space left on device`. A refusal of the writer names its entry or its limit, and the
merge keeps it as it is. Another caller of `Writer` adds the path itself. A tree directory that the merge cannot
create gives `<directory>: <io::Error>`.
