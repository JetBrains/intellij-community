//! The gzip resources of a module: each `.xml` entry of the given archives as one gzip member file. A Bazel action of
//! `gzip_resources.bzl` writes them once, and the module jar holds them as plain resources. The minicat catalogs of the
//! database plugin are the only user.

use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};

use crate::layout::LayoutScratch;
use crate::layout_archive::{EntryKind, LayoutArchive, gzip_member};
use crate::layout_writer::{Content, LayoutWriter, TreeWriter};

/// Writes `<output>/<name>.gzip` for each `.xml` entry of every archive, in archive order and then in central-directory
/// order. An archive is a `.zip` or a `.jar`. The first archive that holds a name wins. A file that is not XML, and a
/// link, fail with the archive name. The member keeps the deflate stream of the archive, so the legacy Kotlin build and
/// this action write the same bytes.
pub fn write_gzip_resources(archives: &[PathBuf], output: &Path) -> Result<()> {
    filemeta::create_dir_all_0755(output)?;
    let mut writer = TreeWriter::new(output.to_path_buf());
    // A zip or a jar needs no scratch directory. Only a decoded `.zip.zst` does.
    let mut scratch = LayoutScratch::unavailable();
    for file in archives {
        let name = file
            .file_name()
            .map(|name| name.to_string_lossy().to_lowercase())
            .unwrap_or_default();
        if !name.ends_with(".zip") && !name.ends_with(".jar") {
            bail!("a gzip resource source is a zip or jar archive: {}", file.display());
        }
        let mut archive = LayoutArchive::open(file, &mut scratch)?;
        archive.visit(&mut |entry| {
            if entry.kind == EntryKind::Directory {
                return Ok(());
            }
            if entry.kind != EntryKind::File || !entry.name.ends_with(".xml") {
                bail!("unexpected file {:?} in {}", entry.name, file.display());
            }
            let stream = entry.deflate().with_context(|| format!("{}: {}", file.display(), entry.name))?;
            writer.file(&format!("{}.gzip", entry.name), Content::Bytes(gzip_member(&stream)), 0o644)
        })?;
    }
    writer.finish()
}

#[cfg(test)]
mod tests;
