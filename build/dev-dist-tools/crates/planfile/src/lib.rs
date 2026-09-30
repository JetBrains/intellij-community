//! The plan file of one complex plugin, and the execution contract that the plugin remainder packer derives from it.
//!
//! The crate root decodes a plan file (`<main module>[.<class>][.<platform>].dev-plan.json`) and compiles it into the
//! recipe, the asset rows and the plugin classpath record of the chain. It computes no signature: `plugin-model-tool
//! --check` owns the layout signature and every operation signature.
//!
//! The Go code supports more shapes than the checked-in plan files use. The crate ports only the shapes in use, and it
//! refuses every other shape with an error that names it.
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
    OperationKind, PlanFile, Preparation, SourceKind, from_slice, module_jar_asset, module_jar_recipe, read,
};

#[cfg(test)]
mod tests;
