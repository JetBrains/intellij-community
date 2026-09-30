// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! One case per rule of `ApplicationInfoPropertiesImpl.kt`. Each test names the Kotlin line that it ports.

use crate::{ApplicationInfo, format_major_release_date, format_version, linux_frame_class, shorten_company_name};

/// `DEV_DIST_PINNED_BUILD_DATE_IN_SECONDS`: 2026-01-01T00:00:00Z.
const PINNED: i64 = 1_767_225_600;

/// An application info with this `version` element, this `names` element and this `build` element, and a fixed
/// company and icon.
fn xml(version: &str, names: &str, build: &str) -> String {
    format!(
        r#"<component xmlns="http://jetbrains.org/intellij/schema/application-info">
  <version {version}/>
  <company name="JetBrains s.r.o." url="https://www.jetbrains.com/"/>
  <build number="IU-__BUILD__" {build}/>
  <icon svg="/idea.svg" svg-small="/idea_16.svg"/>
  <icon-eap svg="/idea_eap.svg"/>
  <names {names}/>
</component>"#
    )
}

fn read(content: &str) -> ApplicationInfo {
    ApplicationInfo::read(content, "test.xml", PINNED).unwrap_or_else(|error| panic!("{error:#}\n{content}"))
}

fn read_error(content: &str) -> String {
    match ApplicationInfo::read(content, "test.xml", PINNED) {
        Ok(info) => panic!("accepted {info:?}:\n{content}"),
        Err(error) => format!("{error:#}"),
    }
}

const NAMES: &str = r#"product="IDEA" fullname="IntelliJ IDEA" script="idea""#;
const RELEASE: &str = r#"majorReleaseDate="20260915""#;

