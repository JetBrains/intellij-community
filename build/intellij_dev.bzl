"""Macros for IntelliJ-based IDE development builds."""

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

# The data directories of the IDE. `_runtime_jvm_flags` owns them, and a flag that states one would lose to its defaults.
_DATA_PROPERTIES = ["idea.config.path", "idea.system.path", "idea.log.path"]

def _runtime_jvm_flags(name, jvm_flags):
    """The flags an IDE needs to run, independent of how it was assembled.

    `$${...}` is a literal `${...}`, which the rule resolves at analysis.

    The config and system directories are `out/dev-data/<name>/config` and `out/dev-data/<name>/system`. On macOS
    `out/dev-data` is a link to a directory outside the workspace. `jvm_flags` must not state a data directory.
    """
    for flag in jvm_flags:
        for key in _DATA_PROPERTIES:
            if flag.startswith("-D%s=" % key):
                fail("%s: `%s` states a data directory, which the rule owns" % (name, flag))

    config_path = "$${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/" + name + "/config"
    system_path = "$${BUILD_WORKSPACE_DIRECTORY}/out/dev-data/" + name + "/system"
    return DEFAULT_JVM_FLAGS + [
        "-Didea.plugins.path=" + config_path + "/plugins",
        "-Didea.log.path=" + system_path + "/log",
    ] + jvm_flags + [
        "-Didea.config.path=" + config_path,
        "-Didea.system.path=" + system_path,
    ]

# The directory of the runfiles tree that holds the home of a `java` launcher.
_JAVA_LAUNCH_HOME = "ide_home"

# The variables that a flag of a `java` launcher can name. `java` expands none, so the rule resolves them at analysis.
_JAVA_LAUNCH_VARIABLES = {
    "BUILD_WORKSPACE_DIRECTORY": WORKSPACE_ROOT,
    "HOME": HOME,
}

def _resolve_variables(label, flag):
    """`flag` with each `${NAME}` of `_JAVA_LAUNCH_VARIABLES` replaced. Any other `${NAME}` fails."""
    result = flag
    for name, value in _JAVA_LAUNCH_VARIABLES.items():
        result = result.replace("${%s}" % name, value)
    if "${" in result:
        fail("%s: `%s` names a variable that a java launcher cannot expand; it knows only %s" % (label, flag, sorted(_JAVA_LAUNCH_VARIABLES.keys())))
    return result

def _execroot_path(ctx, path):
    """The absolute path of the exec path `path`."""
    return "%s/execroot/%s/%s" % (OUTPUT_BASE, ctx.workspace_name, path)

def _windows_path(label, path):
    """`path` with backslashes, for a line of a `.cmd` file. `cmd.exe` expands `%` and ends a quoted path at `"`, so both fail."""
    if "%" in path or '"' in path:
        fail("%s: the path %s has a `%%` or a `\"`, which a `.cmd` file cannot hold" % (label, path))
    return path.replace("/", "\\")

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

