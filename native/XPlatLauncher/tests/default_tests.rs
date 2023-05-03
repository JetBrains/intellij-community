// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
        run_launcher(&LauncherRunSpec::standard().assert_status());
    }

    #[test]
    fn classpath_test() {
        let dump = run_launcher(&LauncherRunSpec::standard().with_dump().assert_status()).dump();
        let classpath = &dump.systemProperties["java.class.path"];

        assert!(classpath.contains("app.jar"), "app.jar is not present in classpath: {}", classpath);

        let os_specific_jar = format!("boot-{}.jar", env::consts::OS);
        assert!(classpath.contains(&os_specific_jar), "{} is not present in classpath: {}", os_specific_jar, classpath);
    }

    #[test]
    fn standard_vm_options_loading_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let vm_options_name = if cfg!(target_os = "windows") { "xplat64.exe.vmoptions" }
            else if cfg!(target_os = "macos") { "xplat.vmoptions" }
            else { "xplat64.vmoptions" };
        let vm_options_file = test.dist_root.join("bin").join(vm_options_name);

        let dump = run_launcher_ext(&test, &LauncherRunSpec::standard().with_dump().assert_status()).dump();

        // `bin/*.vmoptions`
        assert_vm_option_presence(&dump, "-Xmx256m");
        assert_vm_option_presence(&dump, "-XX:+UseG1GC");
        assert_vm_option_presence(&dump, "-Dsun.io.useCanonCaches=false");
        // `product-info.json`
        assert_vm_option_presence(&dump, "-Didea.vendor.name=JetBrains");
        assert_vm_option_presence(&dump, "-Didea.paths.selector=XPlatLauncherTest");

        let vm_option = dump.vmOptions.iter().find(|s| s.starts_with("-Djb.vmOptionsFile="))
            .expect(&format!("'jb.vmOptionsFile' is not in {:?}", dump.vmOptions));
        let path = PathBuf::from(vm_option.split_once('=').unwrap().1);
        assert_eq!(vm_options_file.canonicalize().unwrap(), path.canonicalize().unwrap());
    }

    #[test]
    fn missing_standard_vm_options_failure_test() {
        let test = prepare_test_env(LauncherLocation::Standard);

        let bin_dir = test.dist_root.join("bin");
        for item in fs::read_dir(&bin_dir).expect(&format!("Cannot list: {:?}", bin_dir)) {
            if let Ok(entry) = item {
                if entry.file_name().to_str().unwrap().ends_with(".vmoptions") {
                    fs::remove_file(&entry.path()).expect(&format!("Cannot delete: {:?}", entry.path()));
                    break;
                }
            }
        }

        let result = run_launcher_ext(&test, &LauncherRunSpec::standard().with_dump());
        assert!(!result.exit_status.success(), "Expected to fail: {:?}", result);
    }

    #[test]
    fn product_env_vm_options_loading_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let temp_file = test.create_temp_file("_product_env.vm_options", "-Xmx256m\n-Done.user.option=whatever\n");
        let env = HashMap::from([("XPLAT_VM_OPTIONS", temp_file.to_str().unwrap())]);

        let dump = run_launcher_ext(&test, &LauncherRunSpec::standard().with_dump().with_env(&env).assert_status()).dump();

        assert_vm_option_presence(&dump, "-Xmx256m");
        assert_vm_option_presence(&dump, "-Done.user.option=whatever");
        assert_vm_option_presence(&dump, "-Didea.vendor.name=JetBrains");
        assert_vm_option_presence(&dump, &jvm_property!("jb.vmOptionsFile", temp_file.to_str().unwrap()));

        assert_vm_option_absence(&dump, "-XX:+UseG1GC");
        assert_vm_option_absence(&dump, "-Dsun.io.useCanonCaches=false");
    }

    #[test]
    fn product_env_properties_loading_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let temp_file = test.create_temp_file("_product_env.properties", "one.user.property=whatever\n");
        let env = HashMap::from([("XPLAT_PROPERTIES", temp_file.to_str().unwrap())]);

        let dump = run_launcher_ext(&test, &LauncherRunSpec::standard().with_dump().with_env(&env).assert_status()).dump();

        assert_vm_option_presence(&dump, "-Xmx256m");
        assert_vm_option_presence(&dump, "-XX:+UseG1GC");
        assert_vm_option_presence(&dump, "-Dsun.io.useCanonCaches=false");
        assert_vm_option_presence(&dump, &jvm_property!("idea.properties.file", temp_file.to_str().unwrap()));
    }

    #[test]
    fn toolbox_vm_options_loading_test() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        let vm_options_file = test.create_toolbox_vm_options("-Done.user.option=whatever\n");

        let dump = run_launcher_ext(&test, &LauncherRunSpec::standard().with_dump().assert_status()).dump();

        assert_vm_option_presence(&dump, "-Done.user.option=whatever");
        assert_vm_option_presence(&dump, "-Didea.vendor.name=JetBrains");
        assert_vm_option_presence(&dump, &jvm_property!("jb.vmOptionsFile", vm_options_file.to_str().unwrap()));
    }

    #[test]
    fn vm_options_filtering_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let temp_file = test.create_temp_file("_product_env.vm_options", "# a comment\n \n-Xmx256m \n");
        let env = HashMap::from([("XPLAT_VM_OPTIONS", temp_file.to_str().unwrap())]);

        let dump = run_launcher_ext(&test, &LauncherRunSpec::standard().with_dump().with_env(&env).assert_status()).dump();

        assert_vm_option_presence(&dump, "-Xmx256m");

        assert_vm_option_absence(&dump, "# a comment");
        assert_vm_option_absence(&dump, "");
    }

    #[test]
    fn vm_options_overriding_test() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        test.create_toolbox_vm_options("-Xmx512m\n-XX:+UseZGC\n-Dsun.io.useCanonCaches=true\n");

        let dump = run_launcher_ext(&test, LauncherRunSpec::standard().with_dump().assert_status()).dump();

        assert_eq!(dump.systemProperties["__MAX_HEAP"], "512");
        assert_eq!(dump.systemProperties["__GC"], "ZGC");
        assert_eq!(dump.systemProperties["sun.io.useCanonCaches"], "true");
    }

    #[test]
    fn arguments_test() {
        let args = &["arguments-test-123"];
        let dump = run_launcher(&LauncherRunSpec::standard().with_dump().with_args(args).assert_status()).dump();

        assert_eq!(&dump.cmdArguments[0], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[1], "--output");
        assert_eq!(&dump.cmdArguments[3], args[0]);
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
        let test = prepare_no_jbr_test_env(LauncherLocation::Standard);
        let expected_rt = test.create_jbr_link("_jdk_home_jbr");
        let env = HashMap::from([("JDK_HOME", expected_rt.to_str().unwrap())]);

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().with_env(&env).assert_status());
        test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn selecting_java_home_env_runtime_test() {
        let test = prepare_no_jbr_test_env(LauncherLocation::Standard);
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
        assert!(result.stderr.contains("at com.intellij.idea.Main.exception"), "Stacktrace is missing: {:?}", result);
    }

    #[test]
    fn crash_log_creation() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        let crash_log_path = test.project_dir.join("_jvm_error.log");
        test.create_toolbox_vm_options(&format!("-XX:ErrorFile={}", crash_log_path.display()));

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().with_args(&["sigsegv"]));

        assert!(!result.exit_status.success(), "Expected to fail: {:?}", result);
        assert!(crash_log_path.exists(), "No crash log at {:?}: {:?}", crash_log_path, result);

        let marker = "# A fatal error has been detected by the Java Runtime Environment:";
        let content = fs::read_to_string(&crash_log_path).expect(&format!("Cannot read: {:?}", crash_log_path));
        assert!(content.contains(marker), "Marker message ('{}') is not in the crash log:\n{}", marker, content);

        // preserving disk space on build agents
        assert!(content.contains("No core dump will be written"), "Warning: a core dump was created:\n{}", content);
    }
}
