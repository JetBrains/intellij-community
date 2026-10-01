//! Component manifest v9: the collector writes it, and the composer reads it.

use std::collections::HashSet;
use std::fs;
use std::io;
use std::path::Path;

use anyhow::{Context as _, Result, bail};
use serde::{Deserialize, Serialize};
use serde_json::ser::{Formatter, PrettyFormatter};

use crate::json;

/// The only manifest version that the composer accepts. A manifest without a version has this version.
pub const MANIFEST_VERSION: i32 = 9;

/// The type of a manifest entry.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum ComponentEntryType {
    /// A file with bytes.
    #[default]
    ComponentFile,
    /// A directory. It has a mode and no hash.
    Directory,
    /// A relative symbolic link.
    Symlink,
}

impl ComponentEntryType {
    /// The JSON text of the type.
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::ComponentFile => "component-file",
            Self::Directory => "directory",
            Self::Symlink => "symlink",
        }
    }
}

/// The manifest of one component. A `None` main class is a component that contributes files and nothing else.
///
/// The field order is the order of the Go collector output.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ComponentManifest {
    /// The collector writes the version only for a plugin component. `None` reads as [`MANIFEST_VERSION`].
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub version: Option<i32>,
    pub kind: String,
    pub platform_prefix: String,
    pub os: String,
    pub arch: String,
    /// Always empty. The composition spec declares the modules, and [`validate_manifest`] refuses a module here.
    pub additional_modules: Vec<String>,
    pub main_class: Option<String>,
    pub core_class_path: Vec<String>,
    pub entries: Vec<ComponentEntry>,
    /// 1 for a plugin component and 0 for all other components.
    #[serde(default, skip_serializing_if = "is_zero")]
    pub plugin_count: u32,
}

/// One entry of a component manifest. A `None` field is an absent field.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ComponentEntry {
    pub relative_path: String,
    #[serde(rename = "type")]
    pub entry_type: ComponentEntryType,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub hash: Option<i64>,
    #[serde(default, skip_serializing_if = "is_false")]
    pub executable: bool,
    #[serde(default, skip_serializing_if = "is_none_or_empty")]
    pub source: Option<String>,
    #[serde(default, skip_serializing_if = "is_none_or_empty")]
    pub symlink_target: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub mode: Option<u32>,
}

const fn is_zero(value: &u32) -> bool {
    *value == 0
}

const fn is_false(value: &bool) -> bool {
    !*value
}

#[expect(clippy::ref_option, reason = "serde's skip_serializing_if calls fn(&T)")]
fn is_none_or_empty(value: &Option<String>) -> bool {
    value.as_deref().is_none_or(str::is_empty)
}

impl ComponentManifest {
    /// Tells if the component fits every target platform.
    pub const fn platform_neutral(&self) -> bool {
        self.os.is_empty() && self.arch.is_empty()
    }

    /// The version, with the default for an absent one.
    pub fn effective_version(&self) -> i32 {
        self.version.unwrap_or(MANIFEST_VERSION)
    }

    /// The bytes that the Go collector writes: `encoding/json` with a two-space indent and no HTML escape, and no
    /// trailing newline.
    pub fn to_json(&self) -> Vec<u8> {
        let mut output = Vec::new();
        let mut serializer = serde_json::Serializer::with_formatter(&mut output, GoFormatter::new());
        self.serialize(&mut serializer).expect("a manifest always encodes");
        output
    }
}

impl ComponentEntry {
    /// The file inventory entry of this entry, for the checks of [`filemeta::merge`]. A file without a mode gets the
    /// conventional mode.
    pub fn to_metadata(&self) -> filemeta::Entry {
        let (entry_type, mode) = match self.entry_type {
            ComponentEntryType::ComponentFile => (
                filemeta::EntryType::File,
                self.mode.unwrap_or(if self.executable { 0o755 } else { 0o644 }),
            ),
            ComponentEntryType::Directory => (filemeta::EntryType::Directory, self.mode.unwrap_or_default()),
            ComponentEntryType::Symlink => (filemeta::EntryType::Symlink, self.mode.unwrap_or_default()),
        };
        filemeta::Entry {
            relative_path: self.relative_path.clone(),
            entry_type,
            hash: self.hash.unwrap_or_default(),
            size: 0,
            mode,
            executable: self.executable,
            symlink_target: self.symlink_target.clone().unwrap_or_default(),
        }
    }
}

/// Reads a manifest, then applies [`validate_manifest`].
pub fn read_component_manifest(path: &Path) -> Result<ComponentManifest> {
    let manifest: ComponentManifest = json::read(path)?;
    validate_manifest(&manifest).with_context(|| path.display().to_string())?;
    Ok(manifest)
}

