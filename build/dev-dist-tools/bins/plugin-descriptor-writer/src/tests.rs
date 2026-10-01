// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The request surface of the plugin mode.
//!
//! These cases cover three things. They cover the spelling of the options that the rule states, and the scalars that
//! this binary computes from the build number. They also cover the refusal of a request that the rule cannot state.
//! The curated cases of `descriptorxml`, `stamps` and `structural` gate the bytes. `./build/dev-dist.cmd snapshot diff`
//! gates them over a composed distribution.

use std::path::Path;

use testkit::{TempDir, read_text, require_absent, write_file};

use crate::test_support::{build_number_file, descriptor_jar, lines, option_lines, path_string, run_arguments, run_request, testdata};
use crate::{Mode, take_mode};

fn path(dir: &Path, name: &str) -> String {
    path_string(&dir.join(name))
}

/// `DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS`: 2026-01-01T00:00:00Z.
const PINNED_BUILD_DATE: &str = "--build-date-seconds=1767225600";

/// The option that names an application info of `testdata/plugin_descriptor`.
fn application_info(name: &str) -> String {
    format!("--application-info-source={}", testdata(&format!("plugin_descriptor/{name}.xml")))
}

#[test]
fn the_whole_request_is_patched() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("out").join("intellij.example.plugin.xml");
    write_file(
        &source,
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><id>a</id>\
         <description>&lt;b&gt;x&lt;/b&gt;</description>\
         <xi:include href=\"extra.xml\"/>\
         <content><module name=\"a.b\"/><module name=\"dropped\"/></content></idea-plugin>",
    );
    write_file(dir.join("extra.xml"), "<idea-plugin><extensions/></idea-plugin>");
    write_file(dir.join("a.b.xml"), "<idea-plugin package=\"a.b\"/>");

    let request = vec![
        format!("--out={}", path_string(&output)),
        "--main-module=intellij.example".to_owned(),
        format!("--source={}", path_string(&source)),
        format!("--build-number-file={}", build_number_file(dir, "263.SNAPSHOT")),
        application_info("eap"),
        PINNED_BUILD_DATE.to_owned(),
        "--exact-version=false".to_owned(),
        "--retain-product-descriptor=false".to_owned(),
        "--embed-content-modules=true".to_owned(),
        "--refused-content-module=dropped".to_owned(),
        "--separate-jar=a.b".to_owned(),
        format!("--plugin-descriptor=META-INF/extra.xml={}", path(dir, "extra.xml")),
        format!("--plugin-descriptor=a.b.xml={}", path(dir, "a.b.xml")),
    ];
    assert_eq!(run_request(dir, &request), 0);

    // `263.SNAPSHOT` with the fixed snapshot segment gives the version. The range comes from the **build number** and
    // not from that version, the way the assembly calls `getCompatiblePlatformVersionRange`. `263.SNAPSHOT` matches no
    // numeric shape, so both ends are the build number itself. That is what `//build:idea_air_dist` stamps today.
    assert_eq!(
        read_text(&output),
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\">
  <id>a</id>
  <version>263.99999999.0</version>
  <idea-version since-build=\"263.SNAPSHOT\" until-build=\"263.SNAPSHOT\" />
  <description><![CDATA[<b>x</b>]]></description>
  <extensions />
  <content>
    <module name=\"a.b\"><![CDATA[<idea-plugin package=\"a.b\" separate-jar=\"true\" />]]></module>
  </content>
</idea-plugin>"
    );
}

