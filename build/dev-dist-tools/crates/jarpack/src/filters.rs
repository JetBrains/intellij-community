// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::HashSet;
use std::sync::{Arc, LazyLock};

use regex::Regex;

use crate::merge::Filter;

/// The generated per-jar class-path index. The reader drops it on the way in, and the writer writes a new one. So a jar
/// packed from jars with an index does not inherit a stale index.
pub const INDEX_FILE_NAME: &str = "__index__";

/// The one entry that each jar keeps or drops by its policy, not by its name. See [`crate::MergeSpec::merge`].
pub const MANIFEST_ENTRY_NAME: &str = "META-INF/MANIFEST.MF";

/// Reports whether an entry of a *module output* jar belongs in a distribution jar.
///
/// A module output is ours, so the filter drops only two kinds of file. The icon-rule file is a build-time input. The
/// compilation cache leaves the other files in an output directory. It is a port of `defaultModuleOutputNamesFilter` in
/// `zip/src/jarMerger.kt`.
pub fn module_output_name_filter(name: &str) -> bool {
    name != "icon-robots.txt"
        && !name.ends_with("/icon-robots.txt")
        && name != ".unmodified"
        && name != ".hash"
        && name != "classpath.index"
        && name != "module-info.class"
}

/// [`module_output_name_filter`] as a [`Filter`].
pub fn module_output_filter() -> Filter {
    Arc::new(module_output_name_filter)
}

/// [`library_name_filter`] as a [`Filter`].
pub fn library_filter() -> Filter {
    Arc::new(library_name_filter)
}

/// The exact-name set of `getIgnoredNames` in `zip/src/librarySourcesFilter.kt`, in the order that file builds it. It
/// is a literal, so that a diff against the original is easy.
static IGNORED_LIBRARY_NAMES: LazyLock<HashSet<String>> = LazyLock::new(|| {
    let names = [
        // compilation cache on TC
        ".hash",
        "classpath.index",
        ".gitattributes",
        "pom.xml",
        "about.html",
        "module-info.class",
        // default is ok (modules not used)
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
        // duplicates in maven-resolver-transport-http and maven-resolver-transport-file
        "META-INF/sisu/javax.inject.Named",
        // duplicates in recommenders-jayes-io-2.5.5 and recommenders-jayes-2.5.5.jar
        "OSGI-INF/l10n/bundle.properties",
        // Groovy
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
        // A merging build politic breaks Graal VM Truffle-based plugins in an inconsistant way, so it's better to
        // provide a correctly merged version in the plugin.
        "META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider",
    ];
    let mut set: HashSet<String> = names.iter().map(ToString::to_string).collect();
    // The legal files, made as the original makes them. Each base name is in its given and its lower-case spelling. Each
    // spelling is at the root and under META-INF/, with no extension, `.txt` and `.md`.
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
});

/// The prefix list of `defaultLibrarySourcesNamesFilter`, in source order.
const IGNORED_LIBRARY_PREFIXES: [&str; 14] = [
    "license/",
    "licenses/",
    "native/",
    "META-INF/license/",
    "META-INF/LICENSE-",
    "native-image/",
    "org/xml/sax/", // XmlRPC lib
    "META-INF/versions/9/org/apache/logging/log4j/",
    "META-INF/versions/9/org/bouncycastle/",
    "META-INF/versions/10/org/bouncycastle/",
    "META-INF/versions/15/org/bouncycastle/",
    "kotlinx/coroutines/repackaged/",
    "META-INF/INDEX.LIST",
    "net/sf/cglib/core/AbstractClassGenerator", // we replace the lib class with our own patched version
];

/// Reports whether an entry of a third-party *library* jar belongs in a distribution jar.
///
/// It is a port of `defaultLibrarySourcesNamesFilter`. The filter is a pure function of the entry name. A disagreement
/// with the Kotlin original shows as a jar that differs by the packer that wrote it. The parity check over every packed
/// jar finds such a disagreement.
pub fn library_name_filter(name: &str) -> bool {
    if IGNORED_LIBRARY_NAMES.contains(name) {
        return false;
    }
    if VERSIONED_MODULE_INFO.is_match(name) {
        return false;
    }
    if name.ends_with(".kotlin_metadata") {
        return false;
    }
    if IGNORED_LIBRARY_PREFIXES.iter().any(|prefix| name.starts_with(prefix)) {
        return false;
    }
    if name.starts_with("META-INF/") && (name.ends_with(".DSA") || name.ends_with(".SF") || name.ends_with(".RSA")) {
        return false;
    }
    true
}

/// The `module-info` of a multi-release jar. It matches the whole name, as the Kotlin `Regex.matches` does. `[0-9]`
/// stands for the Kotlin `\d`, which is ASCII only.
static VERSIONED_MODULE_INFO: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^META-INF/versions/[0-9]+/module-info\.class$").expect("a valid pattern"));
