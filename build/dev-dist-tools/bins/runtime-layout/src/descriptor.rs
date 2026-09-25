// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::path::Path;

use anyhow::{Context, bail};
use quick_xml::XmlVersion;
use quick_xml::events::{BytesStart, Event};

/// One `<content><module>` element of a plugin descriptor.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct ContentModule {
    pub name: String,
    pub loading: String,
}

impl ContentModule {
    #[cfg(test)]
    pub(crate) fn new(name: &str, loading: &str) -> Self {
        Self {
            name: name.to_owned(),
            loading: loading.to_owned(),
        }
    }
}

/// Returns the content modules of a plugin descriptor in the order of its `<content>` elements.
/// `computeModuleSourcesByContent` walks the same elements and skips a name with a `/`, which names a descriptor, not a
/// module.
pub(crate) fn read_content_order(file: &Path) -> anyhow::Result<Vec<ContentModule>> {
    let data = std::fs::read(file).with_context(|| format!("cannot read {}", file.display()))?;
    parse_content_order(&data).with_context(|| file.display().to_string())
}

fn parse_content_order(data: &[u8]) -> anyhow::Result<Vec<ContentModule>> {
    let mut reader = quick_xml::Reader::from_reader(data);
    // A self-closing element is a start and an end, as for the Go decoder.
    reader.config_mut().expand_empty_elements = true;
    let mut modules = Vec::new();
    let mut depth = 0usize;
    let mut in_content = false;
    let mut buffer = Vec::new();
    loop {
        let event = reader
            .read_event_into(&mut buffer)
            .with_context(|| format!("at byte {}", reader.error_position()))?;
        match event {
            Event::Start(element) => {
                depth += 1;
                let local_name = element.local_name();
                if depth == 2 && local_name.into_inner() == "content" {
                    in_content = true;
                } else if depth == 3 && in_content && local_name.into_inner() == "module" {
                    let name = attribute(&element, "name")?;
                    if !name.is_empty() && !name.contains('/') {
                        modules.push(ContentModule {
                            name,
                            loading: attribute(&element, "loading")?,
                        });
                    }
                }
            }
            Event::End(_) => {
                if depth == 2 {
                    in_content = false;
                }
                depth = depth.saturating_sub(1);
            }
            Event::Eof => break,
            _ => {}
        }
        buffer.clear();
    }
    if depth != 0 {
        bail!("unexpected EOF");
    }
    Ok(modules)
}

/// The value of the attribute `name` with no namespace prefix, or an empty string.
fn attribute(element: &BytesStart<'_>, name: &str) -> anyhow::Result<String> {
    for attribute in element.attributes() {
        let attribute = attribute?;
        if attribute.key.prefix().is_none() && attribute.key.local_name().into_inner() == name {
            return Ok(attribute.normalized_value(XmlVersion::Implicit1_0)?.into_owned());
        }
    }
    Ok(String::new())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn content_order_of_descriptor() {
        let descriptor = br#"<idea-plugin>
  <id>p</id>
  <content namespace="jetbrains">
    <module name="p.first" loading="required"><![CDATA[<idea-plugin><content><module name="nested"/></content></idea-plugin>]]></module>
    <module name="p.main/descriptor.xml"/>
  </content>
  <extensions><content><module name="not.content"/></content></extensions>
  <content>
    <module name="p.second"/>
  </content>
</idea-plugin>"#;
        let content = parse_content_order(descriptor).unwrap();
        assert_eq!(
            content,
            vec![ContentModule::new("p.first", "required"), ContentModule::new("p.second", "")]
        );
    }

    #[test]
    fn prefixes_and_entities_follow_the_go_decoder() {
        let descriptor = br#"<?xml version="1.0"?>
<!-- a comment -->
<x:idea-plugin xmlns:x="urn:x">
  <x:content>
    <module x:name="prefixed.attribute.is.not.the.name" name="a&amp;b"/>
    <module name="c"></module>
  </x:content>
</x:idea-plugin>"#;
        let content = parse_content_order(descriptor).unwrap();
        assert_eq!(content, vec![ContentModule::new("a&b", ""), ContentModule::new("c", "")]);
    }

    #[test]
    fn malformed_descriptors_fail() {
        for descriptor in [
            "<idea-plugin><content></idea-plugin>",
            "<idea-plugin><content>",
            "<idea-plugin><content><module name=\"&bad;\"/></content></idea-plugin>",
        ] {
            assert!(parse_content_order(descriptor.as_bytes()).is_err(), "{descriptor}");
        }
    }
}
