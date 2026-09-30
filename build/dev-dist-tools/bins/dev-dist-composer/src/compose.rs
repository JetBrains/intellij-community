//! The composition of the component manifests into one distribution or into launch metadata only.
//!
//! This module holds every step that reads only manifests and records. [`crate::merge`] copies the component files
//! of a full distribution.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use component::classpath;
use component::layout::{CORE_CLASSPATH_FILE, FINGERPRINT_FILE, LOCAL_LAYOUT_FILE};
use component::manifest::{self, ComponentManifest};
use component::plugin_classpath::PLUGIN_CLASSPATH;
use component::{Error, Result, fail, paths};

use crate::spec::ComponentSources;
use crate::{fingerprint, local_layout, merge, plugin_classpath};

/// The files that the composer writes itself. No component can provide them.
pub(crate) const RESERVED_FILES: [&str; 4] = [CORE_CLASSPATH_FILE, FINGERPRINT_FILE, LOCAL_LAYOUT_FILE, PLUGIN_CLASSPATH];

/// One component to compose. Its manifest names each file where it already is.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub(crate) struct DevBuildComponent {
    pub(crate) manifest: ComponentManifest,
    /// The absolute path of the plugin records of the component. `None` is a component without plugin records.
    pub(crate) plugin_classpath_part: Option<PathBuf>,
    /// The artifacts that Bazel staged for the component, when the spec names source bindings.
    pub(crate) source_bindings: Option<ComponentSources>,
}

impl DevBuildComponent {
    pub(crate) const fn new(manifest: ComponentManifest) -> Self {
        Self {
            manifest,
            plugin_classpath_part: None,
            source_bindings: None,
        }
    }
}

/// The optional arguments of [`compose_components`]. A `None` `source_runfiles` requests a full distribution, and a
/// map requests launch metadata only. The keys of both maps are absolute paths, as [`absolute_keys`] gives them.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub(crate) struct ComposeOptions {
    pub(crate) plugin_classpath_prefix: Option<PathBuf>,
    pub(crate) expected_fragments: Vec<String>,
    pub(crate) additional_modules: Vec<String>,
    pub(crate) source_runfiles: Option<BTreeMap<String, String>>,
    pub(crate) source_directory_runfiles: Option<BTreeMap<String, String>>,
}

/// The values of the IDE config, the core classpath and the fingerprint of a composition.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct ComposedBuild {
    pub(crate) platform_prefix: String,
    pub(crate) main_class: String,
    pub(crate) additional_modules: Vec<String>,
    pub(crate) core_class_path: Vec<String>,
    pub(crate) fingerprint: String,
}

/// Checks that the components form one distribution. It reads no file, so a failure leaves no output.
pub(crate) fn validate_components(components: &[DevBuildComponent], expected_fragments: &[String]) -> Result<()> {
    if components.is_empty() {
        fail!("At least one dev-build component is required");
    }
    let manifests: Vec<&ComponentManifest> = components.iter().map(|component| &component.manifest).collect();
    for manifest in &manifests {
        manifest::validate_manifest(manifest)?;
    }
    let kinds: Vec<&str> = manifests.iter().map(|manifest| manifest.kind.as_str()).collect();
    let first = manifests[0];
    let main_class = manifests.iter().find_map(|manifest| manifest.main_class.as_deref());
    let platform = manifests.iter().find(|manifest| !manifest.platform_neutral());
    let Some(main_class) = main_class else {
        fail!("No dev-build component declares an IDE main class: {}", kinds.join(", "));
    };
    for manifest in &manifests[1..] {
        if manifest.platform_prefix != first.platform_prefix {
            fail!(
                "Dev-build components have different products: '{}' and '{}'",
                first.platform_prefix,
                manifest.platform_prefix
            );
        }
        if let Some(platform) = platform.filter(|_| !manifest.platform_neutral())
            && (manifest.os != platform.os || manifest.arch != platform.arch)
        {
            fail!(
                "Dev-build components have different target platforms: '{}/{}' and '{}/{}'",
                platform.os,
                platform.arch,
                manifest.os,
                manifest.arch
            );
        }
        if let Some(other) = manifest.main_class.as_deref().filter(|other| *other != main_class) {
            fail!("Dev-build components have different IDE main classes: '{main_class}' and '{other}'");
        }
    }

    let missing_parts: Vec<String> = components
        .iter()
        .filter(|component| component.manifest.plugin_count > 0 && component.plugin_classpath_part.is_none())
        .map(|component| format!("{} ({})", component.manifest.kind, component.manifest.plugin_count))
        .collect();
    if !missing_parts.is_empty() {
        fail!(
            "Dev-build components report plugins but provide no plugin-classpath records: {}",
            missing_parts.join(", ")
        );
    }

    let (present, duplicates) = count_kinds(&kinds);
    if !duplicates.is_empty() {
        fail!(
            "Dev-build fragment kinds must be unique, but these occur more than once: {}",
            duplicates.join(", ")
        );
    }
    if !expected_fragments.is_empty() {
        let (expected, duplicate_expected) = count_kinds(expected_fragments);
        if !duplicate_expected.is_empty() {
            fail!(
                "Expected dev-build fragment kinds must be unique, but these occur more than once: {}",
                duplicate_expected.join(", ")
            );
        }
        let missing = sorted_difference(&expected, &present);
        let unexpected = sorted_difference(&present, &expected);
        if !missing.is_empty() || !unexpected.is_empty() {
            let mut message = String::from("Dev-build fragments do not match the expected composition");
            if !missing.is_empty() {
                message.push_str(&format!("; missing: {}", missing.join(", ")));
            }
            if !unexpected.is_empty() {
                message.push_str(&format!("; unexpected: {}", unexpected.join(", ")));
            }
            message.push_str(&format!("; present: {}", sorted(&present).join(", ")));
            return Err(Error::Message(message));
        }
    }
    Ok(())
}

