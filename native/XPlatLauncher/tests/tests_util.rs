use std::{fs, io};
use std::fs::{create_dir, File};
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

pub fn gradle_command_wrapper(gradle_command: &str) {
    let command_to_ecxecute = Command::new("./gradlew")
        .arg(gradle_command)
        .current_dir("resources/TestProject")
        .output()
        .expect(format!("Failed to execute gradlew :{gradle_command}").as_str());

    command_handler(&command_to_ecxecute);
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

pub fn get_java_parameter_from_output(java_output: &str, parameter: String) -> String {
    let split = java_output.split("\n");
    for s in split {
        if s.starts_with(&parameter) {
            return s.to_string();
        }
    }

    return "".to_string();
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
    // │   └── xplat_launcher
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
        .join("xplat_launcher");
    assert!(launcher.exists());

    fs::copy(launcher, "bin/xplat_launcher").expect("Failed to copy launcher");
    fs::copy(jar_absolute_path, "lib/app.jar").expect("Failed to move jar");
    std::os::unix::fs::symlink(jbr_absolute_path, "jbr").expect("Failed to create symlink for jbr");
    File::create("bin/idea64.vmoptions").expect("Failed to create idea.vmoptions");
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
    //     │   └── xplat_launcher
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
        .join("xplat_launcher");
    assert!(launcher.exists());

    fs::copy(launcher, "Contents/bin/xplat_launcher").expect("Failed to copy launcher");
    fs::copy(jar_absolute_path, "Contents/lib/app.jar").expect("Failed to move jar");
    std::os::unix::fs::symlink(jbr_absolute_path, "Contents/jbr").expect("Failed to create symlink for jbr");
    File::create("Contents/bin/idea.vmoptions").expect("Failed to create idea.vmoptions");
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
    // │   └── xplat_launcher
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
        .join("xplat_launcher");
    assert!(launcher.exists());

    fs::copy(launcher, "bin/xplat_launcher").expect("Failed to copy launcher");
    fs::copy(jar_absolute_path, "lib/app.jar").expect("Failed to move jar");
    std::os::windows::fs::symlink_dir(jbr_absolute_path, "jbr").expect("Failed to create symlink for jbr");
    File::create("bin/ideax64.exe.vmoptions").expect("Failed to create idea.vmoptions");
}
