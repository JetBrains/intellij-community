// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use crate::nativelib::{
    Arch, Family, FamilyToken, Match, detect_os_family, determine_arch, find_family_token, is_executable, is_native_entry,
    lib_name_from_file, parse_variant, relative_path, select,
};

/// The native entries of `org.sqlite:native` in their central-directory order.
const SQLITE_ENTRIES: [&str; 6] = [
    "sqlite/win-aarch64/sqliteij.dll",
    "sqlite/linux-x86_64/libsqliteij.so",
    "sqlite/win-x86_64/sqliteij.dll",
    "sqlite/linux-aarch64/libsqliteij.so",
    "sqlite/mac-x86_64/libsqliteij.jnilib",
    "sqlite/mac-aarch64/libsqliteij.jnilib",
];

#[test]
fn detect_os_family_follows_the_kotlin_regexes() {
    for (entry, want) in [
        ("sqlite/win-aarch64/sqliteij.dll", Some((Family::Windows, "sqlite/"))),
        ("sqlite/mac-x86_64/libsqliteij.jnilib", Some((Family::MacOS, "sqlite/"))),
        ("sqlite/linux-aarch64/libsqliteij.so", Some((Family::Linux, "sqlite/"))),
        (
            "com/sun/jna/darwin-aarch64/libjnidispatch.jnilib",
            Some((Family::MacOS, "com/sun/jna/")),
        ),
        ("com/sun/jna/win32-x86-64/jnidispatch.dll", Some((Family::Windows, "com/sun/jna/"))),
        ("com/sun/jna/linux-x86-64/libjnidispatch.so", Some((Family::Linux, "com/sun/jna/"))),
        (
            "resources/com/pty4j/native/linux/aarch64/libpty.so",
            Some((Family::Linux, "resources/com/pty4j/native/")),
        ),
        ("darwin/libpty.dylib", Some((Family::MacOS, ""))),
        ("Windows/x64/foo.dll", Some((Family::Windows, ""))),
        ("macos-arm64/libskiko.dylib", Some((Family::MacOS, ""))),
        ("Linux-Android/libx.so", None),
        ("Linux-Musl/libx.so", None),
        ("META-INF/MANIFEST.MF", None),
        ("libskiko-macos-arm64.dylib", Some((Family::MacOS, ""))),
        ("skiko-windows-x64.dll", Some((Family::Windows, ""))),
        ("libskiko-linux-x64.so", Some((Family::Linux, ""))),
        ("libskiko-linux-musl-x64.so", None),
        ("icudtl.dat", Some((Family::Windows, ""))),
        ("libfoo.so", None),
        // The cases below are not in the Go test. They pin the leftmost match, the order of the alternatives and the
        // ASCII case folding. The Go `(?i)` also folded U+017F to `s`, and `select` refuses a name that is not ASCII.
        ("/linux/libx.so", Some((Family::Linux, "/"))),
        ("-linux/libx.so", Some((Family::Linux, "-"))),
        ("a/linux-Musl/x/linux/libx.so", None),
        ("a/MACOS/libx.dylib", Some((Family::MacOS, "a/"))),
        ("a/macoſ/libx.dylib", None),
        ("a/xlinux/b/win32-x/libx.dll", Some((Family::Windows, "a/xlinux/b/"))),
        ("a/macx/libx.dylib", None),
    ] {
        assert_eq!(detect_os_family(entry), want, "{entry}");
    }
}

