// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The composition of a product descriptor from the flags of `dev_dist_product_descriptor` and
//! `dev_dist_embedded_product_descriptor`.
//!
//! The element is the one that `buildProductContentXml(inlineModuleSets = true)`
//! (`community/platform/build-scripts/product-dsl/src/generator.kt`) renders, after the round trip of [`descriptorxml`]
//! drops its comments and its whitespace. The rows of `appendModuleLine` and `appendContentBlock`
//! (`community/platform/build-scripts/product-dsl/src/xml/ModuleSetXmlRenderer.kt`) state the module elements. The macro
//! walks the module sets and states every row in the order of the Product DSL, so this module only builds elements.
//!
//! The flags are:
//!
//! - `--alias=<plugin id>`, repeatable. The element lists every alias sorted by byte order.
//! - `--include=<kind>=<href>`, repeatable, in order. The kind is `required` or `optional`.
//! - `--content-module=<name>[;loading=<rule>][;required-if-available=<module>]`, repeatable, in order. The rows of
//!   the one `<content namespace="jetbrains">` block of the module sets.
//! - `--additional-module=<name>[;private][;loading=<rule>][;required-if-available=<module>]`, repeatable, in order.
//!   The rows of the additional modules. They are grouped by namespace in first-seen order, and `private` is the
//!   `<content>` block without a namespace.
//!
//! [`descriptorxml`]: crate::descriptorxml

use std::collections::BTreeSet;

use anyhow::{Result, anyhow, bail};

use crate::descriptorxml::{Element, Namespace, Node};
use crate::structural::XINCLUDE_NAMESPACE;

const ALIAS: &str = "--alias";
const INCLUDE: &str = "--include";
const CONTENT_MODULE: &str = "--content-module";
const ADDITIONAL_MODULE: &str = "--additional-module";

/// The flags of the composition, for a refusal that names them.
pub(crate) const FLAGS: [&str; 4] = [ALIAS, INCLUDE, CONTENT_MODULE, ADDITIONAL_MODULE];

/// `JETBRAINS_NAMESPACE` of the Product DSL, the namespace of the module-set block.
const JETBRAINS_NAMESPACE: &str = "jetbrains";

/// The content of a product descriptor, as the flags state it.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Composition {
    /// The aliases in the order of the flags. The element sorts them.
    pub aliases: Vec<String>,
    pub includes: Vec<Include>,
    /// The rows of the module-set block.
    pub content_modules: Vec<ModuleRow>,
    /// The rows of the additional modules.
    pub additional_modules: Vec<ModuleRow>,
}

/// One deprecated XML include of the product.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Include {
    pub href: String,
    /// An optional include takes an `<xi:fallback/>` child, so the include stage keeps it unresolved.
    pub optional: bool,
}

/// One `<module/>` row of a `<content>` block.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct ModuleRow {
    pub name: String,
    /// The row goes to the `<content>` block without a namespace. Only an additional module states it.
    pub private: bool,
    pub loading: Option<Loading>,
    pub required_if_available: Option<String>,
}

/// `ModuleLoadingRuleValue` of the platform, with the XML values of `PluginXmlConst`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Loading {
    Required,
    Embedded,
    Optional,
    OnDemand,
}

impl Loading {
    const ALL: [Self; 4] = [Self::Required, Self::Embedded, Self::Optional, Self::OnDemand];

    pub(crate) const fn xml_value(self) -> &'static str {
        match self {
            Self::Required => "required",
            Self::Embedded => "embedded",
            Self::Optional => "optional",
            Self::OnDemand => "on-demand",
        }
    }

    fn parse(value: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|loading| loading.xml_value() == value)
    }
}

impl Composition {
    /// Takes the flags of the composition. It returns `None` when the request states none of them.
    ///
    /// A malformed row, a duplicate alias and a module that two rows name are refused. The Product DSL refuses the last
    /// two too (`validateAndRecordAlias` and `validateNoDuplicateModules`).
    pub(crate) fn take(options: &mut cli::Options) -> Result<Option<Self>> {
        let aliases = options.take_all(ALIAS)?;
        let includes = options.take_all(INCLUDE)?;
        let content_modules = options.take_all(CONTENT_MODULE)?;
        let additional_modules = options.take_all(ADDITIONAL_MODULE)?;
        if aliases.is_empty() && includes.is_empty() && content_modules.is_empty() && additional_modules.is_empty() {
            return Ok(None);
        }

        let mut seen = BTreeSet::new();
        for alias in &aliases {
            if alias.is_empty() {
                bail!("the row '{ALIAS}=' states no plugin id");
            }
            if !seen.insert(alias.as_str()) {
                bail!("the row '{ALIAS}={alias}' states an alias that another row states");
            }
        }
        let includes = includes.iter().map(|value| parse_include(value)).collect::<Result<Vec<_>>>()?;
        let content_modules = content_modules
            .iter()
            .map(|value| parse_module_row(CONTENT_MODULE, value))
            .collect::<Result<Vec<_>>>()?;
        let additional_modules = additional_modules
            .iter()
            .map(|value| parse_module_row(ADDITIONAL_MODULE, value))
            .collect::<Result<Vec<_>>>()?;

        let mut names = BTreeSet::new();
        for row in content_modules.iter().chain(&additional_modules) {
            if !names.insert(row.name.as_str()) {
                bail!(
                    "the module '{}' is stated by two rows of {CONTENT_MODULE} or {ADDITIONAL_MODULE}",
                    row.name
                );
            }
        }
        Ok(Some(Self {
            aliases,
            includes,
            content_modules,
            additional_modules,
        }))
    }

