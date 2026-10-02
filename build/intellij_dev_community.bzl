"""The split dev distributions and launchers of the community products.

The file binds `intellij_dev_dist_declarations` to the generated tables of the community half and re-exports its
macros. `build/intellij_dev_ultimate.bzl` of the ultimate checkout is the twin of this file.
"""

load("@jps_dynamic_deps_community//:targets.bzl", "BAZEL_TARGETS_JSON_COMMUNITY")
load("//build/dev-dist-content:dev_dist_content_sets.bzl", "DEV_DIST_PLUGIN_COMPONENTS")
load("//platform/build-scripts/bazel-rules:intellij_dev_dist.bzl", "intellij_project_model_tree")
load(":dev_dist_fragment_inputs.bzl", "DEV_DIST_FRAGMENT_INPUTS")
load(":dev_dist_module_sets.bzl", "DEV_DIST_MODULE_SETS")
load(":dev_dist_packed_labels.bzl", "dev_dist_packed_labels")
load(":dev_dist_plan.bzl", "DEV_DIST_APPLICATION_INFOS", "DEV_DIST_LAUNCH_MODELS", "DEV_DIST_PLANS", "DEV_DIST_PLATFORM_JAR_ORDERS")
load(":dev_dist_product_info.bzl", "DEV_DIST_PRODUCT_INFO")
load(":dev_dist_products.bzl", "DEV_DIST_PRODUCTS", "dev_dist_product")
load(":intellij_dev_dist_declarations.bzl", "intellij_dev_dist_declarations")

# The checkout files a dev assembly of a community product reads that the JPS project model does not name. The
# product's own files are in the `extra_project_files` of its `DEV_DIST_PRODUCTS` entry.
COMMUNITY_EXTRA_PROJECT_FILES = [
    "//build:dev-build.json",
    "//:build.txt",
    "//bin:idea.properties",
    "//build:dependencies/dependencies.properties",
    "//build:dependencies/runtime.properties",
    "//platform/platform-impl:resources/META-INF/OpenedPackages.txt",
]

# The project model tree of the community half, declared by `intellij_dev_project_model_tree_community`. Only the
# runtime module repository component reads it, and no community row asks for that component yet.
_DEV_PROJECT_MODEL_TREE = "//build:dev_project_model_tree"

# The payload key of the platform `lib/` jars, and the component of the runtime module repository. The ultimate
# bridge exports the same two names.
_PLATFORM_LIB_FRAGMENT = "platform_lib"

_PLATFORM_RUNTIME_MODULE_REPOSITORY_FRAGMENT = "platform_runtime_module_repository"

def _platform_lib_payload(product):
    info = DEV_DIST_PRODUCT_INFO.get(product)
    if info == None:
        fail("No generated dev-distribution product info for product '%s', so its product mode is unknown" % product)
    return dev_dist_packed_labels(DEV_DIST_FRAGMENT_INPUTS, DEV_DIST_MODULE_SETS, product, info.mode)

def _product_info_label(product):
    return "//build/dev-dist-descriptors:%s_product_info" % product

# The generated tables of the community half, which the shared declarations read. The community half has no hook:
# no community launcher changes its flags, and none has a before-run step.
_DECLARATIONS = intellij_dev_dist_declarations(struct(
    plans = DEV_DIST_PLANS,
    launch_models = DEV_DIST_LAUNCH_MODELS,
    application_infos = DEV_DIST_APPLICATION_INFOS,
    platform_jar_orders = DEV_DIST_PLATFORM_JAR_ORDERS,
    fragment_inputs = DEV_DIST_FRAGMENT_INPUTS,
    plugin_components = DEV_DIST_PLUGIN_COMPONENTS,
    product = dev_dist_product,
    platform_lib_fragment = _PLATFORM_LIB_FRAGMENT,
    runtime_module_repository_fragment = _PLATFORM_RUNTIME_MODULE_REPOSITORY_FRAGMENT,
    platform_lib_payload = _platform_lib_payload,
    bazel_targets_json = BAZEL_TARGETS_JSON_COMMUNITY,
    project_model_tree = _DEV_PROJECT_MODEL_TREE,
    collector = "//build:dev_dist_packed_jars",
    composer = "//build:dev_dist_composer",
    build_package = "//build",
    product_info_label = _product_info_label,
    launcher_jvm_flags = None,
    before_run = None,
    community = None,
))

# The platform set of one split product. See `platform_set` in `intellij_dev_dist_declarations.bzl`.
intellij_dev_platform_set_community = _DECLARATIONS.platform_set

# The platform fragment labels of one split product, for a distribution declared outside `build/BUILD.bazel`.
dev_dist_platform_fragments = _DECLARATIONS.platform_fragments

# Fails at load time when the generated plan cannot serve a launcher.
dev_run_configuration_check_plan = _DECLARATIONS.check_plan

# The distribution name of a group of rows.
dev_run_distribution_name = _DECLARATIONS.distribution_name

# The rows of `DEV_RUN_CONFIGURATIONS` grouped by the distribution they share.
dev_run_distribution_groups = _DECLARATIONS.distribution_groups

# The launchers of `DEV_RUN_CONFIGURATIONS`, the rows the plan generator writes into `dev_server_run_configurations.bzl`.
intellij_dev_run_configurations = _DECLARATIONS.run_configurations

# One dev launcher written by hand over a distribution of its own. Bazel names the symbolic macro after this global.
intellij_dev_run_configuration = _DECLARATIONS.run_configuration

def intellij_dev_project_model_tree_community():
    """Declares `//build:dev_project_model_tree`, the checkout-shaped tree of the community half.

    It carries the community project model, the product files, and the union of the `extra_project_files` of every
    `DEV_DIST_PRODUCTS` entry. Only the runtime module repository component reads the tree. Call the macro in
    `//build` when the first community row asks for that component.
    """
    if native.package_name() != "build":
        fail("Declare the dev project model tree in //build, where %s points" % _DEV_PROJECT_MODEL_TREE)
    extra_project_files = {}
    for entry in DEV_DIST_PRODUCTS.values():
        for file in entry.extra_project_files:
            extra_project_files[file] = True
    intellij_project_model_tree(
        name = _DEV_PROJECT_MODEL_TREE.split(":")[1],
        materializer = "//build/dev-dist-tools/bins/project-model-tree",
        mode = "community",
        project_model_files = ["//build:community_project_model_files"],
        extra_project_files = COMMUNITY_EXTRA_PROJECT_FILES + extra_project_files.keys(),
    )
