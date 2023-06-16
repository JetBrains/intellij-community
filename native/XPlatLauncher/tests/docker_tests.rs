// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

pub mod utils;

#[cfg(test)]
mod tests {
    #[cfg(target_os = "linux")]
    use {
        std::fs::{create_dir_all, File},
        std::io::Write,
        std::path::PathBuf,
        log::info,
        xplat_launcher::docker::{is_control_group_matches_docker, is_docker_env_file_exist, is_docker_init_file_exist},
        crate::utils::*
    };

    #[cfg(target_os = "windows")]
    use xplat_launcher::docker::is_service_present;

    #[cfg(target_os = "linux")]
    const TEST_CLASS_DIR_NAME: &str = "DockerTestData";

    #[test]
    #[cfg(target_os = "linux")]
    fn is_docker_env_file_exist_file_exist() {
        let env = prepare_env();
        let home_dir = env.test_temp_directory;
        create_file_in_dir(&home_dir, ".dockerenv");

        assert!(is_docker_env_file_exist(Some(home_dir)).unwrap());
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn is_docker_env_file_exist_file_not_exist() {
        let env = prepare_env();
        let home_dir = env.test_temp_directory;
        create_file_in_dir(&home_dir, ".dockerenv_1");

        assert!(!is_docker_env_file_exist(Some(home_dir)).unwrap());
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn is_docker_env_file_exist_root_not_exist() {
        let env = prepare_env();
        let home_dir = env.test_temp_directory;

        assert!(
            !is_docker_env_file_exist(Some(home_dir.join("PathNotExist"))).unwrap()
        );
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn is_docker_init_file_exist_file_exist() {
        let env = prepare_env();
        let home_dir = env.test_temp_directory;
        create_file_in_dir(&home_dir, ".dockerinit");

        assert!(is_docker_init_file_exist(Some(home_dir)).unwrap());
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn is_docker_init_file_exist_file_not_exist() {
        let env = prepare_env();
        let home_dir = env.test_temp_directory;
        create_file_in_dir(&home_dir, ".dockerinit_1");

        assert!(!is_docker_init_file_exist(Some(home_dir)).unwrap());
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn is_docker_init_file_exist_root_not_exist() {
        let env = prepare_env();
        let home_dir = env.test_temp_directory;

        assert!(
            !is_docker_init_file_exist(Some(home_dir.join("PathNotExist"))).unwrap()
        );
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn is_control_group_matches_docker_contains_docker() {
        let env = prepare_env();

        let cgroup_dir = env.test_temp_directory;
        let content = "11:name=systemd:/
10:hugetlb:/
9:perf_event:/
8:blkio:/
7:freezer:/
6:devices:/docker/3601745b3bd54d9780436faa5f0e4f72bb46231663bb99a6bb892764917832c2
5:memory:/
4:cpuacct:/
3:cpu:/docker/3601745b3bd54d9780436faa5f0e4f72bb46231663bb99a6bb892764917832c2
2:cpuset:/";

        create_cgroup_file(&cgroup_dir, content);

        assert!(is_control_group_matches_docker(Some(cgroup_dir)).unwrap());
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn is_control_group_matches_docker_does_not_contain_docker() {
        let env = prepare_env();

        let cgroup_dir = env.test_temp_directory;
        let cgroup_content = "11:name=systemd:/
10:hugetlb:/
9:perf_event:/
8:blkio:/
7:freezer:/
6:devices:/
5:memory:/
4:cpuacct:/
3:cpu:/
2:cpuset:/";

        create_cgroup_file(&cgroup_dir, cgroup_content);

        assert!(!is_control_group_matches_docker(Some(cgroup_dir)).unwrap());
    }

    #[test]
    #[cfg(target_os = "windows")]
    fn is_service_present_service_exist() {
        assert!(is_service_present("Dnscache").unwrap());
    }

    #[test]
    #[cfg(target_os = "windows")]
    fn is_service_present_service_not_exist() {
        assert!(!is_service_present("ServiceDoesNotExist").unwrap());
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn is_control_group_matches_docker_in_container() {
        // TODO: Start a real container from java image with a cgroup file and get result.
    }

    #[test]
    #[cfg(target_os = "windows")]
    fn is_service_present_in_container() {
        // TODO: Start a real container from win image with running 'cexecsvc' service running and get result.
    }

    /**
     * Prepare Docker test environment for a particular test class:
     *    - Prepare temp directory to store temp files
     *    - Prepare common test environment
     */
    #[cfg(target_os = "linux")]
    fn prepare_env() -> DockerTestEnvironment {
        info!("Prepare Docker test environment");
        info!("Preparing test environment");
        let environment = prepare_test_env(LauncherLocation::Standard);

        info!("Preparing temp directory");
        let test_temp_directory = environment.create_temp_dir(TEST_CLASS_DIR_NAME);

        return DockerTestEnvironment { test_temp_directory }
    }

    /**
     * Create a file with a provided name in a specified directory and validate creation result.
     */
    #[cfg(target_os = "linux")]
    fn create_file_in_dir(path: &PathBuf, file_name: &str) -> File {
        if !path.exists() {
            create_dir_all(&path).expect(format!(
                "Unable to create test class temp directory: {}", path.display()).as_str());
        }
        let file_path = path.join(file_name);
        let file = File::create(&file_path).expect(
            format!("Failed to create file '{}' in directory: {}", file_name, path.display()
            ).as_str()
        );

        info!("Test file is created: {}", file_path.display());
        return file;
    }

    /**
     * Create a cgroup file with a content in a specified directory.
     */
    #[cfg(target_os = "linux")]
    fn create_cgroup_file(path: &PathBuf, content: &str) -> File {
        if !path.exists() {
            create_dir_all(&path).expect(format!(
                "Unable to create test class temp directory: {}", path.display()).as_str());
        }
        let cgroup_file_path = path.join("cgroup");
        let mut cgroup_file = File::create(&cgroup_file_path).expect(
            format!(
                "Failed to create cgroup file in directory: {}", cgroup_file_path.display()
            ).as_str()
        );

        cgroup_file.write_all(content.as_ref()).expect(
            format!(
                "Unable to file cgroup file '{}' with content: {}", cgroup_file_path.display(), content
            ).as_str()
        );

        info!("Created cgroup file: {}", cgroup_file_path.display());
        return cgroup_file;
    }

    /**
     * Test environment for Docker tests.
     */
    #[cfg(target_os = "linux")]
    struct DockerTestEnvironment {
        test_temp_directory: PathBuf,
    }
}
