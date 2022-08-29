// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
mod tests_util;

#[cfg(test)]
mod tests {
    use crate::tests_util::{IntellijMainDumpedLaunchParameters, prepare_test_env, run_launcher};
    use std::os::unix::process::ExitStatusExt;

    #[test]
    fn correct_launcher_startup_test() {
        let test = prepare_test_env();
        let status = run_launcher(&test).exit_status;

        // let k = launcher_result.exit_status.signal().expect("");
        // // let exit_code = launcher_result.exit_status.code().expect("There's no exit code for launcher");

        if !status.success() {
            let exit_code = option_to_string(status.code());
            let signal = option_to_string(status.signal());
            let core_dumped = status.core_dumped();

            println!("Launcher didn't exit successfully\nexit code: {exit_code}\ntermination signal: {signal}\ncore dumped: {core_dumped}");
        }

        assert!(
            status.success(),
            "The exit status of the launcher is not successful"
        );
    }

    fn option_to_string(code: Option<i32>) -> String {
        match code {
            None => { "None".to_string() }
            Some(x) => { x.to_string() }
        }
    }

    #[test]
    fn classpath_test() {
        let dump = run_launcher_and_get_dump();
        let classpath = &dump.systemProperties["java.class.path"];

        assert!(
            classpath.contains("app.jar"),
            "app.jar is not present in classpath"
        );
    }

    #[test]
    fn additional_jvm_arguments_in_product_info_test() {
        let dump = run_launcher_and_get_dump();
        let idea_vendor_name_vm_option = dump.vmOptions.iter().find(|&vm| vm.starts_with("-Didea.vendor.name=JetBrains"));

        assert!(
            idea_vendor_name_vm_option.is_some(),
            "Didn't find vmoption which should be set throught product-info.json additionJvmArguments field in launch section"
        );
    }

    #[test]
    fn arguments_test() {
        let dump = run_launcher_and_get_dump();
        let first_arg = &dump.cmdArguments[1];

        assert_eq!(
            first_arg,
            "--output"
        );
    }

    fn run_launcher_and_get_dump() -> IntellijMainDumpedLaunchParameters {
        let test = prepare_test_env();
        let result = run_launcher(&test);
        assert!(result.exit_status.success(), "Launcher didn't exit successfully");
        result.dump.expect("Launcher exited successfully, but there is no output")
    }
}
