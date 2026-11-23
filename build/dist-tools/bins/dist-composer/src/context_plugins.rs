//! The executable list of a context plugin pool: each file of its plugin components that needs the executable bit.

use std::fs;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use component::manifest::{self, ComponentManifest};

/// The directory of the distribution that holds every destination of a context plugin.
const PLUGINS_PREFIX: &str = "plugins/";

/// Returns the destination of each executable file of `manifests`, relative to `<context>/plugins`, sorted and distinct.
///
/// A component manifest names each file below a directory tree too, so the list holds the executable files of a tree.
/// An executable file outside `plugins/` fails, because a context plugin places nothing there.
pub(crate) fn executables<'a>(manifests: &[&'a ComponentManifest]) -> Result<Vec<&'a str>> {
    let mut files = Vec::new();
    for manifest in manifests {
        for entry in manifest.entries.iter().filter(|entry| entry.executable()) {
            let path = entry.relative_path();
            let Some(relative) = path.strip_prefix(PLUGINS_PREFIX) else {
                bail!(
                    "The component '{}' marks '{path}' executable, and a context plugin places no file outside `{PLUGINS_PREFIX}`",
                    manifest.kind
                );
            };
            files.push(relative);
        }
    }
    files.sort_unstable();
    files.dedup();
    Ok(files)
}

/// Reads each manifest of `manifest_files` and writes [`executables`] to `output`: one destination and a line feed per
/// line. A pool without an executable file gets an empty file.
pub(crate) fn write_executables(output: &Path, manifest_files: &[PathBuf]) -> Result<()> {
    if manifest_files.is_empty() {
        bail!("A context plugin pool needs at least one --component-manifest");
    }
    let manifests = manifest_files
        .iter()
        .map(|file| manifest::read_component_manifest(file))
        .collect::<Result<Vec<_>>>()?;
    let manifests: Vec<&ComponentManifest> = manifests.iter().collect();
    let text: String = executables(&manifests)?.into_iter().map(|file| format!("{file}\n")).collect();
    fs::write(output, text).with_context(|| output.display().to_string())
}

#[cfg(test)]
mod tests;
