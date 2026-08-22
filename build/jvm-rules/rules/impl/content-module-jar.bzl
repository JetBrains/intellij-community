"""Packs a module's `lib/<module>.jar` of an IntelliJ-based product's platform distribution.

An extra output of `jvm_library` rather than a target of its own: the jar is named after the module, holds the module's
own output, and only differs from a rename when libraries or other modules are merged into it - so everything except
those two lists is already on the library target. It is exposed in the `content_module_jar` output group, not in
`DefaultInfo`, so building a module does not pack its distribution jar.

The point of packing here at all is what the action declares: the jars it merges, and nothing else. A fragment that
packed these jars had to evaluate the whole product layout, so it declared the shared project-model tree and any
`.iml` edit re-keyed it; no composed fragment packs them any more, and the one that still can - the reference target
`./build/dev-dist.cmd jars` builds - exists only to compare this packer against `JarPackager` byte for byte.

**Source order is load-bearing.** The packer resolves an entry name offered by more than one source to the first source
offering it. To reproduce what the in-process `JarPackager` writes, every library jar comes before every module output,
libraries follow their module's `.iml` declaration order, and module outputs follow the platform layout's order - which
is why the merged modules arrive as a `before` and an `after` list rather than one list: this module's own jar sits at
its own place among them, and a target cannot name itself.

The libraries arrive as the targets that group their jars, not as jar files, so no label here carries a version. The
rule expands each to its jars, in the order the library declares them, and drops a jar it has already seen.
"""

load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load("//:rules/impl/content-module-packer-tool.bzl", "ContentModulePackerInfo")

visibility("private")

# A module output whose module is one of the `intellij.libraries.*` wrappers is a descriptor rather than content: it is
# what `JarPackager` discounts when deciding whether a jar has exactly one meaningful source and may therefore keep that
# source's `META-INF/MANIFEST.MF`.
_LIB_MODULE_PREFIX = "intellij.libraries."

CONTENT_MODULE_JAR_ATTRS = {
    "content_module_jar": attr.bool(
        doc = """Whether to pack this module's `lib/<module_name>.jar` of the platform distribution.

The jar is exposed in the `content_module_jar` output group.""",
        default = False,
    ),
    "content_module_jar_libraries": attr.label_list(
        doc = """Libraries merged into the distribution jar, in merge order.

Each is the target that groups a library's jars - the `jvm_import`, `java_library` or `java_import` the module already
names in its `deps`/`runtime_deps`/`exports` - and the rule expands it to those jars. A target rather than a jar file so
that the label carries no version: a library version bump rewrites the jar file names, and a per-jar label would strand
every `BUILD.bazel` that named one.

The libraries precede every module output, as `JarPackager` writes them, and the order within decides which copy of an
entry two libraries both carry ends up in the jar.""",
        default = [],
        providers = [[JavaInfo]],
    ),
    "content_module_jar_modules_before": attr.label_list(
        doc = "Modules whose output is merged before this module's own.",
        default = [],
        providers = [_KtJvmInfo],
    ),
    "content_module_jar_modules_after": attr.label_list(
        doc = "Modules whose output is merged after this module's own.",
        default = [],
        providers = [_KtJvmInfo],
    ),
    "content_module_jar_rewrite_boot_class_path": attr.bool(
        doc = """Whether to keep the merged manifest and point its `Boot-Class-Path` at the packed jar.

The coverage agent instruments from any class loader, which needs that attribute to name the jar the agent is actually
in - and merging it into `lib/<module>.jar` renames it. `mergeJars.kt` does the same for the same jar on the
`JarPackager` side; which module needs it is decided by the generator, not here.""",
        default = False,
    ),
    # The packer carries its own shape - executable, argument prefix, tools, execution requirements - so this rule
    # needs no knowledge of whether it is a JVM or a native binary. No `cfg` here: the tool rule's own attributes
    # take the exec transition, which keeps `ctx.executable` meaningful and avoids an exec-of-exec configuration.
    "_content_module_packer": attr.label(
        default = "//:content-module-packer",
        providers = [ContentModulePackerInfo],
    ),
}

