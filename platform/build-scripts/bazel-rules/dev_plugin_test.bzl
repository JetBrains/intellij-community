"""Analysis tests for the directly packed plugin component of `dev_plugin.bzl`."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts", "unittest")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "content_module_jar", "content_module_jar_target_name")
load(":dev_dist_content.bzl", "DevDistContentInfo")
load(":dev_dist_plugin.bzl", "dev_dist_plugin", "dev_dist_plugin_component_target_name")
load(":dev_dist_plugin_descriptor.bzl", "dev_dist_plugin_descriptor", "dev_dist_plugin_descriptor_target_name", "dev_dist_product_info")
load(":dev_plugin.bzl", "dev_plugin")
load(":intellij_dev_dist.bzl", "IntellijDevFragmentInfo")

# The suite's package and name are fixed: a test rule's configuration names the product info target by label.
_PACKAGE = "//platform/build-scripts/bazel-rules/dev-plugin-tests"
_SUITE = "dev_plugin_tests"
_PRODUCT_INFO = str(Label(_PACKAGE + ":" + _SUITE + "_product_info"))
_NO_PREFIX_PRODUCT_INFO = str(Label(_PACKAGE + ":" + _SUITE + "_no_prefix_product_info"))
_FRONTEND_PRODUCT_INFO = str(Label(_PACKAGE + ":" + _SUITE + "_frontend_product_info"))
_PRODUCT_INFO_FLAG = str(Label("//build:dev_dist_product_info"))
_TRACE_SPANS = str(Label("//platform/build-scripts/bazel-rules:trace_spans"))
_EMPTY_JAR = "PK\005\006" + ("\000" * 18)
_MAIN_MODULE = "test.dev.plugin"
_SPLIT_MODULE = "test.dev.split"
_MEMBER_MODULE = "test.dev.member"
_MODE_MODULE = "test.dev.mode"

def _fixture_module_impl(ctx):
    jar = ctx.outputs.jar
    ctx.actions.write(jar, _EMPTY_JAR)
    return [
        DefaultInfo(files = depset([jar])),
        _KtJvmInfo(all_output_jars = [jar], module_name = ctx.attr.module_name),
    ]

_fixture_module = rule(
    implementation = _fixture_module_impl,
    attrs = {"module_name": attr.string(mandatory = True)},
    outputs = {"jar": "%{name}.jar"},
)

def _fixture_jar_impl(ctx):
    ctx.actions.write(ctx.outputs.jar, _EMPTY_JAR)
    return [DefaultInfo(files = depset([ctx.outputs.jar]))]

_fixture_jar = rule(implementation = _fixture_jar_impl, outputs = {"jar": "%{name}.jar"})

def _fixture_library_impl(ctx):
    jars = ctx.files.jars
    return [
        DefaultInfo(files = depset(jars)),
        java_common.merge([JavaInfo(output_jar = jar, compile_jar = jar) for jar in jars]),
    ]

_fixture_library = rule(
    implementation = _fixture_library_impl,
    attrs = {"jars": attr.label_list(allow_files = [".jar"])},
)

def _fixture_xml_impl(ctx):
    ctx.actions.write(ctx.outputs.xml, "<idea-plugin/>\n")
    return [DefaultInfo(files = depset([ctx.outputs.xml]))]

_fixture_xml = rule(implementation = _fixture_xml_impl, outputs = {"xml": "%{name}.xml"})

def _fixture_script_impl(ctx):
    ctx.actions.write(ctx.outputs.script, "#!/bin/sh\n")
    return [DefaultInfo(files = depset([ctx.outputs.script]))]

_fixture_script = rule(implementation = _fixture_script_impl, outputs = {"script": "%{name}.sh"})

def _fixture_resources_impl(ctx):
    """Two files below `<name>.source-root`, the directory a copy takes, and one outside it."""
    first = ctx.actions.declare_file(ctx.label.name + ".source-root/first.txt")
    second = ctx.actions.declare_file(ctx.label.name + ".source-root/nested/second.txt")
    outside = ctx.actions.declare_file(ctx.label.name + ".outside.txt")
    for output in [first, second, outside]:
        ctx.actions.write(output, output.basename + "\n")
    return [DefaultInfo(files = depset([first, second, outside]))]

_fixture_resources = rule(implementation = _fixture_resources_impl)

def _with_short_path(argument, inputs):
    """A `library=` or `module=` flag with the file's short path, so two configurations of one jar compare equal."""
    if not argument.startswith("library=") and not argument.startswith("module="):
        return argument
    key, _, path = argument.partition("=")
    for file in inputs:
        if file.path == path:
            return key + "=" + file.short_path
    return argument

