use std::collections::HashMap;
use std::{env, fs};
use std::ffi::OsStr;
use std::fs::File;
use std::io::{BufWriter, Write};
use std::path::{Path, PathBuf};
use log::info;
use path_absolutize::Absolutize;
use crate::errors::{Result};
use crate::{canonical_non_unc, DefaultLaunchConfiguration, err_from_string, LaunchConfiguration};
use crate::default::get_config_home;
use crate::utils::{get_path_from_env_var, PathExt, read_file_to_end};

pub struct RemoteDevLaunchConfiguration {
    default: DefaultLaunchConfiguration,
    config_dir: PathBuf,
    system_dir: PathBuf,
}

impl LaunchConfiguration for RemoteDevLaunchConfiguration {
    fn get_args(&self) -> &[String] {
        self.default.get_args()
    }

    fn get_intellij_vm_options(&self) -> Result<Vec<String>> {
        let default_vm_options = self.default.get_intellij_vm_options()?;

        // TODO: add default Xmx to productInfo as right now we patch the user one
        let mut patched_xmx: Vec<String> = default_vm_options
            .into_iter()
            .filter(|vm| !vm.starts_with("-Xmx"))
            .collect();

        patched_xmx.push("-Xmx2048m".to_string());
        Ok(patched_xmx)
    }

    fn get_properties_file(&self) -> Result<PathBuf> {
        let remote_dev_properties = self.get_remote_dev_properties();
        self.write_merged_properties_file(&remote_dev_properties[..])
    }

    fn get_class_path(&self) -> Result<Vec<String>> {
        self.default.get_class_path()
    }

    #[cfg(any(target_os = "macos", target_os = "windows"))]
    fn prepare_for_launch(&self) -> Result<PathBuf> {
        self.default.prepare_for_launch()
    }

    #[cfg(target_os = "linux")]
    fn prepare_for_launch(&self) -> Result<PathBuf> {
        todo!()
    }
}

impl DefaultLaunchConfiguration {
    fn prepare_host_config_dir(&self, per_project_config_dir_name: &str) -> Result<PathBuf> {
        self.prepare_project_specific_dir(
            "IDE config directory",
            "IJ_HOST_CONFIG_DIR",
            "IJ_HOST_CONFIG_BASE_DIR",
            per_project_config_dir_name
        )
    }

    fn prepare_system_config_dir(&self, per_project_config_dir_name: &str) -> Result<PathBuf> {
        self.prepare_project_specific_dir(
            "IDE system directory",
            "IJ_HOST_SYSTEM_DIR",
            "IJ_HOST_SYSTEM_BASE_DIR",
            per_project_config_dir_name
        )
    }

    fn prepare_project_specific_dir(
        &self,
        human_readable_name: &str,
        specific_dir_env_var_name: &str,
        base_dir_env_var_name: &str,
        per_project_config_dir_name: &str) -> Result<PathBuf> {

        let base_dir = match get_path_from_env_var(base_dir_env_var_name) {
            Ok(x) => x,
            Err(_) => get_config_home(),
        };

        let product_code = &self.product_info.productCode;

        let result = base_dir.join("JetBrains")
            .join(format!("RemoteDev-{product_code}"))
            .join(per_project_config_dir_name);

        let result_string = result.to_string_lossy();

        info!("{human_readable_name}: {result_string}");

        fs::create_dir_all(&result)?;

        Ok(result)
    }
}

impl RemoteDevLaunchConfiguration {
    pub fn parse_remote_dev_args(args: &[String]) -> Result<RemoteDevArgs> {
        if args.is_empty() {
            return err_from_string("Starter command is not specified")
        }

        let remote_dev_starter_command = args[0].as_str();
        let ij_starter_command = match remote_dev_starter_command {
            "registerBackendLocationForGateway" => {
                register_backend();
                std::process::exit(0)
            }
            "run" => { "cwmHostNoLobby" }
            "status" => { "cwmHostStatus" }
            x => {
                print_help();
                return err_from_string(format!("Unknown command: {x}"))
            }
        };

        if args.len() < 1 {
            print_help();
            return err_from_string("Project path is not specified");
        }

        let project_path_string = args[1].as_str();
        if project_path_string == "-h" || project_path_string == "--help" {
            return Ok(
                RemoteDevArgs {
                    project_path: None,
                    ij_args: vec![
                        "remoteDevShowHelp".to_string(),
                        ij_starter_command.to_string()
                    ]
                }
            );
        }

        // TODO: expand tilde
        let project_path = PathBuf::from(project_path_string);
        if !project_path.exists() {
            print_help();
            return err_from_string(format!("Project path does not exist: {project_path_string}"));
        }

        // if [ -d "$PROJECT_PATH" ]; then
        // PROJECT_PATH="$(cd "$PROJECT_PATH" && pwd)"
        // else
        // PROJECT_PATH="$(cd "$(dirname "$PROJECT_PATH")" && pwd)/$(basename "$PROJECT_PATH")"
        // fi

        let absolute_project_path = match project_path.is_dir() {
            true => project_path,
            false => project_path.parent_or_err()?,
        }.absolutize()?.to_path_buf();

        let mut ij_args = args[2..].to_vec();
        let absolute_project_path_string = absolute_project_path.to_string_lossy().to_string();

        match ij_starter_command {
            "warm-up" | "warmup" => {
                ij_args.insert(0, absolute_project_path_string);
                ij_args.insert(0, "warmup".to_string());
            }
            "installPlugins" => {
                ij_args.insert(0, "installPlugins".to_string())
            }
            _ => {
                ij_args.insert(0, absolute_project_path_string);
                ij_args.insert(0, ij_starter_command.to_string())
            }
        };

        Ok(
            RemoteDevArgs {
                project_path: Some(absolute_project_path),
                ij_args
            }
        )
    }

