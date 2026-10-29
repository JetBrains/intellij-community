//! The composition of the component manifests into one distribution or into launch metadata only.
//!
//! This module holds every step that reads only manifests and records. [`crate::merge`] copies the component files
//! of a full distribution. The one exception is [`check_launch_class_path`], which reads the copied `product-info.json`.

use std::collections::{BTreeMap, BTreeSet, HashMap, HashSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use component::classpath;
use component::layout::{CORE_CLASSPATH_FILE, FINGERPRINT_FILE, LOCAL_LAYOUT_FILE};
use component::manifest::{self, ComponentEntry, ComponentManifest};
use component::paths;
use component::plugin_classpath::PLUGIN_CLASSPATH;
use serde::Deserialize;

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
    /// The main modules of the bundled plugin components. [`compose_with_merge`] checks each one against the components.
    pub(crate) bundled_modules: Vec<String>,
    pub(crate) source_runfiles: Option<BTreeMap<PathBuf, String>>,
    pub(crate) source_directory_runfiles: Option<BTreeMap<PathBuf, String>>,
}

/// The values of the IDE config, the core classpath and the fingerprint of a composition.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct ComposedBuild {
    pub(crate) platform_prefix: String,
    pub(crate) main_class: String,
    pub(crate) additional_modules: Vec<String>,
    /// The bundled plugin modules, distinct and sorted.
    pub(crate) bundled_modules: Vec<String>,
    pub(crate) core_class_path: Vec<String>,
    pub(crate) fingerprint: String,
}

