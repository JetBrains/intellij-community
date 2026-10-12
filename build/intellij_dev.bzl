"""Macros for IntelliJ-based IDE development builds."""

load("@bazel_skylib//lib:shell.bzl", "shell")
load("@dev_launch_paths//:paths.bzl", "HOME", "OUTPUT_BASE", "WORKSPACE_ROOT")
load("@intellij_add_opens//:intellij_add_opens.bzl", "INTELLIJ_ADD_OPENS")
load("//platform/build-scripts/bazel-rules:intellij_dev_dist.bzl", "IntellijDevDistInfo")

# Names the prepared distribution for its consumer: the prebuilt dev-build runner of IDE Starter in a test, and
# `PreBuiltDevMain` in the docker run configuration. Keep in sync with `DevIdeConfig.CONFIG_PATH_PROPERTY`, which is
# where the reading side of this contract lives.
DEV_IDE_CONFIG_PATH_PROPERTY = "idea.ide.config.path"

def intellij_dev_dist_config(name, dist, visibility = None, tags = []):
    """A single-file label for an assembled dev distribution's config file, for `$(rlocationpath ...)`.

    That expansion takes a label naming exactly one file, which a dist target - two outputs, one of them declared rather
    than predeclared - is not. Its `ide_config` output group is how it gets one.

    A consumer declares both this and the dist itself in `data`, and they must stay siblings in the runfiles tree: the
    config names the home relatively, so that the pair survives being read from a different path than it was written to.

    `tags` is the filegroup's; a consumer that must stay out of a wildcard build passes `["manual"]`.
    """
    native.filegroup(
        name = name,
        srcs = [dist],
        output_group = "ide_config",
        tags = tags,
        visibility = visibility,
    )

# The JVM flags that every dev launch of an IDE starts with. A rule that writes the flags of an IDE launch elsewhere loads
# this list, so the two launches agree.
DEFAULT_JVM_FLAGS = [
    "--enable-native-access=ALL-UNNAMED",
    "-ea",
    "-Didea.jre.check=true",
    "-Didea.is.internal=true",
    "-Didea.debug.mode=true",
    "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
    "-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider",
]

# The data directories of the IDE. `dev_launch_jvm_flags` owns them, and a flag that states one would lose to its
# defaults.
_DATA_PROPERTIES = ["idea.config.path", "idea.system.path", "idea.log.path"]

# The workspace root in the flags of a `java` launcher. `$$` is a literal `$`, so the rule gets
# `${BUILD_WORKSPACE_DIRECTORY}` and resolves it at analysis.
_LAUNCHER_WORKSPACE = "$${BUILD_WORKSPACE_DIRECTORY}"

def dev_launch_jvm_flags(name, jvm_flags = [], workspace = _LAUNCHER_WORKSPACE):
    """The flags an IDE needs to run, independent of how it was assembled.

    The data directories are below `out/dev-data/<name>`: `config`, `system`, `system/log` and `config/plugins`. On
    macOS `out/dev-data` is a link to a directory outside the workspace. `jvm_flags` must not state a data directory.

    `workspace` spells the workspace root. A `java` launcher keeps the default, which its rule resolves at analysis.
    The sibling vmoptions file of an export passes the absolute root, because no rule expands its lines. So a launch
    target and a bare start of an export get the same flags.
    """
    for flag in jvm_flags:
        for key in _DATA_PROPERTIES:
            if flag.startswith("-D%s=" % key):
                fail("%s: `%s` states a data directory, which the rule owns" % (name, flag))

    config_path = workspace + "/out/dev-data/" + name + "/config"
    system_path = workspace + "/out/dev-data/" + name + "/system"
    return DEFAULT_JVM_FLAGS + [
        "-Didea.plugins.path=" + config_path + "/plugins",
        "-Didea.log.path=" + system_path + "/log",
    ] + jvm_flags + [
        "-Didea.config.path=" + config_path,
        "-Didea.system.path=" + system_path,
        # Not a build-time input: `AppMode.getDevIdeaProjectDir` and the webview native bridge read it at runtime.
        # A `DevMainImpl` launch has it because `DevMainImpl` sets it from the project root it just built against.
        "-Didea.dev.project.root=" + workspace,
    ]

