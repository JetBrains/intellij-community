"""Packs one independently reusable module or platform jar of a dev distribution.

`content_module_jar` packs a jar after its owner module. `dev_dist_platform_jar` packs a generated residual platform
jar. A plugin remainder uses the model-driven batch rule instead of a jar-specific rule.

A target of its own, next to the `jvm_library` whose module it is named after - not attributes on that library. It was
attributes until the packer moved into this module, and the reason it could not be a target then was the reason the whole
thing was wired through a flag: `jvm_library` belongs to `rules_jvm`, which may not name a label in a repository that
consumes it, so the tool had to be pushed in from a `.bazelrc` against a default that failed at execution time. With the
packer in `@community//build/content-module-packer` the rule can name it directly, and a rule of its own is then simply
the better shape.

Three things the attribute form got wrong and this does not:

* **The owner is named, not implied.** `module` is the module whose jar this is, so the jar's name, its own output and
  the manifest decision all come off one label instead of being restated. `modules_before`/`modules_after` are merge
  order *around* that owner - which is what the layout says - rather than the workaround for a target being unable to
  name itself.
* **It works on both compile backends.** The attributes were dropped on the `kt_jvm_library` path, so a module packed
  nothing under the BTA backend. This reads `KtJvmInfo`, which both backends provide.
* **The recipe is readable without guessing.** `dev_dist_content.bzl`'s aspect used to reach it with
  `getattr(ctx.rule.attr, "content_module_jar_libraries", None)` - a name-based read of another rule's attributes that
  answers `None` rather than failing when the name is wrong. They are this rule's own attributes now.

The point of packing here at all is what the action declares: the jars it merges, and nothing else. A fragment that
packed these jars had to evaluate the whole product layout, so it declared the shared project-model tree and any `.iml`
edit re-keyed it; no composed fragment packs them any more, and the one that still can - the reference target
`./build/dev-dist.cmd jars` builds - exists only to compare this packer against `JarPackager` byte for byte.

**Source order is load-bearing.** The packer resolves an entry name offered by more than one source to the first source
offering it. To reproduce what the in-process `JarPackager` writes, every library jar comes before every module output,
libraries follow their module's `.iml` declaration order, and module outputs follow the platform layout's order.

The libraries arrive as the targets that group their jars, not as jar files, so no label here carries a version. The
rule expands each to its jars, in the order the library declares them, and drops a jar it has already seen.
"""

load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load("//build:dev_launch_dependencies.bzl", "HOST_PLATFORMS")

ContentModuleJarInfo = provider(
    doc = """A packed `lib/<module>.jar` of the platform distribution, and what went into it.

    The recipe travels with the jar because everything downstream needs it and this is the only target that knows it. A
    fragment handed this jar must stop declaring the module jars and libraries whose bytes are now inside it, and the
    reference target that packs the same jars the `JarPackager` way declares exactly these two lists. Reading them here
    is what replaced `dev_dist_content.bzl` asking another rule for attributes by name - `getattr(ctx.rule.attr,
    "content_module_jar_libraries", None)`, which answered `None` rather than failing when the name was wrong.""",
    fields = {
        "jar": "The packed `File`, `<target>.production.jar`.",
        "metadata": "The file hash metadata from the same packing action.",
        # The distribution's path for this jar is derived from the module name, not from the jar's own path, so the name
        # travels with the jar rather than being re-derived from a label by every consumer.
        "module_name": "The JPS module the jar is named after.",
        # Derived from the module name, not from the file: the packed file is `<target>.production.jar` and the
        # destination is `<module>.jar`. A field all the same: `DevDistPlatformJarInfo` carries a destination that can
        # name a subdirectory, and a consumer of both providers reads one field rather than deriving the flat case
        # from the file and the nested case from a provider.
        "relative_path": "string: the jar's destination, relative to the plugin's own `lib/`.",
        "member_jars": "tuple of File: the own jar of every merged module, this jar's own module included.",
        "member_modules": """tuple of string: the same members by JPS module name.

        The names, because that is what a payload is written in: a fragment's declared inputs are pruned by asking which
        module contributed each one, and a module name is the one key that means the same thing on both sides of the
        loading/analysis boundary - a label does not, since a repository rule writes `@community//...` where an
        analysis-time `Label` reads back canonically.""",
        "library_jars": """tuple of struct(label, jars): the merged libraries, one entry per container.

        Grouped rather than flat, and not the same list the action merges: a library's manifest key is the container
        target's label, which is not derivable from the files. Deduplication is therefore within a container here, where
        the action's flat list dedups across them as well - the packer needs one copy of a jar, a manifest needs the
        container that offered it.""",
        # The jar leaves the presigned library's native entries out, as `JarPackager` does. The tree of each platform is
        # an action of its own, so the jar does not depend on the platform and a consumer builds one tree only.
        "native_lib_dir": "string: the `lib/` subdirectory that receives the native tree, empty without natives.",
        "native_trees": """dict of string to struct(tree, metadata): the native tree of each `HOST_PLATFORMS` token, with
        the metadata of the action that wrote it. Empty without natives.""",
    },
)

