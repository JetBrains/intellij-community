use std::collections::BTreeMap;

use super::*;

fn links(pairs: &[(&str, &str)]) -> BTreeMap<String, String> {
    pairs.iter().map(|(name, target)| (name.to_string(), target.to_string())).collect()
}

fn error_text<T: std::fmt::Debug>(result: Result<T>) -> String {
    format!("{:#}", result.unwrap_err())
}

#[test]
fn validate_path_refuses_unsafe_paths() {
    for name in [
        "lib/a.jar",
        "a",
        "node_modules/.bin/acorn",
        "Frameworks/Chromium Embedded Framework.framework",
    ] {
        if let Err(error) = validate_path(name) {
            panic!("safe path {name:?}: {error}");
        }
    }
    for name in [
        "",
        "/lib/a.jar",
        "../a.jar",
        "lib/../a.jar",
        "lib/./a.jar",
        "lib//a.jar",
        "C:/a.jar",
        "lib\\a.jar",
        "lib/a.jar/",
        "lib/\0",
    ] {
        let message = error_text(validate_path(name));
        assert!(message.starts_with("invalid relative path"), "{name:?}: {message}");
    }
}

/// No payload name in the repository is outside ASCII or holds `<`, `>` or `&`. The error names such a path.
#[test]
fn validate_path_refuses_names_outside_the_supported_text() {
    for name in ["caf\u{e9}", "cafe\u{301}", "\u{65e5}\u{672c}\u{8a9e}", "a&b", "a<b", "a>b"] {
        let message = error_text(validate_path(name));
        assert!(
            message.contains("unsupported character") && message.contains(&format!("{name:?}")),
            "{name:?}: {message}"
        );
    }
}

#[test]
fn validate_links_refuses_chains_and_unsafe_links() {
    for (name, pairs, message) in [
        (
            "root alias chain",
            &[("current", "."), ("escape", "current/../outside")][..],
            "unsupported symbolic link chain",
        ),
        (
            "case alias chain",
            &[("current", "."), ("escape", "CURRENT/../outside")],
            "unsupported symbolic link chain",
        ),
        (
            "nested alias chain",
            &[("nested/current", ".."), ("nested/escape", "current/../outside")],
            "unsupported symbolic link chain",
        ),
        (
            "safe chain",
            &[("first", "second"), ("second", "third"), ("third", "missing-file")],
            "unsupported symbolic link chain",
        ),
        ("cycle", &[("a", "b/../file"), ("b", "a")], "unsupported symbolic link chain"),
        ("self cycle", &[("a", "a/../file")], "unsupported symbolic link chain"),
        (
            "nested cycle",
            &[("a", "nested/b/../file"), ("nested/b", "../a")],
            "unsupported symbolic link chain",
        ),
        (
            "case alias cycle",
            &[("a", "B/../file"), ("b", "A")],
            "unsupported symbolic link chain",
        ),
        ("escaping target", &[("nested/a", "../../outside")], "escapes"),
        ("absolute target", &[("a", "/outside")], "invalid"),
        ("escaping destination", &[("../a", "inside")], "invalid"),
        ("link parent collision", &[("a", "inside"), ("a/b", "file")], "conflicting"),
        ("case alias collision", &[("a", "inside"), ("A", "inside")], "conflicting"),
        ("case alias parent collision", &[("a", "inside"), ("A/b", "file")], "conflicting"),
        ("non-ASCII destination", &[("caf\u{e9}", ".")], "unsupported character"),
        ("non-ASCII target", &[("a", "cafe\u{301}")], "unsupported character"),
        ("ampersand destination", &[("a&b", "inside")], "unsupported character"),
        ("angle bracket target", &[("a", "<inside>")], "unsupported character"),
    ] {
        let result = validate_links(&links(pairs));
        let text = format!("{result:?}");
        assert!(
            result.is_err() && text.contains(message),
            "{name}: {pairs:?} gave {text}, want {message}"
        );
    }
}