# The runtime module repository of a home, and the property that names it.
_RUNTIME_MODULE_REPOSITORY_FILE = "modules/module-descriptors.dat"
_RUNTIME_MODULE_REPOSITORY_PROPERTY = "-Dintellij.platform.runtime.repository.path="

def export_vm_options(name, runtime_module_repository):
    """The attributes of the sibling vmoptions file of the export `<name>.dist`, for `intellij_dev_fragments_dist`.

    The lines are `dev_launch_jvm_flags` over `out/dev-data/<name>`, with absolute paths. With
    `runtime_module_repository`, the last line names `modules/module-descriptors.dat` of the export as the runtime
    module repository, as the argument file writer does. Pass it only when the export has that file and its
    `product-info.json` does not state the property.
    """
    lines = dev_launch_jvm_flags(name, workspace = WORKSPACE_ROOT)
    if runtime_module_repository:
        lines.append(_RUNTIME_MODULE_REPOSITORY_PROPERTY + "${EXPORT}/" + _RUNTIME_MODULE_REPOSITORY_FILE)
    return {"sibling_vm_options": lines, "output_base": OUTPUT_BASE}

# The directory of the runfiles tree that holds the home of a launch target on Linux and macOS.
_LAUNCH_HOME = "ide_home"

# The variables that a flag of a launch target can name. Neither `java` nor the native launcher expands them, so the
# rule resolves them at analysis.
_LAUNCH_VARIABLES = {
    "BUILD_WORKSPACE_DIRECTORY": WORKSPACE_ROOT,
    "HOME": HOME,
}

# The flag that makes the argument file writer select the custom command of the first program argument. The native
# launcher selects the command from that argument itself, so its vmoptions file leaves the flag out.
_CUSTOM_COMMAND_FLAG = "-Didea.dev.mode.custom.command=true"

def _resolve_variables(label, flag):
    """`flag` with each `${NAME}` of `_LAUNCH_VARIABLES` replaced. Any other `${NAME}` fails."""
    result = flag
    for name, value in _LAUNCH_VARIABLES.items():
        result = result.replace("${%s}" % name, value)
    if "${" in result:
        fail("%s: `%s` names a variable that a launch target cannot expand; it knows only %s" % (label, flag, sorted(_LAUNCH_VARIABLES.keys())))
    return result

def _execroot_path(ctx, path):
    """The absolute path of the exec path `path`."""
    return "%s/execroot/%s/%s" % (OUTPUT_BASE, ctx.workspace_name, path)

def _windows_path(label, path):
    """`path` with backslashes, for a line of a `.cmd` file. `cmd.exe` expands `%` and ends a quoted path at `"`, so both fail."""
    if "%" in path or '"' in path:
        fail("%s: the path %s has a `%%` or a `\"`, which a `.cmd` file cannot hold" % (label, path))
    return path.replace("/", "\\")

def _cmd_argument(label, argument):
    """`argument` in double quotes, for a line of a `.cmd` file.

    A quoted value keeps its spaces and the operators of `cmd.exe`. A `.cmd` file reads `%%` as one `%`, so a `%` of
    the argument is doubled. `"` fails, as in `_windows_path`. A trailing backslash fails too, because the launcher
    would read `\\"` as a quote.
    """
    if '"' in argument or argument.endswith("\\") or "\n" in argument or "\r" in argument:
        fail("%s: the program argument %s cannot be one argument of a `.cmd` file" % (label, repr(argument)))
    return '"%s"' % argument.replace("%", "%%")

def _java_file(ctx, java_runtime):
    for file in java_runtime.files.to_list():
        if file.path == java_runtime.java_executable_exec_path:
            return file
    fail("%s: the Java runtime has no file %s" % (ctx.label, java_runtime.java_executable_exec_path))

