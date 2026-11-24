"""Tests of `jps_modules_layout`, the logic of the `jps_modules` repository rule."""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(":jps_modules.bzl", "jps_modules_layout")

def _modules_xml(*paths):
    return "<project><component><modules>\n" + "".join([
        '<module fileurl="file://$PROJECT_DIR$/%s" filepath="$PROJECT_DIR$/%s" />\n' % (path, path)
        for path in paths
    ]) + "</modules></component></project>\n"

_LAYOUT = struct(
    repository = "@//",
    repositories = {"community/": "@community//"},
    packages = {"intellij.custom": "community/build"},
    imls = {"intellij.skipped": "@separate//:intellij.skipped.iml"},
    build_modules = ["intellij.default.build"],
)

_LISTS = {
    "": "ultimate/a intellij.a intellij.a.tests\nultimate/build intellij.default.build intellij.product.build\n",
    "community/": "build intellij.custom\nplatform/b intellij.b\n",
}

_MODULES_XML = _modules_xml(
    "ultimate/a/intellij.a.iml",
    "ultimate/a/intellij.a.tests.iml",
    "ultimate/build/intellij.default.build.iml",
    "ultimate/build/intellij.product.build.iml",
    "community/platform/b/intellij.b.iml",
    "community/build/jvm-rules/custom/intellij.custom.iml",
    "community/intellij.root.iml",
    "separate/intellij.skipped.iml",
)

_DEV_BUILD = [("dev-build.json", '{"products": {"Product": {"modules": ["intellij.product.build"]}}}')]

def _maps_modules_to_packages_test_impl(ctx):
    env = unittest.begin(ctx)
    layout = jps_modules_layout(_MODULES_XML, _LISTS, _DEV_BUILD, _LAYOUT)
    asserts.equals(env, [], layout.errors)
    asserts.equals(env, [
        "@//ultimate/a:intellij.a",
        "@//ultimate/a:intellij.a.tests",
        "@//ultimate/build:intellij.default.build",
        "@//ultimate/build:intellij.product.build",
        "@community//:intellij.root",
        "@community//build:intellij.custom",
        "@community//platform/b:intellij.b",
    ], layout.records)
    asserts.equals(env, ["@separate//:intellij.skipped.iml"], layout.imls)
    asserts.equals(env, ["@//ultimate/build:intellij.default.build", "@//ultimate/build:intellij.product.build"], layout.build_modules)
    return unittest.end(env)

maps_modules_to_packages_test = unittest.make(_maps_modules_to_packages_test_impl)

def _module_without_record_test_impl(ctx):
    env = unittest.begin(ctx)

    # A new module in a written directory, and a module in a new directory.
    modules_xml = _MODULES_XML + _modules_xml("ultimate/a/intellij.a.new.iml", "ultimate/new/intellij.new.iml")
    layout = jps_modules_layout(modules_xml, _LISTS, _DEV_BUILD, _LAYOUT)
    asserts.equals(env, 1, len(layout.errors))
    error = layout.errors[0]
    asserts.true(env, error.startswith("2 module(s) of modules.xml have no record. Run ./build/jpsModelToBazel.cmd."), error)
    asserts.true(env, "intellij.a.new (ultimate/a/intellij.a.new.iml)" in error, error)
    asserts.true(env, "intellij.new (ultimate/new/intellij.new.iml)" in error, error)
    asserts.true(env, "@//ultimate/a:intellij.a.new" not in layout.records)
    return unittest.end(env)

module_without_record_test = unittest.make(_module_without_record_test_impl)

def _stale_build_module_test_impl(ctx):
    env = unittest.begin(ctx)
    dev_build = [("dev-build.json", '{"products": {"Product": {"modules": ["intellij.product.build", "intellij.removed.build"]}}}')]
    layout = jps_modules_layout(_MODULES_XML, _LISTS, dev_build, _LAYOUT)
    asserts.equals(env, ["The build modules of dev-build.json are stale. The project model does not have them:\n  intellij.removed.build"], layout.errors)
    asserts.equals(env, ["@//ultimate/build:intellij.default.build", "@//ultimate/build:intellij.product.build"], layout.build_modules)
    return unittest.end(env)

stale_build_module_test = unittest.make(_stale_build_module_test_impl)

def jps_modules_test_suite(name):
    unittest.suite(name, maps_modules_to_packages_test, module_without_record_test, stale_build_module_test)
