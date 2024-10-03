// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#[cfg(test)]
mod tests {
    use std::env;
    use xplat_launcher::default::{compute_launch_info, read_product_info, ProductLaunchInfo};
    use xplat_launcher::ProductInfoLaunchField;

    #[test]
    fn product_info_test() {
        assert_default_values("product_info.json", None);
        assert_default_values("product_info.json", Some(&String::from("custom-command")));
    }

    #[test]
    fn product_info_with_empty_custom_commands_test() {
        assert_default_values("product_info_empty_custom_commands.json", None);
        assert_default_values("product_info_empty_custom_commands.json", Some(&String::from("custom-command")));
    }

    #[test]
    fn product_info_with_custom_command_test() {
        assert_default_values("product_info_custom_command.json", None);
        assert_custom_values("product_info_custom_command.json", Some(&String::from("custom-command")));
        assert_default_values("product_info_custom_command.json", Some(&String::from("unknown-command")));
    }

    #[test]
    fn product_info_with_custom_command_no_override_test() {
        assert_default_values("product_info_custom_command_no_override.json", None);
        assert_default_values("product_info_custom_command_no_override.json", Some(&String::from("custom-command")));
    }

    fn assert_custom_values(file_name: &str, command: Option<&String>) {
        let product_launch_info = load_launch_info(file_name, command);
        let launch_info = product_launch_info.product_info_launch_field;
        assert_eq!(launch_info.vmOptionsFilePath, "bin/xplat64custom.vmoptions");
        assert_eq!(launch_info.bootClassPathJarNames, [String::from("custom.jar")].to_vec());
        assert_eq!(launch_info.additionalJvmArguments, [String::from("-Dproduct.property=product.value")].to_vec());
        assert_eq!(launch_info.mainClass, "com.intellij.idea.CustomMain");
        assert_eq!(product_launch_info.custom_env_var_base_name, Some(String::from("CUSTOM_XPLAT")));
        assert_eq!(product_launch_info.custom_data_directory_name, Some(String::from("XPlatLauncherTestCustom")))
    }

    fn assert_default_values(file_name: &str, command: Option<&String>) {
        let product_launch_info = load_launch_info(file_name, command);
        let launch_info = product_launch_info.product_info_launch_field;
        assert_eq!(launch_info.vmOptionsFilePath, "bin/xplat64.vmoptions");
        assert_eq!(launch_info.bootClassPathJarNames, [String::from("app.jar")].to_vec());
        assert_eq!(launch_info.additionalJvmArguments, [String::from("-Didea.paths.selector=XPlatLauncherTest")].to_vec());
        assert_eq!(launch_info.mainClass, "com.intellij.idea.TestMain");
        assert!(product_launch_info.custom_env_var_base_name.is_none(), "Custom env var base name must not be set");
        assert!(product_launch_info.custom_data_directory_name.is_none(), "Custom data directory must not be set")
    }

    fn load_launch_info(file_name: &str, command: Option<&String>) -> ProductLaunchInfo {
        let project_root = env::current_dir().expect("Failed to get project root");
        let product_info_path = project_root.join(format!("resources/product-info/{file_name}"));
        let product_info = read_product_info(&product_info_path).expect("product-info must be loaded");
        compute_launch_info(&product_info, command).expect("launch data must be found")
    }
}