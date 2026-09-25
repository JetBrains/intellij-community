//! The component contract that the dev-distribution collector, composer and launcher share.
//!
//! A component is a manifest that names each file of one part of a dev distribution where the file already is. The
//! collector writes the manifest, and the composer reads the manifests of all components. Then it writes a
//! self-contained distribution or launch metadata only. The launcher links a home from that metadata.

mod error;
mod json;

pub mod classpath;
pub mod compose;
pub mod fingerprint;
pub mod ide_config;
pub mod inventory;
pub mod layout;
pub mod local_home;
pub mod manifest;
pub mod paths;
pub mod plugin_classpath;
pub mod spec;

pub use error::{Error, Result};
pub use manifest::{ComponentEntry, ComponentEntryType, ComponentManifest};

#[cfg(test)]
mod test_support;
