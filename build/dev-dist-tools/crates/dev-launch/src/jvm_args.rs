//! The JVM arguments of a distribution, and the text of the `java` argument file that holds them.
//!
//! The file holds the caller flags, three fixed properties, the distribution properties, the class path and the main
//! class. A caller can add program arguments after the main class. `java` reads each argument after the main class as
//! a program argument, so it expands no `@<file>` there.

use std::borrow::Cow;
use std::path::{Path, PathBuf};

use anyhow::{Context as _, anyhow, bail};
use indexmap::IndexMap;

use crate::properties::{
    ProductInfo, RUNTIME_MODULE_REPOSITORY_PROPERTY, custom_command, properties_of_files, put_system_property, read_lines,
};

/// The class path separator of the JVM on the host.
#[cfg(windows)]
const PATH_LIST_SEPARATOR: &str = ";";
/// The class path separator of the JVM on the host.
#[cfg(not(windows))]
const PATH_LIST_SEPARATOR: &str = ":";

/// What a distribution adds to the command line of `java`.
pub struct Distribution {
    /// The home that the IDE starts from, an absolute path.
    pub home: String,
    pub info: ProductInfo,
    /// The properties of [`properties_of_files`].
    pub properties: IndexMap<String, String>,
    pub main_class: String,
    /// The core class path, every path absolute.
    pub class_path: Vec<String>,
    /// The runtime module repository file of the home, when the home has one.
    pub runtime_module_repository: Option<String>,
}

/// The files of a distribution, at the paths where the caller reads them. In a Bazel action these are the paths of
/// the action inputs, not the paths in the home that the IDE starts from.
pub struct DistributionFiles<'a> {
    /// The `DevIdeConfig` file, which states `main.class.name`.
    pub ide_config: &'a Path,
    /// `bin/idea.properties`.
    pub idea_properties: &'a Path,
    /// The vmoptions file of the OS.
    pub vm_options: &'a Path,
    /// `product-info.json`.
    pub product_info: &'a Path,
    /// `core-classpath.txt`. A relative line is relative to the home.
    pub core_classpath: &'a Path,
}

impl Distribution {
    /// Reads the distribution that the IDE starts from at `home`, an absolute path. `vm_options_destination` is the
    /// slash path of the vmoptions file relative to `home`. With `runtime_module_repository`, the distribution names
    /// `modules/module-descriptors.dat` of `home` as its runtime module repository.
    pub fn read(
        home: &str,
        files: &DistributionFiles<'_>,
        vm_options_destination: &str,
        runtime_module_repository: bool,
    ) -> anyhow::Result<Self> {
        let main_class = read_main_class(files.ide_config)?;
        let info = ProductInfo::read_file(files.product_info)?;
        let vm_options_path = path_string(Path::new(home).join(from_slash(vm_options_destination).as_ref()))?;
        let properties = properties_of_files(files.idea_properties, files.vm_options, &vm_options_path, home, &info)?;
        let runtime_module_repository = if runtime_module_repository {
            Some(path_string(Path::new(home).join("modules").join("module-descriptors.dat"))?)
        } else {
            None
        };
        Ok(Self {
            home: home.to_owned(),
            info,
            properties,
            main_class,
            class_path: read_class_path(files.core_classpath, home)?,
            runtime_module_repository,
        })
    }
}

/// The arguments of `java` from the first caller flag to the main class. `command` is the first program argument, which
/// names the custom command when a caller flag asks for one.
///
/// A caller-owned property of `command_line` keeps its value against the distribution, as `PreBuiltDevMain` does. Every
/// other property of the distribution comes after the caller flags, so it wins.
pub fn java_arguments(command_line: Vec<String>, distribution: Distribution, command: Option<&str>) -> anyhow::Result<Vec<String>> {
    let Distribution {
        home,
        info,
        mut properties,
        mut main_class,
        class_path,
        runtime_module_repository,
    } = distribution;
    let mut caller_properties = IndexMap::new();
    for flag in &command_line {
        put_system_property(&mut caller_properties, flag);
    }
    if caller_properties
        .get("idea.dev.mode.custom.command")
        .is_some_and(|value| value.eq_ignore_ascii_case("true"))
    {
        let Some(command) = command else {
            bail!("-Didea.dev.mode.custom.command=true needs the command as the first program argument");
        };
        let (command_main_class, command_properties) = custom_command(&home, &info, command)?;
        main_class = command_main_class;
        properties.extend(command_properties);
    }
    if let Some(file) = runtime_module_repository {
        add_runtime_module_repository(&mut properties, &file, &caller_properties);
    }

    let mut arguments = command_line;
    // `PreBuiltDevMain` sets these three before the properties of the distribution, which can override them.
    arguments.extend([
        "-Didea.vendor.name=JetBrains".to_owned(),
        "-Didea.use.dev.build.server=true".to_owned(),
        format!("-Didea.home.path={home}"),
    ]);
    for (key, value) in &properties {
        if caller_properties.contains_key(key) && is_caller_owned_property(key) {
            continue;
        }
        arguments.push(format!("-D{key}={value}"));
    }
    arguments.extend(["-cp".to_owned(), class_path.join(PATH_LIST_SEPARATOR), main_class]);
    Ok(arguments)
}

