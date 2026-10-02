//! The plan file of one complex plugin, and the execution contract that the plugin remainder packer derives from it.
//!
//! The crate root decodes a plan file (`<main module>[.<class>][.<platform>].dev-plan.json`) and compiles it into the
//! recipe, the asset rows and the plugin classpath record of the chain. A plan file carries no signature, so the crate
//! computes none. The generator writes every plan file, and `plugin-model-tool --check` regenerates each text and
//! compares it.
//!
//! The crate supports only the shapes that the checked-in plan files use. It refuses every other shape with an error
//! that names it.
//!
//! [`contract`] holds the contract types, [`json`] the JSON reader, and [`classpath`] the record of
//! `plugins/plugin-classpath.txt`. [`validate`] holds the rules of an asset table and of a link graph that the packer
//! and the collector share. Every function that can fail returns `anyhow::Result`, and a refusal names the plan element
//! or the file.

pub mod classpath;
mod compile;
pub mod contract;
pub mod json;
mod plan;
pub mod validate;

pub use compile::{Derivation, derive, omitted_assets};
pub use plan::{
    Asset, DEFAULT_MODE, EXECUTABLE_MODE, JarRecipe, JarSource, JarWriter, LayoutAssetPreparation, LayoutFormat, ManifestPolicy, Operation,
    OperationKind, PlanFile, SourceKind, from_slice, module_jar_asset, module_jar_recipe, read,
};

#[cfg(test)]
mod tests;
