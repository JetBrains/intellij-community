# filemeta

The file metadata of the dev-distribution tools: inventory JSON version 2. The path and link rules are in `distpath`.
The content hash of a file is `xxh3::hash_file` of the `xxh3` crate. `macho::hash_file` gives the same hash and the
Mach-O kind of the file in one read. Rustdoc states each public item and its contract.

## File format

`{"version":2,"entries":[...]}` and one `\n`, with no indentation. The entries are sorted bytewise by `relativePath`.
An entry holds `relativePath, type, hash, size, mode, executable, symlinkTarget, macBinary` in this order. A directory
has no `hash`. `symlinkTarget` is present only when it is not empty. `macBinary` is `executable`, `dylib` or `bundle`,
and it is present only for a Mach-O file of one of these kinds. `serde_json` writes the document.

## The Mach-O kind

`macho::detect` follows `isMacBinary` of the macOS signing of the production build, over the `format-ripper` detector.
A thin header gives the kind of its `filetype`: `MH_EXECUTE`, `MH_DYLIB` or `MH_BUNDLE`. Every other type gives no kind.
The reader walks the load commands by `cmdsize`, and a read past the end gives no kind. A fat header gives the kind of
its slices. No slice, an invalid slice header, or slices of different kinds give no kind. So a Java class file, with the
same magic, has no kind. `testdata/mac-binary-vectors.json` holds the cases, and `MacSigningInputsTest` checks the same
names.

## Supported subset

The crate checks each path with `distpath::validate_path`, each link target with `distpath::validate_link_target`,
and the links of a set of entries with `distpath::validate_links`. So it refuses the paths and links that the `API.md`
of `distpath` lists, with the same text: a name outside ASCII or with `<`, `>` or `&`, a link chain, and a link target
with an empty segment.

`read` accepts only the shape that `write` writes. It refuses data after the document, an unknown, missing or `null`
field, a field of the wrong type, and a version other than 2. It also refuses the key `hash` on a directory and a
negative size.

## Errors

Every function that can fail returns `anyhow::Result`. `create_dir_all_0755` and `read_link_target` return
`io::Result`. A file system failure has the path as its context, so `{:#}` prints `<path>: <error>`, and
`downcast_ref::<io::Error>()` gives the `io::ErrorKind`. A refusal is one message. It states the broken rule, or it
names the unsupported input. `read` puts the path of the file before a refusal of its content.
