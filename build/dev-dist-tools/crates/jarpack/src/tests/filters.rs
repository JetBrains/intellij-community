// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The two filters are shared with the Kotlin `JarPackager` by a port, not by a call. So a disagreement shows as a jar
//! that differs by the packer that wrote it. The names below are the ones a pattern decides, and not a literal, which is
//! where a port drifts.

use crate::{INDEX_FILE_NAME, library_name_filter, module_output_name_filter};

#[test]
fn module_output_name_filter_cases() {
    for (name, want) in [
        ("com/example/Service.class", true),
        ("messages/Bundle.properties", true),
        ("icon-robots.txt", false),
        ("com/example/icon-robots.txt", false),
        ("my-icon-robots.txt", true),
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