/// Checks that the components form one distribution. It reads no file, so a failure leaves no output.
///
/// Each manifest passed `manifest::validate_manifest` when `manifest::read_component_manifest` read it, so the checks
/// here concern the components together.
pub(crate) fn validate_components(components: &[DevBuildComponent], expected_fragments: &[String]) -> Result<()> {
    if components.is_empty() {
        bail!("At least one dev-build component is required");
    }
    let manifests: Vec<&ComponentManifest> = components.iter().map(|component| &component.manifest).collect();
    let kinds: Vec<&str> = manifests.iter().map(|manifest| manifest.kind.as_str()).collect();
    let first = manifests[0];
    let main_class = manifests.iter().find_map(|manifest| manifest.main_class.as_deref());
    let platform = manifests.iter().find(|manifest| !manifest.platform_neutral());
    let Some(main_class) = main_class else {
        bail!("No dev-build component declares an IDE main class: {}", kinds.join(", "));
    };
    for manifest in &manifests[1..] {
        if manifest.platform_prefix != first.platform_prefix {
            bail!(
                "Dev-build components have different products: '{}' and '{}'",
                first.platform_prefix,
                manifest.platform_prefix
            );
        }
        if let Some(platform) = platform.filter(|_| !manifest.platform_neutral())
            && (manifest.os != platform.os || manifest.arch != platform.arch)
        {
            bail!(
                "Dev-build components have different target platforms: '{}/{}' and '{}/{}'",
                platform.os,
                platform.arch,
                manifest.os,
                manifest.arch
            );
        }
        if let Some(other) = manifest.main_class.as_deref().filter(|other| *other != main_class) {
            bail!("Dev-build components have different IDE main classes: '{main_class}' and '{other}'");
        }
    }

    let missing_parts: Vec<String> = components
        .iter()
        .filter(|component| component.manifest.plugin && component.plugin_classpath_part.is_none())
        .map(|component| format!("{} (1)", component.manifest.kind))
        .collect();
    if !missing_parts.is_empty() {
        bail!(
            "Dev-build components report plugins but provide no plugin-classpath records: {}",
            missing_parts.join(", ")
        );
    }

    let (present, duplicates) = count_kinds(&kinds);
    if !duplicates.is_empty() {
        bail!(
            "Dev-build fragment kinds must be unique, but these occur more than once: {}",
            duplicates.join(", ")
        );
    }
    if !expected_fragments.is_empty() {
        let (expected, duplicate_expected) = count_kinds(expected_fragments);
        if !duplicate_expected.is_empty() {
            bail!(
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
            bail!(message);
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
            mode: manifest::conventional_mode(false),
            ..filemeta::Entry::default()
        })
        .collect();
    for entry in manifests.iter().flat_map(|manifest| &manifest.entries) {
        if !destinations.insert(entry.relative_path()) {
            bail!("Dev-build components both provide '{}'", entry.relative_path());
        }
        entries.push(entry.to_metadata());
    }
    filemeta::merge(&entries)?;
    Ok(())
}

/// Checks that the components form one distribution, then assembles them at `target`.
///
/// The function runs [`validate_components`] and [`validate_destinations`] first. A failure there creates nothing.
/// Then it requires that `target` is absent or an empty directory, and it creates it. For a full distribution, it
/// calls [`merge::merge_components`] once with the components in spec order, under `span`, and then
/// [`check_launch_class_path`]. Launch metadata never copies a component file, so it checks no launch. Then the
/// function writes the plugin classpath, writes the local layout for launch metadata, and computes the fingerprint. The
/// caller writes `core-classpath.txt`, `fingerprint.txt` and the IDE config.
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
    let bundled_modules = bundled_modules(&manifests, &options.bundled_modules)?;
    let first = manifests[0];
    let main_class = manifests
        .iter()
        .find_map(|manifest| manifest.main_class.clone())
        .expect("validate_components requires a main class");

    match fs::read_dir(target) {
        Ok(mut children) => {
            if children.next().is_some() {
                bail!("The dev-build composition target must be empty: {}", target.display());
            }
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => return Err(error).with_context(|| target.display().to_string()),
    }
    fs::create_dir_all(target).with_context(|| target.display().to_string())?;
    if options.source_runfiles.is_none() {
        merge(components, target)?;
    }

    let plugin_classpath = write_plugin_classpath(components, target, options.plugin_classpath_prefix.as_deref())?;
    let additional_modules = distinct(&options.additional_modules);
    let core_class_path: Vec<&str> = manifests
        .iter()
        .flat_map(|manifest| manifest.core_class_path.iter().map(String::as_str))
        .collect();
    let core_class_path = classpath::order_core_classpath_entries(&core_class_path);
    if options.source_runfiles.is_none() {
        check_launch_class_path(&manifests, target, &core_class_path)?;
    }
    if let Some(source_runfiles) = &options.source_runfiles {
        local_layout::write_local_layout(
            &manifests,
            target,
            source_runfiles,
            plugin_classpath.is_some(),
            options.source_directory_runfiles.as_ref(),
        )?;
    }
    let fingerprint = fingerprint::compute_ide_fingerprint_from_components(&manifests, plugin_classpath.as_deref(), &additional_modules)?;
    Ok(ComposedBuild {
        platform_prefix: first.platform_prefix.clone(),
        main_class,
        additional_modules,
        bundled_modules,
        core_class_path,
        fingerprint,
    })
}

/// The bundled plugin modules of the spec, distinct and sorted. Each one must be the kind of a plugin component, so the
/// IDE config states no plugin that the distribution does not hold.
fn bundled_modules(manifests: &[&ComponentManifest], modules: &[String]) -> Result<Vec<String>> {
    let plugins: HashSet<&str> = manifests
        .iter()
        .filter(|manifest| manifest.plugin)
        .map(|manifest| manifest.kind.as_str())
        .collect();
    let modules: BTreeSet<&str> = modules.iter().map(String::as_str).collect();
    let missing: Vec<&str> = modules.iter().copied().filter(|module| !plugins.contains(module)).collect();
    if !missing.is_empty() {
        bail!(
            "Bundled plugin modules name no plugin component of the distribution: {}",
            missing.join(", ")
        );
    }
    Ok(modules.into_iter().map(str::to_owned).collect())
}

/// The places of `product-info.json` in a distribution: the root, and `Resources/` of a macOS app.
const PRODUCT_INFO_FILES: [&str; 2] = ["product-info.json", "Resources/product-info.json"];

/// The main class of a modular-loader launch, `BuildContext.ideMainClassName` of such a product.
const MODULAR_LOADER_MAIN_CLASS: &str = "com.intellij.platform.runtime.loader.IntellijLoader";

/// The boot class path of a modular-loader launch.
const PLATFORM_LOADER_JAR: &str = "platform-loader.jar";

/// The jar of the multi-routing file system. `-Xbootclasspath/a:` puts it on the boot class path when the file system is
/// on.
const NIO_FS_JAR: &str = "nio-fs.jar";

/// The fields of `product-info.json` that the check reads.
#[derive(Deserialize)]
struct ProductInfo {
    launch: Vec<Launch>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Launch {
    main_class: String,
    #[serde(default)]
    boot_class_path_jar_names: Vec<String>,
    #[serde(default)]
    additional_jvm_arguments: Vec<String>,
}

/// Checks that the main launch of the copied `product-info.json` starts with the core classpath of the distribution.
///
/// The native launcher builds the class path from `bootClassPathJarNames` alone, as `<home>/lib/<name>` for each name.
/// So a classic-loader launch must name each jar of `core_class_path`, compared as sets. The check drops `nio-fs.jar`
/// when an additional JVM argument puts it on `-Xbootclasspath/a:`. A modular-loader launch names `platform-loader.jar`
/// alone. A distribution without `product-info.json` at a production place has no launch to check.
fn check_launch_class_path(manifests: &[&ComponentManifest], target: &Path, core_class_path: &[String]) -> Result<()> {
    let Some(relative_path) = manifests
        .iter()
        .flat_map(|manifest| &manifest.entries)
        .filter(|entry| matches!(entry, ComponentEntry::ComponentFile { .. }))
        .map(ComponentEntry::relative_path)
        .find(|relative_path| PRODUCT_INFO_FILES.contains(relative_path))
    else {
        return Ok(());
    };
    let file = target.join(paths::from_slash(relative_path).as_ref());
    let data = fs::read(&file).with_context(|| file.display().to_string())?;
    let info: ProductInfo = serde_json::from_slice(&data).with_context(|| file.display().to_string())?;
    let Some(launch) = info.launch.first() else {
        bail!("{relative_path} states no launch");
    };
    if launch.main_class == MODULAR_LOADER_MAIN_CLASS {
        if launch.boot_class_path_jar_names != [PLATFORM_LOADER_JAR] {
            bail!(
                "{relative_path}: the main launch starts the modular loader, so its bootClassPathJarNames must be \
                 [{PLATFORM_LOADER_JAR}], not {:?}",
                launch.boot_class_path_jar_names
            );
        }
        return Ok(());
    }
    let nio_fs_on_boot_class_path = launch.additional_jvm_arguments.iter().any(|argument| {
        argument.strip_prefix("-Xbootclasspath/a:").is_some_and(|value| {
            value
                .split([':', ';'])
                .any(|entry| entry.rsplit(['/', '\\']).next() == Some(NIO_FS_JAR))
        })
    });
    let expected: BTreeSet<String> = core_class_path
        .iter()
        .map(|entry| entry.strip_prefix("lib/").map_or_else(|| format!("../{entry}"), str::to_owned))
        .filter(|name| !(nio_fs_on_boot_class_path && name == NIO_FS_JAR))
        .collect();
    let actual: BTreeSet<String> = launch.boot_class_path_jar_names.iter().cloned().collect();
    let missing: Vec<&String> = expected.difference(&actual).collect();
    let unexpected: Vec<&String> = actual.difference(&expected).collect();
    if !missing.is_empty() || !unexpected.is_empty() {
        bail!(
            "{relative_path}: the bootClassPathJarNames of the main launch differ from the core classpath; \
             missing: {missing:?}; not on the core classpath: {unexpected:?}"
        );
    }
    Ok(())
}

/// Writes `plugins/plugin-classpath.txt` from the prefix and the records of all components, and returns its bytes. The
/// plugin count between the two is the number of plugin components. The result is `None` when no component has
/// records.
pub(crate) fn write_plugin_classpath(components: &[DevBuildComponent], target: &Path, prefix: Option<&Path>) -> Result<Option<Vec<u8>>> {
    let kinds: Vec<&str> = components
        .iter()
        .filter(|component| component.plugin_classpath_part.is_some())
        .map(|component| component.manifest.kind.as_str())
        .collect();
    let plugin_count = components.iter().filter(|component| component.manifest.plugin).count();
    let Ok(plugin_count) = u16::try_from(plugin_count) else {
        bail!("The dev-build components report {plugin_count} plugins, and the plugin classpath holds at most 65535");
    };
    if kinds.is_empty() {
        return Ok(None);
    }
    let Some(prefix) = prefix else {
        bail!(
            "Components contributed plugins ({}), so the plugin-classpath prefix is required",
            kinds.join(", ")
        );
    };
    let file = target.join(paths::from_slash(PLUGIN_CLASSPATH).as_ref());
    let parent = file.parent().expect("the file is below the target");
    fs::create_dir_all(parent).with_context(|| parent.display().to_string())?;
    let prefix = fs::read(prefix).with_context(|| prefix.display().to_string())?;
    let mut parts = Vec::with_capacity(kinds.len());
    for part in components.iter().filter_map(|component| component.plugin_classpath_part.as_deref()) {
        parts.push(fs::read(part).with_context(|| part.display().to_string())?);
    }
    let content = plugin_classpath::compose(&prefix, plugin_count, &parts);
    fs::write(&file, &content).with_context(|| file.display().to_string())?;
    Ok(Some(content))
}

/// The map with the absolute path of each key. Two keys with one absolute path fail.
pub(crate) fn absolute_keys(source: &BTreeMap<String, String>) -> Result<BTreeMap<PathBuf, String>> {
    let mut result = BTreeMap::new();
    for (key, value) in source {
        if result.insert(paths::absolute_path(key)?, value.clone()).is_some() {
            bail!("Two dev-build runfile keys name one path: {key}");
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

/// The values in bytewise order. The order is stable and depends on no locale.
fn sorted<S: AsRef<str>>(values: &[S]) -> Vec<String> {
    let mut result: Vec<String> = values.iter().map(|value| value.as_ref().to_owned()).collect();
    result.sort();
    result
}

#[cfg(test)]
mod tests;
