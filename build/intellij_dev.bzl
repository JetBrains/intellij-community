"""Macros for IntelliJ-based IDE development builds."""

load("@bazel_skylib//lib:shell.bzl", "shell")
load("@bazel_skylib//rules:build_test.bzl", "build_test")
load("@dev_launch_paths//:paths.bzl", "HOME", "OUTPUT_BASE", "WORKSPACE_ROOT")
load("//platform/build-scripts/bazel-rules:intellij_dev_dist.bzl", "IntellijDevDistInfo", "dist_home_path")

# Names the prepared distribution for its consumer: the prebuilt dev-build runner of IDE Starter in a test, and
# `PreBuiltDevMain` in the docker run configuration. Keep in sync with `DevIdeConfig.CONFIG_PATH_PROPERTY`, which is
# where the reading side of this contract lives.
DEV_IDE_CONFIG_PATH_PROPERTY = "idea.ide.config.path"

def intellij_dev_dist_config(name, dist, visibility = None):
    """A single-file label for an assembled dev distribution's config file, for `$(rlocationpath ...)`.

    That expansion takes a label naming exactly one file, which a dist target - two outputs, one of them declared rather
    than predeclared - is not. Its `ide_config` output group is how it gets one.

    A consumer declares both this and the dist itself in `data`, and they must stay siblings in the runfiles tree: the
    config names the home relatively, so that the pair survives being read from a different path than it was written to.

    The filegroup is `manual`. The compose action writes the config, so a wildcard build that matches the filegroup
    composes the distribution. A consumer names the label, so it does not need a wildcard.
    """
    native.filegroup(
        name = name,
        srcs = [dist],
        output_group = "ide_config",
        tags = ["manual"],
        visibility = visibility,
    )

