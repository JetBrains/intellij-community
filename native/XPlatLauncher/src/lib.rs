extern crate core;

mod errors;
mod java;
mod utils;
mod remote_dev;
mod default;

use serde::{Deserialize, Serialize};
use std::{env};
use std::fs::{File};
use std::io::{BufReader};
use std::path::{Path, PathBuf};
use log::{debug, error, info, LevelFilter, Log, warn};
use native_dialog::{MessageDialog, MessageType};
use simplelog::{ColorChoice, CombinedLogger, Config, TerminalMode, TermLogger, WriteLogger};
use crate::default::DefaultLaunchConfiguration;
use crate::errors::{err_from_string, LauncherError, Result};
use crate::remote_dev::RemoteDevLaunchConfiguration;
use crate::utils::{canonical_non_unc};

pub fn main_lib() {
    let main_result = main_impl();
    unwrap_with_human_readable_error(main_result);
    match main_impl() {
        Ok(_) => {}
        Err(e) => {

        }
    }
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
#[derive(Deserialize, Serialize, Clone)]
pub struct ProductInfo {
    pub productCode: String,
    pub classPathJars: Vec<String>,
    pub productVendor: String,
    pub dataDirectoryName: String,
    pub vmOptionsBaseFileName: String
}

trait LaunchConfiguration {
    fn get_args(&self) -> &[String];

    fn get_intellij_vm_options(&self) -> Result<Vec<String>>;
    fn get_properties_file(&self) -> Result<PathBuf>;
    fn get_class_path(&self) -> Result<Vec<String>>;

    fn prepare_for_launch(&self) -> Result<PathBuf>;
}

fn get_configuration() -> Result<Box<dyn LaunchConfiguration>> {
    let cmd_args: Vec<String> = env::args().collect();

    // 0 arg is binary itself
    let is_remote_dev = cmd_args.len() > 1 && cmd_args[1] == "--remote-dev";

    let (remote_dev_project_path, ij_args) = match is_remote_dev {
        true => {
            let remote_dev_args = RemoteDevLaunchConfiguration::parse_remote_dev_args(&cmd_args[2..])?;
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

fn unwrap_with_human_readable_error<S>(result: Result<S>) -> S {
    match result {
        Ok(x) => { x }
        Err(e) => {
            let message =
                match e {
                    LauncherError::HumanReadableError(e) => {
                        e.message
                    }
                    _ => {
                        format!("{e:?}")
                    }
                };

            eprintln!("{}", message);
            error!("{}", message);

            // TODO: detect if there's no UI?
            show_fail_to_start_message("Failed to start", format!("{message:?}").as_str());

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
        Err(e) => {
            debug!("IDE properties file is not set, skipping setting vm option")
        }
    };

    debug!("Resolving IDE VM options from files");
    // 2. .vmoptions
    let intellij_vm_options = configuration.get_intellij_vm_options()?;
    for vm_option in intellij_vm_options {
        full_vm_options.push(vm_option);
    }

    debug!("Resolving classpath");
    // 3. classpath
    let class_path_separator = get_class_path_separator();
    let class_path = configuration.get_class_path()?.join(class_path_separator);
    let class_path_vm_option = "-Djava.class.path=".to_string() + class_path.as_str();

    full_vm_options.push(class_path_vm_option);

    // 4. TODO: find out if that's still needed
    // full_vm_options.push("-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader".to_string());
    full_vm_options.push("-XX:+StartAttachListener".to_string());

    // TODO: shouldn't this already be in .vmoptions?
    // answer: it's platform-specific :D
    // linux - replaced
    // windows - resource file
    // macos - plist
    // answer: add to product-info.json
    full_vm_options.push("--add-opens=java.base/java.io=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.lang=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.net=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.nio=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.nio.charset=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.text=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.time=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.util=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/sun.security.ssl=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.base/sun.security.util=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/com.sun.java.swing.plaf.gtk=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/java.awt=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/java.awt.event=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/java.awt.image=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/java.awt.font=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/javax.swing=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.awt=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.font=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.java2d=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=java.desktop/sun.swing=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED".to_string());
    full_vm_options.push("--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED".to_string());

    // TODO:
    // "-XX:ErrorFile=$HOME/java_error_in___vm_options___%p.log" \
    // "-XX:HeapDumpPath=$HOME/java_error_in___vm_options___.hprof" \

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