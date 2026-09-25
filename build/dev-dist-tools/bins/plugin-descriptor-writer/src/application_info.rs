// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The `--application-info` mode: the executor of `dev_dist_frontend_application_info`.

use anyhow::{Context, Result, bail};

use crate::descriptorxml::{self, Element, Node};
use crate::{
    Mode, OptionLine, assign, is_mode_line, read_text, refuse_repeated_options, report, require_mode, require_options, write_output,
};

pub(crate) const APPLICATION_INFO_NAMESPACE: &str = "http://jetbrains.org/intellij/schema/application-info";

/// The declared inputs of `dev_dist_frontend_application_info`.
///
/// The rule states none of the overrides that `applyApplicationInfoOverrides` in `ApplicationInfoPropertiesImpl.kt`
/// applies. So this mode refuses `--eap-override`, `--version-suffix-override`, `--nightly` and `--branch-name` as unknown
/// options.
#[derive(Debug, Default, PartialEq, Eq)]
pub(crate) struct ApplicationInfoRequest {
    pub output: String,
    pub client_application_info: String,
    pub product_application_info: String,
    pub build_number: String,
}

/// One marker of the application info. The text holds it as `__<key>__`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Replacement {
    pub key: String,
    pub value: String,
}

impl Replacement {
    pub(crate) fn new(key: &str, value: &str) -> Self {
        Self {
            key: key.to_owned(),
            value: value.to_owned(),
        }
    }
}

pub(crate) fn run(lines: &[OptionLine]) -> i32 {
    let parsed = match parse_application_info_request(lines) {
        Ok(parsed) => parsed,
        Err(error) => return report(2, &error),
    };
    let content = match resolve_application_info(&parsed) {
        Ok(content) => content,
        Err(error) => return report(1, &error.context("could not produce the frontend application info")),
    };
    match write_output(&parsed.output, content.as_bytes()) {
        Ok(()) => 0,
        Err(error) => report(1, &error),
    }
}

/// Applies the client build markers, then copies the names, the version and the release date of the product.
pub(crate) fn resolve_application_info(parsed: &ApplicationInfoRequest) -> Result<String> {
    let build_number = read_build_number(&parsed.build_number, "frontend")?;
    let client_content = read_text(&parsed.client_application_info)?;
    let replaced = replace_markers(&client_content, &application_info_replacements(&[], "JBC", &build_number));
    let product_content = read_text(&parsed.product_application_info)?;
    let product = ApplicationInfoElements::parse(&product_content, &parsed.product_application_info)?;
    let product_names = product.element(product.names);
    let Some(product_name) = product_names.attribute("fullname").or_else(|| product_names.attribute("product")) else {
        bail!(
            "the product application info has no product name: {}",
            parsed.product_application_info
        );
    };
    let mut client = ApplicationInfoElements::parse(&replaced, &parsed.client_application_info)?;

    let names = client.element_mut(client.names);
    names.set_attribute("fullname", product_name);
    names.remove_attribute("edition");
    copy_application_info_attribute(names, product_names, "motto");

    let version = client.element_mut(client.version);
    for name in ["eap", "major", "minor", "micro", "patch", "full", "suffix"] {
        copy_application_info_attribute(version, product.element(product.version), name);
    }

    let build = client.element_mut(client.build);
    copy_application_info_attribute(build, product.element(product.build), "majorReleaseDate");
    Ok(descriptorxml::write(&client.root))
}

/// Reads the build number of the file, without surrounding whitespace. An empty build number fails.
pub(crate) fn read_build_number(file: &str, owner: &str) -> Result<String> {
    let build_number = read_text(file)?.trim().to_owned();
    if build_number.is_empty() {
        bail!("the {owner} build number is empty: {file}");
    }
    Ok(build_number)
}

/// The replacement map of `computeAppInfoXml` for a dev distribution.
///
/// The product replacements come first. A base key that a product replacement also states keeps that position and
/// takes the base value, as Kotlin's `Map.plus` does. A dev distribution stamps no `BUILD_DATE`, and it has no artifact
/// server, so `BUILTIN_PLUGINS_URL` is empty.
pub(crate) fn application_info_replacements(product: &[Replacement], product_code: &str, build_number: &str) -> Vec<Replacement> {
    let mut result = product.to_vec();
    for base in [
        Replacement::new("BUILD_NUMBER", &format!("{product_code}-{build_number}")),
        Replacement::new("BUILD", build_number),
        Replacement::new("BUILTIN_PLUGINS_URL", ""),
    ] {
        let mut stated = false;
        for replacement in result.iter_mut().filter(|replacement| replacement.key == base.key) {
            replacement.value.clone_from(&base.value);
            stated = true;
        }
        if !stated {
            result.push(base);
        }
    }
    result
}

/// `BuildUtils.replaceAll` with the marker `__`. It replaces the markers one after another, in order.
pub(crate) fn replace_markers(text: &str, replacements: &[Replacement]) -> String {
    let mut text = text.to_owned();
    for replacement in replacements {
        text = text.replace(&format!("__{}__", replacement.key), &replacement.value);
    }
    text
}

/// The root of an application info and the positions of its three unique children.
pub(crate) struct ApplicationInfoElements {
    pub root: Element,
    pub names: usize,
    pub version: usize,
    pub build: usize,
}

impl ApplicationInfoElements {
    pub(crate) fn parse(content: &str, file: &str) -> Result<Self> {
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

    pub(crate) fn element(&self, index: usize) -> &Element {
        self.root.children[index].as_element().expect("the index points at an element")
    }

    pub(crate) fn element_mut(&mut self, index: usize) -> &mut Element {
        self.root.children[index].as_element_mut().expect("the index points at an element")
    }
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

fn copy_application_info_attribute(target: &mut Element, source: &Element, name: &str) {
    replace_application_info_attribute(target, name, source.attribute(name));
}

fn replace_application_info_attribute(element: &mut Element, name: &str, value: Option<&str>) {
    match value {
        Some(value) => element.set_attribute(name, value),
        None => {
            element.remove_attribute(name);
        }
    }
}

pub(crate) fn parse_application_info_request(lines: &[OptionLine]) -> Result<ApplicationInfoRequest> {
    require_mode(lines, Mode::ApplicationInfo)?;
    refuse_repeated_options(lines, &[])?;
    let mut parsed = ApplicationInfoRequest::default();
    for line in lines {
        if is_mode_line(line, Mode::ApplicationInfo) {
            continue;
        }
        let slot = match line.name.as_str() {
            "--out" => &mut parsed.output,
            "--client-application-info" => &mut parsed.client_application_info,
            "--product-application-info" => &mut parsed.product_application_info,
            "--build-number" => &mut parsed.build_number,
            option => bail!("unknown frontend application info option '{option}'"),
        };
        assign(slot, line)?;
    }
    require_options(&[
        ("--out", &parsed.output),
        ("--client-application-info", &parsed.client_application_info),
        ("--product-application-info", &parsed.product_application_info),
        ("--build-number", &parsed.build_number),
    ])?;
    Ok(parsed)
}
