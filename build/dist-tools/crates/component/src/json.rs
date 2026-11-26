//! The JSON reader of the component contract.
//!
//! Starlark `json.encode` and `serde_json` write every file that [`read`] decodes. Neither quotes a number or repeats
//! a key, so the decoder is plain serde. Each type refuses an unknown key, and a repeated key fails.
//! `null` is valid only for an `Option` field, and an absent `Option` field is `None`. `serde` also reads a JSON array
//! in place of an object, in the field order. No producer writes such an array.

use std::path::Path;

use anyhow::{Context as _, Result};
use serde::de::DeserializeOwned;

/// Reads and decodes a JSON file. The error has the path as its context.
pub fn read<T: DeserializeOwned>(path: &Path) -> Result<T> {
    let data = std::fs::read(path).with_context(|| path.display().to_string())?;
    serde_json::from_slice(&data).with_context(|| path.display().to_string())
}
