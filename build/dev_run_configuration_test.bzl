"""Unit tests for the helpers that the community launchers read.

The twin of `build/dev_run_configuration_test.bzl` of the ultimate checkout. `dev_run_configuration_check_plan` fails
at load time on a row of a product that the generated plan does not serve. A Starlark test cannot catch a `fail()`, so
the check has one test: every community product passes it. The golden lists below are the platform components that
each community distribution composes. A change in the component layout has to change this file too.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load("//build/dev-dist-content:dev_dist_content_sets.bzl", "DEV_DIST_PLUGIN_COMPONENTS")
load(":dev_dist_plan.bzl", "DEV_DIST_PLANS")
load(":dev_server_run_configurations.bzl", "DEV_RUN_CONFIGURATIONS")
load(
    ":intellij_dev_community.bzl",
    "dev_dist_platform_fragments",
    "dev_run_configuration_check_plan",
    "dev_run_distribution_groups",
    "dev_run_distribution_name",
)

# Every product of `DEV_DIST_PRODUCTS` in `dev_dist_products.bzl`.
_PLANNED_PRODUCTS = [
    "Idea",
    "AndroidStudio",
]

_FRAGMENT_NAMES = [
    "platform_resources",
    "platform_packed_content_modules",
    "platform_assets",
]

_IDEA_FRAGMENTS = [
    "//build:idea_community_dev_platform_resources",
    "//build:idea_community_dev_platform_platform_packed_content_modules",
    "//build:idea_community_dev_platform_assets",
]

_ANDROID_STUDIO_FRAGMENTS = [
    "//build:android_studio_dev_platform_resources",
    "//build:android_studio_dev_platform_platform_packed_content_modules",
    "//build:android_studio_dev_platform_assets",
]

def _check_plan_test_impl(ctx):
    env = unittest.begin(ctx)
    for product in _PLANNED_PRODUCTS:
        asserts.equals(env, None, dev_run_configuration_check_plan("x", product), product + " has a generated plan")
        asserts.false(
            env,
            getattr(DEV_DIST_PLANS[product], "runtime_module_repository", False),
            "%s has no runtime module repository, so the community half declares no project model tree" % product,
        )
    return unittest.end(env)

check_plan_test = unittest.make(_check_plan_test_impl)

def _additional_modules_test_impl(ctx):
    env = unittest.begin(ctx)
    for name, row in DEV_RUN_CONFIGURATIONS.items():
        tiers = DEV_DIST_PLUGIN_COMPONENTS[row.product]
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
    distributions = [dev_run_distribution_name(names) for names in dev_run_distribution_groups(DEV_RUN_CONFIGURATIONS).values()]
    for name in ["idea_community", "android_studio_dev_build", "idea_with_compose_dev_build"]:
        asserts.true(env, name in distributions, "'%s' names its distribution" % name)
    return unittest.end(env)

distribution_name_test = unittest.make(_distribution_name_test_impl)

def _idea_platform_fragments_test_impl(ctx):
    env = unittest.begin(ctx)
    platform = dev_dist_platform_fragments("Idea")
    asserts.equals(env, _IDEA_FRAGMENTS, platform.fragments)
    asserts.equals(env, _FRAGMENT_NAMES, platform.fragment_names)
    return unittest.end(env)

idea_platform_fragments_test = unittest.make(_idea_platform_fragments_test_impl)

def _android_studio_platform_fragments_test_impl(ctx):
    env = unittest.begin(ctx)
    platform = dev_dist_platform_fragments("AndroidStudio")
    asserts.equals(env, _ANDROID_STUDIO_FRAGMENTS, platform.fragments)
    asserts.equals(env, _FRAGMENT_NAMES, platform.fragment_names)
    return unittest.end(env)

android_studio_platform_fragments_test = unittest.make(_android_studio_platform_fragments_test_impl)

def dev_run_configuration_test_suite(name):
    """Test suite for the helpers of the community launchers."""
    unittest.suite(
        name,
        check_plan_test,
        additional_modules_test,
        distribution_name_test,
        idea_platform_fragments_test,
        android_studio_platform_fragments_test,
    )
