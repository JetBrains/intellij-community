"""
Dynamic Dependencies Bridge for JPS-to-Bazel target generation (community version)

Derives targets from .iml files and library XMLs using pure Starlark.
Generates targets.bzl with exported target lists for use by build rules.
Parity with the JPS-to-Bazel converter is asserted in JpsModuleToBazelTargetsOnly.
"""

load(":jps_model.bzl", "read_project_model")
load(":jps_target_derivation.bzl", "SKIPPED_MODULES", "compute_build_dir", "compute_iml_target", "compute_module_targets", "module_name_to_target", "parse_iml")
load(":jps_library_derivation.bzl", "derive_library_targets")

def _format_target_list(name, targets):
    """Format a list of targets as a Starlark list assignment."""
    if not targets:
        return "%s = []\n" % name

    lines = ["%s = [" % name]
    for target in targets:
        lines.append('    "%s",' % target)
    lines.append("]\n")
    return "\n".join(lines)

def _generate_targets_bzl(production_targets, test_targets, library_targets, iml_targets):
    """Generate the content for targets.bzl file."""
    content = []
    content.append(_format_target_list("ALL_PRODUCTION_COMMUNITY_TARGETS", production_targets))
    content.append(_format_target_list("ALL_TEST_COMMUNITY_TARGETS", test_targets))
    content.append(_format_target_list("ALL_LIBRARY_COMMUNITY_TARGETS", library_targets))
    content.append(_format_target_list("ALL_COMMUNITY_IML_TARGETS", iml_targets))
    content.append("BAZEL_TARGETS_JSON_COMMUNITY = \"@community//build:community_bazel_targets_json\"")
    content.append("ALL_COMMUNITY_TARGETS = ALL_PRODUCTION_COMMUNITY_TARGETS + ALL_TEST_COMMUNITY_TARGETS + ALL_LIBRARY_COMMUNITY_TARGETS")
    return "\n".join(content)

def _derive_targets_from_model(ctx, project_root, model):
    """Derive production, test, and library targets from project model using pure Starlark.

    Args:
        ctx: repository rule context (needed for jar directory expansion)
        project_root: Path to the project root
        model: struct from read_project_model with modules and library_xmls

    Returns:
        struct with production, test, library (all sorted lists)
    """
    all_production = []
    all_test = []
    iml_data_list = []
    all_iml = []

    # community-only: community_root_parts is [] (project root IS community root)
    community_root_parts = []
    # In community-only mode, ultimateRoot is null
    ultimate_root_parts = None

    for mod in model.modules:
        iml_dir_parts = mod.iml_dir_rel.split("/") if mod.iml_dir_rel else []
        parsed = parse_iml(mod.iml_content, mod.iml_rel_path)
        build_dir_parts = compute_build_dir(iml_dir_parts, parsed.content_root_urls, mod.iml_rel_path)
        iml_target = compute_iml_target(
            module_name = mod.module_name,
            build_dir_parts = build_dir_parts,
            iml_rel_path = mod.iml_rel_path,
            is_community = True,
            community_root_parts = community_root_parts,
        )
        if iml_target not in all_iml:
            all_iml.append(iml_target)

        # Skip modules that the converter also skips (standalone Bazel projects)
        if mod.module_name in SKIPPED_MODULES:
            continue

        iml_data_list.append(struct(
            module_name = mod.module_name,
            iml_dir_rel = mod.iml_dir_rel,
            iml_rel_path = mod.iml_rel_path,
            parsed_iml = parsed,
            is_community = True,
        ))

        target_name = module_name_to_target(
            module_name = mod.module_name,
            build_dir_parts = build_dir_parts,
            community_root_parts = community_root_parts,
            ultimate_root_parts = ultimate_root_parts,
        )

        targets = compute_module_targets(
            module_name = mod.module_name,
            build_dir_parts = build_dir_parts,
            target_name = target_name,
            is_community = True,
            community_root_parts = community_root_parts,
        )

        for t in targets.production:
            if t not in all_production:
                all_production.append(t)
        for t in targets.test:
            if t not in all_test:
                all_test.append(t)

    library_targets = derive_library_targets(
        ctx = ctx,
        project_root = project_root,
        library_xmls = model.library_xmls,
        iml_data_list = iml_data_list,
        is_community_only = True,
        community_root_rel = "",
    )

    return struct(
        production = all_production,
        test = all_test,
        library = library_targets,
        iml = all_iml,
    )

def _targets_repo_impl(ctx):
    root = ctx.path(Label("@community//:MODULE.bazel")).dirname
    model = read_project_model(ctx, root)
    starlark = _derive_targets_from_model(ctx, root, model)

    content = _generate_targets_bzl(
        sorted(starlark.production),
        sorted(starlark.test),
        starlark.library,
        sorted(starlark.iml),
    )
    ctx.file("targets.bzl", content)
    ctx.file("BUILD", 'exports_files(["targets.bzl"])')

targets_repo = repository_rule(
    implementation = _targets_repo_impl,
)

# Define the module extension that uses the repo rule
def _extension_impl(module_ctx):
    # Create the repository named "dynamic_deps"
    targets_repo(name = "jps_dynamic_deps_community")

# Export the extension
jps_dynamic_deps_community_extension = module_extension(
    implementation = _extension_impl,
)
