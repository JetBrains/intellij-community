//! The copy step of a full distribution, which [`compose_components`] calls.
//!
//! [`compose_components`] checks every destination and the link graph of all components before it calls this step.
//! [`ComponentSources::resolve`] checks every source. So this step only refuses the entry shapes that those checks
//! accept and that no collector writes.
//!
//! For each component in order, the step creates each entry below the target and the missing parents. It accepts a
//! directory that exists, and it refuses anything else at a destination, so it never replaces a file. After all
//! components, it applies the mode of each directory entry, the deepest first. It writes no reserved file.
//!
//! [`compose_components`]: crate::compose::compose_components

use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use component::manifest::{ComponentEntry, ComponentEntryType, ComponentManifest};
use component::paths;
use rayon::prelude::*;

use crate::compose::DevBuildComponent;
use crate::spec::ComponentSources;

/// The thread count of the copy step. A clone copies only file metadata, and the Bazel rule books four CPUs for the
/// composer action.
const COPY_THREADS: usize = 4;

/// Writes the files of every component at `target`, an empty directory. The source bindings give the bytes of each
/// file. Each component gets a child span of `parent`.
pub(crate) fn merge_components(components: &[DevBuildComponent], target: &Path, parent: &trace::Span) -> Result<()> {
    let pool = rayon::ThreadPoolBuilder::new().num_threads(COPY_THREADS).build()?;
    let mut links = Vec::new();
    for component in components {
        let manifest = &component.manifest;
        // One span per component, so that a slow composition names the component that made it slow.
        let span = parent.child("merge dev build component");
        span.tag("kind", manifest.kind.as_str());
        match copy_component(manifest, target, component.source_bindings.as_ref(), &pool, &mut links) {
            Ok(byte_count) => {
                span.tag("fileCount", manifest.entries.len());
                span.tag("byteCount", byte_count);
            }
            Err(error) => {
                span.fail(&format_args!("{error:#}"));
                return Err(error);
            }
        }
    }
    // Every file and directory exists now, so a link on Windows gets the kind of its target. The link graph has no
    // chain, so no link target passes through another link, and the links need no order.
    for (link, link_target) in links {
        create_link(&link, link_target)?;
    }
    // A directory mode can remove the write permission, so the deepest directory gets its mode first.
    let mut directories: Vec<&ComponentEntry> = components
        .iter()
        .flat_map(|component| &component.manifest.entries)
        .filter(|entry| entry.entry_type == ComponentEntryType::Directory)
        .collect();
    directories.sort_by(|first, second| paths::compare_utf16(&second.relative_path, &first.relative_path));
    for entry in directories {
        fscopy::set_distribution_file_mode(&destination(target, &entry.relative_path), false, entry.mode)?;
    }
    Ok(())
}

/// One file to copy, after all checks of its component.
struct CopyJob {
    source: PathBuf,
    destination: PathBuf,
    executable: bool,
    mode: Option<u32>,
}

/// Copies the files of one component from the sources that its manifest names, and adds its declared links to
/// `links`. It returns the byte count of the files.
///
/// The manifest declares the executable flag, so a source mode never reaches the distribution.
fn copy_component<'a>(
    manifest: &'a ComponentManifest,
    target: &Path,
    bindings: Option<&ComponentSources>,
    pool: &rayon::ThreadPool,
    links: &mut Vec<(PathBuf, &'a str)>,
) -> Result<u64> {
    let mut byte_count = 0;
    let mut jobs = Vec::new();
    let mut parents = HashSet::new();
    for entry in &manifest.entries {
        let name = &entry.relative_path;
        let destination = destination(target, name);
        match entry.entry_type {
            ComponentEntryType::Directory => create_directory_entry(&destination)?,
            ComponentEntryType::Symlink => {
                let (None, Some(link_target)) = (&entry.source, &entry.symlink_target) else {
                    bail!(
                        "Dev-build component '{}' must declare the symbolic link '{name}' without a file source",
                        manifest.kind
                    );
                };
                links.push((destination, link_target.as_str()));
            }
            ComponentEntryType::ComponentFile => {
                let Some(source) = &entry.source else {
                    bail!(
                        "Dev-build component '{}' declares no tree, so '{name}' must name where its bytes are",
                        manifest.kind
                    );
                };
                let Some(bindings) = bindings else {
                    bail!(
                        "Dev-build component '{}' has no source bindings, so the composer cannot copy '{name}'",
                        manifest.kind
                    );
                };
                // The binding follows the staging link of Bazel to the declared artifact, as the tree walk of the
                // collector does. A copy of the link would leak the execution root.
                let source = bindings.resolve(source)?;
                byte_count += fs::metadata(&source).with_context(|| source.clone())?.len();
                let parent = destination.parent().expect("a destination is below the target");
                if parents.insert(parent.to_path_buf()) {
                    create_directories(parent)?;
                }
                jobs.push(CopyJob {
                    source: source.into(),
                    destination,
                    executable: entry.executable,
                    mode: entry.mode,
                });
            }
        }
    }
    // Each copy is independent, and the first failed copy in manifest order names the error.
    let copied: Vec<Result<()>> = pool.install(|| jobs.par_iter().map(copy_file).collect());
    copied.into_iter().collect::<Result<()>>()?;
    Ok(byte_count)
}

/// Copies one file with its modification time, then sets the declared mode.
///
/// The copy is a clone where the file system can make one, for example with `fclonefileat` on APFS.
fn copy_file(job: &CopyJob) -> Result<()> {
    fscopy::copy_with_attributes(&job.source, &job.destination)?;
    Ok(fscopy::set_distribution_file_mode(&job.destination, job.executable, job.mode)?)
}

/// The path of a distribution entry below `target`.
fn destination(target: &Path, relative_path: &str) -> PathBuf {
    target.join(paths::from_slash(relative_path).as_ref())
}

/// Creates the link with the target text that the manifest declares.
///
/// On Windows a link to an existing directory is a directory link. A relative target starts at the directory of the
/// link.
fn create_link(link: &Path, target: &str) -> Result<()> {
    let parent = link.parent().expect("a link is below the target");
    create_directories(parent)?;
    let target_is_directory =
        cfg!(windows) && fs::metadata(parent.join(paths::from_slash(target).as_ref())).is_ok_and(|metadata| metadata.is_dir());
    Ok(fscopy::symlink(Path::new(target), link, target_is_directory)?)
}

fn create_directories(directory: &Path) -> Result<()> {
    fs::create_dir_all(directory).with_context(|| directory.display().to_string())
}

/// Creates the directory of a directory entry, or accepts the directory that is there.
///
/// A file or a link at the destination fails. The checks before this step make it a defect, not an input.
fn create_directory_entry(directory: &Path) -> Result<()> {
    create_directories(directory)?;
    // `create_dir_all` accepts a link to a directory, so the entry itself must be a directory.
    let metadata = fs::symlink_metadata(directory).with_context(|| directory.display().to_string())?;
    if !metadata.is_dir() {
        bail!("Cannot create the directory {}: a symbolic link is there", directory.display());
    }
    Ok(())
}

#[cfg(test)]
mod tests;
