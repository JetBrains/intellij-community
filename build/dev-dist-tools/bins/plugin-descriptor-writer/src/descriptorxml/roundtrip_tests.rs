// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The curated round-trip cases.
//!
//! Each case states one construct, and every expectation is the text the platform wrote.
//! `JDOMUtil.write(JDOMUtil.load(source))` on a real classpath produced every `want`. No `want` comes from this port's
//! own output. `./build/dev-dist.cmd snapshot diff` guards the population: it compares every plugin main jar of a
//! composed distribution against a baseline. These cases name the rule of each byte, so a failure names the rule and
//! not a plugin.

use super::{Attribute, Element, Node, read, write};

fn round_trip(source: &str) -> String {
    write(&read(source).unwrap_or_else(|error| panic!("read {source:?}: {error:#}")))
}

#[test]
fn the_round_trip_reproduces_the_serializer() {
    let cases = [
        (
            "an element with no content writes a space and a slash",
            "<idea-plugin><depends/></idea-plugin>",
            "<idea-plugin>\n  <depends />\n</idea-plugin>",
        ),
        (
            "an element whose content is text takes no indentation",
            "<idea-plugin>\n  <id>com.example</id>\n</idea-plugin>",
            "<idea-plugin>\n  <id>com.example</id>\n</idea-plugin>",
        ),
        (
            "every text run is trimmed",
            "<idea-plugin><name>\n   Example\n  </name></idea-plugin>",
            "<idea-plugin>\n  <name>Example</name>\n</idea-plugin>",
        ),
        (
            "a whitespace-only element is empty and writes a space and a slash",
            "<idea-plugin><name>   </name></idea-plugin>",
            "<idea-plugin>\n  <name />\n</idea-plugin>",
        ),
        (
            "a comment is deleted",
            "<idea-plugin>\n  <!-- a note -->\n  <id>a</id>\n</idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n</idea-plugin>",
        ),
        (
            "a comment next to whitespace does not split a run",
            "<idea-plugin><name>\n  <!-- x -->\n  one\n  <!-- y -->\n</name></idea-plugin>",
            "<idea-plugin>\n  <name>one</name>\n</idea-plugin>",
        ),
        (
            "an XML declaration is deleted, and the output has no declaration of its own",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<idea-plugin><id>a</id></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n</idea-plugin>",
        ),
        (
            "a CDATA section becomes plain escaped text",
            "<idea-plugin><description><![CDATA[<b>bold</b>]]></description></idea-plugin>",
            "<idea-plugin>\n  <description>&lt;b&gt;bold&lt;/b&gt;</description>\n</idea-plugin>",
        ),
        (
            "a CDATA section is folded into the text run around it",
            "<idea-plugin><name>one <![CDATA[ two]]></name></idea-plugin>",
            "<idea-plugin>\n  <name>one  two</name>\n</idea-plugin>",
        ),
        (
            "a double quote is escaped inside element text, and an apostrophe is not",
            "<idea-plugin><name>say \"it's\"</name></idea-plugin>",
            "<idea-plugin>\n  <name>say &quot;it's&quot;</name>\n</idea-plugin>",
        ),
        (
            "an attribute keeps a single-quoted value and takes double quotes",
            "<idea-plugin><module name='a.b'/></idea-plugin>",
            "<idea-plugin>\n  <module name=\"a.b\" />\n</idea-plugin>",
        ),
        (
            "a newline inside an attribute value becomes a space, and a reference stays a newline",
            "<idea-plugin><module name=\"a&#10;b\" other=\"c\nd\"/></idea-plugin>",
            "<idea-plugin>\n  <module name=\"a&#10;b\" other=\"c d\" />\n</idea-plugin>",
        ),
        (
            "a character reference in an attribute value resolves, and an apostrophe stays unescaped",
            "<idea-plugin><action text=\"Don&#39;t&#8217;s\" tab=\"a&#9;b\"/></idea-plugin>",
            "<idea-plugin>\n  <action text=\"Don't\u{2019}s\" tab=\"a&#9;b\" />\n</idea-plugin>",
        ),
        (
            "a declared prefix is printed before every attribute, whatever the source order was",
            "<idea-plugin url=\"u\" xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"h\"/></idea-plugin>",
            "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\" url=\"u\">\n  <xi:include href=\"h\" />\n</idea-plugin>",
        ),
        (
            "a default namespace and a prefixed attribute of the root keep their order",
            "<component xsi:schemaLocation=\"s\" xmlns=\"urn:a\" xmlns:xsi=\"urn:x\"><names product=\"P\"/></component>",
            "<component xmlns=\"urn:a\" xmlns:xsi=\"urn:x\" xsi:schemaLocation=\"s\">\n  <names product=\"P\" />\n</component>",
        ),
        (
            "an element with both text and an element child indents both",
            "<idea-plugin>text<id>a</id></idea-plugin>",
            "<idea-plugin>\n  text\n  <id>a</id>\n</idea-plugin>",
        ),
        (
            "a stray text between two elements takes a line of its own",
            "<idea-plugin><extensions>\n  <a/>\n  \"\n  <b/>\n</extensions></idea-plugin>",
            "<idea-plugin>\n  <extensions>\n    <a />\n    &quot;\n    <b />\n  </extensions>\n</idea-plugin>",
        ),
        (
            "nesting adds one indent level a step",
            "<idea-plugin><extensions><ep id=\"x\"/></extensions></idea-plugin>",
            "<idea-plugin>\n  <extensions>\n    <ep id=\"x\" />\n  </extensions>\n</idea-plugin>",
        ),
        (
            "the five predefined entities are resolved and then re-escaped",
            "<idea-plugin><name>&lt;&amp;&gt;&quot;&apos;</name></idea-plugin>",
            "<idea-plugin>\n  <name>&lt;&amp;&gt;&quot;'</name>\n</idea-plugin>",
        ),
    ];
    for (name, source, want) in cases {
        assert_eq!(round_trip(source), want, "{name}");
    }
}

