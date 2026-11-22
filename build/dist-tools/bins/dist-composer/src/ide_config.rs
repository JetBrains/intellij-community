//! The file that the Java `DevIdeConfig` reads.

use std::fs;
use std::path::Path;

use anyhow::{Context as _, Result};
use component::paths;

/// The text of the config file. When the home is below the directory of the config file, the text names the home
/// relative to the config file. Then the pair can move as a unit. Both paths are absolute and normalized.
///
/// Each path comes from a UTF-8 argument, so the text form loses nothing. `additional.modules` keeps the order of
/// `additional_modules`. `bundled.modules` names the main modules of the bundled plugin components, so a reader finds a
/// bundled plugin that no row requested. A module can occur in both keys.
pub(crate) fn dev_ide_config_text<S: AsRef<str>>(
    config_file: &Path,
    home: &Path,
    main_class: &str,
    platform_prefix: &str,
    additional_modules: &[S],
    bundled_modules: &[S],
) -> String {
    let home_path = config_file
        .parent()
        .and_then(|config_directory| home.strip_prefix(config_directory).ok())
        .unwrap_or(home);
    let join = |modules: &[S]| modules.iter().map(AsRef::as_ref).collect::<Vec<&str>>().join(",");
    format!(
        "home.path={}\nmain.class.name={main_class}\nplatform.prefix={platform_prefix}\nadditional.modules={}\nbundled.modules={}\n",
        paths::to_slash(&home_path.to_string_lossy()),
        join(additional_modules),
        join(bundled_modules)
    )
}

/// Writes the config file and creates its directory.
pub(crate) fn write_dev_ide_config<S: AsRef<str>>(
    config_file: &Path,
    home: &Path,
    main_class: &str,
    platform_prefix: &str,
    additional_modules: &[S],
    bundled_modules: &[S],
) -> Result<()> {
    if let Some(config_directory) = config_file.parent() {
        fs::create_dir_all(config_directory).with_context(|| config_directory.display().to_string())?;
    }
    let content = dev_ide_config_text(config_file, home, main_class, platform_prefix, additional_modules, bundled_modules);
    fs::write(config_file, content).with_context(|| config_file.display().to_string())
}

#[cfg(test)]
mod tests;
