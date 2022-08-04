use crate::tests_util::{compile_java, download_java, package_jar, resolve_java_command};
use copy_dir;
use std::env::temp_dir;
use std::fs::{canonicalize, create_dir, File};
use std::path::{Path, PathBuf};
use std::sync::Once;
use std::time::SystemTime;
use std::{env, fs};

mod tests_util;

static INIT: Once = Once::new();

// struct JavaWrapper {
//     jbdsdrk_home: PathBuf
// }
//
// impl JavaWrapper {
//     fn resolve_java_command(&self, name: &str) -> Result<PathBuf> {
//
//     }
// }

// @BeforeAll
pub fn initialize() {
    //TODO: refactoring, add other OS
    INIT.call_once(|| {
        // download java and resolve absolute it's path for subsequent layout
        let jbrsdk = download_java();
        let mut jbr_absolute_path = env::current_dir().unwrap();
        jbr_absolute_path.push(jbrsdk);

        // TODO: I really don't like this solution
        let javac = resolve_java_command(jbrsdk, "javac");
        let jar = resolve_java_command(jbrsdk, "jar");

        // preparing files for subsequent layout
        compile_java(javac, "resources/Main.java");
        let jar = package_jar(jar, "com.intellij.idea.Main");
        let product_info = Path::new("resources/product_info.json");
        let mut jar_absolute_path = env::current_dir().unwrap();
        jar_absolute_path.push(jar);
        let mut product_info_absolute_path = env::current_dir().unwrap();
        product_info_absolute_path.push(product_info);

        // create tmp dir
        let dir_name = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .expect("Failed to compute current unix timestamp")
            .as_secs();
        let test_dir_path = temp_dir().join(dir_name.to_string());
        create_dir(&test_dir_path).expect("Failed to create temp dir");

        env::set_current_dir(test_dir_path).expect("Failed to set current dir");

        layout_into_test_dir(
            jbr_absolute_path,
            jar_absolute_path,
            product_info_absolute_path,
        );
    });
}

#[cfg(target_os = "macos")]
fn layout_into_test_dir(
    jbr_absolute_path: PathBuf,
    jar_absolute_path: PathBuf,
    product_info_absolute_path: PathBuf,
) {
    // macos:
    // .
    // └── Contents
    //     ├── bin/
    //     │   └── xplat_launcher
    //     │   └── idea.vmoptions
    //     ├── Resources/
    //     │   └── product-info.json
    //     ├── lib/
    //     │   └── app.jar
    //     ├── jbr/  <-- can be symlinked to java we've already downloaded for now
    create_dir("Contents").expect("Failed to create contents dir");
    create_dir("Contents/Resources").expect("Failed to create resources dir");
    create_dir("Contents/lib").expect("Failed to create lib dir");
    create_dir("Contents/bin").expect("Failed to create bin dir");

    fs::copy(
        product_info_absolute_path,
        "Contents/Resources/product-info.json",
    )
    .expect("Failed to move product_info.json");
    fs::copy(jar_absolute_path, "Contents/lib/app.jar").expect("Failed to move jar");
    copy_dir::copy_dir(jbr_absolute_path, "Contents/jbr").expect("Filed to copy jbr");
    File::create("Contents/bin/idea.vmoptions").expect("Failed to create idea.vmoptions");
}

#[cfg(test)]
mod tests {
    use crate::initialize;
    use crate::tests_util::{get_java_parameter_from_output, package_jar, run_java};
    use std::env::{current_dir, set_current_dir};
    use xplat_launcher::main_lib;

    #[test]
    fn startup_test() {
        initialize();
        let java_output = run_java("Hello_test");
        let vmoptions = get_java_parameter_from_output(&java_output, "VMoptions".to_string());
        let class_path = get_java_parameter_from_output(&java_output, "Class path".to_string());
        assert_eq!(vmoptions, "VMoptions: []");
        assert_eq!(class_path, "Class path: .");
    }

    #[test]
    #[cfg(target_os = "macos")]
    fn test_test() {
        initialize();
        let test_dir = current_dir().unwrap()
            .join("Contents")
            .join("bin");

        set_current_dir(test_dir).unwrap();
        main_lib();
        assert_eq!(2, 2);
    }
}
