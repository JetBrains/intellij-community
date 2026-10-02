use std::collections::HashMap;
use std::path::{Path, PathBuf};

use serde_json::json;

use super::*;
use crate::properties::parse_properties;

#[test]
fn java_properties_follow_properties_load() {
    let properties = parse_properties(b"# comment\n! comment\n  a=1\nb : 2\nc 3\nd\\=e = x\\\n    y\nf=\\u0041\\tz\nempty\n").unwrap();
    let expected = [("a", "1"), ("b", "2"), ("c", "3"), ("d=e", "xy"), ("f", "A\tz"), ("empty", "")];
    let actual: Vec<(&str, &str)> = properties.iter().map(|(key, value)| (key.as_str(), value.as_str())).collect();
    assert_eq!(actual, expected);
    assert!(parse_properties(b"x=\\u00").is_err(), "accepted a truncated \\u escape");
}

#[test]
fn wrapper_arguments_follow_the_java_stub() {
    let mut env: HashMap<&str, &str> = HashMap::new();
    let arguments = |values: &[&str]| values.iter().map(|value| (*value).to_owned()).collect::<Vec<_>>();
    let getenv = |env: &HashMap<&str, &str>, name: &str| env.get(name).copied().unwrap_or_default().to_owned();
    let (wrapper, program) = parse_wrapper_arguments(
        &arguments(&[
            "--debug",
            "--jvm_flag=-Da=1",
            "mcpServer",
            "--jvm_flag=-Db=2",
            "--wrapper_script_flag=--jvm_flags=-Dc=3 -Dd=4",
        ]),
        &|name| getenv(&env, name),
    )
    .unwrap();
    assert_eq!(
        wrapper.debug_flags,
        ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"]
    );
    assert_eq!(wrapper.jvm_flags, ["-Da=1", "-Dc=3", "-Dd=4"]);
    assert_eq!(program, ["mcpServer", "--jvm_flag=-Db=2"]);
    env.insert("DEFAULT_JVM_DEBUG_SUSPEND", "n");
    let (wrapper, _) = parse_wrapper_arguments(&arguments(&["--wrapper_script_flag=--debug=5006"]), &|name| getenv(&env, name)).unwrap();
    assert_eq!(
        wrapper.debug_flags,
        ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006"]
    );
    assert!(
        parse_wrapper_arguments(&arguments(&["--wrapper_script_flag=--unknown"]), &|name| getenv(&env, name)).is_err(),
        "accepted an unknown wrapper flag"
    );
}

#[test]
fn braces_expand_and_bare_macros_stay() {
    let getenv = |name: &str| {
        if name == "BUILD_WORKSPACE_DIRECTORY" {
            "/ws".to_owned()
        } else {
            String::new()
        }
    };
    assert_eq!(
        expand_braces("-Dp=${BUILD_WORKSPACE_DIRECTORY}/out -Dq=$APP_PACKAGE/x ${MISSING}!", &getenv),
        "-Dp=/ws/out -Dq=$APP_PACKAGE/x !"
    );
    assert_eq!(expand_braces("${OPEN", &getenv), "${OPEN");
}

fn ide_home_macro() -> &'static str {
    match std::env::consts::OS {
        "windows" => "%IDE_HOME%",
        "macos" => "$APP_PACKAGE/Contents",
        _ => "$IDE_HOME",
    }
}

fn write_file(path: &Path, content: &str) {
    std::fs::create_dir_all(path.parent().unwrap()).unwrap();
    std::fs::write(path, content).unwrap();
}

/// The files of a distribution that the properties come from, keyed by their path below the home.
fn distribution_files() -> Vec<(&'static str, String)> {
    let home_macro = ide_home_macro();
    let info = json!({"launch": [{
        "additionalJvmArguments": [
            format!("-Xbootclasspath/a:{home_macro}/lib/nio-fs.jar"),
            format!("-Djna.boot.library.path={home_macro}/lib/jna"),
        ],
        "customCommands": [{
            "commands": ["ijLight"],
            "mainClass": "com.example.LightMain",
            "additionalJvmArguments": [format!("-Dlight={home_macro}/light"), "-Xss4m"],
        }],
    }]});
    vec![
        (
            "bin/idea.properties",
            "idea.config.path=${user.home}/config\nidea.platform.prefix=fromDistribution\n".to_owned(),
        ),
        (
            "bin/idea.vmoptions",
            "-Xmx2048m\n-Dsun.io.useCanonCaches=false\n-Dawt.toolkit.name=fromDistribution\n".to_owned(),
        ),
        ("bin/product-info.json", info.to_string()),
    ]
}

