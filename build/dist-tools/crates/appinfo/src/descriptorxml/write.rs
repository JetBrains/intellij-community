// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The serializer, byte for byte as `JDOMUtil.write(Element)` performs it.
//!
//! The chain is `JDOMUtil.write`, `writeElement(element, "\n")`, `createOutputter("\n")` and `MyXMLOutputter` over
//! `DEFAULT_FORMAT` (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:490`, `:524`, `:528`,
//! `:580`, `:631`). `DEFAULT_FORMAT` is
//!
//! ```text
//! Format.getCompactFormat().setIndent("  ").setTextMode(Format.TextMode.TRIM).setLineSeparator("\n")
//! ```
//!
//! (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:563-566`). The layout rules below are
//! `printElement` and its helpers in `community/platform/util/jdom/src/org/jdom/output/XMLOutputter.java`.
//! `quick-xml` writes the events, with an indent of two spaces and `" />"` for an element with no content.
//!
//! Four properties of that configuration decide most of the bytes:
//!
//! - no XML declaration and no trailing newline. `output(Element, Writer)` calls `printElement` and nothing else
//!   (`XMLOutputter.java:278-282`).
//! - an element with no significant content writes `" />"`, because `expandEmptyElements` is false
//!   (`Format.java:375`, `XMLOutputter.java:650`).
//! - `TextMode.TRIM` trims every text node with Java's `String.trim`. That cuts every character at or below `' '`, and
//!   not only the four XML whitespace characters (`XMLOutputter.java:581-589`).
//! - an element whose content is text only takes no indentation at all. An element with an element child takes full
//!   indentation, and each text run takes a line of its own (`XMLOutputter.java:660-673`, `:703-745`).

use std::borrow::Cow;
use std::io;

use quick_xml::Writer;
use quick_xml::events::attributes::Attribute as XmlAttribute;
use quick_xml::events::{BytesCData, BytesEnd, BytesStart, BytesText, Event};
use quick_xml::name::QName;

use super::read::{is_all_xml_whitespace, is_xml_whitespace_byte};
use super::{Element, Namespace, Node};

/// Serializes an element the way `JDOMUtil.write` does.
pub fn write(element: &Element) -> String {
    let mut writer = Writer::new_with_indent(Vec::new(), b' ', 2);
    writer.config_mut().add_space_before_slash_in_empty_elements = true;
    write_element(&mut writer, element, &mut Vec::new()).expect("a writer to memory does not fail");
    String::from_utf8(writer.into_inner()).expect("the writer writes UTF-8 text")
}

/// Writes one element. `bound` is JDOM's `NamespaceStack`: the declarations that the ancestors printed.
fn write_element(writer: &mut Writer<Vec<u8>>, element: &Element, bound: &mut Vec<Namespace>) -> io::Result<()> {
    let depth = bound.len();
    let name = element.qualified_name();
    let mut start = BytesStart::new(name.as_str());
    // `printElementNamespace`, then `printAdditionalNamespaces`, then `printAttributes` (`XMLOutputter.java:639-646`).
    declare(&mut start, bound, &element.prefix, &element.uri);
    for declaration in &element.namespaces {
        declare(&mut start, bound, &declaration.prefix, &declaration.uri);
    }
    for attribute in &element.attributes {
        // `printAttributes` declares the namespace of a prefixed attribute first (`XMLOutputter.java:873-876`).
        if let Some((prefix, _)) = attribute.name.split_once(':') {
            declare(&mut start, bound, prefix, &attribute.uri);
        }
        start.push_attribute(XmlAttribute {
            key: QName(&attribute.name),
            value: escape(&attribute.value, true),
        });
    }

    let content = significant(&element.children);
    if content.is_empty() {
        writer.write_event(Event::Empty(start))?;
    } else {
        writer.write_event(Event::Start(start))?;
        if content.iter().any(|node| matches!(node, Node::Element(_))) {
            let mut index = 0;
            while index < content.len() {
                if let Node::Element(child) = &content[index] {
                    write_element(writer, child, bound)?;
                    index += 1;
                    continue;
                }
                let end = content[index..]
                    .iter()
                    .position(|node| matches!(node, Node::Element(_)))
                    .map_or(content.len(), |offset| index + offset);
                let run = significant(&content[index..end]);
                if !run.is_empty() {
                    writer.write_indent()?;
                    write_text_run(writer, run)?;
                    // A text event keeps the next tag on the same line. `Eof` writes nothing and makes the next tag
                    // start a new line. JDOM puts the next child on a line of its own too.
                    writer.write_event(Event::Eof)?;
                }
                index = end;
            }
        } else {
            write_text_run(writer, content)?;
        }
        writer.write_event(Event::End(BytesEnd::new(name.as_str())))?;
    }
    bound.truncate(depth);
    Ok(())
}

