use std::{env, fs, io};
use std::fs::{create_dir, File};
use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::sync::Once;
use std::time::SystemTime;

static INIT: Once = Once::new();

// @BeforeAll
pub fn initialize() {
    INIT.call_once(|| {
        let project_root = env::current_dir().expect("Failed to get project root");

        // gradle_command_wrapper("clean");
        gradle_command_wrapper("fatJar");

        let gradle_jvm = project_root
            .join("resources")
            .join("TestProject")
            .join("gradle-jvm");

        // TODO: remove after wrapper with https://github.com/mfilippov/gradle-jvm-wrapper/pull/31
        let java_dir_prefix = match env::consts::OS {
            "windows" => { "jdk" }
            _ => { "jbrsdk" }
        };

        // jbrsdk-17.0.3-osx-x64-b469.37-f87880
        let jbrsdk_gradle_parent = get_child_dir(&gradle_jvm, java_dir_prefix).expect("");

        // jbrsdk-17.0.3-x64-b469
        let jbrsdk_root = get_child_dir(&jbrsdk_gradle_parent, java_dir_prefix).expect("");

        let os = env::consts::OS;
        let kek = format!("resources/product_info_{os}.json");
        let product_info_path = Path::new(&kek);
        let jar_path = Path::new("resources/TestProject/build/libs/app.jar");
        let jar_absolute_path = env::current_dir().unwrap().join(jar_path);
        let product_info_absolute_path = env::current_dir().unwrap().join(product_info_path);

        // create tmp dir
        let dir_name = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .expect("Failed to compute current unix timestamp")
            .as_secs();
        let test_dir_path = env::temp_dir().join(dir_name.to_string());
        create_dir(&test_dir_path).expect("Failed to create temp dir");

        env::set_current_dir(test_dir_path).expect("Failed to set current dir");

        layout_into_test_dir(
            &project_root,
            jbrsdk_root,
            jar_absolute_path,
            product_info_absolute_path,
        );
    });
}

pub fn gradle_command_wrapper(gradle_command: &str) {
    let executable_name = get_gradlew_executable_name();
    let executable = PathBuf::from("./resources/TestProject")
        .join(executable_name);

    assert!(executable.exists());

    let gradlew = executable.canonicalize().expect("Failed to get canonical path to gradlew");
    let command_to_execute = Command::new(gradlew)
        .arg(gradle_command)
        .current_dir("resources/TestProject")
        .output()
        .expect(format!("Failed to execute gradlew :{gradle_command}").as_str());

    command_handler(&command_to_execute);
}

#[cfg(target_os = "windows")]
fn get_gradlew_executable_name() -> String {
    "gradlew.bat".to_string()
}

#[cfg(any(target_os = "macos", target_os = "linux"))]
fn get_gradlew_executable_name() -> String {
    "gradlew".to_string()
}

fn command_handler(command: &Output) {
    let exit_status = command.status;
    let stdout = String::from_utf8_lossy(&command.stdout);
    let stderr = String::from_utf8_lossy(&command.stderr);

    if !exit_status.success() {
        let message = format!("Command didn't succeed,\n exit code: {exit_status},\n stdout: {stdout},\n stderr: {stderr}");
        panic!("{message}")
    }
}

pub fn get_child_dir(parent: &Path, prefix: &str) -> io::Result<PathBuf> {
    let read_dir = fs::read_dir(parent)?;

    for dir_entry in read_dir {
        let dir_entry_ok = dir_entry?;
        if dir_entry_ok
            .file_name()
            .to_string_lossy()
            .starts_with(prefix)
        {
            return Ok(dir_entry_ok.path());
        }
    }

    return Err(io::Error::new(
        io::ErrorKind::NotFound,
        "Child dir not found",
    ));
}