def _runfiles_home(ctx, placement, dist):
    """The entries of the runfiles home: a dictionary from a destination to its `File`."""

    # Analysis cannot see the mode of a source file, so an executable source file gets a copy, which Bazel leaves
    # executable.
    home = {}
    executables = {destination: True for destination in placement.executables}
    for destination, file in placement.files.items():
        if destination in executables and file.is_source:
            copy = ctx.actions.declare_file("%s.executables/%s" % (ctx.label.name, destination))
            ctx.actions.run_shell(
                outputs = [copy],
                inputs = [file],
                command = 'cp "$1" "$2"',
                arguments = [file.path, copy.path],
                mnemonic = "DevLaunchExecutableCopy",
                progress_message = "Copying the executable %s of %%{label}" % destination,
            )
            file = copy
        home[destination] = file
    home.update(placement.trees)
    home["fingerprint.txt"] = dist.fingerprint
    if dist.plugin_classpath:
        home["plugins/plugin-classpath.txt"] = dist.plugin_classpath
    return home

def _home_root_symlinks(ctx, placement, dist):
    """The `root_symlinks` of the runfiles home `ide_home`."""
    return {_LAUNCH_HOME + "/" + destination: file for destination, file in _runfiles_home(ctx, placement, dist).items()}

def _target_os(ctx):
    """The OS token of the target platform, as `platform_parts` spells it: `darwin`, `linux` or `windows`."""
    for os, constraint in [("darwin", ctx.attr._macos), ("linux", ctx.attr._linux), ("windows", ctx.attr._windows)]:
        if ctx.target_platform_has_constraint(constraint[platform_common.ConstraintValueInfo]):
            return os
    fail("%s: the target platform is not macOS, Linux or Windows" % ctx.label)

def _os_destination(ctx, attribute, os):
    """The destination that the `string_dict` attribute `attribute` states for the OS token `os`."""
    destinations = getattr(ctx.attr, attribute)
    if os not in destinations:
        fail("%s: `%s` states no destination for %s: %s" % (ctx.label, attribute, os, destinations))
    return destinations[os]

def _merge_data(ctx, runfiles):
    for target in ctx.attr.data:
        runfiles = runfiles.merge(ctx.runfiles(transitive_files = target[DefaultInfo].files)).merge(target[DefaultInfo].default_runfiles)
    return runfiles

def _java_launch(ctx, dist, placement, vm_options, product_info, java_runtime, flags, program_args):
    """The `java` launch of Linux: a link to `java`, which reads `@<name>.jvm.args`."""
    executable = ctx.actions.declare_file(ctx.label.name)
    runfiles_directory = _execroot_path(ctx, executable.path) + ".runfiles"
    home_path = runfiles_directory + "/" + _LAUNCH_HOME

    flags_file = ctx.actions.declare_file(ctx.label.name + ".jvm-flags.txt")
    ctx.actions.write(flags_file, "".join([flag + "\n" for flag in flags]))

    argfile = ctx.actions.declare_file(ctx.label.name + ".jvm.args")
    arguments = ctx.actions.args()
    arguments.add("jvm-args")
    arguments.add(dist.ide_config, format = "--ide-config=%s")
    arguments.add(home_path, format = "--home=%s")
    arguments.add(placement.files["bin/idea.properties"], format = "--idea-properties=%s")
    arguments.add(placement.files[vm_options], format = "--vm-options=%s")
    arguments.add(vm_options, format = "--vm-options-destination=%s")
    arguments.add(placement.files[product_info], format = "--product-info=%s")
    arguments.add(dist.core_classpath, format = "--core-classpath=%s")
    arguments.add(flags_file, format = "--flags-file=%s")
    arguments.add_all(program_args, format_each = "--program-arg=%s")
    if _RUNTIME_MODULE_REPOSITORY_FILE in placement.files:
        arguments.add("--runtime-module-repository")
    arguments.add(argfile, format = "--output=%s")
    ctx.actions.run(
        executable = ctx.executable._launch_args_tool,
        arguments = [arguments],
        inputs = [
            dist.ide_config,
            placement.files["bin/idea.properties"],
            placement.files[vm_options],
            placement.files[product_info],
            dist.core_classpath,
            flags_file,
        ],
        outputs = [argfile],
        # The file holds absolute local paths, so no shared cache may keep it.
        execution_requirements = {"no-remote-cache": "1", "no-remote-exec": "1"},
        mnemonic = "DevLaunchJvmArgs",
        progress_message = "Writing the JVM arguments of %{label}",
    )

    ctx.actions.symlink(output = executable, target_file = _java_file(ctx, java_runtime), is_executable = True)
    runfiles = _merge_data(ctx, ctx.runfiles(
        files = [executable],
        transitive_files = java_runtime.files,
        symlinks = {ctx.label.name + ".jvm.args": argfile},
        root_symlinks = _home_root_symlinks(ctx, placement, dist),
    ))

    # The java stub of rules_java gives these two to the IDE.
    environment = dict(ctx.attr.env)
    environment.update({"JAVA_RUNFILES": runfiles_directory, "RUNFILES_DIR": runfiles_directory})

    # The run handler of the devkit Bazel plugin reads this file. It starts `java` with the debug options before the
    # argument file.
    launch = ctx.actions.declare_file(ctx.label.name + ".launch.json")
    ctx.actions.write(launch, json.encode_indent({
        "version": 2,
        "java": _execroot_path(ctx, java_runtime.java_executable_exec_path),
        "argfile": "%s/%s/%s.jvm.args" % (runfiles_directory, ctx.workspace_name, ctx.label.name),
        "runfilesDirectory": runfiles_directory,
        "workingDirectory": "%s/%s" % (runfiles_directory, ctx.workspace_name),
        "env": environment,
    }) + "\n")
    return [
        DefaultInfo(executable = executable, files = depset([executable, argfile, launch]), runfiles = runfiles),
        RunEnvironmentInfo(environment = environment),
    ]