/// The test uses the shapes of the links in the payloads of the repository. An npm `.bin` directory has relative file
/// links with `..` segments. The macOS JCEF archive has a directory link with a `./` prefix.
#[test]
fn validate_links_accepts_the_real_link_shapes_without_file_system_access() {
    for pairs in [
        &[][..],
        &[("current", ".")],
        &[
            ("node_modules/.bin/acorn", "../acorn/bin/acorn"),
            ("node_modules/.bin/rimraf", "../rimraf/bin.js"),
        ],
        &[(
            "Frameworks/Chromium Embedded Framework.framework",
            "./cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework",
        )],
        &[("current", "directory/nested"), ("safe", "current-other/../inside")],
        &[("alias", "./modules/../modules/separate.jar")],
        &[("first", "missing-file"), ("second", "missing-file")],
    ] {
        if let Err(error) = validate_links(&links(pairs)) {
            panic!("safe links {pairs:?}: {error}");
        }
    }
}

/// The Go composer refused a link target with an empty segment. `filemeta` checks its own entry points separately.
#[test]
fn validate_links_refuses_a_target_with_an_empty_segment() {
    for target in ["payload/", "lib//payload"] {
        let message = error_text(validate_links(&links(&[("lib/alias", target)])));
        assert!(message.contains("has an empty segment"), "validate_links {target:?}: {message}");
        let message = error_text(validate_link_target("lib/alias", target));
        assert!(
            message.contains("has an empty segment"),
            "validate_link_target {target:?}: {message}"
        );
    }
}

#[test]
fn path_identity_lowercases_ascii_and_refuses_other_text() {
    assert_eq!(path_identity("Lib/A.JAR").unwrap(), "lib/a.jar");
    assert_eq!(path_identity("lib/a.jar").unwrap(), "lib/a.jar");
    for name in ["CAFE\u{301}", "\u{212a}", "Stra\u{df}e", "a&b", "a<b", "a>b"] {
        let message = error_text(path_identity(name));
        assert!(message.contains("unsupported character"), "{name:?}: {message}");
    }
}

#[test]
fn clean_link_target_keeps_a_relative_target_relative() {
    for (target, expected) in [
        ("./tool", "tool"),
        (
            "./cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework",
            "cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework",
        ),
        ("lib//payload/", "lib/payload"),
        ("lib/../lib/./native.jar", "lib/../lib/native.jar"),
        ("../alias/../tool", "../alias/../tool"),
        ("../sibling", "../sibling"),
        (".", "."),
        ("./", "."),
        ("", ""),
        ("/absolute//./target/", "/absolute//./target/"),
    ] {
        assert_eq!(clean_link_target(target), expected, "clean_link_target({target:?})");
    }
}

#[test]
fn parent_of_gives_the_path_before_the_last_slash() {
    assert_eq!(parent_of("lib/modules/a.jar"), Some("lib/modules"));
    assert_eq!(parent_of("lib/a.jar"), Some("lib"));
    assert_eq!(parent_of("a.jar"), None);
}

#[test]
fn validate_entry_name_refuses_what_a_jar_entry_cannot_be() {
    let longest = "a".repeat(65535);
    for name in [
        "lib/a.jar",
        "META-INF/MANIFEST.MF",
        "a/__index__",
        "a..b",
        ".hidden",
        longest.as_str(),
    ] {
        if let Err(error) = validate_entry_name(name) {
            panic!("safe entry name {name:?}: {error}");
        }
    }
    let too_long = "a".repeat(65536);
    for name in [
        "",
        ".",
        "..",
        "../a",
        "/a",
        "a//b",
        "a/./b",
        "a/../b",
        "a/",
        "a\\b",
        "C:a",
        "a\0",
        "a\rb",
        "a\nb",
        INDEX_FILE_NAME,
        too_long.as_str(),
    ] {
        let message = error_text(validate_entry_name(name));
        assert!(message.starts_with("unsafe entry name"), "{name:?}: {message}");
    }
}

