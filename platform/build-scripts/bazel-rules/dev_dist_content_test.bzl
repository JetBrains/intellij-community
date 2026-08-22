"""Analysis tests for the prepacked plugin-content provider boundary."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":dev_dist_content.bzl", "DevDistContentInfo", "dev_dist_content_set", "dev_dist_plugin_content")
load(":intellij_dev_dist.bzl", "intellij_dev_build_inputs")

def _fake_module_impl(ctx):
    module_jar = ctx.actions.declare_file(ctx.label.name + ".jar")
    ctx.actions.write(module_jar, ctx.attr.module_name)

    providers = [
        DefaultInfo(files = depset([module_jar])),
        _KtJvmInfo(
            all_output_jars = [module_jar],
            module_name = ctx.attr.module_name,
        ),
    ]

    content_jars = []
    for index in range(ctx.attr.content_output_count):
        jar = ctx.actions.declare_file("%s-content-%d.jar" % (ctx.label.name, index))
        ctx.actions.write(jar, "%s:%d" % (ctx.attr.module_name, index))
        content_jars.append(jar)
    if ctx.attr.has_content_output_group:
        providers.append(OutputGroupInfo(content_module_jar = depset(content_jars)))
    else:
        providers.append(OutputGroupInfo(other = depset()))
    return providers

_fake_module = rule(
    implementation = _fake_module_impl,
    attrs = {
        "content_output_count": attr.int(default = 0),
        "has_content_output_group": attr.bool(default = True),
        "module_name": attr.string(mandatory = True),
    },
)

def _expected_failure_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.expected_message)
    return analysistest.end(env)

_expected_failure_test = analysistest.make(
    _expected_failure_test_impl,
    expect_failure = True,
    attrs = {
        "expected_message": attr.string(mandatory = True),
    },
)

def _composed_provider_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    records = target[DevDistContentInfo].prepacked_plugin_jars.to_list()
    asserts.equals(env, 1, len(records))
    record = records[0]
    asserts.equals(env, "test.plugin", record.plugin_main_module)
    asserts.equals(env, "test.content", record.content_module)
    asserts.equals(env, "modules/test.content.jar", record.relative_output_file)
    return analysistest.end(env)

_composed_provider_test = analysistest.make(_composed_provider_test_impl)

def _completion_provider_test_impl(ctx):
    """A set that completes a cross-repository plugin produces the same record a plugin-content target would.

    The whole point of the completion is that `descriptor_module` is unnameable from the package that has to declare
    these members, so the plugin is named as a string instead - and nothing downstream may be able to tell.
    """
    env = analysistest.begin(ctx)
    records = analysistest.target_under_test(env)[DevDistContentInfo].prepacked_plugin_jars.to_list()
    asserts.equals(env, 1, len(records))
    asserts.equals(env, "test.plugin", records[0].plugin_main_module)
    asserts.equals(env, "test.content", records[0].content_module)
    asserts.equals(env, "modules/test.content.jar", records[0].relative_output_file)
    return analysistest.end(env)

_completion_provider_test = analysistest.make(_completion_provider_test_impl)

def dev_dist_content_test_suite(name):
    _fake_module(
        name = name + "_descriptor",
        module_name = "test.plugin",
    )
    _fake_module(
        name = name + "_content",
        content_output_count = 1,
        module_name = "test.content",
    )
    _fake_module(
        name = name + "_content_same_name",
        content_output_count = 1,
        module_name = "test.content",
    )
    _fake_module(
        name = name + "_missing_output_group",
        has_content_output_group = False,
        module_name = "test.missing",
    )
    _fake_module(
        name = name + "_multiple_outputs",
        content_output_count = 2,
        module_name = "test.multiple",
    )

    dev_dist_plugin_content(
        name = name + "_missing_output_group_content",
        descriptor_module = name + "_descriptor",
        prepacked_content_modules = {
            name + "_missing_output_group": "modules/test.missing.jar",
        },
    )
    _expected_failure_test(
        name = name + "_missing_output_group_test",
        expected_message = "has no `content_module_jar` output",
        target_under_test = name + "_missing_output_group_content",
    )

    dev_dist_plugin_content(
        name = name + "_multiple_outputs_content",
        descriptor_module = name + "_descriptor",
        prepacked_content_modules = {
            name + "_multiple_outputs": "modules/test.multiple.jar",
        },
    )
    _expected_failure_test(
        name = name + "_multiple_outputs_test",
        expected_message = "must have exactly one `content_module_jar` output",
        target_under_test = name + "_multiple_outputs_content",
    )

    dev_dist_plugin_content(
        name = name + "_plugin_content",
        descriptor_module = name + "_descriptor",
        prepacked_content_modules = {
            name + "_content": "modules/test.content.jar",
        },
    )
    dev_dist_plugin_content(
        name = name + "_same_plugin_content",
        descriptor_module = name + "_descriptor",
        prepacked_content_modules = {
            name + "_content": "modules/test.content.jar",
        },
    )
    dev_dist_content_set(
        name = name + "_composed_content",
        deps = [
            name + "_plugin_content",
            name + "_same_plugin_content",
        ],
    )
    _composed_provider_test(
        name = name + "_composed_provider_test",
        target_under_test = name + "_composed_content",
    )

    dev_dist_plugin_content(
        name = name + "_conflicting_plugin_content",
        descriptor_module = name + "_descriptor",
        prepacked_content_modules = {
            name + "_content_same_name": "modules/test.content.jar",
        },
    )
    dev_dist_content_set(
        name = name + "_conflicting_content",
        deps = [
            name + "_plugin_content",
            name + "_conflicting_plugin_content",
        ],
    )
    intellij_dev_build_inputs(
        name = name + "_conflicting_inputs",
        content = name + "_conflicting_content",
    )
    _expected_failure_test(
        name = name + "_conflicting_relation_test",
        expected_message = "is provided by conflicting records",
        target_under_test = name + "_conflicting_inputs",
    )

    dev_dist_content_set(
        name = name + "_completion_content",
        prepacked_content_modules = {
            name + "_content": "modules/test.content.jar",
        },
        prepacked_plugin_main_module = "test.plugin",
    )
    _completion_provider_test(
        name = name + "_completion_provider_test",
        target_under_test = name + "_completion_content",
    )

    # A relation with no plugin has no key, so the two attributes are required together rather than defaulted.
    dev_dist_content_set(
        name = name + "_unnamed_completion_content",
        prepacked_content_modules = {
            name + "_content": "modules/test.content.jar",
        },
    )
    _expected_failure_test(
        name = name + "_unnamed_completion_test",
        expected_message = "must be set together",
        target_under_test = name + "_unnamed_completion_content",
    )

    native.test_suite(
        name = name,
        tests = [
            name + "_missing_output_group_test",
            name + "_multiple_outputs_test",
            name + "_composed_provider_test",
            name + "_conflicting_relation_test",
            name + "_completion_provider_test",
            name + "_unnamed_completion_test",
        ],
    )
