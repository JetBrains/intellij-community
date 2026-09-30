//! The slash-path rules of the dev-distribution tools.
//!
//! A path inside a distribution, a jar entry name and a link target are text in slash form. The functions of this crate
//! check and rewrite such text without file system access. A host path is not text here: the tools that read the file
//! system keep their own host-path helpers. `API.md` beside this crate lists what the functions refuse.

use std::collections::{BTreeMap, HashMap};

use anyhow::{Result, bail};

/// The name of the class-path index that the packer generates in each jar. `jarpack` defines the same name, and a source
/// entry must not have it.
const INDEX_FILE_NAME: &str = "__index__";

/// Checks that `name` is a relative path in slash form that cannot leave its root.
///
/// The path must not be empty, must not hold `\`, `:` or NUL, and must have no empty, `.` or `..` segment. Also, the
/// path must pass [`check_supported_text`].
pub fn validate_path(name: &str) -> Result<()> {
    if name.is_empty() || name.contains(['\\', ':', '\0']) || name.split('/').any(|part| part.is_empty() || part == "." || part == "..") {
        bail!("invalid relative path: {name:?}");
    }
    check_supported_text(name)
}

/// Refuses a path or a link target that is not ASCII or that holds `<`, `>` or `&`.
///
/// No payload name in the repository has such a character. For all other text, `serde_json` writes the bytes of the
/// Go writer, and ASCII case folding gives the [`path_identity`] of the Go original.
pub fn check_supported_text(text: &str) -> Result<()> {
    if let Some(character) = text
        .chars()
        .find(|character| !character.is_ascii() || matches!(character, '<' | '>' | '&'))
    {
        bail!("unsupported character {character:?} in {text:?}: the file metadata supports ASCII without <, > and &");
    }
    Ok(())
}

/// Returns the identity of a path: the path with ASCII letters in lowercase.
///
/// Two paths with one identity collide on a case-insensitive file system. The function refuses a path that is not
/// ASCII or that holds `<`, `>` or `&`, as [`validate_path`] does.
pub fn path_identity(name: &str) -> Result<String> {
    check_supported_text(name)?;
    Ok(identity(name))
}

/// Returns the identity of a path that passed [`check_supported_text`].
pub fn identity(name: &str) -> String {
    name.to_ascii_lowercase()
}

/// Removes `.` segments and repeated slashes from a relative link target in slash form.
///
/// The function keeps every `..` segment. It returns `.` for a target with no other segment, and keeps an empty or an
/// absolute target.
pub fn clean_link_target(target: &str) -> String {
    if target.is_empty() || target.starts_with('/') {
        return target.to_owned();
    }
    let segments: Vec<&str> = target.split('/').filter(|segment| !segment.is_empty() && *segment != ".").collect();
    if segments.is_empty() { ".".to_owned() } else { segments.join("/") }
}

/// Checks a set of links without file system access. A key is the path of a link, and a value is its target.
///
/// Each link path must be valid (see [`validate_path`]) and have a relative target that stays in the directory. A
/// target must not have an empty segment. No two links can have one [`path_identity`], and no link can be below another
/// link. A link to a missing file is valid.
///
/// The function refuses a target that resolves through another link. No payload in the repository has such a chain.
/// Without a chain, the lexical resolution of a target gives the same result as the file system.
pub fn validate_links(links: &BTreeMap<String, String>) -> Result<()> {
    let mut names_by_identity: HashMap<String, &str> = HashMap::with_capacity(links.len());
    for (name, target) in links {
        validate_path(name)?;
        validate_link_target(name, target)?;
        if let Some(previous) = names_by_identity.insert(identity(name), name) {
            bail!("conflicting link destinations: {previous} and {name}");
        }
    }
    for (name, target) in links {
        let mut current = name.as_str();
        while let Some(parent) = parent_of(current) {
            if names_by_identity.contains_key(&identity(parent)) {
                bail!("conflicting link destinations: {parent} contains {name}");
            }
            current = parent;
        }
        let mut resolved: Vec<&str> = parent_of(name).map_or_else(Vec::new, |parent| parent.split('/').collect());
        for part in target.split('/') {
            match part {
                "" | "." => {}
                ".." => {
                    resolved.pop();
                }
                _ => {
                    resolved.push(part);
                    if let Some(link) = names_by_identity.get(&identity(&resolved.join("/"))) {
                        bail!("unsupported symbolic link chain: the target of {name} resolves through the link {link}");
                    }
                }
            }
        }
    }
    Ok(())
}

