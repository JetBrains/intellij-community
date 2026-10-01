# filemeta

The file metadata of the dev-distribution tools: inventory JSON version 1. Port of the Go package
`internal/filemetadata`. The path and link rules of `internal/filemetadata` are in `distpath`. The content hash of a
file, `HashFile`, is `xxh3::hash_file` of the `xxh3` crate. Rustdoc states each public item and its contract.

## File format

`{"version":1,"entries":[...]}` and one `\n`, with no indentation. The entries are sorted bytewise by `relativePath`.
An entry holds `relativePath, type, hash, size, mode, executable, symlinkTarget` in this order. A directory has no
`hash`. `symlinkTarget` is present only when it is not empty. `serde_json` writes the document.

## Supported subset

The crate checks each path with `distpath::validate_path`, each link target with `distpath::validate_link_target`,
and the links of a set of entries with `distpath::validate_links`. So it refuses the paths and links that the `API.md`
of `distpath` lists, with the same text: a name outside ASCII or with `<`, `>` or `&`, a link chain, and a link target
with an empty segment.

`read` accepts only the shape that `write` writes. It refuses data after the document, an unknown, missing or `null`
field, a field of the wrong type, and a version other than 1. It also refuses the key `hash` on a directory and a
negative size.

## Errors

Every function that can fail returns `anyhow::Result`. `create_dir_all_0755` and `read_link_target` return
`io::Result`. A file system failure has the path as its context, so `{:#}` prints `<path>: <error>`, and
`downcast_ref::<io::Error>()` gives the `io::ErrorKind`. A refusal is one message with the text of the Go original, or a
text that names the unsupported input. `read` puts the path of the file before a refusal of its content.
