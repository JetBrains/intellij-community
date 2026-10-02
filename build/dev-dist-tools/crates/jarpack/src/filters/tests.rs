// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The two filters are shared with the Kotlin `JarPackager` by a port, not by a call. So a disagreement shows as a jar
//! that differs by the packer that wrote it. The names below are the ones a pattern decides, and not a literal, which is
//! where a port drifts.

use std::collections::BTreeSet;

use crate::filters::{is_ignored_library_name, is_legal_notice, is_versioned_module_info};
use crate::{INDEX_FILE_NAME, library_name_filter, module_output_name_filter};

#[test]
fn module_output_name_filter_cases() {
    for (name, want) in [
        ("com/example/Service.class", true),
        ("messages/Bundle.properties", true),
        (".unmodified", false),
        (".hash", false),
        ("classpath.index", false),
        ("module-info.class", false),
        ("com/example/module-info.class", true),
    ] {
        assert_eq!(module_output_name_filter(name), want, "module_output_name_filter({name:?})");
    }
}

#[test]
fn library_name_filter_cases() {
    for (name, want) in [
        ("org/thirdparty/Api.class", true),
        ("META-INF/services/org.thirdparty.Spi", true),
        ("META-INF/versions/11/org/thirdparty/A.class", true),
        ("META-INF/versions/9/module-info.class", false),
        ("META-INF/versions/17/module-info.class", false),
        ("META-INF/versions/module-info.class", true),
        ("META-INF/versions/9/x/module-info.class", true),
        ("module-info.class", false),
        ("org/thirdparty/Api.kotlin_metadata", false),
        ("LICENSE", false),
        ("license", false),
        ("META-INF/COPYING.md", false),
        ("licenses/apache.txt", false),
        ("native-image/config.json", false),
        ("META-INF/LICENSE-apache", false),
        ("META-INF/SIGNER.SF", false),
        ("META-INF/SIGNER.RSA", false),
        ("META-INF/SIGNER.DSA", false),
        ("org/thirdparty/SIGNER.SF", true),
        ("org/xml/sax/InputSource.class", false),
        ("kotlinx/coroutines/repackaged/A.class", false),
        ("META-INF/io.netty.versions.properties", false),
        ("pom.xml", false),
    ] {
        assert_eq!(library_name_filter(name), want, "library_name_filter({name:?})");
    }
}

/// `distpath` refuses the name of the generated index as a source entry. It keeps its own copy of the name, and this
/// test pins that the two agree.
#[test]
fn a_source_entry_cannot_have_the_name_of_the_index() {
    let error = distpath::validate_entry_name(INDEX_FILE_NAME).unwrap_err();
    assert_eq!(format!("{error:#}"), format!("unsafe entry name {INDEX_FILE_NAME:?}"));
}

/// The name set that the filter built per process before the match and the legal-file rule. It is the set of
/// `getIgnoredNames` in `zip/src/librarySourcesFilter.kt`, built the same way.
fn original_ignored_names() -> BTreeSet<String> {
    let mut set: BTreeSet<String> = [
        ".hash",
        "classpath.index",
        ".gitattributes",
        "pom.xml",
        "about.html",
        "module-info.class",
        "META-INF/versions/9/kotlin/reflect/jvm/internal/impl/serialization/deserialization/builtins/BuiltInsResourceLoader.class",
        "META-INF/versions/9/org/apache/xmlbeans/impl/tool/MavenPluginResolver.class",
        "META-INF/services/javax.xml.parsers.SAXParserFactory",
        "META-INF/services/javax.xml.stream.XMLEventFactory",
        "META-INF/services/javax.xml.parsers.DocumentBuilderFactory",
        "META-INF/services/javax.xml.datatype.DatatypeFactory",
        "META-INF/services/com.fasterxml.jackson.core.ObjectCodec",
        "META-INF/services/com.fasterxml.jackson.core.JsonFactory",
        "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration",
        "META-INF/io.netty.versions.properties",
        "com/sun/jna/aix-ppc/libjnidispatch.a",
        "com/sun/jna/aix-ppc64/libjnidispatch.a",
        "META-INF/sisu/javax.inject.Named",
        "OSGI-INF/l10n/bundle.properties",
        "META-INF/groovy-release-info.properties",
        "native-image",
        "native",
        "licenses",
        "META-INF/LGPL2.1",
        "META-INF/AL2.0",
        ".gitkeep",
        INDEX_FILE_NAME,
        "kotlinx/coroutines/debug/ByteBuddyDynamicAttach.class",
        "kotlin/coroutines/jvm/internal/DebugProbesKt.class",
        "META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider",
    ]
    .iter()
    .map(ToString::to_string)
    .collect();
    for original in [
        "NOTICE",
        "README",
        "LICENSE",
        "DEPENDENCIES",
        "CHANGES",
        "THIRD_PARTY_LICENSES",
        "COPYING",
    ] {
        for name in [original.to_string(), original.to_lowercase()] {
            for candidate in [
                name.clone(),
                format!("{name}.txt"),
                format!("{name}.md"),
                format!("META-INF/{name}"),
                format!("META-INF/{name}.txt"),
                format!("META-INF/{name}.md"),
            ] {
                set.insert(candidate);
            }
        }
    }
    set
}

#[test]
fn the_ignored_names_are_the_set_of_the_original() {
    let original = original_ignored_names();
    assert_eq!(original.len(), 115);
    let ignored = |name: &str| is_ignored_library_name(name) || is_legal_notice(name);
    for name in &original {
        assert!(ignored(name), "{name:?} is in the original set");
        assert!(!library_name_filter(name), "{name:?} passes the library filter");
    }
    // Every name of the set with one change, and the near misses of the legal-file rule. Each answer must be the answer
    // of the set.
    let mut near_misses: Vec<String> = [
        "License",
        "LICENSE.html",
        "meta-inf/LICENSE",
        "Meta-Inf/license.md",
        "META-INF/META-INF/LICENSE",
        "META-INF/",
        "LICENSE.txt.md",
        "LICENSE.md.txt",
        "LICENSE.TXT",
        "license.MD",
        "Third_Party_Licenses",
        "licenses/LICENSE",
        "x/LICENSE",
        ".txt",
        ".md",
        "",
    ]
    .iter()
    .map(ToString::to_string)
    .collect();
    for name in &original {
        near_misses.push(format!("{name}x"));
        near_misses.push(format!("x{name}"));
        near_misses.push(format!("/{name}"));
        near_misses.push(format!("{name}/"));
        near_misses.push(name.to_uppercase());
        near_misses.push(name.to_lowercase());
        near_misses.push(
            name.strip_suffix(".txt")
                .or_else(|| name.strip_suffix(".md"))
                .unwrap_or(name)
                .to_owned(),
        );
    }
    for name in &near_misses {
        assert_eq!(ignored(name), original.contains(name), "{name:?}");
    }
}

#[test]
fn versioned_module_info_is_a_whole_name() {
    for (name, want) in [
        ("META-INF/versions/9/module-info.class", true),
        ("META-INF/versions/0123/module-info.class", true),
        ("META-INF/versions//module-info.class", false),
        ("META-INF/versions/module-info.class", false),
        ("META-INF/versions/9a/module-info.class", false),
        ("META-INF/versions/9/9/module-info.class", false),
        ("META-INF/versions/9/module-info.class\n", false),
        ("META-INF/versions/9/module-info.classx", false),
        ("xMETA-INF/versions/9/module-info.class", false),
        ("META-INF/versions/\u{663}/module-info.class", false),
    ] {
        assert_eq!(is_versioned_module_info(name), want, "{name:?}");
    }
}
