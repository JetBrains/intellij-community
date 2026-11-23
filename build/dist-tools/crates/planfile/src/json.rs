//! The JSON reader of the plan file, the catalogue and the asset rows: `serde_json::from_slice` with the path in the
//! error.
//!
//! With a `deny_unknown_fields` type, `serde` refuses an unknown key, a repeated key, trailing data after the document
//! and invalid UTF-8. A key must match its field exactly. A `null` value of an `Option` field is an absent key. A
//! `null` value of any other field is an error.
//!
//! The reader has no check of its own. The producers are the kotlinx encoder of the plan file, Starlark `json.encode`
//! and `serde_json`, and none of them can write a repeated key. `serde` also reads a JSON array in place of an object,
//! in the field order. No producer writes such an array.

use std::path::Path;

use anyhow::{Context as _, Result};
use serde::de::DeserializeOwned;

/// Reads one JSON document. The error has the path as its context.
pub fn read<T: DeserializeOwned>(path: &Path) -> Result<T> {
    let data = std::fs::read(path).with_context(|| path.display().to_string())?;
    from_slice(&data).with_context(|| path.display().to_string())
}

/// Decodes one JSON document.
pub fn from_slice<T: DeserializeOwned>(data: &[u8]) -> Result<T> {
    Ok(serde_json::from_slice(data)?)
}

#[cfg(test)]
mod tests;
