//! The reference vectors of XXH3-64.
//!
//! Each pair is a length `n` and `XXH3_64bits` over the first `n` bytes of the sequence `src[i] = i as u8`. The C
//! reference implementation at github.com/Cyan4973/xxHash produced the values. The lengths are the two sides of each
//! branch edge of `xxhash_rust::xxh3::xxh3_64`: the length classes up to 16 bytes, the 32, 64 and 96-byte steps up to 128
//! bytes, the second round loop from 144 bytes, the block path above 240 bytes, the first and the second block boundary
//! at 1 024 and 2 048 bytes, and the first stripe after the block at 1 089 bytes. Length 80 is an inner point.
//!
//! The values are a subset of `XXH3Test_HASHES` in
//! `community/build/tasks/test/org/jetbrains/intellij/build/io/XxHash3Test.java`. Do not edit.

pub(super) static REFERENCE_HASHES: [(usize, i64); 27] = [
    (0, 3244421341483603138),
    (1, -4302098779834749733),
    (3, 6864218090047839419),
    (4, 6979084321315492338),
    (8, 4187271766389786872),
    (9, -1629980255024596516),
    (16, -8983023575487778597),
    (17, -6993173599622040792),
    (32, 3829001002278603781),
    (33, -1833995589090992552),
    (64, 7027844749552840021),
    (65, 7577525643630617296),
    (80, -6074103631611676465),
    (96, 2849157965221752315),
    (97, -1791866940674927372),
    (128, -8807326403944725397),
    (129, -1407864491810865574),
    (143, -9069431037609626595),
    (144, 184117852034042369),
    (240, 3988562325861820517),
    (241, 209643423615708418),
    (1024, -6309269121238725342),
    (1025, 8703327852945446994),
    (1088, 2886049233756462774),
    (1089, 4825677492212933300),
    (2048, -2503433555198690048),
    (2049, 7124680608501705883),
];