/// Two adjacent text nodes print as one run. Only the patch makes them, when it removes an element between two runs.
/// The writer pads the join with one space when either side has whitespace there, and only then, as `printTextRange`
/// does.
#[test]
fn two_adjacent_text_nodes_print_as_one_run() {
    let mut element = Element::new("idea-plugin");
    element.children = vec![Node::Text("one ".into()), Node::Text("two".into()), Node::Text("three".into())];
    assert_eq!(write(&element), "<idea-plugin>one twothree</idea-plugin>");
    element.children.push(Node::Element(Element::new("id")));
    assert_eq!(write(&element), "<idea-plugin>\n  one twothree\n  <id />\n</idea-plugin>");
}

/// The writer must not end the text with a newline. A jar entry that gained one would differ from every recorded
/// descriptor, and the whole port would be off by one byte everywhere.
#[test]
fn the_text_has_no_trailing_newline() {
    let text = round_trip("<idea-plugin><id>a</id></idea-plugin>");
    assert!(!text.ends_with('\n'), "the text ends with a newline: {text:?}");
}

/// A carriage return is reported as a newline, in text and in CDATA. In an attribute value it becomes a space, and
/// `\r\n` becomes one space.
#[test]
fn a_carriage_return_is_normalized() {
    let element = read("<idea-plugin a=\"x\r\ny\rz\"><name>one\r\ntwo\rthree<![CDATA[\r\n]]></name></idea-plugin>").unwrap();
    assert_eq!(element.attribute("a"), Some("x y z"));
    assert_eq!(element.child("name").unwrap().text(), "one\ntwo\nthree\n");
}

/// A malformed descriptor must fail loudly. The platform's reader throws. An action that writes a truncated
/// descriptor instead would fail at class-load time in the IDE.
#[test]
fn malformed_input_is_refused() {
    let cases = [
        ("an unclosed element", "<idea-plugin><id>a</id>"),
        ("a mismatched end tag", "<idea-plugin><id>a</name></idea-plugin>"),
        ("an attribute with no value", "<idea-plugin><module name/></idea-plugin>"),
        (
            "an unterminated CDATA",
            "<idea-plugin><description><![CDATA[x</description></idea-plugin>",
        ),
        ("an unterminated comment", "<idea-plugin><!-- x </idea-plugin>"),
        ("text outside the root", "junk<idea-plugin/>"),
        ("a second root element", "<idea-plugin/><idea-plugin/>"),
        ("a reference with no ending", "<idea-plugin><name>&amp</name></idea-plugin>"),
        ("an unquoted attribute value", "<idea-plugin><module name=a/></idea-plugin>"),
        (
            "a character reference out of range in an attribute",
            "<idea-plugin><name a=\"&#x110000;\"/></idea-plugin>",
        ),
    ];
    for (name, source) in cases {
        assert!(read(source).is_err(), "{name}: {source:?} was accepted");
    }
}