#[test]
fn reserialization_precedes_content_embedding() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("out").join("plugin.xml");
    let descriptor = dir.join("a.b.xml");
    write_file(
        &source,
        "<idea-plugin><description><![CDATA[<b>x</b>]]></description>\
         <content><module name=\"a.b\"/></content></idea-plugin>",
    );
    write_file(&descriptor, "<idea-plugin package=\"a.b\"/>");

    let mut request = minimal_request(dir, &source, &output, "263.100.5");
    request.extend([
        "--embed-content-modules=true".to_owned(),
        "--reserialize-before-content-embedding=true".to_owned(),
        format!("--plugin-descriptor=a.b.xml={}", path_string(&descriptor)),
    ]);
    assert_eq!(run_request(dir, &request), 0);

    let got = read_text(&output);
    assert!(
        got.contains("<description>&lt;b&gt;x&lt;/b&gt;</description>"),
        "the source CDATA was not normalized before embedding:\n{got}"
    );
    assert!(
        got.contains("<module name=\"a.b\"><![CDATA[<idea-plugin package=\"a.b\" />]]></module>"),
        "the embedded descriptor did not keep CDATA:\n{got}"
    );
}

/// `--reserialized-output` adds the descriptor after one more round trip. The plugin classpath record embeds that form
/// (`generatePluginClassPathFromOrderedAssets` of `orderedAssets.kt`). The main output stays as it was.
#[test]
fn a_reserialized_output_is_written_next_to_the_descriptor() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("out").join("plugin.xml");
    let reserialized = dir.join("reserialized").join("plugin.xml");
    let descriptor = dir.join("a.b.xml");
    write_file(
        &source,
        "<idea-plugin><id>a</id><content><module name=\"a.b\"/></content></idea-plugin>",
    );
    write_file(
        &descriptor,
        "<idea-plugin package=\"a.b\"><description><![CDATA[<b>x</b>]]></description></idea-plugin>",
    );

    let mut request = minimal_request(dir, &source, &output, "263.100.5");
    request.extend([
        format!("--reserialized-output={}", path_string(&reserialized)),
        "--embed-content-modules=true".to_owned(),
        format!("--plugin-descriptor=a.b.xml={}", path_string(&descriptor)),
    ]);
    assert_eq!(run_request(dir, &request), 0);

    let got = read_text(&output);
    assert!(
        got.contains("<module name=\"a.b\"><![CDATA[<idea-plugin package=\"a.b\">"),
        "the main output lost its embedded CDATA:\n{got}"
    );
    assert_eq!(
        read_text(&reserialized),
        "<idea-plugin>
  <id>a</id>
  <version>263.100.5</version>
  <idea-version since-build=\"263.100\" until-build=\"263.*\" />
  <content>
    <module name=\"a.b\">&lt;idea-plugin package=&quot;a.b&quot;&gt;
  &lt;description&gt;&amp;lt;b&amp;gt;x&amp;lt;/b&amp;gt;&lt;/description&gt;
&lt;/idea-plugin&gt;</module>
  </content>
</idea-plugin>"
    );
}

/// The request of a release product. The range of a release product is `NEWER_WITH_SAME_BASELINE`.
fn minimal_request(dir: &Path, source: &Path, output: &Path, build_number: &str) -> Vec<String> {
    minimal_request_of(dir, source, output, build_number, "release")
}

/// The request with the application info `testdata/plugin_descriptor/<application_info>.xml`.
fn minimal_request_of(dir: &Path, source: &Path, output: &Path, build_number: &str, application_info_name: &str) -> Vec<String> {
    vec![
        format!("--out={}", path_string(output)),
        "--main-module=intellij.example".to_owned(),
        format!("--source={}", path_string(source)),
        format!("--build-number-file={}", build_number_file(dir, build_number)),
        application_info(application_info_name),
        PINNED_BUILD_DATE.to_owned(),
    ]
}

/// Without `--reserialized-output`, no second file is written. No `.SNAPSHOT`, so the version is the build number.
/// The product is a release, so the range is `NEWER_WITH_SAME_BASELINE`: the number without its last segment, and the
/// baseline with a star.
#[test]
fn a_minimal_request_writes_one_descriptor() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("plugin.out.xml");
    write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");

    assert_eq!(run_request(dir, &minimal_request(dir, &source, &output, "263.100.5")), 0);
    assert_eq!(
        read_text(&output),
        "<idea-plugin>\n  <id>a</id>\n  <version>263.100.5</version>\n  <idea-version since-build=\"263.100\" until-build=\"263.*\" />\n</idea-plugin>"
    );
    let entries = std::fs::read_dir(dir).unwrap().count();
    assert_eq!(
        entries, 4,
        "the source, the build number, the flag file and the output are the only files"
    );
}

