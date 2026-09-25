//! The reader of the executed packaging recipe, the reader of a built distribution, and the purity weighing of the one
//! against the other.
//!
//! A dev-distribution fragment writes its executed packaging recipe as `<fragment>.plan.yaml`. The file is in the
//! content-report schema: `serializeContentEntries` of `intellij.platform.distributionContent` writes a list of
//! `FileEntry`, and `DevDistRecipe` sets `kind` and `sources` on each entry. The unit of the weighing is the output
//! and its bytes, not the source.
//!
//! The crate reads only the shape that `DevDistRecipe` writes. It refuses every other shape with an error that names the file, the line and the field.
//! `API.md` lists the refused input.
//!
//! The crate reads the shape of a file. It decides no packing question.

mod dist;
mod purity;
mod recipe;
#[cfg(test)]
mod test_support;

pub use dist::{Distribution, read_distribution};
pub use purity::{Blocker, OutputPurity, Purity, Weight, weigh_purity};
pub use recipe::{EntryKind, FileEntry, Recipe, RecipeSource, parse_recipe, read_recipes};

/// One refusal or I/O failure. The message names the file, and the line when the refusal is about the YAML text.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
#[error("{message}")]
pub struct Error {
    message: String,
}

impl Error {
    pub(crate) fn new(message: impl Into<String>) -> Self {
        Self { message: message.into() }
    }

    pub fn message(&self) -> &str {
        &self.message
    }
}
