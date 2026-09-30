//! The assembly of `plugins/plugin-classpath.txt`.
//!
//! The file is the Java `DataOutputStream` form that `PluginClassPath` reads: a prefix, the plugin count as a
//! big-endian `u16`, then one record per plugin. The collector writes the record of each plugin component with
//! `planfile::classpath::record`. This module joins the records.

/// The plugin classpath file: the prefix, the plugin count as a big-endian `u16`, then the records of every component
/// in component order.
pub(crate) fn compose<P: AsRef<[u8]>>(prefix: &[u8], plugin_count: u16, parts: &[P]) -> Vec<u8> {
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
