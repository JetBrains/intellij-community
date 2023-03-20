// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
pub mod utils;

#[cfg(test)]
mod tests {
    use std::fs;
    use std::path::Path;
    use std::process::ExitStatus;
    use rstest::*;
    use crate::utils::*;

    #[cfg(any(target_os = "macos", target_os = "linux"))]
    use {
        std::os::unix::process::ExitStatusExt
    };
    use utils::is_executable;

    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::JBR})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::JBR})]
    fn correct_launcher_startup_test(#[case] layout_spec: &LayoutSpec) {
        let status = &run_launcher(layout_spec).exit_status;
        let exit_status_string = exit_status_to_string(status);
        println!("Launcher's exit status:\n{exit_status_string}");

        assert!(
            status.success(),
            "The exit status of the launcher is not successful"
        );
    }

    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::JBR})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::JBR})]
    fn classpath_test(#[case] layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);
        let dump = run_launcher_and_get_dump_default(&test);
        let classpath = &dump.systemProperties["java.class.path"];

        assert!(
            classpath.contains("app.jar"),
            "app.jar is not present in classpath"
        );
    }

    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::JBR})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::JBR})]
    fn additional_jvm_arguments_in_product_info_test(#[case] layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);
        let dump = run_launcher_and_get_dump_default(&test);
        let idea_vendor_name_vm_option = dump.vmOptions.iter().find(|&vm| vm.starts_with("-Didea.vendor.name=JetBrains"));

        assert!(
            idea_vendor_name_vm_option.is_some(),
            "Didn't find vmoption which should be set throught product-info.json additionJvmArguments field in launch section"
        );
    }

    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::JBR})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::JBR})]
    fn arguments_test(#[case] layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);

        let args = &["arguments-test-123"];

        let dump = run_launcher_and_get_dump_with_args(&test, args);

        assert_eq!(&dump.cmdArguments[0], &test.launcher_path.to_string_lossy());
        assert_eq!(&dump.cmdArguments[1], "dump-launch-parameters");
        assert_eq!(&dump.cmdArguments[2], "--output");
        assert_eq!(&dump.cmdArguments[3], &test.test_root_dir.path().join(TEST_OUTPUT_FILE_NAME).to_string_lossy());
        assert_eq!(&dump.cmdArguments[4], args[0]);
    }

    // todo: order tests

    // # shellcheck disable=SC2154
    // if [ -n "$IDEA_JDK" ] && [ -x "$IDEA_JDK/bin/java" ]; then
    //   JRE="$IDEA_JDK"
    // fi
    #[cfg(target_os = "freebsd")]
    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::EnvVar})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::EnvVar})]
    fn jre_is_idea_jdk_test(#[case] layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);

        let dump = run_launcher_and_get_dump_with_java_env(&test, "IU_JDK");

        assert!(&dump.environmentVariables.contains_key("IU_JDK"), "IU_JDK is not set");
        assert!(
            !&dump.environmentVariables["IU_JDK"].is_empty(),
            "IU_JDK is not found in dumped env vars"
        );
        assert!(
            get_bin_java_path(&Path::new(&dump.environmentVariables["IU_JDK"]))
                .exists(),
            "Java executable from JDK does not exists"
        );
        assert!(
            get_bin_java_path(&Path::new(&dump.environmentVariables["IU_JDK"]))
                .is_executable(),
            "Java executable from JDK is not executable"
        );
        assert!(
            &dump.systemProperties["java.home"].starts_with(
                &dump.environmentVariables["IU_JDK"]),
            "Resolved java is not equal to env var"
        );
    }

    // if [ -z "$JRE" ] && [ -s "${CONFIG_HOME}/JetBrains/IntelliJIdea2022.3/idea.jdk" ]; then
    //   USER_JRE=$(cat "${CONFIG_HOME}/JetBrains/IntelliJIdea2022.3/idea.jdk")
    //   if [ -x "$USER_JRE/bin/java" ]; then
    //     JRE="$USER_JRE"
    //   fi
    // fi
    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::UserJRE})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::UserJRE})]
    #[cfg(target_os = "macos")]
    fn jre_is_user_jre_test(#[case] layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);
        let dump = run_launcher_and_get_dump_default(&test);

        let idea_jdk = get_custom_user_file_with_java_path().unwrap().join("idea.jdk");
        let idea_jdk_content = fs::read_to_string(&idea_jdk).unwrap();
        let jbr_home = get_jbr_home(&Path::new(&idea_jdk_content).to_path_buf()).unwrap();
        let resolved_jdk_path = Path::new(&idea_jdk_content);
        let resolved_jdk = get_bin_java_path(resolved_jdk_path);
        let metadata = idea_jdk.metadata().unwrap();

        assert!(idea_jdk.exists(), "Config file is not exists");
        assert_ne!(0, metadata.len(), "Config file is empty");
        assert!(resolved_jdk_path.exists(), "JDK from idea.jdk is not exists");
        assert!(resolved_jdk.exists(), "JDK from idea.jdk is not exists");
        assert!(resolved_jdk.is_executable(), "Java executable from JDK is not executable");
        assert_eq!(
            &dump.systemProperties["java.home"],
            jbr_home.to_str().expect("Can't display jbr_home"),
            "Resolved java is not from .config"
        );
    }

    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::UserJRE})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::UserJRE})]
    #[cfg(any(target_os = "windows", target_os = "linux"))]
    fn jre_is_user_jre_test(#[case] layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);
        let dump = run_launcher_and_get_dump_default(&test);

        let idea_jdk = get_custom_user_file_with_java_path().unwrap().join("idea.jdk");
        let idea_jdk_content = fs::read_to_string(&idea_jdk).unwrap();
        let resolved_jdk_path = Path::new(&idea_jdk_content);
        let java_executable = get_bin_java_path(resolved_jdk_path);
        let metadata = idea_jdk.metadata().unwrap();

        assert!(idea_jdk.exists(), "Config file is not exists");
        assert_ne!(0, metadata.len(), "Config file is empty");
        assert!(resolved_jdk_path.exists(), "JDK from idea.jdk is not exists");
        assert!(java_executable.exists(), "Java executable idea.jdk is not exists");
        assert!(is_executable(&java_executable).expect("Failed to check java executable"), "Java executable from JDK is not executable");
        assert_eq!(
            &dump.systemProperties["java.home"],
            &idea_jdk_content.to_string(),
            "Resolved java is not from .config"
        );
    }

    // if [ -z "$JRE" ] && [ "$OS_TYPE" = "Linux" ] && [ -f "$IDE_HOME/jbr/release" ]; then
    //   JBR_ARCH="OS_ARCH=\"$OS_ARCH\""
    //   if grep -q -e "$JBR_ARCH" "$IDE_HOME/jbr/release" ; then
    //     JRE="$IDE_HOME/jbr"
    //   fi
    // fi
    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::JBR})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::JBR})]
    fn jre_is_jbr_test(#[case] layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);
        let dump = run_launcher_and_get_dump_default(&test);

        let test_root_dir = test.test_root_dir.path();

        let jbr_dir = match std::env::consts::OS {
            "linux" => test_root_dir.join("jbr"),
            "macos" => test_root_dir.join("Contents").join("jbr"),
            "windows" => test_root_dir.join("jbr"),
            unsupported_os => panic!("Unsupported OS: {unsupported_os}")
        };

        let jbr_home = get_jbr_home(&jbr_dir).expect("Can't get jbr home");

        let java_executable = get_bin_java_path(&jbr_dir);

        assert!(jbr_dir.exists(), "JBR dir is not exists");
        assert!(jbr_dir.is_dir(), "Resolved JBR dir is not a directory");
        assert!(java_executable.exists(), "Resolved java executable is not exists");
        assert!(is_executable(&java_executable).expect("Failed to check java executable"), "Resolved java executable is not executable");
        // todo: turn on after wrapper with https://github.com/mfilippov/gradle-jvm-wrapper/pull/31
        // assert_eq!(
        //     &dump.systemProperties["java.vendor"],
        //     "JetBrains s.r.o.",
        //     "Java vendor is not JetBrains. Resolved java is not JBR");
        assert_eq!(
            &dump.systemProperties["java.home"],
            jbr_home.to_str().expect("Can't display jbr_home"),
            "Resolved java is not JBR"
        );
        // assert!(
        //     &dump.systemProperties["java.home"].contains("jbr"),
        //     "java.home property does not contains 'jbr' in path. Resolved java is not JBR"
        // );
    }

    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::EnvVar})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::EnvVar})]
    fn jre_is_jdk_home_test(#[case] layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);
        let dump = run_launcher_and_get_dump_with_java_env(&test, "JDK_HOME");

        assert!(
            std::env::var("IU_JDK").is_err(),
            "IU_JDK var is present. It has a higher priority than tested var"
        );

        assert!(
            &dump.environmentVariables.contains_key("JDK_HOME"),
            "JDK_HOME not found in dump"
        );
        assert!(
            !&dump.environmentVariables["JDK_HOME"].is_empty(),
            "JDK_HOME is empty"
        );
        assert!(
            get_bin_java_path(&Path::new(&dump.environmentVariables["JDK_HOME"]))
                .exists(),
            "Java executable from JDK does not exists"
        );
        let java_executable = get_bin_java_path(&Path::new(&dump.environmentVariables["JDK_HOME"]));
        assert!(
            is_executable(&java_executable).expect("Failed to check java executable"),
            "Java executable from JDK is not executable"
        );
        assert!(
            &dump.systemProperties["java.home"].starts_with(
                &dump.environmentVariables["JDK_HOME"]),
            "Resolved java is not equal to env var"
        );
    }

    #[cfg(target_os = "freebsd")]
    #[rstest]
    #[case::main_bin(& LayoutSpec {launcher_location: LauncherLocation::MainBin, java_type: JavaType::EnvVar})]
    #[case::plugins_bin(& LayoutSpec {launcher_location: LauncherLocation::PluginsBin, java_type: JavaType::EnvVar})]
    fn jre_is_java_home_test(#[case] layout_spec: &LayoutSpec) {
        let test = prepare_test_env(layout_spec);
        let dump = run_launcher_and_get_dump_with_java_env(&test, "JAVA_HOME");

        assert!(
            std::env::var("IU_JDK").is_err(),
            "IU_JDK var is present. It has a higher priority than tested var"
        );

        assert!(
            std::env::var("JDK_HOME").is_err(),
            "JDK_HOME var is present. It has a higher priority than tested var"
        );

        assert!(
            &dump.environmentVariables.contains_key("JAVA_HOME"),
            "JAVA_HOME not found in dump"
        );
        assert!(
            !&dump.environmentVariables["JAVA_HOME"].is_empty(),
            "JAVA_HOME is empty"
        );
        assert!(
            get_bin_java_path(&Path::new(&dump.environmentVariables["JAVA_HOME"]))
                .exists(),
            "Java executable from JDK does not exists"
        );
        assert!(
            get_bin_java_path(&Path::new(&dump.environmentVariables["JAVA_HOME"]))
                .is_executable(),
            "Java executable from JDK is not executable"
        );
        assert!(
            &dump.systemProperties["java.home"].starts_with(
                &dump.environmentVariables["JAVA_HOME"]),
            "Resolved java is not equal to env var"
        );
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