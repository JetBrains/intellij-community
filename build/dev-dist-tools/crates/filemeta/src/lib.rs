//! The file metadata of the dev-distribution tools.
//!
//! The file metadata (the inventory JSON, version 1) lists the files, directories and symbolic links of a payload
//! directory. Kotlin and the other tools read the same bytes, so the format is a frozen contract. See `API.md`.

mod entry;
mod inventory;
mod json;
mod links;
pub mod xxh3;

pub use entry::{Entry, EntryType, Error, merge, validate_path};
pub use inventory::{hash_file, hash_symlink_target, inspect, inventory, permissions, read_link_target};
pub use json::{read, write};
pub use links::{clean_link_target, path_identity, validate_links};

#[cfg(test)]
mod tests;
