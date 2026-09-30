//! The xxh3 hashes of the dev-distribution tools. Each one gives the value that hash4j gives in the platform and in the
//! Kotlin build.
//!
//! All are XXH3-64 with seed 0 and the default secret. They differ in the input bytes. `PackageIndexBuilder` hashes a
//! name as UTF-8 bytes for the IKV keys of the `__index__` of a distribution jar, and as UTF-16 code units for the class
//! and resource package sets. Also for an ASCII name, the two inputs are different byte sequences, so the two hashes are
//! different. The content hash of a file frames the bytes in blocks of 256 KiB. [`Hasher`] computes it over a stream,
//! and [`hash_file`] over a file.
//!
//! The algorithm is `xxhash_rust::xxh3`. The tests check it against the 2 050 reference vectors that the Java
//! implementation of the platform also uses. They check the block framing against the file hashes of the Kotlin build.

use std::fs::File;
use std::io::{self, Read};
use std::path::Path;

use xxhash_rust::xxh3::{Xxh3Default, xxh3_64};

/// The block size of the content hash: 256 KiB.
const BLOCK_SIZE: usize = 256 * 1024;

/// Returns hash4j `Hashing.xxh3_64().hashBytesToLong`: the hash of exactly the given bytes, with no framing.
///
/// The IKV keys and the directory name entries use this form. The result is signed, because the index stores `i64`
/// keys and sorts the package arrays with the signed comparison of Java.
#[expect(clippy::cast_possible_wrap, reason = "the same 64 bits, signed as Java stores them")]
pub fn hash_bytes(data: &[u8]) -> i64 {
    xxh3_64(data) as i64
}

/// Returns hash4j `Hashing.xxh3_64().hashCharsToLong`: the hash of the UTF-16 code units of `value`.
///
/// The class and resource package sets use this form. Each code unit goes in as two little-endian bytes, and no
/// length follows. The length is what makes hash4j `putString` different. So the input is two bytes per code unit,
/// not the UTF-8 encoding of `value`.
pub fn hash_chars(value: &str) -> i64 {
    // A string has at most one UTF-16 code unit per UTF-8 byte.
    const STACK_BYTES: usize = 512;
    if value.len() * 2 <= STACK_BYTES {
        let mut buffer = [0u8; STACK_BYTES];
        let length = encode_utf16_le(value, &mut buffer);
        hash_bytes(&buffer[..length])
    } else {
        let mut buffer = vec![0u8; value.len() * 2];
        let length = encode_utf16_le(value, &mut buffer);
        hash_bytes(&buffer[..length])
    }
}

fn encode_utf16_le(value: &str, buffer: &mut [u8]) -> usize {
    let mut length = 0;
    for unit in value.encode_utf16() {
        buffer[length..length + 2].copy_from_slice(&unit.to_le_bytes());
        length += 2;
    }
    length
}

/// The content hash of a byte stream: XXH3-64 with seed 0 over the stream in blocks of 256 KiB.
///
/// Each block is followed by its length as 4 bytes little-endian, as hash4j `putByteArray` frames an array. Only the
/// last block can be short, and an empty stream has no block. The stream position sets the block bounds, and the size
/// of each [`Hasher::update`] chunk does not. So any split of the stream into chunks gives the same hash.
///
/// The Kotlin build hashes a file the same way. [`hash_file`] is this hash over the bytes of a file, and the packer
/// computes it over the bytes of a jar while it writes them.
#[derive(Default)]
pub struct Hasher {
    state: Xxh3Default,
    /// The byte count of the current block. Between two calls, it is less than [`BLOCK_SIZE`].
    block_length: usize,
}

impl Hasher {
    pub const fn new() -> Self {
        Self {
            state: Xxh3Default::new(),
            block_length: 0,
        }
    }

    /// Adds the next bytes of the stream. A chunk can have any size, also zero.
    pub fn update(&mut self, mut data: &[u8]) {
        while !data.is_empty() {
            let (block, rest) = data.split_at(data.len().min(BLOCK_SIZE - self.block_length));
            self.state.update(block);
            self.block_length += block.len();
            if self.block_length == BLOCK_SIZE {
                self.end_block();
            }
            data = rest;
        }
    }

    /// Returns the hash of the stream. The result is signed, as Kotlin stores it.
    #[expect(clippy::cast_possible_wrap, reason = "the same 64 bits, signed as Kotlin stores them")]
    pub fn finish(mut self) -> i64 {
        if self.block_length != 0 {
            self.end_block();
        }
        self.state.digest() as i64
    }

    fn end_block(&mut self) {
        let length = u32::try_from(self.block_length).expect("a block fits in u32");
        self.state.update(&length.to_le_bytes());
        self.block_length = 0;
    }
}

/// Returns the content hash of the file at `path`: the [`Hasher`] value of its bytes.
///
/// The test `kotlin_hash_vectors` pins the values. An error is the error of the open or of a read, unchanged.
pub fn hash_file(path: &Path) -> io::Result<i64> {
    let mut file = File::open(path)?;
    let mut buffer = vec![0u8; BLOCK_SIZE];
    let mut hasher = Hasher::new();
    loop {
        match file.read(&mut buffer) {
            Ok(0) => return Ok(hasher.finish()),
            Ok(count) => hasher.update(&buffer[..count]),
            Err(error) if error.kind() == io::ErrorKind::Interrupted => {}
            Err(error) => return Err(error),
        }
    }
}

#[cfg(test)]
mod tests;
