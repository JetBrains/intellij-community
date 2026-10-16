//! The system properties of a distribution, as `DevLaunchProperties.kt` derives them.

use std::path::Path;

use anyhow::{Context, anyhow, bail};
use indexmap::IndexMap;
use serde::Deserialize;

/// The parts of `product-info.json` that a dev launch reads.
#[derive(Debug, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct ProductInfo {
    launch: Vec<LaunchInfo>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct LaunchInfo {
    additional_jvm_arguments: Vec<String>,
    custom_commands: Vec<CustomCommand>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(default, rename_all = "camelCase")]
struct CustomCommand {
    commands: Vec<String>,
    main_class: String,
    additional_jvm_arguments: Vec<String>,
}

impl ProductInfo {
    /// Reads a `product-info.json` file at any path.
    pub fn read_file(file: &Path) -> anyhow::Result<Self> {
        let data = std::fs::read(file).with_context(|| format!("read {}", file.display()))?;
        serde_json::from_slice(&data).context("read product-info.json")
    }
}

/// Reads the text of a properties file in the order of its lines. A repeated key keeps its first position and takes
/// its last value, as a Kotlin `LinkedHashMap` does.
pub(crate) fn parse_properties(data: &[u8]) -> anyhow::Result<IndexMap<String, String>> {
    let mut result = IndexMap::new();
    java_properties::PropertiesIter::new(data)
        .read_into(|key, value| {
            result.insert(key, value);
        })
        .map_err(|error| anyhow!("{error}"))?;
    Ok(result)
}

/// Substitutes the `IDE_HOME` macro of `product-info.json` for the host OS, as `DevLaunchProperties.kt` does.
fn resolve_ide_home_macro(argument: &str, home: &str) -> String {
    let macro_name = match std::env::consts::OS {
        "windows" => "%IDE_HOME%",
        "macos" => "$APP_PACKAGE/Contents",
        _ => "$IDE_HOME",
    };
    argument.replace(macro_name, home)
}

/// Puts the `-D` argument `argument` into `properties`. A property without `=` gets an empty value.
pub(crate) fn put_system_property(properties: &mut IndexMap<String, String>, argument: &str) -> bool {
    let Some(property) = argument.strip_prefix("-D") else {
        return false;
    };
    let (key, value) = property.split_once('=').unwrap_or((property, ""));
    properties.insert(key.to_owned(), value.to_owned());
    true
}

/// The system properties of the distribution at `home`, as `getIdeSystemProperties` of `DevLaunchProperties.kt` reads
/// them. The sources are `idea_properties` and the `-D` lines of the vmoptions file `vm_options_file`. Then come
/// `jb.vmOptionsFile` with `vm_options_path` and the `-D` arguments of the first launch of `info`. `vm_options_path` is
/// the path of the vmoptions file in the home that the IDE starts from.
///
/// Java reads `idea.properties` as ISO-8859-1, and `java_properties` reads it as windows-1252. The two differ only in
/// the bytes 0x80 to 0x9F. Every file that the writer reads is ASCII, so the difference has no effect.
pub fn properties_of_files(
    idea_properties: &Path,
    vm_options_file: &Path,
    vm_options_path: &str,
    home: &str,
    info: &ProductInfo,
) -> anyhow::Result<IndexMap<String, String>> {
    let data = std::fs::read(idea_properties).with_context(|| format!("read {}", idea_properties.display()))?;
    let mut result = parse_properties(&data).with_context(|| idea_properties.display().to_string())?;
    for line in read_lines(vm_options_file)? {
        put_system_property(&mut result, &line);
    }
    result.insert("jb.vmOptionsFile".to_owned(), vm_options_path.to_owned());
    if let Some(launch) = info.launch.first() {
        for argument in &launch.additional_jvm_arguments {
            put_system_property(&mut result, &resolve_ide_home_macro(argument, home));
        }
    }
    Ok(result)
}

/// The system property that names the runtime module repository of the IDE.
pub const RUNTIME_MODULE_REPOSITORY_PROPERTY: &str = "intellij.platform.runtime.repository.path";

/// The main class and the system properties of the custom command of the distribution that handles `command`. This
/// is `readCustomCommandLaunch` of `DevLaunchProperties.kt`.
pub fn custom_command(home: &str, info: &ProductInfo, command: &str) -> anyhow::Result<(String, IndexMap<String, String>)> {
    let [launch] = info.launch.as_slice() else {
        bail!(
            "product-info.json of {home} states {} launches, and a dev distribution has one",
            info.launch.len()
        );
    };
    let Some(candidate) = launch
        .custom_commands
        .iter()
        .find(|candidate| candidate.commands.iter().any(|name| name == command))
    else {
        bail!("no custom command found for {command}");
    };
    if candidate.main_class.is_empty() {
        bail!("the custom command '{command}' names no main class");
    }
    let mut properties = IndexMap::new();
    for argument in &candidate.additional_jvm_arguments {
        let resolved = resolve_ide_home_macro(argument, home);
        let Some(property) = resolved.strip_prefix("-D") else {
            continue;
        };
        let (key, value) = property.split_once('=').unwrap_or((property, ""));
        if value.contains('$') {
            bail!("unsubstituted macro in JVM argument: {property}");
        }
        properties.insert(key.to_owned(), value.to_owned());
    }
    Ok((candidate.main_class.clone(), properties))
}

/// The lines of a text file without the line terminators.
pub fn read_lines(file: &Path) -> anyhow::Result<Vec<String>> {
    let text = std::fs::read_to_string(file).with_context(|| format!("read {}", file.display()))?;
    Ok(text.lines().map(str::to_owned).collect())
}

#[cfg(test)]
mod tests;
