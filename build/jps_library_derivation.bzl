"""
Pure Starlark derivation of library jar targets from .idea/libraries/*.xml and .iml module libraries.

Mirrors the jar target generation logic in:
  - JpsModuleToBazel.kt:277  makeJarTarget()     — Maven jar → Bazel label
  - JpsModuleToBazel.kt:340  toBazelLabel()       — local jar → Bazel label
  - JpsModuleToBazel.kt:328  jarTargets mapping   — LocalLibrary files → labels
  - lib.kt:325               mavenCoordinatesToFileName()
  - dependency.kt:234-248    prefix selection for local library deps
"""

load("@xml.bzl//:xml.bzl", "xml")

def maven_url_to_jar_target(url, repo):
    """Convert a $MAVEN_REPOSITORY$ jar URL to a Bazel jar target label.

    Mirrors makeJarTarget() in JpsModuleToBazel.kt:277-281:
      library.target.container.repoLabel + "//:" + mavenCoordinatesToFileName(coords, groupDirectory=true)

    mavenCoordinatesToFileName (lib.kt:325) with groupDirectory=true produces:
      groupId + "/" + artifactId + "-" + version [+ "-" + classifier] + packaging

    We extract groupId and filename from the standard Maven repo URL layout:
      $MAVEN_REPOSITORY$/<group_path>/<artifact>/<version>/<filename>

    Args:
        url: full URL containing $MAVEN_REPOSITORY$
        repo: repository label (e.g., "@lib" or "@ultimate_lib")

    Returns:
        Bazel label like "@lib//:org.tukaani/xz-1.10.jar"
    """
    marker = "$MAVEN_REPOSITORY$/"
    idx = url.find(marker)
    if idx == -1:
        fail("Expected $MAVEN_REPOSITORY$ in URL: %s" % url)

    path = url[idx + len(marker):]

    # Strip trailing "!/" from jar:// URLs
    if path.endswith("!/"):
        path = path[:-2]

    parts = path.split("/")
    if len(parts) < 4:
        fail("Maven URL path too short (expected group/artifact/version/file): %s" % path)

    filename = parts[-1]
    group_dirs = parts[:-3]
    group_id = ".".join(group_dirs)

    return repo + "//:" + group_id + "/" + filename

def local_path_to_jar_target(rel_path, is_community_only, community_root_rel):
    """Convert a project-relative path to a Bazel jar target label.

    Mirrors toBazelLabel() in JpsModuleToBazel.kt:340-356 and the prefix
    selection in dependency.kt:234-248.

    Priority order (same as Kotlin):
      1. Under ultimate lib root (lib/)          → @ultimate_lib//<parent>:<file>
      2. Under community lib root                → @lib//<parent>:<file>
      3. Under community root (ultimate mode)    → @community//<parent>:<file>
      4. Otherwise (project root)                → //<parent>:<file>

    For community-only mode, community root IS the project root, so:
      1. Under lib/                              → @lib//<parent>:<file>
      2. Otherwise                               → //<parent>:<file>

    Args:
        rel_path: project-root-relative path (after stripping $PROJECT_DIR$/)
        is_community_only: True if community-only mode (no ultimate root)
        community_root_rel: relative path of community root from project root
                            (e.g., "community" for ultimate, "" for community-only)

    Returns:
        Bazel label string
    """
    if is_community_only:
        # Community-only: project root IS community root
        lib_prefix = "lib/"
        if rel_path.startswith(lib_prefix):
            lib_rel = rel_path[len(lib_prefix):]
            return _path_to_label("@lib", lib_rel)
        else:
            return _path_to_label("", rel_path)
    else:
        # Ultimate mode: community is a subdirectory
        community_lib_prefix = community_root_rel + "/lib/" if community_root_rel else "lib/"
        ultimate_lib_prefix = "lib/"

        if rel_path.startswith(community_lib_prefix):
            lib_rel = rel_path[len(community_lib_prefix):]
            return _path_to_label("@lib", lib_rel)
        elif rel_path.startswith(ultimate_lib_prefix):
            lib_rel = rel_path[len(ultimate_lib_prefix):]
            return _path_to_label("@ultimate_lib", lib_rel)
        elif community_root_rel and rel_path.startswith(community_root_rel + "/"):
            comm_rel = rel_path[len(community_root_rel) + 1:]
            return _path_to_label("@community", comm_rel)
        else:
            return _path_to_label("", rel_path)