/// Writes a distribution home with the files that the properties and the class path come from.
fn write_distribution(home: &Path) {
    for (name, text) in distribution_files() {
        write_file(&home.join(name), &text);
    }
    write_file(&home.join("core-classpath.txt"), "lib/a.jar\n\nlib/b.jar\n");
}

#[test]
fn distribution_properties_follow_get_ide_system_properties() {
    let directory = tempfile::tempdir().unwrap();
    let home = directory.path().display().to_string();
    write_distribution(directory.path());
    let info = ProductInfo::read(directory.path()).unwrap();
    let properties = distribution_properties(&home, &info).unwrap();
    let vm_options_file = directory.path().join("bin").join("idea.vmoptions").display().to_string();
    let jna = format!("{home}/lib/jna");
    for (key, value) in [
        ("idea.config.path", "${user.home}/config"),
        ("sun.io.useCanonCaches", "false"),
        ("jb.vmOptionsFile", vm_options_file.as_str()),
        ("jna.boot.library.path", jna.as_str()),
        ("awt.toolkit.name", "fromDistribution"),
        ("idea.platform.prefix", "fromDistribution"),
    ] {
        assert_eq!(properties.get(key).map(String::as_str), Some(value), "{key}");
    }
    assert!(
        !properties.contains_key("Xmx2048m"),
        "a vmoptions line without -D became a property"
    );
    let (main_class, command) = custom_command(&home, &info, "ijLight").unwrap();
    assert_eq!(main_class, "com.example.LightMain");
    assert_eq!(
        command.into_iter().collect::<Vec<_>>(),
        [("light".to_owned(), format!("{home}/light"))]
    );
    assert!(
        custom_command(&home, &info, "other").is_err(),
        "found a custom command that the distribution does not declare"
    );
}

#[test]
fn distribution_properties_need_a_single_vm_options_file() {
    let directory = tempfile::tempdir().unwrap();
    write_distribution(directory.path());
    write_file(&directory.path().join("bin/other.vmoptions"), "-Xmx1g\n");
    let info = ProductInfo::read(directory.path()).unwrap();
    let error = distribution_properties(&directory.path().display().to_string(), &info).unwrap_err();
    assert!(error.to_string().contains("no single *.vmoptions file"), "{error:#}");
}

/// A launcher, its manifest and its runfiles in a temporary directory.
struct Launcher {
    _directory: tempfile::TempDir,
    self_path: String,
    runfiles: PathBuf,
    /// The distribution home, the metadata tree of the composer.
    home: PathBuf,
}

impl Launcher {
    fn manifest(&self, value: &serde_json::Value) {
        write_file(Path::new(&format!("{}.launch.json", self.self_path)), &value.to_string());
    }

    fn prepare(&self, args: &[&str], env: &[(&str, &str)]) -> anyhow::Result<Launch> {
        let env: HashMap<&str, &str> = env.iter().copied().collect();
        let mut argv = vec![self.self_path.clone()];
        argv.extend(args.iter().map(|arg| (*arg).to_owned()));
        prepare(
            &argv,
            &|name| env.get(name).copied().unwrap_or_default().to_owned(),
            &mut std::io::sink(),
        )
    }
}

fn launch_manifest(jvm_flags: &[&str]) -> serde_json::Value {
    json!({
        "version": 1,
        "java": "jbr/bin/java",
        "ideConfig": "_main/build/idea_dist_launch.ide.config",
        "beforeRun": "",
        "jvmFlags": jvm_flags,
        "home": "out/dev-data/idea/homes",
    })
}

/// Lays out a launcher over a distribution. Without `local_layout` the home holds the whole distribution. With it the
/// home holds the metadata and the layout, and the runfiles hold the files of the layout.
fn write_launcher(jvm_flags: &[&str], local_layout: bool) -> Launcher {
    let directory = tempfile::tempdir().unwrap();
    let self_path = directory.path().join("bin").join("idea");
    let runfiles = PathBuf::from(format!("{}.runfiles", self_path.display()));
    let home = runfiles.join("_main/build/idea_dist_launch.metadata");
    if local_layout {
        let mut files = Vec::new();
        for (name, text) in distribution_files() {
            let runfile = format!("_main/dist/{name}");
            write_file(&runfiles.join(&runfile), &text);
            files.push(json!({"path": name, "runfile": runfile, "executable": false}));
        }
        write_file(&runfiles.join("_main/dist/lib/a.jar"), "a");
        files.push(json!({"path": "lib/a.jar", "runfile": "_main/dist/lib/a.jar", "executable": false}));
        let layout = json!({"version": 1, "files": files, "metadata": ["core-classpath.txt"]});
        write_file(&home.join("local-layout.json"), &layout.to_string());
        write_file(&home.join("core-classpath.txt"), "lib/a.jar\n");
    } else {
        write_distribution(&home);
    }
    write_file(
        &runfiles.join("_main/build/idea_dist_launch.ide.config"),
        "home.path=idea_dist_launch.metadata\nmain.class.name=com.intellij.idea.Main\n",
    );
    write_file(&runfiles.join("jbr/bin/java"), "");
    let launcher = Launcher {
        _directory: directory,
        self_path: self_path.display().to_string(),
        runfiles,
        home,
    };
    launcher.manifest(&launch_manifest(jvm_flags));
    launcher
}