    /// Builds the `<idea-plugin>` root. The children are `<id>`, the sorted aliases, the includes, the module-set
    /// block and the blocks of the additional modules, in this order. The root declares `xmlns:xi` only when the product
    /// has an include, as `appendOpeningTag` does.
    pub(crate) fn element(&self) -> Element {
        let mut root = Element::new("idea-plugin");
        if !self.includes.is_empty() {
            root.namespaces.push(Namespace {
                prefix: "xi".to_owned(),
                uri: XINCLUDE_NAMESPACE.to_owned(),
            });
        }

        let mut id = Element::new("id");
        id.set_text("com.intellij");
        root.children.push(Node::Element(id));

        let mut aliases: Vec<&str> = self.aliases.iter().map(String::as_str).collect();
        aliases.sort_unstable();
        for alias in aliases {
            let mut module = Element::new("module");
            module.set_attribute("value", alias);
            root.children.push(Node::Element(module));
        }

        for include in &self.includes {
            let mut element = xinclude_element("include");
            element.set_attribute("href", &include.href);
            if include.optional {
                element.children.push(Node::Element(xinclude_element("fallback")));
            }
            root.children.push(Node::Element(element));
        }

        if !self.content_modules.is_empty() {
            root.children
                .push(Node::Element(content_block(Some(JETBRAINS_NAMESPACE), &self.content_modules)));
        }

        // `groupBy` of `appendContentBlock` keeps the first-seen order of the namespaces, and the row order within one.
        let mut groups: Vec<(bool, Vec<&ModuleRow>)> = Vec::new();
        for row in &self.additional_modules {
            match groups.iter_mut().find(|(private, _)| *private == row.private) {
                Some((_, rows)) => rows.push(row),
                None => groups.push((row.private, vec![row])),
            }
        }
        for (private, rows) in groups {
            let namespace = if private { None } else { Some(JETBRAINS_NAMESPACE) };
            root.children.push(Node::Element(content_block(namespace, rows)));
        }
        root
    }
}

/// An element of the XInclude namespace, with the prefix that the root binds.
fn xinclude_element(name: &str) -> Element {
    Element {
        prefix: "xi".to_owned(),
        uri: XINCLUDE_NAMESPACE.to_owned(),
        ..Element::new(name)
    }
}

/// One `<content>` block. A module row writes `name`, then `loading`, then `required-if-available`, as
/// `appendModuleLine` does.
fn content_block<'a>(namespace: Option<&str>, rows: impl IntoIterator<Item = &'a ModuleRow>) -> Element {
    let mut content = Element::new("content");
    if let Some(namespace) = namespace {
        content.set_attribute("namespace", namespace);
    }
    for row in rows {
        let mut module = Element::new("module");
        module.set_attribute("name", &row.name);
        if let Some(loading) = row.loading {
            module.set_attribute("loading", loading.xml_value());
        }
        if let Some(required_if_available) = &row.required_if_available {
            module.set_attribute("required-if-available", required_if_available);
        }
        content.children.push(Node::Element(module));
    }
    content
}

/// Reads `<kind>=<href>`.
fn parse_include(value: &str) -> Result<Include> {
    let refusal = || anyhow!("the row '{INCLUDE}={value}' is not '<kind>=<href>' with the kind 'required' or 'optional'");
    let (kind, href) = value.split_once('=').filter(|(_, href)| !href.is_empty()).ok_or_else(refusal)?;
    let optional = match kind {
        "required" => false,
        "optional" => true,
        _ => return Err(refusal()),
    };
    Ok(Include {
        href: href.to_owned(),
        optional,
    })
}

/// Reads `<name>[;private][;loading=<rule>][;required-if-available=<module>]`. Only an additional module can be
/// private.
fn parse_module_row(flag: &str, value: &str) -> Result<ModuleRow> {
    let mut parts = value.split(';');
    let name = parts.next().unwrap_or_default();
    if name.is_empty() {
        bail!("the row '{flag}={value}' states no module name");
    }
    let mut row = ModuleRow {
        name: name.to_owned(),
        private: false,
        loading: None,
        required_if_available: None,
    };
    let mut keys = BTreeSet::new();
    for part in parts {
        let (key, part_value) = part
            .split_once('=')
            .map_or((part, None), |(key, part_value)| (key, Some(part_value)));
        if !keys.insert(key) {
            bail!("the row '{flag}={value}' states '{key}' twice");
        }
        match (key, part_value) {
            ("private", None) if flag == ADDITIONAL_MODULE => row.private = true,
            ("loading", Some(loading)) => {
                let Some(loading) = Loading::parse(loading) else {
                    let values: Vec<&str> = Loading::ALL.iter().map(|loading| loading.xml_value()).collect();
                    bail!(
                        "the row '{flag}={value}' states the loading '{loading}', and only {} are supported",
                        values.join(", ")
                    );
                };
                row.loading = Some(loading);
            }
            ("required-if-available", Some(module)) if !module.is_empty() => row.required_if_available = Some(module.to_owned()),
            _ => {
                let supported = if flag == ADDITIONAL_MODULE {
                    "'private', 'loading=<rule>' and 'required-if-available=<module>'"
                } else {
                    "'loading=<rule>' and 'required-if-available=<module>'"
                };
                bail!("the row '{flag}={value}' states '{part}', and only {supported} are supported");
            }
        }
    }
    Ok(row)
}

#[cfg(test)]
mod tests;
