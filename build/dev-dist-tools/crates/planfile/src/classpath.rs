//! The record of one plugin in `plugins/plugin-classpath.txt`.
//!
//! The dev-dist collector writes the record for the packed shape. [`crate::derive`] writes it for a plan whose
//! preparation the packer executes. Both call [`record`], so one algorithm serves both shapes.
//!
//! Every plugin directory and jar name in the repository is ASCII text, so [`record`] refuses any other name. For such a
//! name, Java's modified UTF-8 is the name itself, and a UTF-16 length is the byte length. A Kotlin comparison that
//! ignores case is then an ASCII comparison.

use std::cmp::Ordering;

use anyhow::{Result, bail};

/// Writes the record of one plugin in `plugins/plugin-classpath.txt`.
///
/// The layout is the Java `DataOutputStream` form of `writePluginClassPathEntryData` (`orderedAssets.kt`):
/// `writeShort(count)`, `writeUTF(pluginDirName)`, `writeInt(descriptor length)` and the descriptor bytes, then
/// `writeUTF(relativePath)` per classpath jar. `writeUTF` is a big-endian `u16` length and the text. `jars` are the
/// classpath jars relative to the plugin directory in their declared order. The record keeps the distinct jars in the
/// order of `writeOrderedPluginClassPathEntry`.
pub fn record<S: AsRef<str>>(plugin_dir_name: &str, descriptor: &[u8], jars: &[S]) -> Result<Vec<u8>> {
    let names = order(plugin_dir_name, jars);
    let Ok(count) = u16::try_from(names.len()) else {
        bail!("plugin {plugin_dir_name} has too many classpath jars: {}", names.len());
    };
    let Ok(descriptor_length) = u32::try_from(descriptor.len()) else {
        bail!("plugin {plugin_dir_name} has a descriptor of {} bytes", descriptor.len());
    };
    let mut data = Vec::with_capacity(2 + descriptor.len() + 64 * (names.len() + 1));
    data.extend_from_slice(&count.to_be_bytes());
    append_java_utf(&mut data, plugin_dir_name)?;
    data.extend_from_slice(&descriptor_length.to_be_bytes());
    data.extend_from_slice(descriptor);
    for name in &names {
        append_java_utf(&mut data, name)?;
    }
    Ok(data)
}

/// Java `writeUTF` of an ASCII name. Modified UTF-8 writes NUL as two bytes, so the name must not hold NUL either.
fn append_java_utf(data: &mut Vec<u8>, value: &str) -> Result<()> {
    if !value.bytes().all(|byte| (1..0x80).contains(&byte)) {
        bail!("the plugin classpath name {value:?} is not ASCII text without NUL");
    }
    let Ok(length) = u16::try_from(value.len()) else {
        bail!("plugin classpath value is too long: {value:?}");
    };
    data.extend_from_slice(&length.to_be_bytes());
    data.extend_from_slice(value.as_bytes());
    Ok(())
}

/// `writeOrderedPluginClassPathEntry` (`orderedAssets.kt`): the distinct jars, sorted by
/// [`put_more_likely_plugin_jars_first`] when there is more than one.
pub(crate) fn order<S: AsRef<str>>(plugin_dir_name: &str, jars: &[S]) -> Vec<String> {
    let mut names: Vec<String> = Vec::with_capacity(jars.len());
    for jar in jars {
        let jar = jar.as_ref();
        if !names.iter().any(|name| name == jar) {
            names.push(jar.to_owned());
        }
    }
    if names.len() > 1 {
        put_more_likely_plugin_jars_first(plugin_dir_name, &mut names);
    }
    names
}

/// The comparator of the same name in `com.intellij.platform.util` (`plugin.kt`) for ASCII names. Java's
/// `Collections.sort` is stable, and so is this sort.
pub(crate) fn put_more_likely_plugin_jars_first(plugin_dir_name: &str, files: &mut [String]) {
    files.sort_by(|first, second| compare_likely_plugin_jars(plugin_dir_name, file_name(first), file_name(second)));
}

fn file_name(path: &str) -> &str {
    path.rsplit_once('/').map_or(path, |(_, name)| name)
}

fn compare_likely_plugin_jars(plugin_dir_name: &str, first: &str, second: &str) -> Ordering {
    // A trait decides the comparison when only one of the two names has it.
    let last = |first_has: bool, second_has: bool| (first_has != second_has).then(|| first_has.cmp(&second_has));
    let first_of = |first_has: bool, second_has: bool| (first_has != second_has).then(|| second_has.cmp(&first_has));
    let starts_with_plugin_name = |name: &str| {
        name.get(..plugin_dir_name.len())
            .is_some_and(|prefix| prefix.eq_ignore_ascii_case(plugin_dir_name))
    };
    last(first.starts_with("resources"), second.starts_with("resources"))
        .or_else(|| last(is_like_versioned_library_name(first), is_like_versioned_library_name(second)))
        .or_else(|| first_of(starts_with_plugin_name(first), starts_with_plugin_name(second)))
        .or_else(|| first_of(first.ends_with("-idea.jar"), second.ends_with("-idea.jar")))
        .or_else(|| first_of(first == "database-plugin.jar", second == "database-plugin.jar"))
        .unwrap_or_else(|| first.len().cmp(&second.len()))
}

/// The private `fileNameIsLikeVersionedLibraryName` of `plugin.kt`: the text after the last `-` starts with a digit,
/// or with `m` or `M` and then a digit.
fn is_like_versioned_library_name(name: &str) -> bool {
    let Some((_, version)) = name.rsplit_once('-') else {
        return false;
    };
    match version.as_bytes() {
        [b'm' | b'M', next, ..] | [next, ..] => next.is_ascii_digit(),
        [] => false,
    }
}

#[cfg(test)]
mod tests;
