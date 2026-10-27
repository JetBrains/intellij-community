// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use super::*;

#[test]
fn explicit_options_are_complete_tokens() {
    for value in [
        "-Djdk.net.unixdomain.tmpdir=C:\\sockets",
        "\"-Djdk.net.unixdomain.tmpdir=C:\\my sockets\" -Xmx2g",
        "-Xmx2g '-Djdk.net.unixdomain.tmpdir=C:/my sockets'",
        "-Djdk.net.unixdomain.tmpdir=",
        "-Djdk.net.unixdomain.tmpdir",
    ] {
        assert!(has_property(value), "{value}");
    }
    for value in [
        "-Dother=jdk.net.unixdomain.tmpdir",
        "-Djdk.net.unixdomain.tmpdir.extra=C:/other",
        "-Dother=\"-Djdk.net.unixdomain.tmpdir=C:/other\"",
        "-Xmx2g",
    ] {
        assert!(!has_property(value), "{value}");
    }
}

#[test]
fn unrelated_main_keeps_its_options() {
    let mut options = vec!["-Xmx2g".to_owned()];
    prepare("com.intellij.idea.Main", &mut options).unwrap();
    assert_eq!(options, ["-Xmx2g"]);
}

#[test]
fn directories_are_unique_and_private() {
    let first = create_directory().unwrap();
    let second = create_directory().unwrap();
    assert_ne!(first, second);
    assert!(first.to_str().unwrap().len() <= 80);
    std::fs::write(first.join("owned"), "test").unwrap();
    std::fs::remove_dir_all(first).unwrap();
    assert!(second.is_dir());
    std::fs::remove_dir_all(second).unwrap();
}

#[test]
fn long_paths_and_existing_directories_are_refused() {
    let descriptor = private_descriptor().unwrap();
    assert!(create_private_directory(Path::new(&format!("C:/{}", "x".repeat(81))), &descriptor).is_err());
    let path = create_directory().unwrap();
    let error = create_private_directory(&path, &descriptor).unwrap_err();
    assert_eq!(error.downcast_ref::<std::io::Error>().unwrap().raw_os_error(), Some(183));
    std::fs::remove_dir_all(path).unwrap();
}
