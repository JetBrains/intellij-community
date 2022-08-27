// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
mod tests_util;

#[cfg(test)]
// | What do we need to check | status |
// |--------------------------|:------:|
// | launcher exit status     |   V    |
// | Class path               |   V    |
// | vm options               |   X    |  TODO: add tests after dehardcode
// | command line arguments   |   V    |
// | current Java version     |   V    |
// | current Java vendor      |   V    |
// | work dir                 |   V    |
// | PATH, LD_LIBRARY_PATH... |   X    |  writeEnvironmentVariableInFile(String envVariable)
// | setup JRE                |   X    |

mod tests {
    use crate::tests_util::{IntellijMainDumpedLaunchParameters, prepare_test_env, run_launcher};
    use std::path::PathBuf;
    use std::process::{Command, ExitStatus};
    use std::time::Duration;
    use std::{fs, thread, time};

    #[test]
    fn correct_launcher_startup_test() {
        let test = prepare_test_env();
        let launcher_result = run_launcher(&test);

        assert!(
            launcher_result.exit_status.success(),
            "The exit code of the launcher is not successful"
        );
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
        result.dump
    }
}
