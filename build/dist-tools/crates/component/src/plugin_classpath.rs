//! The place of `plugins/plugin-classpath.txt`, the Java `DataOutputStream` form that `PluginClassPath` reads.
//!
//! The collector writes the record of each plugin component, and the composer joins the records into the file. The
//! local layout and the fingerprint name the file by [`PLUGIN_CLASSPATH`].

/// The path of the plugin classpath file in a distribution.
pub const PLUGIN_CLASSPATH: &str = "plugins/plugin-classpath.txt";