/// `printTextRange` (`XMLOutputter.java:765-833`). It trims each node, and it pads the join of two nodes with one space
/// when either side had whitespace there. That is the one place where this serializer inserts a character that no node
/// holds.
fn write_text_run(writer: &mut Writer<Vec<u8>>, run: &[Node]) -> io::Result<()> {
    let mut previous: Option<&str> = None;
    for node in run {
        let (text, is_cdata) = match node {
            Node::Text(text) => (text.as_str(), false),
            Node::CData(text) => (text.as_str(), true),
            Node::Element(_) => continue,
        };
        if text.is_empty() {
            continue;
        }
        if previous.is_some_and(|previous| ends_with_white(previous) || starts_with_white(text)) {
            writer.write_event(Event::Text(BytesText::from_escaped(" ")))?;
        }
        // The body of a CDATA section is trimmed and never escaped (`XMLOutputter.java:553-561`).
        if is_cdata {
            writer.write_event(Event::CData(BytesCData::new(java_trim(text))))?;
        } else {
            writer.write_event(Event::Text(BytesText::from_escaped(escape(java_trim(text), false))))?;
        }
        previous = Some(text);
    }
    Ok(())
}

/// `printNamespace` (`XMLOutputter.java:844-848`). A declaration is printed only when the prefix is not already bound
/// to this URI. So an element with no namespace inside a default namespace prints `xmlns=""`.
fn declare(start: &mut BytesStart<'_>, bound: &mut Vec<Namespace>, prefix: &str, uri: &str) {
    let current = bound
        .iter()
        .rev()
        .find(|declaration| declaration.prefix == prefix)
        .map_or("", |declaration| declaration.uri.as_str());
    if current == uri {
        return;
    }
    let key = if prefix.is_empty() {
        "xmlns".to_owned()
    } else {
        format!("xmlns:{prefix}")
    };
    start.push_attribute(XmlAttribute {
        key: QName(&key),
        value: escape(uri, true),
    });
    bound.push(Namespace {
        prefix: prefix.to_owned(),
        uri: uri.to_owned(),
    });
}

/// The nodes without the whitespace-only text nodes at either end: `skipLeadingWhite` and `skipTrailingWhite`
/// (`XMLOutputter.java:952-999`). An element is never whitespace.
fn significant(nodes: &[Node]) -> &[Node] {
    let is_white = |node: &Node| match node {
        Node::Element(_) => false,
        Node::Text(text) | Node::CData(text) => is_all_xml_whitespace(text),
    };
    let start = nodes.iter().position(|node| !is_white(node)).unwrap_or(nodes.len());
    let end = nodes.iter().rposition(|node| !is_white(node)).map_or(start, |last| last + 1);
    &nodes[start..end]
}

fn starts_with_white(text: &str) -> bool {
    text.bytes().next().is_some_and(is_xml_whitespace_byte)
}

fn ends_with_white(text: &str) -> bool {
    text.bytes().last().is_some_and(is_xml_whitespace_byte)
}

/// `String.trim`, which cuts every leading and trailing character at or below `' '`.
fn java_trim(text: &str) -> &str {
    text.trim_matches(|character: char| character <= ' ')
}

/// `JDOMUtil.escapeText` over `JDOMUtil.escapeChar` (`community/platform/util/src/com/intellij/openapi/util/JDOMUtil.java:587-635`).
///
/// It is not an escape function of `quick-xml`, because each of those differs from JDOM in bytes that descriptors
/// hold. `escape` also escapes an apostrophe, and `partial_escape` leaves a quotation mark in text. Element text is
/// `MyXMLOutputter.escapeElementEntities`, with `attribute` false. An attribute value is
/// `MyXMLOutputter.escapeAttributeEntities`, which also escapes a newline, a carriage return and a tab.
fn escape(text: &str, attribute: bool) -> Cow<'_, str> {
    let replacement = |character: char| match character {
        '<' => Some("&lt;"),
        '>' => Some("&gt;"),
        '"' => Some("&quot;"),
        '&' => Some("&amp;"),
        '\n' if attribute => Some("&#10;"),
        '\r' if attribute => Some("&#13;"),
        '\t' if attribute => Some("&#9;"),
        _ => None,
    };
    if !text.contains(|character| replacement(character).is_some()) {
        return Cow::Borrowed(text);
    }
    let mut escaped = String::with_capacity(text.len() + 16);
    for character in text.chars() {
        match replacement(character) {
            Some(entity) => escaped.push_str(entity),
            None => escaped.push(character),
        }
    }
    Cow::Owned(escaped)
}