/// Checks the target of the link `name`.
///
/// The function refuses a target with an empty segment, for example `payload/` or `lib//payload`. The Go composer
/// refused such a target, because Java `Path` removes the extra slash and the exporter cannot keep the spelling.
pub fn validate_link_target(name: &str, target: &str) -> Result<()> {
    if target.is_empty() || target.contains(['\\', ':', '\0']) || target.starts_with('/') {
        bail!("invalid symbolic link target for {name}");
    }
    if target.split('/').any(str::is_empty) {
        bail!("unsupported symbolic link target for {name}: {target} has an empty segment");
    }
    check_supported_text(target)?;
    if escapes(&format!("{}/{target}", parent_of(name).unwrap_or("."))) {
        bail!("symbolic link escapes the directory: {name}");
    }
    Ok(())
}

/// Returns the parent of a relative slash path, or `None` for a path with one segment.
pub fn parent_of(path: &str) -> Option<&str> {
    path.rfind('/').map(|index| &path[..index])
}

/// Returns true when a relative slash path goes above its start. The check is lexical.
fn escapes(path: &str) -> bool {
    let mut depth = 0usize;
    for part in path.split('/') {
        match part {
            "" | "." => {}
            ".." => match depth.checked_sub(1) {
                Some(parent) => depth = parent,
                None => return true,
            },
            _ => depth += 1,
        }
    }
    false
}

/// Accepts a portable, relative file name. A directory and the generated index are not source entries.
pub fn validate_entry_name(name: &str) -> Result<()> {
    // `name.split('/')` finds each component that the Go `path.Clean(name) != name` test finds: an empty one, `.`, or
    // `..`. The other tests are the ones of the Go function.
    let unsafe_name = name.is_empty()
        || name == "."
        || name
            .split('/')
            .any(|component| component.is_empty() || component == "." || component == "..")
        || name.starts_with('/')
        || name == ".."
        || name.starts_with("../")
        || name.contains(['\\', ':', '\0', '\r', '\n'])
        || name.len() > 65535
        || name == INDEX_FILE_NAME;
    if unsafe_name {
        bail!("unsafe entry name {name:?}");
    }
    Ok(())
}

/// Accepts a relative slash path that is a safe jar entry name and a portable file name on every host.
pub fn validate_relative_path(value: &str) -> Result<()> {
    if validate_entry_name(value).is_err() {
        bail!("unsafe relative path {value:?}");
    }
    for component in value.split('/') {
        if component.trim_end_matches(['.', ' ']) != component
            || component.contains(['<', '>', '"', '|', '?', '*'])
            || component.chars().any(|character| (character as u32) < 0x20)
        {
            bail!("unsafe path component {component:?}");
        }
        let upper = component.to_uppercase();
        let base = upper.split('.').next().unwrap_or_default();
        let numbered = base.len() == 4 && (base.starts_with("COM") || base.starts_with("LPT")) && matches!(base.as_bytes()[3], b'1'..=b'9');
        if matches!(base, "CON" | "PRN" | "AUX" | "NUL") || numbered {
            bail!("reserved path component {component:?}");
        }
    }
    Ok(())
}

/// Go `path.Clean`: removes repeated slashes, `.` elements, and each inner `..` with the element before it.
pub fn clean(path: &str) -> String {
    if path.is_empty() {
        return ".".to_owned();
    }
    let rooted = path.starts_with('/');
    let mut parts: Vec<&str> = Vec::new();
    for part in path.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                if parts.last().is_some_and(|last| *last != "..") {
                    parts.pop();
                } else if !rooted {
                    parts.push("..");
                }
            }
            _ => parts.push(part),
        }
    }
    let joined = parts.join("/");
    if rooted {
        format!("/{joined}")
    } else if joined.is_empty() {
        ".".to_owned()
    } else {
        joined
    }
}

/// Go `path.Dir`: all but the last element, cleaned. A path with one element gives `.`.
pub fn dir(path: &str) -> String {
    match path.rfind('/') {
        Some(index) => clean(&path[..=index]),
        None => ".".to_owned(),
    }
}

/// Go `path.Join` of two elements: the non-empty ones joined by a slash and cleaned. Two empty elements give "".
pub fn join(first: &str, second: &str) -> String {
    match (first.is_empty(), second.is_empty()) {
        (true, true) => String::new(),
        (true, false) => clean(second),
        (false, true) => clean(first),
        (false, false) => clean(&format!("{first}/{second}")),
    }
}

#[cfg(test)]
mod tests;