/// The cases of the hand-written family matcher: every alternative, the order of the alternatives at one start, the
/// leftmost start, the ASCII case folding, and a token that is a prefix of another.
#[test]
fn the_family_token_is_the_leftmost_first_match_of_the_pattern() {
    const MACOS: Option<(FamilyToken, usize)> = Some((FamilyToken::Family(Family::MacOS), 2));
    const WINDOWS: Option<(FamilyToken, usize)> = Some((FamilyToken::Family(Family::Windows), 2));
    const LINUX: Option<(FamilyToken, usize)> = Some((FamilyToken::Family(Family::Linux), 2));
    const NO_FAMILY: Option<(FamilyToken, usize)> = Some((FamilyToken::NoFamily, 2));
    for (entry, want) in [
        // Every alternative, with each separator.
        ("a/darwin-x/l", MACOS),
        ("a/darwin/l", MACOS),
        ("a/mac-x/l", MACOS),
        ("a/mac/l", MACOS),
        ("a/macos-x/l", MACOS),
        ("a/macos/l", MACOS),
        ("a/win32-x/l", WINDOWS),
        ("a/win-x/l", WINDOWS),
        ("a/win/l", WINDOWS),
        ("a/windows-x/l", WINDOWS),
        ("a/windows/l", WINDOWS),
        ("a/linux-android/l", NO_FAMILY),
        ("a/linux-musl/l", NO_FAMILY),
        ("a/linux-x/l", LINUX),
        ("a/linux/l", LINUX),
        // A token that is a prefix of another. `mac`, `win` and `linux` need a separator directly after them, and the
        // Android and Musl tokens need a `/` after them.
        ("a/macosx/l", None),
        ("a/macx/l", None),
        ("a/windowsx/l", None),
        ("a/win64/l", None),
        ("a/win32/l", None),
        ("a/linux-androidx/l", LINUX),
        ("a/linux-musl-x/l", LINUX),
        ("a/linuxx/l", None),
        // ASCII case folding.
        ("a/DaRwIn/l", MACOS),
        ("a/MACOS-x/l", MACOS),
        ("a/WIN32-x/l", WINDOWS),
        ("a/Windows/l", WINDOWS),
        ("a/LINUX-MUSL/l", NO_FAMILY),
        ("a/Linux-Android/l", NO_FAMILY),
        ("a/LiNuX/l", LINUX),
        // The leftmost start wins, in both orders, and a later token does not override an Android or a Musl token.
        ("a/linux/darwin/l", LINUX),
        ("a/darwin/linux/l", MACOS),
        ("a/win-linux/l", WINDOWS),
        ("a/linux-win/l", LINUX),
        ("a/linux-musl/darwin/l", NO_FAMILY),
        ("a/darwin/linux-musl/l", MACOS),
        // A token starts at 0 or after a `-` or a `/`, and at 0 the start before a separator comes first.
        ("linux/l", Some((FamilyToken::Family(Family::Linux), 0))),
        ("-linux/l", Some((FamilyToken::Family(Family::Linux), 1))),
        ("/darwin/l", Some((FamilyToken::Family(Family::MacOS), 1))),
        ("a-win/l", WINDOWS),
        ("ab/c_linux/l", None),
        ("xlinux/l", None),
        ("", None),
        // A non-ASCII byte matches no token, and the start after it is a character boundary.
        ("a/ſ-linux/l", Some((FamilyToken::Family(Family::Linux), 5))),
        ("a/macoſ/l", None),
    ] {
        assert_eq!(find_family_token(entry), want, "{entry}");
    }
}

#[test]
fn determine_arch_follows_the_kotlin_rules() {
    for (family, entry, want) in [
        (Family::Windows, "win-aarch64/sqliteij.dll", Some(Arch::AArch64)),
        (Family::MacOS, "mac-x86_64/libsqliteij.jnilib", Some(Arch::X64)),
        (Family::Linux, "linux/aarch64/libpty.so", Some(Arch::AArch64)),
        (Family::Linux, "linux/x86-64/libpty.so", Some(Arch::X64)),
        (Family::Linux, "linux-x86-64/libjnidispatch.so", Some(Arch::X64)),
        (Family::Linux, "linux-arm64/libx.so", Some(Arch::AArch64)),
        (Family::MacOS, "darwin/libpty.dylib", Some(Arch::Universal)),
        (Family::Linux, "linux/libpty.so", Some(Arch::X64)),
        (Family::Windows, "win32-x86/jnidispatch.dll", None),
        (Family::Linux, "linux/sub/dir/libx.so", None),
        (Family::MacOS, "libskiko-macos-arm64.dylib", Some(Arch::AArch64)),
        (Family::Windows, "skiko-windows-x64.dll", Some(Arch::X64)),
        (Family::Windows, "icudtl.dat", Some(Arch::Universal)),
        (Family::Linux, "libfoo-linux.so", None),
    ] {
        assert_eq!(determine_arch(family, entry), want, "{family} {entry}");
    }
}

