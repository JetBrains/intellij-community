"""The split distributions and launchers of the community products.

The file binds `intellij_dist_declarations` to the generated tables of the community half and re-exports its
macros. `build/intellij_dev_ultimate.bzl` of the ultimate checkout is the twin of this file.
"""

load("//build:bazel_targets_json.bzl", "BAZEL_TARGETS_JSON_COMMUNITY")
load("//build/dist-content:dist_content_sets.bzl", "DIST_PLUGIN_COMPONENTS")
load("//platform/build-scripts/bazel-rules:dist_runtime_module_repository.bzl", "dist_runtime_module_facts")
load("//platform/build-scripts/bazel-rules:intellij_dist.bzl", "intellij_project_model_tree")
load(":component_names.bzl", "PLATFORM_LIB_COMPONENT", "PLATFORM_RUNTIME_MODULE_REPOSITORY_COMPONENT")
load(":dist_fragment_inputs.bzl", "DIST_FRAGMENT_INPUTS")
load(":dist_module_sets.bzl", "DIST_MODULE_SETS")
load(":dist_plan.bzl", "DIST_APPLICATION_INFOS", "DIST_CASE_SAFE_NAMES", "DIST_PLANS", "DIST_PLATFORM_JAR_ORDERS")
load(":dist_product_info.bzl", "DIST_PRODUCT_INFO")
load(":dist_products.bzl", "DIST_PRODUCTS", "dist_product")
load(":intellij_dist_declarations.bzl", "intellij_dist_declarations")
load(":packed_labels.bzl", "packed_labels")

# The checkout files a dev assembly of a community product reads that the JPS project model does not name. The
# product's own files are in the `extra_project_files` of its `DIST_PRODUCTS` entry.
COMMUNITY_EXTRA_PROJECT_FILES = [
    "//build:dev-build.json",
    "//:build.txt",
    "//bin:idea.properties",
    "//build:dependencies/dependencies.properties",
    "//build:dependencies/runtime.properties",
    "//platform/platform-impl:resources/META-INF/OpenedPackages.txt",
]

# The project model tree of the community half, declared by `intellij_project_model_tree_community`. Only the
# runtime module facts action reads it, and no community row asks for the runtime module repository component yet.
_PROJECT_MODEL_TREE = "//build:project_model_tree"

# The runtime module facts of that tree, declared next to it. The runtime module repository actions read them.
_RUNTIME_MODULE_FACTS = "//build:runtime_module_facts"

def _platform_lib_payload(product):
    info = DIST_PRODUCT_INFO.get(product)
    if info == None:
        fail("No generated distribution product info for product '%s', so its product mode is unknown" % product)
    return packed_labels(DIST_FRAGMENT_INPUTS, DIST_MODULE_SETS, product, info.mode)

def _product_mode(product):
    return DIST_PRODUCT_INFO[product].mode

# The generated tables of the community half, which the shared declarations read. The community half has no hook:
# no community launcher changes its flags, and none has a before-run step.
_DECLARATIONS = intellij_dist_declarations(struct(
    plans = DIST_PLANS,
    case_safe_names = DIST_CASE_SAFE_NAMES,
    application_infos = DIST_APPLICATION_INFOS,
    platform_jar_orders = DIST_PLATFORM_JAR_ORDERS,
    component_inputs = DIST_FRAGMENT_INPUTS,
    plugin_components = DIST_PLUGIN_COMPONENTS,
    product = dist_product,
    platform_lib_component = PLATFORM_LIB_COMPONENT,
    runtime_module_repository_component = PLATFORM_RUNTIME_MODULE_REPOSITORY_COMPONENT,
    platform_lib_payload = _platform_lib_payload,
    bazel_targets_json = BAZEL_TARGETS_JSON_COMMUNITY,
    runtime_module_facts = _RUNTIME_MODULE_FACTS,
    collector = "//build:dist_packed_jars",
    composer = "//build:dist_composer",
    build_package = "//build",
    product_mode = _product_mode,
    launcher_jvm_flags = None,
    check_before_run = None,
    community = None,
))

# The platform set of one split product. See `platform_set` in `intellij_dist_declarations.bzl`.
intellij_platform_set_community = _DECLARATIONS.platform_set

# The platform component labels of one split product, for a distribution declared outside `build/BUILD.bazel`.
dist_platform_components = _DECLARATIONS.platform_components

# Fails at load time when the generated plan cannot serve a launcher.
dev_run_configuration_check_plan = _DECLARATIONS.check_plan

# The rows of `DEV_RUN_CONFIGURATIONS` grouped by the distribution that their `distribution` field names.
dev_run_distribution_groups = _DECLARATIONS.distribution_groups

# The launchers of `DEV_RUN_CONFIGURATIONS`, the rows the plan generator writes into `dev_server_run_configurations.bzl`.
intellij_dev_run_configurations = _DECLARATIONS.run_configurations

# One dev launcher written by hand over a distribution of its own. Bazel names the symbolic macro after this global.
intellij_dev_run_configuration = _DECLARATIONS.run_configuration

# A `dist_build_test` that builds some rows.
intellij_dev_launch_assembles_test = _DECLARATIONS.launch_assembles_test

def intellij_project_model_tree_community():
    """Declares `//build:project_model_tree`, the checkout-shaped tree of the community half.

    It carries the community project model, the product files, and the union of the `extra_project_files` of every
    `DIST_PRODUCTS` entry. Only the runtime module facts action reads the tree. Call the macro in
    `//build` when the first community row asks for that component.

    It also declares `//build:runtime_module_facts`, the runtime module facts of the tree.
    """
    if native.package_name() != "build":
        fail("Declare the dev project model tree in //build, where %s points" % _PROJECT_MODEL_TREE)
    extra_project_files = {}
    for entry in DIST_PRODUCTS.values():
        for file in entry.extra_project_files:
            extra_project_files[file] = True
    intellij_project_model_tree(
        name = _PROJECT_MODEL_TREE.split(":")[1],
        materializer = "//build/dist-tools/bins/project-model-tree",
        mode = "community",
        project_model_files = ["//build:community_project_model_files"],
        extra_project_files = COMMUNITY_EXTRA_PROJECT_FILES + extra_project_files.keys(),
    )
    dist_runtime_module_facts(
        name = _RUNTIME_MODULE_FACTS.split(":")[1],
        project_model_tree = _PROJECT_MODEL_TREE,
        tags = ["manual"],
    )