def _intellij_dev_java_launcher_impl(ctx):
    os = _target_os(ctx)
    windows = os == "windows"
    dist = ctx.attr.dist[IntellijDevDistInfo]
    placement = dist.placement
    if placement == None or dist.core_classpath == None:
        fail("%s: %s is no local launch with a placement for every component" % (ctx.label, ctx.attr.dist.label))
    vm_options = _os_destination(ctx, "vm_options", os)
    product_info = _os_destination(ctx, "product_info", os)
    for destination in ["bin/idea.properties", vm_options, product_info]:
        if destination not in placement.files:
            fail("%s: the home has no %s" % (ctx.label, destination))

    java_runtime = ctx.toolchains["@bazel_tools//tools/jdk:runtime_toolchain_type"].java_runtime
    executable = ctx.actions.declare_file(ctx.label.name + (".cmd" if windows else ""))
    runfiles_directory = _execroot_path(ctx, executable.path) + ".runfiles"

    # The home and the files that the argument file action reads. The export holds the same files at the same
    # destinations as the placement of the local launch.
    if windows:
        if not ctx.attr.export:
            fail("%s: a java launcher on Windows needs `export`, the self-contained distribution of the row" % ctx.label)
        export = ctx.attr.export[IntellijDevDistInfo]
        tree = export.home.path
        home_path = _windows_path(ctx.label, _execroot_path(ctx, tree))
        sources = struct(
            ide_config = export.ide_config,
            idea_properties = tree + "/bin/idea.properties",
            vm_options = tree + "/" + vm_options,
            product_info = tree + "/" + product_info,
            core_classpath = tree + "/core-classpath.txt",
            inputs = [export.ide_config, export.home],
        )
    else:
        export = None
        home_path = runfiles_directory + "/" + _JAVA_LAUNCH_HOME
        sources = struct(
            ide_config = dist.ide_config,
            idea_properties = placement.files["bin/idea.properties"],
            vm_options = placement.files[vm_options],
            product_info = placement.files[product_info],
            core_classpath = dist.core_classpath,
            inputs = [
                dist.ide_config,
                placement.files["bin/idea.properties"],
                placement.files[vm_options],
                placement.files[product_info],
                dist.core_classpath,
            ],
        )

    # `$$` is a literal `$` in the macro, so a flag reaches the rule as `${NAME}`.
    targets = ctx.attr.data + [ctx.attr.dist]
    flags = ctx.fragments.java.default_jvm_opts + [
        ctx.expand_make_variables("jvm_flags", ctx.expand_location(flag, targets), {})
        for flag in ctx.attr.jvm_flags
    ] + ["--add-opens=%s=ALL-UNNAMED" % package for package in ctx.attr.add_opens]
    flags_file = ctx.actions.declare_file(ctx.label.name + ".jvm-flags.txt")
    ctx.actions.write(flags_file, "".join([_resolve_variables(ctx.label, flag) + "\n" for flag in flags]))

    # Each value is one program argument, as the run configuration of the IDE passes it. So the rule splits no value at
    # a space, which Bazel does for a value of `args`.
    program_args = [
        _resolve_variables(ctx.label, ctx.expand_make_variables("program_args", ctx.expand_location(argument, targets), {}))
        for argument in ctx.attr.program_args
    ]

    argfile = ctx.actions.declare_file(ctx.label.name + ".jvm.args")
    arguments = ctx.actions.args()
    arguments.add("jvm-args")
    arguments.add(sources.ide_config, format = "--ide-config=%s")
    arguments.add(home_path, format = "--home=%s")
    arguments.add(sources.idea_properties, format = "--idea-properties=%s")
    arguments.add(sources.vm_options, format = "--vm-options=%s")
    arguments.add(vm_options, format = "--vm-options-destination=%s")
    arguments.add(sources.product_info, format = "--product-info=%s")
    arguments.add(sources.core_classpath, format = "--core-classpath=%s")
    arguments.add(flags_file, format = "--flags-file=%s")
    arguments.add_all(program_args, format_each = "--program-arg=%s")
    if "modules/module-descriptors.dat" in placement.files:
        arguments.add("--runtime-module-repository")
    arguments.add(argfile, format = "--output=%s")
    ctx.actions.run(
        executable = ctx.executable._launch_args_tool,
        arguments = [arguments],
        inputs = sources.inputs + [flags_file],
        outputs = [argfile],
        # The file holds absolute local paths, so no shared cache may keep it.
        execution_requirements = {"no-remote-cache": "1", "no-remote-exec": "1"},
        mnemonic = "DevLaunchJvmArgs",
        progress_message = "Writing the JVM arguments of %{label}",
    )

    java = _execroot_path(ctx, java_runtime.java_executable_exec_path)
    if windows:
        # A link to `java.exe` cannot find the DLLs of the runtime, so the stub runs the real file. The macro passes no
        # `args` on Windows, because the stub names the argument file itself.
        ctx.actions.write(
            executable,
            '@echo off\r\n"%s" "@%s" %%*\r\n' % (_windows_path(ctx.label, java), _windows_path(ctx.label, _execroot_path(ctx, argfile.path))),
            is_executable = True,
        )
        runfiles = ctx.runfiles(files = [executable, argfile, export.home, export.ide_config], transitive_files = java_runtime.files)
        environment = dict(ctx.attr.env)
        launch_argfile = _execroot_path(ctx, argfile.path)

        # `bazel run` starts the stub in `<name>.cmd.runfiles`, so a debug launch starts there too.
        working_directory = runfiles_directory
    else:
        ctx.actions.symlink(output = executable, target_file = _java_file(ctx, java_runtime), is_executable = True)
        runfiles = ctx.runfiles(
            files = [executable],
            transitive_files = java_runtime.files,
            symlinks = {ctx.label.name + ".jvm.args": argfile},
            root_symlinks = {_JAVA_LAUNCH_HOME + "/" + destination: file for destination, file in _runfiles_home(ctx, placement, dist).items()},
        )

        # The java stub of rules_java gives these two to the IDE.
        environment = dict(ctx.attr.env)
        environment.update({"JAVA_RUNFILES": runfiles_directory, "RUNFILES_DIR": runfiles_directory})
        launch_argfile = "%s/%s/%s.jvm.args" % (runfiles_directory, ctx.workspace_name, ctx.label.name)
        working_directory = "%s/%s" % (runfiles_directory, ctx.workspace_name)
    for target in ctx.attr.data:
        runfiles = runfiles.merge(ctx.runfiles(transitive_files = target[DefaultInfo].files)).merge(target[DefaultInfo].default_runfiles)

    # The run handler of the devkit Bazel plugin reads this file. It starts `java` with the debug options before the
    # argument file.
    launch = ctx.actions.declare_file(ctx.label.name + ".launch.json")
    ctx.actions.write(launch, json.encode_indent({
        "version": 2,
        "java": java,
        "argfile": launch_argfile,
        "runfilesDirectory": runfiles_directory,
        "workingDirectory": working_directory,
        "env": environment,
    }) + "\n")
    return [
        DefaultInfo(executable = executable, files = depset([executable, argfile, launch]), runfiles = runfiles),
        RunEnvironmentInfo(environment = environment),
    ]