def dev_dist_build_test(name, targets, tags = [], **kwargs):
    """A skylib `build_test` that gates the build of dev distribution targets, kept out of an IDE sync.

    A "Build and Resync" of the Bazel plugin builds the generated `srcs` of every `genrule` below a sync target. The
    genrules of a `build_test` have the gated targets as `srcs`, so a sync would compose each gated distribution. The
    sync drops a target with the `no-ide` tag. Bazel and CI ignore the tag, so the `ultimate-smoke` pass still runs the
    gate.
    """
    build_test(
        name = name,
        targets = targets,
        tags = tags + ["no-ide"],
        **kwargs
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
    runtime module repository, as the vmoptions file of a generation does. Pass it only when the export has that
    file and its `product-info.json` does not state the property.
    """
    lines = dev_launch_jvm_flags(name, workspace = WORKSPACE_ROOT)
    if runtime_module_repository:
        lines.append(_RUNTIME_MODULE_REPOSITORY_PROPERTY + "${EXPORT}/" + _RUNTIME_MODULE_REPOSITORY_FILE)
    return {"sibling_vm_options": lines, "output_base": OUTPUT_BASE}

# The variables that `bazel run` removes from the environment of the executable. A run handler removes them too.
_BAZEL_RUN_REMOVED_VARIABLES = ["JAVA_RUNFILES", "RUNFILES_DIR", "RUNFILES_MANIFEST_FILE", "RUNFILES_MANIFEST_ONLY", "TEST_SRCDIR"]

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

def _home_files(placement, dist):
    """The entries of the home: a dictionary from a destination to its `File`, a file or a tree.

    A file is where Bazel wrote it, a source file included. `dev-home` sets the mode of each file from the layout.
    """
    home = dict(placement.files)
    home.update(placement.trees)
    home["fingerprint.txt"] = dist.fingerprint
    if dist.plugin_classpath:
        home["plugins/plugin-classpath.txt"] = dist.plugin_classpath
    return home

def _dev_home_root():
    """The root of the store and the generations of `dev-home`: `dev-home` beside the output base, on its volume."""
    return OUTPUT_BASE.rpartition("/")[0] + "/dev-home"

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

def _vm_options_lines(ctx, placement, flags):
    """The lines of `<name>.vmoptions`, the flags of the row, one per line.

    `dev-home` writes the vmoptions file of the generation from them. With `runtime_module_repository` it adds the line
    that names `modules/module-descriptors.dat` of the generation, unless a flag states the property. The launcher
    trims each line and skips an empty line and a `#` line, so such a line fails here.
    """
    lines = list(flags)
    if ctx.attr.runtime_module_repository and _RUNTIME_MODULE_REPOSITORY_FILE not in placement.files:
        fail("%s: `runtime_module_repository` is set, and the home has no %s" % (ctx.label, _RUNTIME_MODULE_REPOSITORY_FILE))
    for line in lines:
        if not line or line != line.strip() or line.startswith("#") or "\n" in line or "\r" in line:
            fail("%s: the JVM flag %s cannot be one line of a vmoptions file" % (ctx.label, repr(line)))
    return lines

def _states_repository(lines):
    """Whether a flag states the runtime module repository itself."""
    return bool([line for line in lines if line.startswith(_RUNTIME_MODULE_REPOSITORY_PROPERTY)])

def _sh_stub(tool, spec, program_args):
    """The text of the `sh` stub of macOS and Linux.

    `dev-home` prints the launcher of the generation and the vmoptions variable as `sh` lines, and the stub `exec`s the
    launcher. So the IDE has the pid of the stub, and no process stays before it.
    """
    lines = [
        "#!/bin/sh",
        "dev_home_env=$(%s materialize %s --format=sh) || exit 1" % (shell.quote(tool), shell.quote("--spec=" + spec)),
        'eval "$dev_home_env"',
        " ".join(['exec "$DEV_HOME_LAUNCHER"'] + [shell.quote(argument) for argument in program_args] + ['"$@"']),
    ]
    return "".join([line + "\n" for line in lines])

def _cmd_stub(label, tool, spec, program_args):
    """The text of the `.cmd` stub of Windows.

    `dev-home` writes two `set` lines into a temporary file, which the stub calls and deletes. The start of the launcher
    and the `exit` are on one line: `cmd.exe` reads a batch file line by line, and Bazel can rewrite the stub while the
    IDE runs.
    """
    return "".join([line + "\r\n" for line in [
        "@echo off",
        "setlocal",
        'set "DEV_HOME_ENV=%TEMP%\\dev-home-%RANDOM%%RANDOM%.cmd"',
        '"%s" materialize "--spec=%s" --format=cmd "--output=%%DEV_HOME_ENV%%" || exit /b 1' % (_windows_path(label, tool), _windows_path(label, spec)),
        'call "%DEV_HOME_ENV%"',
        'del "%DEV_HOME_ENV%"',
        " ".join(['"%DEV_HOME_LAUNCHER%"'] + [_cmd_argument(label, argument) for argument in program_args] + ["%*", "& exit /b"]),
    ]])

def _native_launch(ctx, os, dist, placement, flags, program_args):
    """The launch of a target: a stub that runs `dev-home` and then the production native launcher of the generation.

    The launcher reads the vmoptions file of the generation through `<ENV>_VM_OPTIONS`. It finds the bundled runtime at
    `<home>/jbr`.
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

    lines = _vm_options_lines(ctx, placement, flags)
    vm_options_file = ctx.actions.declare_file(ctx.label.name + ".vmoptions")
    ctx.actions.write(vm_options_file, "".join([line + "\n" for line in lines]))

    # The home spec names each file of the home by its absolute path, so `dev-home` reads no runfiles tree.
    home = _home_files(placement, dist)
    files = {destination: _execroot_path(ctx, file.path) for destination, file in home.items() if not file.is_directory}
    trees = {destination: _execroot_path(ctx, file.path) for destination, file in home.items() if file.is_directory}
    layout = _execroot_path(ctx, dist_home_path(dist)) + "/local-layout.json"
    tool = ctx.attr._dev_home[DefaultInfo].files_to_run.executable
    tool_path = _execroot_path(ctx, tool.path)
    spec_file = ctx.actions.declare_file(ctx.label.name + ".home.json")
    spec_path = _execroot_path(ctx, spec_file.path)

    environment = dict(ctx.attr.env)
    if windows:
        # `bazel run` starts the stub in `<name>.cmd.runfiles`.
        working_directory = runfiles_directory
    else:
        environment.update({"JAVA_RUNFILES": runfiles_directory, "RUNFILES_DIR": runfiles_directory})
        working_directory = "%s/%s" % (runfiles_directory, ctx.workspace_name)

    # `bazel run` starts the stub with these variables, and a run handler starts the launcher with them.
    launch_environment = dict(environment)
    launch_environment.update({"BUILD_WORKING_DIRECTORY": WORKSPACE_ROOT, "BUILD_WORKSPACE_DIRECTORY": WORKSPACE_ROOT})
    ctx.actions.write(spec_file, json.encode({
        "version": 1,
        "target": str(ctx.label),
        "root": _dev_home_root(),
        "fingerprint": _execroot_path(ctx, dist.fingerprint.path),
        "homePrefix": dist.home_prefix or "",
        "layout": layout,
        "files": files,
        "trees": trees,
        "launcher": launcher,
        "vmOptions": _execroot_path(ctx, vm_options_file.path),
        "vmOptionsVariable": ctx.attr.env_var_base_name + "_VM_OPTIONS",
        "runtimeModuleRepository": ctx.attr.runtime_module_repository and not _states_repository(lines),
        "launch": {
            "programArguments": program_args,
            "workingDirectory": working_directory,
            "env": launch_environment,
            "unsetEnv": _BAZEL_RUN_REMOVED_VARIABLES,
        },
    }))

    if windows:
        ctx.actions.write(executable, _cmd_stub(ctx.label, tool_path, spec_path, program_args), is_executable = True)
    else:
        ctx.actions.write(executable, _sh_stub(tool_path, spec_path, program_args), is_executable = True)

    # The files of the home are runfiles, so `bazel run` builds them, and `bazel build` lists only the files below.
    # `dev-home` reads each one where Bazel wrote it, through the home spec, not through the runfiles tree.
    runfiles = ctx.runfiles(
        files = [executable, vm_options_file, spec_file],
        transitive_files = depset(list(home.values()) + [dist.home]),
    ).merge(ctx.attr._dev_home[DefaultInfo].default_runfiles)
    runfiles = _merge_data(ctx, runfiles)

    # A run handler reads only the command. The answer of `dev-home` names the rest of the launch, so a change of the
    # launch stays in the checkout.
    launch_content = {
        "version": 4,
        "materializer": [tool_path, "materialize", "--spec=" + spec_path, "--format=json"],
    }
    launch = ctx.actions.declare_file(ctx.label.name + ".launch.json")
    ctx.actions.write(launch, json.encode_indent(launch_content) + "\n")
    return [
        DefaultInfo(
            executable = executable,
            files = depset([executable, vm_options_file, launch, spec_file]),
            runfiles = runfiles,
        ),
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

The executable is a stub. It runs `dev-home`, which materializes the generation of the distribution: a home of hard
links outside the output base, keyed by the fingerprint. So no IDE runs from a file that Bazel owns, and Bazel can
rebuild while an IDE runs. Then the stub starts the production native launcher of the generation with the program
arguments of the row and its own arguments. `dev-home` sets one variable, `<ENV>_VM_OPTIONS`, to the vmoptions file of
the generation.

`<name>.vmoptions` holds the JVM flags of the row, one per line. `dev-home` writes the vmoptions file of the generation
from it, with the runtime module repository line of `runtime_module_repository`. The launcher reads that file after
the vmoptions file of the distribution and before `additionalJvmArguments` of `product-info.json`. The launcher takes
the `--add-opens` lines of that list, which the product files action renders for the target OS. So the file has no
`--add-opens` line. The launcher finds the bundled runtime at `<home>/jbr`, its third place, and the rule checks that
the home has `bin/java` of it. The launcher selects a custom command from the first program argument.

`<name>.home.json` names the source file of each destination of the home by its absolute path. The files of the home
are runfiles of the target, so `bazel run` builds them, and `dev-home` reads them where Bazel wrote them.

On macOS and Linux the stub is an `sh` file. It `eval`s the lines that `dev-home` prints and `exec`s the launcher, so
the IDE keeps the pid of the stub. On Windows the stub is `<name>.cmd`. `dev-home` writes two `set` lines into a
temporary file, which the stub calls. The stub runs the console launcher, so `bazel run` keeps a console.

`bazel run //<package>:<name> -- <argument>` adds a program argument after the ones of the row. `bazel run` starts the
executable in `<name>.runfiles/_main`, or in `<name>.cmd.runfiles` on Windows. `<name>.launch.json` has version 4. It
states the command of `dev-home` that prints the generation as JSON, and nothing else. The answer names the launcher, the vmoptions file, the program
arguments, the working directory, the variables that `bazel run` sets and the ones that it removes. An IDE run handler
runs that command, writes a copy of the vmoptions file with its debug options, and starts the launcher itself.""",
    implementation = _intellij_dev_java_launcher_impl,
    executable = True,
    fragments = ["java"],
    attrs = {
        "dist": attr.label(mandatory = True, providers = [IntellijDevDistInfo], doc = "The local launch of the distribution, the `_dist_launch` target. Its placement names the files of the home, its layout the hashes and the modes, and its `home_prefix` the directory of the home below the generation."),
        "vm_options": attr.string_dict(mandatory = True, doc = "The destination of the main vmoptions file in the home, `bin/<name>`, for each OS token: `darwin`, `linux` and `windows`. The native launcher reads the file there, and the rule checks that the home has it."),
        "product_info": attr.string_dict(mandatory = True, doc = "The destination of `product-info.json` in the home, for each OS token. The production place is the root, or `Resources/` for a macOS app."),
        "launcher": attr.string_dict(doc = "The destination of the native launcher in the home, for each OS token: `darwin`, `linux` and `windows`."),
        "env_var_base_name": attr.string(doc = "The base of the variable `<base>_VM_OPTIONS` that the native launcher reads. It is the `envVarBaseName` of the product, or of the custom command that the first program argument selects."),
        "runtime_module_repository": attr.bool(doc = "Whether the vmoptions file of the generation names `modules/module-descriptors.dat` of the home as the runtime module repository. Set it when the home has the file and `product-info.json` does not state the property."),
        "jvm_flags": attr.string_list(doc = "JVM flags; `$(location)` and make variables expand, and `${BUILD_WORKSPACE_DIRECTORY}` and `${HOME}` resolve at analysis."),
        "env": attr.string_dict(doc = "Environment variables `bazel run` sets for the IDE."),
        "data": attr.label_list(allow_files = True, doc = "Extra runfiles of the launch target."),
        "program_args": attr.string_list(doc = "The program arguments of the row, which come before the arguments of `bazel run`. Each value is one argument; `$(location)` and make variables expand, and `${BUILD_WORKSPACE_DIRECTORY}` and `${HOME}` resolve at analysis. The first one can select a custom command."),
        "_dev_home": attr.label(default = Label("//build/dev-dist-tools/bins/dev-home:dev-home_opt"), executable = True, cfg = "target"),
        "_linux": attr.label(default = Label("@platforms//os:linux")),
        "_macos": attr.label(default = Label("@platforms//os:macos")),
        "_windows": attr.label(default = Label("@platforms//os:windows")),
    },
)

def intellij_dev_java_launcher_binary(
        name,
        dist,
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

    `dist` is the `_dist_launch` target of the distribution. `vm_options`, `product_info` and `launcher` map each OS token to the destination of the main vmoptions
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
