// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The application info as an element tree: the markers, the three unique elements, and the frontend merge.

use anyhow::{Context, Result, bail};

use crate::descriptorxml::{self, Element, Node};

/// The namespace URI of the `names`, `version` and `build` elements.
pub const APPLICATION_INFO_NAMESPACE: &str = "http://jetbrains.org/intellij/schema/application-info";

/// One marker of the application info. The text holds it as `__<key>__`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Replacement {
    pub key: String,
    pub value: String,
}

impl Replacement {
    pub fn new(key: &str, value: &str) -> Self {
        Self {
            key: key.to_owned(),
            value: value.to_owned(),
        }
    }
}

/// `BuildUtils.replaceAll` with the marker `__`. It replaces the markers one after another, in order.
pub fn replace_markers(text: &str, replacements: &[Replacement]) -> String {
    let mut text = text.to_owned();
    for replacement in replacements {
        text = text.replace(&format!("__{}__", replacement.key), &replacement.value);
    }
    text
}

/// The root of an application info and the positions of its three unique children.
pub struct ApplicationInfoElements {
    pub root: Element,
    pub names: usize,
    pub version: usize,
    pub build: usize,
}

impl ApplicationInfoElements {
    /// Parses the text, and finds the `names`, `version` and `build` elements of [`APPLICATION_INFO_NAMESPACE`].
    ///
    /// Each one must occur once, as `getChildren(name, namespace).singleOrNull()` requires in
    /// `applyApplicationInfoOverrides` of `ApplicationInfoPropertiesImpl.kt`. The file names the input in an error.
    pub fn parse(content: &str, file: &str) -> Result<Self> {
        let root = descriptorxml::read(content).with_context(|| file.to_owned())?;
        let mut indexes = [0; 3];
        for (index, name) in indexes.iter_mut().zip(["names", "version", "build"]) {
            let Some(found) = single_child(&root, name, APPLICATION_INFO_NAMESPACE) else {
                bail!("the application info has no unique {name} element: {file}");
            };
            *index = found;
        }
        let [names, version, build] = indexes;
        Ok(Self {
            root,
            names,
            version,
            build,
        })
    }

    pub fn element(&self, index: usize) -> &Element {
        self.root.children[index].as_element().expect("the index points at an element")
    }

    pub fn element_mut(&mut self, index: usize) -> &mut Element {
        self.root.children[index].as_element_mut().expect("the index points at an element")
    }
}

/// Copies the names, the version and the release date of the host product into the application info of a frontend.
///
/// It is the XML that `applyApplicationInfoOverrides` writes for `JetBrainsClientPropertiesForLaunchers`
/// (`platform/buildScripts/src/JetBrainsClientPropertiesForLaunchers.kt`). The frontend takes the full name of the
/// host, or its product name, and loses its edition. Each other attribute takes the value of the host, and an attribute
/// that the host does not state is removed. The host file names the input in an error.
pub fn merge_host_application_info(client: &mut ApplicationInfoElements, host: &ApplicationInfoElements, host_file: &str) -> Result<()> {
    let host_names = host.element(host.names);
    let Some(product_name) = host_names.attribute("fullname").or_else(|| host_names.attribute("product")) else {
        bail!("the product application info has no product name: {host_file}");
    };

    let names = client.element_mut(client.names);
    names.set_attribute("fullname", product_name);
    names.remove_attribute("edition");
    copy_application_info_attribute(names, host_names, "motto");

    let version = client.element_mut(client.version);
    for name in ["eap", "major", "minor", "micro", "patch", "full", "suffix"] {
        copy_application_info_attribute(version, host.element(host.version), name);
    }

    let build = client.element_mut(client.build);
    copy_application_info_attribute(build, host.element(host.build), "majorReleaseDate");
    Ok(())
}

/// `getChildren(name, namespace).singleOrNull()`: the position of the one child element with this name in this
/// namespace URI.
fn single_child(root: &Element, name: &str, uri: &str) -> Option<usize> {
    let mut matches = root.children.iter().enumerate().filter(|(_, node)| match node {
        Node::Element(element) => element.name == name && element.uri == uri,
        _ => false,
    });
    match (matches.next(), matches.next()) {
        (Some((index, _)), None) => Some(index),
        _ => None,
    }
}

/// `replaceAttribute` of `applyApplicationInfoOverrides`: sets the attribute to the value of the source, or removes it
/// when the source does not state it.
pub fn copy_application_info_attribute(target: &mut Element, source: &Element, name: &str) {
    match source.attribute(name) {
        Some(value) => target.set_attribute(name, value),
        None => target.remove_attribute(name),
    }
}
