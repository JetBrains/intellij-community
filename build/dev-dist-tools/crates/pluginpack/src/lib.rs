//! The plugin remainder packer. It plans the recipe of one complex plugin against its input catalogue. Then it writes
//! the remainder assets of the plugin into one directory, and the inventory into a separate file.
//!
//! [`plan`] validates the recipe without file system access. [`Execution::write`] executes it. The contract types are
//! in `planfile::contract`, and `planfile::derive` compiles them from the plan file.
//!
//! The Go packer supported more shapes than the checked-in plan files use. The crate ports only the shapes in use, and
//! it refuses every other shape with an error that names it. `API.md` beside this crate lists the refused shapes.

mod error;
mod execute;
mod gzip_resources;
mod layout;
mod layout_archive;
mod layout_writer;
mod paths;
mod plan;

pub use error::{Error, Result};
pub use gzip_resources::write_gzip_resources;
pub use plan::{Execution, plan, validate_assets, validate_link_graph};

#[cfg(test)]
mod tests;