/// Every rule passes its options in one flag file, so direct arguments are refused.
#[test]
fn the_request_is_one_flag_file() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("plugin.out.xml");
    write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");

    let flagfile = format!("--flagfile={}", crate::test_support::request_file(dir, &[]));
    for arguments in [
        Vec::new(),
        minimal_request(dir, &source, &output, "263.100.5"),
        vec![flagfile.clone(), flagfile],
        lines(&["--flagfile"]),
    ] {
        assert_eq!(run_arguments(&arguments), 2, "{arguments:?}");
    }
    require_absent(&output);
}

/// A flag file that does not exist fails the request.
#[test]
fn a_missing_flag_file_is_refused() {
    let dir = TempDir::new();
    let flagfile = format!("--flagfile={}", path(dir.path(), "absent.txt"));
    assert_eq!(run_arguments(&[flagfile]), 2);
}

/// `--exact-version` pins both ends to the build number (`CompatibleBuildRange.EXACT` of `PluginXmlPatcher.kt`).
#[test]
fn an_exact_version_pins_both_ends() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("plugin.out.xml");
    write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");

    let mut request = minimal_request_of(dir, &source, &output, "263.100.5", "eap");
    request.extend(lines(&["--exact-version=true"]));
    assert_eq!(run_request(dir, &request), 0);
    let got = read_text(&output);
    assert!(got.contains("since-build=\"263.100.5\" until-build=\"263.100.5\""), "{got}");
}

/// `--compatible-build-range` is the range that the layout states (`DataPluginVersionEvaluator.compatibleBuildRange`).
/// It replaces the EAP fallback, so an EAP build keeps `NEWER_WITH_SAME_BASELINE` when the layout states it.
#[test]
fn a_stated_compatible_build_range_replaces_the_eap_fallback() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("plugin.out.xml");
    write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");

    let mut request = minimal_request_of(dir, &source, &output, "263.100.5", "eap");
    request.extend(lines(&[
        "--version-suffix=-IJ",
        "--compatible-build-range=NEWER_WITH_SAME_BASELINE",
    ]));
    assert_eq!(run_request(dir, &request), 0);
    let got = read_text(&output);
    assert!(got.contains("<version>263.100.5-IJ</version>"), "{got}");
    assert!(got.contains("since-build=\"263.100\" until-build=\"263.*\""), "{got}");
}

/// An EAP application info without a stated range restricts the range to the same release.
#[test]
fn an_eap_build_restricts_the_range_to_the_same_release() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("plugin.out.xml");
    write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");

    let request = minimal_request_of(dir, &source, &output, "263.100.5", "eap");
    assert_eq!(run_request(dir, &request), 0);
    let got = read_text(&output);
    assert!(got.contains("since-build=\"263.100\" until-build=\"263.100.*\""), "{got}");
}

/// The layouts state one range. The two other names of `CompatibleBuildRange` reach the stamps through the two flags,
/// so a stated one is refused like an unknown one.
#[test]
fn a_compatible_build_range_other_than_the_stated_one_is_refused() {
    for range in ["EXACT", "RESTRICTED_TO_SAME_RELEASE", "SAME_RELEASE"] {
        let dir = TempDir::new();
        let dir = dir.path();
        let source = dir.join("plugin.xml");
        let output = dir.join("plugin.out.xml");
        write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");

        let mut request = minimal_request(dir, &source, &output, "263.100.5");
        request.push(format!("--compatible-build-range={range}"));
        assert_eq!(run_request(dir, &request), 2, "{range}");
        require_absent(&output);
    }
}

