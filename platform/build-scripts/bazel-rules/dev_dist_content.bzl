"""The content boundary of a dev distribution, expressed as a Bazel provider.

`DevDistContentInfo` carries the module and library jars one slice of a distribution reads, and
`intellij_dev_build_inputs` turns them into manifest entries. `dev_dist_platform_payload` is its producer: the payload
of the fragment that owns `lib/`, split by which producer packs each jar.
"""

load("@rules_kotlin//kotlin/internal:defs.bzl", _KtJvmInfo = "KtJvmInfo")
load(":content_module_jar.bzl", "ContentModuleJarInfo", "DevDistPlatformJarInfo")

DevDistContentInfo = provider(
    doc = "The module and library jars one slice of a dev distribution is made of.",
    fields = {
        # Bare `File`s, because the only consumer - `intellij_dev_build_inputs` - keys a module jar by
        # `str(file.owner) + ".jar"` and needs nothing else from the target that produced it.
        "module_jars": "depset of File: the jars of the modules this content declares as its own members.",
        # Not bare `File`s, unlike the module halves: a library's manifest key is the container target's label, which is
        # not derivable from the files. The producer expands the container into its jars.
        "library_jars": "depset of struct(label, jars): `label` being the container target's own label.",
    },
)

DevDistPlatformPayloadInfo = provider(
    doc = "What a product's `lib/`-owning payload contains, split by which producer packs each jar.",
    fields = {
        "packed_jars": "depset of File: the `lib/<module>.jar`s a `content_module_jar` target packed.",
        # Jars only in `packed_jars`, because the byte gate reads it as the set of jars to compare. The native tree a
        # platform jar writes beside itself travels in the record, and the packed-jars component places it from there.
        "packed_metadata": """depset of struct(jar, metadata, relative_path, native_tree, native_lib_dir): the metadata
        and the destination of each packed jar, and its native tree with the `lib/` subdirectory the tree goes to.
        `None` and empty for a jar without one.""",
        "packed_jar_names": """list of string: their destinations within `lib/`, sorted - the jar-name exclusion set.

        A destination, not a file name: a platform jar can name a subdirectory of `lib/`, and the fragment that must not
        pack it matches this against the destination its own plan states.""",
        "declared_modules": """depset of string: the payload modules whose inputs a fragment still declares.

        The payload minus everything a packed jar already holds. `intellij_dev_build_inputs` keeps an `owned_inputs`
        entry when any module that contributed it is in here, which is what removes a handed-over module's jar and its
        libraries at once.""",
    },
)

