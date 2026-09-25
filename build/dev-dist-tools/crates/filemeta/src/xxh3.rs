//! The two xxh3 hashes that key the `__index__` of an IntelliJ distribution jar.
//!
//! Both are XXH3-64 with seed 0 and the default secret. They differ in the input bytes. `PackageIndexBuilder` hashes a
//! name as UTF-8 bytes for the IKV keys, and as UTF-16 code units for the class and resource package sets. Also for an
//! ASCII name, the two inputs are different byte sequences, so the two hashes are different.
//!
//! The algorithm is `xxhash_rust::xxh3`. The tests check it against the 2 050 reference vectors that the Java
//! implementation of the platform also uses.

use xxhash_rust::xxh3::xxh3_64;

#[cfg(test)]
#[allow(clippy::unreadable_literal, reason = "a generated table of 2 050 reference hashes")]
mod reference_hashes;

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

#[cfg(test)]
#[allow(
    clippy::cast_possible_truncation,
    clippy::unreadable_literal,
    reason = "the byte pattern is the index modulo 256, and the hash values are copied from the Java output"
)]
mod tests {
    use super::reference_hashes::REFERENCE_HASHES;
    use super::*;

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
}
