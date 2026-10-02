// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The cases of the composition grammar.

use crate::compose::{Composition, Include, Loading, ModuleRow};
use crate::descriptorxml;
use crate::test_support::option_lines;

/// Takes the composition of these flag lines, and refuses a line that it leaves.
fn take(lines: &[&str]) -> anyhow::Result<Option<Composition>> {
    let mut options = option_lines(lines);
    let composition = Composition::take(&mut options)?;
    options.finish()?;
    Ok(composition)
}

/// Composes the flags and checks two things. The element equals the tree that the reader builds from the expected text,
/// and the writer prints the expected text from it. So a composition and its source give one tree to every stage.
fn assert_composes(lines: &[&str], expected: &str) {
    let element = take(lines).unwrap().expect("the flags state a composition").element();
    assert_eq!(element, descriptorxml::read(expected).unwrap(), "the tree of {lines:?}");
    assert_eq!(descriptorxml::write(&element), expected, "the text of {lines:?}");
}

#[test]
fn no_flag_is_no_composition() {
    assert_eq!(take(&[]).unwrap(), None);
}

#[test]
fn the_rows_are_parsed() {
    let composition = take(&[
        "--alias=b",
        "--alias=a",
        "--include=required=/META-INF/a.xml",
        "--include=optional=b.xml",
        "--content-module=m.a",
        "--content-module=m.b;loading=embedded;required-if-available=m.a",
        "--additional-module=m.c;private;loading=on-demand",
        "--additional-module=m.d;required-if-available=m.c;loading=required",
        "--additional-module=m.e;loading=optional",
    ])
    .unwrap()
    .unwrap();
    let row = |name: &str, private, loading, required_if_available: Option<&str>| ModuleRow {
        name: name.to_owned(),
        private,
        loading,
        required_if_available: required_if_available.map(str::to_owned),
    };
    assert_eq!(
        composition,
        Composition {
            aliases: vec!["b".into(), "a".into()],
            includes: vec![
                Include {
                    href: "/META-INF/a.xml".into(),
                    optional: false,
                },
                Include {
                    href: "b.xml".into(),
                    optional: true,
                },
            ],
            content_modules: vec![
                row("m.a", false, None, None),
                row("m.b", false, Some(Loading::Embedded), Some("m.a")),
            ],
            additional_modules: vec![
                row("m.c", true, Some(Loading::OnDemand), None),
                row("m.d", false, Some(Loading::Required), Some("m.c")),
                row("m.e", false, Some(Loading::Optional), None),
            ],
        }
    );
}

#[test]
fn the_aliases_are_sorted_by_byte_order() {
    assert_composes(
        &[
            "--alias=com.intellij.modules.b",
            "--alias=com.intellij.modules.B",
            "--alias=com.intellij.modules.a",
        ],
        "<idea-plugin>
  <id>com.intellij</id>
  <module value=\"com.intellij.modules.B\" />
  <module value=\"com.intellij.modules.a\" />
  <module value=\"com.intellij.modules.b\" />
</idea-plugin>",
    );
}

#[test]
fn the_root_declares_no_xinclude_namespace_without_an_include() {
    assert_composes(
        &["--content-module=m.a"],
        "<idea-plugin>
  <id>com.intellij</id>
  <content namespace=\"jetbrains\">
    <module name=\"m.a\" />
  </content>
</idea-plugin>",
    );
}

#[test]
fn the_root_declares_the_xinclude_namespace_with_an_include() {
    assert_composes(
        &["--include=required=/META-INF/a.xml"],
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">
  <id>com.intellij</id>
  <xi:include href=\"/META-INF/a.xml\" />
</idea-plugin>",
    );
}

#[test]
fn an_optional_include_has_the_fallback_child_and_the_includes_keep_their_order() {
    assert_composes(
        &["--include=optional=z.xml", "--include=required=/META-INF/a.xml", "--alias=a"],
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">
  <id>com.intellij</id>
  <module value=\"a\" />
  <xi:include href=\"z.xml\">
    <xi:fallback />
  </xi:include>
  <xi:include href=\"/META-INF/a.xml\" />
</idea-plugin>",
    );
}

#[test]
fn a_module_row_writes_name_then_loading_then_required_if_available() {
    assert_composes(
        &[
            "--content-module=m.b;required-if-available=m.a;loading=required",
            "--content-module=m.a",
            "--content-module=m.c;required-if-available=m.a",
        ],
        "<idea-plugin>
  <id>com.intellij</id>
  <content namespace=\"jetbrains\">
    <module name=\"m.b\" loading=\"required\" required-if-available=\"m.a\" />
    <module name=\"m.a\" />
    <module name=\"m.c\" required-if-available=\"m.a\" />
  </content>
</idea-plugin>",
    );
}

#[test]
fn every_loading_value_of_the_platform_is_written() {
    assert_composes(
        &[
            "--content-module=m.a;loading=required",
            "--content-module=m.b;loading=embedded",
            "--content-module=m.c;loading=optional",
            "--content-module=m.d;loading=on-demand",
        ],
        "<idea-plugin>
  <id>com.intellij</id>
  <content namespace=\"jetbrains\">
    <module name=\"m.a\" loading=\"required\" />
    <module name=\"m.b\" loading=\"embedded\" />
    <module name=\"m.c\" loading=\"optional\" />
    <module name=\"m.d\" loading=\"on-demand\" />
  </content>
</idea-plugin>",
    );
}