/// The properties that the caller flags keep against the properties of the distribution, as
/// `PreBuiltDevMain.isCallerOwnedProperty` does.
fn is_caller_owned_property(name: &str) -> bool {
    let lower = name.to_lowercase();
    lower.starts_with("rider.")
        || lower.starts_with("resharper.")
        || matches!(
            name,
            "idea.platform.prefix" | "intellij.platform.product.mode" | "idea.suppressed.plugins.set.selector" | "awt.toolkit.name"
        )
}

/// Adds the runtime module repository `file` of the home to `properties`, as
/// `PreBuiltDevMain.addRuntimeModuleRepository` does.
///
/// The distribution states the property through `product-info.json` when its launch model asks. So the writer adds
/// `file` only when neither `properties` nor `caller_properties` state the property.
pub(crate) fn add_runtime_module_repository(
    properties: &mut IndexMap<String, String>,
    file: &str,
    caller_properties: &IndexMap<String, String>,
) {
    if !properties.contains_key(RUNTIME_MODULE_REPOSITORY_PROPERTY) && !caller_properties.contains_key(RUNTIME_MODULE_REPOSITORY_PROPERTY) {
        properties.insert(RUNTIME_MODULE_REPOSITORY_PROPERTY.to_owned(), file.to_owned());
    }
}

/// The class path of `core-classpath.txt` at `file`. A relative line is relative to `home`.
pub fn read_class_path(file: &Path, home: &str) -> anyhow::Result<Vec<String>> {
    read_lines(file)?
        .iter()
        .map(|line| line.trim())
        .filter(|line| !line.is_empty())
        .map(|line| {
            if Path::new(line).is_absolute() {
                Ok(line.to_owned())
            } else {
                path_string(Path::new(home).join(from_slash(line).as_ref()))
            }
        })
        .collect()
}

/// Reads the main class of the IDE from the `DevIdeConfig` file.
///
/// Java reads the file as ISO-8859-1, and `java_properties` reads it as windows-1252. The composer writes ASCII, so the
/// difference has no effect.
fn read_main_class(file: &Path) -> anyhow::Result<String> {
    let data = std::fs::read(file).with_context(|| format!("read {}", file.display()))?;
    let properties = java_properties::read(data.as_slice()).map_err(|error| anyhow!("{}: {error}", file.display()))?;
    match properties.get("main.class.name") {
        Some(main_class) if !main_class.is_empty() => Ok(main_class.clone()),
        _ => bail!("{} states no main.class.name", file.display()),
    }
}

/// Changes each slash of `value` to the native separator.
///
/// It is written here, because `component::paths` would bring its dependencies into each binary that links this crate.
fn from_slash(value: &str) -> Cow<'_, str> {
    if cfg!(windows) {
        Cow::Owned(value.replace('/', "\\"))
    } else {
        Cow::Borrowed(value)
    }
}

fn path_string(path: PathBuf) -> anyhow::Result<String> {
    path.into_os_string()
        .into_string()
        .map_err(|path| anyhow!("the path is not valid UTF-8: {}", path.display()))
}

/// The text of a `java` argument file: each argument of `arguments` on its own line, quoted by [`quote_argument`].
/// It refuses an argument with a line break.
pub fn argument_file_text(arguments: &[String]) -> anyhow::Result<String> {
    let mut text = String::new();
    for argument in arguments {
        // `java` ends an unquoted argument at a line break, so the file would split the argument in silence.
        if argument.contains(['\n', '\r']) {
            bail!("the argument {argument:?} holds a line break, which a java argument file cannot hold");
        }
        text.push_str(&quote_argument(argument));
        text.push('\n');
    }
    Ok(text)
}

/// One argument of a `java` argument file. An argument with a space, a quote, a number sign or a backslash is in double
/// quotes, with each backslash and each double quote escaped. `java` reads every other argument as it is.
pub fn quote_argument(argument: &str) -> String {
    if !argument.is_empty() && !argument.contains(|character: char| character.is_whitespace() || "\"'#\\".contains(character)) {
        return argument.to_owned();
    }
    let mut quoted = String::with_capacity(argument.len() + 2);
    quoted.push('"');
    for character in argument.chars() {
        if character == '"' || character == '\\' {
            quoted.push('\\');
        }
        quoted.push(character);
    }
    quoted.push('"');
    quoted
}

#[cfg(test)]
mod tests;
