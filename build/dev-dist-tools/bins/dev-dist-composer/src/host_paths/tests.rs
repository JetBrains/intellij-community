use super::*;

#[cfg(unix)]
#[test]
fn parent_and_resolve_relative_of_absolute_paths() {
    assert_eq!(parent("/a/b"), "/a");
    assert_eq!(parent("/a"), "/");
    assert_eq!(parent("/"), "/");
    assert_eq!(resolve_relative("/a", "b/c.jar"), "/a/b/c.jar");
}
