"""
Pure Starlark derivation of module targets from .iml files.

Mirrors the logic in BazelBuildFileGenerator.kt for computing BUILD directory,
target name, and production/test target labels.
"""

load("@xml.bzl//:xml.bzl", "xml")

# Modules skipped by the converter (standalone Bazel projects).
# Mirrors computeModuleList in BazelBuildFileGenerator.kt.
SKIPPED_MODULES = [
    "intellij.platform.buildScripts.bazel",
    "intellij.tools.build.bazel.jvmIncBuilder",
    "intellij.tools.build.bazel.jvmIncBuilderTests",
]

# Custom modules with hardcoded Bazel packages and target names.
# Mirrors DEFAULT_CUSTOM_MODULES in BazelBuildFileGenerator.kt.
CUSTOM_MODULES = {
    "intellij.idea.community.build.zip": struct(
        bazel_package = "@community//build",
        target_name = "zip",
    ),
    "intellij.platform.jps.build.dependencyGraph": struct(
        bazel_package = "@community//build",
        target_name = "dependency-graph",
    ),
    "intellij.platform.jps.build.javac.rt": struct(
        bazel_package = "@community//build",
        target_name = "build-javac-rt",
    ),
}

STANDALONE_BAZEL_REPOS = [
    struct(
        repo_name = "@jps_to_bazel",
        repo_root_parts = ["community", "platform", "build-scripts", "bazel"],
    ),
    struct(
        repo_name = "@rules_jvm",
        repo_root_parts = ["community", "build", "jvm-rules"],
    ),
]

def _extract_relative_path(url):
    """Extract relative path from a $MODULE_DIR$ URL, allowing the module dir itself."""
    marker = "$MODULE_DIR$"
    idx = url.find(marker)
    if idx == -1:
        return None

    suffix = url[idx + len(marker):]
    if suffix == "" or suffix == "!/":
        return ""
    if not suffix.startswith("/"):
        return None

    suffix = suffix[1:]
    if suffix.endswith("!/"):
        suffix = suffix[:-2]
    return suffix

def parse_iml(iml_content, iml_path):
    """Parse .iml XML and extract source types, module libraries, and project library refs.

    Args:
        iml_content: raw XML content of the .iml file
        iml_path: project-relative path (for error messages)

    Returns struct with:
      - content_root_urls: list of $MODULE_DIR$-relative content root paths
      - has_production_sources: bool
      - has_test_sources: bool
      - module_libraries: list of structs with jar_urls
      - project_library_refs: list of project-level library names referenced by this module
    """
    doc = xml.parse(iml_content, strict = True)
    root = xml.get_document_element(doc)

    content_root_urls = []
    has_production = False
    has_test = False
    module_libraries = []
    project_library_refs = []

    found_nrm = False
    for component_el in xml.find_elements_by_tag_name(root, "component"):
        if xml.get_attribute(component_el, "name") != "NewModuleRootManager":
            continue
        found_nrm = True

        for child in xml.get_child_elements(component_el):
            tag = xml.get_tag_name(child)
            if tag == "content":
                url = xml.get_attribute(child, "url")
                if not url:
                    fail("<content> missing required 'url' attribute in %s" % iml_path)
                rel = _extract_relative_path(url)
                if rel == None:
                    fail("Unsupported <content> URL in %s (expected $MODULE_DIR$ path): %s" % (iml_path, url))
                content_root_urls.append(rel)

                for sf in xml.find_elements_by_tag_name(child, "sourceFolder"):
                    is_test_attr = xml.get_attribute(sf, "isTestSource")
                    sf_type = xml.get_attribute(sf, "type") or ""
                    if is_test_attr == "true" or sf_type.startswith("java-test"):
                        has_test = True
                    else:
                        has_production = True

            elif tag == "orderEntry":
                oe_type = xml.get_attribute(child, "type")
                if not oe_type:
                    fail("<orderEntry> missing required 'type' attribute in %s" % iml_path)
                if oe_type == "module-library":
                    lib_els = xml.find_elements_by_tag_name(child, "library")
                    if not lib_els:
                        fail("<orderEntry type='module-library'> missing <library> child in %s" % iml_path)
                    module_libraries.append(_parse_library_element(lib_els[0], iml_path))
                elif oe_type == "library":
                    lib_name = xml.get_attribute(child, "name")
                    lib_level = xml.get_attribute(child, "level")
                    if not lib_name:
                        fail("<orderEntry type='library'> missing required 'name' attribute in %s" % iml_path)
                    if not lib_level:
                        fail("<orderEntry type='library'> missing required 'level' attribute in %s" % iml_path)
                    if lib_level == "project":
                        project_library_refs.append(lib_name)

    if not found_nrm:
        fail("No <component name='NewModuleRootManager'> found in %s" % iml_path)

    return struct(
        content_root_urls = content_root_urls,
        has_production_sources = has_production,
        has_test_sources = has_test,
        module_libraries = module_libraries,
        project_library_refs = project_library_refs,
    )

