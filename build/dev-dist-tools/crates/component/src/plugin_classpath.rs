//! The assembly of `plugins/plugin-classpath.txt`.
//!
//! The file is the Java `DataOutputStream` form that `PluginClassPath` reads: a prefix, the plugin count as a
//! big-endian `u16`, then one record per plugin. [`planfile::classpath::record`] writes a record. This module gives
//! the record of a component, checks it, and assembles the file.
//!
//! A record holds only ASCII names without NUL, and Java modified UTF-8 keeps the bytes of such a name.

use std::collections::HashSet;

use crate::error::Result;
use crate::fail;
use crate::inventory::SourcedFile;

/// The path of the plugin classpath file in a distribution.
pub const PLUGIN_CLASSPATH: &str = "plugins/plugin-classpath.txt";

/// The record of a component's classpath files. A file's name in the record is relative to the plugin directory.
pub fn component_record(plugin_directory: &str, descriptor: &[u8], files: &[SourcedFile]) -> Result<Vec<u8>> {
    let names: Vec<&str> = class_path_names(plugin_directory, files).collect();
    Ok(planfile::classpath::record(directory_name(plugin_directory), descriptor, &names)?)
}

/// The last name of the plugin directory, a relative path in slash form.
fn directory_name(plugin_directory: &str) -> &str {
    plugin_directory.rsplit_once('/').map_or(plugin_directory, |(_, name)| name)
}

fn class_path_names<'a>(plugin_directory: &'a str, files: &'a [SourcedFile]) -> impl Iterator<Item = &'a str> + 'a {
    let prefix = format!("{plugin_directory}/");
    files.iter().filter(|file| file.class_path).map(move |file| {
        let name = file.relative_path.as_str();
        name.strip_prefix(prefix.as_str()).unwrap_or(name)
    })
}

/// Checks that a record names the plugin directory and each classpath file once, and nothing else.
pub fn validate_component_record(data: &[u8], plugin_directory: &str, files: &[SourcedFile]) -> Result<()> {
    let directory = directory_name(plugin_directory);
    let mut expected: HashSet<&[u8]> = HashSet::new();
    for name in std::iter::once(directory).chain(class_path_names(plugin_directory, files)) {
        if !name.is_ascii() || name.contains('\0') {
            fail!("plugin classpath name is not ASCII text without NUL: {name:?}");
        }
    }
    expected.extend(class_path_names(plugin_directory, files).map(str::as_bytes));
    let mut input = data;
    let count = read_u16(&mut input);
    if count.is_none_or(|count| usize::from(count) != expected.len()) {
        fail!("plugin classpath count does not match the declared assets");
    }
    let count = count.unwrap_or_default();
    if read_name(&mut input).is_none_or(|name| name != directory.as_bytes()) {
        fail!("plugin classpath names the wrong directory");
    }
    let descriptor_size = read_u32(&mut input);
    let Some(descriptor_size) = descriptor_size.filter(|&size| size as usize <= input.len()) else {
        fail!("plugin classpath has an invalid descriptor size");
    };
    input = &input[descriptor_size as usize..];
    for _ in 0..count {
        match read_name(&mut input) {
            Some(name) if expected.remove(name) => {}
            _ => fail!("plugin classpath contains an undeclared, excluded, or repeated asset"),
        }
    }
    if !expected.is_empty() || !input.is_empty() {
        fail!("plugin classpath has missing assets or trailing data");
    }
    Ok(())
}

fn read_u16(input: &mut &[u8]) -> Option<u16> {
    let (head, rest) = input.split_first_chunk::<2>()?;
    *input = rest;
    Some(u16::from_be_bytes(*head))
}

fn read_u32(input: &mut &[u8]) -> Option<u32> {
    let (head, rest) = input.split_first_chunk::<4>()?;
    *input = rest;
    Some(u32::from_be_bytes(*head))
}

fn read_name<'a>(input: &mut &'a [u8]) -> Option<&'a [u8]> {
    let size = usize::from(read_u16(input)?);
    if input.len() < size {
        return None;
    }
    let (name, rest) = input.split_at(size);
    *input = rest;
    Some(name)
}

/// The plugin classpath file: the prefix, the plugin count as a big-endian `u16`, then the records of every component
/// in component order.
pub fn compose<P: AsRef<[u8]>>(prefix: &[u8], plugin_count: u16, parts: &[P]) -> Vec<u8> {
    let size = prefix.len() + 2 + parts.iter().map(|part| part.as_ref().len()).sum::<usize>();
    let mut content = Vec::with_capacity(size);
    content.extend_from_slice(prefix);
    content.extend_from_slice(&plugin_count.to_be_bytes());
    for part in parts {
        content.extend_from_slice(part.as_ref());
    }
    content
}

#[cfg(test)]
mod tests;
