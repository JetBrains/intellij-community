//! The records of the jar mode and the file mode. The inventory checks the destinations of both modes.

use std::collections::hash_map::Entry;
use std::collections::{HashMap, HashSet};
use std::path::Path;

use anyhow::{Context, bail};
use serde::Deserialize;

use crate::inventory::{Classpath, JarRecord, SourcedFile};

/// One record of `--jars-file` or `--files-file`. A jar record states no `executable`, and a file record states it.
/// Only a jar record can state `tree`, which names a native tree directory in place of a jar. Only a file record can
/// state `executableList` and `archivePath`, which the rule writes together for each file of an unpacked archive.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
struct Record {
    #[serde(default)]
    source: String,
    #[serde(default)]
    relative_path: String,
    #[serde(default)]
    executable: Option<bool>,
    #[serde(default)]
    tree: bool,
    #[serde(default)]
    core_class_path: bool,
    #[serde(default)]
    executable_list: Option<String>,
    #[serde(default)]
    archive_path: Option<String>,
}

fn read_records(file: &str) -> anyhow::Result<Vec<Record>> {
    planfile::json::read(Path::new(file))
}

/// The executable list of an extracted repository: the paths in the archive with an executable mode bit. The list is
/// `null` when a Windows host extracted the archive, because its file system has no executable bit.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct ExecutableList {
    executables: Option<HashSet<String>>,
}

/// The packed jars and the native trees of `intellij_dev_packed_jars_component`, each at `lib/<relativePath>`.
pub(crate) fn platform_jars(file: &str) -> anyhow::Result<Vec<JarRecord>> {
    let records = read_records(file)?;
    let mut files = Vec::with_capacity(records.len());
    for (index, record) in records.into_iter().enumerate() {
        let number = index + 1;
        if record.source.trim().is_empty() || record.relative_path.trim().is_empty() {
            bail!("{file}: record {number} requires source and relativePath");
        }
        // A packed jar is a jar, never a program. A record that states the bit is a file record in the wrong mode.
        if record.executable.is_some() {
            bail!("{file}: record {number} states executable, which a packed jar never is");
        }
        if record.tree && record.core_class_path {
            bail!("{file}: record {number} states coreClassPath for a native tree, which is no jar");
        }
        if record.executable_list.is_some() || record.archive_path.is_some() {
            bail!("{file}: record {number} states executableList or archivePath, which only a file record can");
        }
        // The destination of the jar, not the name of its file: a platform jar can name a subdirectory of `lib/`. A
        // tree record names the directory of the library below `lib/`, as the Kotlin packer places `lib/jna/`.
        distpath::validate_path(&record.relative_path).with_context(|| format!("{file}: record {number}"))?;
        let relative_path = format!("lib/{}", record.relative_path);
        files.push(if record.tree {
            JarRecord::Tree {
                source: record.source,
                relative_path,
            }
        } else {
            JarRecord::Jar(SourcedFile {
                classpath: if record.core_class_path { Classpath::Core } else { Classpath::None },
                ..SourcedFile::new(record.source, relative_path)
            })
        });
    }
    if files.is_empty() {
        bail!("{file} names no jar, so this component would contribute nothing");
    }
    Ok(files)
}

/// The explicit files of `intellij_dev_packed_jars_component`, each at its `relativePath`.
///
/// `os` is the target OS of the manifest. A file of an unpacked archive states `executableList` and `archivePath`. The
/// collector refuses such a file when the list names its path and the record states no executable bit. The check skips
/// the target OS `windows`, which gives a file no executable bit, and a `null` list. The mode of the source on disk
/// does not count: Bazel can write a source of an external repository with the mode `0555`.
pub(crate) fn explicit_files(file: &str, os: &str) -> anyhow::Result<Vec<SourcedFile>> {
    let records = read_records(file)?;
    let mut lists: HashMap<String, Option<HashSet<String>>> = HashMap::new();
    let mut files = Vec::with_capacity(records.len());
    for (index, record) in records.into_iter().enumerate() {
        let number = index + 1;
        let Some(executable) = record.executable else {
            bail!("{file}: record {number} requires source, relativePath and executable");
        };
        if record.source.trim().is_empty() || record.relative_path.trim().is_empty() {
            bail!("{file}: record {number} requires source, relativePath and executable");
        }
        if record.tree || record.core_class_path {
            bail!("{file}: record {number} states tree or coreClassPath, which only a packed jar record can");
        }
        distpath::validate_path(&record.relative_path).with_context(|| format!("{file}: record {number}"))?;
        match (&record.executable_list, &record.archive_path) {
            (None, None) => {}
            (Some(list), Some(archive_path)) if !list.trim().is_empty() && !archive_path.trim().is_empty() => {
                let executables = match lists.entry(list.clone()) {
                    Entry::Occupied(entry) => entry.into_mut(),
                    Entry::Vacant(entry) => entry.insert(planfile::json::read::<ExecutableList>(Path::new(list))?.executables),
                };
                let archive_executable = executables.as_ref().is_some_and(|executables| executables.contains(archive_path));
                if os != "windows" && archive_executable && !executable {
                    bail!(
                        "'{}' has an executable mode bit in its archive, as {list} states, but the record states no \
                         executable bit. unpacked_archive_executables must name '{}'",
                        record.relative_path,
                        record.relative_path
                    );
                }
            }
            _ => bail!("{file}: record {number} requires both executableList and archivePath, or neither"),
        }
        files.push(SourcedFile {
            executable,
            ..SourcedFile::new(record.source, record.relative_path)
        });
    }
    Ok(files)
}

#[cfg(test)]
mod tests;