/// Checks every destination of the components, before the composer writes a file.
///
/// Each destination must be a valid inventory path, and only one component can provide it. The reserved files are
/// destinations too. [`filemeta::merge`] then checks the spellings, the ancestors and the link graph of all entries
/// together.
pub(crate) fn validate_destinations(manifests: &[&ComponentManifest]) -> Result<()> {
    let mut destinations: HashSet<&str> = HashSet::from(RESERVED_FILES);
    let mut entries: Vec<filemeta::Entry> = RESERVED_FILES
        .iter()
        .map(|name| filemeta::Entry {
            relative_path: (*name).to_owned(),
            mode: fscopy::conventional_mode(false),
            ..filemeta::Entry::default()
        })
        .collect();
    for entry in manifests.iter().flat_map(|manifest| &manifest.entries) {
        manifest::validate_entry_mode(entry)?;
        if !destinations.insert(&entry.relative_path) {
            fail!("Dev-build components both provide '{}'", entry.relative_path);
        }
        entries.push(entry.to_metadata());
    }
    filemeta::merge(&entries).map_err(|error| Error::msg(format!("{error:#}")))?;
    Ok(())
}

/// Checks that the components form one distribution, then assembles them at `target`.
///
/// The function runs [`validate_components`] and [`validate_destinations`] first. A failure there creates nothing.
/// Then it requires that `target` is absent or an empty directory, and it creates it. For a full distribution, it
/// calls [`merge::merge_components`] once with the components in spec order, under `span`. Launch metadata never copies
/// a component file. Then the function writes the plugin classpath, writes the local layout for launch metadata, and
/// computes the fingerprint. The caller writes `core-classpath.txt`, `fingerprint.txt` and the IDE config.
pub(crate) fn compose_components(
    components: &[DevBuildComponent],
    target: &Path,
    options: &ComposeOptions,
    span: &trace::Span,
) -> Result<ComposedBuild> {
    compose_with_merge(components, target, options, |components, target| {
        merge::merge_components(components, target, span)
    })
}

