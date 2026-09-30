# xxh3

The xxh3 hashes of the dev-distribution tools, with the values of hash4j. Port of the Go package `internal/xxh3` and of
`HashFile` of `internal/filemetadata`. The crate has no first-party dependency, so a tool that needs a hash links no
other crate for it.

## Frozen values

The tests pin every hash form:

- `hash_bytes` gives the 2 050 reference vectors of `XxHash3Test.java` and its string vectors.
- `hash_chars` gives the string and package vectors of `XxHash3Test.java`.
- `hash_file` and `Hasher` give the file hashes of the Kotlin build, for sizes from 0 bytes to past two blocks.
- `Hasher` gives the value of `hash_file` for every split of the stream into chunks.

## Supported subset

Every byte sequence and every string has a hash, so the crate refuses no input. `hash_file` returns the `io::Error` of
the open or of a read unchanged. The error does not name the path, so the caller adds it.

## Public items

| Item | Go original | Description |
|---|---|---|
| `hash_bytes(&[u8]) -> i64` | `xxh3.HashBytes` | hash4j `hashBytesToLong`: XXH3-64, seed 0, over the bytes. The IKV keys of the `__index__` use it. |
| `hash_chars(&str) -> i64` | `xxh3.HashChars` | hash4j `hashCharsToLong`: XXH3-64, seed 0, over the UTF-16LE code units, with no length. The package sets of the `__index__` use it. |
| `struct Hasher` | `HashFile` without the file | The content hash of a byte stream: XXH3-64, seed 0, over the stream in blocks of 256 KiB. Each block is followed by its length as 4 bytes little-endian, as hash4j `putByteArray` frames an array. `Hasher::new()`, `update(&[u8])` with a chunk of any size, `finish(self) -> i64`. It implements `Default`. |
| `hash_file(&Path) -> io::Result<i64>` | `filemetadata.HashFile` | The `Hasher` value of the bytes of a file. The inventory JSON of `filemeta` holds it as the hash of a file. |