/// An option that the parser does not know fails the run. That keeps the rule and this binary on one spelling: a rule
/// that grows an option reaches this parser or fails here. The list also holds `--platform-descriptor`, `--plugin-module`
/// and `--platform-module`, which no rule states.
#[test]
fn an_unknown_option_is_refused() {
    let dir = TempDir::new();
    let dir = dir.path();
    for unknown in [
        "--not-an-option=1",
        "--platform-descriptor=META-INF/a.xml=a.xml",
        "--plugin-module=intellij.example",
        "--platform-module=intellij.platform.core",
        "--",
        "-x",
        "-x=1",
        "positional",
        "positional=1",
        "",
    ] {
        let source = dir.join("plugin.xml");
        let output = dir.join("plugin.out.xml");
        write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");
        let mut request = minimal_request(dir, &source, &output, "263.100.5");
        request.push(unknown.to_owned());
        assert_eq!(run_request(dir, &request), 2, "{unknown:?}");
        require_absent(&output);
    }
}

/// A scalar option takes one value, as `--name=value`, and the rules state it once.
#[test]
fn a_valueless_or_repeated_option_is_refused() {
    let dir = TempDir::new();
    let dir = dir.path();
    for request in [
        &["--out"][..],
        &["--exact-version"][..],
        &["--application-info-source"][..],
        &["--refused-content-module"][..],
        &["--out=a", "--out=b"][..],
        &["--exact-version=true", "--exact-version=false"][..],
        &["--build-date-seconds=1", "--build-date-seconds=2"][..],
        &["--source=a.xml", "--source=b.xml"][..],
    ] {
        let source = dir.join("plugin.xml");
        let output = dir.join("plugin.out.xml");
        write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");
        let mut lines = minimal_request(dir, &source, &output, "263.100.5");
        let names: Vec<&str> = request.iter().map(|line| line.split('=').next().unwrap()).collect();
        lines.retain(|line| !names.contains(&line.split('=').next().unwrap()));
        lines.extend(request.iter().map(|line| (*line).to_owned()));
        assert_eq!(run_request(dir, &lines), 2, "{request:?}");
        require_absent(&output);
    }
}

/// A boolean is strict, the way Kotlin's `toBooleanStrict` is.
#[test]
fn a_loose_boolean_is_refused() {
    let dir = TempDir::new();
    assert_eq!(run_request(dir.path(), &lines(&["--exact-version=yes"])), 2);
}

#[test]
fn a_missing_required_option_is_refused() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = path_string(&dir.join("plugin.xml"));
    write_file(dir.join("plugin.xml"), "<idea-plugin/>");
    let source_option = format!("--source={source}");
    let info = application_info("release");
    let complete = [
        "--out=o",
        "--main-module=m",
        &source_option,
        "--build-number-file=b",
        &info,
        PINNED_BUILD_DATE,
    ];
    for (index, name) in complete.iter().enumerate() {
        let request: Vec<&str> = complete
            .iter()
            .enumerate()
            .filter(|(other, _)| *other != index)
            .map(|(_, line)| *line)
            .collect();
        assert_eq!(run_request(dir, &lines(&request)), 2, "no {name}");
    }
}

/// A descriptor pair must have the form `<load path>=<file>`, and a load path takes one declaration.
#[test]
fn a_malformed_descriptor_pair_is_refused() {
    let dir = TempDir::new();
    let dir = dir.path();
    for request in [
        &["--plugin-descriptor=no-separator"][..],
        &["--plugin-descriptor==only-a-file"][..],
        &["--plugin-descriptor=a.xml=one.xml", "--plugin-descriptor=a.xml=two.xml"][..],
    ] {
        assert_eq!(run_request(dir, &lines(request)), 2, "{request:?}");
    }
}

/// A second source is refused, in both of its spellings.
#[test]
fn a_second_source_is_refused() {
    let dir = TempDir::new();
    let dir = dir.path();
    for request in [
        &["--source=a.xml", "--source-in-jar=META-INF/plugin.xml=b.jar"][..],
        &["--source-in-jar=META-INF/plugin.xml"][..],
    ] {
        assert_eq!(run_request(dir, &lines(request)), 2, "{request:?}");
    }
}

