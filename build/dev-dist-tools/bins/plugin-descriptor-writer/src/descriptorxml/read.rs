// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use anyhow::{Result, anyhow, bail};
use quick_xml::XmlVersion;
use quick_xml::escape::resolve_xml_entity;
use quick_xml::events::{BytesDecl, BytesStart, Event};

use super::{Attribute, Element, Namespace, Node};

/// Parses a descriptor the way `JDOMUtil.load` does, and returns the root element.
///
/// ### The events and the platform's reader
///
/// The platform's reader is Aalto, configured at
/// `community/platform/util/xmlDom/src/com/intellij/util/xml/dom/StaxFactory.kt:17-34` and driven by
/// `community/platform/util/src/com/intellij/openapi/util/SafeStAXStreamBuilder.kt`. `quick-xml` supplies the events,
/// the attribute-value normalization, the line-end normalization and the five predefined entities. This function
/// builds the tree from them. `doCoalesceText(true)` merges a CDATA section into the text run around it, so the tree
/// holds one text node where the document wrote three. A trimming writer then writes `foo <![CDATA[ bar]]>` as
/// `foo  bar`, and three nodes would give `foo bar`.
///
/// ### What this drops, and where the platform drops it
///
/// - a comment and the XML declaration: `SafeStAXStreamBuilder.kt:21`, `:178`
/// - a whitespace-only text run: `SafeStAXStreamBuilder.kt:171-177`
///
/// ### What this refuses
///
/// The descriptors that the rules declare use a subset of XML. Each construct below occurs in none of them, so the
/// reader refuses it and names it. A port of the platform's answer for each one would be code that no input runs.
///
/// - a DOCTYPE, a processing instruction and a byte order mark.
/// - an XML declaration of a version other than 1.0, or of an encoding other than UTF-8.
/// - an entity reference that is not predefined, and a character reference in text. A character reference in an
///   attribute value is accepted, because descriptors use it there.
/// - a namespace declaration below the root element, and a prefix that the root element does not declare.
/// - an attribute of the `xml` prefix, such as `xml:space` or `xml:base`.
/// - a duplicate attribute, a document without a root element, and a comment between two parts of one text run. Aalto
///   ends the text node at such a comment, and the reader would join the two parts.
pub(crate) fn read(text: &str) -> Result<Element> {
    // `quick-xml` removes a UTF-8 byte order mark without an event, so the check is here.
    if text.starts_with('\u{feff}') {
        bail!("line 1, offset 0: a byte order mark is not supported");
    }
    let mut reader = Reader {
        xml: quick_xml::Reader::from_str(text),
        input: text,
        root_namespaces: Vec::new(),
    };
    reader.read_document()
}

struct Reader<'a> {
    xml: quick_xml::Reader<&'a [u8]>,
    input: &'a str,
    /// The declarations of the root element, the only element that can state one.
    root_namespaces: Vec<Namespace>,
}

impl<'a> Reader<'a> {
    fn read_document(&mut self) -> Result<Element> {
        let mut root = None;
        loop {
            match self.next_event()? {
                Event::Decl(declaration) if root.is_none() => self.check_declaration(&declaration)?,
                Event::Comment(_) => {}
                Event::Text(text) if is_all_xml_whitespace(&text) => {}
                Event::Start(start) if root.is_none() => root = Some(self.read_element(&start, false, true)?),
                Event::Empty(start) if root.is_none() => root = Some(self.read_element(&start, true, true)?),
                Event::Start(_) | Event::Empty(_) => return Err(self.error("a second root element")),
                Event::Text(_) | Event::CData(_) | Event::GeneralRef(_) => {
                    return Err(self.error("text outside the root element"));
                }
                Event::Eof => break,
                event => return Err(self.refusal(&event)),
            }
        }
        root.ok_or_else(|| self.error("the document has no root element"))
    }

    /// Accepts the declaration that every declared descriptor writes, `version="1.0"` with an optional UTF-8 encoding.
    fn check_declaration(&self, declaration: &BytesDecl<'_>) -> Result<()> {
        let version = declaration.version().map_err(|error| self.error(&error.to_string()))?;
        if version != "1.0" {
            return Err(self.error(&format!("XML version {version} is not supported")));
        }
        if let Some(encoding) = declaration.encoding() {
            let encoding = encoding.map_err(|error| self.error(&error.to_string()))?;
            if !encoding.eq_ignore_ascii_case("utf-8") {
                return Err(self.error(&format!("the encoding {encoding} is not supported")));
            }
        }
        Ok(())
    }

    /// Reads one element and everything under it. `start` is its start tag.
    fn read_element(&mut self, start: &BytesStart<'a>, empty: bool, is_root: bool) -> Result<Element> {
        let mut attributes = Vec::new();
        for attribute in start.attributes() {
            let attribute = attribute.map_err(|error| self.error(&error.to_string()))?;
            let value = attribute
                .normalized_value(XmlVersion::Implicit1_0)
                .map_err(|error| self.error(&error.to_string()))?;
            let name = attribute.key.0;
            let declared_prefix = if name == "xmlns" { Some("") } else { name.strip_prefix("xmlns:") };
            match declared_prefix {
                // Aalto reports a declaration through `getNamespacePrefix` and never through `getAttributeLocalName`.
                // So it is a declaration here and not an attribute.
                Some(prefix) if is_root => self.root_namespaces.push(Namespace {
                    prefix: prefix.to_owned(),
                    uri: value.into_owned(),
                }),
                Some(_) => {
                    return Err(self.error(&format!("the declaration {name} below the root element is not supported")));
                }
                None => attributes.push(Attribute {
                    name: name.to_owned(),
                    uri: String::new(),
                    value: value.into_owned(),
                }),
            }
        }
        // The root can declare a prefix after an attribute that uses it, so the check runs after every declaration.
        for attribute in &mut attributes {
            if let Some((prefix, _)) = attribute.name.split_once(':') {
                if prefix == "xml" {
                    return Err(self.error(&format!("the attribute {} is not supported", attribute.name)));
                }
                attribute.uri = self.namespace_uri(prefix)?.to_owned();
            }
        }

        let (prefix, name) = start.name().0.split_once(':').unwrap_or(("", start.name().0));
        let mut element = Element {
            name: name.to_owned(),
            prefix: prefix.to_owned(),
            uri: self.namespace_uri(prefix)?.to_owned(),
            namespaces: if is_root { self.root_namespaces.clone() } else { Vec::new() },
            attributes,
            children: Vec::new(),
        };
        if !empty {
            self.read_children(&mut element)?;
        }
        Ok(element)
    }

