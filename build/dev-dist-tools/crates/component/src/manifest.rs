//! Component manifest v10: the collector writes it, and the composer reads it.
//!
//! Only the composer reads a manifest, so the format has no reader outside this workspace. Each entry states its type
//! in the JSON key `type`, and each type has only the keys that it needs.

use std::collections::HashSet;
use std::fs;
use std::path::Path;

use anyhow::{Context as _, Result, bail};
use serde::{Deserialize, Serialize};

/// The only manifest version. The collector writes it in every manifest, and the composer refuses every other version.
pub const MANIFEST_VERSION: i32 = 10;

/// The manifest of one component. A `None` main class is a component that contributes files and nothing else.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ComponentManifest {
    pub version: i32,
    pub kind: String,
    pub platform_prefix: String,
    pub os: String,
    pub arch: String,
    /// A plugin component holds one plugin and gives a plugin classpath record. Every other component holds none.
    pub plugin: bool,
    pub main_class: Option<String>,
    pub core_class_path: Vec<String>,
    pub entries: Vec<ComponentEntry>,
}

/// One entry of a component manifest. `relative_path` is the destination in the distribution, in slash form.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "kebab-case", rename_all_fields = "camelCase", deny_unknown_fields)]
pub enum ComponentEntry {
    /// A file whose bytes are at `source`, a host path. `mode` is an exact mode, or `None` for the
    /// [`conventional_mode`] of the executable flag.
    ComponentFile {
        relative_path: String,
        hash: i64,
        #[serde(default, skip_serializing_if = "is_false")]
        executable: bool,
        source: String,
        #[serde(default, skip_serializing_if = "Option::is_none")]
        mode: Option<u32>,
    },
    /// A directory with its mode.
    Directory { relative_path: String, mode: u32 },
    /// A relative symbolic link. `hash` is [`filemeta::hash_symlink_target`] of the target.
    Symlink {
        relative_path: String,
        hash: i64,
        symlink_target: String,
    },
}

const fn is_false(value: &bool) -> bool {
    !*value
}

impl ComponentManifest {
    /// Tells if the component fits every target platform.
    pub const fn platform_neutral(&self) -> bool {
        self.os.is_empty() && self.arch.is_empty()
    }

    /// The bytes of the manifest: JSON with a two-space indent and no trailing newline.
    pub fn to_json(&self) -> Vec<u8> {
        serde_json::to_vec_pretty(self).expect("a manifest always encodes")
    }
}

impl ComponentEntry {
    /// The destination in the distribution, in slash form.
    pub fn relative_path(&self) -> &str {
        match self {
            Self::ComponentFile { relative_path, .. } | Self::Directory { relative_path, .. } | Self::Symlink { relative_path, .. } => {
                relative_path
            }
        }
    }

    /// The JSON text of the type: `component-file`, `directory` or `symlink`.
    pub const fn type_name(&self) -> &'static str {
        match self {
            Self::ComponentFile { .. } => "component-file",
            Self::Directory { .. } => "directory",
            Self::Symlink { .. } => "symlink",
        }
    }

    /// The hash of a file or of a link target. A directory has the hash 0.
    pub const fn hash(&self) -> i64 {
        match self {
            Self::ComponentFile { hash, .. } | Self::Symlink { hash, .. } => *hash,
            Self::Directory { .. } => 0,
        }
    }

    /// The executable flag. Only a file can have it.
    pub const fn executable(&self) -> bool {
        matches!(self, Self::ComponentFile { executable: true, .. })
    }

    /// The file inventory entry of this entry, for the checks of [`filemeta::merge`]. A file without a mode gets the
    /// conventional mode.
    pub fn to_metadata(&self) -> filemeta::Entry {
        let relative_path = self.relative_path().to_owned();
        match self {
            Self::ComponentFile {
                hash, executable, mode, ..
            } => filemeta::Entry {
                relative_path,
                entry_type: filemeta::EntryType::File,
                hash: *hash,
                mode: mode.unwrap_or(conventional_mode(*executable)),
                executable: *executable,
                ..filemeta::Entry::default()
            },
            Self::Directory { mode, .. } => filemeta::Entry {
                relative_path,
                entry_type: filemeta::EntryType::Directory,
                mode: *mode,
                ..filemeta::Entry::default()
            },
            Self::Symlink { hash, symlink_target, .. } => filemeta::Entry {
                relative_path,
                entry_type: filemeta::EntryType::Symlink,
                hash: *hash,
                symlink_target: symlink_target.clone(),
                ..filemeta::Entry::default()
            },
        }
    }
}

