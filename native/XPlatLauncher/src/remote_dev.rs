use std::collections::HashMap;
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
use std::fs;
use std::fs::File;
use std::io::{BufWriter, Write};
use std::path::{Path, PathBuf};
use log::{debug, info};
use path_absolutize::Absolutize;
use anyhow::{bail, Context, Result};
use crate::default::{get_cache_home, get_config_home};
use utils::{get_path_from_env_var, PathExt, read_file_to_end};
use crate::{DefaultLaunchConfiguration, is_remote_dev, LaunchConfiguration};

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

    fn get_properties_file(&self) -> Result<Option<PathBuf>> {
        let remote_dev_properties = self.get_remote_dev_properties();
        let remote_dev_properties_file = self.write_merged_properties_file(&remote_dev_properties[..])
            .context("Failed to write remote dev IDE properties file")?;

        Ok(Some(remote_dev_properties_file))
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
        // TODO: ld patching
        self.default.prepare_for_launch()
    }
}

impl DefaultLaunchConfiguration {
    fn prepare_host_config_dir(&self, per_project_config_dir_name: &str) -> Result<PathBuf> {
        self.prepare_project_specific_dir(
            "IDE config directory",
            "IJ_HOST_CONFIG_DIR",
            "IJ_HOST_CONFIG_BASE_DIR",
            &get_config_home(),
            per_project_config_dir_name
        )
    }

    fn prepare_system_config_dir(&self, per_project_config_dir_name: &str) -> Result<PathBuf> {
        self.prepare_project_specific_dir(
            "IDE system directory",
            "IJ_HOST_SYSTEM_DIR",
            "IJ_HOST_SYSTEM_BASE_DIR",
            &get_cache_home(),
            per_project_config_dir_name
        )
    }

    fn prepare_project_specific_dir(
        &self,
        human_readable_name: &str,
        specific_dir_env_var_name: &str,
        base_dir_env_var_name: &str,
        default_base_dir: &Path,
        per_project_config_dir_name: &str) -> Result<PathBuf> {
        debug!("Per project config dir name: {per_project_config_dir_name:?}");

        let specific_dir = match get_path_from_env_var(specific_dir_env_var_name) {
            Ok(x) => {
                debug!("{human_readable_name}: {specific_dir_env_var_name} is set to {x:?}, will use it as a target dir");
                x
            },
            Err(_) => {
                let base_dir = match get_path_from_env_var(base_dir_env_var_name) {
                    Ok(x) => {
                        debug!("{human_readable_name}: {base_dir_env_var_name} is set to {x:?}, will use it as a base dir");
                        x
                    },
                    Err(_) => default_base_dir.to_path_buf(),
                };

                let product_code = &self.product_info.productCode;

                let result = base_dir.join("JetBrains")
                    .join(format!("RemoteDev-{product_code}"))
                    .join(per_project_config_dir_name);

                result
            }
        };

        let result_string = specific_dir.to_string_lossy();

        info!("{human_readable_name}: {result_string}");

        fs::create_dir_all(&specific_dir)?;

        Ok(specific_dir)
    }
}

struct IjStarterCommand {
    ij_command: String,
    is_project_path_required: bool
}

impl RemoteDevLaunchConfiguration {
    // launcher.exe --remote-dev command_name /path/to/project args ->
    // launcher.exe ij_command_name /path/to/project args
    pub fn parse_remote_dev_args(args: &[String]) -> Result<RemoteDevArgs> {
        debug!("Parsing remote dev command-line arguments");

        if !is_remote_dev(args) {
            bail!("Expected to see --remote-dev marker in command-line arguments")
        }

        if args.len() < 3 {
            bail!("Starter command is not specified")
        }

        let remote_dev_starter_command = args[2].as_str();
        let is_project_required_by_known_commands = HashMap::from([
            ("registerBackendLocationForGateway", ("", false)),
            ("run", ("cwmHostNoLobby", true)),
            ("status", ("cwmHostStatus", false)),
            ("cwmHostStatus", ("cwmHostStatus", false)),
            ("dumpLaunchParameters", ("dump-launch-parameters", false)),
            ("warmup", ("warmup", true)),
            ("warm-up", ("warmup", true)),
            ("invalidate-caches", ("invalidateCaches", true)),
            ("installPlugins", ("installPlugins", false)),
        ]);

        let ij_starter_command = match is_project_required_by_known_commands.get(remote_dev_starter_command) {
            Some((ij_command, is_project_path_required)) => IjStarterCommand {
                ij_command: ij_command.to_string(),
                is_project_path_required: *is_project_path_required
            },
            None => {
                print_help();
                bail!("Unknown command: {remote_dev_starter_command}")
            }
        };

        let project_path = if args.len() > 3 {
            let arg = args[3].as_str();
            if arg == "-h" || arg == "--help" {
                return Ok(
                    RemoteDevArgs {
                        project_path: None,
                        ij_args: vec![
                            args[0].to_string(),
                            "remoteDevShowHelp".to_string(),
                            ij_starter_command.ij_command
                        ]
                    }
                );
            }

            Some(Self::get_project_path(arg)?)
        } else {
            None
        };

        let ij_args = match &project_path {
            None => {
                if ij_starter_command.is_project_path_required {
                    print_help();
                    bail!("Project path is not specified");
                }

                let command_arguments = args[3..].to_vec();

                [vec![ij_starter_command.ij_command], command_arguments]
            }
            Some(x) => {
                let project_path_string = x.to_string_lossy().to_string();
                let command_arguments = args[4..].to_vec();

                if ij_starter_command.ij_command == "warmup" {
                    [vec![ij_starter_command.ij_command, format!("--project-dir={project_path_string}")], command_arguments]
                } else {
                    [vec![ij_starter_command.ij_command, project_path_string], command_arguments]
                }
            }
        }.concat();

        Ok(RemoteDevArgs { project_path, ij_args })
    }

    fn get_project_path(argument: &str) -> Result<PathBuf> {
        let project_path_string = argument;

        // TODO: expand tilde
        let project_path = PathBuf::from(project_path_string);
        if !project_path.exists() {
            print_help();
            bail!("Project path does not exist: {project_path_string}");
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

        return Ok(absolute_project_path);
    }

    pub fn new(project_path: PathBuf, default: DefaultLaunchConfiguration) -> Result<Self> {
        let per_project_config_dir_name = project_path.to_string_lossy()
            .replace("/", "_")
            .replace("\\", "_")
            .replace(":", "_");

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

            ("#com.intellij.idea.SocketLock.level", "FINE"),

            // TODO: CWM-5782 figure out why posix_spawn / jspawnhelper does not work in tests
            // ("jdk.lang.Process.launchMechanism", "vfork"),
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

        match path.parent() {
            None => {}
            Some(x) => fs::create_dir_all(x)
                .context("Failed to create to parent folder for IDE properties file at path {x:?}")?
        }

        let file = File::create(&path)?;
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
        // TODO: implement
        Ok(())
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

fn print_help() {
    todo!("")
}