def _library_jars(ctx):
    """The jars of every library in `content_module_jar_libraries`, in merge order, first occurrence winning.

    `transitive_runtime_jars` rather than another `JavaInfo` jar set, because it is the only one correct for all three
    shapes the library generator emits: a single-jar Maven library is a `jvm_import` whose `compile_jar` is the real jar,
    a multi-jar one is a srcs-less `java_library` re-exporting one `jvm_import` per jar, and a local library is a
    `java_import`. `full_compile_jars` would prepend the `java_library`'s own empty output jar - a `MANIFEST.MF` ahead of
    every real source - and `compile_jars` would hand back the `java_import`'s ijars.

    Deduplication is first-wins because that is how the packer resolves an entry two sources both offer: two libraries of
    one module can name the same jar - `libraries/google-auth` shares `jsr305`, `commons-codec` and `listenablefuture`
    between two of its libraries - and the copy that ships must be the one placed first.
    """
    jars = []
    seen = {}
    for dep in ctx.attr.content_module_jar_libraries:
        dep_jars = dep[JavaInfo].transitive_runtime_jars.to_list()
        if not dep_jars:
            fail(
                "%s contributes no runtime jars, so it would merge nothing into the jar. " % dep.label +
                "A `-provided` target is `neverlink` and never will - name the library itself.",
                attr = "content_module_jar_libraries",
            )
        for jar in dep_jars:
            if jar.path not in seen:
                seen[jar.path] = True
                jars.append(jar)
    return jars

def _keep_manifest(library_jars, merged_module_names):
    significant_sources = len(library_jars) + len([
        name
        for name in merged_module_names
        if not name.startswith(_LIB_MODULE_PREFIX)
    ])
    return significant_sources == 1

def content_module_jar_action(ctx, module_jar):
    """Registers the packing action, and returns the distribution jar, or `None` when the module packs none.

    Args:
        ctx: the `jvm_library` rule context.
        module_jar: this module's own output jar.

    Returns:
        The packed `File`, or `None`.
    """
    if not ctx.attr.content_module_jar:
        return None

    module_name = ctx.attr.module_name
    if not module_name:
        fail("content_module_jar needs module_name: the distribution jar is named after the module", attr = "content_module_jar")

    before = [module[_KtJvmInfo] for module in ctx.attr.content_module_jar_modules_before]
    after = [module[_KtJvmInfo] for module in ctx.attr.content_module_jar_modules_after]
    module_jars = [info.all_output_jars[0] for info in before] + [module_jar] + [info.all_output_jars[0] for info in after]
    merged_module_names = [info.module_name for info in before] + [module_name] + [info.module_name for info in after]

    library_jars = _library_jars(ctx)

    output = ctx.actions.declare_file(module_name + ".jar")
    args = ctx.actions.args()
    args.set_param_file_format("multiline")

    # The packer takes a flag file rather than arguments: a product packs hundreds of jars from thousands of inputs, and
    # `--flagfile=` is the only argument it accepts.
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add(output, format = "output=%s")
    if _keep_manifest(library_jars, merged_module_names):
        args.add("keep-manifest=true")
    if ctx.attr.content_module_jar_rewrite_boot_class_path:
        args.add("rewrite-boot-class-path=true")

    # Files, not `.path` strings, so path mapping can rewrite them.
    args.add_all(library_jars, format_each = "library=%s")
    args.add_all(module_jars, format_each = "module=%s")

    packer = ctx.attr._content_module_packer[ContentModulePackerInfo]
    ctx.actions.run(
        # One mnemonic for every content-module jar, so a strategy or an execution-info override reaches all of them.
        mnemonic = "PackContentModuleJar",
        inputs = depset(library_jars + module_jars),
        outputs = [output],
        tools = packer.tools,
        executable = packer.executable,
        execution_requirements = packer.execution_requirements,
        arguments = packer.argument_prefix + [args],
        progress_message = "Packing distribution jar of %{label}",
    )
    return output
