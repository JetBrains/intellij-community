//! The records of the jar mode and the file mode, and the destination check that both modes share.

use std::collections::BTreeSet;
use std::path::Path;

use anyhow::{Context, bail};
use serde::Deserialize;

use crate::inventory::{Classpath, JarRecord, SourcedFile};

/// One record of `--jars-file` or `--files-file`. A jar record states no `executable`, and a file record states it.
/// Only a jar record can state `tree`, which names a native tree directory in place of a jar.
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
}

fn read_records(file: &str) -> anyhow::Result<Vec<Record>> {
    planfile::json::read(Path::new(file))
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
pub(crate) fn explicit_files(file: &str) -> anyhow::Result<Vec<SourcedFile>> {
    let records = read_records(file)?;
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
        files.push(SourcedFile {
            source: record.source,
            relative_path: record.relative_path,
            executable,
            ..SourcedFile::default()
        });
    }
    Ok(files)
}

/// Refuses an unsafe destination, a repeated one, and a destination below another. It runs after the metadata
/// expands the tree records, because only then the destinations to check are known.
pub(crate) fn validate_destinations(files: &[SourcedFile]) -> anyhow::Result<()> {
    let mut destinations = BTreeSet::new();
    for file in files {
        distpath::validate_path(&file.relative_path)?;
        if !destinations.insert(file.relative_path.as_str()) {
            bail!("conflicting destination: {}", file.relative_path);
        }
    }
    for name in &destinations {
        let mut current = *name;
        while let Some((parent, _)) = current.rsplit_once('/') {
            if destinations.contains(parent) {
                bail!("conflicting destinations: {parent} contains {name}");
            }
            current = parent;
        }
    }
    Ok(())
}
