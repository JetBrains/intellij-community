//! The host paths of the collector and the composer, and the slash form of a path.
//!
//! A host path is a `String`: a Bazel `File.path` relative to the working directory, or an absolute path. Bazel
//! writes each path without an empty, `.` or `..` element, so [`host_path`] refuses those spellings, and
//! [`absolute_path`] needs no lexical normalization. A path inside a distribution is a relative path in slash form, and
//! `distpath::validate_path` checks it.

use std::borrow::Cow;
use std::path::PathBuf;

use anyhow::{Context as _, Result, bail};

/// The native name separator.
pub const SEPARATOR: char = std::path::MAIN_SEPARATOR;

/// Checks a host path and gives it with native separators. A path can start at the root, but it must not have an
/// empty, `.` or `..` element.
pub fn host_path(value: &str) -> Result<String> {
    let native = from_slash(value);
    let names = native.strip_prefix(SEPARATOR).unwrap_or(&native);
    if value.contains('\0') || names.split(SEPARATOR).any(|name| name.is_empty() || name == "." || name == "..") {
        bail!("Unsupported host path '{value}': Bazel writes no empty, '.' or '..' element");
    }
    Ok(native.into_owned())
}

/// Changes each native separator to a slash.
pub fn to_slash(value: &str) -> Cow<'_, str> {
    if cfg!(windows) {
        Cow::Owned(value.replace('\\', "/"))
    } else {
        Cow::Borrowed(value)
    }
}

/// Changes each slash to a native separator.
pub fn from_slash(value: &str) -> Cow<'_, str> {
    if cfg!(windows) {
        Cow::Owned(value.replace('/', "\\"))
    } else {
        Cow::Borrowed(value)
    }
}

/// The absolute path of a [`host_path`]. A relative path starts at the working directory.
pub fn absolute_path(value: &str) -> Result<String> {
    let native = host_path(value)?;
    let absolute = std::path::absolute(&native).with_context(|| value.to_owned())?;
    text(absolute)
}

fn text(path: PathBuf) -> Result<String> {
    match path.into_os_string().into_string() {
        Ok(value) => Ok(value),
        Err(value) => bail!("The path is not valid UTF-8: {}", value.display()),
    }
}

#[cfg(test)]
mod tests;