/// Each construct that no declared descriptor uses is refused with an error that names it.
#[test]
fn a_construct_outside_the_subset_is_refused() {
    let cases = [
        ("<!DOCTYPE idea-plugin><idea-plugin/>", "a DOCTYPE is not supported"),
        (
            "<idea-plugin><?target data?></idea-plugin>",
            "the processing instruction <?target?> is not supported",
        ),
        ("<?target data?><idea-plugin/>", "the processing instruction <?target?>"),
        ("\u{feff}<idea-plugin/>", "a byte order mark is not supported"),
        ("<?xml version=\"1.1\"?><idea-plugin/>", "XML version 1.1 is not supported"),
        (
            "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><idea-plugin/>",
            "the encoding ISO-8859-1 is not supported",
        ),
        (
            "<idea-plugin><name>one&nbsp;two</name></idea-plugin>",
            "the reference &nbsp; in text is not supported",
        ),
        (
            "<idea-plugin><name>&#65;</name></idea-plugin>",
            "the reference &#65; in text is not supported",
        ),
        ("<idea-plugin a=\"&nbsp;\"/>", "nbsp"),
        (
            "<idea-plugin><extensions xmlns:xi=\"http://www.w3.org/2001/XInclude\"/></idea-plugin>",
            "the declaration xmlns:xi below the root element is not supported",
        ),
        (
            "<idea-plugin><xi:include href=\"a.xml\"/></idea-plugin>",
            "the prefix 'xi' has no declaration on the root element",
        ),
        (
            "<idea-plugin><name p:a=\"1\"/></idea-plugin>",
            "the prefix 'p' has no declaration on the root element",
        ),
        (
            "<idea-plugin><pre xml:space=\"preserve\"> a </pre></idea-plugin>",
            "the attribute xml:space is not supported",
        ),
        (
            "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"a.xml\" xml:base=\"b\"/></idea-plugin>",
            "the attribute xml:base is not supported",
        ),
        ("<idea-plugin a=\"1\" a=\"2\"/>", "a"),
        ("", "the document has no root element"),
        ("<!-- only a comment -->\n", "the document has no root element"),
        (
            "<idea-plugin><name>one<!-- x -->two</name></idea-plugin>",
            "a comment inside the text of <name> is not supported",
        ),
        (
            "<idea-plugin><name>one <!-- x --> <![CDATA[two]]></name></idea-plugin>",
            "a comment inside the text of <name> is not supported",
        ),
    ];
    for (source, want) in cases {
        match read(source) {
            Ok(element) => panic!("{source:?} was accepted as {element:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{source:?}: {error:#}"),
        }
    }
}

/// The error of an unclosed element names the innermost open element.
#[test]
fn an_unclosed_element_names_itself() {
    let error = read("<idea-plugin>").unwrap_err();
    assert!(
        format!("{error:#}").contains("the element \"idea-plugin\" does not end"),
        "{error:#}"
    );
}

/// The writer declares the namespace of a prefixed attribute just before the attribute, when no ancestor binds the
/// prefix (`XMLOutputter.java:873-876`). The include stage refuses the input that needs it, so the case builds the tree.
#[test]
fn an_unbound_attribute_prefix_is_declared_before_the_attribute() {
    let attribute = |name: &str, uri: &str, value: &str| Attribute {
        name: name.to_owned(),
        uri: uri.to_owned(),
        value: value.to_owned(),
    };
    let mut plain = Element::new("plain");
    plain.attributes = vec![attribute("a", "", "1"), attribute("p:attr", "urn:p", "2")];
    let mut root = Element::new("idea-plugin");
    root.children.push(Node::Element(plain));
    assert_eq!(
        write(&root),
        "<idea-plugin>\n  <plain a=\"1\" xmlns:p=\"urn:p\" p:attr=\"2\" />\n</idea-plugin>"
    );
}
