Known issues:

1) ... invalid data in index - calculated checksum does not match expected; class=Index (10)

check your feature.manyfiles / index.skiphash (.git/config has correct values)

git v2.40.0 + old version of libgit causes cargo to fail on our repo with index.skiphash set to true
git.manyfiles=true implies index.skiphash=true by default

libgit2 has merged the fix, but not release it yet
https://github.com/libgit2/libgit2/issues/6531

cargo issue
https://github.com/rust-lang/cargo/issues/11857