/// `--source-in-jar` reads the descriptor from an entry of a jar.
#[test]
fn a_source_in_a_jar_is_read() {
    let dir = TempDir::new();
    let dir = dir.path();
    let jar = descriptor_jar(
        dir,
        "plugin.jar",
        &[("META-INF/plugin.xml", "<idea-plugin><id>from.jar</id></idea-plugin>")],
    );
    let output = dir.join("plugin.out.xml");
    let request = vec![
        format!("--out={}", path_string(&output)),
        "--main-module=intellij.example".to_owned(),
        format!("--source-in-jar=META-INF/plugin.xml={jar}"),
        format!("--build-number-file={}", build_number_file(dir, "263.100.5")),
        application_info("release"),
        PINNED_BUILD_DATE.to_owned(),
    ];
    assert_eq!(run_request(dir, &request), 0);
    assert!(read_text(&output).contains("<id>from.jar</id>"));
}

/// `--plugin-descriptor-in-jar` seeds the cache from the first jar that has the entry. A load path that a file and a
/// jar both answer fails, because the rule refuses it too.
#[test]
fn a_plugin_descriptor_in_a_jar_is_read() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    write_file(
        &source,
        "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"/META-INF/extra.xml\"/></idea-plugin>",
    );
    let first = descriptor_jar(dir, "first.jar", &[("unrelated.xml", "<idea-plugin/>")]);
    let second = descriptor_jar(
        dir,
        "second.jar",
        &[("META-INF/extra.xml", "<idea-plugin><from-jar/></idea-plugin>")],
    );
    let output = dir.join("plugin.out.xml");
    let mut request = minimal_request(dir, &source, &output, "263.100.5");
    request.push(format!("--plugin-descriptor-in-jar=META-INF/extra.xml={first}"));
    request.push(format!("--plugin-descriptor-in-jar=META-INF/extra.xml={second}"));
    assert_eq!(run_request(dir, &request), 0);
    assert!(read_text(&output).contains("<from-jar />"), "{}", read_text(&output));

    std::fs::remove_file(&output).unwrap();
    write_file(dir.join("extra.xml"), "<idea-plugin><from-file/></idea-plugin>");
    request.push(format!("--plugin-descriptor=META-INF/extra.xml={}", path(dir, "extra.xml")));
    assert_eq!(run_request(dir, &request), 1);
    require_absent(&output);
}

/// A marker row patches the raw text before the round trip.
#[test]
fn a_marker_row_patches_the_raw_text() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("plugin.out.xml");
    write_file(
        &source,
        "<idea-plugin><id>a</id><dependencies><!-- OS/ARCH-DEPENDENCY-PLACEHOLDER --></dependencies></idea-plugin>",
    );
    let mut request = minimal_request(dir, &source, &output, "263.100.5");
    request.push("--marker=os-arch:linux:arm64".to_owned());
    assert_eq!(run_request(dir, &request), 0);
    assert!(
        read_text(&output).contains(
            "  <dependencies>\n    <plugin id=\"com.intellij.modules.os.linux\" />\n    <plugin id=\"com.intellij.modules.arch.arm64\" />\n  </dependencies>"
        ),
        "{}",
        read_text(&output)
    );
}

/// A failure of any stage must leave no output behind. A half-patched descriptor that looks written is worse than a
/// failed action. A wrong descriptor fails at class-load time inside the IDE, where nothing here can see it.
#[test]
fn a_failed_stage_writes_no_output() {
    for (name, descriptor) in [
        (
            "an unresolvable include",
            "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"absent.xml\"/></idea-plugin>",
        ),
        (
            "an undeclared content module",
            "<idea-plugin><content><module name=\"a.b\"/></content></idea-plugin>",
        ),
        ("a malformed descriptor", "<idea-plugin><id>a</id>"),
        ("an unsupported construct", "<!DOCTYPE idea-plugin><idea-plugin/>"),
    ] {
        let dir = TempDir::new();
        let dir = dir.path();
        let source = dir.join("plugin.xml");
        let output = dir.join("plugin.out.xml");
        write_file(&source, descriptor);
        assert_eq!(run_request(dir, &minimal_request(dir, &source, &output, "263.100.5")), 1, "{name}");
        require_absent(&output);
    }
}