# A module output whose module is one of the `intellij.libraries.*` wrappers is a descriptor rather than content: it is
# what `JarPackager` discounts when deciding whether a jar has exactly one meaningful source and may therefore keep that
# source's `META-INF/MANIFEST.MF`.
_LIB_MODULE_PREFIX = "intellij.libraries."

def library_entries(ctx, libraries = None, attr_name = "libraries"):
    """One `struct(label, jars)` per library container, in merge order, deduped first-wins within the container.

    Public because `dev_plugin.bzl` expands the libraries of a plugin jar the same way. `libraries` defaults to
    `ctx.attr.libraries`, and `attr_name` names the attribute a failure points at.

    `transitive_runtime_jars` rather than another `JavaInfo` jar set, because it is the only one correct for all three
    shapes the library generator emits: a single-jar Maven library is a `jvm_import` whose `compile_jar` is the real jar,
    a multi-jar one is a srcs-less `java_library` re-exporting one `jvm_import` per jar, and a local library is a
    `java_import`. `full_compile_jars` would prepend the `java_library`'s own empty output jar - a `MANIFEST.MF` ahead of
    every real source - and `compile_jars` would hand back the `java_import`'s ijars.

    The label is the container's, so it carries no artifact version - see the `libraries` attribute.
    """
    if libraries == None:
        libraries = ctx.attr.libraries
    entries = []
    for dep in libraries:
        jars = []
        seen = {}
        for jar in dep[JavaInfo].transitive_runtime_jars.to_list():
            if jar.path not in seen:
                seen[jar.path] = True
                jars.append(jar)
        if not jars:
            fail(
                "%s contributes no runtime jars, so it would merge nothing into the jar. " % dep.label +
                "A `-provided` target is `neverlink` and never will - name the library itself.",
                attr = attr_name,
            )

        # A tuple, not the list: a depset element must be immutable, and a struct holding a list is not.
        entries.append(struct(label = str(dep.label), jars = tuple(jars)))
    return entries

def merge_order_jars(library_entries):
    """The library jars the action merges: the entries flattened, deduped first-wins *across* containers too.

    Public for the same reason as `library_entries`.

    Two libraries of one module can name the same jar - `libraries/google-auth` shares `jsr305`, `commons-codec` and
    `listenablefuture` between two of its libraries - and the copy that ships must be the one placed first, because that
    is how the packer resolves an entry two sources both offer.
    """
    jars = []
    seen = {}
    for entry in library_entries:
        for jar in entry.jars:
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

def module_output_jar(target):
    """The module target's declared `<name>.jar`, or None if the target did not declare one.

    This is the module's distribution jar, fetched in the uniformed way no matter which compilation backend is used:
    rules_jvm (JPS) or the underlying rules_kotlin (BTA)
    """
    jar_name = target.label.name + ".jar"
    for file in target[DefaultInfo].files.to_list():
        if file.basename == jar_name:
            return file
    return None

def _module(target, attr_name):
    """One merged module's own jar and JPS name.

    `jvm_library` always sets `KtJvmInfo.module_name`, deriving one from the label when the attribute is absent, and so
    does `kt_jvm_library`; a library container does not. So this is also what tells a module from something that only
    looks like one.
    """
    info = target[_KtJvmInfo]
    if not hasattr(info, "module_name") or not info.module_name:
        fail("%s is merged into this jar but is not a module" % target.label, attr = attr_name)
    jar = module_output_jar(target)
    if jar == None:
        fail("%s has a module name ('%s') but produced no output jar" % (target.label, info.module_name), attr = attr_name)
    return struct(jar = jar, name = info.module_name)