def _vm_options_lines(ctx, placement, home_path, flags):
    """The lines of `<name>.vmoptions`, the user vmoptions file of the native launcher.

    The lines are the flags of the row without `_CUSTOM_COMMAND_FLAG`. With `runtime_module_repository`, the last line
    names `modules/module-descriptors.dat` of the home, unless a flag states the property. The launcher trims each line
    and skips an empty line and a `#` line, so such a line fails here.
    """
    lines = [flag for flag in flags if flag != _CUSTOM_COMMAND_FLAG]
    if ctx.attr.runtime_module_repository:
        if _RUNTIME_MODULE_REPOSITORY_FILE not in placement.files:
            fail("%s: `runtime_module_repository` is set, and the home has no %s" % (ctx.label, _RUNTIME_MODULE_REPOSITORY_FILE))
        if not [line for line in lines if line.startswith(_RUNTIME_MODULE_REPOSITORY_PROPERTY)]:
            lines.append(_RUNTIME_MODULE_REPOSITORY_PROPERTY + home_path + "/" + _RUNTIME_MODULE_REPOSITORY_FILE)
    for line in lines:
        if not line or line != line.strip() or line.startswith("#") or "\n" in line or "\r" in line:
            fail("%s: the JVM flag %s cannot be one line of a vmoptions file" % (ctx.label, repr(line)))
    return lines