/// A build number that the semantic-version check of the platform refuses must fail here too, and not reach a jar
/// (`computePluginBuildNumber` of `SnapshotBuildNumber.kt`).
#[test]
fn a_build_number_that_is_not_semantic_is_refused() {
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    write_file(&source, "<idea-plugin/>");
    assert_eq!(run_request(dir, &minimal_request(dir, &source, &dir.join("o.xml"), "263.x.1")), 1);
}

#[test]
fn an_unreadable_source_fails() {
    let dir = TempDir::new();
    let dir = dir.path();
    let request = minimal_request(dir, &dir.join("absent.xml"), &dir.join("o.xml"), "263.100.5");
    assert_eq!(run_request(dir, &request), 1);
}

#[test]
fn mode_selection() {
    for (values, want) in [
        (&[][..], None),
        (&["--out=o", "--source=s"][..], None),
        (&["--out=o", "--embedded-product"][..], Some(Mode::EmbeddedProduct)),
        (&["--product-descriptor", "--out=o"][..], Some(Mode::ProductDescriptor)),
        (&["--application-info", "--out=o"][..], Some(Mode::ApplicationInfo)),
        (&["--out=o", "--stamp-application-info"][..], Some(Mode::StampApplicationInfo)),
        // A value option of the frontend mode is not the mode of a product.
        (
            &["--application-info", "--product-application-info=p.xml"][..],
            Some(Mode::ApplicationInfo),
        ),
    ] {
        let got = take_mode(&mut option_lines(values)).unwrap_or_else(|error| panic!("{values:?}: {error:#}"));
        assert_eq!(got, want, "{values:?}");
    }
}

#[test]
fn invalid_modes_are_refused() {
    for modes in [
        &["--embedded-product", "--embedded-product"][..],
        &["--application-info", "--application-info"][..],
        &["--embedded-product", "--application-info"][..],
        &["--application-info", "--embedded-product"][..],
        &["--embedded-product", "--product-descriptor"][..],
        &["--application-info", "--stamp-application-info"][..],
        &["--product-descriptor=true"][..],
        &["--stamp-application-info="][..],
        &["--embedded-product=true"][..],
        &["--embedded-product="][..],
        &["--application-info=true"][..],
        &["--application-info="][..],
    ] {
        let dir = TempDir::new();
        let dir = dir.path();
        let output = dir.join("output.xml");
        let mut request = vec![format!("--out={}", path_string(&output)), "--source=unused.xml".to_owned()];
        request.extend(lines(modes));
        let values: Vec<&str> = request.iter().map(String::as_str).collect();
        assert!(take_mode(&mut option_lines(&values)).is_err(), "{modes:?}");
        assert_eq!(run_request(dir, &request), 2, "{modes:?}");
        require_absent(&output);
    }
}