def declare_spans(ctx, name):
    """The action's span file, or `None` when the trace flag is off.

    Public because every rule that calls `pack_jar` declares its span file the same way.

    Named after what the caller states rather than after the jar, because two targets may write a jar of one name and a
    target name is unique in its package. `dev-dist trace` joins a span file to an action through the action's primary
    output, so the jar and this file must not disagree about which action wrote them.
    """
    if not ctx.attr._trace_spans[BuildSettingInfo].value:
        return None
    return ctx.actions.declare_file(name + ".spans.json")

def pack_jar(ctx, output, spans, module_jars, library_jars, merged_module_names, mnemonic, progress_message, extra_flags = [], extra_outputs = [], descriptor = None, descriptor_module = None, descriptor_path = "META-INF/plugin.xml", metadata = None, coverage_agent_manifest = False):
    """Runs the packer over one jar, for either of this file's two rules and for `dev_plugin.bzl`.

    The rules differ in the jar's identity - its path, its mnemonic and its provider - and in nothing the packer
    sees. So the flag file, the worker contract and the merge order are stated once here. The caller's rule must
    declare `_packer` and `_trace_spans`.

    The packer takes a flag file rather than arguments: a product packs hundreds of jars from thousands of inputs, and
    `--flagfile=` is the only argument it accepts. `output=` starts a group, so the order below is the grammar's own:
    the output, the trace file, the flags of the jar, then the libraries and then the module outputs. Libraries lead
    because that is `buildAsset`'s order, and the packer resolves an entry two sources both offer to the first one.

    An `extra_flags` entry is a string, or a `struct(file, format)` for a line that names a `File`, such as the
    natives-mode tree. The `File` form, not its path, so that path mapping can rewrite the line with the rest of the
    flag file. A `File` the packer writes besides the jar and its metadata goes into `extra_outputs`.

    `coverage_agent_manifest` selects the coverage policy `JarPackager` applies to the same jar. Each source named
    `intellij-coverage-agent*` gets `source-manifest=coverage-agent`, which rewrites its `Boot-Class-Path` to the jar
    it ends up in. The call fails when the policy is selected and no source has that name, so a renamed agent library
    cannot ship an unrewritten manifest.
    """
    args = ctx.actions.args()
    args.set_param_file_format("multiline")
    args.use_param_file("--flagfile=%s", use_always = True)
    args.add(output, format = "output=%s")
    if metadata == None:
        metadata = ctx.actions.declare_file(ctx.label.name + ".metadata.json")
    args.add(metadata, format = "metadata-file=%s")

    outputs = [output, metadata]
    if spans:
        # Packers put `trace-file=` inside their flag file so per-action paths do not split persistent workers.
        # Inside the flag file, as a `trace-file=` line, rather than as a `--trace-file=` argument - which is what every
        # other producer of these span files takes. A worker has no other per-action channel: Bazel splits a worker
        # spawn's arguments at the param file, everything before it becomes the worker *process*'s command line and part
        # of its `WorkerKey`, and a per-action path there would start a fresh worker for each of the ~2 500 actions.
        # Everything added to this `Args` lands in the file instead. It follows `output=`, because the grammar starts a
        # group there.
        #
        # The `File`, not `spans.path`: this argument travels in a param file that output path mapping may rewrite, and
        # only the `File` form is rewritten with it. `intellij_dev_dist.bzl`'s `_declare_spans` passes the string,
        # because there the argument sits on a local-exec command line among neighbours that all pass strings.
        args.add(spans, format = "trace-file=%s")

        # Appended, never prepended: the jar stays this action's primary output, which is both its identity in a Bazel
        # profile and the key `dev-dist trace` joins this file by.
        outputs.append(spans)
    outputs.extend(extra_outputs)

    if descriptor == None and _keep_manifest(library_jars, merged_module_names):
        args.add("keep-manifest=true")
    for flag in extra_flags:
        if type(flag) == "string":
            args.add(flag)
        else:
            # `add_all` without directory expansion, because `add` refuses a directory: the line names the tree itself,
            # not its members, and the packer writes them.
            args.add_all([flag.file], format_each = flag.format, expand_directories = False)

    # Files, not `.path` strings, so path mapping can rewrite them. The module outputs come first and the libraries
    # after them, as `JarPackager` orders the same jar, so the module descriptor is the first entry.
    coverage_agent_sources = 0
    for module_name, module_jar in zip(merged_module_names, module_jars):
        if descriptor != None and module_name == descriptor_module:
            args.add(descriptor, format = "patch=" + descriptor_path + "=%s")
        args.add(module_jar, format = "module=%s")
        if coverage_agent_manifest and module_jar.basename.startswith("intellij-coverage-agent"):
            args.add("source-manifest=coverage-agent")
            coverage_agent_sources += 1
    if coverage_agent_manifest:
        for library_jar in library_jars:
            args.add(library_jar, format = "library=%s")
            if library_jar.basename.startswith("intellij-coverage-agent"):
                args.add("source-manifest=coverage-agent")
                coverage_agent_sources += 1
    else:
        args.add_all(library_jars, format_each = "library=%s")
    if coverage_agent_manifest and coverage_agent_sources == 0:
        fail("%s: the module name selects the coverage-agent manifest policy, but no merged jar is named intellij-coverage-agent*" % ctx.label)

    ctx.actions.run(
        # One mnemonic per producer, so a strategy or an execution-info override reaches every jar of that producer and
        # of no other. `common.bazelrc` pins a pool size to each, and `no-cache` to the platform one alone.
        mnemonic = mnemonic,
        inputs = depset(library_jars + module_jars + ([descriptor] if descriptor != None else [])),
        outputs = outputs,
        executable = ctx.executable._packer,
        # A worker, even though the binary starts in about two milliseconds. What a worker amortises here is not this
        # process's startup but Bazel's per-spawn cost, and the per-jar work is ~1 ms against a spawn-and-teardown
        # envelope an order of magnitude larger - so at this action count the envelope *is* the build. Measured on this
        # repository at 2 524 jars, one process per action cost 38.4 s where the worker costs 26.0 s, and 6.6 s once the
        # action stopped being cached.
        #
        # `requires-worker-protocol` is absent because proto is Bazel's default, and the packer speaks it: it decodes
        # the six fields by hand in `internal/worker`, with no protobuf dependency and no generated schema, the way
        # `@rules_jvm//worker-framework:protocol.kt` already decodes the same message on the JVM side. Every other worker
        # in this repository is on the same default. An explicit `"proto"` would only add something that can drift from
        # the code - and an unrecognised value here is a hard failure, so a typo in one is worse than its absence.
        #
        # `supports-path-mapping` is deliberately absent - path mapping is not enabled in this repository - and so is
        # `supports-multiplex-sandboxing`, which is inert without `--worker_sandboxing`.
        #
        # `no-sandbox` stays, and it is parity rather than an optimisation. It is inert under the worker strategy, but it
        # is what keeps the `local` fallback - `--strategy=PackContentModuleJar=local`, or `--noworker_multiplex` - at
        # the cost the JVM worker this replaced paid: that worker ran *non-sandboxed*, since Bazel's worker strategy
        # behaves like `local` unless `--worker_sandboxing` is set, and it is set nowhere here. Measured at 2 524 jars:
        # 59.4 s sandboxed against 55.1 s not. The action reads only its declared inputs and writes only its declared
        # output, so the sandbox was buying nothing.
        execution_requirements = {
            "supports-workers": "1",
            "supports-multiplex-workers": "1",
            "supports-worker-cancellation": "1",
            "no-sandbox": "1",
        },
        arguments = [args],
        progress_message = progress_message,
    )
    return metadata

