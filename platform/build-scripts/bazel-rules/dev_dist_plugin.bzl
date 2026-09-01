"""One plugin's whole dev-distribution statement, in the plugin's own package.

A plugin used to state itself twice. `jpsModelToBazel` wrote a `dev content` section calling `dev_dist_plugin_content`
and a `dev descriptor` section calling `dev_dist_plugin_descriptor`, and the two restated the plugin's identity: 356
content targets and 148 descriptor leaves named `descriptor_module`, so 504 statements answered 392 plugins. A descriptor
leaf also restated every fact of its plugin once per layout variant. `intellij.jcef.plugin` has six variants, and its six
leaves were identical apart from the `variant` string - 66 lines of `descriptors` where 11 say the same thing.

`dev_dist_plugin` is the one statement. It expands to the same two rules, with the same target names, so nothing
downstream can tell the difference: `dev_dist_plugin_content` for the members and the libraries, and one
`dev_dist_plugin_descriptor` per layout variant.

**The two populations differ, in both directions, so both halves are optional.** 254 of the 392 plugins state content and
no descriptor; 36 state a descriptor and no content. A plugin whose content resolves to nothing beyond its own main module
earns no content leaf at all (`resolvePluginContent`), and a plugin whose own Bazel package holds no `META-INF/plugin.xml`
can declare no descriptor leaf. So which leaves a call expands to follows from what it states, and a call that states
neither fails.

**The per-content-module relation stays two statements, and that was measured rather than assumed.** The content half
names a member by the module's `jvm_library` or its `content_module_jar`; the descriptor half names the same member by its
descriptor *file*. Neither label is derivable from the other - the resource-root directory is not in a target name, and
the two halves spell one community package `//x` and `@community//x` - and the module name that would join them is in
neither. Over the 102 plugins that have both halves, the two name 1 011 packages in common, 141 that only the content
half names and 34 that only the descriptor half names, and only 55 of the 102 name the same set. A joint table keyed by
module name would therefore add a third token to every relation and still need a two-sided remainder. That is the mistake
`PluginContent.prepackedContentModuleLabels` already records having made once, at 2 030 relations.

**Eight attributes are the JPS-to-Bazel converter's own input, and this macro reads none of them.** They carry the
plugin's content residue - what the converter cannot derive about the plugin's members, their jars and their libraries.
They sit on this call so that the plugin states itself in one file, and so that a conflict over them stays inside one
plugin's package (ADR 0007 rule 2). Their names all start with `residue_`, so one search finds the whole group and no
reader has to ask which attributes a leaf rule takes. They are named parameters, and not part of `**descriptor_attrs`,
because a forwarded one would reach `dev_dist_plugin_descriptor` and fail there under a name that rule never had.
"""

load(":dev_dist_content.bzl", "dev_dist_plugin_content")
load(":dev_dist_plugin_descriptor.bzl", "dev_dist_plugin_descriptor")

# The attributes both leaf macros take. Forwarded through `**descriptor_attrs`, either one would reach the descriptor
# leaf and leave the content leaf on its default, so one stated fact would answer half a plugin. Refused here, which is
# the one place both halves are visible.
_SHARED_LEAF_ATTRS = ["tags", "visibility"]

