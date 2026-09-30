use super::*;
use crate::test_support::require_error;

#[test]
fn compare_utf16_is_the_java_string_order() {
    let mut values = vec!["Ａ", "\u{1F600}", "b", "a", "ab", "", "é"];
    values.sort_by(|first, second| compare_utf16(first, second));
    assert_eq!(values, ["", "a", "ab", "b", "é", "\u{1F600}", "Ａ"]);
}

#[test]
fn host_path_accepts_the_bazel_spelling() {
    for value in ["a", "bazel-out/k8-fastbuild/bin/lib.jar", "external/+repo+name/a b.jar"] {
        assert_eq!(host_path(value).unwrap(), from_slash(value), "{value:?}");
    }
    #[cfg(unix)]
    assert_eq!(host_path("/tmp/a").unwrap(), "/tmp/a");
}

#[test]
fn host_path_refuses_every_other_spelling() {
    for value in ["", "a//b", "a/", "./a", "a/./b", "a/..", "../a", "a\0b"] {
        require_error(host_path(value), "Unsupported host path");
    }
    #[cfg(unix)]
    require_error(host_path("//a"), "Unsupported host path");
}

#[test]
fn absolute_path_starts_at_the_working_directory() {
    let directory = std::env::current_dir().unwrap();
    let expected = directory.join(from_slash("a/b.jar").as_ref());
    assert_eq!(absolute_path("a/b.jar").unwrap(), expected.to_str().unwrap());
    require_error(absolute_path("a/../b.jar"), "Unsupported host path");
}