intellij_dev_java_launcher = rule(
    doc = """Starts a composed dev distribution with `java` itself, `bazel run //<package>:<name>`.

The executable is a link to the `java` of the Java runtime. Its `args` are `@<name>.jvm.args`, the argument file that
`dev-launch-args jvm-args` writes at build time. The file ends with the main class and the program arguments of the row.
So `bazel run //<package>:<name> -- <argument>` adds a program argument after them. The home is
`<name>.runfiles/ide_home`, which Bazel links from the placement of the components. So no process runs before the JVM,
and a launch writes no file. `bazel run` starts the executable in `<name>.runfiles/_main`, so every path of the
argument file is absolute. A caller adds JVM flags through `JDK_JAVA_OPTIONS`, and `java` reads them before the
argument file. `<name>.launch.json` states the absolute paths of `java`, the argument file and the runfiles tree, the
working directory and the environment. The devkit Bazel plugin reads it to debug the row.

Windows builds no runfiles tree, and a link to `java.exe` does not start. So on Windows the executable is `<name>.cmd`.
It runs the `java.exe` of the Java runtime by its absolute path with `@<name>.jvm.args` and its own arguments. The home
is the directory of `export`. `<name>.launch.json` names the real `java.exe`. Its working directory is
`<name>.cmd.runfiles`, where `bazel run` starts the stub, so a debug launch starts in the same directory.""",
    implementation = _intellij_dev_java_launcher_impl,
    executable = True,
    fragments = ["java"],
    toolchains = ["@bazel_tools//tools/jdk:runtime_toolchain_type"],
    attrs = {
        "dist": attr.label(mandatory = True, providers = [IntellijDevDistInfo], doc = "The local launch of the distribution, the `_dist_launch` target. Its placement names the files of the home."),
        "export": attr.label(providers = [IntellijDevDistInfo], doc = "The self-contained distribution, the `_dist` target. The home on Windows, where it is mandatory."),
        "vm_options": attr.string_dict(mandatory = True, doc = "The destination of the main vmoptions file in the home, `bin/<name>`, for each OS token: `darwin`, `linux` and `windows`. The argument file writer reads the file there."),
        "product_info": attr.string_dict(mandatory = True, doc = "The destination of `product-info.json` in the home, for each OS token. The production place is the root, or `Resources/` for a macOS app."),
        "jvm_flags": attr.string_list(doc = "JVM flags; `$(location)` and make variables expand, and `${BUILD_WORKSPACE_DIRECTORY}` and `${HOME}` resolve at analysis."),
        "add_opens": attr.string_list(doc = "Packages opened to the unnamed module, as `java_binary.add_opens`."),
        "env": attr.string_dict(doc = "Environment variables `bazel run` sets for the IDE."),
        "data": attr.label_list(allow_files = True, doc = "Extra runfiles of the launcher."),
        "program_args": attr.string_list(doc = "The program arguments of the row, which the argument file holds after the main class. Each value is one argument; `$(location)` and make variables expand, and `${BUILD_WORKSPACE_DIRECTORY}` and `${HOME}` resolve at analysis. The first one names a custom command."),
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
        jvm_flags = [],
        env = {},
        program_args = [],
        data = [],
        visibility = None):
    """The `java` launcher of a composed dev distribution.

    `dist` is the `_dist_launch` target of the distribution, and `export` is its `_dist` target. Only Windows builds
    `export`. `vm_options` and `product_info` map each OS token to the destination of the main vmoptions file and of
    `product-info.json` in the home. The config and system directories are `out/dev-data/<name>/config` and
    `out/dev-data/<name>/system`, as absolute paths.
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
        jvm_flags = _runtime_jvm_flags(name, jvm_flags) + [
            # Not a build-time input: `AppMode.getDevIdeaProjectDir` and the webview native bridge read it at runtime,
            # and a dev launch has it only because `DevMainImpl` sets it from the project root it just built against.
            "-Didea.dev.project.root=$${BUILD_WORKSPACE_DIRECTORY}",
        ],
        add_opens = INTELLIJ_ADD_OPENS,
        env = env,
        # `bazel run` starts the executable in `<name>.runfiles/_main`, which holds the argument file. The file holds
        # the program arguments, so a `bazel run` argument comes after them. The Windows stub names the file itself.
        args = select({
            "@platforms//os:windows": [],
            "//conditions:default": ["@%s.jvm.args" % name],
        }),
        program_args = program_args,
        data = data,
    )
