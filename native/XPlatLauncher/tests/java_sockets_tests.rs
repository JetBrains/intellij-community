// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#![cfg(target_os = "windows")]

use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::sync::OnceLock;
use std::time::{Duration, Instant};

use tempfile::TempDir;

struct Fixture {
    _root: TempDir,
    jar: PathBuf,
    jbr: PathBuf,
}

fn fixture() -> &'static Fixture {
    static FIXTURE: OnceLock<Fixture> = OnceLock::new();
    FIXTURE.get_or_init(|| {
        let jdk = PathBuf::from(std::env::var_os("JAVA_SOCKETS_TEST_JDK").expect("Set JAVA_SOCKETS_TEST_JDK to a JDK 21 or newer"));
        let jbr = PathBuf::from(std::env::var_os("JAVA_SOCKETS_TEST_JBR").expect("Set JAVA_SOCKETS_TEST_JBR to the server's bundled JBR"));
        let root = tempfile::tempdir().unwrap();
        let source = root.path().join("MainImpl.java");
        fs::write(&source, r#"
package com.intellij.ls.server;
public class MainImpl {
  public static void main(String[] args) throws Exception {
    var record = java.nio.file.Path.of(args[0]);
    var directory = System.getProperty("jdk.net.unixdomain.tmpdir", "unset");
    java.nio.file.Files.writeString(record, directory);
    if (!args[1].equals("no-selector")) {
      try (var selector = java.nio.channels.Selector.open()) {
        java.nio.file.Files.writeString(record, directory + "\nselector=PASS");
      }
    }
    if (args[1].equals("wait")) {
      while (!java.nio.file.Files.exists(java.nio.file.Path.of(args[0] + ".stop"))) Thread.sleep(25);
    }
    if (args.length > 2) System.exit(Integer.parseInt(args[2]));
  }
}
"#).unwrap();
        let other = root.path().join("OtherMain.java");
        fs::write(&other, "public class OtherMain { public static void main(String[] args) throws Exception { com.intellij.ls.server.MainImpl.main(args); } }").unwrap();
        let classes = root.path().join("classes");
        fs::create_dir(&classes).unwrap();
        let compiled = Command::new(jdk.join("bin/javac.exe")).args(["--release", "21", "-d"])
            .arg(&classes).arg(&source).arg(other).output().unwrap();
        assert_success(&compiled, 0);
        let jar = root.path().join("probe.jar");
        let packed = Command::new(jdk.join("bin/jar.exe")).arg("cf").arg(&jar).arg("-C").arg(classes).arg(".").output().unwrap();
        assert_success(&packed, 0);
        Fixture { _root: root, jar, jbr }
    })
}

fn launcher(main_class: &str, options: &str) -> (TempDir, Command, PathBuf) {
    let fixture = fixture();
    let root = tempfile::tempdir().unwrap();
    fs::create_dir(root.path().join("bin")).unwrap();
    fs::create_dir(root.path().join("lib")).unwrap();
    fs::copy(&fixture.jar, root.path().join("lib/probe.jar")).unwrap();
    let binary = root.path().join("bin/xplat64.exe");
    fs::copy(env!("CARGO_BIN_EXE_xplat-launcher-win-con"), &binary).unwrap();
    let mut info: serde_json::Value = serde_json::from_str(include_str!("../resources/product_info_windows.json")).unwrap();
    info["launch"][0]["mainClass"] = main_class.into();
    info["launch"][0]["bootClassPathJarNames"] = serde_json::json!(["probe.jar"]);
    info["launch"][0]["customCommands"] = serde_json::json!([]);
    fs::write(root.path().join("product-info.json"), serde_json::to_vec(&info).unwrap()).unwrap();
    fs::write(root.path().join("bin/xplat64.exe.vmoptions"), options).unwrap();
    let record = root.path().join("record");
    let user_options = root.path().join("user.vmoptions");
    fs::write(&user_options, "").unwrap();
    let mut command = Command::new(binary);
    for name in [
        "IJ_JAVA_OPTIONS",
        "JAVA_TOOL_OPTIONS",
        "JDK_JAVA_OPTIONS",
        "_JAVA_OPTIONS",
        "XPLAT_VM_OPTIONS",
    ] {
        command.env_remove(name);
    }
    command
        .env("IJ_LAUNCHER_DEBUG", "1")
        .env("XPLAT_JDK", &fixture.jbr)
        .env("XPLAT_VM_OPTIONS", &user_options)
        .env("APPDATA", root.path().join("appdata"))
        .env("LOCALAPPDATA", root.path().join("localappdata"))
        .arg(&record);
    (root, command, record)
}

