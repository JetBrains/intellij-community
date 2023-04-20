// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
use std::{env, fs};
use std::collections::HashMap;
use std::fs::File;
use std::io::{BufWriter, Write};
use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use log::{debug, info};
use utils::{canonical_non_unc, get_current_exe, get_path_from_env_var, read_file_to_end};

use crate::{DefaultLaunchConfiguration, get_cache_home, get_config_home, get_logs_home, LaunchConfiguration};
use crate::docker::is_running_in_docker;

pub struct RemoteDevLaunchConfiguration {
    default: DefaultLaunchConfiguration,
    config_dir: PathBuf,
    system_dir: PathBuf,
    logs_dir: Option<PathBuf>,
    ij_starter_command: String,
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
        let remote_dev_properties_file = self.write_merged_properties_file(&remote_dev_properties?[..])
            .context("Failed to write remote dev IDE properties file")?;

        Ok(Some(remote_dev_properties_file))
    }

    fn get_class_path(&self) -> Result<Vec<String>> {
        self.default.get_class_path()
    }

    fn prepare_for_launch(&self) -> Result<PathBuf> {
        init_env_vars()?;
        let project_trust_file = self.init_project_trust_file_if_needed()?;
        debug!("Project trust file is: {:?}", project_trust_file);

        self.default.prepare_for_launch()
    }
}

impl DefaultLaunchConfiguration {
    fn prepare_host_config_dir(&self, per_project_config_dir_name: &str) -> Result<PathBuf> {
        self.prepare_project_specific_dir(
            "IDE config directory",
            "IJ_HOST_CONFIG_DIR",
            "IJ_HOST_CONFIG_BASE_DIR",
            &get_config_home()?,
            per_project_config_dir_name
        )
    }

    fn prepare_host_system_dir(&self, per_project_config_dir_name: &str) -> Result<PathBuf> {
        self.prepare_project_specific_dir(
            "IDE system directory",
            "IJ_HOST_SYSTEM_DIR",
            "IJ_HOST_SYSTEM_BASE_DIR",
            &get_cache_home()?,
            per_project_config_dir_name
        )
    }

    fn prepare_host_logs_dir(&self, per_project_config_dir_name: &str) -> Result<Option<PathBuf>> {
        let logs_home = &get_logs_home()?;

        match logs_home {
            None => return Ok(None),
            Some(x) => {
                let prepared_logs_home = self.prepare_project_specific_dir(
                    "IDE logs directory",
                    "IJ_HOST_LOGS_DIR",
                    "IJ_HOST_LOGS_BASE_DIR",
                    x,
                    per_project_config_dir_name
                )?;
                Ok(Some(prepared_logs_home))
            }
        }
    }

