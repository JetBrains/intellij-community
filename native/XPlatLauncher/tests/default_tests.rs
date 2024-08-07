// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pub mod utils;

#[cfg(test)]
mod tests {
    use std::{env, fs};
    use std::collections::HashMap;
    use std::path::PathBuf;
    use xplat_launcher::jvm_property;
    use crate::utils::*;

    #[test]
    fn correct_launcher_startup_test() {
        run_launcher(LauncherRunSpec::standard().assert_status());
    }

    #[test]
    fn classpath_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        classpath_test_impl(&test);
    }

    #[test]
    fn classpath_test_on_unicode_path() {
        let suffix = "δοκιμή-परीक्षा-시험";
        let test = prepare_custom_test_env(LauncherLocation::Standard, Some(suffix), true);

        #[cfg(target_os = "windows")]
        {
            let result = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump());
            if result.exit_status.success() {
                let dump = result.dump();
                let classpath = &dump.systemProperties["java.class.path"];
                assert!(classpath.contains("app.jar"), "app.jar is not present in classpath: {}", classpath);
                let os_specific_jar = format!("boot-{}.jar", env::consts::OS);
                assert!(classpath.contains(&os_specific_jar), "{} is not present in classpath: {}", os_specific_jar, classpath);
            } else {
                assert_startup_error(&result, "Cannot convert VM option string");
            }
        }

        #[cfg(not(target_os = "windows"))]
        {
            classpath_test_impl(&test);
        }
    }

    #[test]
    #[cfg(target_os = "windows")]
    fn classpath_test_on_unc() {
        let test_orig = prepare_test_env(LauncherLocation::Standard); // to prevent directories from disappearing
        let test = test_orig.to_unc();
        classpath_test_impl(&test);
    }

    #[test]
    #[cfg(target_os = "windows")]
    fn classpath_test_on_ns_prefixed_path() {
        let test_orig = prepare_test_env(LauncherLocation::Standard); // to prevent directories from disappearing
        let test = test_orig.to_ns_prefix();
        classpath_test_impl(&test);
    }

    #[test]
    #[cfg(target_os = "windows")]
    fn classpath_test_on_acp() {
        use windows::Win32::Globalization::{CP_ACP, MB_ERR_INVALID_CHARS, MultiByteToWideChar};

        let acp_chars = [0xc0, 0xc1, 0xc2, 0xc3];
        let mut ucs_chars = vec![0u16; 4];
        let ucs_len = unsafe { MultiByteToWideChar(CP_ACP, MB_ERR_INVALID_CHARS, &acp_chars, Some(&mut ucs_chars)) };
        assert_eq!(ucs_len, 4, "MultiByteToWideChar={} err={}", ucs_len, std::io::Error::last_os_error());
        let utf_str = String::from_utf16(&ucs_chars).unwrap();

        let test = prepare_custom_test_env(LauncherLocation::Standard, Some(&utf_str), true);
        classpath_test_impl(&test);
    }

    fn classpath_test_impl(test: &TestEnvironment) {
        let dump = run_launcher_ext(test, LauncherRunSpec::standard().with_dump().assert_status()).dump();
        let classpath = &dump.systemProperties["java.class.path"];
        assert!(classpath.contains("app.jar"), "app.jar is not present in classpath: {}", classpath);
        let os_specific_jar = format!("boot-{}.jar", env::consts::OS);
        assert!(classpath.contains(&os_specific_jar), "{} is not present in classpath: {}", os_specific_jar, classpath);
    }

    fn assert_startup_error(result: &LauncherRunResult, message: &str) {
        let header = "Cannot start the IDE";
        let header_present = result.stderr.find(header);
        assert!(header_present.is_some(), "Error header ('{}') is missing: {:?}", header, result);
        let message_present = result.stderr.find(message);
        assert!(message_present.is_some(), "JVM error message ('{}') is missing: {:?}", message, result);
        assert!(header_present.unwrap() < message_present.unwrap(), "JVM error message wasn't captured: {:?}", result);
    }

    #[test]
    fn standard_vm_options_loading_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let vm_options_name = if cfg!(target_os = "windows") { "xplat64.exe.vmoptions" }
            else if cfg!(target_os = "macos") { "xplat.vmoptions" }
            else { "xplat64.vmoptions" };
        let vm_options_file = test.dist_root.join("bin").join(vm_options_name);

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        // `bin/*.vmoptions`
        assert_vm_option_presence(&dump, "-Xmx256m");
        assert_vm_option_presence(&dump, "-XX:+UseG1GC");
        assert_vm_option_presence(&dump, "-Dsun.io.useCanonCaches=false");

        // `product-info.json`
        assert_vm_option_presence(&dump, "-Didea.vendor.name=JetBrains");
        assert_vm_option_presence(&dump, "-Didea.paths.selector=XPlatLauncherTest");

        // options injected by the launcher
        let vm_option = dump.vmOptions.iter().find(|s| s.starts_with("-Djb.vmOptionsFile="))
            .unwrap_or_else(|| panic!("'-Djb.vmOptionsFile=' is not in {:?}", dump.vmOptions));
        let path = PathBuf::from(vm_option.split_once('=').unwrap().1);
        assert_eq!(vm_options_file.canonicalize().unwrap(), path.canonicalize().unwrap());

        // hardcoded VM options
        assert_vm_option_presence(&dump, "-Dide.native.launcher=true");

        dump.vmOptions.iter().find(|s| s.starts_with("-XX:ErrorFile="))
            .unwrap_or_else(|| panic!("'-XX:ErrorFile=' is not in {:?}", dump.vmOptions));
    }

    #[test]
    #[cfg(target_os = "windows")]
    fn cef_sandbox_vm_options_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        assert_vm_option_presence(&dump, format!("-Djcef.sandbox.cefVersion={}", env!("CEF_VERSION")).as_ref());
        dump.vmOptions.iter().find(|s| s.starts_with("-Djcef.sandbox.ptr="))
            .unwrap_or_else(|| panic!("'-Djcef.sandbox.ptr=' is not in {:?}", dump.vmOptions));
    }

    #[test]
    fn path_macro_expansion_test() {
        let test = prepare_test_env(LauncherLocation::Standard);

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        let vm_option = dump.vmOptions.iter().find(|s| s.starts_with("-Dpath.macro.test="))
            .unwrap_or_else(|| panic!("'-Dpath.macro.test=' is not in {:?}", dump.vmOptions));
        let path = PathBuf::from(vm_option.split_once('=').unwrap().1);
        assert_eq!(test.dist_root.canonicalize().unwrap(), path.canonicalize().unwrap());
    }

    #[test]
    fn missing_standard_vm_options_failure_test() {
        let test = prepare_test_env(LauncherLocation::Standard);

        let bin_dir = test.dist_root.join("bin");
        for entry in fs::read_dir(&bin_dir).unwrap_or_else(|_| panic!("Cannot list: {:?}", bin_dir)).flatten() {
            if entry.file_name().to_str().unwrap().ends_with(".vmoptions") {
                fs::remove_file(entry.path()).unwrap_or_else(|_| panic!("Cannot delete: {:?}", entry.path()));
                break;
            }
        }

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump());
        assert!(!result.exit_status.success(), "Expected to fail: {:?}", result);
    }

    #[test]
    fn product_env_vm_options_loading_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let temp_file = test.create_temp_file("_product_env.vm_options", "-Done.user.option=whatever\n");
        let env = HashMap::from([("XPLAT_VM_OPTIONS", temp_file.to_str().unwrap())]);

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().with_env(&env).assert_status()).dump();

        assert_vm_option_presence(&dump, "-Done.user.option=whatever");
        assert_vm_option_presence(&dump, "-XX:+UseG1GC");
        assert_vm_option_presence(&dump, "-Dsun.io.useCanonCaches=false");
        assert_vm_option_presence(&dump, "-Didea.vendor.name=JetBrains");
        assert_vm_option_presence(&dump, &jvm_property!("jb.vmOptionsFile", temp_file.to_str().unwrap()));
    }

    #[test]
    fn product_env_properties_loading_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let temp_file = test.create_temp_file("_product_env.properties", "one.user.property=whatever\n");
        let env = HashMap::from([("XPLAT_PROPERTIES", temp_file.to_str().unwrap())]);

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().with_env(&env).assert_status()).dump();

        assert_vm_option_presence(&dump, "-Xmx256m");
        assert_vm_option_presence(&dump, "-XX:+UseG1GC");
        assert_vm_option_presence(&dump, "-Dsun.io.useCanonCaches=false");
        assert_vm_option_presence(&dump, &jvm_property!("idea.properties.file", temp_file.to_str().unwrap()));
    }

    #[test]
    fn toolbox_vm_options_loading_test() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        let vm_options_file = test.create_toolbox_vm_options("-Done.user.option=whatever\n");

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        assert_vm_option_presence(&dump, "-Done.user.option=whatever");
        assert_vm_option_presence(&dump, "-Didea.vendor.name=JetBrains");
        assert_vm_option_presence(&dump, &jvm_property!("jb.vmOptionsFile", vm_options_file.to_str().unwrap()));
    }

    #[test]
    fn vm_options_filtering_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let temp_file = test.create_temp_file("_product_env.vm_options", "# a comment\n \n-Xmx256m \n");
        let env = HashMap::from([("XPLAT_VM_OPTIONS", temp_file.to_str().unwrap())]);

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().with_env(&env).assert_status()).dump();

        assert_vm_option_presence(&dump, "-Xmx256m");

        assert_vm_option_absence(&dump, "# a comment");
        assert_vm_option_absence(&dump, "");
    }

    #[test]
    fn vm_options_gc_overriding_test() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        test.create_toolbox_vm_options("-Xmx512m\n-XX:+UseZGC\n-Dsun.io.useCanonCaches=true\n");

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        assert_eq!(dump.systemProperties["__MAX_HEAP"], "512");
        assert_eq!(dump.systemProperties["__GC"], "ZGC");
        assert_eq!(dump.systemProperties["sun.io.useCanonCaches"], "true");
    }

    #[test]
    fn vm_options_mx_overriding_test() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        test.create_toolbox_vm_options("-XX:MaxRAMPercentage=50\n");

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        assert_ne!(dump.systemProperties["__MAX_HEAP"], "256");
    }

    #[test]
    fn corrupted_vm_options_test() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        test.create_toolbox_vm_options("\0\0\0\0-Xmx512m\n");

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        assert_eq!(dump.systemProperties["jb.vmOptionsFile.corrupted"], "true");
    }

    #[test]
    fn arguments_test() {
        let args = &["arguments-test-123"];
        let dump = run_launcher(LauncherRunSpec::standard().with_dump().with_args(args).assert_status()).dump();

        assert_eq!(&dump.cmdArguments[0], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[1], "--output");
        assert_eq!(&dump.cmdArguments[3], args[0]);
    }

    #[test]
    fn selecting_custom_launch_info() {
        let result = run_launcher(LauncherRunSpec::standard().with_args(&["custom-command"]).assert_status());
        assert!(result.stdout.contains("Custom command: product.property=product.value, custom.property=null"), "Custom system property is not set: {:?}", result);
    }

    #[test]
    fn selecting_product_jdk_env_runtime_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let expected_rt = test.create_jbr_link("_prod_jdk_jbr");
        let env = HashMap::from([("XPLAT_JDK", expected_rt.to_str().unwrap())]);

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().with_env(&env).assert_status());
        test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn selecting_bundled_runtime_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let expected_rt = test.dist_root.join("jbr");

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().assert_status());
        test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn selecting_jdk_home_env_runtime_test() {
        let test = prepare_custom_test_env(LauncherLocation::Standard, None, false);
        let expected_rt = test.create_jbr_link("_jdk_home_jbr");
        let env = HashMap::from([("JDK_HOME", expected_rt.to_str().unwrap())]);

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().with_env(&env).assert_status());
        test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn selecting_java_home_env_runtime_test() {
        let test = prepare_custom_test_env(LauncherLocation::Standard, None, false);
        let expected_rt = test.create_jbr_link("_java_home_jbr");
        let env = HashMap::from([("JAVA_HOME", expected_rt.to_str().unwrap())]);

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().with_env(&env).assert_status());
        test_runtime_selection(result, expected_rt);
    }

    #[test]
    #[cfg(target_family = "unix")]
    fn async_profiler_loading() {
        let result = run_launcher(LauncherRunSpec::standard().with_args(&["async-profiler"]).assert_status());
        assert!(result.stdout.contains("version="), "Profiler version is missing from the output: {:?}", result);
    }

    #[test]
    fn exit_code_passing() {
        let result = run_launcher(LauncherRunSpec::standard().with_args(&["exit-code", "42"]));
        assert_eq!(result.exit_status.code(), Some(42), "The exit code of the launcher is unexpected: {:?}", result);
    }

    #[test]
    fn exception_handling() {
        let result = run_launcher(LauncherRunSpec::standard().with_args(&["exception"]));

        assert!(!result.exit_status.success(), "Expected to fail: {:?}", result);

        let exception = "java.lang.UnsupportedOperationException: aw, snap";
        assert!(result.stderr.contains(exception), "Exception message ('{}') is missing: {:?}", exception, result);
        assert!(result.stderr.contains("at com.intellij.idea.TestMain.exception"), "Stacktrace is missing: {:?}", result);
    }

    #[test]
    fn reporting_vm_creation_failures() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        test.create_toolbox_vm_options("-XX:+UseG1GC\n-XX:+UseZGC\n");

        let result = run_launcher_ext(&test, &LauncherRunSpec::standard());
        assert!(!result.exit_status.success(), "Expected to fail:{:?}", result);
        assert_startup_error(&result, "Conflicting collector combinations in option list");
    }

    #[test]
    fn reporting_vm_creation_panics() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        test.create_toolbox_vm_options("-Xms2g\n-Xmx1g\n");

        let result = run_launcher_ext(&test, &LauncherRunSpec::standard());
        assert!(!result.exit_status.success(), "Expected to fail:{:?}", result);
        assert_startup_error(&result, "Initial heap size set to a larger value than the maximum heap size");
    }

    #[test]
    #[cfg(not(all(target_os = "windows", target_arch = "aarch64")))]
    fn crash_log_creation() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        let crash_log_path = test.project_dir.join("_jvm_error.log");
        test.create_toolbox_vm_options(&format!("-XX:ErrorFile={}", crash_log_path.display()));

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().with_args(&["sigsegv"]));

        assert!(!result.exit_status.success(), "Expected to fail: {:?}", result);
        assert!(crash_log_path.exists(), "No crash log at {:?}: {:?}", crash_log_path, result);

        let marker = "# A fatal error has been detected by the Java Runtime Environment:";
        let content = fs::read_to_string(&crash_log_path).unwrap_or_else(|_| panic!("Cannot read: {:?}", crash_log_path));
        assert!(content.contains(marker), "Marker message ('{}') is not in the crash log:\n{}", marker, content);
    }

    #[test]
    #[cfg(target_os = "macos")]
    fn macos_adjusting_current_dir() {
        let test = prepare_test_env(LauncherLocation::Standard);

        let app_bundle_path_str = test.dist_root.parent().unwrap().to_str().unwrap();
        let debug_mode_var = xplat_launcher::DEBUG_MODE_ENV_VAR.to_string() + "=1";
        let stdout_path = test.project_dir.join("_stdout.txt");
        let stdout_path_str = stdout_path.to_str().unwrap();
        let args = vec!["-Wna", app_bundle_path_str, "--env", &debug_mode_var, "--stdout", stdout_path_str, "--args", "print-cwd"];
        let open_res = std::process::Command::new("/usr/bin/open").args(&args)
            .output().unwrap_or_else(|_| panic!("Failed: 'open {:?}'", args));
        assert!(open_res.status.success(), "Failed: 'open {:?}':\n{:?}", args, open_res);

        let stdout = fs::read_to_string(&stdout_path).unwrap_or_else(|_| panic!("Cannot read: {:?}", stdout_path));
        let expected = format!("CWD={}", env::current_dir().unwrap().display());
        assert!(stdout.contains(&expected), "'{}' is not in the output:\n{}", expected, stdout);
    }

    #[test]
    fn launching_via_external_symlink() {
        let test = prepare_test_env(LauncherLocation::Standard);

        let ext_link = test.create_launcher_link("launcher_link");

        let run_result = std::process::Command::new(&ext_link)
            .env(xplat_launcher::DEBUG_MODE_ENV_VAR, "1")
            .output().unwrap_or_else(|_| panic!("Failed: '{}'", ext_link.display()));
        assert!(run_result.status.success(), "Failed: '{}':\n{:?}", ext_link.display(), run_result);
    }

    #[test]
    fn exposing_main_class_name() {
        let test = prepare_test_env(LauncherLocation::Standard);

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().with_args(&["main-class"]));

        let expected = "main.class=com.intellij.idea.TestMain";
        assert!(result.stdout.contains(expected), "'{}' is not in the output:\n{:?}", expected, result)
    }
}
