// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pub mod utils;

#[cfg(test)]
mod tests {
    use rstest::*;
    use crate::utils::*;

    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::JBR})]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::JBR})]
    fn remote_dev_args_test(#[case]launcher_location: &LayoutSpec) {
        let test = &prepare_test_env(launcher_location);
        let output_file = test.test_root_dir.path().join(TEST_OUTPUT_FILE_NAME);
        let output_args = [
            "--remote-dev",
            "dumpLaunchParameters", &test.test_root_dir.path().to_string_lossy(),
            "--output",
            &output_file.to_string_lossy()
        ];

        let full_args = &mut output_args.to_vec();

        let args = [ "remote_remote_dev_arg_test" ];
        full_args.append(&mut args.to_vec());

        let result = run_launcher(test, full_args, &output_file);

        let dump = &result.dump.expect("Exit status was successful, but no dump was received");

        assert_eq!(&dump.cmdArguments[0], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[1], &test.test_root_dir.path().to_string_lossy());
        assert_eq!(&dump.cmdArguments[2], "--output");
        assert_eq!(&dump.cmdArguments[3], &test.test_root_dir.path().join(TEST_OUTPUT_FILE_NAME).to_string_lossy());
        assert_eq!(&dump.cmdArguments[4], args[0]);
    }
}