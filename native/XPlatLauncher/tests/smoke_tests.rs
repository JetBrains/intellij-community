// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pub mod utils;

#[cfg(test)]
mod tests {
    use std::collections::HashMap;
    use std::{env, fs};
    use std::fs::File;
    use std::io::Write;
    use std::path::PathBuf;
    use std::sync::Mutex;
    use crate::utils::*;

    /// Because of the shared config directory, runtime selection tests cannot run concurrently.
    static RT_LOCK: Mutex<usize> = Mutex::new(0);

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
    fn additional_jvm_arguments_in_product_info_test() {
        let dump = run_launcher(&LauncherRunSpec::standard().with_dump().assert_status()).dump();
        let idea_vendor_name_vm_option = dump.vmOptions.iter().find(|&vm| vm.starts_with("-Didea.vendor.name=JetBrains"));

        assert!(
            idea_vendor_name_vm_option.is_some(),
            "Didn't find VM option which should be set through product-info.json additionJvmArguments field in launch section"
        );
    }

    #[test]
    fn arguments_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let args = &["arguments-test-123"];
        let dump = run_launcher_ext(&test, &LauncherRunSpec::standard().with_dump().with_args(args).assert_status()).dump();

        assert_eq!(&dump.cmdArguments[0], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[1], "--output");
        assert_eq!(&dump.cmdArguments[3], args[0]);
    }

    #[test]
    fn selecting_product_jdk_env_runtime_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let expected_rt = test.create_jbr_link("_prod_jdk_jbr");
        let env = HashMap::from([("XPLAT_JDK", expected_rt.to_str().unwrap())]);

        let _lock = RT_LOCK.lock().expect("Failed to acquire the runtime lock");
        let result = run_launcher_ext(&test, LauncherRunSpec::standard().assert_status().with_env(&env));
        do_test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn selecting_user_config_runtime_test() {
        let mut test = prepare_test_env(LauncherLocation::Standard);
        let expected_rt = test.create_jbr_link("_user_jbr");

        let _lock = RT_LOCK.lock().expect("Failed to acquire the runtime lock");

        let custom_config_dir = get_custom_config_dir();
        test.delete_later(&custom_config_dir);

        let jdk_config_name = if cfg!(target_os = "windows") { "xplat64.exe.jdk" } else { "xplat.jdk" };
        let jdk_config_file = custom_config_dir.join(jdk_config_name);
        fs::create_dir_all(custom_config_dir).unwrap();
        File::create(jdk_config_file).unwrap()
            .write_all(expected_rt.to_str().unwrap().as_bytes()).unwrap();

        let result = run_launcher_ext(&test, LauncherRunSpec::standard().assert_status());
        do_test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn selecting_bundled_runtime_test() {
        let test = prepare_test_env(LauncherLocation::Standard);
        let expected_rt = test.dist_root.join("jbr");

        let _lock = RT_LOCK.lock().expect("Failed to acquire the runtime lock");
        let result = run_launcher_ext(&test, LauncherRunSpec::standard().assert_status());
        do_test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn selecting_jdk_home_env_runtime_test() {
        let test = prepare_no_jbr_test_env(LauncherLocation::Standard);
        let expected_rt = test.create_jbr_link("_jdk_home_jbr");
        let env = HashMap::from([("JDK_HOME", expected_rt.to_str().unwrap())]);

        let _lock = RT_LOCK.lock().expect("Failed to acquire the runtime lock");
        let result = run_launcher_ext(&test, LauncherRunSpec::standard().assert_status().with_env(&env));
        do_test_runtime_selection(result, expected_rt);
    }

    #[test]
    fn selecting_java_home_env_runtime_test() {
        let test = prepare_no_jbr_test_env(LauncherLocation::Standard);
        let expected_rt = test.create_jbr_link("_java_home_jbr");
        let env = HashMap::from([("JAVA_HOME", expected_rt.to_str().unwrap())]);

        let _lock = RT_LOCK.lock().expect("Failed to acquire the runtime lock");
        let result = run_launcher_ext(&test, LauncherRunSpec::standard().assert_status().with_env(&env));
        do_test_runtime_selection(result, expected_rt);
    }

    fn do_test_runtime_selection(result: LauncherRunResult, expected_rt: PathBuf) {
        let rt_line = result.stdout.lines().into_iter()
            .find(|line| line.contains("Resolved runtime: "))
            .expect(format!("The 'resolved runtime' line is not in the output: {}", result.stdout).as_str());
        let resolved_rt = rt_line.split_once("Resolved runtime: ").unwrap().1;
        let actual_rt = &resolved_rt[1..resolved_rt.len() - 1].replace("\\\\", "\\");

        let adjusted_rt = if cfg!(target_os = "macos") { expected_rt.join("Contents/Home") } else { expected_rt };

        assert_eq!(adjusted_rt.to_str().unwrap(), actual_rt, "Wrong runtime; run result: {:?}", result);
    }

    #[test]
    #[cfg(any(target_family = "unix"))]
    fn async_profiler_loading() {
        let result = run_launcher(LauncherRunSpec::standard().assert_status().with_args(&["async-profiler"]));
        assert!(result.stdout.contains("version="), "Profiler version is missing from the output: {:?}", result);
    }

    #[test]
    fn exit_code_passing() {
        let result = run_launcher(LauncherRunSpec::standard().with_args(&["exit-code", "42"]));
        assert_eq!(result.exit_status.code(), Some(42), "The exit code of the launcher is unexpected: {:?}", result);
    }
}
