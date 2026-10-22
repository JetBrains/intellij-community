"""Macros for IntelliJ-based IDE development builds."""

load("@bazel_skylib//lib:shell.bzl", "shell")
load("@dev_launch_paths//:paths.bzl", "HOME", "OUTPUT_BASE", "WORKSPACE_ROOT")
load("//platform/build-scripts/bazel-rules:intellij_dev_dist.bzl", "IntellijDevDistInfo", "dist_home_path")

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

# The workspace root in the flags of a launch target. `$$` is a literal `$`, so the rule gets
# `${BUILD_WORKSPACE_DIRECTORY}` and resolves it at analysis.
_LAUNCHER_WORKSPACE = "$${BUILD_WORKSPACE_DIRECTORY}"

def dev_launch_jvm_flags(name, jvm_flags = [], workspace = _LAUNCHER_WORKSPACE):
    """The flags an IDE needs to run, independent of how it was assembled.

    The data directories are below `out/dev-data/<name>`: `config`, `system`, `system/log` and `config/plugins`. On
    macOS `out/dev-data` is a link to a directory outside the workspace. `jvm_flags` must not state a data directory.

    `workspace` spells the workspace root. A launch target keeps the default, which its rule resolves at analysis.
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
    `runtime_module_repository`, the last line names `modules/module-descriptors.dat` of the home of the export as the
    runtime module repository, as the vmoptions file of a launch target does. Pass it only when the export has that
    file and its `product-info.json` does not state the property.
    """
    lines = dev_launch_jvm_flags(name, workspace = WORKSPACE_ROOT)
    if runtime_module_repository:
        lines.append(_RUNTIME_MODULE_REPOSITORY_PROPERTY + "${EXPORT}/" + _RUNTIME_MODULE_REPOSITORY_FILE)
    return {"sibling_vm_options": lines, "output_base": OUTPUT_BASE}

# The directory of the runfiles tree that holds the home of a launch target on Linux and macOS. The home is this
# directory, or its `home_prefix` child as in the export: `ide_home/Contents` on macOS.
_LAUNCH_HOME = "ide_home"

# The `java` of the bundled runtime in the home, for each OS token. The native launcher finds the runtime at `<home>/jbr`
# when no variable and no user file names one. On macOS it appends `Contents/Home` to the runtime.
_RUNTIME_JAVA = {
    "darwin": "jbr/Contents/Home/bin/java",
    "linux": "jbr/bin/java",
    "windows": "jbr/bin/java.exe",
}

# The variables that a flag of a launch target can name. The native launcher does not expand them, so the rule resolves
# them at analysis.
_LAUNCH_VARIABLES = {
    "BUILD_WORKSPACE_DIRECTORY": WORKSPACE_ROOT,
    "HOME": HOME,
}

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

def _runfiles_home_root(dist):
    """The path of the runfiles home relative to the runfiles directory: `ide_home`, or `ide_home/<home_prefix>`."""
    if dist.home_prefix:
        return _LAUNCH_HOME + "/" + dist.home_prefix
    return _LAUNCH_HOME

def _home_root_symlinks(dist, home):
    """The `root_symlinks` of the runfiles home below `ide_home`. `home` is the dictionary of `_runfiles_home`."""
    root = _runfiles_home_root(dist)
    return {root + "/" + destination: file for destination, file in home.items()}

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

def _vm_options_lines(ctx, placement, home_path, flags):
    """The lines of `<name>.vmoptions`, the user vmoptions file of the native launcher.

    The lines are the flags of the row. With `runtime_module_repository`, the last line names
    `modules/module-descriptors.dat` of the home, unless a flag states the property. The launcher trims each line and
    skips an empty line and a `#` line, so such a line fails here.
    """
    lines = list(flags)
    if ctx.attr.runtime_module_repository:
        if _RUNTIME_MODULE_REPOSITORY_FILE not in placement.files:
            fail("%s: `runtime_module_repository` is set, and the home has no %s" % (ctx.label, _RUNTIME_MODULE_REPOSITORY_FILE))
        if not [line for line in lines if line.startswith(_RUNTIME_MODULE_REPOSITORY_PROPERTY)]:
            lines.append(_RUNTIME_MODULE_REPOSITORY_PROPERTY + home_path + "/" + _RUNTIME_MODULE_REPOSITORY_FILE)
    for line in lines:
        if not line or line != line.strip() or line.startswith("#") or "\n" in line or "\r" in line:
            fail("%s: the JVM flag %s cannot be one line of a vmoptions file" % (ctx.label, repr(line)))
    return lines

