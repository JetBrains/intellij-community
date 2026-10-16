use std::path::Path;

use super::*;

fn write_file(path: &Path, content: &str) {
    std::fs::create_dir_all(path.parent().unwrap()).unwrap();
    std::fs::write(path, content).unwrap();
}

/// Writes the files of a distribution below `directory` and reads it with the home `/ws/ide_home`.
fn read_distribution(directory: &Path, runtime_module_repository: bool) -> anyhow::Result<Distribution> {
    write_file(&directory.join("idea.properties"), "idea.platform.prefix=fromDistribution\n");
    write_file(&directory.join("vmoptions"), "-Xmx2048m\n-Dsun.io.useCanonCaches=false\n");
    write_file(&directory.join("product-info.json"), r#"{"launch": [{}]}"#);
    write_file(&directory.join("core-classpath.txt"), "lib/a.jar\n\n/abs/b.jar\n");
    let files = DistributionFiles {
        ide_config: &directory.join("idea.ide.config"),
        idea_properties: &directory.join("idea.properties"),
        vm_options: &directory.join("vmoptions"),
        product_info: &directory.join("product-info.json"),
        core_classpath: &directory.join("core-classpath.txt"),
    };
    Distribution::read("/ws/ide_home", &files, "bin/idea.vmoptions", runtime_module_repository)
}

#[test]
fn a_distribution_reads_its_main_class_properties_and_class_path() {
    let directory = tempfile::tempdir().unwrap();
    write_file(
        &directory.path().join("idea.ide.config"),
        "main.class.name=com.intellij.idea.Main\n",
    );
    let distribution = read_distribution(directory.path(), true).unwrap();
    assert_eq!(distribution.home, "/ws/ide_home");
    assert_eq!(distribution.main_class, "com.intellij.idea.Main");
    let home = Path::new("/ws/ide_home");
    let path = |relative: &Path| home.join(relative).display().to_string();
    assert_eq!(
        distribution.class_path,
        [path(Path::new("lib").join("a.jar").as_path()), "/abs/b.jar".to_owned()]
    );
    assert_eq!(
        distribution.properties.get("jb.vmOptionsFile"),
        Some(&path(Path::new("bin").join("idea.vmoptions").as_path()))
    );
    assert_eq!(
        distribution.runtime_module_repository,
        Some(path(Path::new("modules").join("module-descriptors.dat").as_path()))
    );
    assert!(
        read_distribution(directory.path(), false)
            .unwrap()
            .runtime_module_repository
            .is_none()
    );
}

#[test]
fn a_distribution_needs_a_main_class() {
    let directory = tempfile::tempdir().unwrap();
    let config = directory.path().join("idea.ide.config");
    write_file(&config, "home.path=idea.metadata\nmain.class.name=\n");
    let Err(error) = read_distribution(directory.path(), false) else {
        panic!("read a distribution without a main class");
    };
    assert_eq!(format!("{error:#}"), format!("{} states no main.class.name", config.display()));
}

#[test]
fn the_argument_file_text_holds_one_quoted_argument_per_line() {
    let arguments = ["-ea", "-Dx=a b", "-cp", "com.intellij.idea.Main"].map(str::to_owned);
    assert_eq!(
        argument_file_text(&arguments).unwrap(),
        "-ea\n\"-Dx=a b\"\n-cp\ncom.intellij.idea.Main\n"
    );
    for argument in ["a\nb", "a\rb"] {
        let error = argument_file_text(&[argument.to_owned()]).unwrap_err();
        assert!(format!("{error:#}").contains("holds a line break"), "{error:#}");
    }
}

#[test]
fn the_distribution_states_the_runtime_module_repository_first() {
    let property = RUNTIME_MODULE_REPOSITORY_PROPERTY;
    let mut distribution = IndexMap::from([(property.to_owned(), "/product-info.dat".to_owned())]);
    add_runtime_module_repository(&mut distribution, "/home/modules/module-descriptors.dat", &IndexMap::new());
    assert_eq!(distribution.get(property).map(String::as_str), Some("/product-info.dat"));

    let mut distribution = IndexMap::new();
    let caller = IndexMap::from([(property.to_owned(), "/custom.dat".to_owned())]);
    add_runtime_module_repository(&mut distribution, "/home/modules/module-descriptors.dat", &caller);
    assert!(distribution.is_empty(), "the home overrode the caller flag: {distribution:?}");
}

#[test]
fn an_argument_is_quoted_only_when_java_needs_it() {
    assert_eq!(quote_argument("-Da=b"), "-Da=b");
    assert_eq!(quote_argument(""), "\"\"");
    assert_eq!(quote_argument("-Da=x y"), "\"-Da=x y\"");
    assert_eq!(quote_argument("-Da=\"\""), "\"-Da=\\\"\\\"\"");
    assert_eq!(quote_argument("-Da=p\\q#r"), "\"-Da=p\\\\q#r\"");
}

#[test]
fn a_windows_path_is_quoted_with_each_backslash_escaped() {
    // Inside double quotes, `java` reads `\\` as one backslash, and `\t` or `\n` as a control character.
    assert_eq!(
        quote_argument(r"-Didea.home.path=C:\Program Files\idea"),
        r#""-Didea.home.path=C:\\Program Files\\idea""#
    );
    assert_eq!(quote_argument(r"-Dx=C:\temp\new\table"), r#""-Dx=C:\\temp\\new\\table""#);
    assert_eq!(quote_argument(r"-Dx=C:\dir\"), r#""-Dx=C:\\dir\\""#);
    assert_eq!(quote_argument(r"-Dx=\\?\C:\dir"), r#""-Dx=\\\\?\\C:\\dir""#);
    assert_eq!(
        quote_argument(r"C:\home dir\lib\a.jar;C:/out/lib/b.jar"),
        r#""C:\\home dir\\lib\\a.jar;C:/out/lib/b.jar""#
    );
    // A backslash alone is a reason to quote. A drive path with forward slashes and no space stays as it is.
    assert_eq!(quote_argument(r"-Dx=C:\dev\idea"), r#""-Dx=C:\\dev\\idea""#);
    assert_eq!(quote_argument("-Dx=C:/dev/idea"), "-Dx=C:/dev/idea");
}
