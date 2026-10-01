use super::*;

#[test]
fn content_order_of_descriptor() {
    let descriptor = r#"<idea-plugin>
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

/// A prefixed root and a prefixed `<content>` still match by the local name, and a prefixed attribute is not the
/// name. The XML declaration and the comment go, and the entity resolves.
#[test]
fn prefixes_and_entities_match_by_the_local_name() {
    let descriptor = r#"<?xml version="1.0"?>
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
        assert!(parse_content_order(descriptor).is_err(), "{descriptor}");
    }
}
