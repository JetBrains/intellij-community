// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
#![warn(
absolute_paths_not_starting_with_crate,
elided_lifetimes_in_paths,
explicit_outlives_requirements,
keyword_idents,
macro_use_extern_crate,
meta_variable_misuse,
missing_abi,
missing_copy_implementations,
missing_debug_implementations,
non_ascii_idents,
noop_method_call,
pointer_structural_match,
rust_2021_incompatible_closure_captures,
rust_2021_incompatible_or_patterns,
rust_2021_prefixes_incompatible_syntax,
rust_2021_prelude_collisions,
single_use_lifetimes,
trivial_numeric_casts,
unsafe_op_in_unsafe_fn,
unstable_features,
unused_crate_dependencies,
unused_extern_crates,
unused_import_braces,
unused_lifetimes,
unused_macro_rules,
unused_qualifications,
unused_results,
variant_size_differences
)]

mod java;
mod remote_dev;
mod default;

use serde::{Deserialize, Serialize};
use std::env;
use std::fs::File;
use std::path::PathBuf;
use log::{debug, error, LevelFilter};
use native_dialog::{MessageDialog, MessageType};
use simplelog::{ColorChoice, CombinedLogger, Config, TerminalMode, TermLogger, WriteLogger};
use crate::default::DefaultLaunchConfiguration;
use anyhow::{bail, Result};
use crate::remote_dev::RemoteDevLaunchConfiguration;

pub fn main_lib() {
    let show_error_ui = match env::var(DO_NOT_SHOW_ERROR_UI_ENV_VAR) {
        Ok(_) => false,
        Err(_) => {
            let cmd_args: Vec<String> = env::args().collect();
            let is_remote_dev = cmd_args.len() > 1 && cmd_args[1] == "--remote-dev";

            !is_remote_dev
        }
    };

    let main_result = main_impl();
    unwrap_with_human_readable_error(main_result, show_error_ui);
}

fn main_impl() -> Result<()> {
    println!("Initializing logger");

    let term_logger = TermLogger::new(LevelFilter::Debug, Config::default(), TerminalMode::Mixed, ColorChoice::Auto);

    // TODO: do not crash if failed to create a log file?
    let file_logger = WriteLogger::new(LevelFilter::Debug, Config::default(), File::create("launcher.log")?);

    // TODO: set agreed configuration (probably simple logger instead of pretty colors for terminal) or replace the lib with our own code
    let loggers: Vec<Box<dyn simplelog::SharedLogger>> = vec![ term_logger, file_logger ];

    CombinedLogger::init(loggers)?;

    // lets the panic on JVM thread crash the launcher (or not?)
    let orig_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |panic_info| {
        error!("{panic_info:?}");
        // TODO: crash on JVM thread
        // for l in &loggers {
        //     l.flush()
        // }

        orig_hook(panic_info);
        std::process::exit(1);
    }));

    debug!("Launching");

    let configuration = &get_configuration()?;

    debug!("Preparing runtime");
    let java_home = &configuration.prepare_for_launch()?;
    debug!("Resolved runtime: {java_home:?}");

    debug!("Resolving vm options");
    let vm_options = get_full_vm_options(configuration)?;

    debug!("Resolving args for vm launch");
    let args = configuration.get_args();

    let result = java::run_jvm_and_event_loop(java_home, vm_options, args.to_vec());

    log::logger().flush();

    result
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct ProductInfo {
    pub productCode: String,
    pub productVendor: String,
    pub dataDirectoryName: String,
    pub launch: Vec<ProductInfoLaunchField>,
}

#[allow(non_snake_case)]
#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct ProductInfoLaunchField {
    pub os: String,
    pub vmOptionsFilePath: String,
    pub bootClassPathJarNames: Vec<String>,
    pub additionalJvmArguments: Vec<String>
}

impl ProductInfo {
    pub fn get_current_platform_launch_field(&self) -> Result<&ProductInfoLaunchField> {
        let current_os = env::consts::OS;
        let os_specific_launch_field =
            self.launch.iter().find(|&l| l.os.to_lowercase() == current_os);

        match os_specific_launch_field {
            None => bail!("Could not find current os {current_os} launch element in product-info.json 'launch' field"),
            Some(x) => Ok(x),
        }
    }
}