def _native_launch(ctx, os, dist, placement, java_runtime, flags, program_args):
    """The launch of macOS and Windows: a stub that starts the production native launcher of the home.

    The launcher reads `<name>.vmoptions` through `<ENV>_VM_OPTIONS` and starts the runtime of `<ENV>_JDK`.
    """
    windows = os == "windows"
    launcher = _os_destination(ctx, "launcher", os)
    if launcher not in placement.files or launcher not in placement.executables:
        fail("%s: the home has no executable launcher %s" % (ctx.label, launcher))
    for name in ["_VM_OPTIONS", "_JDK"]:
        if ctx.attr.env_var_base_name + name in ctx.attr.env:
            fail("%s: `env` states %s, which the rule owns" % (ctx.label, ctx.attr.env_var_base_name + name))

    executable = ctx.actions.declare_file(ctx.label.name + (".cmd" if windows else ""))
    runfiles_directory = _execroot_path(ctx, executable.path) + ".runfiles"
    if windows:
        if not ctx.attr.export:
            fail("%s: a launch target on Windows needs `export`, the self-contained distribution of the row" % ctx.label)
        export = ctx.attr.export[IntellijDevDistInfo]
        home_path = _execroot_path(ctx, export.home.path)
    else:
        export = None
        home_path = runfiles_directory + "/" + _LAUNCH_HOME
    launcher_path = home_path + "/" + launcher

    vm_options_file = ctx.actions.declare_file(ctx.label.name + ".vmoptions")
    ctx.actions.write(vm_options_file, "".join([line + "\n" for line in _vm_options_lines(ctx, placement, home_path, flags)]))
    vm_options_path = _execroot_path(ctx, vm_options_file.path)

    if windows:
        jdk_path = _execroot_path(ctx, java_runtime.java_home)
        jdk_files = []
    else:
        # The launcher appends `Contents/Home` to a runtime on macOS, and the runtime of the toolchain is flat. So
        # `<name>.jdk/Contents/Home` is a link to the runtime.
        jdk_link = ctx.actions.declare_symlink(ctx.label.name + ".jdk/Contents/Home")
        ctx.actions.symlink(output = jdk_link, target_path = _execroot_path(ctx, java_runtime.java_home))
        jdk_path = _execroot_path(ctx, executable.path) + ".jdk"
        jdk_files = [jdk_link]
    launch_environment = {
        ctx.attr.env_var_base_name + "_VM_OPTIONS": vm_options_path,
        ctx.attr.env_var_base_name + "_JDK": jdk_path,
    }

    # The stub sets the two variables itself, so a start of the stub outside `bazel run` never reads the user
    # vmoptions file of an installed IDE.
    if windows:
        ctx.actions.write(
            executable,
            "@echo off\r\nsetlocal\r\n" +
            "".join(['set "%s=%s"\r\n' % (name, _windows_path(ctx.label, value)) for name, value in launch_environment.items()]) +
            " ".join(['"%s"' % _windows_path(ctx.label, launcher_path)] + [_cmd_argument(ctx.label, argument) for argument in program_args] + ["%*"]) + "\r\n",
            is_executable = True,
        )
        runfiles = ctx.runfiles(files = [executable, vm_options_file, export.home, export.ide_config], transitive_files = java_runtime.files)
        environment = dict(ctx.attr.env)

        # `bazel run` starts the stub in `<name>.cmd.runfiles`.
        working_directory = runfiles_directory
    else:
        ctx.actions.write(
            executable,
            "#!/bin/sh\n" +
            "".join(["export %s=%s\n" % (name, shell.quote(value)) for name, value in launch_environment.items()]) +
            "exec %s\n" % " ".join([shell.quote(launcher_path)] + [shell.quote(argument) for argument in program_args] + ['"$@"']),
            is_executable = True,
        )
        runfiles = ctx.runfiles(
            files = [executable, vm_options_file] + jdk_files,
            transitive_files = java_runtime.files,
            root_symlinks = _home_root_symlinks(ctx, placement, dist),
        )
        environment = dict(ctx.attr.env)
        environment.update({"JAVA_RUNFILES": runfiles_directory, "RUNFILES_DIR": runfiles_directory})
        working_directory = "%s/%s" % (runfiles_directory, ctx.workspace_name)
    environment.update(launch_environment)
    runfiles = _merge_data(ctx, runfiles)

    launch = ctx.actions.declare_file(ctx.label.name + ".launch.json")
    ctx.actions.write(launch, json.encode_indent({
        "version": 3,
        "launcher": launcher_path,
        "vmOptionsFile": vm_options_path,
        "programArguments": program_args,
        "runfilesDirectory": runfiles_directory,
        "workingDirectory": working_directory,
        "env": environment,
    }) + "\n")
    return [
        DefaultInfo(executable = executable, files = depset([executable, vm_options_file, launch] + jdk_files), runfiles = runfiles),
        RunEnvironmentInfo(environment = environment),
    ]