def _path_to_label(repo, rel_path):
    """Convert repo + relative path to a Bazel label.

    Mirrors Path.toBazelLabel() in JpsModuleToBazel.kt:340-346:
      "$repoName//${parent ?: ""}:${fileName}"

    Args:
        repo: repository label (e.g., "@lib", "@ultimate_lib", "@community", "")
        rel_path: path relative to the repo root (e.g., "ant/lib/ant.jar")

    Returns:
        Bazel label like "@lib//ant/lib:ant.jar"
    """
    last_slash = rel_path.rfind("/")
    if last_slash == -1:
        return repo + "//:" + rel_path
    else:
        package_path = rel_path[:last_slash]
        filename = rel_path[last_slash + 1:]
        return repo + "//" + package_path + ":" + filename

def _extract_project_relative_path(url):
    """Extract project-relative path from a $PROJECT_DIR$ URL.

    Handles both jar:// and file:// URL schemes.

    Args:
        url: URL containing $PROJECT_DIR$

    Returns:
        project-relative path string, or None if not a $PROJECT_DIR$ URL
    """
    marker = "$PROJECT_DIR$"
    idx = url.find(marker)
    if idx == -1:
        return None

    rel_path = url[idx + len(marker):]
    if rel_path == "" or rel_path == "!/":
        return ""
    if not rel_path.startswith("/"):
        fail("Malformed $PROJECT_DIR$ URL (expected '/' after macro): %s" % url)
    rel_path = rel_path[1:]

    # Strip trailing "!/" from jar:// URLs
    if rel_path.endswith("!/"):
        rel_path = rel_path[:-2]

    return rel_path

def _normalize_path(path, context):
    """Normalize a path by resolving '..' components."""
    parts = path.split("/")
    result = []
    for p in parts:
        if p == "." or p == "":
            continue
        elif p == "..":
            if not result:
                fail("%s resolves outside project root via '..': %s" % (context, path))
            result.pop()
        else:
            result.append(p)
    return "/".join(result)

def _resolve_module_dir_path(url, iml_dir_rel, iml_path):
    """Resolve a $MODULE_DIR$ URL to a project-relative path.

    Module-level libraries in .iml files use $MODULE_DIR$-relative paths
    (e.g., jar://$MODULE_DIR$/lib/saxon.jar!/ or jar://$MODULE_DIR$/../lib/foo.jar!/).
    JPS resolves these internally via JpsPathUtil.urlToNioPath().

    Args:
        url: URL containing $MODULE_DIR$
        iml_dir_rel: project-relative directory of the .iml file

    Returns:
        normalized project-relative path, or None if not a $MODULE_DIR$ URL
    """
    marker = "$MODULE_DIR$/"
    idx = url.find(marker)
    if idx == -1:
        # Also handle $MODULE_DIR$ at the end (directory itself)
        if "$MODULE_DIR$" in url:
            suffix = url[url.find("$MODULE_DIR$") + len("$MODULE_DIR$"):]
            if suffix == "" or suffix == "!/":
                return iml_dir_rel
            fail("Unsupported $MODULE_DIR$ URL in %s: %s" % (iml_path, url))
        return None

    rel_to_module = url[idx + len(marker):]

    # Strip trailing "!/" from jar:// URLs
    if rel_to_module.endswith("!/"):
        rel_to_module = rel_to_module[:-2]

    # Combine with iml directory and normalize (resolve "..")
    if iml_dir_rel:
        full_path = iml_dir_rel + "/" + rel_to_module
    else:
        full_path = rel_to_module

    return _normalize_path(full_path, "module-library URL in %s" % iml_path)

def _get_library_element(lib_xml_content, lib_xml_path):
    """Parse library XML and return the top-level <library> element."""
    doc = xml.parse(lib_xml_content, strict = True)
    lib_els = xml.find_elements_by_tag_name(doc, "library")
    if not lib_els:
        fail("No <library> element found in %s" % lib_xml_path)
    return lib_els[0]

def _get_library_name(lib_el, lib_xml_path):
    """Read the required library name attribute from a parsed <library> element."""
    lib_name = xml.get_attribute(lib_el, "name")
    if not lib_name:
        fail("<library> missing required 'name' attribute in %s" % lib_xml_path)
    return lib_name

