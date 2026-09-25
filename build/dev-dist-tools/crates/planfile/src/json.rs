//! The JSON reader of the plan file, the catalogue and the asset rows: `serde_json::from_slice` with the path in the
//! error.
//!
//! With a `deny_unknown_fields` type, `serde` refuses what Go `pluginpack.ReadJSON` refuses: an unknown key, a
//! repeated key, trailing data after the document and invalid UTF-8. A key must match its field exactly. A `null`
//! value of an `Option` field is an absent key, the way Go decodes `null` into a pointer field. A `null` value of any
//! other field is an error, where Go keeps the zero value.
//!
//! The reader has no check of its own. The producers are the kotlinx encoder of the plan file, Starlark `json.encode`
//! and `serde_json`, and none of them can write a repeated key. `serde` also reads a JSON array in place of an object,
//! in the field order, where Go refuses it. No producer writes such an array.

use std::path::Path;

use serde::de::DeserializeOwned;

use crate::Error;

/// Reads one JSON document. The error starts with the path.
pub fn read<T: DeserializeOwned>(path: &Path) -> Result<T, Error> {
    let data = std::fs::read(path).map_err(|error| Error::new(error.to_string()).context(path.display()))?;
    from_slice(&data).map_err(|error| error.context(path.display()))
}

/// Decodes one JSON document.
pub fn from_slice<T: DeserializeOwned>(data: &[u8]) -> Result<T, Error> {
    serde_json::from_slice(data).map_err(|error| Error::new(error.to_string()))
}

#[cfg(test)]
mod tests;
