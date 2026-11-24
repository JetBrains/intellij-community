"""The jar files of the libraries of one package of a library repository."""

visibility("public")

def _library_jars_impl(name, visibility, srcs):
    jars = {}
    copied = {}
    imported = {}
    for rule in native.existing_rules().values():
        kind = rule["kind"]
        if kind == "_copy_file":
            jars[str(rule["out"])] = None
            copied[str(rule["src"])] = None
        elif kind == "java_import":
            for jar in rule["jars"]:
                jars[str(jar)] = None
        elif kind == "jvm_import" and rule.get("jar"):
            imported[str(rule["jar"])] = rule["name"]

    # A Maven jar is a `copy_file` output. If a rule kind of the converter changes, the group would lose the jars of a
    # `jvm_import` silently, so each imported jar must have its copy.
    uncopied = sorted([target for jar, target in imported.items() if jar not in copied])
    if uncopied:
        fail("The jars of %d `jvm_import` target(s) of the package have no `copy_file`, so `library_jars` does not have them: %s" % (
            len(uncopied),
            ", ".join(uncopied[:10]),
        ))
    native.filegroup(
        name = name,
        srcs = sorted(jars) + srcs,
        visibility = visibility,
    )

library_jars = macro(
    doc = """A filegroup of the library jars of the package: the `out` of each `copy_file` and the `jars` of each `java_import`.

The converter writes a `copy_file` for each Maven jar and a `java_import` for each local library, so the group follows
the generated sections. The macro is a finalizer, so it sees every rule of the package wherever it is called.
""",
    implementation = _library_jars_impl,
    finalizer = True,
    attrs = {
        "srcs": attr.label_list(
            doc = "The groups of other packages of the repository, such as `//ant/lib:library_jars`.",
            default = [],
            configurable = False,
        ),
    },
)
