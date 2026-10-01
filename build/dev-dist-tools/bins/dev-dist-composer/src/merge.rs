//! The copy step of a full distribution, which [`compose_components`] calls.
//!
//! [`compose_components`] checks every destination and the link graph of all components before it calls this step.
//! Each copy job checks its source with [`ComponentSources::resolve`]. So this step refuses only a component file of a
//! component without source bindings, which the Starlark caller never writes.
//!
//! For each component in order, the step creates each directory entry and the missing parents of each file. It accepts
//! a directory that exists, and it refuses anything else at a directory destination. Then one pool copies the files of
//! all components, and a copy refuses a destination that exists, so it never replaces a file. Then the step creates the
//! links and applies the mode of each directory entry, the deepest first. It writes no reserved file.
//!
//! [`compose_components`]: crate::compose::compose_components

use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use component::manifest::ComponentEntry;
use component::paths;
use rayon::prelude::*;

use crate::compose::DevBuildComponent;
use crate::spec::ComponentSources;

/// The thread count of the copy step. A clone copies only file metadata, and the Bazel rule books four CPUs for the
/// composer action.
const COPY_THREADS: usize = 4;

/// Writes the files of every component at `target`, an empty directory. The source bindings give the bytes of each
/// file.
///
/// The step prepares every component in order: it creates the directory entries and the parents of the files, and it
/// lists the files and the links. Then one pool copies the files of all components. Each component gets a child span
/// of `parent` with its file count and its byte count. The spans end after the shared copy, so they overlap.
pub(crate) fn merge_components(components: &[DevBuildComponent], target: &Path, parent: &trace::Span) -> Result<()> {
    let pool = rayon::ThreadPoolBuilder::new().num_threads(COPY_THREADS).build()?;
    let mut jobs = Vec::new();
    let mut links = Vec::new();
    let mut parents = HashSet::new();
    let mut spans = Vec::with_capacity(components.len());
    for (index, component) in components.iter().enumerate() {
        // One span per component, so that a composition names the files and the bytes of each component.
        let span = parent.child("merge dev build component");
        span.tag("kind", component.manifest.kind.as_str());
        if let Err(error) = prepare_component(index, component, target, &mut parents, &mut jobs, &mut links) {
            span.fail(&format_args!("{error:#}"));
            return Err(error);
        }
        span.tag("fileCount", component.manifest.entries.len());
        spans.push(span);
    }
    // Each copy is independent, and the first failed copy in manifest order names the error.
    let copied: Vec<Result<u64>> = pool.install(|| jobs.par_iter().map(copy_file).collect());
    let mut byte_counts = vec![0u64; components.len()];
    for (job, result) in jobs.iter().zip(copied) {
        match result {
            Ok(byte_count) => byte_counts[job.component] += byte_count,
            Err(error) => {
                spans[job.component].fail(&format_args!("{error:#}"));
                return Err(error);
            }
        }
    }
    for (span, byte_count) in spans.into_iter().zip(byte_counts) {
        span.tag("byteCount", byte_count);
        span.end();
    }
    // Every file and directory exists now, so a link on Windows gets the kind of its target. The link graph has no
    // chain, so no link target passes through another link, and the links need no order.
    for (link, link_target) in links {
        create_link(&link, link_target)?;
    }
    // A directory mode can remove the write permission, so the deepest directory gets its mode first.
    let mut directories: Vec<(&str, u32)> = components
        .iter()
        .flat_map(|component| &component.manifest.entries)
        .filter_map(|entry| match entry {
            ComponentEntry::Directory { relative_path, mode } => Some((relative_path.as_str(), *mode)),
            _ => None,
        })
        .collect();
    directories.sort_by(|first, second| second.0.cmp(first.0));
    for (relative_path, mode) in directories {
        fscopy::set_distribution_file_mode(&destination(target, relative_path), false, Some(mode))?;
    }
    Ok(())
}

/// One file to copy. The copy job resolves the source through the bindings of its component.
struct CopyJob<'a> {
    /// The index of the component in the composition.
    component: usize,
    bindings: &'a ComponentSources,
    source: &'a str,
    destination: PathBuf,
    executable: bool,
    mode: Option<u32>,
}

/// Creates the directory entries of one component and the parents of its files, and adds its files to `jobs` and its
/// declared links to `links`.
///
/// The manifest declares the executable flag, so a source mode never reaches the distribution.
fn prepare_component<'a>(
    index: usize,
    component: &'a DevBuildComponent,
    target: &Path,
    parents: &mut HashSet<PathBuf>,
    jobs: &mut Vec<CopyJob<'a>>,
    links: &mut Vec<(PathBuf, &'a str)>,
) -> Result<()> {
    let manifest = &component.manifest;
    for entry in &manifest.entries {
        let name = entry.relative_path();
        let destination = destination(target, name);
        match entry {
            ComponentEntry::Directory { .. } => create_directory_entry(&destination)?,
            ComponentEntry::Symlink { symlink_target, .. } => links.push((destination, symlink_target.as_str())),
            ComponentEntry::ComponentFile {
                source, executable, mode, ..
            } => {
                let Some(bindings) = &component.source_bindings else {
                    bail!(
                        "Dev-build component '{}' has no source bindings, so the composer cannot copy '{name}'",
                        manifest.kind
                    );
                };
                let parent = destination.parent().expect("a destination is below the target");
                if parents.insert(parent.to_path_buf()) {
                    create_directories(parent)?;
                }
                jobs.push(CopyJob {
                    component: index,
                    bindings,
                    source,
                    destination,
                    executable: *executable,
                    mode: *mode,
                });
            }
        }
    }
    Ok(())
}

/// Resolves the source, copies the file with its modification time, then sets the declared mode. It returns the byte
/// count of the file.
///
/// The binding follows the staging link of Bazel to the declared artifact, as the tree walk of the collector does. A
/// copy of the link would leak the execution root. The copy is a clone where the file system can make one, for example
/// with `fclonefileat` on APFS.
fn copy_file(job: &CopyJob<'_>) -> Result<u64> {
    let (source, byte_count) = job.bindings.resolve(job.source)?;
    fscopy::copy_with_attributes(&source, &job.destination)?;
    fscopy::set_distribution_file_mode(&job.destination, job.executable, job.mode)?;
    Ok(byte_count)
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
