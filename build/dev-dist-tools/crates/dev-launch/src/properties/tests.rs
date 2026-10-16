use std::path::Path;

use serde_json::json;

use super::*;

fn write_file(path: &Path, content: &str) {
    std::fs::create_dir_all(path.parent().unwrap()).unwrap();
    std::fs::write(path, content).unwrap();
}

/// The macro of `product-info.json` for the IDE home on the host OS.
fn ide_home_macro() -> &'static str {
    match std::env::consts::OS {
        "windows" => "%IDE_HOME%",
        "macos" => "$APP_PACKAGE/Contents",
        _ => "$IDE_HOME",
    }
}

#[test]
fn java_properties_follow_properties_load() {
    let properties = parse_properties(b"# comment\n! comment\n  a=1\nb : 2\nc 3\nd\\=e = x\\\n    y\nf=\\u0041\\tz\nempty\n").unwrap();
    let expected = [("a", "1"), ("b", "2"), ("c", "3"), ("d=e", "xy"), ("f", "A\tz"), ("empty", "")];
    let actual: Vec<(&str, &str)> = properties.iter().map(|(key, value)| (key.as_str(), value.as_str())).collect();
    assert_eq!(actual, expected);
    assert!(parse_properties(b"x=\\u00").is_err(), "accepted a truncated \\u escape");
}

#[test]
fn distribution_properties_follow_get_ide_system_properties() {
    let directory = tempfile::tempdir().unwrap();
    let home_macro = ide_home_macro();
    let idea_properties = directory.path().join("idea.properties");
    write_file(
        &idea_properties,
        "idea.config.path=${user.home}/config\nidea.platform.prefix=fromDistribution\n",
    );
    let vm_options = directory.path().join("vmoptions");
    write_file(
        &vm_options,
        "-Xmx2048m\n-Dsun.io.useCanonCaches=false\n-Dawt.toolkit.name=fromDistribution\n",
    );
    let product_info = directory.path().join("product-info.json");
    write_file(
        &product_info,
        &json!({"launch": [{
            "additionalJvmArguments": [
                format!("-Xbootclasspath/a:{home_macro}/lib/nio-fs.jar"),
                format!("-Djna.boot.library.path={home_macro}/lib/jna"),
            ],
            "customCommands": [{
                "commands": ["ijLight"],
                "mainClass": "com.example.LightMain",
                "additionalJvmArguments": [format!("-Dlight={home_macro}/light"), "-Xss4m"],
            }],
        }]})
        .to_string(),
    );
    let info = ProductInfo::read_file(&product_info).unwrap();
    let home = "/ws/ide_home";
    let properties = properties_of_files(&idea_properties, &vm_options, "/ws/ide_home/bin/idea.vmoptions", home, &info).unwrap();
    for (key, value) in [
        ("idea.config.path", "${user.home}/config"),
        ("sun.io.useCanonCaches", "false"),
        ("jb.vmOptionsFile", "/ws/ide_home/bin/idea.vmoptions"),
        ("jna.boot.library.path", "/ws/ide_home/lib/jna"),
        ("awt.toolkit.name", "fromDistribution"),
        ("idea.platform.prefix", "fromDistribution"),
    ] {
        assert_eq!(properties.get(key).map(String::as_str), Some(value), "{key}");
    }
    assert!(
        !properties.contains_key("Xmx2048m"),
        "a vmoptions line without -D became a property"
    );
    let (main_class, command) = custom_command(home, &info, "ijLight").unwrap();
    assert_eq!(main_class, "com.example.LightMain");
    assert_eq!(
        command.into_iter().collect::<Vec<_>>(),
        [("light".to_owned(), "/ws/ide_home/light".to_owned())]
    );
    assert!(
        custom_command(home, &info, "other").is_err(),
        "found a custom command that the distribution does not declare"
    );
}
