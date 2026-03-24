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

def watch_project_model_files(ctx, project_root):
    idea_dir = project_root.get_child(".idea")
    modules_xml = idea_dir.get_child("modules.xml")

    # Read (to make sure it exists) and watch all modules to re-run generator on changes
    for relativeModulePath in _parse_project_relative_module_paths_strict(ctx.read(modules_xml, watch='yes')):
        ctx.read(project_root.get_child(relativeModulePath), watch='yes')

    # Read (to make sure it exists) and watch all libraries under .idea/libraries to re-run generator on changes
    libraries_dir = idea_dir.get_child("libraries")
    for library_xml in libraries_dir.readdir(watch='yes'):
        ctx.read(library_xml, watch='yes')

def read_project_model(ctx, project_root):
    """Read project model files and return their contents for Starlark-based derivation.

    Watches all files for invalidation (same as watch_project_model_files) but also
    returns the file contents for processing.

    Returns struct with:
      - modules: list of structs (module_name, iml_dir_rel, iml_content, iml_rel_path)
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

        modules.append(struct(
            module_name = module_name,
            iml_dir_rel = iml_dir_rel,
            iml_content = iml_content,
            iml_rel_path = rel_path,
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