def _sh_stub(launch_environment, launcher_path, program_args, launcher_source):
    """The text of the `sh` stub of macOS and Linux.

    With `launcher_source`, the absolute path of the launcher file that Bazel builds, the stub first makes the launcher
    of the home a real file with the same bytes. Linux passes it.
    """
    lines = ["#!/bin/sh"] + ["export %s=%s" % (name, shell.quote(value)) for name, value in launch_environment.items()]
    launcher = shell.quote(launcher_path)
    if launcher_source:
        lines.extend([
            "# The launcher reads its path from /proc/self/exe, which resolves every link. So the launcher of the home",
            "# must be a real file. Bazel restores the link when the runfiles manifest changes. Then this step copies",
            "# the file again. IJPL-258306 removes this step.",
            "launcher=" + launcher,
            "source=" + shell.quote(launcher_source),
            'if [ -L "$launcher" ] || ! cmp -s "$source" "$launcher" 2>/dev/null; then',
            '  cp "$source" "$launcher.tmp.$$" && chmod 755 "$launcher.tmp.$$" && mv -f "$launcher.tmp.$$" "$launcher" || {',
            '    rm -f "$launcher.tmp.$$"',
            "    exit 1",
            "  }",
            "fi",
        ])
        launcher = '"$launcher"'
    lines.append("exec " + " ".join([launcher] + [shell.quote(argument) for argument in program_args] + ['"$@"']))
    return "".join([line + "\n" for line in lines])

