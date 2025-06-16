// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

use std::collections::HashMap;
use std::fs::File;
use std::io::{BufRead, BufReader, BufWriter, IsTerminal, Write};
use std::path::{Path, PathBuf};
use std::{env, fs};

use anyhow::{bail, Context, Result};
#[allow(unused_imports)]
use log::{debug, error, info};

use crate::docker::is_running_in_docker;
use crate::*;

pub struct RemoteDevLaunchConfiguration {
    default: DefaultLaunchConfiguration,
    started_via_remote_dev_launcher: bool,
}

impl LaunchConfiguration for RemoteDevLaunchConfiguration {
    fn get_args(&self) -> &[String] {
        self.default.get_args()
    }

    fn get_vm_options(&self) -> Result<Vec<String>> {
        let mut vm_options = self.default.get_vm_options()?;

        let has_xmx = vm_options.iter().any(|opt| opt.starts_with("-Xmx"));

        if !has_xmx {
            // TODO: add default Xmx to productInfo as right now we patch the user one
            vm_options.push("-Xmx2048m".to_string());
        }

        #[cfg(target_os = "linux")]
        if is_wsl2() && !parse_bool_env_var("REMOTE_DEV_SERVER_ALLOW_IPV6_ON_WSL2", false)? {
            vm_options.push("-Djava.net.preferIPv4Stack=true".to_string())
        }

        if let Some(command) = self.get_args().get(0) {
            match command.as_str() {
                "remoteDevStatus" | "cwmHostStatus" => {
                    vm_options.retain(|opt| {
                        if opt.starts_with("-agentlib:jdwp=") {
                            info!("Dropping debug option to prevent startup failure due to port conflict: {}", opt);
                            false
                        } else {
                            true
                        }
                    });
                }
                _ => {}
            }
        }

        Ok(vm_options)
    }

    fn get_custom_properties_file(&self) -> Result<PathBuf> {
        let remote_dev_properties_file = self.write_merged_properties_file()
            .context("Failed to write remote dev IDE properties file")?;

        Ok(remote_dev_properties_file)
    }

    fn get_class_path(&self) -> Result<Vec<String>> {
        self.default.get_class_path()
    }

    fn prepare_for_launch(&self) -> Result<(PathBuf, &str)> {
        init_env_vars(&self.default).context("Preparing environment variables")?;

        preload_native_libs(&self.default.ide_home).context("Preloading native libraries")?;
        self.default.prepare_for_launch()
    }
}

impl RemoteDevLaunchConfiguration {
    #[allow(clippy::new_ret_no_self)]
    pub fn new(exe_path: &Path, args: Vec<String>, started_via_remote_dev_launcher: bool) -> Result<Box<dyn LaunchConfiguration>> {
        let (_, default_cfg_args) = Self::parse_remote_dev_args(&args)?;

        let default_cfg = DefaultLaunchConfiguration::new(exe_path, default_cfg_args)?;

        let configuration = Self::create(default_cfg, started_via_remote_dev_launcher)?;
        Ok(Box::new(configuration))
    }

