"""Tests for the `directory_name` attribute of `ij_plugin`."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//platform/build-scripts/bazel-rules:ij_plugin.bzl", "ij_plugin")

def _directory_name_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    dist_directory = target_under_test.label.name + "_dist"
    files = target_under_test[DefaultInfo].files.to_list()
    asserts.equals(
        env,
        sorted([dist_directory + "/" + ctx.attr.expected_directory_name, dist_directory + "/file-attributes.txt", dist_directory + "/packed-modules.yaml"]),
        sorted([file.dirname.split("/")[-1] + "/" + file.basename for file in files]),
    )
    return analysistest.end(env)

# Both passing cases are in one package and one configuration.
# So the test also checks that two `ij_plugin` targets of one package do not declare the same output.
_directory_name_test = analysistest.make(
    _directory_name_test_impl,
    attrs = {"expected_directory_name": attr.string(mandatory = True)},
)

def _invalid_directory_name_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, "`directory_name` must be `$auto` or a valid directory name")
    return analysistest.end(env)

_invalid_directory_name_test = analysistest.make(
    _invalid_directory_name_test_impl,
    expect_failure = True,
)

def directory_name_tests(name, descriptor_module):
    """Checks the default and a custom plugin directory name, and rejects a path as the name."""
    cases = [
        ("default", "$auto", "ijPluginPackagerSmoke"),
        ("custom", "custom-directory", "custom-directory"),
    ]
    tests = []
    for case_name, directory_name, expected_directory_name in cases:
        prefix = name + "-" + case_name
        ij_plugin(
            name = prefix + "-plugin",
            descriptor_module = descriptor_module,
            directory_name = directory_name,
            tags = ["manual"],
        )
        _directory_name_test(
            name = prefix + "-test",
            expected_directory_name = expected_directory_name,
            target_under_test = ":" + prefix + "-plugin",
        )
        tests.append(":" + prefix + "-test")

    for case_name, directory_name in [("empty", ""), ("path", "a/b"), ("parent", "..")]:
        prefix = name + "-" + case_name
        ij_plugin(
            name = prefix + "-plugin",
            descriptor_module = descriptor_module,
            directory_name = directory_name,
            tags = ["manual"],
        )
        _invalid_directory_name_test(
            name = prefix + "-test",
            target_under_test = ":" + prefix + "-plugin",
        )
        tests.append(":" + prefix + "-test")

    native.test_suite(
        name = name,
        tests = tests,
    )
