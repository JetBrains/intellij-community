"""The record of a JPS module in its package, and the groups that the `jps_modules` repository builds from the records.

The JPS-to-Bazel converter writes one `jps_module` call into the `iml <module>` section of each module. The call states
only the facts that the convention does not give. The `jps_modules` repository rule reads the module list from
`.idea/modules.xml` and names the targets of each record by the module name and the package of its `.iml` file.

Each kind of group has its own target in the record. A dependency edge makes Bazel analyze the target that it names, so
the project model files do not reach a `jvm_library`, and the jars do not reach an `ij_plugin`.
"""

visibility("public")

# The suffix that the converter gives to the target of a test module.
_TEST_TARGET_SUFFIX = "_test_lib"

def _jps_module_jars_impl(ctx):
    production = []
    if ctx.attr.target:
        jar_name = ctx.attr.target.label.name + ".jar"
        production = [file for file in ctx.attr.target[DefaultInfo].files.to_list() if file.basename == jar_name]
        if len(production) != 1:
            fail("%s has no output %s" % (ctx.attr.target.label, jar_name), attr = "target")
    files = depset(production + ctx.files.libraries)
    return [DefaultInfo(files = files, runfiles = ctx.runfiles(transitive_files = files))]

_jps_module_jars = rule(
    doc = "The production jar of one module and the jars of its local module libraries.",
    implementation = _jps_module_jars_impl,
    attrs = {
        "target": attr.label(
            doc = "The target of a production module. A rule that walks the module graph follows this edge.",
        ),
        "libraries": attr.label_list(allow_files = [".jar"]),
    },
)

def jps_module(name, target = None, iml = None, descriptors = [], exports = [], hand_exports = [], resources = True, plugin = False, libraries = []):
    """The record of the JPS module `name`. The converter writes the call into the `iml <module>` section.

    The call exports the `.iml` file, the descriptors and `exports` with public visibility. It also declares the targets
    that the groups of the `jps_modules` repository name:

    - `<module>_model_files`: the `.iml` file and the descriptors. It holds source files only.
    - `<module>_jars`: the jar of a production module and the jars of its local module libraries.
      It names the target of the module, so a walk of the module graph can start there.
    - `<module>_test_jars`: the jar of a test module. It names the jar as a file, so the record is not testonly, and
      a group of production jars does not analyze the test targets.
    - `<module>_resource_jars`: the resource jar of the module target. The plugin model tool reads it.
    - `<module>_plugin_distribution`: the `ij_plugin` target of the module.

    A group that the module does not have is an empty filegroup, so each label of the repository exists.

    Args:
        name: The module name.
        target: The name of the module target in the package. A module of a test target ends with `_test_lib`.
        iml: The `.iml` file, when it is not `<module>.iml` in the package.
        descriptors: The descriptors of the module in the package: `<module>.xml` and `META-INF/plugin.xml`.
        exports: The other files of the package that the module exports, such as the XML files of its resource roots.
        hand_exports: The descriptors that the package exports elsewhere: by hand, with a visibility of its own, or by
            the record of another module of the package.
        resources: Whether the module has resources, so the plugin model tool reads its resource jar.
        plugin: Whether the package has the `<target>_plugin` target of the module.
        libraries: The jars of the local module libraries outside the library repositories.
    """
    iml = iml or name + ".iml"
    native.exports_files(
        [iml] + [descriptor for descriptor in descriptors if descriptor not in hand_exports] + exports,
        visibility = ["//visibility:public"],
    )
    native.filegroup(
        name = name + "_model_files",
        srcs = [iml] + descriptors,
        visibility = ["//visibility:public"],
    )

    test = target != None and target.endswith(_TEST_TARGET_SUFFIX)
    _jps_module_jars(
        name = name + "_jars",
        target = ":" + target if target and not test else None,
        libraries = libraries,
        visibility = ["//visibility:public"],
    )
    native.filegroup(
        name = name + "_test_jars",
        srcs = [":" + target + ".jar"] if test else [],
        visibility = ["//visibility:public"],
    )
    native.filegroup(
        name = name + "_resource_jars",
        srcs = [":" + target + "_resource_jar"] if target and resources else [],
        testonly = test,
        visibility = ["//visibility:public"],
    )
    if plugin:
        native.alias(
            name = name + "_plugin_distribution",
            actual = ":" + target + "_plugin",
            visibility = ["//visibility:public"],
        )
    else:
        native.filegroup(
            name = name + "_plugin_distribution",
            visibility = ["//visibility:public"],
        )

def _jps_modules_failure_impl(ctx):
    if ctx.attr.messages:
        fail("\n".join(ctx.attr.messages))
    return [DefaultInfo()]

jps_modules_failure = rule(
    doc = """Fails the analysis with the messages of the `jps_modules` repository rule, when it has any.

The repository rule writes this target instead of failing the fetch. A failed fetch would also fail the load of each
package that loads `defs.bzl` of the repository, and Bazel runs the rule again when one of its inputs changes.
""",
    implementation = _jps_modules_failure_impl,
    attrs = {
        "messages": attr.string_list(doc = "The problems that the repository rule found."),
    },
)