def _pack_action(actions, basename):
    matches = [action for action in actions if [file for file in action.outputs.to_list() if file.basename == basename]]
    if len(matches) != 1:
        fail("expected one action that writes %s, got %d" % (basename, len(matches)))
    return matches[0]

def _dev_plugin_test_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    fragment = target[IntellijDevFragmentInfo]
    groups = target[OutputGroupInfo]
    actions = analysistest.target_actions(env)
    packs = [action for action in actions if action.mnemonic == "PackDevPluginJar"]
    collects = [action for action in actions if action.mnemonic == "CollectDevPluginComponent"]
    asserts.equals(env, 2, len(packs))
    asserts.equals(env, 1, len(collects))

    # The component is neutral and states the product's platform prefix.
    collect = collects[0]
    asserts.true(env, "--platform-neutral" in collect.argv)
    asserts.true(env, "--platform-prefix=idea" in collect.argv)
    asserts.true(env, "--kind=" + _MAIN_MODULE in collect.argv)
    asserts.equals(env, [], [argument for argument in collect.argv if argument.startswith("--os=") or argument.startswith("--arch=")])
    asserts.equals(env, _MAIN_MODULE, fragment.name)
    asserts.equals(env, None, fragment.home)
    asserts.equals(env, [fragment.plugin_classpath_part], groups.dev_dist_plugin_classpath.to_list())
    asserts.equals(env, 3, len(groups.file_metadata.to_list()))
    asserts.equals(env, 3 if ctx.attr.spans else 0, len(groups.trace_spans.to_list()))

    # The payload holds the two packed jars, the reused content module jar, the copied script and the two files of the
    # copied directory. The file outside the prefix is not copied.
    # Files are compared by short path: the test's own attributes are configured with the test, and the component's
    # inputs with the reset, so the same jar arrives as two `File` objects.
    payload = fragment.payload.to_list()
    payload_paths = [file.short_path for file in payload]
    content = ctx.attr.content_jar[ContentModuleJarInfo]
    asserts.equals(env, 6, len(payload))
    asserts.true(env, content.jar.short_path in payload_paths)
    helper = ctx.file.helper
    asserts.true(env, helper.short_path in payload_paths)
    resources = {file.basename: file for file in ctx.attr.resources[DefaultInfo].files.to_list()}
    asserts.true(env, resources["first.txt"].short_path in payload_paths)
    asserts.true(env, resources["second.txt"].short_path in payload_paths)
    asserts.false(env, resources[ctx.attr.resources.label.name + ".outside.txt"].short_path in payload_paths)
    for file in payload:
        asserts.true(env, file in collect.inputs.to_list(), file.path)

    # The main jar's flag file: the patch, the modules in order, then the library, as `JarPackager` orders the jar.
    main = _pack_action(packs, "dev-plugin.jar")
    module_jars = [module[_KtJvmInfo].all_output_jars[0] for module in ctx.attr.modules]
    library_jars = ctx.attr.library[JavaInfo].transitive_runtime_jars.to_list()
    main_argv = [_with_short_path(argument, main.inputs.to_list()) for argument in main.argv[1:]]
    asserts.true(env, main_argv[0].startswith("output=") and main_argv[0].endswith("/lib/dev-plugin.jar"), main_argv[0])
    asserts.true(env, main_argv[1].startswith("metadata-file=") and main_argv[1].endswith("/lib/dev-plugin.jar.json"), main_argv[1])
    rest = main_argv[2:]
    if ctx.attr.spans:
        asserts.true(env, rest[0].startswith("trace-file=") and rest[0].endswith("/lib/dev-plugin.spans.json"), rest[0])
        rest = rest[1:]
    asserts.equals(env, "merge-entities=true", rest[0])
    asserts.true(env, rest[1].startswith("patch=META-INF/plugin.xml=") and rest[1].endswith(_MAIN_MODULE + ".plugin.xml"), rest[1])
    rest = rest[2:]
    expected = ["module=" + jar.short_path for jar in module_jars] + ["library=" + jar.short_path for jar in library_jars]
    asserts.equals(env, expected, rest)

    # A jar file token is one archive: the jar takes that file and nothing else.
    single = _pack_action(packs, "foo.jar")
    single_libraries = [argument for argument in single.argv if argument.startswith("library=")]
    asserts.equals(env, 1, len(single_libraries))
    asserts.true(env, single_libraries[0].endswith("/foo-1.2.3.jar"), single_libraries[0])
    asserts.equals(env, [], [argument for argument in single.argv if argument.startswith("module=") or argument.startswith("patch=")])

    # Every compiled input comes from the neutral configuration, not from the component's product configuration. Only
    # the descriptor is product specific. The roots are compared, and not tested for `-ST-`: under an analysis test the
    # neutral configuration still carries the analysis-test marker and therefore a hash of its own.
    component_root = fragment.manifest.root.path
    for action in packs:
        for file in action.inputs.to_list():
            if file.basename.endswith(".plugin.xml"):
                asserts.equals(env, component_root, file.root.path, file.path)
            elif not file.is_source:
                asserts.false(env, component_root == file.root.path, file.path)
    reused = [jar for jar in payload if jar.short_path == content.jar.short_path]
    asserts.equals(env, 1, len(reused))
    asserts.false(env, component_root == reused[0].root.path, reused[0].path)
    copied_helper = [file for file in payload if file.short_path == helper.short_path]
    asserts.equals(env, 1, len(copied_helper))
    asserts.false(env, component_root == copied_helper[0].root.path, copied_helper[0].path)

    # The collector spec names every jar with its destination and the ready classpath descriptor. The jars come in
    # `classpath_jars` order, which puts the reused jar first here.
    specs = [action for action in actions if [file for file in action.outputs.to_list() if file.basename.endswith(".packed.json")]]
    asserts.equals(env, 1, len(specs))
    spec = json.decode(specs[0].content)
    asserts.equals(env, 1, spec["version"])
    asserts.equals(env, "plugins/dev-plugin", spec["pluginDirectory"])
    asserts.true(env, spec["descriptor"].endswith(_MAIN_MODULE + ".plugin.classpath.xml"), spec["descriptor"])
    asserts.equals(env, ["lib/member.jar", "lib/dev-plugin.jar", "lib/foo.jar"], [jar["destination"] for jar in spec["jars"]])
    asserts.equals(env, reused[0].path, spec["jars"][0]["source"])

    # The metadata is compared by its repository-relative tail. From the ultimate root the community rules are an
    # external repository: its `short_path` starts with `../community+/`, while `path` holds `external/community+/`.
    metadata_tail = content.metadata.short_path.removeprefix("../")
    asserts.true(env, spec["jars"][0]["metadata"].endswith("/" + metadata_tail), spec["jars"][0]["metadata"])

    # The copies come sorted by destination. The directory copy expands to one record per file below the prefix, and
    # only the single file `executable_files` names is executable. No action copies a byte: the source is the file itself.
    asserts.equals(env, ["bin/helper.sh", "helpers/first.txt", "helpers/nested/second.txt"], [copy["destination"] for copy in spec["files"]])
    asserts.equals(env, [True, False, False], [copy["executable"] for copy in spec["files"]])
    asserts.equals(env, copied_helper[0].path, spec["files"][0]["source"])
    asserts.true(env, spec["files"][1]["source"].endswith("/" + resources["first.txt"].short_path.removeprefix("../")), spec["files"][1]["source"])
    asserts.true(env, spec["files"][2]["source"].endswith("/" + resources["second.txt"].short_path.removeprefix("../")), spec["files"][2]["source"])
    asserts.equals(env, [], [action for action in actions if action.mnemonic not in ["PackDevPluginJar", "CollectDevPluginComponent", "FileWrite"]])

    # The raw content: every merged module jar, the members of the reused content module jar, and every library the
    # plugin names, the jar file token included. Neutral, like the packed inputs, so compared by short path.
    raw_content = target[DevDistContentInfo]
    asserts.equals(
        env,
        sorted([jar.short_path for jar in module_jars] + [jar.short_path for jar in content.member_jars]),
        sorted([jar.short_path for jar in raw_content.module_jars.to_list()]),
    )
    raw_libraries = {entry.label: [jar.short_path for jar in entry.jars] for entry in raw_content.library_jars.to_list()}
    asserts.equals(env, sorted([str(ctx.attr.library.label), str(ctx.attr.single_jar.label)]), sorted(raw_libraries.keys()))
    asserts.equals(env, [jar.short_path for jar in library_jars], raw_libraries[str(ctx.attr.library.label)])
    asserts.equals(env, [ctx.file.single_jar.short_path], raw_libraries[str(ctx.attr.single_jar.label)])
    for file in raw_content.module_jars.to_list():
        asserts.false(env, component_root == file.root.path, file.path)
    return analysistest.end(env)