#[test]
fn prepare_builds_the_java_command_line() {
    let launcher = write_launcher(
        &[
            "-Dawt.toolkit.name=auto",
            "-Didea.log.path=${BUILD_WORKSPACE_DIRECTORY}/log",
            "-Dsun.io.useCanonCaches=true",
        ],
        false,
    );
    let launch = launcher
        .prepare(
            &["--wrapper_script_flag=--jvm_flag=-Dextra=1", "arg"],
            &[("BUILD_WORKSPACE_DIRECTORY", "/ws")],
        )
        .unwrap();
    let argv = launch.argv.join("\n");
    let home = launcher.home.display().to_string();
    for expected in [
        "-Dawt.toolkit.name=auto".to_owned(),
        "-Didea.log.path=/ws/log".to_owned(),
        "-Dextra=1".to_owned(),
        format!("-Didea.home.path={home}"),
        // the distribution wins over a flag that the caller does not own
        "-Dsun.io.useCanonCaches=false".to_owned(),
        "-Didea.platform.prefix=fromDistribution".to_owned(),
        "com.intellij.idea.Main\narg".to_owned(),
        format!(
            "{}{PATH_LIST_SEPARATOR}{}",
            launcher.home.join("lib").join("a.jar").display(),
            launcher.home.join("lib").join("b.jar").display()
        ),
    ] {
        assert!(argv.contains(&expected), "the command line misses {expected:?}:\n{argv}");
    }
    // the caller owns awt.toolkit.name, so the distribution does not override it
    assert!(
        !argv.contains("-Dawt.toolkit.name=fromDistribution"),
        "the distribution overrode a property that the caller owns:\n{argv}"
    );
    assert_eq!(launch.dir, "/ws");
    assert_eq!(Path::new(&launch.java).file_name().unwrap(), "java");
    assert_eq!(
        launch.env,
        [
            ("RUNFILES_DIR", launcher.runfiles.display().to_string()),
            ("JAVA_RUNFILES", launcher.runfiles.display().to_string()),
        ]
    );
}

#[test]
fn prepare_starts_a_custom_command() {
    let launcher = write_launcher(&["-Didea.dev.mode.custom.command=true"], false);
    let env = [("BUILD_WORKSPACE_DIRECTORY", "/ws")];
    let launch = launcher.prepare(&["ijLight", "/project"], &env).unwrap();
    let argv = launch.argv.join("\n");
    assert!(
        argv.contains("com.example.LightMain\nijLight\n/project") && argv.contains(&format!("-Dlight={}/light", launcher.home.display())),
        "the custom command did not start:\n{argv}"
    );
    assert!(launcher.prepare(&[], &env).is_err(), "started a custom command without its name");
}

#[test]
fn prepare_adds_the_runtime_module_repository_of_the_home() {
    let env = [("BUILD_WORKSPACE_DIRECTORY", "/ws")];
    let property = "-Dintellij.platform.runtime.repository.path=";
    let without = write_launcher(&[], false);
    let argv = without.prepare(&[], &env).unwrap().argv.join("\n");
    assert!(!argv.contains(property), "a home without the repository got the property:\n{argv}");

    let with = write_launcher(&[], false);
    let repository = with.home.join("modules").join("module-descriptors.dat");
    write_file(&repository, "");
    let argv = with.prepare(&[], &env).unwrap().argv.join("\n");
    assert!(
        argv.contains(&format!("{property}{}\n", repository.display())),
        "the command line misses the repository of the home:\n{argv}"
    );

    let caller = write_launcher(&["-Dintellij.platform.runtime.repository.path=/custom.dat"], false);
    write_file(&caller.home.join("modules").join("module-descriptors.dat"), "");
    let argv = caller.prepare(&[], &env).unwrap().argv.join("\n");
    assert_eq!(argv.matches(property).count(), 1, "{argv}");
    assert!(
        argv.contains(&format!("{property}/custom.dat\n")),
        "the home overrode the caller flag:\n{argv}"
    );
}