/// Checks the fields that serde cannot check: the version, the plugin count, the modules, the core classpath and the entry
/// modes. Each core classpath jar must be a component file of the manifest.
pub fn validate_manifest(manifest: &ComponentManifest) -> Result<()> {
    if manifest.effective_version() != MANIFEST_VERSION {
        bail!("Unsupported dev-build component manifest version {}", manifest.effective_version());
    }
    if manifest.plugin_count > 1 {
        bail!(
            "Dev-build component '{}' reports {} plugins, and a component holds at most one",
            manifest.kind,
            manifest.plugin_count
        );
    }
    if !manifest.additional_modules.is_empty() {
        bail!(
            "Dev-build component '{}' lists additional modules, and only the composition spec declares them",
            manifest.kind
        );
    }
    let component_files: HashSet<&str> = manifest
        .entries
        .iter()
        .filter(|entry| entry.entry_type == ComponentEntryType::ComponentFile)
        .map(|entry| entry.relative_path.as_str())
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

/// The Kotlin `validateDevBuildEntryMode`. A directory has a mode and nothing else. A file with a mode has the
/// executable flag of that mode.
pub fn validate_entry_mode(entry: &ComponentEntry) -> Result<()> {
    if entry.entry_type == ComponentEntryType::Directory {
        if entry.hash.is_some() || entry.source.is_some() || entry.symlink_target.is_some() || entry.executable || !valid_mode(entry.mode) {
            bail!("Invalid directory entry '{}'", entry.relative_path);
        }
        return Ok(());
    }
    if entry.hash.is_none() {
        bail!("Dev-build component entry '{}' requires a hash", entry.relative_path);
    }
    let Some(mode) = entry.mode else {
        return Ok(());
    };
    if !valid_mode(entry.mode)
        || entry.symlink_target.is_some()
        || entry.entry_type != ComponentEntryType::ComponentFile
        || entry.executable != (mode & 0o111 != 0)
    {
        bail!(
            "Dev-build component entry '{}' has an invalid or conflicting file mode: {mode}",
            entry.relative_path
        );
    }
    Ok(())
}

fn valid_mode(mode: Option<u32>) -> bool {
    mode.is_some_and(|mode| mode <= 0o777)
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

/// The formatter of Go `encoding/json` with `SetIndent("", "  ")` and `SetEscapeHTML(false)`.
///
/// It is hand-written because the manifest bytes are frozen, and `PrettyFormatter` does not escape U+2028 and U+2029
/// as Go does. The other escapes are the ones of serde_json.
struct GoFormatter<'a> {
    pretty: PrettyFormatter<'a>,
}

impl GoFormatter<'_> {
    fn new() -> Self {
        Self {
            pretty: PrettyFormatter::with_indent(b"  "),
        }
    }
}

impl Formatter for GoFormatter<'_> {
    fn write_string_fragment<W: ?Sized + io::Write>(&mut self, writer: &mut W, fragment: &str) -> io::Result<()> {
        let mut rest = fragment;
        while let Some(index) = rest.find(['\u{2028}', '\u{2029}']) {
            writer.write_all(&rest.as_bytes()[..index])?;
            let separator = if rest[index..].starts_with('\u{2028}') {
                b"\\u2028"
            } else {
                b"\\u2029"
            };
            writer.write_all(separator)?;
            rest = &rest[index + '\u{2028}'.len_utf8()..];
        }
        writer.write_all(rest.as_bytes())
    }

    fn begin_array<W: ?Sized + io::Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty.begin_array(writer)
    }

    fn end_array<W: ?Sized + io::Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty.end_array(writer)
    }

    fn begin_array_value<W: ?Sized + io::Write>(&mut self, writer: &mut W, first: bool) -> io::Result<()> {
        self.pretty.begin_array_value(writer, first)
    }

    fn end_array_value<W: ?Sized + io::Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty.end_array_value(writer)
    }

    fn begin_object<W: ?Sized + io::Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty.begin_object(writer)
    }

    fn end_object<W: ?Sized + io::Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty.end_object(writer)
    }

    fn begin_object_key<W: ?Sized + io::Write>(&mut self, writer: &mut W, first: bool) -> io::Result<()> {
        self.pretty.begin_object_key(writer, first)
    }

    fn begin_object_value<W: ?Sized + io::Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty.begin_object_value(writer)
    }

    fn end_object_value<W: ?Sized + io::Write>(&mut self, writer: &mut W) -> io::Result<()> {
        self.pretty.end_object_value(writer)
    }
}

#[cfg(test)]
mod tests;
