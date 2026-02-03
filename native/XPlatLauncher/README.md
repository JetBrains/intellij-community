Known issues:

1) ... invalid data in index - calculated checksum does not match expected; class=Index (10)
TLDR: `git config --local index.skiphash false && git reset --mixed && git update-index --refresh`

check your feature.manyfiles / index.skiphash

git v2.40.0+ causes cargo to fail on our repo with index.skiphash set to true
git.manyfiles=true implies index.skiphash=true by default

libgit2 has merged the fix, but didn't release it yet
https://github.com/libgit2/libgit2/issues/6531

cargo issue
https://github.com/rust-lang/cargo/issues/11857