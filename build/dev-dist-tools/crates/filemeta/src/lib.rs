//! The file metadata of the dev-distribution tools.
//!
//! The file metadata (the inventory JSON, version 1) lists the files, directories and symbolic links of a payload
//! directory. Kotlin and the other tools read the same bytes, so the format is a frozen contract. See `API.md`.

mod directory;
mod entry;
mod inventory;
mod json;
pub mod xxh3;

pub use directory::create_dir_all_0755;
pub use entry::{Entry, EntryType, Error, merge};
pub use inventory::{hash_file, hash_symlink_target, inspect, inventory, permissions, read_link_target};
pub use json::{read, write};

#[cfg(test)]
mod tests;
