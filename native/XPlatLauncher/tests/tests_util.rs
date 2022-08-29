// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::{env, fs, io, thread, time};
use std::collections::HashMap;
use std::fs::{create_dir, File};
use std::io::BufReader;
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus, Output};
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Once;
use std::time::SystemTime;

static INIT: Once = Once::new();
static mut SHARED: Option<TestEnvironmentShared> = None;

pub struct TestEnvironment {
    pub launcher_path: PathBuf,
    pub working_dir: PathBuf
}

pub fn prepare_test_env() -> TestEnvironment {
    let result = match prepare_test_env_impl() {
        Ok(x) => x,
        Err(e) => {
            panic!("Failed to prepare test environment: {e:?}")
        }
    };

    result
}

fn prepare_test_env_impl() -> Result<TestEnvironment> {
    INIT.call_once(|| {
        let shared = init_test_environment_once().expect("Failed to init shared test environment");
        unsafe {
            SHARED = Some(shared)
        }
    });

    let shared = unsafe { SHARED.as_ref() }.expect("Shared test environment should have already been initialized");

    let os = env::consts::OS;
    let product_info_relative = format!("resources/product_info_{os}.json");
    let product_info_path = shared.project_root.join(product_info_relative);

    let test_number = shared.test_counter.fetch_add(1, Ordering::SeqCst);
    // create tmp dir
    let dir_name = shared.start_unix_timestamp_nanos + u128::from(test_number);
    let test_dir = env::temp_dir().join(dir_name.to_string());
    create_dir(&test_dir).context(format!("Failed to create temp directory: {test_dir:?}"))?;

    layout_launcher(
        &test_dir,
        &shared.project_root,
        &shared.jbrsdk_root,
        &shared.intellij_app_jar_source,
        &product_info_path,
    )?;

    let launcher_dir = resolve_launcher_dir(&test_dir);
    env::set_current_dir(&launcher_dir)?;

    let launcher_path = launcher_dir.join("xplat-launcher");

    let result = TestEnvironment {
        launcher_path,
        working_dir: test_dir
    };

    Ok(result)
}
pub fn init_test_environment_once() -> Result<TestEnvironmentShared> {
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
    let jbrsdk_gradle_parent = get_child_dir(&gradle_jvm, java_dir_prefix)?;

    // jbrsdk-17.0.3-x64-b469
    let jbrsdk_root = get_child_dir(&jbrsdk_gradle_parent, java_dir_prefix)?;

    let jar_path = Path::new("resources/TestProject/build/libs/app.jar");
    let intellij_app_jar_source = jar_path.canonicalize()?;

    let start_unix_timestamp_nanos = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)?
        .as_nanos();

    let test_counter = AtomicU32::new(0);
    let result = TestEnvironmentShared {
        project_root,
        jbrsdk_root,
        intellij_app_jar_source,
        start_unix_timestamp_nanos,
        test_counter
    };

    Ok(result)
}