    // remote-dev-server.exe ij_command_name /path/to/project args
    fn parse_remote_dev_args(args: &[String]) -> Result<(Option<PathBuf>, Vec<String>)> {
        debug!("Parsing remote dev command-line arguments");

        if args.len() < 2 {
            print_help();
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

        let should_parse_project_path = ij_starter_command.ij_command == "warmup";

        let project_path = if args.len() > 2 {
            let arg = args[2].as_str();
            if arg == "-h" || arg == "--help" {
                let args = vec![
                    args[0].to_string(),
                    "remoteDevShowHelp".to_string(),
                    ij_starter_command.ij_command
                ];
                return Ok((None, args));
            }

            if should_parse_project_path {
                Some(Self::get_project_path(arg)?)
            } else {
                None
            }
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

        Ok((project_path, ij_args))
    }

    fn get_project_path(argument: &str) -> Result<PathBuf> {
        let project_path_string = argument;

        // TODO: expand tilde
        let project_path = PathBuf::from(project_path_string);
        if !project_path.exists() {
            print_help();
            bail!("Project path does not exist: {project_path_string}");
        }

        Ok(project_path)
    }

    fn create(default: DefaultLaunchConfiguration, started_via_remote_dev_launcher: bool) -> Result<Self> {
        let config = RemoteDevLaunchConfiguration {
            default,
            started_via_remote_dev_launcher,
        };

        Ok(config)
    }

    fn get_remote_dev_properties(&self) -> Result<Vec<IdeProperty>> {
        let mut remote_dev_properties = vec![
            // TODO: remove once all of this is disabled for remote dev
            ("jb.privacy.policy.text", "<!--999.999-->"),
            ("jb.consents.confirmation.enabled", "false"),
            ("idea.initially.ask.config", "never"),
            ("ide.show.tips.on.startup.default.value", "false"),

            // Prevent CWM plugin from being disabled, as it's required for Remote Dev
            ("idea.required.plugins.id", "com.jetbrains.codeWithMe"),

            // Automatic updates are not supported by Remote Development
            // It should be done manually by selecting the correct IDE version in JetBrains Gateway
            // For pre-configured environment (e.g., cloud) the version is fixed anyway
            ("ide.no.platform.update", "true"),

            // Don't ask user about indexes download
            ("shared.indexes.download", "true"),
            ("shared.indexes.download.auto.consent", "true"),

            // TODO: disable once IDEA doesn't require JBA login for remote dev
            ("eap.login.enabled", "false"),

            // TODO: CWM-5782 figure out why posix_spawn / jspawnhelper does not work in tests
            // ("jdk.lang.Process.launchMechanism", "vfork"),
        ];

        if self.started_via_remote_dev_launcher {
            remote_dev_properties.push(("ide.started.from.remote.dev.launcher", "true"))
        }

        match parse_bool_env_var_optional("REMOTE_DEV_JDK_DETECTION")? {
            Some(remote_dev_jdk_detection_value) => {
                if remote_dev_jdk_detection_value {
                    info!("Enable JDK auto-detection and project SDK setup");
                    remote_dev_properties.push(("jdk.configure.existing", "true"));
                } else {
                    info!("Disable JDK auto-detection and project SDK setup");
                    remote_dev_properties.push(("jdk.configure.existing", "false"));
                }
            }
            None => {
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

    fn write_merged_properties_file(&self) -> Result<PathBuf> {
        let pid = std::process::id();
        let filename = format!("pid.{pid}.temp.remote-dev.properties");
        let path = get_ide_temp_directory(&self.default)?.join(filename);

        if let Some(dir) = path.parent() {
            fs::create_dir_all(dir)
                .with_context(|| format!("Failed to create to parent folder for IDE properties file at path {dir:?}"))?
        }

        let file = File::create(&path)?;
        let mut writer = BufWriter::new(file);

        let remote_dev_properties = self.get_remote_dev_properties()?;
        for p in remote_dev_properties {
            let key = p.key.as_str();
            let value = p.value.as_str();
            writeln!(&mut writer, "{key}={value}")?;
        }

        writer.flush()?;

        Ok(path)
    }
}

fn get_ide_temp_directory(default: &DefaultLaunchConfiguration) -> Result<PathBuf> {
    Ok(default.user_caches_dir.join("tmp"))
}

#[cfg(not(target_os = "linux"))]
fn setup_font_config(default: &DefaultLaunchConfiguration) -> Result<Option<(String, String)>> {
    // fontconfig is Linux-specific
    Ok(None)
}

#[cfg(target_os = "linux")]
fn setup_font_config(default: &DefaultLaunchConfiguration) -> Result<Option<(String, String)>> {
    use std::hash::{Hash, Hasher};

    let ide_home_path = &default.ide_home;
    let source_font_config_file = ide_home_path.join("plugins/remote-dev-server/selfcontained/fontconfig/fonts.conf");
    if !source_font_config_file.is_file() {
        error!("Missing self-contained font config file at {}; fontconfig setup will be skipped", source_font_config_file.to_string_lossy());
        return Ok(None);
    }

    let extra_fonts_path_config = ide_home_path.join("plugins/remote-dev-server/selfcontained/fontconfig/fonts");
    let extra_fonts_path_jbr = ide_home_path.join("jbr/lib/fonts");

    if !extra_fonts_path_config.as_path().is_dir() {
        bail!("Extra fonts in '{}' are missing", extra_fonts_path_config.to_string_lossy())
    }

    if !extra_fonts_path_jbr.as_path().is_dir() {
        bail!("Extra fonts in '{}' are missing", extra_fonts_path_jbr.to_string_lossy())
    }

    let extra_fonts_path_config = extra_fonts_path_config.to_string_lossy().to_string();
    let extra_fonts_path_jbr = extra_fonts_path_jbr.to_string_lossy().to_string();

    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    source_font_config_file.hash(&mut hasher);
    let patched_dir = get_ide_temp_directory(default)?.join(format!("jbrd-fontconfig-{}", hasher.finish()));
    fs::create_dir_all(&patched_dir).context("Creating directory for temporary fontconfig")?;

    let patched_file_path = patched_dir.join("fonts.conf");

    let patched_file = File::create(&patched_file_path).context("Creating patched fonts.conf file")?;
    let mut writer = BufWriter::new(patched_file);

    let source_font_config_file = File::open(&source_font_config_file).context("Failed to open source fonts.conf file")?;

    for l in BufReader::new(source_font_config_file).lines() {
        let mut l = l.context("Failed to read fonts.conf file")?;
        l = l.replace("PATH_FONTS", &extra_fonts_path_config);
        l = l.replace("PATH_JBR", &extra_fonts_path_jbr);
        writeln!(&mut writer, "{}", l).context("Failed to write patched fonts.conf file")?;
    }

    writer.flush().context("Failed to flush patched fonts.conf file")?;

    let new_font_config = match env::var("FONTCONFIG_PATH") {
        Ok(s) if !s.is_empty() => s + ":" + patched_dir.to_string_lossy().as_ref(),
        _ => patched_dir.to_string_lossy().to_string(),
    };

    Ok(Some(("FONTCONFIG_PATH".to_string(), new_font_config)))
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
    HashMap::from([
        ("run", IjStarterCommand {ij_command: "remoteDevHost".to_string(), is_project_path_required: false, is_arguments_required: true}),
        ("status", IjStarterCommand {ij_command: "remoteDevStatus".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("cwmHostStatus", IjStarterCommand {ij_command: "cwmHostStatus".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("remoteDevStatus", IjStarterCommand {ij_command: "remoteDevStatus".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("dumpLaunchParameters", IjStarterCommand {ij_command: "dump-launch-parameters".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("printEnvVar", IjStarterCommand {ij_command: "print-env-var".to_string(), is_project_path_required: false, is_arguments_required: true}),
        ("warmup", IjStarterCommand {ij_command: "warmup".to_string(), is_project_path_required: true, is_arguments_required: true}),
        ("warm-up", IjStarterCommand {ij_command: "warmup".to_string(), is_project_path_required: true, is_arguments_required: true}),
        ("invalidate-caches", IjStarterCommand {ij_command: "invalidateCaches".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("installPlugins", IjStarterCommand {ij_command: "installPlugins".to_string(), is_project_path_required: false, is_arguments_required: true}),
        ("stop", IjStarterCommand {ij_command: "exit".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("registerBackendLocationForGateway", IjStarterCommand {ij_command: "registerBackendLocationForGateway".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("help", IjStarterCommand{ij_command: "".to_string(), is_project_path_required: false, is_arguments_required: false}),
        ("serverMode", IjStarterCommand{ij_command: "serverMode".to_string(), is_project_path_required: false, is_arguments_required: false}),
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
            writeln!(f, "\t{:max_len$} {}", remote_dev_env_var.name, remote_dev_env_var.description)?;
        }
        Ok(())
    }
}

fn get_remote_dev_env_vars() -> RemoteDevEnvVars {
    RemoteDevEnvVars(vec![
        RemoteDevEnvVar {name: "REMOTE_DEV_SERVER_TRACE".to_string(), description: "set to any value to get more debug output from the startup script".to_string()},
        RemoteDevEnvVar {name: "REMOTE_DEV_SERVER_USE_SELF_CONTAINED_LIBS".to_string(), description: "set to '0' to skip using bundled X11 and other Linux libraries from plugins/remote-dev-server/self-contained. Use everything from the system. by default bundled libraries are used".to_string()},
        RemoteDevEnvVar {name: "REMOTE_DEV_TRUST_PROJECTS".to_string(), description: "set to any value to skip project trust warning (will execute build scripts automatically)".to_string()},
        RemoteDevEnvVar {name: "REMOTE_DEV_NEW_UI_ENABLED".to_string(), description: "set to '1' to start with forced enabled new UI".to_string()},
        RemoteDevEnvVar {name: "REMOTE_DEV_NON_INTERACTIVE".to_string(), description: "set to any value to skip all interactive shell prompts (set automatically if running without TTY)".to_string()},
    ])
}

struct IdeProperty {
    key: String,
    value: String
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

fn init_env_vars(default: &DefaultLaunchConfiguration) -> Result<()> {
    let mut remote_dev_env_var_values = Vec::new();

    if !std::io::stdout().is_terminal() {
        remote_dev_env_var_values.push(("REMOTE_DEV_NON_INTERACTIVE", "1"))
    }

    // required for the most basic launch (e.g., showing help)
    // as there may be nothing on a user system and we'll crash
    let font_config_env = setup_font_config(default).context("Preparing fontconfig override")?;
    if let Some(vars) = &font_config_env {
        remote_dev_env_var_values.push((&vars.0, &vars.1));
    }

    for (key, value) in remote_dev_env_var_values {
        let backup_key = format!("INTELLIJ_ORIGINAL_ENV_{key}");
        if let Ok(old_value) = env::var(key) {
            debug!("'{key}' has already been assigned the value {old_value}, overriding to {value}. \
                        Old value will be preserved for child processes.");
            env::set_var(backup_key, old_value)
        }
        else {
            debug!("'{key}' was set to {value}. It will be unset for child processes.");
            env::set_var(backup_key, "")
        }

        env::set_var(key, value)
    }

    Ok(())
}

#[cfg(target_os = "linux")]
fn parse_bool_env_var(var_name: &str, default: bool) -> Result<bool> {
    Ok(parse_bool_env_var_optional(var_name)?.unwrap_or(default))
}

fn parse_bool_env_var_optional(var_name: &str) -> Result<Option<bool>> {
    Ok(match env::var(var_name) {
        Ok(s) if s == "0" || s.eq_ignore_ascii_case("false") => Some(false),
        Ok(s) if s == "1" || s.eq_ignore_ascii_case("true") => Some(true),
        Ok(s) if !s.is_empty() => bail!("Unsupported value '{}' for '{}' environment variable", s, var_name),
        _ => None,
    })
}

#[cfg(not(target_os = "linux"))]
fn preload_native_libs(_ide_home_dir: &Path) -> Result<()> {
    // We don't ship self-contained libraries outside of Linux
    Ok(())
}

#[cfg(target_os = "linux")]
fn preload_native_libs(ide_home_dir: &PathBuf) -> Result<()> {
    use libloading::os::unix::Library;
    use std::collections::BTreeSet;
    use std::mem;

    let use_libs = parse_bool_env_var("REMOTE_DEV_SERVER_USE_SELF_CONTAINED_LIBS", true)?;
    if !use_libs {
        return Ok(())
    }

    debug!("Loading self-contained libraries");

    let full_set = parse_bool_env_var("REMOTE_DEV_SERVER_FULL_SELF_CONTAINED_LIBS", false)?;
    debug!("Using full set of libraries: {full_set}");

    let self_contained_dir = &ide_home_dir.join("plugins/remote-dev-server/selfcontained/");
    if !self_contained_dir.is_dir() {
        error!("Self-contained dir not found at {self_contained_dir:?}. Only OS-provided libraries will be used.");
        return Ok(());
    }

    let libs_dir = &self_contained_dir.join("lib");
    if !libs_dir.is_dir() {
        bail!("Self-contained dir is present at {self_contained_dir:?}, but lib dir is missing at {libs_dir:?}")
    }

    let lib_load_order_file = &self_contained_dir.join(if full_set { "lib-load-order" } else { "lib-load-order-limited" });
    if !lib_load_order_file.is_file() {
        bail!("Self-contained dir is present at {self_contained_dir:?}, but load order file is missing at {lib_load_order_file:?}")
    }

    let mut provided_libs = BTreeSet::new();
    let filter_extensions = vec![
        "hmac".to_string()
    ];

    for f in fs::read_dir(libs_dir)? {
        let entry = f?;
        let file_name = entry.file_name();
        let file_extension = entry.path().extension().map(|ext| ext.to_string_lossy().to_string());

        if file_extension.is_some_and(|ext| filter_extensions.contains(&ext)) {
            debug!("Filter the file by extension '{file_name:?}'");
            continue;
        }

        if !provided_libs.insert(file_name.clone()) {
            bail!("Two files with the same name '{file_name:?}' in {libs_dir:?}")
        }
    }

    let provided_libs_initial_len = provided_libs.len();
    debug!("Provided libraries count: {provided_libs_initial_len}");

    let file = File::open(lib_load_order_file)?;
    let lines = BufReader::new(file).lines();

    let mut ordered_libs_to_load = vec![];

    for line in lines {
        let soname = line?;
        ordered_libs_to_load.insert(ordered_libs_to_load.len(), soname);
    }

    debug!("Libraries to load: {}", ordered_libs_to_load.len());

    for soname in &ordered_libs_to_load {
        debug!("{soname}: trying to load");

        let lib_file = &libs_dir.join(&soname);
        if !lib_file.is_file() {
            bail!("{soname} needs to be loaded as self-contained, but is missing at {lib_file:?}");
        };

        unsafe {
            let lib = Library::open(lib_file.to_str(), libc::RTLD_LAZY | libc::RTLD_GLOBAL)?;

            // handle intentionally lost to keep the library loaded; RTLD_NODELETE is non-POSIX
            mem::forget(lib)
        }

        debug!("{soname}: loaded");

        let file_name = lib_file.file_name()
            .with_context(|| format!("Failed to get the filename from {lib_file:?}"))?;

        if !provided_libs.remove(file_name) {
            bail!("Loaded {soname} as {lib_file:?}, but failed to resolve it as a file in {libs_dir:?} previously")
        }
    }

    if full_set {
        if !provided_libs.is_empty() {
            let error: Vec<String> = provided_libs
                .iter()
                .map(|os| os.to_string_lossy().to_string())
                .collect();
            let joined = error.join(", ");
            bail!("Libs were provided but not loaded: {joined}")
        }

        // we should have more detailed logs in this count,
        // but just to be safe we'll do this simple assertion
        if ordered_libs_to_load.len() != provided_libs_initial_len {
            bail!("Library count mismatch");
        }
    }

    debug!("All self-contained libraries ({}) were loaded", ordered_libs_to_load.len());

    Ok(())
}

#[cfg(target_os = "linux")]
fn is_wsl2() -> bool {
    fs::read_to_string("/proc/sys/kernel/osrelease")
        .map(|x| x.contains("WSL2"))
        .unwrap_or(false)
}
