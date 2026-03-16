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
