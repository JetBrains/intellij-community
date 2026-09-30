//! The shared rules of an asset table and of the link graph of one tree. They read no file.
//!
//! The remainder packer applies them before it writes. The collector applies them again to the produced table and
//! inventory in a second process, because it does not trust the producer. So both processes refuse with one text.

use std::collections::{BTreeMap, HashMap, HashSet};

use crate::contract::{Asset, TREE_VERSION};
use crate::{Error, fail};

/// The kind of an asset row. An empty kind is `file`.
pub fn asset_kind(asset: &Asset) -> &str {
    if asset.kind.is_empty() { "file" } else { &asset.kind }
}

/// The identity of a destination: `distpath::path_identity`. It refuses a path that the inventory cannot hold, so a
/// refused name fails before any write.
fn identity(path: &str) -> Result<String, Error> {
    distpath::path_identity(path).map_err(|error| Error::new(error.to_string()))
}

/// Applies the shared asset rules to one asset table. The rules cover the version, the plugin root target, and the
/// relative path. They also cover the kind, the trees, and the destination collisions. Every asset is below the plugin
/// directory. The directory-spellings check runs only when `check_directory_spellings` is true.
///
/// The packer calls it in its plan step before it writes. The collector calls it again on the produced table in a
/// second process, because the collector does not trust the producer.
pub fn validate_assets(version: u32, assets: &[Asset], check_directory_spellings: bool) -> Result<(), Error> {
    validated_assets(version, assets, check_directory_spellings).map(|_| ())
}

/// [`validate_assets`] with the result: each asset keyed by the identity of its destination.
pub fn validated_assets(version: u32, assets: &[Asset], check_directory_spellings: bool) -> Result<BTreeMap<String, &Asset>, Error> {
    let mut validated = BTreeMap::new();
    let mut spellings: HashMap<String, String> = HashMap::new();
    // The modules of the reused jars. An independent tree is the native tree of one of them.
    let reused_jars: HashSet<&str> = (assets.iter())
        .filter(|asset| asset.producer == "independent" && asset_kind(asset) == "file")
        .map(|asset| asset.artifact.as_str())
        .collect();
    for asset in assets {
        let kind = asset_kind(asset);
        if asset.destination.is_empty() && kind != "tree" {
            fail!("only a declared tree can target the plugin root");
        }
        if !asset.destination.is_empty() {
            distpath::validate_relative_path(&asset.destination).map_err(|error| Error::new(error.to_string()))?;
        }
        if validated.insert(identity(&asset.destination)?, asset).is_some() {
            fail!("destination collision at {:?}", asset.destination);
        }
        if kind != "file" && kind != "tree" {
            fail!("unknown asset kind {:?}; the packer writes only file and tree assets", asset.kind);
        }
        // A remainder tree, or the native tree of a reused natives jar next to the jar.
        let owned_tree = asset.producer == "remainder" || asset.producer == "independent" && reused_jars.contains(asset.artifact.as_str());
        if kind == "tree" && (version < TREE_VERSION || !owned_tree || asset.class_path != Some(false)) {
            fail!(
                "tree {:?} requires version 2, remainder or native tree ownership, and classPath false",
                asset.destination
            );
        }
        if check_directory_spellings {
            let mut prefix = asset.destination.clone();
            while !prefix.is_empty() && prefix != "." {
                let spelling = identity(&prefix)?;
                if let Some(previous) = spellings.get(&spelling)
                    && *previous != prefix
                {
                    fail!("conflicting directory spellings {previous:?} and {prefix:?}");
                }
                let parent = distpath::dir(&prefix);
                spellings.insert(spelling, prefix);
                prefix = parent;
            }
        }
    }
    Ok(validated)
}

/// Checks the link graph of one directory tree. `directories` names every node and marks each directory true, and
/// `links` names each link with its target. It refuses a link through a non-directory, a link that escapes the root,
/// and a target absent from the tree. It also refuses two names that differ only in case, a parent that is not a
/// directory, and a directory cycle through links.
///
/// The caller must run `distpath::validate_links` first, because that function refuses a target that resolves through
/// another link. The packer calls this function on the links of each tree. The collector calls it again on the
/// produced inventory in a second process, because the collector does not trust the producer.
pub fn validate_link_graph(directories: &BTreeMap<String, bool>, links: &BTreeMap<String, String>) -> Result<(), Error> {
    let mut casing: HashMap<String, &str> = HashMap::with_capacity(directories.len());
    for name in directories.keys() {
        if let Some(previous) = casing.insert(name.to_lowercase(), name)
            && previous != name
        {
            fail!("ambiguous path casing in link graph: {previous:?} and {name:?}");
        }
    }
    let is_directory = |name: &str| directories.get(name).copied().unwrap_or(false);
    let mut edges: BTreeMap<String, Vec<String>> = BTreeMap::new();
    for (name, directory) in directories {
        if name == "." {
            continue;
        }
        let parent = distpath::dir(name);
        if !is_directory(&parent) {
            fail!("missing directory {parent:?} in link graph");
        }
        if *directory {
            edges.entry(parent).or_default().push(name.clone());
        }
    }
    for (link, target_text) in links {
        let mut current = distpath::dir(link);
        for component in target_text.split('/') {
            if !is_directory(&current) {
                fail!("symlink {link:?} traverses a non-directory {current:?}");
            }
            match component {
                "" | "." => continue,
                ".." => {
                    if current == "." {
                        fail!("symlink {link:?} escapes the plugin through {target_text:?}");
                    }
                    current = distpath::dir(&current);
                    continue;
                }
                _ => {}
            }
            current = distpath::join(&current, component);
            if !directories.contains_key(&current) {
                fail!("unresolved symlink target {target_text:?} at {current:?}");
            }
        }
        if is_directory(&current) {
            edges.entry(distpath::dir(link)).or_default().push(current);
        }
    }
    let mut states: HashMap<&str, u8> = HashMap::new();
    visit_directory(".", &edges, &mut states)
}

fn visit_directory<'e>(
    directory: &'e str,
    edges: &'e BTreeMap<String, Vec<String>>,
    states: &mut HashMap<&'e str, u8>,
) -> Result<(), Error> {
    match states.get(directory) {
        Some(1) => fail!("symlink directory cycle at {directory:?}"),
        Some(_) => return Ok(()),
        None => {}
    }
    states.insert(directory, 1);
    for child in edges.get(directory).into_iter().flatten() {
        visit_directory(child, edges, states)?;
    }
    states.insert(directory, 2);
    Ok(())
}

#[cfg(test)]
mod tests;
