//! The store: `<root>/store/<hash>-<size>-<mode>`, one immutable file per content and mode.
//!
//! A generation links each of its files to a blob. A hard link shares the inode, and so the mode, so the mode is part
//! of the name. The store holds clones or copies of the Bazel outputs, never a link to one: on Windows a shared file
//! would stop Bazel from deleting its output while an IDE runs.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::process;
use std::sync::atomic::{AtomicU64, Ordering};

use anyhow::{Context as _, Result, bail};

/// The name of the store directory in the root.
pub(crate) const STORE_DIR: &str = "store";

/// The prefix of a file that a writer has not published yet.
pub(crate) const TEMPORARY_PREFIX: &str = ".tmp-";

static COUNTER: AtomicU64 = AtomicU64::new(0);

/// The store of one root.
#[derive(Debug, Clone)]
pub(crate) struct Store {
    directory: PathBuf,
    /// The number of links at which a blob counts as full, besides the limit of the file system. Tests set it.
    link_limit: Option<u64>,
}

/// One file of a generation: its source, the hash of its bytes, and its mode.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct BlobSource<'a> {
    pub(crate) source: &'a Path,
    /// The content hash, or `None` for a file that the layout does not hash, which the store then hashes.
    pub(crate) hash: Option<i64>,
    pub(crate) mode: u32,
}

impl Store {
    pub(crate) fn new(root: &Path) -> Result<Self> {
        let directory = root.join(STORE_DIR);
        fs::create_dir_all(&directory).with_context(|| format!("cannot create {}", directory.display()))?;
        Ok(Self {
            directory,
            link_limit: None,
        })
    }

    #[cfg(test)]
    pub(crate) const fn with_link_limit(mut self, limit: u64) -> Self {
        self.link_limit = Some(limit);
        self
    }

    /// Links `destination` to the blob of `file`, and publishes the blob first when it is missing.
    pub(crate) fn link(&self, file: &BlobSource<'_>, destination: &Path) -> Result<()> {
        let size = fs::metadata(file.source)
            .with_context(|| format!("cannot read {}", file.source.display()))?
            .len();
        let hash = match file.hash {
            Some(hash) => hash,
            None => xxh3::hash_file(file.source).with_context(|| format!("cannot read {}", file.source.display()))?,
        };
        let name = format!("{hash:016x}-{size}-{:o}", file.mode);
        let blob = self.directory.join(&name);
        if !blob.exists() {
            self.publish(file.source, &blob, hash, size, file.mode)?;
        }
        let mut current = blob;
        let mut next = 1;
        loop {
            match self.hard_link(&current, destination) {
                Ok(()) => return Ok(()),
                Err(error) if is_too_many_links(&error) => {
                    // A blob of many identical files is full. The next copy takes the next links.
                    let overflow = self.directory.join(format!("{name}.{next}"));
                    if !overflow.exists() {
                        self.publish(&current, &overflow, hash, size, file.mode)?;
                    }
                    current = overflow;
                    next += 1;
                }
                Err(error) => {
                    return Err(error).with_context(|| format!("cannot link {} to {}", destination.display(), current.display()));
                }
            }
        }
    }

    fn hard_link(&self, blob: &Path, destination: &Path) -> io::Result<()> {
        if let Some(limit) = self.link_limit
            && link_count(blob)? >= limit
        {
            return Err(too_many_links());
        }
        fs::hard_link(blob, destination)
    }

    /// Clones `source` to a temporary file, checks its hash and size, sets the mode, and publishes it as `blob`.
    /// A blob that another writer published first is kept.
    fn publish(&self, source: &Path, blob: &Path, hash: i64, size: u64, mode: u32) -> Result<()> {
        let temporary = self.directory.join(format!(
            "{TEMPORARY_PREFIX}{}-{}",
            process::id(),
            COUNTER.fetch_add(1, Ordering::Relaxed)
        ));
        let result = (|| {
            fscopy::clone_or_copy(source, &temporary).with_context(|| format!("cannot copy {} into the store", source.display()))?;
            let actual = xxh3::hash_file(&temporary).with_context(|| format!("cannot read {}", temporary.display()))?;
            let actual_size = fs::metadata(&temporary)
                .with_context(|| format!("cannot read {}", temporary.display()))?
                .len();
            if actual != hash || actual_size != size {
                bail!(
                    "{} changed while the launch copied it: the copy has the hash {actual:016x} and {actual_size} bytes, \
                     and the layout states {hash:016x} and {size} bytes. Build the target again",
                    source.display()
                );
            }
            set_blob_mode(&temporary, mode)?;
            match fs::hard_link(&temporary, blob) {
                Ok(()) => Ok(()),
                Err(error) if error.kind() == io::ErrorKind::AlreadyExists => Ok(()),
                Err(error) => Err(error).with_context(|| format!("cannot publish {}", blob.display())),
            }
        })();
        let _ = fs::remove_file(&temporary);
        result
    }
}

/// A blob is read-only on Unix. On Windows a hard link shares the attributes, and the tool keeps none, so a generation
/// deletes like any directory.
#[cfg(unix)]
fn set_blob_mode(path: &Path, mode: u32) -> Result<()> {
    fscopy::set_mode(path, mode & !0o222).with_context(|| format!("cannot set the mode of {}", path.display()))
}

#[cfg(windows)]
fn set_blob_mode(path: &Path, _mode: u32) -> Result<()> {
    let mut permissions = fs::metadata(path)
        .with_context(|| format!("cannot read {}", path.display()))?
        .permissions();
    #[expect(
        clippy::permissions_set_readonly_false,
        reason = "Windows has no world-writable mode, only the read-only attribute"
    )]
    permissions.set_readonly(false);
    fs::set_permissions(path, permissions).with_context(|| format!("cannot set the attributes of {}", path.display()))
}

/// `EMLINK` on Unix, `ERROR_TOO_MANY_LINKS` on Windows.
#[cfg(unix)]
const TOO_MANY_LINKS: i32 = 31;
#[cfg(windows)]
const TOO_MANY_LINKS: i32 = 1142;

fn is_too_many_links(error: &io::Error) -> bool {
    error.raw_os_error() == Some(TOO_MANY_LINKS)
}

fn too_many_links() -> io::Error {
    io::Error::from_raw_os_error(TOO_MANY_LINKS)
}

/// The number of hard links of the file at `path`.
#[cfg(unix)]
pub(crate) fn link_count(path: &Path) -> io::Result<u64> {
    use std::os::unix::fs::MetadataExt as _;
    Ok(fs::symlink_metadata(path)?.nlink())
}

/// The number of hard links of the file at `path`. Stable Rust gives no link count on Windows, so the function asks
/// `GetFileInformationByHandle`.
#[cfg(windows)]
pub(crate) fn link_count(path: &Path) -> io::Result<u64> {
    use std::os::windows::io::AsRawHandle as _;
    use windows_sys::Win32::Storage::FileSystem::{BY_HANDLE_FILE_INFORMATION, GetFileInformationByHandle};

    let file = fs::File::open(path)?;
    let mut information = BY_HANDLE_FILE_INFORMATION::default();
    // SAFETY: the handle is open for the life of `file`, and `information` is a valid out pointer.
    let ok = unsafe { GetFileInformationByHandle(file.as_raw_handle(), &raw mut information) };
    if ok == 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(u64::from(information.nNumberOfLinks))
}

#[cfg(test)]
mod tests;
