"""Analysis tests for the directly packed plugin component of `dev_plugin.bzl`."""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts", "unittest")
load("@rules_java//java:defs.bzl", "JavaInfo", "java_common")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "content_module_jar", "content_module_jar_target_name")
load(":dev_dist_plugin.bzl", "dev_dist_plugin")
load(":dev_dist_plugin_descriptor.bzl", "dev_dist_plugin_descriptor", "dev_dist_plugin_descriptor_target_name", "dev_dist_product_info")
load(":dev_plugin.bzl", "dev_plugin")
load(":intellij_dev_dist.bzl", "IntellijDevFragmentInfo")

# The suite's package and name are fixed: a test rule's configuration names the product info target by label.
_PACKAGE = "//platform/build-scripts/bazel-rules/dev-plugin-tests"
_SUITE = "dev_plugin_tests"
_PRODUCT_INFO = str(Label(_PACKAGE + ":" + _SUITE + "_product_info"))
_NO_PREFIX_PRODUCT_INFO = str(Label(_PACKAGE + ":" + _SUITE + "_no_prefix_product_info"))
_PRODUCT_INFO_FLAG = str(Label("//build:dev_dist_product_info"))
_TRACE_SPANS = str(Label("//platform/build-scripts/bazel-rules:trace_spans"))
_EMPTY_JAR = "PK\005\006" + ("\000" * 18)
_MAIN_MODULE = "test.dev.plugin"
_SPLIT_MODULE = "test.dev.split"
_MEMBER_MODULE = "test.dev.member"

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

    # The payload holds the two packed jars and the reused content module jar.
    # Files are compared by short path: the test's own attributes are configured with the test, and the component's
    # inputs with the reset, so the same jar arrives as two `File` objects.
    payload = fragment.payload.to_list()
    content = ctx.attr.content_jar[ContentModuleJarInfo]
    asserts.equals(env, 3, len(payload))
    asserts.true(env, content.jar.short_path in [jar.short_path for jar in payload])
    for jar in payload:
        asserts.true(env, jar in collect.inputs.to_list(), jar.path)

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
    return analysistest.end(env)

_DEV_PLUGIN_ATTRS = {
    "content_jar": attr.label(mandatory = True, providers = [ContentModuleJarInfo]),
    "library": attr.label(mandatory = True, providers = [JavaInfo]),
    "modules": attr.label_list(mandatory = True, providers = [_KtJvmInfo]),
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

    tests.append(_stale_macro_test(name))
    native.test_suite(name = name, tests = tests)