/// The mode of a component file without an exact mode: 0755 for an executable file and 0644 for every other file.
pub const fn conventional_mode(executable: bool) -> u32 {
    if executable { 0o755 } else { 0o644 }
}

/// Reads a manifest, then applies [`validate_manifest`]. The error has the path as its context.
///
/// The reader checks the version before the other keys, so a manifest of another version fails with its version and
/// not with its first unknown key. A manifest without a version is a version 9 manifest, which wrote the version only
/// for a plugin component.
pub fn read_component_manifest(path: &Path) -> Result<ComponentManifest> {
    #[derive(Deserialize)]
    struct Version {
        version: Option<i32>,
    }
    let data = fs::read(path).with_context(|| path.display().to_string())?;
    let version: Version = serde_json::from_slice(&data).with_context(|| path.display().to_string())?;
    check_version(version.version.unwrap_or(9)).with_context(|| path.display().to_string())?;
    let manifest: ComponentManifest = serde_json::from_slice(&data).with_context(|| path.display().to_string())?;
    validate_manifest(&manifest).with_context(|| path.display().to_string())?;
    Ok(manifest)
}

fn check_version(version: i32) -> Result<()> {
    if version != MANIFEST_VERSION {
        bail!("Unsupported dev-build component manifest version {version}");
    }
    Ok(())
}

/// Checks the fields that serde cannot check: the version, the core classpath and the entry modes. Each core classpath
/// jar must be a component file of the manifest.
pub fn validate_manifest(manifest: &ComponentManifest) -> Result<()> {
    check_version(manifest.version)?;
    let component_files: HashSet<&str> = manifest
        .entries
        .iter()
        .filter(|entry| matches!(entry, ComponentEntry::ComponentFile { .. }))
        .map(ComponentEntry::relative_path)
        .collect();
    for jar in &manifest.core_class_path {
        if !component_files.contains(jar.as_str()) {
            bail!(
                "Dev-build component '{}' lists the core classpath jar '{jar}', which is not a component file of the manifest",
                manifest.kind
            );
        }
    }
    for entry in &manifest.entries {
        validate_entry_mode(entry)?;
    }
    Ok(())
}

/// Writes the manifest bytes and creates the parent directory.
pub fn write_component_manifest(path: &Path, manifest: &ComponentManifest) -> Result<()> {
    if let Some(parent) = path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        fs::create_dir_all(parent).with_context(|| parent.display().to_string())?;
    }
    fs::write(path, manifest.to_json()).with_context(|| path.display().to_string())
}

/// The value rules of the Kotlin `validateDevBuildEntryMode`. A directory mode has no other bit than the permission
/// bits. A file with a mode has the executable flag of that mode. The entry type makes every other rule of the Kotlin
/// check a decode error.
pub fn validate_entry_mode(entry: &ComponentEntry) -> Result<()> {
    match entry {
        ComponentEntry::Directory { relative_path, mode } if *mode > 0o777 => bail!("Invalid directory entry '{relative_path}'"),
        ComponentEntry::ComponentFile {
            relative_path,
            executable,
            mode: Some(mode),
            ..
        } if *mode > 0o777 || *executable != (mode & 0o111 != 0) => {
            bail!("Dev-build component entry '{relative_path}' has an invalid or conflicting file mode: {mode}")
        }
        _ => Ok(()),
    }
}

/// The logical mode of a component file. Bazel makes its outputs read-only, and the manifest records the writable
/// mode that the distribution gets.
pub const fn logical_component_mode(mode: u32) -> u32 {
    match mode {
        0o444 => 0o644,
        0o555 => 0o755,
        mode => mode,
    }
}

#[cfg(test)]
mod tests;