def _parse_library_element(lib_el, iml_path):
    """Parse a <library> element from an .iml module-library, return struct with jar URLs.

    Args:
        lib_el: parsed <library> XML element
        iml_path: project-relative path of the .iml file (for error messages)
    """
    classes_els = xml.find_elements_by_tag_name(lib_el, "CLASSES")
    if not classes_els:
        fail("<library> in module-library missing <CLASSES> child in %s" % iml_path)
    urls = []
    for root_el in xml.find_elements_by_tag_name(classes_els[0], "root"):
        url = xml.get_attribute(root_el, "url")
        if not url:
            fail("<root> missing required 'url' attribute in module-library CLASSES in %s" % iml_path)
        urls.append(url)
    return struct(jar_urls = urls)

def _normalize_path_parts(parts, context):
    """Normalize path segments by resolving '..' components."""
    result = []
    for p in parts:
        if p == "." or p == "":
            continue
        elif p == "..":
            if not result:
                fail("%s resolves outside project root via '..'" % context)
            result.pop()
        else:
            result.append(p)
    return result

def compute_build_dir(iml_dir_parts, content_root_rel_paths, iml_path = None):
    """Compute the BUILD.bazel directory by walking up from iml_dir until all content roots are descendants.

    Args:
        iml_dir_parts: list of path segments for the .iml directory (relative to project root)
        content_root_rel_paths: list of $MODULE_DIR$-relative content root paths

    Returns:
        list of path segments for the BUILD directory
    """
    # Compute absolute content root paths (relative to project root), resolving ".."
    absolute_roots = []
    for rel in content_root_rel_paths:
        if rel == "" or rel == ".":
            absolute_roots.append(list(iml_dir_parts))
        else:
            raw = list(iml_dir_parts) + rel.split("/")
            context = "content root '%s'" % rel
            if iml_path != None:
                context = "%s in %s" % (context, iml_path)
            absolute_roots.append(_normalize_path_parts(raw, context))

    if not absolute_roots:
        # No content roots, use iml dir
        return list(iml_dir_parts)

    build_dir = list(iml_dir_parts)

    # Walk up until all content roots are under build_dir
    # Use bounded loop (need extra iterations since content roots may be above iml_dir)
    max_depth = len(iml_dir_parts) + 1
    for root in absolute_roots:
        if len(root) + 1 > max_depth:
            max_depth = len(root) + 1

    for _ in range(max_depth):
        if _all_start_with(absolute_roots, build_dir):
            return build_dir
        if not build_dir:
            break
        build_dir = build_dir[:-1]

    fail("Unable to find parent for all content roots above %s" % "/".join(iml_dir_parts))

def _all_start_with(paths, prefix):
    """Check if all paths start with the given prefix segments."""
    for path in paths:
        if len(path) < len(prefix):
            return False
        for i in range(len(prefix)):
            if path[i] != prefix[i]:
                return False
    return True

def _camel_to_snake_case(s, replacement = "-"):
    """CamelCase → kebab-case (or snake_case with replacement='_').

    Mirrors camelToSnakeCase in dependency.kt:
    - "JUnit..." → "junit..."
    - ALL_CAPS → lowercase
    - Otherwise: insert replacement before uppercase letters
    """
    if s.startswith("JUnit"):
        return "junit" + s[len("JUnit"):]

    all_upper = True
    for c in s.elems():
        if c.islower():
            all_upper = False
            break
    if all_upper and len(s) > 0:
        return s.lower()

    # Replace spaces and normalize some patterns
    s = s.replace(" ", "").replace("_RC", "_rc").replace("SNAPSHOT", "snapshot")

    result = []
    for i, c in enumerate(s.elems()):
        if c.isupper() and i > 0:
            result.append(replacement)
        result.append(c.lower())
    return "".join(result)

