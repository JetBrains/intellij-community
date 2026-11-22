//! The file sizes of a built distribution. The schema carries no size, so this is the only byte source.

use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};

use walkdir::WalkDir;

use anyhow::{Result, anyhow, bail};

/// The file sizes of a built distribution, by the path that a recipe names them with.
///
/// The index is keyed by directory first: `""` for the distribution root, `plugins/<name>` for a plugin. A path below
/// `plugins/<name>` belongs to that plugin, and a path below the root belongs to the platform.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Distribution {
    root: PathBuf,
    files: usize,
    index: HashMap<String, HashMap<String, u64>>,
}

impl Distribution {
    /// The root as the caller named it.
    pub fn root(&self) -> &Path {
        &self.root
    }

    /// The number of regular files in the index.
    pub const fn files(&self) -> usize {
        self.files
    }

    /// The number of directories that key the index. The distribution root counts as one.
    pub fn directories(&self) -> usize {
        self.index.len()
    }

    /// The size of one output, or `None` when the distribution does not hold that file. `path` is relative to the
    /// distribution root, as a recipe names it.
    pub fn lookup_from_root(&self, path: &str) -> Option<u64> {
        let (directory, below) = split_distribution_path(path);
        self.index.get(directory)?.get(below).copied()
    }
}

/// Indexes every regular file below `root`.
///
/// The walk follows a link at the root and no link below it. A link below the root is not a regular file, so a jar
/// that the distribution holds only through a link stays unjoined. A directory that an output names is not sized
/// either.
pub fn read_distribution(root: &Path) -> Result<Distribution> {
    let metadata = fs::metadata(root).map_err(|error| anyhow!("{}: {error}", root.display()))?;
    if !metadata.is_dir() {
        bail!("{}: the distribution root is not a directory", root.display());
    }
    let mut dist = Distribution {
        root: root.to_path_buf(),
        ..Distribution::default()
    };
    for entry in WalkDir::new(root) {
        let entry = entry.map_err(|error| anyhow!("{}: {error}", root.display()))?;
        if !entry.file_type().is_file() {
            continue;
        }
        let size = entry
            .metadata()
            .map_err(|error| anyhow!("{}: {error}", entry.path().display()))?
            .len();
        let relative = slash_path(entry.path(), root)?;
        let (directory, below) = split_distribution_path(&relative);
        dist.index.entry(directory.to_owned()).or_default().insert(below.to_owned(), size);
        dist.files += 1;
    }
    Ok(dist)
}

/// The path of `file` relative to `root`, with `/` between the segments.
fn slash_path(file: &Path, root: &Path) -> Result<String> {
    let relative = file.strip_prefix(root).unwrap_or(file);
    let segments = relative
        .components()
        .map(|segment| segment.as_os_str().to_str())
        .collect::<Option<Vec<_>>>()
        .ok_or_else(|| anyhow!("{}: a distribution file name is not UTF-8", file.display()))?;
    Ok(segments.join("/"))
}

/// Splits a path into the key of the index. The walk and [`Distribution::lookup_from_root`] share it, so they cannot
/// derive two different keys for one file.
fn split_distribution_path(path: &str) -> (&str, &str) {
    if let Some(after) = path.strip_prefix("plugins/")
        && let Some(slash) = after.find('/')
    {
        let end = "plugins/".len() + slash;
        return (&path[..end], &path[end + 1..]);
    }
    ("", path)
}

#[cfg(test)]
mod tests;