def dev_dist_plugin(
        descriptor_module,
        main_module = "",
        content_modules = [],
        libraries = [],
        prepacked_content_modules = [],
        prepacked_jars = {},
        prepacked_layout_jars = [],
        descriptor = "",
        variants = [],
        residue_lib_root_jars = [],
        residue_member_jars = {},
        residue_merged_libraries = {},
        residue_module_libraries = {},
        residue_project_libraries = [],
        residue_raw_members = [],
        residue_separate_jars = [],
        residue_vetoed_members = [],
        **descriptor_attrs):
    """The dev-distribution content and the patched descriptor of one plugin.

    Args:
        descriptor_module: the plugin's main module target - the one whose resources carry `META-INF/plugin.xml`. Both
            leaves take it, and it is the whole identity the content leaf needs: the rule reads the module name off the
            target, and the macro derives the target name from the label.
        main_module: the plugin's main JPS module. Stated because a descriptor leaf's *target name* carries it, which a
            macro composes while Bazel loads and can therefore read from no provider. Stated only where a descriptor leaf
            exists, so the 254 content-only plugins carry no line for it.
        content_modules: see `dev_dist_plugin_content.content_modules`.
        libraries: see `dev_dist_plugin_content.libraries`.
        prepacked_content_modules: see `dev_dist_plugin_content.prepacked_content_modules`.
        prepacked_jars: see `dev_dist_plugin_content.prepacked_jars`.
        prepacked_layout_jars: see `dev_dist_plugin_content.prepacked_layout_jars`.
        descriptor: the plugin's own `META-INF/plugin.xml`, as a path inside `descriptor_module`'s Bazel package. Its
            presence is what asks for the descriptor leaves, for the reason the content attributes are what ask for the
            content leaf: the rule makes it mandatory, and `computePluginDescriptor` emits nothing without it.
        variants: the layout variants this plugin has, one descriptor leaf each. Empty takes the one variant that serves
            every platform, which is 135 of the 136 single-leaf plugins.

            A list and not a table of per-variant deviations. Every other attribute here is a fact about the plugin, and
            the two plugins that have variants today state their six leaves identically apart from the variant token.
            A variant that ever needs its own deviation must be refused by whatever writes this call, not approximated:
            the residue's `descriptor:` part is keyed by `<plugin>/<variant>`, so the writer sees both sections and one
            of them would be silently dropped here.
        residue_lib_root_jars: the members whose jar this plugin puts at `lib/<module>.jar` although the convention says
            `lib/modules/<module>.jar`. A name list and never a path map: the path follows from the module name alone.
        residue_member_jars: the jars this plugin packs a member into, by member, where the layout names the jar itself.
            The whole jar set of the member, relative to the plugin's `lib/`, so a member two products place differently
            states both jars. The plugin's own main jar name puts the member back in the main jar.
        residue_merged_libraries: the module libraries a member's jar really merges, by member, where the layout took
            some of them out. The whole set and not the difference, so an empty list is a jar that merges nothing.
        residue_module_libraries: the libraries the layout packs that no member declares, by the module that owns them.
        residue_project_libraries: the same, for a project library, which has no owning module. Two attributes and not
            one list of rows, because Starlark has no optional field of a record: a list of two-field dicts would make
            `buildifier` reflow one row of a plugin and leave its neighbour inline, so the generator would carry a copy
            of the formatter's own line-breaking rules.
        residue_raw_members: the members this plugin does not hand over, although the module has a packing target for the
            repository. A second jar of this plugin holds the module.
        residue_separate_jars: the members that get a jar of their own although the convention puts them in the plugin's
            main jar.
        residue_vetoed_members: the members this plugin must not hand over, which takes the packing target away from
            every plugin. The veto is repository-global, because one packing target serves every plugin that ships the
            module, so one plugin stating the row is enough.
        **descriptor_attrs: every remaining `dev_dist_plugin_descriptor` attribute, forwarded to each *descriptor* leaf
            unchanged. Forwarded rather than enumerated so that this macro has no second copy of that rule's surface to
            keep in step; an attribute that rule does not have fails at the rule, which names it. An attribute both leaf
            macros take is refused instead - see `_SHARED_LEAF_ATTRS`.
    """

    # The converter's own input, gathered so that no line below can read one, and named so that a typo fails while Bazel
    # loads. `emitDevDistPlugin` writes them and `parseDevDistPluginResidue` reads them back. The sink is what keeps
    # `unused-variable` quiet for all eight at once, so a reader meets one suppression rather than eight.
    _ = {
        "residue_lib_root_jars": residue_lib_root_jars,
        "residue_member_jars": residue_member_jars,
        "residue_merged_libraries": residue_merged_libraries,
        "residue_module_libraries": residue_module_libraries,
        "residue_project_libraries": residue_project_libraries,
        "residue_raw_members": residue_raw_members,
        "residue_separate_jars": residue_separate_jars,
        "residue_vetoed_members": residue_vetoed_members,
    }  # buildifier: disable=unused-variable

    shared = [attr for attr in _SHARED_LEAF_ATTRS if attr in descriptor_attrs]
    if shared:
        fail("dev_dist_plugin: %s states %s, which would reach the descriptor leaf and not the content leaf" %
             (descriptor_module, shared))

    # What a content leaf is made of, stated once. A call that states none of these declares precisely what naming the
    # plugin's main module in the consumer's own `modules` declares, which is why `resolvePluginContent` emits no target
    # for it - see `dev_dist_plugin_content`. One dict, so the emptiness rule, the forwarding and the failure message
    # cannot disagree about which attributes the rule is over.
    content = {
        "content_modules": content_modules,
        "libraries": libraries,
        "prepacked_content_modules": prepacked_content_modules,
        "prepacked_jars": prepacked_jars,
        "prepacked_layout_jars": prepacked_layout_jars,
    }
    stated_content = [attr for attr, value in content.items() if value]
    if stated_content:
        dev_dist_plugin_content(descriptor_module = descriptor_module, **content)

    if not descriptor:
        # Everything below states a descriptor leaf, and there is none. Refused rather than ignored: a stated fact this
        # macro drops would be a fact the plugin's own package appears to carry and no rule ever reads.
        if main_module:
            fail("dev_dist_plugin: '%s' states a main module and no descriptor, so it asks for no descriptor leaf" % main_module)
        if variants:
            fail("dev_dist_plugin: %s states layout variants and no descriptor" % descriptor_module)
        if descriptor_attrs:
            fail("dev_dist_plugin: %s states %s and no descriptor" % (descriptor_module, sorted(descriptor_attrs.keys())))
        if not stated_content:
            # A call that expands to nothing. It cannot happen from a generator - both halves emit only what they
            # resolved - so this catches a hand edit that removed the last fact and left the call behind.
            fail("dev_dist_plugin: %s states neither content (%s) nor a descriptor" % (descriptor_module, content.keys()))
        return

    if not main_module:
        fail("dev_dist_plugin: %s states a descriptor, and a descriptor leaf's target name carries the main module" % descriptor_module)

    # One leaf per variant, and the empty variant is a variant: `dev_dist_plugin_descriptor_platforms` answers it with
    # every platform, and `dev_dist_plugin_descriptor_target_name` leaves the name unsuffixed.
    for variant in variants if variants else [""]:
        dev_dist_plugin_descriptor(
            main_module = main_module,
            descriptor_module = descriptor_module,
            descriptor = descriptor,
            variant = variant,
            **descriptor_attrs
        )
