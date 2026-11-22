//! The lock of a store: `<root>/store.lock`.
//!
//! A materialize holds it shared while it links blobs. The garbage collection holds it exclusive while it renames a
//! generation away or deletes blobs, so it never deletes a blob that a materialize links. The kernel drops the lock with
//! the handle, also when the process is killed. The file is never deleted.

use std::fs::{File, OpenOptions, TryLockError};
use std::path::Path;
use std::thread;
use std::time::{Duration, Instant};

use anyhow::{Context as _, Result};

/// The name of the lock file in the root.
pub(crate) const LOCK_FILE: &str = "store.lock";

/// A held lock. The drop releases it.
#[derive(Debug)]
pub(crate) struct StoreLock {
    _file: File,
}

fn open(root: &Path) -> Result<File> {
    let path = root.join(LOCK_FILE);
    OpenOptions::new()
        .create(true)
        .truncate(false)
        .write(true)
        .open(&path)
        .with_context(|| format!("cannot open {}", path.display()))
}

impl StoreLock {
    /// Takes the shared lock, and waits while a garbage collection holds the exclusive one.
    pub(crate) fn shared(root: &Path) -> Result<Self> {
        let file = open(root)?;
        file.lock_shared()
            .with_context(|| format!("cannot lock {}", root.join(LOCK_FILE).display()))?;
        Ok(Self { _file: file })
    }

    /// Takes the exclusive lock, and waits up to `limit`. `None` means that a materialize held the lock for all of it.
    ///
    /// `File::lock` waits without a limit, so the function tries again every 50 ms.
    pub(crate) fn exclusive(root: &Path, limit: Duration) -> Result<Option<Self>> {
        let file = open(root)?;
        let deadline = Instant::now() + limit;
        loop {
            match file.try_lock() {
                Ok(()) => return Ok(Some(Self { _file: file })),
                Err(TryLockError::WouldBlock) => {
                    if Instant::now() >= deadline {
                        return Ok(None);
                    }
                    thread::sleep(Duration::from_millis(50));
                }
                Err(TryLockError::Error(error)) => {
                    return Err(error).with_context(|| format!("cannot lock {}", root.join(LOCK_FILE).display()));
                }
            }
        }
    }
}
