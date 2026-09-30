// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The packer core behind `PackContentModuleJar`. It merges source jars and single files into one STORED jar with a
//! generated `__index__`. It can also write the native files of one library as a tree.
//!
//! The output bytes are frozen. The golden digests in the tests and the `./build/dev-dist.cmd jars` gate hold them to
//! the bytes that `JarPackager` writes. `API.md` beside this crate lists the public items.

mod error;
mod filters;
mod flagfile;
mod index;
mod inventory;
mod merge;
pub mod nativelib;
mod natives;
mod pack;
pub mod reader;
pub mod writer;

#[cfg(test)]
mod tests;

pub use error::{Error, Result};
pub use filters::{
    INDEX_FILE_NAME, MANIFEST_ENTRY_NAME, library_filter, library_name_filter, module_output_filter, module_output_name_filter,
};
pub use flagfile::{parse_flag_file, resolve_path};
pub use inventory::{InventoryReport, write_inventory};
pub use merge::{Filter, ManifestMode, MergeReport, MergeSpec, Source};
pub use natives::NativeSpec;
pub use pack::duplicate_line;
pub use reader::{Entry, Jar};
pub use writer::{DirectoryMode, Writer};