def _dev_dist_platform_payload_impl(ctx):
    packed_jars = []
    packed_metadata = []
    packed_member_jars = []
    packed_member_names = []
    packed_library_jars = []
    packed_destinations = []
    native_dir_owners = {}
    for target in ctx.attr.packed:
        info = target[ContentModuleJarInfo] if ContentModuleJarInfo in target else target[DevDistPlatformJarInfo]
        packed_jars.append(info.jar)
        packed_destinations.append(struct(destination = info.relative_path, jar = info.jar))

        # `getattr`, because only a platform jar can carry a native tree and `ContentModuleJarInfo` has no such field.
        native_tree = getattr(info, "native_tree", None)
        native_lib_dir = getattr(info, "native_lib_dir", "") if native_tree != None else ""
        if native_tree != None:
            # One owner per `lib/<dir>/`, like one owner per jar destination below: two trees in one directory would
            # be two producers of whichever files they share.
            previous = native_dir_owners.get(native_lib_dir)
            if previous != None:
                fail("%s: lib/%s/ receives the native tree of both %s and %s" % (ctx.label, native_lib_dir, previous.owner, info.jar.owner))
            native_dir_owners[native_lib_dir] = info.jar
        packed_metadata.append(struct(
            jar = info.jar,
            metadata = info.metadata,
            relative_path = info.relative_path,
            native_tree = native_tree,
            native_lib_dir = native_lib_dir,
        ))
        packed_member_jars.extend(info.member_jars)
        packed_member_names.extend(info.member_modules)
        packed_library_jars.extend(info.library_jars)

    packed = depset(packed_jars)

    # Keyed by the destination each jar declares, not by the name of the file that holds it. A platform jar can name a
    # subdirectory of `lib/`, so two jars can share a file name and still land in two places, and one destination can be
    # claimed by two jars whose file names differ.
    owner_by_name = {}
    for entry in packed_destinations:
        previous = owner_by_name.get(entry.destination)
        if previous != None and previous != entry.jar:
            # Two producers for one `lib/` path. `mergeDevBuildComponent` would catch it during a compose, but only as a
            # colliding path; here the two owning modules can still be named.
            fail("%s: %s is packed by both %s and %s" % (ctx.label, entry.destination, previous.owner, entry.jar.owner))
        owner_by_name[entry.destination] = entry.jar

    if not owner_by_name:
        fail("%s: no module in this payload packs a `lib/` jar, which cannot be right for a platform payload" % ctx.label)

    packed_members = {name: True for name in packed_member_names}

    # The payload minus the members of the packed jars, by the JPS module name `jvm_library` sets on `KtJvmInfo`. That
    # name is the key the packed jars' `member_modules` and the fragment's `owned_inputs` use too, so nothing has to
    # repeat the payload as a name list. A repository rule used to prune the payload with a checked-in table of packed
    # module names; the packing answer is a provider, so the pruning happens here.
    declared_modules = []
    for target in ctx.attr.modules:
        module_name = getattr(target[_KtJvmInfo], "module_name", None)
        if not module_name:
            fail("%s is in the payload but is not a module" % target.label, attr = "modules")
        if module_name not in packed_members:
            declared_modules.append(module_name)

    return [
        DevDistPlatformPayloadInfo(
            packed_jars = packed,
            packed_metadata = depset(packed_metadata),
            packed_jar_names = sorted(owner_by_name.keys()),
            declared_modules = depset(declared_modules),
        ),
        # The reference target's whole declaration: it packs the handed-over jars the `JarPackager` way, so what it reads
        # is exactly what is inside them - the member module jars and the libraries merged into them. Ordinary content,
        # so it arrives through the content boundary `intellij_dev_build_inputs` reads.
        DevDistContentInfo(
            module_jars = depset(packed_member_jars),
            library_jars = depset(packed_library_jars),
        ),
    ]

dev_dist_platform_payload = rule(
    doc = """The payload of the fragment that owns `lib/`, and which of its jars another producer already packed.

    This is the one intersection that decides jar ownership within `lib/`, and it is a **question asked of the graph**.
    Pruning happens during analysis because the bridge cannot inspect `ContentModuleJarInfo` providers during loading.
    It used to be a set intersection at *fetch* time: `jpsModelToBazel` wrote every module that packs a jar to a
    generated `build/dev_dist_content_module_jars.bzl` - 2 524 names, 18 of which said anything the module's own
    `jvm_library` did not already say - and the repository rule intersected that table with the payload, because a
    repository rule cannot see providers. The table was checked in, so every branch that added or renamed a platform
    module rewrote a line of it.

    Nothing needs to be told any more. The payload arrives whole and unfiltered, `packed` names the packing targets that
    stand beside its modules, and everything the intersection used to produce comes out of one provider so the answers
    cannot disagree:

    * `packed_jars` go to `intellij_dev_packed_jars_component`, which composes them in;
    * `packed_jar_names` go to the owning fragment as the jars it must **not** pack, and to the reference target as the
      jars it packs and nothing else;
    * `declared_modules` is what the owning fragment still declares: the payload minus the members of the packed jars;
    * `DevDistContentInfo` is the other side of the same split, and is the reference target's whole declaration.

    A stale set is no longer a thing that can happen: a module that stops packing a jar stops appearing here in the same
    analysis that stops producing it.
    """,
    implementation = _dev_dist_platform_payload_impl,
    attrs = {
        "modules": attr.label_list(
            doc = "The payload's own modules, as their `jvm_library` targets - the dependency edge that makes this " +
                  "target stand for the platform this product assembles. Their `KtJvmInfo.module_name` is the key " +
                  "`declared_modules` and `owned_inputs` share.",
            providers = [_KtJvmInfo],
            mandatory = True,
        ),
        "packed": attr.label_list(
            doc = "The `content_module_jar` targets of those payload modules that own a `lib/` jar. One per jar - a " +
                  "module that packs none has no such target, so this list *is* the handover set.",
            providers = [[ContentModuleJarInfo], [DevDistPlatformJarInfo]],
            mandatory = True,
        ),
    },
)