    fn prepare_project_specific_dir(
        &self,
        human_readable_name: &str,
        specific_dir_env_var_name: &str,
        base_dir_env_var_name: &str,
        default_base_dir: &Path,
        per_project_config_dir_name: &str) -> Result<PathBuf> {
        debug!("Per-project {human_readable_name} name: {per_project_config_dir_name:?}");

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

impl RemoteDevLaunchConfiguration {
    // remote-dev-server.exe ij_command_name /path/to/project args
    pub fn parse_remote_dev_args(args: &[String]) -> Result<RemoteDevArgs> {
        debug!("Parsing remote dev command-line arguments");

        if args.len() < 2 {
            bail!("Starter command is not specified")
        }

        let remote_dev_starter_command = args[1].as_str();
        let known_ij_commands = get_known_intellij_commands();

        let ij_starter_command = match known_ij_commands.get(remote_dev_starter_command) {
            Some(ij_starter_command) => IjStarterCommand {
                ij_command: ij_starter_command.ij_command.to_string(),
                is_project_path_required: ij_starter_command.is_project_path_required,
                is_arguments_required: ij_starter_command.is_arguments_required
            },
            None => {
                print_help();
                bail!("Unknown command: {remote_dev_starter_command}")
            }
        };

        if remote_dev_starter_command == "help" {
            print_help();
            std::process::exit(0)
        }

        let project_path = if args.len() > 2 {
            let arg = args[2].as_str();
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

                let command_arguments = args[2..].to_vec();

                [vec![ij_starter_command.ij_command], command_arguments]
            }
            Some(x) => {
                let project_path_string = x.to_string_lossy().to_string();
                let command_arguments = args[3..].to_vec();

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

        return Ok(project_path);
    }

    pub fn new(project_path: &Path, default: DefaultLaunchConfiguration) -> Result<Self> {
        // prevent opening of 2 backends for the same directory via symlinks
        let canonical_project_path = canonical_non_unc(project_path)?;

        if project_path != project_path.canonicalize()? {
            info!("Will use canonical form '{canonical_project_path:?}' of '{project_path:?}' to avoid concurrent IDE instances on the same project");
        }

        let per_project_config_dir_name = canonical_project_path
            .replace("/", "_")
            .replace("\\", "_")
            .replace(":", "_");

        debug!("Per-project config dir name: '{per_project_config_dir_name}'");

        let config_dir = default.prepare_host_config_dir(&per_project_config_dir_name)?;
        let system_dir = default.prepare_host_system_dir(&per_project_config_dir_name)?;
        let logs_dir = default.prepare_host_logs_dir(&per_project_config_dir_name)?;
        let ij_starter_command = default.args[0].to_string();

        let config = RemoteDevLaunchConfiguration {
            default,
            config_dir,
            system_dir,
            logs_dir,
            ij_starter_command,
        };

        Ok(config)
    }

    fn get_remote_dev_properties(&self) -> Result<Vec<IdeProperty>> {
        let config_path_string = escape_for_idea_properties(&self.config_dir);
        let plugins_path_string = escape_for_idea_properties(&self.config_dir.join("plugins"));
        let system_path_string = escape_for_idea_properties(&self.system_dir);

        let logs_path_string = match &self.logs_dir {
            None => escape_for_idea_properties(&self.system_dir.join("log")),
            Some(x) => escape_for_idea_properties(x)
        };

        let mut remote_dev_properties = vec![
            ("idea.config.path", config_path_string.as_str()),
            ("idea.plugins.path", plugins_path_string.as_str()),
            ("idea.system.path", system_path_string.as_str()),
            ("idea.log.path", logs_path_string.as_str()),

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

            // Don't ask user about indexes download
            ("shared.indexes.download", "true"),
            ("shared.indexes.download.auto.consent", "true"),

            // TODO: disable once IDEA doesn't require JBA login for remote dev
            ("eap.login.enabled", "false"),

            // TODO: CWM-5782 figure out why posix_spawn / jspawnhelper does not work in tests
            // ("jdk.lang.Process.launchMechanism", "vfork"),
        ];

        match env::var("REMOTE_DEV_NEW_UI_ENABLED") {
            Ok(remote_dev_new_ui_enabled) => {
                match remote_dev_new_ui_enabled.as_str() {
                    "1" | "true" => {
                        info!("Force enable new UI");
                        remote_dev_properties.push(("ide.experimental.ui", "true"));
                    },
                    _ => {
                        bail!("Unsupported value for REMOTE_DEV_NEW_UI_ENABLED variable: '{}'", remote_dev_new_ui_enabled);
                    },
                }
            }
            Err(_) => {
                info!("Using ui config with default values");
            }
        }

        match env::var("REMOTE_DEV_JDK_DETECTION") {
            Ok(remote_dev_jdk_detection_value) => {
                match remote_dev_jdk_detection_value.as_str() {
                    "1" | "true" => {
                        info!("Enable JDK auto-detection and project SDK setup");
                        remote_dev_properties.push(("jdk.configure.existing", "true"));
                    },
                    "0" | "false" => {
                        info!("Disable JDK auto-detection and project SDK setup");
                        remote_dev_properties.push(("jdk.configure.existing", "false"));
                    },
                    _ => {
                        bail!("Unsupported value for REMOTE_DEV_JDK_DETECTION variable: '{}'", remote_dev_jdk_detection_value);
                    },
                }
            }
            Err(_) => {
                info!("Enable JDK auto-detection and project SDK setup by default. Set REMOTE_DEV_JDK_DETECTION=false to disable.");
                remote_dev_properties.push(("jdk.configure.existing", "true"));
            }
        }

        let is_docker = is_running_in_docker()?;
        info!("Run host in docker: {is_docker}");
        if is_docker {
            remote_dev_properties.push(("remotedev.run.in.docker", "true"));
            remote_dev_properties.push(("unknown.sdk.show.editor.actions", "false"));
        }

        let result = remote_dev_properties
            .into_iter()
            .map(|x| IdeProperty {
                key: x.0.to_string(),
                value: x.1.to_string(),
            })
            .collect();

        Ok(result)
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
        let default_properties = read_file_to_end(&distribution_properties).context("Failed to read IDE properties file")?;

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

    fn init_project_trust_file_if_needed(&self) -> Result<PathBuf> {
        let ij_started_command = (&self.ij_starter_command).as_str();
        match ij_started_command {
            "cwmHost" | "cwmHostNoLobby" | "remoteDevHost" => {
                debug!("Running with '{ij_started_command}' command, considering making project trust checks")
            }
            _ => { }
        };

        let ij_host_config_dir = &self.config_dir;
        let trust_file_path = ij_host_config_dir.join("accepted-trust-warning");

        if trust_file_path.exists() {
            debug!("{trust_file_path:?} exists, considering project trusted");
            return Ok(trust_file_path)
        }

        let vars = [
            "REMOTE_DEV_TRUST_PROJECTS",
            "REMOTE_DEV_NON_INTERACTIVE"
        ];

        for key in vars {
            match env::var(key) {
                Ok(_) => {
                    debug!("{key:?} env var is set, considering project trusted");
                    return Ok(trust_file_path)
                }
                Err(_) => {
                    debug!("{key:?} env var is not set")
                }
            };
        }

        create_trust_file(&trust_file_path)
            .context("Failed to create a trust file")?;

        Ok(trust_file_path)
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

#[allow(non_snake_case)]
#[derive(Debug)]
struct IjStarterCommand {
    pub ij_command: String,
    pub is_project_path_required: bool,
    pub is_arguments_required: bool,
}

impl std::fmt::Display for IjStarterCommand {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let path = if self.is_project_path_required {"/path/to/project"} else { "" };
        let args = if self.is_arguments_required {"[arguments...]"} else { "" };
        write!(f, "{} {}", path, args)
    }
}

fn get_known_intellij_commands() -> HashMap<&'static str, IjStarterCommand> {
    std::collections::HashMap::from([
        ("run", IjStarterCommand {ij_command: "cwmHostNoLobby".to_string(), is_project_path_required: true, is_arguments_required: true}),
        ("status", IjStarterCommand {ij_command: "cwmHostStatus".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("cwmHostStatus", IjStarterCommand {ij_command: "cwmHostStatus".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("remoteDevStatus", IjStarterCommand {ij_command: "remoteDevStatus".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("dumpLaunchParameters", IjStarterCommand {ij_command: "dump-launch-parameters".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("warmup", IjStarterCommand {ij_command: "warmup".to_string(), is_project_path_required: true, is_arguments_required: true}),
        ("warm-up", IjStarterCommand {ij_command: "warmup".to_string(), is_project_path_required: true, is_arguments_required: true}),
        ("invalidate-caches", IjStarterCommand {ij_command: "invalidateCaches".to_string(), is_project_path_required: true, is_arguments_required: false}),
        ("installPlugins", IjStarterCommand {ij_command: "installPlugins".to_string(), is_project_path_required: false, is_arguments_required: true}),
        ("stop", IjStarterCommand {ij_command: "exit".to_string(), is_project_path_required: true, is_arguments_required: false}),
        ("registerBackendLocationForGateway", IjStarterCommand {ij_command: "".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("help", IjStarterCommand{ij_command: "".to_string(), is_project_path_required: false, is_arguments_required: false}),
    ])
}

#[allow(non_snake_case)]
#[derive(Debug)]
struct RemoteDevEnvVar {
    pub name: String,
    pub description: String,
}

#[allow(non_snake_case)]
#[derive(Debug)]
struct RemoteDevEnvVars(Vec<RemoteDevEnvVar>);

impl std::fmt::Display for RemoteDevEnvVars {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let max_len = self
            .0
            .iter()
            .map(|remote_dev_env_var| remote_dev_env_var.name.len())
            .max()
            .unwrap_or(0);

        for remote_dev_env_var in &self.0 {
            write!(f, "\t{:max_len$} {}\n", remote_dev_env_var.name, remote_dev_env_var.description)?;
        }
        Ok(())
    }
}

fn get_remote_dev_env_vars() -> RemoteDevEnvVars {
    RemoteDevEnvVars(vec![
        RemoteDevEnvVar {name: "REMOTE_DEV_SERVER_TRACE".to_string(), description: "set to any value to get more debug output from the startup script".to_string()},
        RemoteDevEnvVar {name: "REMOTE_DEV_SERVER_JCEF_ENABLED".to_string(), description: "set to '1' to enable JCEF (embedded chromium) in IDE".to_string()},
        RemoteDevEnvVar {name: "REMOTE_DEV_SERVER_USE_SELF_CONTAINED_LIBS".to_string(), description: "set to '0' to skip using bundled X11 and other linux libraries from plugins/remote-dev-server/selfcontained. Use everything from the system. by default bundled libraries are used".to_string()},
        RemoteDevEnvVar {name: "REMOTE_DEV_LAUNCHER_NAME_FOR_USAGE".to_string(), description: "set to any value to use as the script name in this output".to_string()},
        RemoteDevEnvVar {name: "REMOTE_DEV_TRUST_PROJECTS".to_string(), description: "set to any value to skip project trust warning (will execute build scripts automatically)".to_string()},
        RemoteDevEnvVar {name: "REMOTE_DEV_NON_INTERACTIVE".to_string(), description: "set to any value to skip all interactive shell prompts (set automatically if running without TTY)".to_string()},
    ])
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
    let remote_dev_commands = &get_known_intellij_commands();
    let mut remote_dev_commands_message = String::from("\nExamples:\n");
    for (command_name, command_parameters) in remote_dev_commands.iter() {
        let command_string = format!("\t./remote-dev-server {command_name} {command_parameters}\n");
        remote_dev_commands_message.push_str(command_string.as_str())
    }

    let remote_dev_environment_variables = get_remote_dev_env_vars();

    let remote_dev_environment_variables_message = format!("Environment variables:\n{remote_dev_environment_variables}");

    let help_message = "\nUsage: ./remote-dev-server [ij_command_name] [/path/to/project] [arguments...]";
    println!("{help_message}{remote_dev_commands_message}{remote_dev_environment_variables_message}");
}

fn init_env_vars() -> Result<()> {
    let remote_dev_launcher_name_for_usage = get_remote_dev_launcher_name_for_usage()?;
    let remote_dev_env_var_values = vec![
        ("IDEA_RESTART_VIA_EXIT_CODE", "88"),
        ("ORG_JETBRAINS_PROJECTOR_SERVER_ENABLE_WS_SERVER", "false"),
        ("ORG_JETBRAINS_PROJECTOR_SERVER_ATTACH_TO_IDE", "false"),
        ("REMOTE_DEV_LAUNCHER_NAME_FOR_USAGE", &remote_dev_launcher_name_for_usage),
    ];

    for (key, value) in remote_dev_env_var_values {
        match env::var(key) {
            Ok(old_value) => {
                let backup_key = format!("INTELLIJ_ORIGINAL_ENV_{key}");
                debug!("'{key}' has already been assigned the value {old_value}, overriding to {value}. \
                        Old value will be preserved for child processes.");
                env::set_var(backup_key, old_value)
            }
            Err(_) => { }
        }

        env::set_var(key, value)
    }

    return Ok(())
}

fn create_trust_file(trust_file_path: &PathBuf) -> Result<()> {
    info!(
            "\nOpening the project with this launcher will trust it and execute build scripts in it.\n\
            You can read more about this at https://www.jetbrains.com/help/idea/project-security.html\n\
            This warning is only shown once per project\n\
            Run ./remote-dev-server --help to see how to automate this check\n\n\
            Press ENTER to continue, or Ctrl-C to abort execution\n"
        );

    let mut input = String::new();
    let _i = std::io::stdin().read_line(&mut input).context("Failed to read from stdin")?;

    let file = File::create(&trust_file_path).context("Failed to create trust file")?;
    debug!("File '{:?}' has been created", file);

    Ok(())
}

fn get_remote_dev_launcher_name_for_usage() -> Result<String>{
    let current_exe = get_current_exe();
    let remote_dev_launcher_name_for_usage_with_exit_code_check = current_exe.file_name()
        .context("Failed to get current filename")?.to_os_string();

    let result = remote_dev_launcher_name_for_usage_with_exit_code_check.into_string()
        .expect("Failed to convert current executable name to string");

    Ok(result)
}

fn escape_for_idea_properties(path: &Path) -> String {
    path.to_string_lossy().replace("\\", "\\\\")
}