# The private names this file's own rules call.
_library_entries = library_entries
_merge_order_jars = merge_order_jars
_declare_spans = declare_spans
_pack = pack_jar

# The tree's base name. The packer writes the tree's inventory under this key, and the packed-jars component's
# catalogue names the tree by it. So the two spell it once, here.
_NATIVE_TREE_NAME = "native"

def _natives(ctx):
    """The natives mode as `struct(lib, lib_dir)`, or `None` when both attributes are empty.

    Both or none. The library's name finds the native files, and the `lib/` subdirectory receives them. One without the
    other would place nothing or place it nowhere, so it is refused rather than guessed at. The directory is one name,
    with no separator and no relative element, because the composer places the tree under `lib/<name>/`.
    """
    lib = ctx.attr.native_lib
    lib_dir = ctx.attr.native_lib_dir
    if not lib and not lib_dir:
        return None
    if not lib or not lib_dir:
        fail("natives mode needs both native_lib and native_lib_dir", attr = "native_lib" if not lib else "native_lib_dir")
    if "/" in lib_dir or lib_dir == "." or lib_dir == "..":
        fail("'%s' is not one directory name under `lib/`" % lib_dir, attr = "native_lib_dir")
    return struct(lib = lib, lib_dir = lib_dir)

def _native_trees(ctx, natives, library_jars):
    """Declares the native tree of each `HOST_PLATFORMS` token, one action per platform.

    One action per platform, not one for all: a consumer builds the tree of its own platform only. The action packs the
    libraries into a scratch jar, because the packer writes a tree beside a jar. The module outputs stay out, so a module
    edit does not rebuild the trees.
    """
    trees = {}
    for platform in HOST_PLATFORMS:
        prefix = ctx.label.name + ".native_" + platform
        tree = ctx.actions.declare_directory(prefix + "/" + _NATIVE_TREE_NAME)
        metadata = _pack(
            ctx,
            output = ctx.actions.declare_file(prefix + "/scratch.jar"),
            spans = None,
            metadata = ctx.actions.declare_file(prefix + ".metadata.json"),
            module_jars = [],
            library_jars = library_jars,
            merged_module_names = [],
            mnemonic = "PackContentModuleJar",
            progress_message = "Selecting the %s natives of %%{label}" % platform,
            extra_flags = [
                struct(file = tree, format = "native-tree=%s"),
                "native-variant=" + platform,
                "native-lib=" + natives.lib,
            ],
            extra_outputs = [tree],
        )
        trees[platform] = struct(tree = tree, metadata = metadata)
    return trees

