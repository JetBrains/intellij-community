//! The JSON reader of the component contract.
//!
//! The collector, the composer and Starlark `json.encode` write every file that [`read`] decodes. None of them quotes
//! a number or repeats a key, so the decoder is plain serde. Each type refuses an unknown key, and a repeated key fails.
//! `null` is valid only for an `Option` field, and an absent `Option` field is `None`. `serde` also reads a JSON array
//! in place of an object, in the field order, where Go refuses it. No producer writes such an array.

use std::path::Path;

use serde::de::DeserializeOwned;

use crate::error::{Error, Result};

/// Reads and decodes a JSON file. The error names the file.
pub fn read<T: DeserializeOwned>(path: &Path) -> Result<T> {
    let data = std::fs::read(path).map_err(|error| Error::io(path, error))?;
    serde_json::from_slice(&data).map_err(|error| Error::json(path, error))
}