    /// Returns the URI that the root element binds to this prefix. The empty prefix without a declaration is no
    /// namespace, and any other prefix without one is refused.
    fn namespace_uri(&self, prefix: &str) -> Result<&str> {
        match self.root_namespaces.iter().find(|declaration| declaration.prefix == prefix) {
            Some(declaration) => Ok(&declaration.uri),
            None if prefix.is_empty() => Ok(""),
            None => Err(self.error(&format!("the prefix '{prefix}' has no declaration on the root element"))),
        }
    }

    /// Reads the content up to the end tag of the element.
    ///
    /// The text run grows across events and goes into the tree at every element boundary. The platform's reader
    /// coalesces text and then drops a run that is whitespace only.
    fn read_children(&mut self, element: &mut Element) -> Result<()> {
        let mut run = String::new();
        // A comment after text is harmless until more text follows it in the same run.
        let mut comment_after_text = false;
        loop {
            let piece = match self.next_event()? {
                Event::Text(text) => text.xml10_content().into_owned(),
                Event::CData(text) => text.xml10_content().into_owned(),
                // A character reference is no predefined entity, so it is refused here too.
                Event::GeneralRef(reference) => match resolve_xml_entity(&reference) {
                    Some(expansion) => expansion.to_owned(),
                    None => {
                        return Err(self.error(&format!("the reference &{}; in text is not supported", &*reference)));
                    }
                },
                Event::Comment(_) => {
                    comment_after_text |= !is_all_xml_whitespace(&run);
                    continue;
                }
                Event::Start(start) => {
                    flush(&mut run, element);
                    comment_after_text = false;
                    let child = self.read_element(&start, false, false)?;
                    element.children.push(Node::Element(child));
                    continue;
                }
                Event::Empty(start) => {
                    flush(&mut run, element);
                    comment_after_text = false;
                    let child = self.read_element(&start, true, false)?;
                    element.children.push(Node::Element(child));
                    continue;
                }
                // The reader checks that the end tag matches the start tag.
                Event::End(_) => {
                    flush(&mut run, element);
                    return Ok(());
                }
                Event::Eof => return Err(self.error(&format!("the element {:?} does not end", element.name))),
                event => return Err(self.refusal(&event)),
            };
            if comment_after_text && !is_all_xml_whitespace(&piece) {
                return Err(self.error(&format!(
                    "a comment inside the text of <{}> is not supported",
                    element.qualified_name()
                )));
            }
            run.push_str(&piece);
        }
    }

    /// Names a construct that no declared descriptor uses.
    fn refusal(&self, event: &Event<'_>) -> anyhow::Error {
        let construct = match event {
            Event::DocType(_) => "a DOCTYPE".to_owned(),
            Event::PI(instruction) => format!("the processing instruction <?{}?>", instruction.target()),
            Event::Decl(_) => "an XML declaration after the start of the document".to_owned(),
            other => format!("the event {other:?}"),
        };
        self.error(&format!("{construct} is not supported"))
    }

    fn next_event(&mut self) -> Result<Event<'a>> {
        self.xml.read_event().map_err(|error| {
            let at = usize::try_from(self.xml.error_position()).unwrap_or(usize::MAX);
            self.error_at(at, &error.to_string())
        })
    }

    fn error(&self, message: &str) -> anyhow::Error {
        let at = usize::try_from(self.xml.buffer_position()).unwrap_or(usize::MAX);
        self.error_at(at, message)
    }

    #[expect(clippy::naive_bytecount, reason = "an error path, counted once")]
    fn error_at(&self, at: usize, message: &str) -> anyhow::Error {
        let at = at.min(self.input.len());
        let line = 1 + self.input.as_bytes()[..at].iter().filter(|&&byte| byte == b'\n').count();
        anyhow!("line {line}, offset {at}: {message}")
    }
}

/// Moves the run into the tree as a text node, unless it is whitespace only.
fn flush(run: &mut String, element: &mut Element) {
    if !is_all_xml_whitespace(run) {
        element.children.push(Node::Text(std::mem::take(run)));
    }
    run.clear();
}

/// Reports whether the text is `Verifier.isXMLWhitespace` only
/// (`community/platform/util/jdom/src/org/jdom/Verifier.java:1033-1041`). That is four characters and not Unicode
/// whitespace. The empty string is all whitespace (`Verifier.java:1056`).
pub(super) fn is_all_xml_whitespace(text: &str) -> bool {
    text.bytes().all(is_xml_whitespace_byte)
}

/// Reports whether the byte is one of the four characters of `Verifier.isXMLWhitespace`.
pub(super) const fn is_xml_whitespace_byte(byte: u8) -> bool {
    matches!(byte, b' ' | b'\n' | b'\t' | b'\r')
}