def _content_module_jar_impl(ctx):
    before = [_module(target, "modules_before") for target in ctx.attr.modules_before]
    owner = _module(ctx.attr.module, "module")
    after = [_module(target, "modules_after") for target in ctx.attr.modules_after]
    merged = before + [owner] + after

    module_name = owner.name
    module_jars = [module.jar for module in merged]
    merged_module_names = [module.name for module in merged]

    library_entries = _library_entries(ctx)
    library_jars = _merge_order_jars(library_entries)
    natives = _natives(ctx)

    # The predeclared outputs, so the plan files and the plugin chain can name the jar by its label.
    output = ctx.outputs.production_jar
    spans = _declare_spans(ctx, ctx.label.name + ".production")
    metadata = _pack(
        ctx,
        output = output,
        spans = spans,
        metadata = ctx.outputs.production_metadata,
        module_jars = module_jars,
        library_jars = library_jars,
        merged_module_names = merged_module_names,
        mnemonic = "PackContentModuleJar",
        progress_message = "Packing distribution jar of %{label}",
        extra_flags = ["merge-entities=true"] + (["native-lib=" + natives.lib] if natives else []),
        descriptor = ctx.file.descriptor,
        descriptor_module = module_name,
        descriptor_path = ctx.attr.descriptor_path,
        coverage_agent_manifest = "intellij.platform.coverage.agent" in module_name,
    )
    native_trees = _native_trees(ctx, natives, library_jars) if natives else {}
    return [
        DefaultInfo(files = depset([output])),
        # Deliberately not a field of `ContentModuleJarInfo`: everything in that provider is read to *declare* something,
        # and a span file must never be declared. The group is here for symmetry and for an explicit request; a dist
        # build needs neither, because the jar and its spans are outputs of the same action and Bazel writes both.
        OutputGroupInfo(
            trace_spans = depset([spans] if spans else []),
            file_metadata = depset([metadata]),
            **{
                "native_tree_" + platform: depset([native.tree, native.metadata])
                for platform, native in native_trees.items()
            }
        ),
        ContentModuleJarInfo(
            jar = output,
            metadata = metadata,
            module_name = module_name,
            relative_path = module_name + ".jar",
            member_jars = tuple(module_jars),
            member_modules = tuple(merged_module_names),
            library_jars = tuple(library_entries),
            native_lib_dir = natives.lib_dir if natives else "",
            native_trees = native_trees,
        ),
    ]

