load("@xml.bzl//:xml.bzl", "xml")

_PLUGIN_XML_MARKER_PREFIX = "<!-- BUILD_USING_BAZEL_MARKER"

def _parse_project_relative_module_paths_strict(modules_xml_text):
    marker = 'filepath="'
    prefix = "$PROJECT_DIR$/"

    rels = []
    i = 0

    # Upper bound: you can't have more attributes than characters.
    for _ in range(len(modules_xml_text) + 1):
        j = modules_xml_text.find(marker, i)
        if j == -1:
            break

        j += len(marker)
        k = modules_xml_text.find('"', j)
        if k == -1:
            fail("Malformed XML: unterminated filepath attribute (missing closing quote)")

        full = modules_xml_text[j:k]
        if not full.startswith(prefix):
            fail("Unexpected module filepath (expected to start with %r): %r" % (prefix, full))

        rels.append(full[len(prefix):])
        i = k + 1

    if not rels:
        fail("No module filepaths found")

    return rels

def _normalize_project_relative_path(parts, context):
    result = []
    for part in parts:
        if part == "" or part == ".":
            continue
        if part == "..":
            if not result:
                fail("%s resolves outside the project root" % context)
            result.pop()
        else:
            result.append(part)
    return "/".join(result)

def _production_resource_roots(iml_content, iml_rel_path, iml_dir_rel):
    """The project-relative path of every production resource root an .iml declares."""
    doc = xml.parse(iml_content, strict = True)
    root = xml.get_document_element(doc)

    roots = []
    for source_folder in xml.find_elements_by_tag_name(root, "sourceFolder"):
        if xml.get_attribute(source_folder, "type") != "java-resource":
            continue

        url = xml.get_attribute(source_folder, "url")
        if not url:
            fail("Production resource root is missing the 'url' attribute in %s" % iml_rel_path)

        prefix = "file://$MODULE_DIR$"
        if not url.startswith(prefix):
            fail("Unsupported production resource root URL in %s (expected $MODULE_DIR$ path): %s" % (iml_rel_path, url))

        resource_root_rel = url[len(prefix):]
        if resource_root_rel.startswith("/"):
            resource_root_rel = resource_root_rel[1:]
        elif resource_root_rel:
            fail("Unsupported production resource root URL in %s (expected $MODULE_DIR$ path): %s" % (iml_rel_path, url))

        parts = iml_dir_rel.split("/") if iml_dir_rel else []
        if resource_root_rel:
            parts += resource_root_rel.split("/")
        roots.append(_normalize_project_relative_path(
            parts,
            "Production resource root '%s' in %s" % (url, iml_rel_path),
        ))
    return roots

def _find_plugin_xml_rel_path(ctx, project_root, resource_roots):
    for resource_root in resource_roots:
        plugin_xml_rel_path = _join_project_relative_path(resource_root, "META-INF/plugin.xml")
        plugin_xml_path = project_root.get_child(plugin_xml_rel_path)
        if not plugin_xml_path.exists:
            continue

        plugin_xml_content = ctx.read(plugin_xml_path, watch = "yes")
        first_line = plugin_xml_content.split("\n")[0]
        if first_line.startswith(_PLUGIN_XML_MARKER_PREFIX):
            return plugin_xml_rel_path

    return None

def _join_project_relative_path(directory, relative):
    return directory + "/" + relative if directory else relative

def _find_descriptor_rel_paths(project_root, module_name, resource_roots):
    """The descriptors a dev-distribution assembly reads that the module name alone is enough to name.

    A content module's descriptor is `<moduleName>.xml` at a production resource root, and a plugin's descriptor is
    `META-INF/plugin.xml`. Almost every descriptor follows that convention; `build/dev_dist_plan.bzl` carries only
    what it cannot predict - descriptors reached through an `xi:include`, whose names the product model knows and
    this side does not. Splitting it that way keeps the generated file ~120 lines instead of ~3300, which matters
    for a file every model change would otherwise rewrite under a large team.

    Probed rather than listed: `readdir` would watch each resource root's listing, so adding any file under one
    would re-run this repository rule.
    """
    result = []
    for resource_root in resource_roots:
        for candidate in [module_name + ".xml", "META-INF/plugin.xml"]:
            rel_path = _join_project_relative_path(resource_root, candidate)
            if project_root.get_child(rel_path).exists:
                result.append(rel_path)
    return result

def watch_project_model_files(ctx, project_root):
    idea_dir = project_root.get_child(".idea")
    modules_xml = idea_dir.get_child("modules.xml")

    # Read (to make sure it exists) and watch all modules to re-run generator on changes
    for relativeModulePath in _parse_project_relative_module_paths_strict(ctx.read(modules_xml, watch = "yes")):
        ctx.read(project_root.get_child(relativeModulePath), watch = "yes")

    # Read (to make sure it exists) and watch all libraries under .idea/libraries to re-run generator on changes
    libraries_dir = idea_dir.get_child("libraries")
    for library_xml in libraries_dir.readdir(watch = "yes"):
        ctx.read(library_xml, watch = "yes")

def read_project_model(ctx, project_root):
    """Read project model files and return their contents for Starlark-based derivation.

    Watches all files for invalidation (same as watch_project_model_files) but also
    returns the file contents for processing.

    Returns struct with:
      - modules: list of structs (module_name, iml_dir_rel, iml_content, iml_rel_path, plugin_xml_rel_path,
        descriptor_rel_paths)
      - library_xmls: list of structs (xml_content, xml_rel_path) from .idea/libraries/
    """
    idea_dir = project_root.get_child(".idea")
    modules_xml = idea_dir.get_child("modules.xml")
    modules_xml_text = ctx.read(modules_xml, watch = "yes")
    iml_paths = _parse_project_relative_module_paths_strict(modules_xml_text)

    modules = []
    for rel_path in iml_paths:
        iml_content = ctx.read(project_root.get_child(rel_path), watch = "yes")

        # Extract module name from filename: "path/to/intellij.platform.core.iml" → "intellij.platform.core"
        filename = rel_path.split("/")[-1]
        if filename.endswith(".iml"):
            module_name = filename[:-4]
        else:
            fail("module filename must end with `.iml`: " + filename)

        # iml directory relative to project root
        last_slash = rel_path.rfind("/")
        if last_slash != -1:
            iml_dir_rel = rel_path[:last_slash]
        else:
            iml_dir_rel = ""

        resource_roots = _production_resource_roots(
            iml_content = iml_content,
            iml_rel_path = rel_path,
            iml_dir_rel = iml_dir_rel,
        )

        modules.append(struct(
            module_name = module_name,
            iml_dir_rel = iml_dir_rel,
            iml_content = iml_content,
            iml_rel_path = rel_path,
            plugin_xml_rel_path = _find_plugin_xml_rel_path(ctx = ctx, project_root = project_root, resource_roots = resource_roots),
            descriptor_rel_paths = _find_descriptor_rel_paths(
                project_root = project_root,
                module_name = module_name,
                resource_roots = resource_roots,
            ),
        ))

    # Read library XMLs and retain relative paths for error messages.
    library_xmls = []
    libraries_dir = idea_dir.get_child("libraries")
    for library_xml_path in libraries_dir.readdir(watch = "yes"):
        library_xmls.append(struct(
            xml_content = ctx.read(library_xml_path, watch = "yes"),
            xml_rel_path = ".idea/libraries/" + str(library_xml_path).split("/")[-1],
        ))

    return struct(
        modules = modules,
        library_xmls = library_xmls,
    )
