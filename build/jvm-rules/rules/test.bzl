load("//:rules/common-attrs.bzl", "add_dicts", "common_attr", "common_outputs", "common_toolchains")
load("//:rules/impl/compile.bzl", "kt_jvm_produce_jar_actions")

visibility("private")

_runnable_implicit_deps = {
    "_java_runtime": attr.label(
        default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
    ),
}

_runnable_common_attr = add_dicts(common_attr, _runnable_implicit_deps, {
    "jvm_flags": attr.string_list(
        doc = """A list of flags to embed in the wrapper script generated for running this binary. Note: does not yet
        support make variable substitution.""",
        default = [],
    ),
})

_SPLIT_STRINGS = [
    "src/test/java/",
    "src/test/kotlin/",
    "javatests/",
    "kotlin/",
    "java/",
    "test/",
]

def _is_absolute(path):
    return path.startswith("/") or (len(path) > 2 and path[1] == ":")

def _write_launcher_action(ctx, rjars, main_class, jvm_flags):
    """Macro that writes out a launcher script shell script.
      Args:
        rjars: All of the runtime jars required to launch this java target.
        main_class: the main class to launch.
        jvm_flags: The flags that should be passed to the jvm.
        args: Args that should be passed to the Binary.
    """
    jvm_flags = " ".join([ctx.expand_location(f, ctx.attr.data) for f in jvm_flags])
    template = ctx.attr._java_stub_template.files.to_list()[0]

    java_runtime = ctx.toolchains["@bazel_tools//tools/jdk:runtime_toolchain_type"].java_runtime
    java_bin_path = java_runtime.java_executable_exec_path

    # Following https://github.com/bazelbuild/bazel/blob/6d5b084025a26f2f6d5041f7a9e8d302c590bc80/src/main/starlark/builtins_bzl/bazel/java/bazel_java_binary.bzl#L66-L67
    # Enable the security manager past deprecation.
    # On bazel 6, this check isn't possible...
    if getattr(java_runtime, "version", 0) >= 17:
        jvm_flags = jvm_flags + " -Djava.security.manager=allow"

    classpath = ctx.configuration.host_path_separator.join(
        ["${RUNPATH}%s" % (j.short_path) for j in rjars.to_list()],
    )

    ctx.actions.expand_template(
        template = template,
        output = ctx.outputs.executable,
        substitutions = {
            "%classpath%": classpath,
            "%runfiles_manifest_only%": "",
            "%java_start_class%": main_class,
            "%javabin%": "JAVABIN=" + java_bin_path,
            "%jvm_flags%": jvm_flags,
            "%set_jacoco_metadata%": "",
            "%set_jacoco_main_class%": "",
            "%set_jacoco_java_runfiles_root%": "",
            "%set_java_coverage_new_implementation%": """export JAVA_COVERAGE_NEW_IMPLEMENTATION=NO""",
            "%workspace_prefix%": ctx.workspace_name + "/",
            "%test_runtime_classpath_file%": "export TEST_RUNTIME_CLASSPATH_FILE=${JAVA_RUNFILES}",
            "%needs_runfiles%": "0" if _is_absolute(java_bin_path) else "1",
        },
        is_executable = True,
    )
    return []

def _jvm_test(ctx):
    providers = kt_jvm_produce_jar_actions(ctx)
    runtime_jars = depset(ctx.files._bazel_test_runner, transitive = [providers.java.transitive_runtime_jars])

    #     coverage_runfiles = []
    #     if ctx.configuration.coverage_enabled:
    #         jacocorunner = ctx.toolchains[_TOOLCHAIN_TYPE].jacocorunner
    #         coverage_runfiles = jacocorunner.files.to_list()

    test_class = ctx.attr.test_class

    # If no test_class, do a best-effort attempt to infer one.
    if not bool(ctx.attr.test_class):
        for file in ctx.files.srcs:
            package_relative_path = file.path.replace(ctx.label.package + "/", "")
            if package_relative_path.split(".")[0] == ctx.attr.name:
                for splitter in _SPLIT_STRINGS:
                    elements = file.short_path.split(splitter, 1)
                    if len(elements) == 2:
                        test_class = elements[1].split(".")[0].replace("/", ".")
                        break

    jvm_flags = []
    if hasattr(ctx.fragments.java, "default_jvm_opts"):
        jvm_flags = ctx.fragments.java.default_jvm_opts

    jvm_flags.extend(ctx.attr.jvm_flags)
    coverage_metadata = _write_launcher_action(
        ctx,
        runtime_jars,
        main_class = ctx.attr.main_class,
        jvm_flags = [
            "-ea",
            "-Dbazel.test_suite=%s" % test_class,
        ] + jvm_flags,
    )

    # adds common test variables, including TEST_WORKSPACE
    files = [ctx.outputs.jar]
    return [
        providers.java,
        providers.kt,
        DefaultInfo(
            files = depset(files),
            runfiles = ctx.runfiles(
                # explicitly include data files, otherwise they appear to be missing
                files = ctx.files.data,
                transitive_files = depset(
                    order = "default",
                    transitive = [runtime_jars, depset(coverage_metadata)],
                    direct = ctx.files._java_runtime,
                ),
                # continue to use collect_default until proper transitive data collecting is implemented.
                collect_default = True,
            ),
        ),
    ] + [testing.TestEnvironment(environment = ctx.attr.env)]

jvm_test = rule(
    doc = """\
Setup a simple kotlin_test.

**Notes:**
* The kotlin test library is not added implicitly, it is available with the label
`@rules_kotlin//kotlin/compiler:kotlin-test`.
""",
    attrs = add_dicts(_runnable_common_attr, {
        "srcs": attr.label_list(
            doc = """The list of source files that are processed to create the target, this can contain both Java and Kotlin
              files. Java analysis occurs first so Kotlin classes may depend on Java classes in the same compilation unit.""",
            default = [],
            allow_files = [".kt", ".java"],
        ),
        "_bazel_test_runner": attr.label(
            default = Label("@bazel_tools//tools/jdk:TestRunner_deploy.jar"),
            allow_files = True,
        ),
        "test_class": attr.string(
            doc = "The Java class to be loaded by the test runner.",
            default = "",
        ),
        "main_class": attr.string(default = "com.google.testing.junit.runner.BazelTestRunner"),
        "env": attr.string_dict(
            doc = "Specifies additional environment variables to set when the target is executed by bazel test.",
            default = {},
        ),
        "_lcov_merger": attr.label(
            default = Label("@bazel_tools//tools/test/CoverageOutputGenerator/java/com/google/devtools/coverageoutputgenerator:Main"),
        ),
        "_java_stub_template": attr.label(
            cfg = "exec",
            default = Label("@bazel_tools//tools/java:java_stub_template.txt"),
            allow_single_file = True,
        ),
    }),
    executable = True,
    outputs = common_outputs,
    test = True,
    toolchains = common_toolchains + ["@bazel_tools//tools/jdk:runtime_toolchain_type"],
    implementation = _jvm_test,
    fragments = ["java"],  # Required fragments of the target configuration
    host_fragments = ["java"],  # Required fragments of the host configuration
)