_content_module_jar = rule(
    doc = """Packs one content module's `lib/` jar of a platform distribution.

One `PackContentModuleJar` action writes `<target>.production.jar` and `<target>.production.metadata.json`. The jar is
`DefaultInfo`, and the plan files and the plugin chain name it by that label. The destination `<module>.jar` travels
in `ContentModuleJarInfo.relative_path`. The action merges the entity lists of its sources. When the module name
contains `intellij.platform.coverage.agent`, it rewrites the `Boot-Class-Path` of each source named
`intellij-coverage-agent*`, the way `JarPackager` does for the same jar.

With `native_lib` and `native_lib_dir` set, the jar leaves the native entries of that presigned library out, and one
action per `HOST_PLATFORMS` token writes the platform's native files into `<target>.native_<platform>/native`. A consumer
places the tree of its platform under `lib/<native_lib_dir>/`, the way `JarPackager` extracts the natives.""",
    implementation = _content_module_jar_impl,
    outputs = {
        "production_jar": "%{name}.production.jar",
        "production_metadata": "%{name}.production.metadata.json",
    },
    attrs = {
        "module": attr.label(
            doc = """The module this jar belongs to: it is named `<module_name>.jar` and its output is merged in place.

A label rather than a name, so the jar's name, its own output and the significant-source count all come off one
attribute. `KtJvmInfo.module_name` is what carries the name, which both the JPS and the `kt_jvm_library` backend set.""",
            mandatory = True,
            providers = [_KtJvmInfo],
        ),
        "modules_before": attr.label_list(
            doc = "Modules whose output is merged before the owner's own, in merge order.",
            providers = [_KtJvmInfo],
        ),
        "modules_after": attr.label_list(
            doc = "Modules whose output is merged after the owner's own, in merge order.",
            providers = [_KtJvmInfo],
        ),
        "libraries": attr.label_list(
            doc = """Libraries merged into the jar, in merge order.

Each is the target that groups a library's jars - the `jvm_import`, `java_library` or `java_import` the module already
names in its `deps`/`runtime_deps`/`exports` - and the rule expands it to those jars. A target rather than a jar file so
that the label carries no version: a library version bump rewrites the jar file names, and a per-jar label would strand
every `BUILD.bazel` that named one.

The libraries precede every module output, and the order within decides which copy of an entry two libraries both carry
ends up in the jar.""",
            providers = [[JavaInfo]],
        ),
        "descriptor": attr.label(
            doc = "The product descriptor that replaces `META-INF/plugin.xml` in the owner module output.",
            allow_single_file = True,
        ),
        "descriptor_path": attr.string(
            doc = "The path of the product descriptor in the owner module output.",
            default = "META-INF/plugin.xml",
        ),
        "native_lib": attr.string(
            doc = "The Maven artifact name of the presigned library whose native files the jar leaves out, such as `jna`. Empty means no natives mode.",
        ),
        "native_lib_dir": attr.string(
            doc = "The `lib/` subdirectory that receives the native tree, `presignedNativeLibs[native_lib]`. One directory name, no `/`.",
        ),
        "_packer": attr.label(
            default = "//build/content-module-packer",
            executable = True,
            cfg = "exec",
        ),
        # Whether to declare a span output per packed jar. Off by default and off means absent - see the flag's own
        # comment in this package's `BUILD.bazel`.
        "_trace_spans": attr.label(
            default = "//platform/build-scripts/bazel-rules:trace_spans",
            providers = [BuildSettingInfo],
        ),
    },
)

def _relative_output_file(ctx):
    """The jar's `lib/`-relative destination, refused rather than repaired when it cannot be one.

    Four shapes a generator can produce that would silently place a jar somewhere else, and every one of them is cheaper
    to refuse here than to find in a distribution: an absolute path, a `..` element, a `lib/` prefix on a token that is
    already `lib/`-relative, and a name that is not a jar at all.
    """
    path = ctx.attr.relative_output_file
    if not path.endswith(".jar"):
        fail("'%s' is not a jar name" % path, attr = "relative_output_file")
    if path.startswith("/"):
        fail("'%s' is absolute; state the path relative to the plugin's own `lib/`" % path, attr = "relative_output_file")
    if path.startswith("lib/"):
        fail("'%s' is already relative to the plugin's `lib/`, so it states no `lib/` itself" % path, attr = "relative_output_file")
    for element in path.split("/"):
        if element == "" or element == "." or element == "..":
            fail("'%s' holds an empty or relative path element" % path, attr = "relative_output_file")
    return path