_DEV_PLUGIN_ATTRS = {
    "content_jar": attr.label(mandatory = True, providers = [ContentModuleJarInfo]),
    "library": attr.label(mandatory = True, providers = [JavaInfo]),
    "modules": attr.label_list(mandatory = True, providers = [_KtJvmInfo]),
    "helper": attr.label(mandatory = True, allow_single_file = True),
    "resources": attr.label(mandatory = True),
    "single_jar": attr.label(mandatory = True, allow_single_file = [".jar"]),
    "spans": attr.bool(),
}

# The product flag alone, so the reset of the inputs target lands in the default configuration. A flag set to its
# default value explicitly is not the same configuration as one that leaves it out.
_dev_plugin_test = analysistest.make(
    _dev_plugin_test_impl,
    attrs = _DEV_PLUGIN_ATTRS,
    config_settings = {_PRODUCT_INFO_FLAG: _PRODUCT_INFO},
)

_dev_plugin_spans_test = analysistest.make(
    _dev_plugin_test_impl,
    attrs = _DEV_PLUGIN_ATTRS,
    config_settings = {_PRODUCT_INFO_FLAG: _PRODUCT_INFO, _TRACE_SPANS: True},
)

def _mode_test_impl(ctx):
    """Under a frontend product the leaf refuses the modules of that mode, and the shared packaging ships none of them.

    A refused module leaves every jar, and a jar that merges no module any more goes. A refused reused jar goes too. A
    packaging a product states for itself keeps everything `jars` names.
    """
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    packed = sorted([file.basename for action in actions if action.mnemonic == "PackDevPluginJar" for file in action.outputs.to_list() if file.basename.endswith(".jar")])
    specs = [action for action in actions if [file for file in action.outputs.to_list() if file.basename.endswith(".packed.json")]]
    asserts.equals(env, 1, len(specs))
    destinations = sorted([jar["destination"] for jar in json.decode(specs[0].content)["jars"]])
    asserts.equals(env, sorted(ctx.attr.expected_packed), packed)
    asserts.equals(env, sorted(ctx.attr.expected_destinations), destinations)
    return analysistest.end(env)

