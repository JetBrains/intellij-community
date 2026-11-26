//! The plugin classpath record of a plugin component.
//!
//! [`planfile::classpath::record`] writes a record. This module gives the record of a component. The composer joins the
//! records into `plugins/plugin-classpath.txt`.

use anyhow::Result;

use crate::inventory::{Classpath, SourcedFile};

/// The record of a component's classpath files. A file's name in the record is relative to the plugin directory.
pub(crate) fn component_record(plugin_directory: &str, descriptor: &[u8], files: &[SourcedFile]) -> Result<Vec<u8>> {
    let names: Vec<&str> = class_path_names(plugin_directory, files).collect();
    planfile::classpath::record(directory_name(plugin_directory), descriptor, &names)
}

/// The last name of the plugin directory, a relative path in slash form.
fn directory_name(plugin_directory: &str) -> &str {
    plugin_directory.rsplit_once('/').map_or(plugin_directory, |(_, name)| name)
}

fn class_path_names<'a>(plugin_directory: &'a str, files: &'a [SourcedFile]) -> impl Iterator<Item = &'a str> + 'a {
    let prefix = format!("{plugin_directory}/");
    files.iter().filter(|file| file.classpath == Classpath::Plugin).map(move |file| {
        let name = file.relative_path.as_str();
        name.strip_prefix(prefix.as_str()).unwrap_or(name)
    })
}

#[cfg(test)]
mod tests;