#[test]
fn relative_path_follows_each_library_rule() {
    for (lib_name, arch, file_name, entry, want) in [
        (
            "async-profiler",
            Arch::X64,
            "libasyncProfiler.so",
            "linux-x64/libasyncProfiler.so",
            "amd64/libasyncProfiler.so",
        ),
        (
            "async-profiler",
            Arch::Universal,
            "libasyncProfiler.dylib",
            "macos/libasyncProfiler.dylib",
            "libasyncProfiler.dylib",
        ),
        (
            "skiko-awt-runtime-all",
            Arch::AArch64,
            "libskiko-macos-arm64.dylib",
            "libskiko-macos-arm64.dylib",
            "libskiko-macos-arm64.dylib",
        ),
        (
            "jna",
            Arch::AArch64,
            "libjnidispatch.jnilib",
            "darwin-aarch64/libjnidispatch.jnilib",
            "aarch64/libjnidispatch.jnilib",
        ),
        (
            "jna",
            Arch::X64,
            "jnidispatch.dll",
            "win32-x86-64/jnidispatch.dll",
            "amd64/jnidispatch.dll",
        ),
        (
            "native",
            Arch::AArch64,
            "sqliteij.dll",
            "win-aarch64/sqliteij.dll",
            "win-aarch64/sqliteij.dll",
        ),
        (
            "pty4j",
            Arch::AArch64,
            "libpty.so",
            "linux/aarch64/libpty.so",
            "linux/aarch64/libpty.so",
        ),
    ] {
        assert_eq!(relative_path(lib_name, arch, file_name, entry).unwrap(), want, "{lib_name} {entry}");
    }
    assert!(
        relative_path("jna", Arch::Universal, "libjnidispatch.jnilib", "darwin/libjnidispatch.jnilib").is_err(),
        "a universal jna file has no directory"
    );
}

#[test]
fn lib_name_from_file_cases() {
    for (file, want) in [
        ("native-3.42.0-jb.1.jar", "native"),
        ("async-profiler-3.0-9-9d5c2f3.jar", "async-profiler"),
        ("jna-5.14.0.jar", "jna"),
        ("skiko-awt-runtime-all-0.8.18.jar", "skiko-awt-runtime-all"),
        ("pty4j-0.13.5.jar", "pty4j"),
        ("intellij-deps-rocksdbjni-10.8.3.jar", "intellij-deps-rocksdbjni"),
        ("sqlite-native.jar", "sqlite"),
        ("native.jar", ""),
    ] {
        assert_eq!(lib_name_from_file(file), want, "{file}");
    }
}

#[test]
fn is_native_entry_and_is_executable() {
    for (name, want) in [
        ("sqlite/mac-aarch64/libsqliteij.jnilib", true),
        ("a/b.dylib", true),
        ("a/b.so", true),
        ("a/b.tbd", true),
        ("a/b.exe", true),
        ("a/b.dll", true),
        ("resources/com/pty4j/native/linux/x86-64/pty4j-unix-spawn-helper", true),
        ("icudtl.dat", true),
        ("sqlite/mac-aarch64/libsqliteij.jnilib.sha256", false),
        ("META-INF/MANIFEST.MF", false),
        ("a/b.class", false),
        ("a.so/b", false),
    ] {
        assert_eq!(is_native_entry(name), want, "is_native_entry({name})");
    }
    for (family, file_name, want) in [
        (Family::Linux, "pty4j-unix-spawn-helper", true),
        (Family::MacOS, "pty4j-unix-spawn-helper", true),
        (Family::Windows, "helper", false),
        (Family::Linux, "libpty.so", false),
        (Family::MacOS, "libsqliteij.jnilib", false),
        (Family::Windows, "sqliteij.dll", false),
    ] {
        assert_eq!(is_executable(Some(family), file_name), want, "is_executable({family}, {file_name})");
    }
}

#[test]
fn select_refuses_an_entry_that_is_not_ascii() {
    let error = select(&["a/macoſ/libx.dylib"], Family::MacOS, Arch::AArch64)
        .unwrap_err()
        .to_string();
    assert!(
        error.contains("\"a/macoſ/libx.dylib\" is not ASCII"),
        "the error {error:?} must name the entry"
    );
}