_mode_test = analysistest.make(
    _mode_test_impl,
    attrs = {
        "expected_packed": attr.string_list(mandatory = True),
        "expected_destinations": attr.string_list(mandatory = True),
    },
    config_settings = {_PRODUCT_INFO_FLAG: _FRONTEND_PRODUCT_INFO},
)

def _expected_failure_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.expected_message)
    return analysistest.end(env)

_FAILURE_ATTRS = {"expected_message": attr.string(mandatory = True)}

_failure_test = analysistest.make(
    _expected_failure_test_impl,
    expect_failure = True,
    attrs = _FAILURE_ATTRS,
    config_settings = {_PRODUCT_INFO_FLAG: _PRODUCT_INFO},
)

_no_prefix_failure_test = analysistest.make(
    _expected_failure_test_impl,
    expect_failure = True,
    attrs = _FAILURE_ATTRS,
    config_settings = {_PRODUCT_INFO_FLAG: _NO_PREFIX_PRODUCT_INFO},
)

def _declaration_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(env, json.decode(ctx.attr.expected), json.decode(ctx.attr.actual))
    return unittest.end(env)

_declaration_test = unittest.make(
    _declaration_test_impl,
    attrs = {
        "actual": attr.string(mandatory = True),
        "expected": attr.string(mandatory = True),
    },
)

