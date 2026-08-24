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

def _module_dir_relative_project_path(url, iml_rel_path, iml_dir_rel, root_type):
    """A `file://$MODULE_DIR$/...` root URL as a project-relative path."""
    prefix = "file://$MODULE_DIR$"
    if not url.startswith(prefix):
        fail("Unsupported %s root URL in %s (expected $MODULE_DIR$ path): %s" % (root_type, iml_rel_path, url))

    root_rel = url[len(prefix):]
    if root_rel.startswith("/"):
        root_rel = root_rel[1:]
    elif root_rel:
        fail("Unsupported %s root URL in %s (expected $MODULE_DIR$ path): %s" % (root_type, iml_rel_path, url))

    parts = iml_dir_rel.split("/") if iml_dir_rel else []
    if root_rel:
        parts += root_rel.split("/")
    return _normalize_project_relative_path(parts, "The %s root '%s' in %s" % (root_type, url, iml_rel_path))

def _iml_roots(iml_content, iml_rel_path, iml_dir_rel):
    """The project-relative resource roots an .iml declares, split by scope, plus its first content root.

    All of it comes out of one parse: this runs once per module in the repository, and a second `xml.parse` of the
    same .iml just to find the test roots would double the cost of the whole model read.

    A generated test plugin keeps its descriptor under a test root - `intellij.lambda.test.plugin` is a module with
    no production output at all, only `resources/META-INF/plugin.xml` marked `java-test-resource`.

    The first content root is where a plugin's content report lives, and only the first: that is the rule the report
    writer follows (`contentChecker.kt`) and the rule the converter reads it back with
    (`pluginContentReportFile` in `pluginContent.kt`).
    """
    doc = xml.parse(iml_content, strict = True)
    root = xml.get_document_element(doc)

    first_content_root = None
    for content in xml.find_elements_by_tag_name(root, "content"):
        url = xml.get_attribute(content, "url")
        if not url:
            fail("A content root is missing the 'url' attribute in %s" % iml_rel_path)
        first_content_root = _module_dir_relative_project_path(
            url = url,
            iml_rel_path = iml_rel_path,
            iml_dir_rel = iml_dir_rel,
            root_type = "content",
        )
        break

    production = []
    test = []
    for source_folder in xml.find_elements_by_tag_name(root, "sourceFolder"):
        root_type = xml.get_attribute(source_folder, "type")
        if root_type == "java-resource":
            target = production
        elif root_type == "java-test-resource":
            target = test
        else:
            continue

        url = xml.get_attribute(source_folder, "url")
        if not url:
            fail("A %s root is missing the 'url' attribute in %s" % (root_type, iml_rel_path))

        target.append(_module_dir_relative_project_path(
            url = url,
            iml_rel_path = iml_rel_path,
            iml_dir_rel = iml_dir_rel,
            root_type = root_type,
        ))
    return struct(production = production, test = test, first_content_root = first_content_root)

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

_TEST_DESCRIPTOR_SUFFIX = "._test"

def _find_test_plugin_modules(ctx, project_root, test_resource_roots):
    """Every JPS module a generated test plugin's descriptor names, or `None` when this module owns no such descriptor.

    A dev-distribution fragment lays out the whole plugin, so declaring only the descriptor module's own jar is not
    enough - it needs the jar of every module the plugin packs. That set is written in the descriptor, and the
    descriptor is generated from the `testPlugin { }` block and checked in, so reading it here is reading the same
    answer the assembly reads. Nothing has to name these modules in a generated plan.

    Probed, never listed: the plugin.xml of a test plugin sits at `META-INF/plugin.xml` under a test resource root,
    exactly the way a production plugin's does under a production one.
    """
    for resource_root in test_resource_roots:
        rel_path = _join_project_relative_path(resource_root, "META-INF/plugin.xml")
        descriptor_path = project_root.get_child(rel_path)
        if not descriptor_path.exists:
            continue
        return _parse_test_plugin_modules(ctx.read(descriptor_path, watch = "yes"), rel_path)

    return None

def _parse_test_plugin_modules(descriptor_content, descriptor_rel_path):
    doc = xml.parse(descriptor_content, strict = True)
    root = xml.get_document_element(doc)

    names = {}

    # `<content>` only. A fragment needs the jar of every module the plugin *packs*, and that is what a content
    # module is; `<dependencies>` names modules the platform or another plugin owns and packs, and declaring those
    # here doubled what this plugin asks for - 224 labels to 436 for `intellij.lambda.test.plugin`, whose
    # `<dependencies>` block is 58 platform modules. The layout still resolves them, from the shared project model
    # tree rather than from a jar.
    for container in xml.find_elements_by_tag_name(root, "content"):
        for module_element in xml.find_elements_by_tag_name(container, "module"):
            name = xml.get_attribute(module_element, "name")
            if not name:
                fail("<module> is missing the 'name' attribute in %s" % descriptor_rel_path)
            names[_normalize_content_module_name(name)] = True

    return sorted(names.keys())

