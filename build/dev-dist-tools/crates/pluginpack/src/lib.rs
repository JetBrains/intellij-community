//! The plugin remainder packer. It plans the recipe of one complex plugin against its input catalogue. Then it writes
//! the remainder assets of the plugin into one directory, and the inventory into a separate file.
//!
//! [`plan`] validates the recipe without file system access. [`Execution::write`] executes it. The contract types are
//! in `planfile::contract`, and `planfile::derive` compiles them from the plan file. The asset rules and the link-graph
//! rules are in `planfile::validate`, because the collector applies them too.
//!
//! The crate supports only the shapes that the checked-in plan files use, and it refuses every other shape with an
//! error that names it. `API.md` beside this crate lists the refused shapes. Every function that can fail returns
//! `anyhow::Result`, and `{:#}` prints the error with its context.

mod execute;
mod gzip_resources;
mod layout;
mod layout_archive;
mod layout_writer;
mod paths;
mod plan;

pub use gzip_resources::write_gzip_resources;
pub use plan::{Execution, plan};

#[cfg(test)]
mod tests;