def _native_launch(ctx, os, dist, placement, flags, program_args):
    """The launch of a target: a stub that starts the production native launcher of the home.

    The launcher reads `<name>.vmoptions` through `<ENV>_VM_OPTIONS`. It finds the bundled runtime at `<home>/jbr`.
    """
    windows = os == "windows"
    launcher = _os_destination(ctx, "launcher", os)
    if launcher not in placement.files or launcher not in placement.executables:
        fail("%s: the home has no executable launcher %s" % (ctx.label, launcher))
    java = _RUNTIME_JAVA[os]
    if java not in placement.files or (not windows and java not in placement.executables):
        fail("%s: the home has no executable runtime %s" % (ctx.label, java))
    if ctx.attr.env_var_base_name + "_VM_OPTIONS" in ctx.attr.env:
        fail("%s: `env` states %s, which the rule owns" % (ctx.label, ctx.attr.env_var_base_name + "_VM_OPTIONS"))

    executable = ctx.actions.declare_file(ctx.label.name + (".cmd" if windows else ""))
    runfiles_directory = _execroot_path(ctx, executable.path) + ".runfiles"
    if windows:
        if not ctx.attr.export:
            fail("%s: a launch target on Windows needs `export`, the self-contained distribution of the row" % ctx.label)
        export = ctx.attr.export[IntellijDevDistInfo]
        home = None
        home_path = _execroot_path(ctx, dist_home_path(export))
    else:
        export = None

        # One call declares each executable copy once. The root symlinks and the Linux stub share the result.
        home = _runfiles_home(ctx, placement, dist)
        home_path = runfiles_directory + "/" + _runfiles_home_root(dist)
    launcher_path = home_path + "/" + launcher

    vm_options_file = ctx.actions.declare_file(ctx.label.name + ".vmoptions")
    ctx.actions.write(vm_options_file, "".join([line + "\n" for line in _vm_options_lines(ctx, placement, home_path, flags)]))
    vm_options_path = _execroot_path(ctx, vm_options_file.path)

    vm_options_variable = ctx.attr.env_var_base_name + "_VM_OPTIONS"
    launch_environment = {
        vm_options_variable: vm_options_path,
    }

    # The stub sets the variable itself, so a start of the stub outside `bazel run` never reads the user vmoptions file
    # of an installed IDE. The stub sets no `<ENV>_JDK`. So the launcher takes the user `.jdk` file of the product, or
    # else the bundled runtime of the home.
    if windows:
        ctx.actions.write(
            executable,
            "@echo off\r\nsetlocal\r\n" +
            "".join(['set "%s=%s"\r\n' % (name, _windows_path(ctx.label, value)) for name, value in launch_environment.items()]) +
            " ".join(['"%s"' % _windows_path(ctx.label, launcher_path)] + [_cmd_argument(ctx.label, argument) for argument in program_args] + ["%*"]) + "\r\n",
            is_executable = True,
        )
        runfiles = ctx.runfiles(files = [executable, vm_options_file, export.home, export.ide_config])
        environment = dict(ctx.attr.env)

        # `bazel run` starts the stub in `<name>.cmd.runfiles`.
        working_directory = runfiles_directory
        launcher_source = None
    else:
        launcher_source = _execroot_path(ctx, home[launcher].path) if os == "linux" else None
        ctx.actions.write(executable, _sh_stub(launch_environment, launcher_path, program_args, launcher_source), is_executable = True)
        runfiles = ctx.runfiles(
            files = [executable, vm_options_file],
            root_symlinks = _home_root_symlinks(dist, home),
        )
        environment = dict(ctx.attr.env)
        environment.update({"JAVA_RUNFILES": runfiles_directory, "RUNFILES_DIR": runfiles_directory})
        working_directory = "%s/%s" % (runfiles_directory, ctx.workspace_name)
    environment.update(launch_environment)
    runfiles = _merge_data(ctx, runfiles)

    launch_content = {
        "version": 3,
        "launcher": launcher_path,
        "vmOptionsFile": vm_options_path,
        "vmOptionsVariable": vm_options_variable,
        "programArguments": program_args,
        "runfilesDirectory": runfiles_directory,
        "workingDirectory": working_directory,
        "env": environment,
    }
    if launcher_source:
        launch_content["launcherSource"] = launcher_source
    launch = ctx.actions.declare_file(ctx.label.name + ".launch.json")
    ctx.actions.write(launch, json.encode_indent(launch_content) + "\n")
    return [
        DefaultInfo(executable = executable, files = depset([executable, vm_options_file, launch]), runfiles = runfiles),
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

    # The native launcher applies the `--add-opens` lines of `additionalJvmArguments`. The product files action renders
    # them for the target OS, as production does, so the vmoptions file of the row has none.
    return _native_launch(ctx, os, dist, placement, flags, program_args)

intellij_dev_java_launcher = rule(
    doc = """Starts a composed dev distribution, `bazel run //<package>:<name>`.

The executable is a stub that starts the production native launcher of the home. The stub sets one variable,
`<ENV>_VM_OPTIONS`, to `<name>.vmoptions`. Then it runs the launcher with the program arguments of the row and its own
arguments. The launcher finds the bundled runtime at `<home>/jbr`, its third place. The launcher checks `bin/java` of a
runtime, and the rule checks that the home has that file of the bundled runtime. `<name>.vmoptions` holds the JVM flags of the row, one per line. The launcher reads it after the
vmoptions file of the distribution and before `additionalJvmArguments` of `product-info.json`. The launcher takes the
`--add-opens` lines of that list, which the product files action renders for the target OS. So the file has no
`--add-opens` line. The launcher selects a custom command from the first program argument.

On macOS the stub is an `sh` file, and the home is `<name>.runfiles/ide_home/Contents`, as the home of the export is
`Contents`. The launcher keeps the path of the link, so it finds `ide_home/Contents/Resources/product-info.json`.
It appends `Contents/Home` to the runtime, so the runtime is `ide_home/Contents/jbr/Contents/Home`.

On Linux the stub is an `sh` file, and the home is `<name>.runfiles/ide_home`. The launcher resolves the links of its
own path, so from a link it finds no home (IJPL-258306). So the stub first replaces the link `ide_home/bin/<base>` with
a copy of the launcher file. It copies when the entry is a link or `cmp` finds other bytes. The runtime is
`ide_home/jbr`.

Windows builds no runfiles tree, so the stub is `<name>.cmd` and the home is the home of `export`. The runtime is
`jbr` of that home. The stub runs the console launcher, so `bazel run` keeps a console.

`bazel run //<package>:<name> -- <argument>` adds a program argument after the ones of the row. `bazel run` starts the
executable in `<name>.runfiles/_main`, or in `<name>.cmd.runfiles` on Windows, so every path is absolute. On macOS and
Windows no process runs before the native launcher, and a launch writes no file. On Linux the stub runs `cmp`, and it
writes the launcher copy after a change of the runfiles manifest. `<name>.launch.json` has version 3. It states the
launcher, the vmoptions file, the variable that names it, the program arguments, the working directory and the
environment of a launch. On Linux it also states `launcherSource`, the launcher file that the stub copies. An IDE run
handler starts the launcher itself. It names a copy of the vmoptions file with its debug options in the variable, and on
Linux it copies the launcher as the stub does.""",
    implementation = _intellij_dev_java_launcher_impl,
    executable = True,
    fragments = ["java"],
    attrs = {
        "dist": attr.label(mandatory = True, providers = [IntellijDevDistInfo], doc = "The local launch of the distribution, the `_dist_launch` target. Its placement names the files of the home, and its `home_prefix` names the directory of the home below `ide_home`."),
        "export": attr.label(providers = [IntellijDevDistInfo], doc = "The self-contained distribution, the `_dist` target. The home on Windows, where it is mandatory."),
        "vm_options": attr.string_dict(mandatory = True, doc = "The destination of the main vmoptions file in the home, `bin/<name>`, for each OS token: `darwin`, `linux` and `windows`. The native launcher reads the file there, and the rule checks that the home has it."),
        "product_info": attr.string_dict(mandatory = True, doc = "The destination of `product-info.json` in the home, for each OS token. The production place is the root, or `Resources/` for a macOS app."),
        "launcher": attr.string_dict(doc = "The destination of the native launcher in the home, for each OS token: `darwin`, `linux` and `windows`."),
        "env_var_base_name": attr.string(doc = "The base of the variable `<base>_VM_OPTIONS` that the native launcher reads. It is the `envVarBaseName` of the product, or of the custom command that the first program argument selects."),
        "runtime_module_repository": attr.bool(doc = "Whether `<name>.vmoptions` names `modules/module-descriptors.dat` of the home as the runtime module repository. Set it when the home has the file and `product-info.json` does not state the property."),
        "jvm_flags": attr.string_list(doc = "JVM flags; `$(location)` and make variables expand, and `${BUILD_WORKSPACE_DIRECTORY}` and `${HOME}` resolve at analysis."),
        "env": attr.string_dict(doc = "Environment variables `bazel run` sets for the IDE."),
        "data": attr.label_list(allow_files = True, doc = "Extra runfiles of the launch target."),
        "program_args": attr.string_list(doc = "The program arguments of the row, which come before the arguments of `bazel run`. Each value is one argument; `$(location)` and make variables expand, and `${BUILD_WORKSPACE_DIRECTORY}` and `${HOME}` resolve at analysis. The first one can select a custom command."),
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
    `out/dev-data/<name>/config` and `out/dev-data/<name>/system`, as absolute paths. The stub names the program
    arguments of the row itself, so the target has no `args`.
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
        env = env,
        program_args = program_args,
        data = data,
    )
