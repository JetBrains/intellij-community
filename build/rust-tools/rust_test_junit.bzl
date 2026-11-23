# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

"""A `rust_test` which writes a JUnit report.

libtest writes no report, so Bazel synthesizes one which holds a single test case for the whole target. The
rule here runs the test binary through `//build/rust-tools/libtest-junit-wrapper` of the community module, which
writes one test case per test to `XML_OUTPUT_FILE`. The `wrapper` argument names another wrapper binary, the
`junit_class` argument gives every test case one class name, and the `optimized` argument builds the test optimized.
"""

load("@rules_rs//rs:rust_test.bzl", "rust_test")

# A `Label` resolves the package in the community module, so a caller of another module gets the same binary.
_WRAPPER = Label("//build/rust-tools/libtest-junit-wrapper")

# Holds the runfiles path of the test binary the wrapper must run.
_TEST_ENV_VAR = "LIBTEST_JUNIT_TEST"

# Holds the one class name of every test case of the report.
_CLASS_ENV_VAR = "LIBTEST_JUNIT_CLASS"

def _rlocation_path(ctx, file):
    """The runfiles path of a file, which is what the runfiles library of the wrapper resolves."""
    if file.short_path.startswith("../"):
        return file.short_path[len("../"):]
    return ctx.workspace_name + "/" + file.short_path

def _rust_test_junit_test_impl(ctx):
    wrapper = ctx.attr.wrapper[DefaultInfo].files_to_run.executable

    # Bazel requires the executable of a target to be an output of that target, so the wrapper is
    # symlinked. The extension of the wrapper carries the `.exe` suffix Windows needs.
    extension = "." + wrapper.extension if wrapper.extension else ""
    executable = ctx.actions.declare_file(ctx.label.name + extension)
    ctx.actions.symlink(output = executable, target_file = wrapper, is_executable = True)

    test = ctx.attr.test
    test_executable = test[DefaultInfo].files_to_run.executable

    environment = {_TEST_ENV_VAR: _rlocation_path(ctx, test_executable)}
    if ctx.attr.junit_class:
        environment[_CLASS_ENV_VAR] = ctx.attr.junit_class
    inherited_environment = []
    if RunEnvironmentInfo in test:
        test_environment = test[RunEnvironmentInfo]
        environment = test_environment.environment | environment
        inherited_environment = test_environment.inherited_environment

    return [
        DefaultInfo(
            executable = executable,
            runfiles = ctx.runfiles([executable, test_executable]).merge_all([
                ctx.attr.wrapper[DefaultInfo].default_runfiles,
                test[DefaultInfo].default_runfiles,
            ]),
        ),
        RunEnvironmentInfo(
            environment = environment,
            inherited_environment = inherited_environment,
        ),
    ]

_PLATFORMS_SETTING = "//command_line_option:platforms"
_EXTRA_RUSTC_FLAGS = str(Label("@rules_rust//rust/settings:extra_rustc_flags"))

# The rustc flags of an `optimized` test, after the flags of the configuration. rustc takes the last `-Copt-level`, so
# `1` replaces the `0` of `fastbuild`. Each crate keeps its debug assertions and its overflow checks, which rustc
# turns off with an `-Copt-level` above `0` unless a flag names them.
OPTIMIZED_TEST_RUSTC_FLAGS = ["-Copt-level=1", "-Cdebug-assertions=on", "-Coverflow-checks=on"]

def without_optimized_test_rustc_flags(flags):
    """`flags` of `@rules_rust//rust/settings:extra_rustc_flags` without the flags of an `optimized` test at their end.

    A transition to a shipped build uses it, so a binary or a library in the data of an `optimized` test builds as it
    does outside the test, and both share one build.
    """
    count = len(OPTIMIZED_TEST_RUSTC_FLAGS)
    if len(flags) >= count and flags[len(flags) - count:] == OPTIMIZED_TEST_RUSTC_FLAGS:
        return flags[:len(flags) - count]
    return flags

def _test_transition_impl(settings, attr):
    extra_rustc_flags = settings[_EXTRA_RUSTC_FLAGS]
    return {
        _PLATFORMS_SETTING: str(attr.platform) if attr.platform else settings[_PLATFORMS_SETTING],
        _EXTRA_RUSTC_FLAGS: extra_rustc_flags + OPTIMIZED_TEST_RUSTC_FLAGS if attr.optimized else extra_rustc_flags,
    }