#[test]
fn validate_relative_path_refuses_names_that_a_host_cannot_hold() {
    for name in ["lib/a.jar", "bin/tool", "lib/console.txt", "COM0", "COM10", "lpt", "a.b/c"] {
        if let Err(error) = validate_relative_path(name) {
            panic!("portable path {name:?}: {error}");
        }
    }
    for (name, message) in [
        ("", "unsafe relative path"),
        ("../escape", "unsafe relative path"),
        ("nested/../../escape", "unsafe relative path"),
        ("/absolute", "unsafe relative path"),
        (r"nested\escape", "unsafe relative path"),
        ("./file", "unsafe relative path"),
        (INDEX_FILE_NAME, "unsafe relative path"),
        ("a./b", "unsafe path component \"a.\""),
        ("a /b", "unsafe path component \"a \""),
        ("a<b", "unsafe path component"),
        ("a>b", "unsafe path component"),
        ("a\"b", "unsafe path component"),
        ("a|b", "unsafe path component"),
        ("a?b", "unsafe path component"),
        ("a*b", "unsafe path component"),
        ("a\u{1}b", "unsafe path component"),
        ("lib/NUL", "reserved path component \"NUL\""),
        ("lib/con.jar", "reserved path component \"con.jar\""),
        ("Aux.txt", "reserved path component"),
        ("prn", "reserved path component"),
        ("com1", "reserved path component"),
        ("LPT9.log", "reserved path component"),
    ] {
        let text = error_text(validate_relative_path(name));
        assert!(text.starts_with(message), "{name:?}: {text}, want {message}");
    }
}

/// The expectations are the results of Go `path.Clean`, `path.Dir` and `path.Join`.
#[test]
fn slash_paths_follow_the_go_rules() {
    for (path, expected) in [
        ("", "."),
        (".", "."),
        ("./", "."),
        ("a", "a"),
        ("a/", "a"),
        ("a//b", "a/b"),
        ("a/./b", "a/b"),
        ("a//b/./c/", "a/b/c"),
        ("./A///", "A"),
        ("a/b/..", "a"),
        ("a/../../b", "../b"),
        ("a/../..", ".."),
        ("../a", "../a"),
        ("../../a/../b", "../../b"),
        ("/", "/"),
        ("/../a", "/a"),
        ("//a//b/", "/a/b"),
    ] {
        assert_eq!(clean(path), expected, "clean({path:?})");
    }
    for (path, expected) in [
        ("a/b/c", "a/b"),
        ("a", "."),
        ("a/", "a"),
        ("a/b/", "a/b"),
        ("/a", "/"),
        ("", "."),
        ("a/./b", "a"),
    ] {
        assert_eq!(dir(path), expected, "dir({path:?})");
    }
    for (first, second, expected) in [
        ("", "", ""),
        ("a", "", "a"),
        ("", "b", "b"),
        ("a", "b", "a/b"),
        ("a", "b/c", "a/b/c"),
        ("kotlinc", ".", "kotlinc"),
        ("lib", "../x", "x"),
        ("a/", "b", "a/b"),
        (".", "a", "a"),
        ("a", "b/../c", "a/c"),
        ("a", "..", "."),
        ("a", "../..", ".."),
    ] {
        assert_eq!(join(first, second), expected, "join({first:?}, {second:?})");
    }
}

#[test]
fn slash_path_joins_the_components_of_a_relative_host_path() {
    assert_eq!(slash_path(Path::new("")).as_deref(), Some(""));
    assert_eq!(slash_path(Path::new("lib")).as_deref(), Some("lib"));
    assert_eq!(
        slash_path(&Path::new("lib").join("modules").join("a.jar")).as_deref(),
        Some("lib/modules/a.jar")
    );
    #[cfg(unix)]
    {
        use std::ffi::OsStr;
        use std::os::unix::ffi::OsStrExt;
        assert_eq!(slash_path(&Path::new("lib").join(OsStr::from_bytes(b"\xff"))), None);
    }
}