def _parse_library_element(lib_el, lib_xml_path):
    """Parse a .idea/libraries/*.xml file in a single pass.

    Extracts the library name and all jar URL references from one XML parse.

    Returns struct with:
      - name: library name from <library name="..."> attribute
      - maven_urls: list of $MAVEN_REPOSITORY$ URLs (for Maven jar targets)
      - local_urls: list of $PROJECT_DIR$ URLs (for local jar targets, non-directory)
      - jar_directory_urls: list of $PROJECT_DIR$ directory URLs (need expansion via ctx)
      - unsupported_urls: list of CLASSES root URLs that this derivation does not understand
    """
    lib_name = _get_library_name(lib_el, lib_xml_path)

    classes_els = xml.find_elements_by_tag_name(lib_el, "CLASSES")
    if not classes_els:
        # No CLASSES is valid for source-only / javadoc-only libraries
        return struct(name = lib_name, maven_urls = [], local_urls = [], jar_directory_urls = [])

    # Collect jar directory URLs for expansion
    jar_dir_url_set = {}
    for jd in xml.find_elements_by_tag_name(lib_el, "jarDirectory"):
        jd_url = xml.get_attribute(jd, "url")
        if not jd_url:
            fail("<jarDirectory> missing required 'url' attribute in library '%s' (%s)" % (lib_name, lib_xml_path))
        jar_dir_url_set[jd_url] = True

    maven_urls = []
    local_urls = []
    jar_directory_urls = []
    unsupported_urls = []

    for root_el in xml.find_elements_by_tag_name(classes_els[0], "root"):
        url = xml.get_attribute(root_el, "url")
        if not url:
            fail("<root> missing required 'url' attribute in CLASSES in library '%s' (%s)" % (lib_name, lib_xml_path))

        if "$MAVEN_REPOSITORY$" in url:
            maven_urls.append(url)
        elif "$PROJECT_DIR$" in url:
            # Check if this root URL matches a jarDirectory URL
            # jarDirectory URLs use file:// scheme, CLASSES roots may use jar:// or file://
            if url in jar_dir_url_set:
                jar_directory_urls.append(url)
            else:
                local_urls.append(url)
        else:
            unsupported_urls.append(url)

    return struct(
        name = lib_name,
        maven_urls = maven_urls,
        local_urls = local_urls,
        jar_directory_urls = jar_directory_urls,
        unsupported_urls = unsupported_urls,
    )

def _expand_jar_directory(ctx, project_root, url, context):
    """Expand a jar directory URL to individual jar file paths.

    Uses ctx.path().readdir() to list jar files, mirroring JPS's
    JpsOrderRootType.COMPILED resolution of <jarDirectory> elements.

    Args:
        ctx: repository rule context (for filesystem access)
        project_root: Path to the project root
        url: file:// URL with $PROJECT_DIR$

    Returns:
        list of project-relative jar file paths
    """
    rel_path = _extract_project_relative_path(url)
    if rel_path == None:
        fail("Expected $PROJECT_DIR$ in %s: %s" % (context, url))
    rel_path = _normalize_path(rel_path, context)

    dir_path = project_root.get_child(rel_path)
    jar_files = []
    for entry in dir_path.readdir():
        name = str(entry).split("/")[-1]
        if name.endswith(".jar"):
            jar_files.append(rel_path + "/" + name)

    return sorted(jar_files)

