use std::{env};
use std::fs::File;
use std::io::BufReader;
use std::path::{Path};
use is_executable::is_executable;
use log::{debug, error, info, LevelFilter, warn};
use native_dialog::{MessageDialog, MessageType};
use serde::Deserialize;
use simplelog::{ColorChoice, CombinedLogger, Config, TerminalMode, TermLogger, WriteLogger};

fn main() {
    println!("Initializing logger");

    // TODO: set agreed configuration (probably simple logger instead of pretty colors for terminal) or replace the lib with our own code
    CombinedLogger::init(
        vec![
            TermLogger::new(LevelFilter::Debug, Config::default(), TerminalMode::Mixed, ColorChoice::Auto),
            WriteLogger::new(LevelFilter::Debug, Config::default(), File::create("launcher.log").unwrap()),
        ]
    ).unwrap();


    info!("Launching");

    let current_exe = env::current_exe().unwrap();
    debug!("Resolve current executable path as '{:?}'", current_exe);

    let ide_bin = current_exe.parent().unwrap();
    debug!("Resolved ide bin dir as '{:?}'", ide_bin);

    let ide_home = ide_bin.parent().unwrap();
    debug!("Resolved ide home dir as '{:?}'", ide_home);

    let config_home = get_config_home();
    debug!("Resolved config home as '{:?}'", config_home);

    // TODO: replace
    // let launcher = Launcher { ide_home };
    let launcher = Launcher { ide_home: Path::new("/home/haze/tmp/1/idea-IU-221.SNAPSHOT"), config_home: config_home.as_ref() };
    let jre_home = launcher.get_jre_home();
}

struct Launcher<'a> {
    ide_home: &'a Path,
    config_home: &'a Path
}

impl<'a> Launcher<'a> {
    fn get_jre_home(&self) -> Box<Path> {
        // TODO: this is a behaviour change (as we're not patching the binary the same way we are patching the executable template)
        let product_info = self.get_product_info().unwrap_or(ProductInfo { productCode: String::from("") });

        // TODO: potentially a behaviour change if _JDK env var is defined
        let product_code_env_var = product_info.productCode + "_JDK";

        match env::var(product_code_env_var) {
            Ok(s) => {
                match self.get_jre_root_from_product_code_env_var(&s) {
                    None => {}
                    Some(jdk) => { return jdk }
                }
            }
            Err(_) => {}
        }

        Box::from(Path::new(""))
    }

    // if [ -z "$JRE" ] && [ -s "${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__.jdk" ]; then
    // USER_JRE=$(cat "${CONFIG_HOME}/__product_vendor__/__system_selector__/__vm_options__.jdk")
    // if [ -x "$USER_JRE/bin/java" ]; then
    // JRE="$USER_JRE"
    // fi
    // fi
    fn get_user_jre(&self) -> Option<Box<Path>> {

    }

    // if [ -n "$__product_uc___JDK" ] && [ -x "$__product_uc___JDK/bin/java" ]; then
    // JRE="$__product_uc___JDK"
    // fi
    fn get_jre_root_from_product_code_env_var(&self, env_var_value: &String) -> Option<Box<Path>> {
        if !env_var_value.is_empty() {
            debug!("Env var {} is not set, skipping JDK detection from it", env_var_value);
            return Option::None
        }

        let product_jdk_dir = Path::new(env_var_value);
        let java_executable = product_jdk_dir.join("bin").join("java");

        if !java_executable.exists() {
            warn!("Java executable from JDK {:?} does not exist", java_executable);
            return Option::None
        }

        // TODO: write the same code ourselves instead of using is_executable crate?
        if !is_executable(java_executable.as_path()) {
            warn!("{:?} is not an executable file", java_executable);
            return Option::None
        }

        return Option::Some(Box::from(java_executable));
    }

    fn get_product_info(&self) -> Option<ProductInfo> {
        let buf = self.ide_home.join("product-info.json");
        let product_info_path = buf.as_path();

        let file = match File::open(product_info_path) {
            Ok(f) => { f }
            Err(e) => {
                warn!("Failed to read product-info.json by path {:?}: {:?}", product_info_path, e);
                return Option::None
            }
        };

        let reader = BufReader::new(file);
        let product_info: ProductInfo = match serde_json::from_reader(reader) {
            Ok(p) => { p }
            Err(e) => {
                warn!("Failed to read product code info from product-info.json by path {:?} {:?}", product_info_path, e);
                return Option::None
            }
        };

        Option::Some(product_info)
    }
}

#[allow(non_snake_case)]
#[derive(Deserialize)]
struct ProductInfo {
    productCode: String,
}

#[cfg(target_os = "macos")]
fn get_config_home() -> Box<Path> {
    let user_home = get_user_home().join(".config");
    Box::from(user_home)
}

// CONFIG_HOME="${XDG_CONFIG_HOME:-${HOME}/.config}"
#[cfg(target_os = "linux")]
fn get_config_home() -> Box<Path> {
    let xdg_config_home = get_xdg_config_home();

    match xdg_config_home {
        Some(p) => { Box::from(p) }
        None => {
            let user_home = get_user_home().join(".config");
            Box::from(user_home)
        }
    }
}

#[cfg(target_os = "linux")]
fn get_xdg_config_home() -> Option<Box<Path>> {
    let xdg_config_home = env::var("XDG_CONFIG_HOME").unwrap_or(String::from(""));
    debug!("XDG_CONFIG_HOME={}", xdg_config_home);

    if xdg_config_home.is_empty() {
        return Option::None
    }

    let path = Path::new(&xdg_config_home);
    if !path.is_absolute() {
        // TODO: consider change
        warn!("XDG_CONFIG_HOME is not set to an absolute path, this may be a misconfiguration");
    }

    Option::Some(Box::from(path))
}


// used in ${HOME}/.config
// TODO: is this the same as env:
#[cfg(any(target_os = "linux", target_os = "macos"))]
fn get_user_home() -> Box<Path> {
    // TODO: dirs::home_dir seems better then just simply using $HOME as it checks `getpwuid_r`

    match env::var("HOME") {
        Ok(s) => {
            debug!("User home directory resolved as '{}'", s);
            let path = Path::new(&s);
            if !path.is_absolute() {
                warn!("User home directory is not absolute, this may be a misconfiguration");
            }

            return Box::from(path)
        }
        Err(e) => {
            // TODO: this seems wrong
            warn!("Failed to get $HOME env var value: {:?}, using / as home dir", e);

            Box::from(Path::new("/"))
        }
    }
}


fn show_fail_to_start_message(title: &str, text: &str) {
    let result = MessageDialog::new()
        .set_title(title)
        .set_text(text)
        .set_type(MessageType::Error)
        .show_alert();

    match result {
        Ok(_) => {}
        Err(e) => {
            error!("Oh {:?}", e)
        }
    }
}