def _intellij_dev_java_launcher_impl(ctx):
    os = _target_os(ctx)
    dist = ctx.attr.dist[IntellijDevDistInfo]
    placement = dist.placement
    if placement == None or dist.core_classpath == None:
        fail("%s: %s is no local launch with a placement for every component" % (ctx.label, ctx.attr.dist.label))
    vm_options = _os_destination(ctx, "vm_options", os)
    product_info = _os_destination(ctx, "product_info", os)
    for destination in ["bin/idea.properties", vm_options, product_info]:
        if destination not in placement.files:
            fail("%s: the home has no %s" % (ctx.label, destination))

    # `$$` is a literal `$` in the macro, so a flag reaches the rule as `${NAME}`.
    targets = ctx.attr.data + [ctx.attr.dist]
    flags = [
        _resolve_variables(ctx.label, flag)
        for flag in ctx.fragments.java.default_jvm_opts + [
            ctx.expand_make_variables("jvm_flags", ctx.expand_location(flag, targets), {})
            for flag in ctx.attr.jvm_flags
        ]
    ]

    # Each value is one program argument, as the run configuration of the IDE passes it. So the rule splits no value at
    # a space, which Bazel does for a value of `args`.
    program_args = [
        _resolve_variables(ctx.label, ctx.expand_make_variables("program_args", ctx.expand_location(argument, targets), {}))
        for argument in ctx.attr.program_args
    ]

    java_runtime = ctx.toolchains["@bazel_tools//tools/jdk:runtime_toolchain_type"].java_runtime
    if os == "linux":
        add_opens = ["--add-opens=%s=ALL-UNNAMED" % package for package in ctx.attr.add_opens]
        return _java_launch(ctx, dist, placement, vm_options, product_info, java_runtime, flags + add_opens, program_args)

    # The native launcher applies the `--add-opens` lines of `additionalJvmArguments`. The product files action renders
    # them for the target OS, as production does, so the vmoptions file of the row has none.
    return _native_launch(ctx, os, dist, placement, java_runtime, flags, program_args)

intellij_dev_java_launcher = rule(
    doc = """Starts a composed dev distribution, `bazel run //<package>:<name>`.

On macOS and Windows the executable is a stub that starts the production native launcher of the home. The stub sets
`<ENV>_VM_OPTIONS` to `<name>.vmoptions` and `<ENV>_JDK` to the Java runtime. Then it runs the launcher with the
program arguments of the row and its own arguments. `<name>.vmoptions` holds the JVM flags of the row, one per line.
The launcher reads it after the vmoptions file of the distribution and before `additionalJvmArguments` of
`product-info.json`. The launcher takes the `--add-opens` lines of that list, which the product files action renders
for the target OS. So the file has no `add_opens` line. The launcher selects a custom command from the first program
argument. On macOS the stub is an `sh` file
and the home is `<name>.runfiles/ide_home`. The launcher keeps the path of the link, so it finds
`ide_home/Resources/product-info.json`. `<ENV>_JDK` names `<name>.jdk`, whose `Contents/Home` links to the flat
runtime. Windows builds no runfiles tree, so the stub is `<name>.cmd` and the home is the directory of `export`. The
stub runs the console launcher, so `bazel run` keeps a console.

On Linux the launcher finds no home through a link (IJPL-258306). So the executable is a link to the `java` of the
Java runtime. Its `args` are `@<name>.jvm.args`, the argument file that `dev-launch-args jvm-args` writes at build time.
The file holds the `add_opens` lines and ends with the main class and the program arguments of the row. A caller adds
JVM flags through `JDK_JAVA_OPTIONS`.

`bazel run //<package>:<name> -- <argument>` adds a program argument after the ones of the row. No process runs
before the native launcher or `java`, and a launch writes no file. `bazel run` starts the executable in
`<name>.runfiles/_main`, or in `<name>.cmd.runfiles` on Windows, so every path is absolute. `<name>.launch.json` states
the absolute paths, the working directory and the environment of a launch. On Linux it has version 2 and names `java`
and the argument file. On macOS and Windows it has version 3 and names the launcher, the vmoptions file and the program
arguments.""",
    implementation = _intellij_dev_java_launcher_impl,
    executable = True,
    fragments = ["java"],
    toolchains = ["@bazel_tools//tools/jdk:runtime_toolchain_type"],
    attrs = {
        "dist": attr.label(mandatory = True, providers = [IntellijDevDistInfo], doc = "The local launch of the distribution, the `_dist_launch` target. Its placement names the files of the home."),
        "export": attr.label(providers = [IntellijDevDistInfo], doc = "The self-contained distribution, the `_dist` target. The home on Windows, where it is mandatory."),
        "vm_options": attr.string_dict(mandatory = True, doc = "The destination of the main vmoptions file in the home, `bin/<name>`, for each OS token: `darwin`, `linux` and `windows`. The argument file writer reads the file there."),
        "product_info": attr.string_dict(mandatory = True, doc = "The destination of `product-info.json` in the home, for each OS token. The production place is the root, or `Resources/` for a macOS app."),
        "launcher": attr.string_dict(doc = "The destination of the native launcher in the home, for each OS token that starts one: `darwin` and `windows`. Linux starts `java`."),
        "env_var_base_name": attr.string(doc = "The base of the variables `<base>_VM_OPTIONS` and `<base>_JDK` that the native launcher reads. It is the `envVarBaseName` of the product, or of the custom command that the first program argument selects."),
        "runtime_module_repository": attr.bool(doc = "Whether `<name>.vmoptions` names `modules/module-descriptors.dat` of the home as the runtime module repository. Set it when the home has the file and `product-info.json` does not state the property."),
        "jvm_flags": attr.string_list(doc = "JVM flags; `$(location)` and make variables expand, and `${BUILD_WORKSPACE_DIRECTORY}` and `${HOME}` resolve at analysis."),
        "add_opens": attr.string_list(doc = "Packages opened to the unnamed module, as `java_binary.add_opens`. Only the Linux argument file holds them. The native launcher takes the `--add-opens` lines of `product-info.json`."),
        "env": attr.string_dict(doc = "Environment variables `bazel run` sets for the IDE."),
        "data": attr.label_list(allow_files = True, doc = "Extra runfiles of the launch target."),
        "program_args": attr.string_list(doc = "The program arguments of the row, which come before the arguments of `bazel run`. Each value is one argument; `$(location)` and make variables expand, and `${BUILD_WORKSPACE_DIRECTORY}` and `${HOME}` resolve at analysis. The first one can select a custom command."),
        "_launch_args_tool": attr.label(default = Label("//build/dev-dist-tools/bins/dev-launch-args"), executable = True, cfg = "exec"),
        "_linux": attr.label(default = Label("@platforms//os:linux")),
        "_macos": attr.label(default = Label("@platforms//os:macos")),
        "_windows": attr.label(default = Label("@platforms//os:windows")),
    },
)