#[test]
fn a_plain_eap_product() {
    let info = read(&xml(r#"major="2026" minor="3" eap="true""#, NAMES, ""));
    assert_eq!(
        info,
        ApplicationInfo {
            full_product_name: "IntelliJ IDEA".to_owned(),
            edition: None,
            version: "2026.3".to_owned(),
            major_version: "2026".to_owned(),
            minor_version_main_part: "3".to_owned(),
            version_suffix: Some("EAP".to_owned()),
            is_eap: true,
            svg_icon: Some("/idea_eap.svg".to_owned()),
            short_company_name: "JetBrains".to_owned(),
            major_release_date: "20260101".to_owned(),
        }
    );
}

/// Line 124: the full name falls back to the product name.
#[test]
fn the_full_name_falls_back_to_the_product_name() {
    let info = read(&xml(r#"major="2026" minor="3""#, r#"product="Gateway""#, RELEASE));
    assert_eq!(info.full_product_name, "Gateway");
    let error = read_error(&xml(r#"major="2026""#, r#"fullname="Gateway""#, RELEASE));
    assert_eq!(error, "the names element has no product attribute: test.xml");
}

/// Lines 61 and 125: an empty edition is no edition, and a stated one follows the name after a space.
#[test]
fn the_edition_joins_the_name() {
    let empty = read(&xml(r#"major="2026""#, &format!(r#"{NAMES} edition="""#), RELEASE));
    assert_eq!(
        (empty.edition.as_deref(), empty.product_name_with_edition().as_str()),
        (None, "IntelliJ IDEA")
    );
    let stated = read(&xml(r#"major="2026""#, &format!(r#"{NAMES} edition="Ultimate Edition""#), RELEASE));
    assert_eq!(stated.product_name_with_edition(), "IntelliJ IDEA Ultimate Edition");
}

/// Lines 58 and 72-76: the parts default to `0`, and the pattern defaults to `{0}.{1}`.
#[test]
fn the_version_follows_the_pattern() {
    assert_eq!(read(&xml(r#"major="2026""#, NAMES, RELEASE)).version, "2026.0");
    let full = read(&xml(r#"major="2026" minor="3" micro="1" full="{0}.{1}.{2} ({3})""#, NAMES, RELEASE));
    assert_eq!(full.version, "2026.3.1 (0)");
    assert_eq!(
        read(&xml(r#"major="2026" minor="" full="{0}-{1}""#, NAMES, RELEASE)).version,
        "2026-"
    );
    let error = read_error(&xml(r#"minor="3""#, NAMES, RELEASE));
    assert_eq!(error, "the version element has no major attribute: test.xml");
}

/// Lines 54-55 and 83: the licensing version joins the major version, the minor version up to its first dot, and `00`.
#[test]
fn the_release_version_for_licensing() {
    for (version, expected) in [
        (r#"major="2026" minor="3""#, "2026300"),
        (r#"major="2026" minor="2.1""#, "2026200"),
        (r#"major="2026""#, "2026000"),
        (r#"major="2026" minor="""#, "202600"),
    ] {
        assert_eq!(
            read(&xml(version, NAMES, RELEASE)).release_version_for_licensing(),
            expected,
            "{version}"
        );
    }
}

/// Line 58: the port of `MessageFormat` refuses what it does not port.
#[test]
fn the_version_pattern_refuses_other_elements() {
    assert_eq!(format_version("v{3}{2}{1}{0}", ["a", "b", "c", "d"]).unwrap(), "vdcba");
    for pattern in ["{4}", "{0,number}", "'{0}'", "it's", "{", "}", "{0", "{{0}}", "{ü}"] {
        let error = format_version(pattern, ["1", "2", "3", "4"]).unwrap_err();
        assert_eq!(
            error.to_string(),
            format!("the version pattern {pattern:?} is not literal text with the elements {{0}} to {{3}}"),
        );
    }
    let error = read_error(&xml(r#"major="2026" full="{0}'{1}'""#, NAMES, RELEASE));
    assert!(error.starts_with("test.xml: the version pattern"), "{error}");
}

/// Lines 77-82: `eap` is a case-insensitive `true`, and an EAP product without a suffix has the suffix `EAP`.
#[test]
fn the_suffix_follows_the_eap_flag() {
    for (version, is_eap, suffix) in [
        (r#"major="2026" eap="TRUE""#, true, Some("EAP")),
        (r#"major="2026" eap="yes""#, false, None),
        (r#"major="2026" eap="true" suffix="EAP 6 D""#, true, Some("EAP 6 D")),
        (r#"major="2026" eap="false" suffix="RC""#, false, Some("RC")),
        (r#"major="2026" eap="true" suffix="""#, true, Some("")),
    ] {
        let info = read(&xml(version, NAMES, RELEASE));
        assert_eq!((info.is_eap, info.version_suffix.as_deref()), (is_eap, suffix), "{version}");
    }
}

/// Lines 101-123: only an EAP product can omit the release date, and it then takes the pinned build date.
#[test]
fn the_release_date_of_a_release_is_required() {
    for build in ["", r#"majorReleaseDate="""#, r#"majorReleaseDate="__MAJOR_RELEASE_DATE__""#] {
        let error = read_error(&xml(r#"major="2026" eap="false""#, NAMES, build));
        assert_eq!(error, "majorReleaseDate may be omitted only for EAP: test.xml", "{build}");
        let eap = read(&xml(r#"major="2026" eap="true""#, NAMES, build));
        assert_eq!(eap.major_release_date, "20260101", "{build}");
    }
    let stated = read(&xml(r#"major="2026" eap="true""#, NAMES, RELEASE));
    assert_eq!(stated.major_release_date, "20260915");
}

/// Lines 304-319: `yyyyMMdd` stays, `yyyyMMddHHmm` loses the time, and the build date is UTC.
#[test]
fn the_release_date_formats() {
    for (raw, seconds, expected) in [
        (None, PINNED, "20260101"),
        (None, PINNED - 1, "20251231"),
        (None, 0, "19700101"),
        (None, 951_782_400, "20000229"),
        (Some("__BUILD_DATE__"), PINNED, "20260101"),
        (Some("20240229"), 0, "20240229"),
        (Some("202609151200"), 0, "20260915"),
        (Some("202612312359"), 0, "20261231"),
    ] {
        assert_eq!(format_major_release_date(raw, seconds).unwrap(), expected, "{raw:?} {seconds}");
    }
    for raw in [
        "20260229",
        "2026091",
        "202609150",
        "2026-09-15",
        "20261301",
        "20260431",
        "202609152400",
        "202609151260",
        "２０２６０９１５",
    ] {
        let error = format_major_release_date(Some(raw), 0).unwrap_err();
        assert_eq!(
            error.to_string(),
            format!("the major release date {raw:?} is neither yyyyMMdd nor yyyyMMddHHmm")
        );
    }
}

/// Lines 128-130 and 291: the short name wins, and otherwise the legal suffixes go.
#[test]
fn the_vendor_is_the_short_company_name() {
    let company = |company: &str| {
        xml(r#"major="2026""#, NAMES, RELEASE).replace(r#"<company name="JetBrains s.r.o." url="https://www.jetbrains.com/"/>"#, company)
    };
    assert_eq!(
        read(&company(r#"<company name="Acme s.r.o." shortName="ACME"/>"#)).short_company_name,
        "ACME"
    );
    assert_eq!(read(&company(r#"<company name="Acme Inc."/>"#)).short_company_name, "Acme");
    assert_eq!(read_error(&company("")), "the application info has no company element: test.xml");
    assert_eq!(
        read_error(&company("<company/>")),
        "the company element has no name attribute: test.xml"
    );
    for (name, expected) in [
        ("JetBrains s.r.o.", "JetBrains"),
        ("JetBrains Inc.", "JetBrains"),
        ("JetBrains Inc. s.r.o.", "JetBrains"),
        ("JetBrains s.r.o. Inc.", "JetBrains s.r.o."),
        ("JetBrains", "JetBrains"),
    ] {
        assert_eq!(shorten_company_name(name), expected, "{name}");
    }
}

/// Lines 131-132: an EAP product prefers the EAP icon, even an empty one, and an empty plain icon is no icon.
#[test]
fn the_icon_follows_the_eap_flag() {
    let icons = |version: &str, icons: &str| {
        let content = xml(version, NAMES, RELEASE).replace(
            r#"<icon svg="/idea.svg" svg-small="/idea_16.svg"/>
  <icon-eap svg="/idea_eap.svg"/>"#,
            icons,
        );
        read(&content).svg_icon
    };
    let both = r#"<icon svg="/a.svg"/><icon-eap svg="/b.svg"/>"#;
    assert_eq!(icons(r#"major="2026" eap="true""#, both).as_deref(), Some("/b.svg"));
    assert_eq!(icons(r#"major="2026""#, both).as_deref(), Some("/a.svg"));
    assert_eq!(
        icons(r#"major="2026" eap="true""#, r#"<icon svg="/a.svg"/>"#).as_deref(),
        Some("/a.svg")
    );
    assert_eq!(
        icons(r#"major="2026" eap="true""#, r#"<icon svg="/a.svg"/><icon-eap svg=""/>"#).as_deref(),
        Some("")
    );
    assert_eq!(icons(r#"major="2026""#, r#"<icon svg=""/><icon-eap svg="/b.svg"/>"#), None);
    assert_eq!(icons(r#"major="2026""#, ""), None);
}

/// `readXmlAsModel` keys the elements and the attributes by the local name, in any namespace.
#[test]
fn the_reader_ignores_namespaces() {
    let content = r#"<a:component xmlns:a="http://jetbrains.org/intellij/schema/application-info" xmlns:o="urn:other">
  <a:version major="2026" o:minor="7"/>
  <o:company name="Acme"/>
  <build majorReleaseDate="20260915"/>
  <names product="P"/>
  <names product="Second"/>
</a:component>"#;
    let info = read(content);
    assert_eq!((info.version.as_str(), info.full_product_name.as_str()), ("2026.7", "P"));
    let error = read_error(&content.replace(r#"o:minor="7""#, r#"o:minor="7" minor="8""#));
    assert_eq!(error, "the version element has two attributes with the local name minor: test.xml");
    assert!(read_error("<component").starts_with("test.xml: "), "an XML error names the file");
}

/// `JetBrainsClientPropertiesForLaunchers.kt:118-165`: the host states the names, the version and the release date.
/// A value that the host does not state falls back to the frontend, as `override ?: xml` does on lines 72-124.
#[test]
fn a_frontend_takes_the_facts_of_its_host() {
    let client = xml(
        r#"major="2026" minor="3" micro="9" eap="true""#,
        r#"product="JetBrainsClient" fullname="JetBrains Client" edition="Client" motto="M""#,
        "",
    );
    let host = r#"<component xmlns="http://jetbrains.org/intellij/schema/application-info">
  <names product="Rider" fullname="JetBrains Rider"/>
  <version major="2026" minor="4" suffix="EAP 6 D" full="{0}.{1}.{2}"/>
  <build majorReleaseDate="20261001"/>
</component>"#;
    let info = ApplicationInfo::read_frontend(&client, "client.xml", host, "host.xml", PINNED).unwrap();
    assert_eq!(
        info,
        ApplicationInfo {
            full_product_name: "JetBrains Rider".to_owned(),
            edition: Some("Client".to_owned()),
            version: "2026.4.9".to_owned(),
            major_version: "2026".to_owned(),
            minor_version_main_part: "4".to_owned(),
            version_suffix: Some("EAP 6 D".to_owned()),
            is_eap: true,
            svg_icon: Some("/idea_eap.svg".to_owned()),
            short_company_name: "JetBrains".to_owned(),
            major_release_date: "20261001".to_owned(),
        }
    );
    let product_only = host.replace(r#" fullname="JetBrains Rider""#, "");
    let info = ApplicationInfo::read_frontend(&client, "client.xml", &product_only, "host.xml", PINNED).unwrap();
    assert_eq!(info.full_product_name, "Rider");

    let nameless = host.replace(r#"product="Rider" fullname="JetBrains Rider""#, "");
    let error = ApplicationInfo::read_frontend(&client, "client.xml", &nameless, "host.xml", PINNED).unwrap_err();
    assert_eq!(error.to_string(), "the product application info has no product name: host.xml");
    let unqualified = host.replace(" xmlns=\"http://jetbrains.org/intellij/schema/application-info\"", "");
    let error = ApplicationInfo::read_frontend(&client, "client.xml", &unqualified, "host.xml", PINNED).unwrap_err();
    assert_eq!(error.to_string(), "the application info has no unique names element: host.xml");
}

/// `linuxFrameClass` of `BuildTasksImpl.kt:970-981`.
#[test]
fn the_linux_frame_class() {
    for (name, expected) in [
        ("IntelliJ IDEA", "jetbrains-idea"),
        ("IntelliJ IDEA Community Edition", "jetbrains-idea-ce"),
        ("IntelliJ IDEA Ultimate Edition", "jetbrains-idea"),
        ("PyCharm Professional Edition", "jetbrains-pycharm"),
        ("Android Studio", "jetbrains-studio"),
        ("JetBrains Rider", "jetbrains-rider"),
        ("JetBrains Client", "jetbrains-client"),
        ("Rider", "jetbrains-rider"),
        ("Server \"Q\"", "jetbrains-server-\"q\""),
    ] {
        assert_eq!(linux_frame_class(name), expected, "{name}");
    }
}