/// The set block comes first, and the additional modules group by namespace after it.
#[test]
fn a_private_additional_module_after_jetbrains_ones_gives_two_blocks_in_that_order() {
    assert_composes(
        &[
            "--additional-module=a.one",
            "--additional-module=a.private;private",
            "--additional-module=a.two;loading=embedded",
            "--content-module=s.one",
        ],
        "<idea-plugin>
  <id>com.intellij</id>
  <content namespace=\"jetbrains\">
    <module name=\"s.one\" />
  </content>
  <content namespace=\"jetbrains\">
    <module name=\"a.one\" />
    <module name=\"a.two\" loading=\"embedded\" />
  </content>
  <content>
    <module name=\"a.private\" />
  </content>
</idea-plugin>",
    );
}

#[test]
fn a_private_additional_module_first_gives_the_reverse_order() {
    assert_composes(
        &[
            "--additional-module=a.private;private",
            "--additional-module=a.one",
            "--additional-module=a.second.private;loading=required;private",
        ],
        "<idea-plugin>
  <id>com.intellij</id>
  <content>
    <module name=\"a.private\" />
    <module name=\"a.second.private\" loading=\"required\" />
  </content>
  <content namespace=\"jetbrains\">
    <module name=\"a.one\" />
  </content>
</idea-plugin>",
    );
}

/// The children come in the order of the grammar, whatever the order of the flags.
#[test]
fn a_full_composition_orders_its_children() {
    assert_composes(
        &[
            "--additional-module=a.one",
            "--content-module=s.one",
            "--include=optional=b.xml",
            "--alias=z",
            "--alias=y",
            "--include=required=/META-INF/a.xml",
        ],
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">
  <id>com.intellij</id>
  <module value=\"y\" />
  <module value=\"z\" />
  <xi:include href=\"b.xml\">
    <xi:fallback />
  </xi:include>
  <xi:include href=\"/META-INF/a.xml\" />
  <content namespace=\"jetbrains\">
    <module name=\"s.one\" />
  </content>
  <content namespace=\"jetbrains\">
    <module name=\"a.one\" />
  </content>
</idea-plugin>",
    );
}

#[test]
fn a_malformed_row_is_refused_and_named() {
    for (row, want) in [
        ("--alias=", "the row '--alias=' states no plugin id"),
        (
            "--include=/META-INF/a.xml",
            "the row '--include=/META-INF/a.xml' is not '<kind>=<href>' with the kind 'required' or 'optional'",
        ),
        (
            "--include=fallback=a.xml",
            "the row '--include=fallback=a.xml' is not '<kind>=<href>'",
        ),
        ("--include=required=", "the row '--include=required=' is not '<kind>=<href>'"),
        ("--content-module=", "the row '--content-module=' states no module name"),
        (
            "--content-module=;loading=embedded",
            "the row '--content-module=;loading=embedded' states no module name",
        ),
        ("--additional-module=", "the row '--additional-module=' states no module name"),
        (
            "--content-module=m.a;loading=lazy",
            "the row '--content-module=m.a;loading=lazy' states the loading 'lazy', and only required, embedded, optional, \
             on-demand are supported",
        ),
        (
            "--content-module=m.a;loading=",
            "the row '--content-module=m.a;loading=' states the loading ''",
        ),
        (
            "--content-module=m.a;Loading=embedded",
            "the row '--content-module=m.a;Loading=embedded' states 'Loading=embedded', and only 'loading=<rule>' and \
             'required-if-available=<module>' are supported",
        ),
        (
            "--content-module=m.a;private",
            "the row '--content-module=m.a;private' states 'private', and only 'loading=<rule>'",
        ),
        (
            "--additional-module=m.a;private=true",
            "the row '--additional-module=m.a;private=true' states 'private=true', and only 'private', 'loading=<rule>'",
        ),
        (
            "--additional-module=m.a;required-if-available",
            "the row '--additional-module=m.a;required-if-available' states 'required-if-available'",
        ),
        (
            "--additional-module=m.a;required-if-available=",
            "the row '--additional-module=m.a;required-if-available=' states 'required-if-available='",
        ),
        ("--additional-module=m.a;", "the row '--additional-module=m.a;' states ''"),
        (
            "--additional-module=m.a;loading=embedded;loading=required",
            "the row '--additional-module=m.a;loading=embedded;loading=required' states 'loading' twice",
        ),
        (
            "--additional-module=m.a;private;private",
            "the row '--additional-module=m.a;private;private' states 'private' twice",
        ),
    ] {
        match take(&["--alias=a", row]) {
            Ok(composition) => panic!("{row}: accepted {composition:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{row}: {error:#}"),
        }
    }
}

#[test]
fn a_repeated_alias_or_module_is_refused() {
    for (rows, want) in [
        (
            &["--alias=a", "--alias=b", "--alias=a"][..],
            "the row '--alias=a' states an alias that another row states",
        ),
        (
            &["--content-module=m.a", "--content-module=m.a;loading=embedded"][..],
            "the module 'm.a' is stated by two rows of --content-module or --additional-module",
        ),
        (
            &["--content-module=m.a", "--additional-module=m.a;private"][..],
            "the module 'm.a' is stated by two rows",
        ),
    ] {
        match take(rows) {
            Ok(composition) => panic!("{rows:?}: accepted {composition:?}"),
            Err(error) => assert!(format!("{error:#}").contains(want), "{rows:?}: {error:#}"),
        }
    }
}
