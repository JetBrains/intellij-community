use std::fs;
use std::io::{self, Read};
use std::path::Path;

use walkdir::WalkDir;
use xxhash_rust::xxh3::Xxh3Default;

use crate::entry::{Entry, EntryType, Error, invalid, merge, validate_entry, validate_path};
use crate::xxh3;

/// The block size of [`hash_file`]: 256 KiB.
const BLOCK_SIZE: usize = 256 * 1024;

/// Returns the content hash of a file: XXH3-64 with seed 0 over the file in blocks of 256 KiB.
///
/// Each block is followed by its length as 4 bytes little-endian, as hash4j `putByteArray` frames an array. The Kotlin
/// build hashes a file the same way, and the test `kotlin_hash_vectors` pins the values.
#[expect(clippy::cast_possible_wrap, reason = "the same 64 bits, signed as Kotlin stores them")]
pub fn hash_file(path: &Path) -> io::Result<i64> {
    let mut file = fs::File::open(path)?;
    let mut buffer = Vec::with_capacity(BLOCK_SIZE);
    let mut hasher = Xxh3Default::new();
    loop {
        buffer.clear();
        // A block is short only at the end of the file.
        let count = (&mut file).take(BLOCK_SIZE as u64).read_to_end(&mut buffer)?;
        if count != 0 {
            hasher.update(&buffer);
            hasher.update(&u32::try_from(count).expect("a block fits in u32").to_le_bytes());
        }
        if count < BLOCK_SIZE {
            return Ok(hasher.digest() as i64);
        }
    }
}

/// Returns the hash of the target text of a link: XXH3-64 with seed 0 over the UTF-8 bytes.
pub fn hash_symlink_target(target: &str) -> i64 {
    xxh3::hash_bytes(target.as_bytes())
}

/// Returns the permission bits of an entry.
///
/// NTFS stores no POSIX mode. On Windows the function returns 0o755 for a directory and 0o644 for all other entries.
pub fn permissions(metadata: &fs::Metadata) -> u32 {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;

        metadata.permissions().mode() & 0o777
    }
    #[cfg(not(unix))]
    {
        if metadata.is_dir() { 0o755 } else { 0o644 }
    }
}

/// Returns the target of the link at `source`.
///
/// A relative target comes back in slash form, which the metadata and the archives hold, because Windows stores it
/// with backslashes. An absolute target keeps the form of the host. A target that is not valid UTF-8 is an error of
/// the kind [`io::ErrorKind::InvalidData`].
pub fn read_link_target(source: &Path) -> io::Result<String> {
    let target = fs::read_link(source)?.into_os_string().into_string().map_err(|target| {
        io::Error::new(
            io::ErrorKind::InvalidData,
            format!("the link target is not valid UTF-8: {}", target.display()),
        )
    })?;
    if cfg!(windows) && !Path::new(&target).is_absolute() {
        return Ok(target.replace('\\', "/"));
    }
    Ok(target)
}

/// Returns the entry of the file, directory or link at `source`, with the path `relative_path`.
///
/// The function does not follow a link. It hashes a file with [`hash_file`] and rejects all other file types.
pub fn inspect(source: &Path, relative_path: &str) -> Result<Entry, Error> {
    validate_path(relative_path)?;
    let metadata = fs::symlink_metadata(source).map_err(|error| Error::io(source, error))?;
    let file_type = metadata.file_type();
    let mut entry = Entry {
        relative_path: relative_path.to_owned(),
        ..Entry::default()
    };
    if file_type.is_dir() {
        entry.entry_type = EntryType::Directory;
        entry.mode = permissions(&metadata);
    } else if file_type.is_file() {
        entry.mode = permissions(&metadata);
        entry.size = i64::try_from(metadata.len()).map_err(|error| invalid(format!("file too large: {}: {error}", source.display())))?;
        entry.executable = entry.mode & 0o111 != 0;
        entry.hash = hash_file(source).map_err(|error| Error::io(source, error))?;
    } else if file_type.is_symlink() {
        entry.entry_type = EntryType::Symlink;
        entry.symlink_target = read_link_target(source).map_err(|error| Error::io(source, error))?;
        entry.hash = hash_symlink_target(&entry.symlink_target);
    } else {
        return Err(invalid(format!("not a regular file or symbolic link: {}", source.display())));
    }
    validate_entry(&entry)?;
    Ok(entry)
}

/// Returns the merged entries of every file, directory and link below the directory `root`.
///
/// The root must be a real directory, not a link to one. The function does not follow a link below the root. The
/// result is sorted and checked as [`merge`] does, so an unsafe link graph is an error.
pub fn inventory(root: &Path) -> Result<Vec<Entry>, Error> {
    let metadata = fs::symlink_metadata(root).map_err(|error| Error::io(root, error))?;
    if !metadata.is_dir() {
        return Err(invalid(format!("inventory root is not a directory: {}", root.display())));
    }
    let mut entries = Vec::new();
    for item in WalkDir::new(root).min_depth(1).sort_by_file_name() {
        let item = item.map_err(|error| {
            let path = error.path().unwrap_or(root).to_path_buf();
            Error::Io { path, error: error.into() }
        })?;
        let relative = item.path().strip_prefix(root).expect("a walk entry is below the root");
        let relative_path = relative
            .iter()
            .map(|part| part.to_str())
            .collect::<Option<Vec<_>>>()
            .ok_or_else(|| invalid(format!("the file name is not valid UTF-8: {}", item.path().display())))?
            .join("/");
        entries.push(inspect(item.path(), &relative_path)?);
    }
    merge(&entries)
}
