use std::env::current_dir;
use jni_sys::jvalue;
use std::fs::{canonicalize, create_dir};
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

#[cfg(any(target_os = "linux", target_os = "macos"))]
pub fn download_java() -> PathBuf {
    let download_link = if cfg!(target_os = "linux") {
        "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.3-linux-x64-b469.37.tar.gz"
    } else if cfg!(target_os = "macos") {
        if cfg!(target_arch = "x86_64") {
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.3-osx-x64-b469.37.tar.gz"
        } else
        {
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.3-osx-aarch64-b469.37.tar.gz"
        }
    } else {
        todo!("")
    };

    let jbr_archive = "jbr.tar.gz";

    if !PathBuf::from(jbr_archive).exists() {
        let install_java_command = Command::new("curl")
            .args([
                "-fsSL",
                "-o",
                jbr_archive,
                download_link])
            .output()
            .expect("failed to download Java");

        command_handler(&install_java_command);
    }

    let jbr_dir = unpack_jbr_tar(jbr_archive);
    return jbr_dir;
}

#[cfg(target_os = "windows")]
pub fn download_java() -> & 'static Path {
    let jbr_archive = "jbr.tar.gz";
    let mut current_dir_name = current_dir().unwrap().into_os_string().into_string().unwrap();
    let mut jbr_file = Path::new(&*current_dir_name);
    jbr_file.join(jbr_archive);

    let download_java_command = Command::new("powershell.exe")
        .args([
            "Invoke-WebRequest",
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.3-windows-x64-b469.37.tar.gz",
            "-OutFile ",
            jbr_file.to_str().unwrap(),
        ])
        .output()
        .expect("failed to download Java");

    command_handler(&download_java_command);

    let jbr_dir = unpack_jbr_tar(jbr_archive);
    return jbr_dir;
}

#[cfg(any(target_os = "linux", target_os = "macos"))]
fn unpack_jbr_tar(archive: &str) -> PathBuf {
    create_dir("jbr").expect("Failed to create jbr dir");
    let jbrsdk_dir = PathBuf::from("jbr");
    let archive = Command::new("tar")
        .args(["-C", "jbr", "-xzvf", archive, "--strip-components", "1"])
        .output()
        .expect("Failed to unpack tar archive");

    command_handler(&archive);

    return jbrsdk_dir;
}

#[cfg(target_os = "linux")]
pub fn resolve_java_command(jbrsdk_dir: &Path, java_command: &str) -> PathBuf {
    return jbrsdk_dir
        .join("bin")
        .join(java_command);
}

#[cfg(target_os = "macos")]
pub fn resolve_java_command<P: AsRef<Path>>(jbrsdk_dir: P, java_command: &str) -> PathBuf {
    return jbrsdk_dir.as_ref()
        .join("Contents")
        .join("Home")
        .join("bin")
        .join(java_command);
}

#[cfg(target_os = "windows")]
pub fn resolve_java_command(jbrsdk_dir: &Path, java_command: &str) -> PathBuf {
    let windiws_java_command = java_command.to_string() + ".exe";
    return jbrsdk_dir
        .join("bin")
        .join(windiws_java_command);
}

pub fn compile_java(javac: PathBuf, java_name: &str) {
    create_dir("resources/classes").expect("Failed to create classes dir");

    let compile_command = Command::new(javac)
        .arg(java_name)
        .arg("-d")
        .arg("resources/classes")
        .output()
        .expect("failed to compile test class");

    command_handler(&compile_command);
}

pub fn package_jar(jar: PathBuf, package: &str) -> &Path {
    let jar_command = Command::new(jar)
        .arg("-cfe")
        .arg("app.jar")
        .arg(package)
        .arg("-C")
        .arg("resources/classes")
        .arg(".")
        .output()
        .expect("failed to build jar");

    command_handler(&jar_command);

    let jar_path = Path::new("app.jar");
    return jar_path;
}

pub fn run_java(class_name: &str) -> String {
    let run_java_command = Command::new("java")
        .arg(class_name)
        .current_dir("resources")
        .output()
        .expect("failed to run java class");

    let exit_status = run_java_command.status;
    let stdout = String::from_utf8_lossy(&run_java_command.stdout);

    command_handler(&run_java_command);
    if !exit_status.success() {
        return "".to_string();
    }
    return stdout.to_string();
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
