//! The host paths that only the composer resolves. A host path is a `String`, as [`component::paths`] states. The
//! functions resolve links with `fscopy` and join names with `Path`.

use std::path::{Path, PathBuf};

use anyhow::{Context as _, Result, bail};
use component::paths::{self, from_slash};

/// The absolute path of a host path with every symbolic link resolved. The path must exist.
pub(crate) fn real_path(value: &str) -> Result<String> {
    eval_symlinks(&paths::absolute_path(value)?)
}

/// An absolute path with every symbolic link resolved. The path must exist.
pub(crate) fn eval_symlinks(path: &str) -> Result<String> {
    let resolved = fscopy::real_path(Path::new(path)).with_context(|| path.to_owned())?;
    text(resolved)
}

/// The parent directory of an absolute host path. The root is its own parent.
pub(crate) fn parent(value: &str) -> &str {
    Path::new(value).parent().and_then(Path::to_str).unwrap_or(value)
}

/// `base.resolve(relative)` for a relative path in slash form.
pub(crate) fn resolve_relative(base: &str, relative: &str) -> String {
    let joined = Path::new(base).join(from_slash(relative).as_ref());
    joined.into_os_string().into_string().expect("two UTF-8 paths join to UTF-8")
}

fn text(path: PathBuf) -> Result<String> {
    match path.into_os_string().into_string() {
        Ok(value) => Ok(value),
        Err(value) => bail!("The path is not valid UTF-8: {}", value.display()),
    }
}

#[cfg(test)]
mod tests;
