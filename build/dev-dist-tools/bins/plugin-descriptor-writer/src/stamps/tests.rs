// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The curated stamps cases. Every `want` is the text that
//! `doPatchPluginXml` produced on a real classpath, over the same source and the same scalars.
//!
//! The order of the two created elements is the part a reader cannot guess. `idea-version` is created first and
//! `version` second, and both go **after the anchor**. So the second one pushes the first one along, and the bytes
//! state `version` before `idea-version` every time.

use super::{Request, apply};
use crate::descriptorxml::{read, write};

fn stamp(source: &str, request: &Request) -> String {
    let mut element = read(source).unwrap_or_else(|error| panic!("read: {error:#}"));
    apply(&mut element, request);
    write(&element)
}

fn base() -> Request {
    Request {
        version: "1.0.0".into(),
        since_build: "263".into(),
        until_build: "263.*".into(),
        release_date: "20260101".into(),
        release_version: "2026300".into(),
        is_eap: true,
        ..Request::default()
    }
}

fn retained() -> Request {
    Request {
        retain_product_descriptor_for_bundled_plugin: true,
        ..base()
    }
}

#[test]
fn the_stamps_stage_matches_the_platform() {
    let cases = [
        (
            "with no anchor both created elements go to the front",
            base(),
            "<idea-plugin><depends>x</depends></idea-plugin>",
            "<idea-plugin>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <depends>x</depends>\n</idea-plugin>",
        ),
        (
            "id is the first anchor",
            base(),
            "<idea-plugin><id>a</id><depends>x</depends></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <depends>x</depends>\n</idea-plugin>",
        ),
        (
            "name is the anchor when there is no id",
            base(),
            "<idea-plugin><name>N</name><depends>x</depends></idea-plugin>",
            "<idea-plugin>\n  <name>N</name>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <depends>x</depends>\n</idea-plugin>",
        ),
        (
            "id wins over name whatever the document order was",
            base(),
            "<idea-plugin><name>N</name><id>a</id></idea-plugin>",
            "<idea-plugin>\n  <name>N</name>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n</idea-plugin>",
        ),
        (
            "a prefixed id is not an anchor",
            base(),
            "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:id>a</xi:id><depends>x</depends></idea-plugin>",
            "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <xi:id>a</xi:id>\n  <depends>x</depends>\n</idea-plugin>",
        ),
        (
            "an existing idea-version keeps its position and its other attributes",
            base(),
            "<idea-plugin><id>a</id><idea-version since-build=\"1\" other=\"k\"/></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" other=\"k\" until-build=\"263.*\" />\n</idea-plugin>",
        ),
        (
            "an existing version keeps its position and takes the new text",
            base(),
            "<idea-plugin><id>a</id><version>9.9</version></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <version>1.0.0</version>\n</idea-plugin>",
        ),
        (
            "a bundled plugin loses its product-descriptor",
            base(),
            "<idea-plugin><id>a</id><product-descriptor code=\"C\" release-date=\"20200101\" release-version=\"1\"/></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n</idea-plugin>",
        ),
        (
            "a retained product-descriptor keeps a stated release date and gains eap last",
            retained(),
            "<idea-plugin><id>a</id><product-descriptor code=\"C\" release-date=\"20200101\" release-version=\"1\"/></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <product-descriptor code=\"C\" release-date=\"20200101\" release-version=\"2026300\" eap=\"true\" />\n</idea-plugin>",
        ),
        (
            "a release date that starts with two underscores is a placeholder and is replaced",
            retained(),
            "<idea-plugin><id>a</id><product-descriptor code=\"C\" release-date=\"__DATE__\" release-version=\"1\"/></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <product-descriptor code=\"C\" release-date=\"20260101\" release-version=\"2026300\" eap=\"true\" />\n</idea-plugin>",
        ),
        (
            "a product-descriptor with no attribute but the code gains three in stamping order",
            retained(),
            "<idea-plugin><id>a</id><product-descriptor code=\"C\"/></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <product-descriptor code=\"C\" eap=\"true\" release-date=\"20260101\" release-version=\"2026300\" />\n</idea-plugin>",
        ),
        (
            "an eap the descriptor states keeps its position",
            retained(),
            "<idea-plugin><id>a</id><product-descriptor code=\"C\" eap=\"false\"/></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <product-descriptor code=\"C\" eap=\"true\" release-date=\"20260101\" release-version=\"2026300\" />\n</idea-plugin>",
        ),
        (
            "a description returns to a CDATA section, which removes the escapes of its prose",
            base(),
            "<idea-plugin><id>a</id><description>&lt;b&gt;bold&lt;/b&gt; &amp; \"quoted\"</description></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <description><![CDATA[<b>bold</b> & \"quoted\"]]></description>\n</idea-plugin>",
        ),
        (
            "change-notes returns to a CDATA section too",
            base(),
            "<idea-plugin><id>a</id><change-notes>one &lt;br/&gt; two</change-notes></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <change-notes><![CDATA[one <br/> two]]></change-notes>\n</idea-plugin>",
        ),
        (
            "an empty description gets no CDATA section",
            base(),
            "<idea-plugin><id>a</id><description></description></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <description />\n</idea-plugin>",
        ),
        (
            "markup inside a description is destroyed by the CDATA restoration",
            base(),
            "<idea-plugin><id>a</id><description>before<b>bold</b>after</description></idea-plugin>",
            "<idea-plugin>\n  <id>a</id>\n  <version>1.0.0</version>\n  <idea-version since-build=\"263\" until-build=\"263.*\" />\n  <description><![CDATA[beforeafter]]></description>\n</idea-plugin>",
        ),
    ];
    for (name, request, source, want) in cases {
        assert_eq!(stamp(source, &request), want, "{name}");
    }
}

/// A null plugin version reaches the stage as an empty string. `Element.setText` then adds a text node that the writer
/// treats as insignificant. The result is `<version />`, which is what the platform writes.
#[test]
fn an_empty_version_writes_an_empty_element() {
    let request = Request {
        since_build: "1".into(),
        until_build: "2".into(),
        ..Request::default()
    };
    assert_eq!(
        stamp("<idea-plugin><id>a</id></idea-plugin>", &request),
        "<idea-plugin>\n  <id>a</id>\n  <version />\n  <idea-version since-build=\"1\" until-build=\"2\" />\n</idea-plugin>"
    );
}