def intellij_dev_java_launcher_binary(
        name,
        dist,
        export,
        vm_options,
        product_info,
        launcher,
        env_var_base_name,
        runtime_module_repository,
        jvm_flags = [],
        env = {},
        program_args = [],
        data = [],
        visibility = None):
    """The launch target of a composed dev distribution.

    `dist` is the `_dist_launch` target of the distribution, and `export` is its `_dist` target. Only Windows builds
    `export`. `vm_options`, `product_info` and `launcher` map each OS token to the destination of the main vmoptions
    file, of `product-info.json` and of the native launcher in the home. `env_var_base_name` and
    `runtime_module_repository` are the attributes of the rule. The config and system directories are
    `out/dev-data/<name>/config` and `out/dev-data/<name>/system`, as absolute paths.
    """
    intellij_dev_java_launcher(
        name = name,
        visibility = visibility,
        tags = ["manual"],
        dist = dist,
        export = select({
            "@platforms//os:windows": export,
            "//conditions:default": None,
        }),
        vm_options = vm_options,
        product_info = product_info,
        launcher = launcher,
        env_var_base_name = env_var_base_name,
        runtime_module_repository = runtime_module_repository,
        jvm_flags = dev_launch_jvm_flags(name, jvm_flags),
        add_opens = INTELLIJ_ADD_OPENS,
        env = env,
        # On Linux `bazel run` starts `java` in `<name>.runfiles/_main`, which holds the argument file. The file holds
        # the program arguments, so a `bazel run` argument comes after them. The stub of macOS and Windows names the
        # program arguments itself.
        args = select({
            "@platforms//os:linux": ["@%s.jvm.args" % name],
            "//conditions:default": [],
        }),
        program_args = program_args,
        data = data,
    )