def _stale_macro_test(name):
    """A stale module name in `jars` warns and declares no component. The descriptor is still declared.

    The case lives in this package, and not beside the other macro tests in `dev_dist_content_test.bzl`, because the
    warning must not print in a dist analysis. Every dist loads the `bazel-rules` package for `:trace_spans`.
    """
    stale_owner = name + "_stale_owner"
    stale_source = name + "_stale_descriptor"
    _fixture_module(name = stale_owner, module_name = "intellij.test.stale")
    _fixture_xml(name = stale_source)
    dev_dist_plugin(
        main_module = "intellij.test.stale",
        module_targets = {"intellij.test.stale": [":" + stale_owner + ".jar"]},
        descriptor = stale_source,
        jars = {"lib/test-stale.jar": ["intellij.test.stale", "intellij.test.gone"]},
    )
    test = name + "_stale_test"
    _declaration_test(
        name = test,
        actual = json.encode([
            native.existing_rule("intellij.test.stale_dev_plugin") == None,
            native.existing_rule(dev_dist_plugin_descriptor_target_name("intellij.test.stale")) != None,
        ]),
        expected = json.encode([True, True]),
    )
    return test

def _copies_macro_test(name, helper_token, resources_token, resources_prefix):
    """`dev_dist_plugin` forwards `files`, `file_prefixes` and `executable_files` to the component it declares."""
    main_module = "intellij.test.copied"
    copied_owner = name + "_copied_owner"
    copied_source = name + "_copied_descriptor"
    _fixture_module(name = copied_owner, module_name = main_module)
    _fixture_xml(name = copied_source)
    files = {"bin/helper.sh": helper_token, "helpers": resources_token}
    file_prefixes = {"helpers": resources_prefix}
    executable_files = ["bin/helper.sh"]
    dev_dist_plugin(
        main_module = main_module,
        module_targets = {main_module: [":" + copied_owner + ".jar"]},
        descriptor = copied_source,
        jars = {"lib/test-copied.jar": [main_module]},
        files = files,
        file_prefixes = file_prefixes,
        executable_files = executable_files,
    )
    component = native.existing_rule(dev_dist_plugin_component_target_name(main_module))
    test = name + "_copies_macro_test"
    _declaration_test(
        name = test,
        actual = json.encode([component["files"], component["file_prefixes"], component["executable_files"]]),
        expected = json.encode([files, file_prefixes, executable_files]),
    )
    return test