#[test]
fn select_takes_the_entries_of_one_platform() {
    for (variant, path_with_prefix, path, arch) in [
        (
            "darwin_aarch64",
            "sqlite/mac-aarch64/libsqliteij.jnilib",
            "mac-aarch64/libsqliteij.jnilib",
            Arch::AArch64,
        ),
        (
            "darwin_x64",
            "sqlite/mac-x86_64/libsqliteij.jnilib",
            "mac-x86_64/libsqliteij.jnilib",
            Arch::X64,
        ),
        (
            "linux_aarch64",
            "sqlite/linux-aarch64/libsqliteij.so",
            "linux-aarch64/libsqliteij.so",
            Arch::AArch64,
        ),
        (
            "linux_x64",
            "sqlite/linux-x86_64/libsqliteij.so",
            "linux-x86_64/libsqliteij.so",
            Arch::X64,
        ),
        (
            "windows_aarch64",
            "sqlite/win-aarch64/sqliteij.dll",
            "win-aarch64/sqliteij.dll",
            Arch::AArch64,
        ),
        (
            "windows_x64",
            "sqlite/win-x86_64/sqliteij.dll",
            "win-x86_64/sqliteij.dll",
            Arch::X64,
        ),
    ] {
        let (family, target) = parse_variant(variant).unwrap();
        let matches = select(&SQLITE_ENTRIES, family, target).unwrap();
        let want = Match {
            path_with_prefix: path_with_prefix.into(),
            path: path.into(),
            arch,
        };
        assert_eq!(matches, std::slice::from_ref(&want), "{variant}");
        assert_eq!(matches[0].file_name(), path.rsplit('/').next().unwrap(), "{variant}");
    }
    // A universal file serves both architectures. A Musl entry and an entry of no architecture are skipped.
    let pty4j = [
        "resources/com/pty4j/native/darwin/libpty.dylib",
        "resources/com/pty4j/native/linux/aarch64/libpty.so",
        "resources/com/pty4j/native/linux/x86-64/pty4j-unix-spawn-helper",
        "resources/com/pty4j/native/Linux-Musl/libpty.so",
        "resources/com/pty4j/native/win/x86/winpty.dll",
    ];
    for arch in [Arch::X64, Arch::AArch64] {
        let matches = select(&pty4j, Family::MacOS, arch).unwrap();
        assert!(
            matches.len() == 1 && matches[0].path == "darwin/libpty.dylib" && matches[0].arch == Arch::Universal,
            "{matches:?}"
        );
    }
    let matches = select(&pty4j, Family::Linux, Arch::X64).unwrap();
    assert!(
        matches.len() == 1 && matches[0].path == "linux/x86-64/pty4j-unix-spawn-helper",
        "{matches:?}"
    );
    assert!(
        select(&pty4j, Family::Windows, Arch::X64).unwrap().is_empty(),
        "a 32-bit Windows file has no architecture"
    );
    // rocksdbjni keeps every native at the jar root, and ships more Linux flavours than the six platforms take.
    let rocksdb = [
        "librocksdbjni-linux-aarch64-musl.so",
        "librocksdbjni-linux-aarch64.so",
        "librocksdbjni-linux-ppc64le-musl.so",
        "librocksdbjni-linux-ppc64le.so",
        "librocksdbjni-linux-riscv64.so",
        "librocksdbjni-linux-s390x-musl.so",
        "librocksdbjni-linux-s390x.so",
        "librocksdbjni-linux32-musl.so",
        "librocksdbjni-linux32.so",
        "librocksdbjni-linux64-musl.so",
        "librocksdbjni-linux64.so",
        "librocksdbjni-osx-arm64.jnilib",
        "librocksdbjni-osx-x86_64.jnilib",
        "librocksdbjni-win-arm64.dll",
        "librocksdbjni-win64.dll",
    ];
    for (variant, want) in [
        ("darwin_aarch64", "librocksdbjni-osx-arm64.jnilib"),
        ("darwin_x64", "librocksdbjni-osx-x86_64.jnilib"),
        ("linux_aarch64", "librocksdbjni-linux-aarch64.so"),
        ("linux_x64", "librocksdbjni-linux64.so"),
        ("windows_aarch64", "librocksdbjni-win-arm64.dll"),
        ("windows_x64", "librocksdbjni-win64.dll"),
    ] {
        let (family, arch) = parse_variant(variant).unwrap();
        let matches = select(&rocksdb, family, arch).unwrap();
        assert!(
            matches.len() == 1 && matches[0].path == want,
            "rocksdbjni {variant}: {matches:?}, want {want} alone"
        );
        let path = relative_path(
            "intellij-deps-rocksdbjni",
            matches[0].arch,
            matches[0].file_name(),
            &matches[0].path,
        )
        .unwrap();
        assert_eq!(path, want, "rocksdbjni {variant}");
    }
    let error = select(&["a/linux-x64/libx.so", "b/linux-x64/libx.so"], Family::Linux, Arch::X64).unwrap_err();
    assert!(error.to_string().contains("common path prefix"), "two prefixes: {error}");
}

#[test]
fn parse_variant_cases() {
    for (variant, family, arch) in [
        ("darwin_aarch64", Family::MacOS, Arch::AArch64),
        ("linux_x64", Family::Linux, Arch::X64),
        ("windows_aarch64", Family::Windows, Arch::AArch64),
    ] {
        assert_eq!(parse_variant(variant).unwrap(), (family, arch), "{variant}");
        assert_eq!(format!("{family}_{arch}"), variant);
    }
    for variant in ["", "linux", "linux_", "_x64", "mac_x64", "windows_arm64", "{platform}"] {
        assert!(parse_variant(variant).is_err(), "accepted {variant:?}");
    }
}