/// The stamps of a retained `product-descriptor` come from the application info: the EAP flag, the release date and the
/// release version. A frontend takes them from its host, and a language server applies its markers first.
#[test]
fn the_stamps_come_from_the_application_info() {
    let host = format!("--host-application-info-source={}", testdata("plugin_descriptor/release.xml"));
    let cases: [(&str, &[&str], &str, &str); 4] = [
        (
            "eap",
            &[],
            r#"<product-descriptor code="X" release-date="20260101" release-version="2026300" eap="true" />"#,
            r#"since-build="263.100" until-build="263.100.*""#,
        ),
        (
            "release",
            &[],
            r#"<product-descriptor code="X" release-date="20261201" release-version="2026200" />"#,
            r#"since-build="263.100" until-build="263.*""#,
        ),
        (
            "client",
            &[&host],
            r#"<product-descriptor code="X" release-date="20261201" release-version="2026200" />"#,
            r#"since-build="263.100" until-build="263.*""#,
        ),
        (
            "server",
            &[
                "--replacement=BUNDLE_EAP= eap=\"true\"",
                "--replacement=RELEASE_DATE=",
                "--replacement=BUNDLE_NAME=server",
            ],
            r#"<product-descriptor code="X" release-date="20260101" release-version="2026300" eap="true" />"#,
            r#"since-build="263.100" until-build="263.100.*""#,
        ),
    ];
    for (name, options, descriptor, range) in cases {
        let dir = TempDir::new();
        let dir = dir.path();
        let source = dir.join("plugin.xml");
        let output = dir.join("plugin.out.xml");
        write_file(
            &source,
            r#"<idea-plugin><id>a</id><product-descriptor code="X" release-date="__DATE__" release-version="__VERSION__"/></idea-plugin>"#,
        );
        let mut request = minimal_request_of(dir, &source, &output, "263.100.5", name);
        request.push("--retain-product-descriptor=true".to_owned());
        request.extend(lines(options));
        assert_eq!(run_request(dir, &request), 0, "{name}");
        let got = read_text(&output);
        assert!(got.contains(descriptor), "{name}:\n{got}");
        assert!(got.contains(range), "{name}:\n{got}");
    }
}

/// The rule states the application info and no stamp value, so the three old options are unknown.
#[test]
fn the_old_stamp_options_are_refused() {
    for option in ["--eap=true", "--release-date=20260101", "--release-version=2026300"] {
        let dir = TempDir::new();
        let dir = dir.path();
        let source = dir.join("plugin.xml");
        let output = dir.join("plugin.out.xml");
        write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");
        let mut request = minimal_request(dir, &source, &output, "263.100.5");
        request.push(option.to_owned());
        assert_eq!(run_request(dir, &request), 2, "{option}");
        require_absent(&output);
    }
}

/// A malformed application info option is a request that the rule cannot state. An application info that the reader
/// refuses is a request that the inputs cannot satisfy.
#[test]
fn a_bad_application_info_is_refused() {
    for (options, code) in [
        (&["--build-date-seconds=soon"][..], 2),
        (&["--replacement=VALUE"][..], 2),
        (&["--replacement==value"][..], 2),
        (&["--replacement=A=1", "--replacement=A=2"][..], 2),
        (&["--host-application-info-source="][..], 2),
        (&["--application-info-source=absent.xml"][..], 1),
    ] {
        let dir = TempDir::new();
        let dir = dir.path();
        let source = dir.join("plugin.xml");
        let output = dir.join("plugin.out.xml");
        write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");
        let mut request = minimal_request(dir, &source, &output, "263.100.5");
        let replaced: Vec<&str> = options.iter().map(|option| option.split('=').next().unwrap()).collect();
        request.retain(|line| {
            !replaced
                .iter()
                .any(|name| *name != "--replacement" && line.starts_with(&format!("{name}=")))
        });
        request.extend(lines(options));
        assert_eq!(run_request(dir, &request), code, "{options:?}");
        require_absent(&output);
    }

    // A release product without a release date fails at the reader, and the error names the file.
    let dir = TempDir::new();
    let dir = dir.path();
    let source = dir.join("plugin.xml");
    let output = dir.join("plugin.out.xml");
    write_file(&source, "<idea-plugin><id>a</id></idea-plugin>");
    let info = dir.join("dateless.xml");
    let release = read_text(Path::new(&testdata("plugin_descriptor/release.xml")));
    write_file(&info, release.replace(r#" majorReleaseDate="20261201""#, ""));
    let mut request = minimal_request(dir, &source, &output, "263.100.5");
    request.retain(|line| !line.starts_with("--application-info-source="));
    request.push(format!("--application-info-source={}", path_string(&info)));
    assert_eq!(run_request(dir, &request), 1);
    require_absent(&output);
}
