// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pub mod utils;

#[cfg(test)]
mod tests {
    use std::collections::HashMap;
    use rstest::*;
    use crate::utils::*;

    #[rstest]
    #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    fn remote_dev_args_test(#[case]layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);

        let output_file = test.test_root_dir.path().join(TEST_OUTPUT_FILE_NAME);
        let project_dir = &test.test_root_dir.path().to_string_lossy();

        let args = &[ "remote_remote_dev_arg_test" ];
        let output_args = ["dumpLaunchParameters", &project_dir, "--output", &output_file.to_string_lossy()];
        let full_args = &mut output_args.to_vec();
        full_args.append(&mut args.to_vec());

        let default_env_var: HashMap<&str, &str> = HashMap::from([
            (xplat_launcher::DO_NOT_SHOW_ERROR_UI_ENV_VAR, "1"),
            ("CWM_NO_PASSWORD", "1"),
            ("CWM_HOST_PASSWORD", "1"),
            ("REMOTE_DEV_NON_INTERACTIVE", "1")
        ]);

        let result = match run_launcher_impl(&test, full_args, default_env_var, &output_file) {
            Ok(launcher_dump) => launcher_dump,
            Err(e) => {
                panic!("Failed to get launcher run result: {e:?}")
            }
        };
        assert!(result.exit_status.success(), "Launcher didn't exit successfully");

        let dump = result.dump.expect("Launcher exited successfully, but there is no output");

        assert_eq!(&dump.cmdArguments[0], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[1], &test.test_root_dir.path().to_string_lossy());
        assert_eq!(&dump.cmdArguments[2], "--output");
        assert_eq!(&dump.cmdArguments[3], &test.test_root_dir.path().join(TEST_OUTPUT_FILE_NAME).to_string_lossy());
        assert_eq!(&dump.cmdArguments[4], args[0]);
    }

    // todo: write run_RD realisation with custom command                X
    // todo: redirect std_out to file and load it                        X
    // todo: 1) существующая команда с проектом                          X
    // todo: 2) существующая команда без проекта                         X
    // todo: 3) существающая команда с двумя ключами (warm-up и warmup)  X
    // todo: 4) несуществующая команда                                   X

    // #[rstest]
    // #[case::remote_dev_test(& LayoutSpec {launcher_location: LauncherLocation::MainBinRemoteDev, java_type: JavaType::JBR})]
    // fn remote_dev_help_test(#[case]layoyt_spec: &LayoutSpec) {
    //
    //     let args = & ["kek"];
    //     let dump = run_remote_dev_and_get_dump_with_args(layoyt_spec, args);
    // }
}