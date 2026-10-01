use super::*;
use crate::test_support::{TempDir, read_text};

#[test]
fn write_dev_ide_config() {
    let directory = TempDir::new();
    let root = directory.root();
    let separator = paths::SEPARATOR;
    let join = |relative: &str| format!("{root}{separator}{}", paths::from_slash(relative));
    for (config, home, home_path) in [
        (join("a/dist.ide.config"), join("a/dist"), "dist".to_owned()),
        (join("b/dist.ide.config"), join("b/nested/dist"), "nested/dist".to_owned()),
        (join("c/dist.ide.config"), join("c"), String::new()),
        (
            join("d/dist.ide.config"),
            join("other/dist"),
            paths::to_slash(&join("other/dist")).into_owned(),
        ),
        (
            join("e/dist.ide.config"),
            join("ee/dist"),
            paths::to_slash(&join("ee/dist")).into_owned(),
        ),
    ] {
        super::write_dev_ide_config(Path::new(&config), Path::new(&home), "Main", "idea", &["a", "b"]).unwrap();
        let expected = format!("home.path={home_path}\nmain.class.name=Main\nplatform.prefix=idea\nadditional.modules=a,b\n");
        assert_eq!(read_text(&config), expected);
    }
    let config = join("f.config");
    super::write_dev_ide_config::<&str>(Path::new(&config), directory.path(), "Main", "idea", &[]).unwrap();
    assert!(read_text(&config).ends_with("additional.modules=\n"));
}

#[cfg(unix)]
#[test]
fn the_composer_config_names_the_home_relative_to_the_config() {
    assert_eq!(
        dev_ide_config_text(
            Path::new("/out/dist.ide.config"),
            Path::new("/out/dist"),
            "com.intellij.idea.Main",
            "idea",
            &["intellij.packed", "intellij.bundled"]
        ),
        "home.path=dist\nmain.class.name=com.intellij.idea.Main\nplatform.prefix=idea\nadditional.modules=intellij.packed,intellij.bundled\n"
    );
}