#[test]
fn the_distribution_states_the_runtime_module_repository_first() {
    let directory = tempfile::tempdir().unwrap();
    let home = directory.path().display().to_string();
    write_file(&directory.path().join("modules").join("module-descriptors.dat"), "");
    let mut distribution = IndexMap::from([(
        properties::RUNTIME_MODULE_REPOSITORY_PROPERTY.to_owned(),
        "/product-info.dat".to_owned(),
    )]);
    add_runtime_module_repository(&mut distribution, &home, &IndexMap::new());
    assert_eq!(
        distribution.get(properties::RUNTIME_MODULE_REPOSITORY_PROPERTY).map(String::as_str),
        Some("/product-info.dat")
    );
}

#[test]
fn prepare_needs_the_workspace() {
    let launcher = write_launcher(&[], false);
    let error = launcher.prepare(&[], &[]).unwrap_err();
    assert!(error.to_string().contains("BUILD_WORKSPACE_DIRECTORY"), "{error:#}");
}

#[test]
fn prepare_reads_only_the_fields_of_the_launch_manifest() {
    let launcher = write_launcher(&[], false);
    let env = [("BUILD_WORKSPACE_DIRECTORY", "/ws")];
    // The rule writes every field. Only `beforeRun` has a default.
    for field in ["version", "java", "ideConfig", "jvmFlags", "home"] {
        let mut manifest = launch_manifest(&[]);
        manifest.as_object_mut().unwrap().remove(field);
        launcher.manifest(&manifest);
        let error = launcher.prepare(&[], &env).unwrap_err();
        assert!(
            format!("{error:#}").contains(&format!("missing field `{field}`")),
            "{field}: {error:#}"
        );
    }
    let mut manifest = launch_manifest(&[]);
    manifest.as_object_mut().unwrap().remove("beforeRun");
    launcher.manifest(&manifest);
    launcher.prepare(&[], &env).unwrap();
    // A manifest of an older rule still names the tool that linked the home.
    manifest["localHomeTool"] = json!("_main/build/collector");
    launcher.manifest(&manifest);
    let error = launcher.prepare(&[], &env).unwrap_err();
    assert!(format!("{error:#}").contains("unknown field `localHomeTool`"), "{error:#}");
    let mut manifest = launch_manifest(&[]);
    manifest["version"] = json!(2);
    launcher.manifest(&manifest);
    let error = launcher.prepare(&[], &env).unwrap_err();
    assert!(error.to_string().contains("unsupported launch manifest version: 2"), "{error:#}");
}

#[cfg(unix)]
#[test]
fn prepare_links_the_local_home_in_process() {
    let launcher = write_launcher(&[], true);
    let workspace = tempfile::tempdir().unwrap();
    let workspace = fscopy::resolve_links(workspace.path()).unwrap();
    let homes = workspace.join("out/dev-data/idea/homes");
    let parent = std::os::unix::process::parent_id().to_string();
    for name in ["999999999", parent.as_str(), "not-a-process"] {
        std::fs::create_dir_all(homes.join(name).join("config")).unwrap();
    }
    let workspace_text = workspace.display().to_string();
    let launch = launcher.prepare(&[], &[("BUILD_WORKSPACE_DIRECTORY", &workspace_text)]).unwrap();
    let home = homes.join(std::process::id().to_string());
    let argv = launch.argv.join("\n");
    assert!(
        argv.contains(&format!("-Didea.home.path={}\n", home.display())),
        "the command line misses the linked home:\n{argv}"
    );
    assert!(
        argv.contains(&format!("-cp\n{}\n", home.join("lib").join("a.jar").display())),
        "{argv}"
    );
    assert!(argv.contains("-Dsun.io.useCanonCaches=false"), "{argv}");
    assert_eq!(
        std::fs::read_link(home.join("bin/idea.properties")).unwrap(),
        launcher.runfiles.join("_main/dist/bin/idea.properties")
    );
    assert!(
        !std::fs::symlink_metadata(home.join("core-classpath.txt"))
            .unwrap()
            .file_type()
            .is_symlink()
    );
    assert!(!homes.join("999999999").exists(), "kept the home of a stale process");
    assert!(homes.join(&parent).exists(), "removed the home of a running process");
    assert!(homes.join("not-a-process").exists(), "removed a directory that names no process");
}

