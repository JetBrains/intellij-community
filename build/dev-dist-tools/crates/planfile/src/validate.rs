//! The shared rules of an asset table and of the link graph of one tree. They read no file.
//!
//! The remainder packer applies them before it writes. The collector applies them again to the produced table and
//! inventory in a second process, because it does not trust the producer. So both processes refuse with one text.

use std::collections::{BTreeMap, HashMap, HashSet};

use anyhow::{Result, bail};

use crate::contract::{Asset, AssetKind, Producer, TREE_VERSION};

/// The identity of a destination: `distpath::path_identity`. It refuses a path that the inventory cannot hold, so a
/// refused name fails before any write.
fn identity(path: &str) -> Result<String> {
    distpath::path_identity(path)
}

/// Applies the shared asset rules to one asset table. The rules cover the version, the plugin root target, and the
/// relative path. They also cover the trees and the destination collisions. Every asset is below the plugin
/// directory. The directory-spellings check runs only when `check_directory_spellings` is true.
///
/// The packer calls it in its plan step before it writes. The collector calls it again on the produced table in a
/// second process, because the collector does not trust the producer.
pub fn validate_assets(version: u32, assets: &[Asset], check_directory_spellings: bool) -> Result<()> {
    validated_assets(version, assets, check_directory_spellings).map(|_| ())
}

/// [`validate_assets`] with the result: each asset keyed by the identity of its destination.
pub fn validated_assets(version: u32, assets: &[Asset], check_directory_spellings: bool) -> Result<BTreeMap<String, &Asset>> {
    let mut validated = BTreeMap::new();
    let mut spellings: HashMap<String, String> = HashMap::new();
    // The modules of the reused jars. An independent tree is the native tree of one of them.
    let reused_jars: HashSet<&str> = (assets.iter())
        .filter(|asset| asset.producer == Producer::Independent && asset.kind == AssetKind::File)
        .map(|asset| asset.artifact.as_str())
        .collect();
    for asset in assets {
        if asset.destination.is_empty() && asset.kind != AssetKind::Tree {
            bail!("only a declared tree can target the plugin root");
        }
        if !asset.destination.is_empty() {
            distpath::validate_relative_path(&asset.destination)?;
        }
        if validated.insert(identity(&asset.destination)?, asset).is_some() {
            bail!("destination collision at {:?}", asset.destination);
        }
        // A remainder tree, or the native tree of a reused natives jar next to the jar.
        let owned_tree = match asset.producer {
            Producer::Remainder => true,
            Producer::Independent => reused_jars.contains(asset.artifact.as_str()),
        };
        if asset.kind == AssetKind::Tree && (version < TREE_VERSION || !owned_tree || asset.class_path != Some(false)) {
            bail!(
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
                    bail!("conflicting directory spellings {previous:?} and {prefix:?}");
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
pub fn validate_link_graph(directories: &BTreeMap<String, bool>, links: &BTreeMap<String, String>) -> Result<()> {
    let mut casing: HashMap<String, &str> = HashMap::with_capacity(directories.len());
    for name in directories.keys() {
        if let Some(previous) = casing.insert(name.to_lowercase(), name)
            && previous != name
        {
            bail!("ambiguous path casing in link graph: {previous:?} and {name:?}");
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
            bail!("missing directory {parent:?} in link graph");
        }
        if *directory {
            edges.entry(parent).or_default().push(name.clone());
        }
    }
    for (link, target_text) in links {
        let mut current = distpath::dir(link);
        for component in target_text.split('/') {
            if !is_directory(&current) {
                bail!("symlink {link:?} traverses a non-directory {current:?}");
            }
            match component {
                "" | "." => continue,
                ".." => {
                    if current == "." {
                        bail!("symlink {link:?} escapes the plugin through {target_text:?}");
                    }
                    current = distpath::dir(&current);
                    continue;
                }
                _ => {}
            }
            current = distpath::join(&current, component);
            if !directories.contains_key(&current) {
                bail!("unresolved symlink target {target_text:?} at {current:?}");
            }
        }
        if is_directory(&current) {
            edges.entry(distpath::dir(link)).or_default().push(current);
        }
    }
    let mut states: HashMap<&str, u8> = HashMap::new();
    visit_directory(".", &edges, &mut states)
}

fn visit_directory<'e>(directory: &'e str, edges: &'e BTreeMap<String, Vec<String>>, states: &mut HashMap<&'e str, u8>) -> Result<()> {
    match states.get(directory) {
        Some(1) => bail!("symlink directory cycle at {directory:?}"),
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
