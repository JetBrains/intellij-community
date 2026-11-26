"""Unit tests for the helpers that the community launchers read.

The twin of `build/dev_run_configuration_test.bzl` of the ultimate checkout. `dev_run_configuration_check_plan` fails
at load time on a row of a product that the generated plan does not serve. A Starlark test cannot catch a `fail()`, so
the check has one test: every community product passes it. The golden lists below are the platform components that
each community distribution composes. A change in the component layout has to change this file too. The last three
tests pin the production places of `product-info.json` and `build.txt`, and the home prefix of a distribution. The
platform resources and the native launcher read `product-info.json`.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load("//build/dist-content:dist_content_sets.bzl", "DIST_PLUGIN_COMPONENTS")
load("//platform/build-scripts/bazel-rules:dist_product_files.bzl", "build_txt_destination", "home_prefix", "product_info_destination", "vm_options_file_name")
load(":dev_server_run_configurations.bzl", "DEV_RUN_CONFIGURATIONS")
load(":dist_plan.bzl", "DIST_PLANS")
load(
    ":intellij_dev_community.bzl",
    "dev_run_configuration_check_plan",
    "dev_run_distribution_groups",
    "dist_platform_components",
)

# Every product of `DIST_PRODUCTS` in `dist_products.bzl`.
_PLANNED_PRODUCTS = [
    "Idea",
    "AndroidStudio",
]

_COMPONENT_NAMES = [
    "platform_resources",
    "platform_packed_content_modules",
    "platform_assets",
]

_IDEA_COMPONENTS = [
    "//build:idea_community_platform_resources",
    "//build:idea_community_platform_platform_packed_content_modules",
    "//build:idea_community_platform_assets",
]

_ANDROID_STUDIO_COMPONENTS = [
    "//build:android_studio_platform_resources",
    "//build:android_studio_platform_platform_packed_content_modules",
    "//build:android_studio_platform_assets",
]

def _check_plan_test_impl(ctx):
    env = unittest.begin(ctx)
    for product in _PLANNED_PRODUCTS:
        asserts.equals(env, None, dev_run_configuration_check_plan("x", product), product + " has a generated plan")
        asserts.false(
            env,
            getattr(DIST_PLANS[product], "runtime_module_repository", False),
            "%s has no runtime module repository, so the community half declares no project model tree" % product,
        )
    return unittest.end(env)

check_plan_test = unittest.make(_check_plan_test_impl)

def _additional_modules_test_impl(ctx):
    env = unittest.begin(ctx)
    for name, row in DEV_RUN_CONFIGURATIONS.items():
        tiers = DIST_PLUGIN_COMPONENTS[row.product]
        for module in getattr(row, "additional_modules", []):
            asserts.true(
                env,
                module in tiers["bundled"] or module in tiers["additional"],
                "the additional module '%s' of the row '%s' has a component" % (module, name),
            )
    return unittest.end(env)

additional_modules_test = unittest.make(_additional_modules_test_impl)

def _distribution_name_test_impl(ctx):
    env = unittest.begin(ctx)

    # The distributions that the smoke tests and the aliases of `build/BUILD.bazel` name.
    distributions = dev_run_distribution_groups(DEV_RUN_CONFIGURATIONS).keys()
    for name in ["idea_community", "android_studio_dev_build", "idea_with_compose_dev_build"]:
        asserts.true(env, name in distributions, "'%s' names its distribution" % name)
    return unittest.end(env)

distribution_name_test = unittest.make(_distribution_name_test_impl)

def _idea_platform_components_test_impl(ctx):
    env = unittest.begin(ctx)
    platform = dist_platform_components("Idea")
    asserts.equals(env, _IDEA_COMPONENTS, platform.components)
    asserts.equals(env, _COMPONENT_NAMES, platform.component_names)
    return unittest.end(env)

idea_platform_components_test = unittest.make(_idea_platform_components_test_impl)

def _android_studio_platform_components_test_impl(ctx):
    env = unittest.begin(ctx)
    platform = dist_platform_components("AndroidStudio")
    asserts.equals(env, _ANDROID_STUDIO_COMPONENTS, platform.components)
    asserts.equals(env, _COMPONENT_NAMES, platform.component_names)
    return unittest.end(env)

android_studio_platform_components_test = unittest.make(_android_studio_platform_components_test_impl)

def _product_info_destination_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(env, "Resources/product-info.json", product_info_destination("darwin", False), "a macOS app")
    for os, language_server in [("darwin", True), ("linux", False), ("linux", True), ("windows", False), ("windows", True)]:
        asserts.equals(
            env,
            "product-info.json",
            product_info_destination(os, language_server),
            "%s, language server %s" % (os, language_server),
        )
    return unittest.end(env)

product_info_destination_test = unittest.make(_product_info_destination_test_impl)

def _build_txt_destination_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(env, "Resources/build.txt", build_txt_destination("darwin", False), "a macOS app")
    for os, language_server in [("darwin", True), ("linux", False), ("linux", True), ("windows", False), ("windows", True)]:
        asserts.equals(env, "build.txt", build_txt_destination(os, language_server), "%s, language server %s" % (os, language_server))
    return unittest.end(env)

build_txt_destination_test = unittest.make(_build_txt_destination_test_impl)

# The cases of `VmOptionsFileNameTest`, which pins the same names of `vmOptionsFileName` in Kotlin.
_VM_OPTIONS_FILE_NAMES = [
    ("idea", False, {"darwin": "idea.vmoptions", "linux": "idea64.vmoptions", "windows": "idea64.exe.vmoptions"}),
    ("jetbrains_client", False, {"darwin": "jetbrains_client.vmoptions", "linux": "jetbrains_client64.vmoptions", "windows": "jetbrains_client64.exe.vmoptions"}),
    ("intellij-server", True, {"darwin": "intellij-server.vmoptions", "linux": "intellij-server.vmoptions", "windows": "intellij-server.exe.vmoptions"}),
]

def _vm_options_file_name_test_impl(ctx):
    env = unittest.begin(ctx)
    for base_file_name, language_server, names in _VM_OPTIONS_FILE_NAMES:
        for os, expected in names.items():
            asserts.equals(env, expected, vm_options_file_name(os, base_file_name, language_server), "%s on %s" % (base_file_name, os))
    return unittest.end(env)

vm_options_file_name_test = unittest.make(_vm_options_file_name_test_impl)

def _home_prefix_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(env, "Contents", home_prefix("darwin", False), "a macOS app")
    for os, language_server in [("darwin", True), ("linux", False), ("linux", True), ("windows", False), ("windows", True)]:
        asserts.equals(env, "", home_prefix(os, language_server), "%s, language server %s" % (os, language_server))
    return unittest.end(env)

home_prefix_test = unittest.make(_home_prefix_test_impl)

def dev_run_configuration_test_suite(name):
    """Test suite for the helpers of the community launchers."""
    unittest.suite(
        name,
        check_plan_test,
        additional_modules_test,
        distribution_name_test,
        idea_platform_components_test,
        android_studio_platform_components_test,
        product_info_destination_test,
        build_txt_destination_test,
        vm_options_file_name_test,
        home_prefix_test,
    )