pub struct TestEnvironmentShared {
    project_root: PathBuf,
    jbrsdk_root: PathBuf,
    intellij_app_jar_source: PathBuf,
    start_unix_timestamp_nanos: u128,
    test_counter: AtomicU32
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
pub fn layout_launcher(
    target_dir: &Path,
    project_root: &Path,
    jbr_absolute_path: &Path,
    intellij_main_mock_jar: &Path,
    product_info_absolute_path: &Path,
) -> Result<()> {
    // linux:
    // .
    // ├── bin/
    // │   └── xplat-launcher
    // │   └── idea64.vmoptions
    // ├── lib/
    // │   └── app.jar
    // ├── jbr/
    // └── product-info.json


    let launcher = project_root
        .join("target")
        .join("debug")
        .join("xplat-launcher");
    assert!(launcher.exists());

    layout_launcher_impl(
        target_dir,
        vec![ "lib", "bin" ],
        vec![
            "bin/idea64.vmoptions",
            "lib/test.jar"
        ],
        HashMap::from([
            (launcher.as_path(), "bin/xplat-launcher"),
            (intellij_main_mock_jar, "lib/app.jar"),
            (product_info_absolute_path, "product-info.json"),
        ]),
        HashMap::from([
            (jbr_absolute_path, "jbr")
        ])
    )?;

    Ok(())
}

#[cfg(target_os = "macos")]
pub fn layout_launcher(
    target_dir: &Path,
    project_root: &Path,
    jbr_absolute_path: &Path,
    intellij_main_mock_jar: &Path,
    product_info_absolute_path: &Path,
) -> Result<()> {
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

    let launcher = project_root
        .join("target")
        .join("debug")
        .join("xplat-launcher");
    assert!(launcher.exists());

    layout_launcher_impl(
        target_dir,
        vec![
            "Contents",
            "Contents/Resources",
            "Contents/lib",
            "Contents/bin"
        ],
        vec![
            "Contents/bin/idea.vmoptions",
            "Contents/lib/test.jar"
        ],
        HashMap::from([
            (launcher.as_path(), "Contents/bin/xplat-launcher"),
            (intellij_main_mock_jar, "Contents/lib/app.jar"),
            (product_info_absolute_path, "Contents/Resources/product-info.json"),
        ]),
        HashMap::from([
            (jbr_absolute_path, "Contents/jbr")
        ])
    )?;

    Ok(())
}

#[cfg(target_os = "windows")]
pub fn layout_launcher(
    target_dir: &Path,
    project_root: &Path,
    jbr_absolute_path: &Path,
    intellij_main_mock_jar: &Path,
    product_info_absolute_path: &Path,
) -> Result<()> {
    // windows:
    // .
    // ├── bin/
    // │   └── xplat-launcher
    // │   └── idea64.exe.vmoptions
    // ├── lib/
    // │   └── app.jar
    // ├── jbr/
    // └── product-info.json

    let launcher = project_root
        .join("target")
        .join("debug")
        .join("xplat-launcher.exe");
    assert!(launcher.exists());

    layout_launcher_impl(
        target_dir,
        vec![ "lib", "bin" ],
        vec![
            "bin/idea64.exe.vmoptions",
            "lib/test.jar"
        ],
        HashMap::from([
            (launcher.as_path(), "bin/xplat-launcher.exe"),
            (intellij_main_mock_jar, "lib/app.jar"),
            (product_info_absolute_path, "product-info.json"),
        ]),
        HashMap::from([
            (jbr_absolute_path, "jbr")
        ])
    )?;

    Ok(())
}

fn layout_launcher_impl(
    target_dir: &Path,
    create_dirs: Vec<&str>,
    create_files: Vec<&str>,
    copy_files: HashMap<&Path, &str>,
    symlink_dirs: HashMap<&Path, &str>
) -> Result<()> {
    for dir in create_dirs {
        let path = &target_dir.join(dir);
        create_dir(path).context(format!("Failed to create dir {path:?}"))?;
    }

    for file in create_files {
        let path = &target_dir.join(file);
        File::create(path).context(format!("Failed to create file {path:?}"))?;
    }

    for (source, target_relative) in copy_files {
        let target = &target_dir.join(target_relative);
        fs::copy(source, target).context(format!("Failed to copy from {source:?} to {target:?}"))?;
    }

    for (source, target_relative) in symlink_dirs {
        let target = &target_dir.join(target_relative);
        symlink(source, target)?;
    }

    Ok(())
}

#[cfg(any(target_os = "macos", target_os = "linux"))]
fn symlink(source: &Path, target: &Path) -> Result<()> {
    std::os::unix::fs::symlink(source, target)
        .context(format!("Failed to create symlink {target:?} pointing to {source:?}"))?;

    Ok(())
}

#[cfg(target_os = "windows")]
fn symlink(source: &Path, target: &Path) -> Result<()> {
    junction::create(source, target)
        .context(format!("Failed to create junction {target:?} pointing to {source:?}"))?;

    Ok(())
}

pub fn resolve_launcher_dir(test_dir: &Path) -> PathBuf {
    if cfg!(target_os = "macos") {
        test_dir.join("Contents").join("bin")
    } else {
        test_dir.join("bin")
    }
}

// TODO: test for additionalJvmArguments in product-info.json being set
// (e.g. "-Didea.vendor.name=JetBrains")

pub struct LauncherRunResult {
    pub exit_status: ExitStatus,
    pub dump: Option<IntellijMainDumpedLaunchParameters>
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct IntellijMainDumpedLaunchParameters {
    pub cmdArguments: Vec<String>,
    pub vmOptions: Vec<String>,
    pub environmentVariables: HashMap<String, String>,
    pub systemProperties: HashMap<String, String>
}

pub fn run_launcher(test: &TestEnvironment) -> LauncherRunResult {
    let result = match run_launcher_impl(test) {
        Ok(x) => x,
        Err(e) => {
            panic!("Failed to get launcher run result: {e:?}")
        }
    };

    result
}

fn run_launcher_impl(test: &TestEnvironment) -> Result<LauncherRunResult> {
    let output_file = test.working_dir.join("output.json");

    let mut launcher_process = Command::new(&test.launcher_path)
        .current_dir(&test.working_dir)
        .args(["--output", &output_file.to_string_lossy()])
        .env(xplat_launcher::DO_NOT_SHOW_ERROR_UI_ENV_VAR, "1")
        .spawn()
        .expect("Failed to spawn launcher process");

    let started = time::Instant::now();

    loop {
        let elapsed = time::Instant::now() - started;
        if elapsed > time::Duration::from_secs(60) {
            panic!("Launcher has been running for more than 60 seconds, terminating")
        }

        match launcher_process.try_wait() {
            Ok(opt) => match opt {
                None => {
                    println!("Waiting for launcher process to exit");
                }
                Some(es) => return Ok(LauncherRunResult {
                    exit_status: es,
                    dump: match es.success() {
                        true => Some(read_launcher_run_result(&output_file)?),
                        false => None
                    }
                }),
            },
            Err(e) => {
                Err(e)?
            }
        };

        thread::sleep(time::Duration::from_secs(1))
    }
}

fn read_launcher_run_result(path: &Path) -> Result<IntellijMainDumpedLaunchParameters> {
    let file = File::open(path)?;

    let reader = BufReader::new(file);
    let dump: IntellijMainDumpedLaunchParameters = serde_json::from_reader(reader)?;
    Ok(dump)
}