    pub fn new(project_path: PathBuf, default: DefaultLaunchConfiguration) -> Result<Self> {
        let per_project_config_dir_name = match project_path.file_name() {
            None => {
                let message = format!("Failed to get project dir name, project path: {project_path:?}");
                err_from_string(message)
            }
            Some(x) => Ok(x)
        }?.to_string_lossy();

        let config_dir = default.prepare_host_config_dir(&per_project_config_dir_name)?;
        let system_dir = default.prepare_system_config_dir(&per_project_config_dir_name)?;

        let config = RemoteDevLaunchConfiguration {
            default,
            config_dir,
            system_dir
        };

        Ok(config)
    }

    fn get_remote_dev_properties(&self) -> Vec<IdeProperty> {
        let config_path = self.config_dir.to_string_lossy();
        let plugins_path = self.config_dir.join("plugins").to_string_lossy().to_string();
        let system_path = self.system_dir.to_string_lossy();
        let log_path = self.system_dir.join("log").to_string_lossy().to_string();

        let remote_dev_properties = vec![
            ("idea.config.path", config_path.as_ref()),
            ("idea.plugins.path", plugins_path.as_ref()),
            ("idea.system.path", system_path.as_ref()),
            ("idea.log.path", log_path.as_ref()),

            // TODO: remove once all of this is disabled for remote dev
            ("jb.privacy.policy.text", "<!--999.999-->"),
            ("jb.consents.confirmation.enabled", "false"),
            ("idea.initially.ask.config", "never"),
            ("ide.show.tips.on.startup.default.value", "false"),

            // Prevent CWM plugin from being disabled, as it's required for Remote Dev
            ("idea.required.plugins.id", "com.jetbrains.codeWithMe"),

            // Automatic updates are not supported by Remote Development
            // It should be done manually by selecting correct IDE version in JetBrains Gateway
            // For pre-configured environment (e.g. cloud) the version is fixed anyway
            ("ide.no.platform.update", "true"),

            // TODO: disable once IDEA doesn't require JBA login for remote dev
            ("eap.login.enabled", "false"),

            // TODO: CWM-5782 figure out why posix_spawn / jspawnhelper does not work in tests
            ("jdk.lang.Process.launchMechanism", "vfork"),
        ];

        remote_dev_properties
            .into_iter()
            .map(|x| IdeProperty {
                key: x.0.to_string(),
                value: x.1.to_string(),
            })
            .collect()
    }

    fn write_merged_properties_file(&self, remote_dev_properties: &[IdeProperty]) -> Result<PathBuf> {
        let pid = std::process::id();
        let filename = format!("pid.{pid}.temp.remote-dev.properties");
        let path = self.system_dir.join(filename);

        let file = File::open(&path)?;
        let mut writer = BufWriter::new(file);

        // TODO: maybe check the user-set properties file?
        // let default_properties = self.default.get_properties_file();

        // TODO: use IDE-specific properties file
        let distribution_properties = self.default.ide_bin.join("idea.properties");
        let default_properties = read_file_to_end(&distribution_properties)?;

        for l in default_properties.lines() {
            writeln!(&mut writer, "{l}")?;
        }

        for p in remote_dev_properties {
            let key = p.key.as_str();
            let value = p.value.as_str();
            writeln!(&mut writer, "{key}={value}")?;
        }

        writer.flush()?;

        Ok(path)
    }

    #[cfg(any(target_os = "macos", target_os = "windows"))]
    pub fn setup_font_config() -> Result<()> {
        Ok(())
    }

    #[cfg(any(target_os = "linux"))]
    pub fn setup_font_config() -> Result<()> {

        todo!("")
    }
}

struct IdeProperty {
    key: String,
    value: String
}

pub struct RemoteDevArgs {
    pub project_path: Option<PathBuf>,
    pub ij_args: Vec<String>
}

fn register_backend() {
    todo!("")
}

fn print_help() {
    todo!("")
}