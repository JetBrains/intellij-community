"""Analysis tests of `context_plugin_dir`: the outputs and the manifest of a pool with directory trees, and its refusals."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":context_plugin_dir.bzl", "ContextPluginInfo", "context_plugin_dir")
load(":intellij_dev_dist.bzl", "DevDistPlacementInfo", "IntellijDevFragmentInfo")

_MODULE = "test.context.plugin"

def _fake_component_impl(ctx):
    """A plugin component that places one empty file per entry of `files` and one directory per entry of `trees`."""
    outputs = []
    files = {}
    for index, destination in enumerate(ctx.attr.files):
        file = ctx.actions.declare_file("%s/file-%d" % (ctx.label.name, index))
        ctx.actions.write(file, "")
        files[destination] = file
        outputs.append(file)
    trees = {}
    for index, destination in enumerate(ctx.attr.trees):
        tree = ctx.actions.declare_directory("%s/tree-%d" % (ctx.label.name, index))
        ctx.actions.run_shell(outputs = [tree], command = 'touch "$1/entry.txt"', arguments = [tree.path])
        trees[destination] = tree
        outputs.append(tree)
    manifest = ctx.actions.declare_file(ctx.label.name + ".manifest")
    ctx.actions.write(manifest, "")
    return [
        DefaultInfo(files = depset(outputs)),
        DevDistPlacementInfo(files = files, trees = trees, executables = ctx.attr.executables, executable_copies = {}),
        IntellijDevFragmentInfo(name = _MODULE, manifest = manifest, payload = None, plugin_classpath_part = None, plugin_classpath_prefix = None),
    ]

_fake_component = rule(
    implementation = _fake_component_impl,
    attrs = {
        "executables": attr.string_list(),
        "files": attr.string_list(),
        "trees": attr.string_list(),
    },
)

def _tree_pool_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    name = target.label.name
    info = target[ContextPluginInfo]
    asserts.equals(env, ["fake/lib/fake.jar"], sorted(info.files))
    asserts.equals(env, ["fake/native/mac", "fake/resources/apks"], sorted(info.trees))
    for destination, tree in info.trees.items():
        asserts.true(env, tree.is_directory, "The tree %s must be a directory output." % destination)
        asserts.true(env, tree.short_path.endswith("/%s/%s" % (name, destination)), tree.short_path)
    asserts.equals(env, {_MODULE: "fake"}, info.directories)

    outputs = info.files.values() + info.trees.values()
    asserts.equals(env, sorted([file.short_path for file in outputs]), sorted([file.short_path for file in target[DefaultInfo].files.to_list()]))
    runfiles = target[DefaultInfo].default_runfiles.files.to_list()
    for file in outputs + [info.manifest, info.modules]:
        asserts.true(env, file in runfiles, "%s must be a runfile." % file.short_path)

    writes = {
        output.basename: action.content
        for action in analysistest.target_actions(env)
        if action.mnemonic == "FileWrite"
        for output in action.outputs.to_list()
    }
    asserts.equals(
        env,
        "".join([
            "fake/lib/fake.jar\t%s/fake/lib/fake.jar\n" % name,
            "fake/native/mac/\t%s/fake/native/mac/\n" % name,
            "fake/resources/apks/\t%s/fake/resources/apks/\n" % name,
        ]),
        writes[info.manifest.basename],
    )
    asserts.equals(env, "%s\tfake\n" % _MODULE, writes[info.modules.basename])
    asserts.equals(
        env,
        [],
        [action.mnemonic for action in analysistest.target_actions(env) if action.mnemonic not in ["Symlink", "FileWrite"]],
        "The pool must only link the placed files and directory trees.",
    )
    return analysistest.end(env)

_tree_pool_test = analysistest.make(_tree_pool_test_impl)

def _refused_pool_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.message)
    return analysistest.end(env)

_refused_pool_test = analysistest.make(
    _refused_pool_test_impl,
    attrs = {"message": attr.string(mandatory = True)},
    expect_failure = True,
)

def context_plugin_dir_test_suite(name):
    """Declares the analysis tests of `context_plugin_dir`, each against a pool of its own.

    Args:
        name: the name of the test suite.
    """
    cases = {
        "trees": struct(
            files = ["plugins/fake/lib/fake.jar"],
            trees = ["plugins/fake/resources/apks", "plugins/fake/native/mac"],
            executables = [],
            message = None,
        ),
        "executable": struct(
            files = ["plugins/fake/bin/tool"],
            trees = [],
            executables = ["plugins/fake/bin/tool"],
            message = "a context plugin holds no executable",
        ),
        "file_below_tree": struct(
            files = ["plugins/fake/resources/apks/extra.apk"],
            trees = ["plugins/fake/resources/apks"],
            executables = [],
            message = "places 'plugins/fake/resources/apks/extra.apk' below the directory tree 'plugins/fake/resources/apks'",
        ),
        "whole_plugin_directory_tree": struct(
            files = [],
            trees = ["plugins/fake"],
            executables = [],
            message = "places 'plugins/fake' as a whole",
        ),
        "tree_in_second_directory": struct(
            files = ["plugins/fake/lib/fake.jar"],
            trees = ["plugins/other/resources"],
            executables = [],
            message = "a context plugin is one directory",
        ),
    }
    tests = []
    for case, spec in cases.items():
        component = "%s_%s_component" % (name, case)
        _fake_component(
            name = component,
            files = spec.files,
            trees = spec.trees,
            executables = spec.executables,
            tags = ["manual"],
        )
        pool = "%s_%s" % (name, case)
        context_plugin_dir(
            name = pool,
            plugins = [":" + component],
            stamp_class = "monolith",
            tags = ["manual"],
        )
        test = pool + "_test"
        if spec.message == None:
            _tree_pool_test(name = test, size = "small", target_under_test = ":" + pool)
        else:
            _refused_pool_test(name = test, size = "small", target_under_test = ":" + pool, message = spec.message)
        tests.append(":" + test)
    native.test_suite(name = name, tests = tests)
