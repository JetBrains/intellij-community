#![allow(
    clippy::cast_possible_truncation,
    clippy::unreadable_literal,
    reason = "the byte patterns are an index modulo 256, and the hash values are copied from the Java and the Kotlin output"
)]

mod reference_hashes;

use std::fs;

use super::*;
use reference_hashes::REFERENCE_HASHES;

/// The test uses every length of the reference table. The short paths are for 1 to 3, 4 to 8, 9 to 16, 17 to 128
/// and 129 to 240 bytes. The block loop starts above 240 bytes, and length 1 025 crosses the 1 024-byte block.
#[test]
fn hash_bytes_matches_the_reference_vectors() {
    let data: Vec<u8> = (0..REFERENCE_HASHES.len()).map(|index| index as u8).collect();
    for (length, &expected) in REFERENCE_HASHES.iter().enumerate() {
        assert_eq!(hash_bytes(&data[..length]), expected, "hash_bytes(src[..{length}])");
    }
}

/// The vectors come from `XxHash3Test.java`, which checks them against the platform implementation and hash4j.
/// The jars in the repository were indexed with these values.
#[test]
fn hash_bytes_of_strings() {
    for (input, expected) in [
        ("com/intellij/profiler/async/windows/WinAsyncProfilerLocator", 2833214887294487028),
        ("test", -7004795540881933248),
        ("тест буковок", -2011715203481716521),
    ] {
        assert_eq!(hash_bytes(input.as_bytes()), expected, "hash_bytes({input:?})");
    }
}

#[test]
fn hash_chars_of_strings() {
    for (input, expected) in [
        ("com/intellij/profiler/async/windows/WinAsyncProfilerLocator", -7916769887311287428),
        ("test", -1876252253805819900),
        ("тест буковок", -3590458601327935281),
    ] {
        assert_eq!(hash_chars(input), expected, "hash_chars({input:?})");
    }
}

/// `checkPackage` of `PackageIndexBuilder` hashes a package name in slash form, as the parent directory of an
/// entry name.
#[test]
fn hash_chars_of_packages() {
    for (input, expected) in [
        ("com.intellij.util.lang", -9217824570049207139),
        ("com.intellij.idea", -635775336887217634),
        ("kotlin.coroutines.jvm.internal", -3930079881136890558),
    ] {
        assert_eq!(hash_chars(&input.replace('.', "/")), expected, "hash_chars({input:?})");
    }
}

/// The two forms give different hashes, also for an ASCII name.
#[test]
fn the_two_forms_differ() {
    for value in ["a", "com/intellij/util/lang", "тест"] {
        assert_ne!(hash_bytes(value.as_bytes()), hash_chars(value), "{value:?}");
    }
    // The empty string is the one input where both forms hash zero bytes.
    assert_eq!(hash_bytes(b""), hash_chars(""));
}

#[test]
fn hash_chars_of_a_long_string_and_a_supplementary_character() {
    let long = "k".repeat(1_000);
    let units: Vec<u8> = long.encode_utf16().flat_map(u16::to_le_bytes).collect();
    assert_eq!(hash_chars(&long), hash_bytes(&units));
    assert_eq!(hash_chars("\u{1F600}"), hash_bytes(&[0x3D, 0xD8, 0x00, 0xDE]));
}

#[test]
fn kotlin_hash_vectors() {
    let temporary = tempfile::tempdir().unwrap();
    for (size, expected) in [
        (0usize, 3244421341483603138i64),
        (1, -2399747073602280719),
        (3, -737883702129266468),
        (240, 2788469911834355041),
        (241, -4155630063455057979),
        (262143, 9078738661776034622),
        (262144, -1692254647099917537),
        (262145, -2541306581069977202),
        (524288, 3157545227256347297),
        (524301, 8144707773225287728),
    ] {
        let data: Vec<u8> = (0..size).map(|index| (index * 31 + 7) as u8).collect();
        let source = temporary.path().join(format!("input-{size}.jar"));
        fs::write(&source, &data).unwrap();
        assert_eq!(hash_file(&source).unwrap(), expected, "size {size}");
        let mut hasher = Hasher::new();
        hasher.update(&data);
        assert_eq!(hasher.finish(), expected, "size {size}");
    }
}

/// A SplitMix64 sequence with a fixed seed. The test data and the chunk sizes are random, and the same in every run.
struct Random(u64);

impl Random {
    const fn next(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut value = self.0;
        value = (value ^ (value >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        value = (value ^ (value >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        value ^ (value >> 31)
    }

    /// Returns a value from 1 to `limit`.
    const fn size(&mut self, limit: usize) -> usize {
        1 + (self.next() % limit as u64) as usize
    }
}

/// The stream of `data` as hash4j `putByteArray` frames it: each block of 256 KiB, then its length. It shares no code
/// with [`Hasher`].
fn framed(data: &[u8]) -> Vec<u8> {
    let mut stream = Vec::new();
    for block in data.chunks(BLOCK_SIZE) {
        stream.extend_from_slice(block);
        stream.extend_from_slice(&(block.len() as u32).to_le_bytes());
    }
    stream
}

/// The packer feeds the hasher with the chunks of its write buffer, and [`hash_file`] with the chunks of its reads. The
/// chunk bounds do not match the block bounds, so each split of the stream must give the hash of the file.
#[test]
fn the_hasher_over_random_chunks_equals_hash_file() {
    let temporary = tempfile::tempdir().unwrap();
    let mut random = Random(0x0DD5_EED5);
    for size in [
        0,
        1,
        2,
        BLOCK_SIZE - 1,
        BLOCK_SIZE,
        BLOCK_SIZE + 1,
        2 * BLOCK_SIZE - 1,
        2 * BLOCK_SIZE,
        2 * BLOCK_SIZE + 1,
        3 * BLOCK_SIZE + 4_099,
    ] {
        let data: Vec<u8> = (0..size).map(|_| random.next() as u8).collect();
        let source = temporary.path().join(format!("input-{size}"));
        fs::write(&source, &data).unwrap();
        let expected = hash_file(&source).unwrap();
        assert_eq!(expected, hash_bytes(&framed(&data)), "size {size}");

        // One chunk, a chunk per block, and random chunks up to 16 bytes, up to 64 KiB and up to 1 MiB.
        let mut splits: Vec<Vec<usize>> = vec![vec![size], data.chunks(BLOCK_SIZE).map(<[u8]>::len).collect()];
        for limit in [16, 64 * 1024, 4 * BLOCK_SIZE] {
            let mut split = Vec::new();
            let mut rest = size;
            while rest != 0 {
                let chunk = random.size(limit).min(rest);
                split.push(chunk);
                rest -= chunk;
            }
            splits.push(split);
        }
        for split in &splits {
            let mut hasher = Hasher::new();
            let mut rest = data.as_slice();
            for &chunk in split {
                let (head, tail) = rest.split_at(chunk);
                hasher.update(head);
                // An empty chunk changes nothing.
                hasher.update(&[]);
                rest = tail;
            }
            assert!(rest.is_empty());
            assert_eq!(hasher.finish(), expected, "size {size}, {} chunks", split.len());
        }
    }
}

#[test]
fn hash_file_keeps_the_error_kind() {
    let temporary = tempfile::tempdir().unwrap();
    let error = hash_file(&temporary.path().join("missing.jar")).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::NotFound);
}