#[cfg(unix)]
#[test]
fn prepare_links_the_dev_data_before_the_home() {
    let fixture = devdata::tests::Fixture::new();
    let launcher = write_launcher(&[], true);
    let workspace = fixture.workspace.display().to_string();
    let parent = fixture.parent.display().to_string();
    launcher
        .prepare(
            &[],
            &[
                ("BUILD_WORKSPACE_DIRECTORY", &workspace),
                (devdata::DEV_DATA_ROOT_VARIABLE, &parent),
            ],
        )
        .unwrap();
    devdata::tests::assert_link(&fixture.link(), &fixture.root());
    assert!(
        fixture
            .root()
            .join("idea/homes")
            .join(std::process::id().to_string())
            .join("bin/idea.properties")
            .exists(),
        "the home is not below the dev-data root"
    );
}

#[cfg(unix)]
#[test]
fn prepare_runs_the_before_run_step_in_the_workspace() {
    use std::os::unix::fs::PermissionsExt;
    let launcher = write_launcher(&[], false);
    let workspace = tempfile::tempdir().unwrap();
    let step = launcher.runfiles.join("_main/build/before_run");
    write_file(&step, "#!/bin/sh\necho \"$RUNFILES_DIR\" > before-run.txt\n");
    std::fs::set_permissions(&step, std::fs::Permissions::from_mode(0o755)).unwrap();
    let mut manifest = launch_manifest(&[]);
    manifest["beforeRun"] = json!("_main/build/before_run");
    launcher.manifest(&manifest);
    let workspace_text = workspace.path().display().to_string();
    let env = [("BUILD_WORKSPACE_DIRECTORY", workspace_text.as_str())];
    launcher.prepare(&[], &env).unwrap();
    assert_eq!(
        std::fs::read_to_string(workspace.path().join("before-run.txt")).unwrap(),
        format!("{}\n", launcher.runfiles.display())
    );
    write_file(&step, "#!/bin/sh\nexit 3\n");
    let error = launcher.prepare(&[], &env).unwrap_err();
    assert!(error.to_string().contains("the before-run step"), "{error:#}");
}

/// Runs the `local-home` command with the arguments after the command name, and returns the exit code and stderr.
fn run_local_home_command(args: &[&str]) -> (u8, String) {
    let args: Vec<OsString> = args.iter().map(OsString::from).collect();
    let (mut output, mut errors) = (Vec::new(), Vec::new());
    let code = run_local_home(&args, &mut output, &mut errors);
    (code, String::from_utf8(errors).unwrap())
}

#[test]
fn local_home_refuses_bad_options() {
    for args in [
        &[][..],
        &["--unknown=value"],
        &["--layout="],
        &["--layout"],
        &["--layout=a", "--layout=b", "--output-dir=home"],
        &["--layout=a"],
    ] {
        let (code, errors) = run_local_home_command(args);
        assert!(code == 2 && errors.contains("ERROR:"), "{args:?}: exit {code}, errors {errors:?}");
    }
}

#[cfg(unix)]
#[test]
fn local_home_links_the_layout() {
    let directory = tempfile::tempdir().unwrap();
    let runfiles = directory.path().join("runfiles");
    write_file(&runfiles.join("_main/dist/bin/tool"), "tool");
    let layout = directory.path().join("metadata/local-layout.json");
    let layout_json = json!({
        "version": 1,
        "files": [{"path": "bin/tool", "runfile": "_main/dist/bin/tool", "executable": false}],
        "metadata": ["core-classpath.txt"],
    });
    write_file(&layout, &layout_json.to_string());
    write_file(&directory.path().join("metadata/core-classpath.txt"), "lib/a.jar");
    let home = directory.path().join("home");
    // The command reads the runfiles variables of the process, as `PreBuiltDevMain` starts it.
    let env = local_home::RunfilesEnv {
        runfiles_dir: Some(runfiles.clone()),
        ..Default::default()
    };
    local_home::link_local_home(&layout, &home, &env).unwrap();
    assert_eq!(
        std::fs::read_link(home.join("bin/tool")).unwrap(),
        runfiles.join("_main/dist/bin/tool")
    );
    let (code, errors) = run_local_home_command(&[
        &format!("--layout={}", layout.display()),
        &format!("--output-dir={}", home.display()),
    ]);
    assert!(
        code == 1 && errors.contains("the local home must be empty"),
        "exit {code}, errors {errors:?}"
    );
}
