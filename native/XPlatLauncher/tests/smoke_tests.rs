// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pub mod utils;

#[cfg(test)]
mod tests {
    use std::process::ExitStatus;
    use rstest::*;
    use crate::utils::*;

    #[cfg(any(target_os = "macos", target_os = "linux"))]
    use {
        std::os::unix::process::ExitStatusExt
    };

    #[rstest]
    #[case::main_bin(&LauncherLocation::MainBin)]
    #[case::plugins_bin(&LauncherLocation::PluginsBin)]
    fn correct_launcher_startup_test(#[case] launcher_location: &LauncherLocation) {
        let test = prepare_test_env(launcher_location);
        let status = &run_launcher_with_default_args(&test, &[]).exit_status;

        let exit_status_string = exit_status_to_string(status);
        println!("Launcher's exit status:\n{exit_status_string}");

        assert!(
            status.success(),
            "The exit status of the launcher is not successful"
        );
    }

    #[rstest]
    #[case::main_bin(&LauncherLocation::MainBin)]
    #[case::plugins_bin(&LauncherLocation::PluginsBin)]
    fn classpath_test(#[case] launcher_location: &LauncherLocation) {
        let dump = run_launcher_and_get_dump(launcher_location);
        let classpath = &dump.systemProperties["java.class.path"];

        assert!(
            classpath.contains("app.jar"),
            "app.jar is not present in classpath"
        );
    }

    #[rstest]
    #[case::main_bin(&LauncherLocation::MainBin)]
    #[case::plugins_bin(&LauncherLocation::PluginsBin)]
    fn additional_jvm_arguments_in_product_info_test(#[case] launcher_location: &LauncherLocation) {
        let dump = run_launcher_and_get_dump(launcher_location);
        let idea_vendor_name_vm_option = dump.vmOptions.iter().find(|&vm| vm.starts_with("-Didea.vendor.name=JetBrains"));

        assert!(
            idea_vendor_name_vm_option.is_some(),
            "Didn't find vmoption which should be set throught product-info.json additionJvmArguments field in launch section"
        );
    }

    #[rstest]
    #[case::main_bin(&LauncherLocation::MainBin)]
    #[case::plugins_bin(&LauncherLocation::PluginsBin)]
    fn arguments_test(#[case] launcher_location: &LauncherLocation) {
        let test = prepare_test_env(launcher_location);

        let args = &["arguments-test-123"];
        let result = run_launcher_with_default_args(&test, args);
        assert!(&result.exit_status.success());

        let dump = &result.dump.expect("Launcher exited successfully, but no dump received");

        assert_eq!(&dump.cmdArguments[0], &test.launcher_path.to_string_lossy());
        assert_eq!(&dump.cmdArguments[1], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[2], "--output");
        assert_eq!(&dump.cmdArguments[3], &test.test_root_dir.path().join(TEST_OUTPUT_FILE_NAME).to_string_lossy());
        assert_eq!(&dump.cmdArguments[4], args[0]);
    }

    #[cfg(target_os = "windows")]
    fn exit_status_to_string(status: &ExitStatus) -> String {
        let exit_code = option_to_string(status.code());
        format!("exit code: {exit_code}")
    }

    #[cfg(any(target_os = "macos", target_os = "linux"))]
    fn exit_status_to_string(status: &ExitStatus) -> String {
        let exit_code = option_to_string(status.code());
        let signal = option_to_string(status.signal());
        let core_dumped = status.core_dumped();
        format!("exit code: {exit_code}, signal: {signal}, core_dumped: {core_dumped}")
    }

    fn option_to_string(code: Option<i32>) -> String {
        match code {
            None => { "None".to_string() }
            Some(x) => { x.to_string() }
        }
    }
}