/// [`compose_components`] with another copy step. A test gives a step that copies nothing, so it checks the metadata
/// of a full distribution without payload.
pub(crate) fn compose_with_merge<M>(
    components: &[DevBuildComponent],
    target: &Path,
    options: &ComposeOptions,
    merge: M,
) -> Result<ComposedBuild>
where
    M: FnOnce(&[DevBuildComponent], &Path) -> Result<()>,
{
    validate_components(components, &options.expected_fragments)?;
    let manifests: Vec<&ComponentManifest> = components.iter().map(|component| &component.manifest).collect();
    validate_destinations(&manifests)?;
    let first = manifests[0];
    let main_class = manifests
        .iter()
        .find_map(|manifest| manifest.main_class.clone())
        .expect("validate_components requires a main class");

    match fs::read_dir(target) {
        Ok(mut children) => {
            if children.next().is_some() {
                fail!("The dev-build composition target must be empty: {}", target.display());
            }
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(Error::io(target, error)),
    }
    fs::create_dir_all(target).map_err(|error| Error::io(target, error))?;
    if options.source_runfiles.is_none() {
        merge(components, target)?;
    }

    let plugin_classpath_file = write_plugin_classpath(components, target, options.plugin_classpath_prefix.as_deref())?;
    let additional_modules = distinct(&options.additional_modules);
    let core_class_path: Vec<&str> = manifests
        .iter()
        .flat_map(|manifest| manifest.core_class_path.iter().map(String::as_str))
        .collect();
    let core_class_path = classpath::order_core_classpath_entries(&core_class_path);
    if let Some(source_runfiles) = &options.source_runfiles {
        local_layout::write_local_layout(
            &manifests,
            target,
            source_runfiles,
            plugin_classpath_file.is_some(),
            options.source_directory_runfiles.as_ref(),
        )?;
    }
    let fingerprint =
        fingerprint::compute_ide_fingerprint_from_components(&manifests, plugin_classpath_file.as_deref(), &additional_modules)?;
    Ok(ComposedBuild {
        platform_prefix: first.platform_prefix.clone(),
        main_class,
        additional_modules,
        core_class_path,
        fingerprint,
    })
}

/// Writes `plugins/plugin-classpath.txt` from the prefix and the records of all components. The plugin count between
/// the two covers the whole distribution. The result is `None` when no component has records.
pub(crate) fn write_plugin_classpath(components: &[DevBuildComponent], target: &Path, prefix: Option<&Path>) -> Result<Option<PathBuf>> {
    let kinds: Vec<&str> = components
        .iter()
        .filter(|component| component.plugin_classpath_part.is_some())
        .map(|component| component.manifest.kind.as_str())
        .collect();
    let plugin_count: u32 = components.iter().map(|component| component.manifest.plugin_count).sum();
    let Ok(plugin_count) = u16::try_from(plugin_count) else {
        fail!("The dev-build components report {plugin_count} plugins, and the plugin classpath holds at most 65535");
    };
    if kinds.is_empty() {
        return Ok(None);
    }
    let Some(prefix) = prefix else {
        fail!(
            "Components contributed plugins ({}), so the plugin-classpath prefix is required",
            kinds.join(", ")
        );
    };
    let file = target.join(paths::from_slash(PLUGIN_CLASSPATH).as_ref());
    let parent = file.parent().expect("the file is below the target");
    fs::create_dir_all(parent).map_err(|error| Error::io(parent, error))?;
    let prefix = fs::read(prefix).map_err(|error| Error::io(prefix, error))?;
    let mut parts = Vec::with_capacity(kinds.len());
    for part in components.iter().filter_map(|component| component.plugin_classpath_part.as_deref()) {
        parts.push(fs::read(part).map_err(|error| Error::io(part, error))?);
    }
    let content = plugin_classpath::compose(&prefix, plugin_count, &parts);
    fs::write(&file, content).map_err(|error| Error::io(&file, error))?;
    Ok(Some(file))
}

/// The map with the absolute path of each key. Two keys with one absolute path fail.
pub(crate) fn absolute_keys(source: &BTreeMap<String, String>) -> Result<BTreeMap<String, String>> {
    let mut result = BTreeMap::new();
    for (key, value) in source {
        if result.insert(paths::absolute_path(key)?, value.clone()).is_some() {
            fail!("Two dev-build runfile keys name one path: {key}");
        }
    }
    Ok(result)
}

/// The first occurrence of each value, in order.
pub(crate) fn distinct<S: AsRef<str>>(values: impl IntoIterator<Item = S>) -> Vec<String> {
    let mut known = HashSet::new();
    values
        .into_iter()
        .map(|value| value.as_ref().to_owned())
        .filter(|value| known.insert(value.clone()))
        .collect()
}

/// The distinct values in the order of first occurrence and the sorted values that occur more than once.
fn count_kinds<S: AsRef<str>>(values: &[S]) -> (Vec<String>, Vec<String>) {
    let mut counts: HashMap<&str, usize> = HashMap::with_capacity(values.len());
    let mut present = Vec::new();
    for value in values {
        let count = counts.entry(value.as_ref()).or_insert(0);
        if *count == 0 {
            present.push(value.as_ref().to_owned());
        }
        *count += 1;
    }
    let duplicates: Vec<&String> = present.iter().filter(|value| counts[value.as_str()] > 1).collect();
    let duplicates = sorted(&duplicates);
    (present, duplicates)
}

/// The values of `first` that `second` does not contain, sorted.
fn sorted_difference(first: &[String], second: &[String]) -> Vec<String> {
    let difference: Vec<&String> = first.iter().filter(|value| !second.contains(value)).collect();
    sorted(&difference)
}

/// The values in bytewise order. A fragment kind is ASCII, so this is also the Java string order of the Go tool.
fn sorted<S: AsRef<str>>(values: &[S]) -> Vec<String> {
    let mut result: Vec<String> = values.iter().map(|value| value.as_ref().to_owned()).collect();
    result.sort();
    result
}

#[cfg(test)]
mod tests;