def _normalize_content_module_name(name):
    """Descriptor vocabulary to JPS module name.

    `<name>/<loading>` is one module with a loading mode, and `._test` is a synthetic name for a module's test
    output. Neither exists on the Bazel side, which resolves plain JPS module names to output labels.
    """
    slash = name.rfind("/")
    if slash != -1:
        name = name[:slash]
    if name.endswith(_TEST_DESCRIPTOR_SUFFIX):
        name = name[:-len(_TEST_DESCRIPTOR_SUFFIX)]
    return name

def _join_project_relative_path(directory, relative):
    return directory + "/" + relative if directory else relative

def _find_descriptor_rel_paths(project_root, module_name, resource_roots, extra_rel_paths):
    """Every descriptor a dev-distribution assembly reads out of this module's production resources.

    A content module's descriptor is `<moduleName>.xml` at a production resource root, and a plugin's descriptor is
    `META-INF/plugin.xml`. Almost every descriptor follows that convention, and it is derived here.
    [extra_rel_paths] is the remainder, named by the product model in `build/dev_dist_descriptors.bzl`: descriptors reached
    only through an `xi:include`, whose names this side cannot predict. Splitting it that way keeps the generated
    file ~300 lines instead of ~3300, which matters for a file every model change would otherwise rewrite under a
    large team.

    Probed rather than listed: `readdir` would watch each resource root's listing, so adding any file under one
    would re-run this repository rule. The listed paths are probed too, and a miss is silently dropped rather than
    an error - a stale entry then costs the pinned module jar the assembly reads that descriptor from today, and
    nothing else. Failing here would be far worse: this runs during module-extension evaluation, so it would make
    the very tool that regenerates the plan unbuildable. Staleness is caught by the blocking `model-generation`
    validation instead.
    """
    result = []
    for resource_root in resource_roots:
        for candidate in [module_name + ".xml", "META-INF/plugin.xml"]:
            rel_path = _join_project_relative_path(resource_root, candidate)
            if project_root.get_child(rel_path).exists:
                result.append(rel_path)
    for rel_path in extra_rel_paths:
        if project_root.get_child(rel_path).exists:
            result.append(rel_path)
    return result

_PLUGIN_CONTENT_REPORT_FILE_NAME = "plugin-content.yaml"

def _find_plugin_content_report_rel_path(project_root, first_content_root):
    """The `plugin-content.yaml` of the plugin this module is the main module of, or `None`.

    The report is what a plugin's dev-distribution content target is generated from, and only the converter reads it -
    but the hermetic `bazel-targets.json` run loads its project model from a tree materialized out of declared labels,
    so a report nobody names is a report that run cannot see, and its `contentTarget` would silently differ from the
    full-checkout run's. Naming it here is what puts it in that tree.

    Existence only, deliberately: whether a report yields a content target depends on what is written in it, this side
    cannot parse YAML, and it does not have to - `JpsModuleToBazelTargetsOnly` asserts that the two sides pick out the
    same set of files, and the one converter then decides the rest. Probed rather than listed, for the reason
    [_find_descriptor_rel_paths] gives.
    """
    if first_content_root == None:
        return None
    rel_path = _join_project_relative_path(first_content_root, _PLUGIN_CONTENT_REPORT_FILE_NAME)
    return rel_path if project_root.get_child(rel_path).exists else None

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

def read_project_model(ctx, project_root, extra_descriptor_rel_paths_by_module = {}, collect_test_plugin_modules = False):
    """Read project model files and return their contents for Starlark-based derivation.

    Watches all files for invalidation (same as watch_project_model_files) but also
    returns the file contents for processing.

    Args:
        ctx: repository rule context.
        project_root: the repository root to read the model from.
        extra_descriptor_rel_paths_by_module: module name to the project-relative descriptor paths the convention
            in [_find_descriptor_rel_paths] cannot derive, from `build/dev_dist_descriptors.bzl`. Only the Ultimate
            repository rule passes it: the list is an Ultimate artifact, community has to build standalone without
            it, and no community target materializes a project model tree.
        collect_test_plugin_modules: whether to probe test resource roots for a generated test plugin's descriptor
            and parse the modules it names. Off by default so a community build pays neither the probe nor the
            parse; only the Ultimate repository rule needs it, to declare a dev fragment's inputs for a test
            plugin an `additional_modules` names.

    Returns struct with:
      - modules: list of structs (module_name, iml_dir_rel, iml_content, iml_rel_path, plugin_xml_rel_path,
        descriptor_rel_paths, plugin_content_report_rel_path, test_plugin_modules)
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

        iml_roots = _iml_roots(
            iml_content = iml_content,
            iml_rel_path = rel_path,
            iml_dir_rel = iml_dir_rel,
        )
        resource_roots = iml_roots.production

        test_plugin_modules = None
        if collect_test_plugin_modules:
            test_plugin_modules = _find_test_plugin_modules(
                ctx = ctx,
                project_root = project_root,
                test_resource_roots = iml_roots.test,
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
                extra_rel_paths = extra_descriptor_rel_paths_by_module.get(module_name, []),
            ),
            plugin_content_report_rel_path = _find_plugin_content_report_rel_path(
                project_root = project_root,
                first_content_root = iml_roots.first_content_root,
            ),
            test_plugin_modules = test_plugin_modules,
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