DevDistPlatformJarInfo = provider(
    fields = {
        "jar": "The packed `File`.",
        "metadata": "The file hash metadata from the same packing action.",
        # The jar's own file name says where the jar goes only while every destination is flat. A platform jar can name
        # a subdirectory of the plugin's `lib/`, so the destination travels with the jar. The consumers that place it
        # and the fragment that stops packing it must agree on `ext/platform-main.jar`, not on `platform-main.jar`.
        "relative_path": "string: the jar's destination, relative to the plugin's own `lib/`.",
        "member_jars": "tuple of File: the own jar of every merged module.",
        "member_modules": "tuple of string: the same members by JPS module name.",
        "library_jars": "tuple of struct(label, jars): the merged libraries, one entry per container.",
    },
)

def _dev_dist_platform_jar_impl(ctx):
    members = [_module(target, "modules") for target in ctx.attr.modules]
    destination = _relative_output_file(ctx)
    libraries = _library_entries(ctx)
    output = ctx.actions.declare_file(ctx.label.name + "/" + destination)
    spans = _declare_spans(ctx, ctx.label.name)
    metadata = _pack(
        ctx,
        output = output,
        spans = spans,
        module_jars = [member.jar for member in members],
        library_jars = _merge_order_jars(libraries),
        merged_module_names = [member.name for member in members],
        mnemonic = "PackContentModuleJar",
        progress_message = "Packing the platform jar of %{label}",
        # A residual jar carries no native file: a module with a presigned library packs as a `content_module_jar`.
        extra_flags = ["merge-entities=true", "reject-native-entries=true"],
    )
    return [
        DefaultInfo(files = depset([output])),
        OutputGroupInfo(trace_spans = depset([spans] if spans else []), file_metadata = depset([metadata])),
        DevDistPlatformJarInfo(
            jar = output,
            metadata = metadata,
            relative_path = destination,
            member_jars = tuple([member.jar for member in members]),
            member_modules = tuple([member.name for member in members]),
            library_jars = tuple(libraries),
        ),
    ]

dev_dist_platform_jar = rule(
    doc = """Packs one generated residual platform jar of a dev distribution.

One `PackContentModuleJar` action writes the jar at `<target>/<relative_output_file>` and its metadata. The jar holds no
native file.""",
    implementation = _dev_dist_platform_jar_impl,
    attrs = {
        "relative_output_file": attr.string(mandatory = True),
        "modules": attr.label_list(providers = [_KtJvmInfo], mandatory = True),
        "libraries": attr.label_list(providers = [[JavaInfo]]),
        "_packer": attr.label(default = "//build/content-module-packer", executable = True, cfg = "exec"),
        "_trace_spans": attr.label(default = ":trace_spans", providers = [BuildSettingInfo]),
    },
)

def content_module_jar_target_name(module):
    """The packing target's name, from the owner's label - `":core-impl"` gives `"core-impl_content_module_jar"`.

    Public because generated product content refers to this target by label.
    """
    return module.rpartition(":")[2] + "_content_module_jar"

def content_module_jar(module, name = None, tags = [], visibility = ["//visibility:public"], **kwargs):
    """Packs one content module's `lib/` jar, as a target excluded from wildcard builds.

    Two things the macro derives rather than have them restated 2 524 times over. `name` comes from `module`, the way
    `dev_dist_plugin_descriptor` derives its own from `main_module`; and `manual` is added, because the jar is this
    target's `DefaultInfo` and `bazel build //...` would otherwise pack all of them - with
    `--modify_execution_info=PackContentModuleJar=+no-cache`, on every invocation. Under the attribute form the jar sat
    in an output group and a wildcard build packed nothing; `manual` is what keeps that exactly true. Explicit labels and
    `bazel query` still see these targets, which is all a dev distribution needs.

    Args:
        module: the module whose jar this is - see the rule's own `module`.
        tags: extra tags. `manual` is added.
        visibility: public by default. A generated component can use a target from another package.
        **kwargs: see `_content_module_jar`.
    """
    _content_module_jar(
        name = name or content_module_jar_target_name(module),
        module = module,
        tags = tags + ["manual"],
        visibility = visibility,
        **kwargs
    )