#[cfg(target_os = "linux")]
pub fn layout_into_test_dir(
    project_root: &Path,
    jbr_absolute_path: PathBuf,
    jar_absolute_path: PathBuf,
    product_info_absolute_path: PathBuf,
) {
    // linux:
    // .
    // ├── bin/
    // │   └── xplat-launcher
    // │   └── idea64.vmoptions
    // ├── lib/
    // │   └── app.jar
    // ├── jbr/
    // └── product-info.json
    create_dir("lib").expect("Failed to create lib dir");
    create_dir("bin").expect("Failed to create bin dir");

    fs::copy(product_info_absolute_path, "product-info.json")
        .expect("Failed to move product_info.json");

    let launcher = project_root
        .join("target")
        .join("debug")
        .join("xplat-launcher");
    assert!(launcher.exists());

    fs::copy(launcher, "bin/xplat-launcher").expect("Failed to copy launcher");
    fs::copy(jar_absolute_path, "lib/app.jar").expect("Failed to move jar");
    std::os::unix::fs::symlink(jbr_absolute_path, "jbr").expect("Failed to create symlink for jbr");
    File::create("bin/idea64.vmoptions").expect("Failed to create idea.vmoptions");
    File::create("lib/test.jar").expect("Failed to create test.jar file for classpath test");
}

#[cfg(target_os = "macos")]
pub fn layout_into_test_dir(
    project_root: &Path,
    jbr_absolute_path: PathBuf,
    jar_absolute_path: PathBuf,
    product_info_absolute_path: PathBuf,
) {
    // macos:
    // .
    // └── Contents
    //     ├── bin/
    //     │   └── xplat-launcher
    //     │   └── idea.vmoptions
    //     ├── Resources/
    //     │   └── product-info.json
    //     ├── lib/
    //     │   └── app.jar
    //     └── jbr/
    create_dir("Contents").expect("Failed to create contents dir");
    create_dir("Contents/Resources").expect("Failed to create resources dir");
    create_dir("Contents/lib").expect("Failed to create lib dir");
    create_dir("Contents/bin").expect("Failed to create bin dir");

    fs::copy(
        product_info_absolute_path,
        "Contents/Resources/product-info.json",
    )
        .expect("Failed to move product_info.json");

    let launcher = project_root
        .join("target")
        .join("debug")
        .join("xplat-launcher");
    assert!(launcher.exists());

    fs::copy(launcher, "Contents/bin/xplat-launcher").expect("Failed to copy launcher");
    fs::copy(jar_absolute_path, "Contents/lib/app.jar").expect("Failed to move jar");
    std::os::unix::fs::symlink(jbr_absolute_path, "Contents/jbr").expect("Failed to create symlink for jbr");
    File::create("Contents/bin/idea.vmoptions").expect("Failed to create idea.vmoptions");
    File::create("Contents/lib/test.jar").expect("Failed to create test.jar file for classpath test");
}

#[cfg(target_os = "windows")]
pub fn layout_into_test_dir(
    project_root: &Path,
    jbr_absolute_path: PathBuf,
    jar_absolute_path: PathBuf,
    product_info_absolute_path: PathBuf,
) {
    // windows:
    // .
    // ├── bin/
    // │   └── xplat-launcher
    // │   └── idea64.exe.vmoptions
    // ├── lib/
    // │   └── app.jar
    // ├── jbr/
    // └── product-info.json

    create_dir("lib").expect("Failed to create lib dir");
    create_dir("bin").expect("Failed to create bin dir");

    fs::copy(product_info_absolute_path, "product-info.json")
        .expect("Failed to move product_info.json");

    let launcher = project_root
        .join("target")
        .join("debug")
        .join("xplat-launcher.exe");
    assert!(launcher.exists());

    fs::copy(launcher, "bin/xplat-launcher").expect("Failed to copy launcher");
    fs::copy(jar_absolute_path, "lib/app.jar").expect("Failed to move jar");
    junction::create(jbr_absolute_path, "jbr").expect("Failed to create junction for jbr");
    File::create("bin/idea64.exe.vmoptions").expect("Failed to create idea.vmoptions");
    File::create("lib/test.jar").expect("Failed to create test.jar file for classpath test");
}

pub fn resolve_test_dir() -> PathBuf {
    if cfg!(target_os = "macos") {
        env::current_dir().unwrap().join("Contents").join("bin")
    } else {
        env::current_dir().unwrap().join("bin")
    }
}