fn assert_success(output: &Output, status: i32) {
    assert_eq!(
        output.status.code(),
        Some(status),
        "stdout={}\nstderr={}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
}

fn generated_directory(output: &Output) -> PathBuf {
    let text = format!(
        "{}\n{}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    PathBuf::from(
        text.lines()
            .find_map(|line| line.split_once("Java socket directory: ").map(|(_, path)| path.trim()))
            .expect("The launcher logged no generated directory"),
    )
}

#[test]
#[ignore = "Requires JAVA_SOCKETS_TEST_JDK and JAVA_SOCKETS_TEST_JBR"]
fn returning_main_and_java_exit_remove_the_directory() {
    for status in [None, Some(7), Some(11)] {
        let (_root, mut command, record) = launcher("com.intellij.ls.server.MainImpl", "");
        command.arg("selector");
        if let Some(status) = status {
            command.arg(status.to_string());
        }
        let output = command.output().unwrap();
        assert_success(&output, status.unwrap_or(0));
        let record = fs::read_to_string(record).unwrap();
        assert!(record.ends_with("selector=PASS"), "{record}");
        assert!(!generated_directory(&output).exists());
    }
}

#[test]
#[ignore = "Requires JAVA_SOCKETS_TEST_JDK and JAVA_SOCKETS_TEST_JBR"]
fn explicit_options_and_other_main_classes_create_no_directory() {
    let explicit = tempfile::tempdir().unwrap();
    let option = format!("-Djdk.net.unixdomain.tmpdir={}", explicit.path().display());
    for variable in [
        None,
        Some("IJ_JAVA_OPTIONS"),
        Some("JAVA_TOOL_OPTIONS"),
        Some("JDK_JAVA_OPTIONS"),
        Some("_JAVA_OPTIONS"),
    ] {
        let (_root, mut command, record) = launcher("com.intellij.ls.server.MainImpl", if variable.is_none() { &option } else { "" });
        if let Some(variable) = variable {
            command.env(variable, format!("\"{option}\""));
        }
        let output = command.arg("no-selector").output().unwrap();
        assert_success(&output, 0);
        let text = format!(
            "{}{}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
        assert!(!text.contains("Java socket directory: "), "{text}");
        let record = fs::read_to_string(record).unwrap();
        if variable == Some("JDK_JAVA_OPTIONS") {
            assert_eq!(record, "unset");
        } else {
            assert_eq!(Path::new(&record), explicit.path());
        }
        assert!(explicit.path().is_dir());
    }
    let (_root, mut command, record) = launcher("OtherMain", "");
    let output = command.arg("no-selector").output().unwrap();
    assert_success(&output, 0);
    assert_eq!(fs::read_to_string(record).unwrap(), "unset");
}

#[test]
#[ignore = "Requires JAVA_SOCKETS_TEST_JDK and JAVA_SOCKETS_TEST_JBR"]
fn jvm_creation_failure_removes_the_directory() {
    let (_root, mut command, record) = launcher("com.intellij.ls.server.MainImpl", "-Xmxinvalid");
    let output = command.arg("selector").output().unwrap();
    assert!(!output.status.success());
    assert!(!record.exists());
    assert!(!generated_directory(&output).exists());
}

#[test]
#[ignore = "Requires JAVA_SOCKETS_TEST_JDK and JAVA_SOCKETS_TEST_JBR"]
fn a_property_inside_another_option_does_not_suppress_the_repair() {
    let (_root, mut command, record) = launcher("com.intellij.ls.server.MainImpl", "");
    let output = command
        .env("IJ_JAVA_OPTIONS", r"-Dother=x\ -Djdk.net.unixdomain.tmpdir=C:/custom")
        .arg("selector")
        .output()
        .unwrap();
    assert_success(&output, 0);
    let text = fs::read_to_string(record).unwrap();
    assert!(text.ends_with("selector=PASS"), "{text}");
    assert!(!generated_directory(&output).exists());
}

#[test]
#[ignore = "Requires JAVA_SOCKETS_TEST_JDK and JAVA_SOCKETS_TEST_JBR"]
fn the_directory_stays_private_until_the_server_exits() {
    let (_root, mut command, record) = launcher("com.intellij.ls.server.MainImpl", "");
    let mut child = command.arg("wait").spawn().unwrap();
    let deadline = Instant::now() + Duration::from_secs(30);
    let directory = loop {
        if let Ok(text) = fs::read_to_string(&record)
            && text.ends_with("selector=PASS")
        {
            break PathBuf::from(text.lines().next().unwrap());
        }
        if Instant::now() >= deadline || child.try_wait().unwrap().is_some() {
            let _ = child.kill();
            panic!("The probe did not become ready");
        }
        std::thread::sleep(Duration::from_millis(25));
    };
    assert!(directory.is_dir());
    let acl = Command::new("powershell.exe").args(["-NoProfile", "-NonInteractive", "-Command",
        "$a=Get-Acl -LiteralPath $env:SOCKET_TEST_DIRECTORY; if (!$a.AreAccessRulesProtected) {exit 1}; $r=@($a.Access); if ($r.Count -ne 1 -or $r[0].IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value -ne [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value) {exit 2}"])
        .env_remove("PSModulePath")
        .env("SOCKET_TEST_DIRECTORY", &directory).output().unwrap();
    fs::write(record.with_file_name("record.stop"), "stop").unwrap();
    assert_eq!(child.wait().unwrap().code(), Some(0));
    assert_success(&acl, 0);
    assert!(!directory.exists());
}