def derive_library_targets(ctx, project_root, library_xmls, iml_data_list,
                           is_community_only, community_root_rel):
    """Derive all library jar targets from project and module libraries.

    This is the main entry point called from repo rule implementations.
    It needs ctx for jar directory expansion via filesystem access.

    Args:
        ctx: repository rule context
        project_root: Path to the project root
        library_xmls: list of structs with (xml_content, xml_rel_path) from .idea/libraries/
        iml_data_list: list of structs with (module_name, iml_dir_rel, iml_rel_path, is_community, parsed_iml)
        is_community_only: whether in community-only mode
        community_root_rel: community root relative to project root ("community" or "")

    Returns:
        sorted list of unique jar target labels
    """
    targets = {}  # dict used as set

    # Repo for project-level Maven libraries:
    # In community-only mode, all project libs use @lib.
    # In ultimate mode, we'd need to know which module first references each library
    # to determine @lib vs @ultimate_lib. For now, we determine this from module refs.
    # See dependency.kt:281 — container = context.getLibraryContainer(module.isCommunity)
    project_lib_repo = "@lib" if is_community_only else None  # None = needs per-library determination

    # Build set of all referenced project-level library names.
    # The converter only includes libraries that are actually referenced by modules
    # (via jpsLibrary.getPaths()), so we must filter to match.
    all_referenced_libs = {}
    lib_community_status = {}
    for iml_data in iml_data_list:
        for lib_ref in iml_data.parsed_iml.project_library_refs:
            all_referenced_libs[lib_ref] = True
            # For ultimate mode: track whether any community module references each lib
            if not is_community_only and iml_data.is_community:
                lib_community_status[lib_ref] = True

    # Project-level libraries: parse XML once to discover the library name, and
    # only validate CLASSES roots for libraries actually referenced from .iml files.
    for lib_xml in library_xmls:
        lib_el = _get_library_element(lib_xml.xml_content, lib_xml.xml_rel_path)
        lib_name = _get_library_name(lib_el, lib_xml.xml_rel_path)
        if lib_name not in all_referenced_libs:
            continue

        parsed = _parse_library_element(lib_el, lib_xml.xml_rel_path)

        if parsed.unsupported_urls:
            fail("Unsupported CLASSES root URL in library '%s' (%s): %s" % (parsed.name, lib_xml.xml_rel_path, parsed.unsupported_urls[0]))

        # Determine repo for Maven URLs
        if project_lib_repo != None:
            repo = project_lib_repo
        elif lib_name in lib_community_status:
            repo = "@lib"
        else:
            repo = "@ultimate_lib"

        for url in parsed.maven_urls:
            target = maven_url_to_jar_target(url, repo)
            targets[target] = True

        for url in parsed.local_urls:
            rel_path = _extract_project_relative_path(url)
            if rel_path == None:
                fail("Expected $PROJECT_DIR$ in local library URL: %s (library='%s')" %
                     (url, parsed.name))
            rel_path = _normalize_path(rel_path, "local library URL in %s" % lib_xml.xml_rel_path)
            target = local_path_to_jar_target(rel_path, is_community_only, community_root_rel)
            targets[target] = True

        # Expand jar directories
        for url in parsed.jar_directory_urls:
            jar_paths = _expand_jar_directory(ctx, project_root, url, "jarDirectory in %s" % lib_xml.xml_rel_path)
            for jar_path in jar_paths:
                target = local_path_to_jar_target(jar_path, is_community_only, community_root_rel)
                targets[target] = True

    # Module-level libraries (from .iml files)
    # Container determined by module's is_community status.
    # See dependency.kt:281 — getLibraryContainer(module.isCommunity)
    # JPS resolves $MODULE_DIR$ via JpsPathUtil.urlToNioPath() (dependency.kt:427).
    for iml_data in iml_data_list:
        module_repo = "@lib" if (is_community_only or iml_data.is_community) else "@ultimate_lib"

        for module_lib in iml_data.parsed_iml.module_libraries:
            for url in module_lib.jar_urls:
                if "$MAVEN_REPOSITORY$" in url:
                    target = maven_url_to_jar_target(url, module_repo)
                    targets[target] = True
                elif "$PROJECT_DIR$" in url:
                    rel_path = _extract_project_relative_path(url)
                    if rel_path == None:
                        fail("Expected $PROJECT_DIR$ in module library URL: %s (module=%s)" %
                             (url, iml_data.module_name))
                    rel_path = _normalize_path(rel_path, "module-library URL in %s" % iml_data.iml_rel_path)
                    target = local_path_to_jar_target(rel_path, is_community_only, community_root_rel)
                    targets[target] = True
                elif "$MODULE_DIR$" in url:
                    rel_path = _resolve_module_dir_path(url, iml_data.iml_dir_rel, iml_data.iml_rel_path)
                    if rel_path == None:
                        fail("Failed to resolve $MODULE_DIR$ in module library URL: %s (module=%s)" %
                             (url, iml_data.module_name))
                    target = local_path_to_jar_target(rel_path, is_community_only, community_root_rel)
                    targets[target] = True
                else:
                    fail("Unsupported module-library CLASSES root in %s (module=%s): %s" %
                         (iml_data.iml_rel_path, iml_data.module_name, url))

    return sorted(targets.keys())