# The transition is on the test target and not on the `rust_test`, so the wrapper builds for the same platform and
# with the same flags. A wrapper for another platform would build all its dependencies, the cc runtime libraries
# included, a second time.
_test_transition = transition(
    implementation = _test_transition_impl,
    inputs = [_PLATFORMS_SETTING, _EXTRA_RUSTC_FLAGS],
    outputs = [_PLATFORMS_SETTING, _EXTRA_RUSTC_FLAGS],
)

# Bazel asks the class name of a test rule to end with `_test`, so the name of the rule holds the suffix
# and the macro below does not.
_rust_test_junit_test = rule(
    doc = "Runs a `rust_test` through the wrapper which writes the JUnit report.",
    implementation = _rust_test_junit_test_impl,
    test = True,
    cfg = _test_transition,
    attrs = {
        "junit_class": attr.string(
            doc = "The class name of every test case of the report. Empty keeps the module path of each test.",
        ),
        "optimized": attr.bool(
            doc = "Builds the test, the wrapper and their dependencies with `OPTIMIZED_TEST_RUSTC_FLAGS`.",
        ),
        "platform": attr.label(
            doc = "The target platform of the test, the wrapper and their dependencies. `None` keeps the platform.",
        ),
        "test": attr.label(
            doc = "The `rust_test` to run.",
            mandatory = True,
            executable = True,
            cfg = "target",
        ),
        "wrapper": attr.label(
            doc = "The binary which runs the test binary and writes the JUnit report.",
            default = _WRAPPER,
            executable = True,
            cfg = "target",
        ),
    },
)

def rust_test_junit(
        name,
        args = [],
        exec_properties = {},
        flaky = False,
        junit_class = "",
        optimized = False,
        platform = None,
        size = None,
        tags = [],
        target_compatible_with = [],
        timeout = None,
        visibility = None,
        wrapper = _WRAPPER,
        **kwargs):
    """Declares a `rust_test` and the target which runs it through the wrapper.

    The public `name` stays the name of the test target, so a CI configuration and a local run do not
    change. The `rust_test` itself takes the `_libtest` suffix and the `manual` tag, because only the
    wrapper may run it.

    Bazel runs the wrapper, so every attribute which drives the run of a test belongs to the wrapper and
    not to the `rust_test`. `args` is the clearest case: the wrapper gives its own arguments to the test
    binary, and a `size` or a `timeout` on the `rust_test` would reach a target which never runs. `env` and
    `env_inherit` stay with the `rust_test`, because the rule above reads them back through
    `RunEnvironmentInfo`.

    Args:
        name: Name of the test target.
        args: Arguments the wrapper gives to the test binary.
        exec_properties: Execution properties of the test target.
        flaky: Whether Bazel retries the test target.
        junit_class: The class name of every test case of the report, such as the name of the JUnit test that the
            target replaces. Each case is then named by the last segment of its test name, which must be unique, and
            a `--test_filter` of `<class>#<case>` selects the case.
        optimized: Builds the test target, the wrapper, the `rust_test` and their dependencies with
            `OPTIMIZED_TEST_RUSTC_FLAGS`. The `rust_test` itself, such as the one that a clippy test names, keeps the
            flags of its configuration.
        platform: The target platform of the test target, the wrapper and the `rust_test`. `None` keeps the
            platform of the configuration.
        size: Size of the test target.
        tags: Tags of both targets. See https://bazel.build/reference/be/common-definitions#common.tags
        target_compatible_with: Constraints of both targets.
        timeout: Timeout of the test target.
        visibility: Visibility of the test target.
        wrapper: The binary which runs the test binary and writes the JUnit report.
        **kwargs: Every other argument of `rust_test`.
    """
    test_name = name + "_libtest"
    rust_test(
        name = test_name,
        tags = tags + ["manual"],
        target_compatible_with = target_compatible_with,
        **kwargs
    )
    _rust_test_junit_test(
        name = name,
        test = test_name,
        args = args,
        exec_properties = exec_properties,
        flaky = flaky,
        junit_class = junit_class,
        optimized = optimized,
        platform = platform,
        size = size,
        tags = tags,
        target_compatible_with = target_compatible_with,
        timeout = timeout,
        visibility = visibility,
        wrapper = wrapper,
    )