trait LaunchConfiguration {
    fn get_args(&self) -> &[String];

    fn get_intellij_vm_options(&self) -> Result<Vec<String>>;
    fn get_properties_file(&self) -> Result<PathBuf>;
    fn get_class_path(&self) -> Result<Vec<String>>;

    fn prepare_for_launch(&self) -> Result<PathBuf>;
}

pub fn is_remote_dev(cmd_args: &[String]) -> bool {
    // 0 arg is binary itself
    cmd_args.len() > 1 && cmd_args[1] == "--remote-dev"
}

fn get_configuration() -> Result<Box<dyn LaunchConfiguration>> {
    let cmd_args: Vec<String> = env::args().collect();
    
    let is_remote_dev = is_remote_dev(&cmd_args);

    let (remote_dev_project_path, ij_args) = match is_remote_dev {
        true => {
            let remote_dev_args = RemoteDevLaunchConfiguration::parse_remote_dev_args(&cmd_args)?;
            (remote_dev_args.project_path, remote_dev_args.ij_args)
        },
        false => (None, cmd_args)
    };

    if is_remote_dev {
        // required for the most basic launch (e.g. showing help)
        // as there may be nothing on user system and we'll crash
        RemoteDevLaunchConfiguration::setup_font_config()?;
    }

    let default = DefaultLaunchConfiguration::new(ij_args)?;

    match remote_dev_project_path {
        None => Ok(Box::new(default)),
        Some(x) => {
            let config = RemoteDevLaunchConfiguration::new(x, default)?;
            Ok(Box::new(config))
        }
    }
}

pub const DO_NOT_SHOW_ERROR_UI_ENV_VAR: &str = "DO_NOT_SHOW_ERROR_UI";

fn unwrap_with_human_readable_error<S>(result: Result<S>, show_error_ui: bool) -> S {
    match result {
        Ok(x) => { x }
        Err(e) => {
            eprintln!("{e:?}");
            error!("{e:?}");

            if show_error_ui {
                show_fail_to_start_message("Failed to start", format!("{e:?}").as_str())
            }

            std::process::exit(1);
        }
    }
}

fn get_full_vm_options(configuration: &Box<dyn LaunchConfiguration>) -> Result<Vec<String>> {
    let mut full_vm_options: Vec<String> = Vec::new();

    debug!("Resolving IDE properties file");
    // 1. properties file
    match configuration.get_properties_file() {
        Ok(p) => {
            let path_string = p.to_string_lossy();
            let vm_option = format!("-Didea.properties.file={path_string}");
            full_vm_options.push(vm_option);
        }
        Err(_) => {
            debug!("IDE properties file is not set, skipping setting vm option")
        }
    };

    debug!("Resolving classpath");
    // 2. classpath
    let class_path_separator = get_class_path_separator();
    let class_path = configuration.get_class_path()?.join(class_path_separator);
    let class_path_vm_option = "-Djava.class.path=".to_string() + class_path.as_str();
    full_vm_options.push(class_path_vm_option);

    debug!("Resolving IDE VM options");
    // 3. vmoptions
    let intellij_vm_options = configuration.get_intellij_vm_options()?;
    full_vm_options.extend_from_slice(&intellij_vm_options);

    Ok(full_vm_options)
}

#[cfg(any(target_os = "linux", target_os = "macos"))]
fn get_class_path_separator<'a>() -> &'a str {
    ":"
}

#[cfg(target_os = "windows")]
fn get_class_path_separator<'a>() -> &'a str {
    ";"
}

fn show_fail_to_start_message(title: &str, text: &str) {
    let result = MessageDialog::new()
        .set_title(title)
        .set_text(text)
        .set_type(MessageType::Error)
        .show_alert();

    match result {
        Ok(_) => { }
        Err(e) => {
            // TODO: proper message
            error!("Failed to show error message: {e:?}")
        }
    }
}