def dev_plugin_test_suite(name):
    """Declares the packed plugin component tests.

    Args:
        name: the suite's name. It must be `dev_plugin_tests`, because the test configurations name its product info.
    """
    if name != _SUITE or native.package_name() != _PACKAGE.lstrip("/"):
        fail("dev_plugin_test_suite must be declared as '%s' in package '%s'" % (_SUITE, _PACKAGE))
    dev_dist_product_info(
        name = name + "_product_info",
        release_date = "20260101",
        release_version = "2026300",
        platform_prefix = "idea",
    )
    dev_dist_product_info(
        name = name + "_no_prefix_product_info",
        release_date = "20260101",
        release_version = "2026300",
    )
    dev_dist_product_info(
        name = name + "_frontend_product_info",
        release_date = "20260101",
        release_version = "2026300",
        platform_prefix = "client",
        mode = "frontend",
    )

    owner = name + "_owner"
    split = name + "_split"
    member = name + "_member"
    _fixture_module(name = owner, module_name = _MAIN_MODULE)
    _fixture_module(name = split, module_name = _SPLIT_MODULE)
    _fixture_module(name = member, module_name = _MEMBER_MODULE)
    content_module_jar(module = ":" + member)
    content_jar = content_module_jar_target_name(member)

    library = name + "_library"

    # A container token expands to every jar of the library. A jar file token names one archive.
    for jar in ["foo-1.2.3", "bar-2.0", "baz-1.0"]:
        _fixture_jar(name = jar)
    _fixture_library(name = library, jars = [":bar-2.0", ":baz-1.0"])
    library_token = _PACKAGE + ":" + library
    single_token = _PACKAGE + ":foo-1.2.3.jar"

    source = name + "_descriptor_source"
    _fixture_xml(name = source)
    dev_dist_plugin_descriptor(
        main_module = _MAIN_MODULE,
        descriptor_module = ":" + source,
        descriptor = source,
    )
    descriptor = ":" + dev_dist_plugin_descriptor_target_name(_MAIN_MODULE)
    modules = {":" + owner: _MAIN_MODULE, ":" + split: _SPLIT_MODULE}

    # A single file copy and a directory copy. The filegroup stands for the package's `:dev_dist_resources`, and the
    # prefix is the repository-relative directory the copy takes, as `source_tree_prefixes` of the chain states it.
    helper = name + "_helper"
    _fixture_script(name = helper)
    helper_token = _PACKAGE + ":" + helper + ".sh"
    resource_files = name + "_resource_files"
    _fixture_resources(name = resource_files)
    resources = name + "_resources"
    native.filegroup(name = resources, srcs = [":" + resource_files])
    resources_token = _PACKAGE + ":" + resources
    resources_prefix = native.package_name() + "/" + resource_files + ".source-root"

    component = name + "_component"
    dev_plugin(
        name = component,
        main_module = _MAIN_MODULE,
        descriptor = descriptor,
        plugin_directory = "plugins/dev-plugin",
        modules = modules,
        libraries = [library_token, single_token],
        content_module_jars = [":" + content_jar],
        jars = {
            "lib/dev-plugin.jar": [library_token, _MAIN_MODULE, _SPLIT_MODULE],
            "lib/foo.jar": [single_token],
        },
        module_jar_paths = {_MEMBER_MODULE: "lib/member.jar"},
        classpath_jars = ["lib/member.jar", "lib/dev-plugin.jar", "lib/foo.jar"],
        files = {
            "bin/helper.sh": helper_token,
            "helpers": resources_token,
        },
        file_prefixes = {"helpers": resources_prefix},
        executable_files = ["bin/helper.sh"],
    )
    tests = []
    for suffix, test_rule, spans in [("_test", _dev_plugin_test, False), ("_spans_test", _dev_plugin_spans_test, True)]:
        tests.append(component + suffix)
        test_rule(
            name = tests[-1],
            target_under_test = ":" + component,
            content_jar = ":" + content_jar,
            library = ":" + library,
            modules = [":" + owner, ":" + split],
            helper = ":" + helper + ".sh",
            resources = ":" + resource_files,
            single_jar = ":foo-1.2.3.jar",
            spans = spans,
        )

    for case, jars, classpath_jars, message, test_rule in [
        ("no_main", {"lib/x.jar": [_SPLIT_MODULE]}, [], "is merged into no jar", _failure_test),
        ("unknown_module", {"lib/x.jar": [_MAIN_MODULE, "test.dev.unknown"]}, [], "which `modules` does not declare", _failure_test),
        ("unknown_library", {"lib/x.jar": [_MAIN_MODULE, "//nowhere:lib"]}, [], "which `libraries` does not declare", _failure_test),
        ("twice", {"lib/x.jar": [_MAIN_MODULE, _MAIN_MODULE]}, [], "names module 'test.dev.plugin' twice", _failure_test),
        ("classpath", {"lib/x.jar": [_MAIN_MODULE], "lib/y.jar": [_SPLIT_MODULE]}, ["lib/y.jar"], "must name every jar once", _failure_test),
        ("no_prefix", {"lib/x.jar": [_MAIN_MODULE]}, [], "requires a product configuration", _no_prefix_failure_test),
    ]:
        failing = name + "_failing_" + case
        dev_plugin(
            name = failing,
            main_module = _MAIN_MODULE,
            descriptor = descriptor,
            plugin_directory = "plugins/dev-plugin",
            modules = modules,
            libraries = [library_token],
            jars = jars,
            classpath_jars = classpath_jars,
        )
        tests.append(failing + "_test")
        test_rule(name = tests[-1], target_under_test = ":" + failing, expected_message = message)

    # A copy is refused when it meets a jar or another copy, when a single-file destination has two sources, when a
    # directory copy is empty, and when `executable_files` names anything but a copied single file.
    for case, files, file_prefixes, executable_files, message in [
        ("many_files", {"bin/helper": resources_token}, {}, [], "copies one file"),
        ("jar_collision", {"lib/x.jar": helper_token}, {}, [], "is both stated in `jars` and copied by `files`"),
        ("unknown_executable", {"bin/helper.sh": helper_token}, {}, ["bin/none"], "executable_files names 'bin/none', which `files` does not copy"),
        ("directory_executable", {"helpers": resources_token}, {"helpers": resources_prefix}, ["helpers"], "which is a directory copy"),
        ("empty_directory", {"helpers": resources_token}, {"helpers": "nowhere/at/all"}, [], "has no declared File below nowhere/at/all"),
        ("file_under_directory", {"helpers": resources_token, "helpers/extra.txt": helper_token}, {"helpers": resources_prefix}, [], "is below 'helpers', which `file_prefixes` copies as a whole"),
        ("directory_over_jar", {"lib": resources_token}, {"lib": resources_prefix}, [], "'lib' is a directory of another destination"),
        ("unknown_prefix", {"bin/helper.sh": helper_token}, {"helpers": resources_prefix}, [], "file_prefixes names 'helpers', which `files` does not copy"),
    ]:
        failing = name + "_failing_" + case
        dev_plugin(
            name = failing,
            main_module = _MAIN_MODULE,
            descriptor = descriptor,
            plugin_directory = "plugins/dev-plugin",
            modules = modules,
            jars = {"lib/x.jar": [_MAIN_MODULE]},
            files = files,
            file_prefixes = file_prefixes,
            executable_files = executable_files,
        )
        tests.append(failing + "_test")
        _failure_test(name = tests[-1], target_under_test = ":" + failing, expected_message = message)

    tests.append(_stale_macro_test(name))
    tests.append(_copies_macro_test(name, helper_token, resources_token, resources_prefix))

    # One leaf serves a frontend product: it refuses the split module and the member there, and the shared packaging
    # drops both. A packaging that keeps them is one product's own.
    mode_owner = name + "_mode_owner"
    _fixture_module(name = mode_owner, module_name = _MODE_MODULE)
    dev_dist_plugin_descriptor(
        main_module = _MODE_MODULE,
        descriptor_module = ":" + source,
        descriptor = source,
        mode_refused_content_modules = {"frontend": [_SPLIT_MODULE, _MEMBER_MODULE]},
    )
    for case, keeps, expected_packed, expected_destinations in [
        ("shared", False, ["mode.jar"], ["lib/mode.jar"]),
        ("own", True, ["mode.jar", "split.jar"], ["lib/mode.jar", "lib/modules/test.dev.member.jar", "lib/split.jar"]),
    ]:
        mode_component = name + "_mode_" + case
        dev_plugin(
            name = mode_component,
            main_module = _MODE_MODULE,
            descriptor = ":" + dev_dist_plugin_descriptor_target_name(_MODE_MODULE),
            plugin_directory = "plugins/mode",
            modules = {":" + mode_owner: _MODE_MODULE, ":" + split: _SPLIT_MODULE},
            libraries = [library_token],
            content_module_jars = [":" + content_jar],
            jars = {
                "lib/mode.jar": [_MODE_MODULE, _SPLIT_MODULE],
                "lib/split.jar": [_SPLIT_MODULE, library_token],
            },
            keeps_mode_refused_modules = keeps,
        )
        tests.append(mode_component + "_test")
        _mode_test(
            name = tests[-1],
            target_under_test = ":" + mode_component,
            expected_packed = expected_packed,
            expected_destinations = expected_destinations,
        )

    native.test_suite(name = name, tests = tests)