def module_name_to_target(module_name, build_dir_parts, community_root_parts, ultimate_root_parts):
    """Derive the Bazel target name from a JPS module name.

    Mirrors jpsModuleNameToBazelBuildName in BazelBuildFileGenerator.kt.

    Args:
        module_name: JPS module name (e.g., "intellij.platform.core")
        build_dir_parts: path segments of the BUILD directory relative to project root
        community_root_parts: path segments of the community root relative to project root (e.g., ["community"] or [])
        ultimate_root_parts: path segments of the ultimate root relative to project root (e.g., [] or None for community-only)
    """
    custom = CUSTOM_MODULES.get(module_name)
    if custom:
        return custom.target_name

    # Determine baseDirFilename
    is_at_root = (build_dir_parts == community_root_parts or build_dir_parts == ultimate_root_parts)
    base_dir_filename = None if is_at_root else (build_dir_parts[-1] if build_dir_parts else None)

    if base_dir_filename != None and base_dir_filename != "resources":
        if module_name.endswith("." + base_dir_filename):
            return base_dir_filename
        if _camel_to_snake_case(module_name, "-").endswith("." + base_dir_filename):
            return base_dir_filename

    # Strip known prefixes
    result = module_name
    for prefix in ["intellij.platform.", "intellij.idea.community.", "intellij."]:
        if result.startswith(prefix):
            result = result[len(prefix):]
            break

    # Compute parentDirDirName
    parent_dir_name = None
    if ultimate_root_parts != None:
        if build_dir_parts == ultimate_root_parts:
            parent_dir_name = None
        elif len(build_dir_parts) > 0 and build_dir_parts[:-1] == ultimate_root_parts:
            parent_dir_name = "idea"
        elif len(build_dir_parts) > 1:
            parent_dir_name = build_dir_parts[-2]
    else:
        # community-only mode: ultimateRoot is null in Kotlin
        # baseBuildDir == null → false
        # baseBuildDir.parent == null → false (for normal paths)
        # → falls through to baseBuildDir.parent.fileName
        if len(build_dir_parts) > 1:
            parent_dir_name = build_dir_parts[-2]

    if parent_dir_name != None:
        prefix = parent_dir_name + "."
        if result.startswith(prefix):
            result = result[len(prefix):]

    return result.replace(".", "-")

def _package_label_to_build_dir_parts(package_label, community_root_parts):
    for repo in STANDALONE_BAZEL_REPOS:
        prefix = repo.repo_name + "//"
        if package_label.startswith(prefix):
            rel_path = package_label[len(prefix):]
            return repo.repo_root_parts + (rel_path.split("/") if rel_path else [])
    if package_label.startswith("@community//"):
        rel_path = package_label[len("@community//"):]
        return community_root_parts + (rel_path.split("/") if rel_path else [])
    if package_label.startswith("//"):
        rel_path = package_label[len("//"):]
        return rel_path.split("/") if rel_path else []
    fail("Unsupported Bazel package label: %s" % package_label)

def _compute_package_info(module_name, build_dir_parts, is_community, community_root_parts):
    custom = CUSTOM_MODULES.get(module_name)
    if custom:
        return struct(
            package_prefix = custom.bazel_package,
            effective_build_dir_parts = _package_label_to_build_dir_parts(custom.bazel_package, community_root_parts),
        )

    if community_root_parts and is_community:
        for repo in STANDALONE_BAZEL_REPOS:
            if _all_start_with([build_dir_parts], repo.repo_root_parts):
                rel_parts = build_dir_parts[len(repo.repo_root_parts):]
                rel_path = "/".join(rel_parts)
                return struct(
                    package_prefix = repo.repo_name + "//" + rel_path,
                    effective_build_dir_parts = build_dir_parts,
                )

    if is_community:
        rel_parts = build_dir_parts[len(community_root_parts):]
        rel_path = "/".join(rel_parts)
        return struct(
            package_prefix = "@community//" + rel_path,
            effective_build_dir_parts = build_dir_parts,
        )

    rel_path = "/".join(build_dir_parts)
    return struct(
        package_prefix = "//" + rel_path,
        effective_build_dir_parts = build_dir_parts,
    )

def compute_iml_target(module_name, build_dir_parts, iml_rel_path, is_community, community_root_parts):
    """Compute the Bazel file label for a module's .iml file."""
    package_info = _compute_package_info(module_name, build_dir_parts, is_community, community_root_parts)
    iml_parts = iml_rel_path.split("/") if iml_rel_path else []
    effective_build_dir_parts = package_info.effective_build_dir_parts

    if not _all_start_with([iml_parts], effective_build_dir_parts):
        fail(
            "IML path for module '%s' is not under Bazel package '%s': %s" % (
                module_name,
                package_info.package_prefix,
                iml_rel_path,
            ),
        )

    relative_iml_parts = iml_parts[len(effective_build_dir_parts):]
    return package_info.package_prefix + ":" + "/".join(relative_iml_parts)

def compute_module_targets(module_name, build_dir_parts, target_name, is_community, community_root_parts):
    """Compute production and test target labels for a module.

    Returns struct with production (list) and test (list) target labels.
    """
    package_prefix = _compute_package_info(module_name, build_dir_parts, is_community, community_root_parts).package_prefix

    # Always use colon form — the JSON output always uses "$packagePrefix:$targetName"
    label = package_prefix + ":" + target_name

    production = [label + ".jar"]

    # BazelBuildFileGenerator.generateBuildTargets always emits a test_lib target for every module.
    # Some modules use that target as a test classpath container even when they have no test sources.
    test = [package_prefix + ":" + target_name + "_test_lib.jar"]

    return struct(production = production, test = test)
