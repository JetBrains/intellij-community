use std::collections::{BTreeMap, HashMap};

use crate::entry::{Error, check_supported_text, invalid, validate_path};

/// Returns the identity of a path: the path with ASCII letters in lowercase.
///
/// Two paths with one identity collide on a case-insensitive file system. The function refuses a path that is not
/// ASCII or that holds `<`, `>` or `&`, as [`validate_path`] does.
pub fn path_identity(name: &str) -> Result<String, Error> {
    check_supported_text(name)?;
    Ok(identity(name))
}

/// Returns the identity of a path that passed [`check_supported_text`].
pub(crate) fn identity(name: &str) -> String {
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
pub fn validate_links(links: &BTreeMap<String, String>) -> Result<(), Error> {
    let mut names_by_identity: HashMap<String, &str> = HashMap::with_capacity(links.len());
    for (name, target) in links {
        validate_path(name)?;
        validate_link_target(name, target)?;
        if let Some(previous) = names_by_identity.insert(identity(name), name) {
            return Err(invalid(format!("conflicting link destinations: {previous} and {name}")));
        }
    }
    for (name, target) in links {
        let mut current = name.as_str();
        while let Some(parent) = parent_of(current) {
            if names_by_identity.contains_key(&identity(parent)) {
                return Err(invalid(format!("conflicting link destinations: {parent} contains {name}")));
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
                        return Err(invalid(format!(
                            "unsupported symbolic link chain: the target of {name} resolves through the link {link}"
                        )));
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
pub(crate) fn validate_link_target(name: &str, target: &str) -> Result<(), Error> {
    if target.is_empty() || target.contains(['\\', ':', '\0']) || target.starts_with('/') {
        return Err(invalid(format!("invalid symbolic link target for {name}")));
    }
    if target.split('/').any(str::is_empty) {
        return Err(invalid(format!(
            "unsupported symbolic link target for {name}: {target} has an empty segment"
        )));
    }
    check_supported_text(target)?;
    if escapes(&format!("{}/{target}", parent_of(name).unwrap_or("."))) {
        return Err(invalid(format!("symbolic link escapes the directory: {name}")));
    }
    Ok(())
}

/// Returns the parent of a relative slash path, or `None` for a path with one segment.
pub(crate) fn parent_of(path: &str) -> Option<&str> {
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
