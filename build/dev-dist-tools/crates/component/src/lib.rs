//! The component contract that the dev-distribution collector and composer share.
//!
//! A component is a manifest that names each file of one part of a dev distribution where the file already is. The
//! collector writes the manifest, and the composer reads the manifests of all components. Then it writes a
//! self-contained distribution or launch metadata only.

pub mod classpath;
pub mod json;
pub mod layout;
pub mod manifest;
pub mod paths;
pub mod plugin_classpath;

pub use manifest::{ComponentEntry, ComponentManifest};

#[cfg(test)]
mod test_support;
