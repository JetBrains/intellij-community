//! The file that the Java `DevIdeConfig` reads.

use std::fs;
use std::path::Path;

use anyhow::{Context as _, Result};
use component::paths;

use crate::host_paths;

/// The text of the config file. When the home is below the directory of the config file, the text names the home
/// relative to the config file. Then the pair can move as a unit. Both paths are absolute and normalized.
pub(crate) fn dev_ide_config_text<S: AsRef<str>>(
    config_file: &str,
    home: &str,
    main_class: &str,
    platform_prefix: &str,
    additional_modules: &[S],
) -> String {
    let config_directory = host_paths::parent(config_file);
    let has_parent = config_directory != config_file;
    let mut home_path = home;
    if has_parent && Path::new(home).starts_with(config_directory) {
        home_path = home.strip_prefix(config_directory).unwrap_or(home);
        home_path = home_path.strip_prefix(paths::SEPARATOR).unwrap_or(home_path);
    }
    let modules: Vec<&str> = additional_modules.iter().map(AsRef::as_ref).collect();
    format!(
        "home.path={}\nmain.class.name={main_class}\nplatform.prefix={platform_prefix}\nadditional.modules={}\n",
        paths::to_slash(home_path),
        modules.join(",")
    )
}

/// Writes the config file and creates its directory.
pub(crate) fn write_dev_ide_config<S: AsRef<str>>(
    config_file: &str,
    home: &str,
    main_class: &str,
    platform_prefix: &str,
    additional_modules: &[S],
) -> Result<()> {
    let config_directory = host_paths::parent(config_file);
    if config_directory != config_file {
        fs::create_dir_all(config_directory).with_context(|| config_directory.to_owned())?;
    }
    let content = dev_ide_config_text(config_file, home, main_class, platform_prefix, additional_modules);
    fs::write(config_file, content).with_context(|| config_file.to_owned())
}

#[cfg(test)]
mod tests;
