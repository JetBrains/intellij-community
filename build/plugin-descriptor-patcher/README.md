# plugin-descriptor-patcher

Writes the `META-INF/plugin.xml` that a plugin's main jar receives.

This is the Go port of `applyPluginDescriptorPatch`
([`PluginXmlPatcher.kt`](../../platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt)).
[ADR 0006](../../../build/decisions/0006-content-module-in-jar-out-composer-places-it.md) puts the executors in Go and
keeps the JVM in the generator only. A patched descriptor feeds every plugin main jar, so a JVM action for it sits on
the build's critical path, which is what the ADR rules out.

`content-module-packer` is the neighbour to read for the conventions: a deterministic writer, a hand-maintained
`BUILD.bazel`, and a byte gate against the code it replaces.

## The state of the port

The patch has seven stages. `DevDistDescriptorStage` names them, and a dev assembly records the size and the text of
each one per plugin.

| stage | here |
|---|---|
| `source` | the input |
| `rawTextPatcher` | **never**. It is a per-layout Kotlin lambda, so a plugin that states one is not a candidate |
| `reserialized` | `internal/descriptorxml`. `JDOMUtil.load` and `JDOMUtil.write`, byte for byte |
| `stamps` | `internal/stamps`. `doPatchPluginXml` |
| `includes` | `internal/structural`. `resolveIncludes` over a seeded descriptor cache |
| `contentModules` | `internal/structural`. `resolveAndEmbedContentModuleDescriptor`, driven by the plan |
| `textPatcher` | **never**, for the reason `rawTextPatcher` never does |

The two structural stages read **other** descriptors, and they read them from the files the action declares and from
nothing else. Every other step of the platform's search needs a JPS project model, and
`DevDistPluginDescriptorMain.kt` refuses to load one. So an include no declared file answers **fails**. The failure
names the load path and every declared one, because the fix is always a missing entry in the generated plan.

The stage that would silently lose bytes is the content-module filter, and the plan drives it. The plan states the
survivors in order. The action refuses a descriptor that lists them in another order: the two are joined by position,
and a wrong join takes the wrong `separate-jar` verdicts in silence.

**This binary is the executor of `dev_dist_plugin_descriptor`**
([`dev_dist_plugin_descriptor.bzl`](../../platform/build-scripts/bazel-rules/dev_dist_plugin_descriptor.bzl)). The JVM
tool it replaced, `@community//platform/build-scripts/bazel-rules/dev-dist-plugin-descriptor`, stays as the second
producer. Both take one request: the same option spelling and the same `--flagfile` parameter file, which is why the
swap was an executable swap.

## Why the round trip is the load-bearing half

The platform reads a descriptor with `JDOMUtil.load` and writes each stage with `JDOMUtil.write`. That pair rewrites
whitespace, attribute quoting and CDATA on **every** descriptor before any patch runs. Over one product's 163 plugins
the round trip alone rewrites 162 texts, for −71 718 bytes. So a port that gets the patch right and the serializer
wrong produces the wrong bytes for every plugin.

`internal/descriptorxml` therefore mirrors both halves from the platform's own source, with a `file:line` for each rule:

- the format is `Format.getCompactFormat().setIndent("  ").setTextMode(TRIM).setLineSeparator("\n")`;
- there is no XML declaration and no trailing newline, because `output(Element, Writer)` prints the element and nothing
  else;
- `MyXMLOutputter` escapes `"` inside element text, which a standard serializer does not, and never escapes `'`;
- the reader deletes every comment, every processing instruction and every whitespace-only text run;
- the reader coalesces text, so a CDATA section arrives as plain text and only the patch creates one back.

## The gates

    bazel test @community//build/plugin-descriptor-patcher/... --test_arg=-test.v

The curated cases are the committed gate. Every expectation in them is a text the platform produced on a real
classpath, one construct a case.

The whole-population gate is `internal/stamps/population_test.go`. **It reads the artifact a dev-distribution
assembly writes**, and skips unless `IJ_DESCRIPTOR_CASES` names a `*.patched-descriptors.json` or a directory of them.
`DevDistPatchedDescriptors` holds the text of every stage of the patch, so a change to `JDOMUtil` or to
`doPatchPluginXml` reaches this gate through the assembly that produced the file.

Refresh the fixture, and copy the files out at once, because a later Bazel run with the flag off prunes them:

    ./bazel.cmd build //build:idea_air_dist \
      --@community//platform/build-scripts/bazel-rules:dev_dist_patched_descriptors \
      --output_groups=+dev_dist_patched_descriptors
    cp out/bazel-bin/build/*.patched-descriptors.json <dir>/

    bazel test @community//build/plugin-descriptor-patcher/internal/stamps:stamps_test \
      --test_env=IJ_DESCRIPTOR_CASES=<dir> --test_arg=-test.v

The artifact is not committed, because it is megabytes of one product's descriptors.

**Which arm you build decides what this gate can cover.** A fragment that is handed a produced descriptor reads that
file and runs no stage of the patch, so its record states `"origin": "produced"` and holds no stage text. The gate
skips such a record by name and prints the count. Over `//build:idea_air_dist` as it stands that is 43 of the 163, and
two of the four arms are then empty. Measured on 2026-08-28:

    skipped                        43 records, which the fragment read from a produced descriptor
    rawTextPatcher -> reserialized 120 identical, 0 differing, 0 absent of 120
    reserialized -> stamps         120 identical, 0 differing, 0 absent of 120
    stamps against patched           0 identical, 0 differing, 0 absent of 0
    the structural stages inert      0 identical, 0 differing, 0 absent of 0
    structural elsewhere           120 records, whose structural stages moved bytes

To cover all 163, build the fixture from the arm that computes every descriptor: empty `plugins` and every deviation
list of the product entry in `build/dev_dist_plugin_descriptors.bzl`, re-run the generator, build, copy the files out,
and restore the file. That arm runs every stage of every descriptor, so its artifact holds every stage text. Measured on
2026-08-27 over one artifact of that shape, the gate reports 163 of 163 for `rawTextPatcher` to `reserialized`, 163 of
163 for `reserialized` to `stamps`, and 43 of 43 for the class (a) stamps text against `patched`. That last text is what
the assembly put in the plugin's main jar. Keep the artifact you build there: the declaring arm overwrites it.

**The two structural stages have no byte arm in this gate, by construction.** They read other descriptors, and the
artifact records one plugin's stage texts and no descriptor closure. So a record whose structural stages moved bytes is
counted and skipped here. The fourth arm proves inertness only. Over a descriptor the platform's own stages did not
change, the Go stages must change nothing either.

`<dir>/request.txt` states the stamp scalars, one `key=value` a line, because the artifact records no scalar. A
`key@<main module>=value` line states one plugin's deviation. Three plugins of this product need one, for a per-layout
version suffix. A wrong scalar is loud: it moves the `<version>` of every recorded plugin.

An artifact of the older schema recorded a byte count and no text. The gate then reports the stages it cannot answer
as absent and fails, rather than passing a case against nothing.

## The two-producer gate, which is where the structural stages are proved

    ./build/dev-dist.cmd descriptors --two-producer

Every `dev_dist_plugin_descriptor` target runs both producers over one parameter file, and the mode compares their
bytes per plugin. It reads no artifact and assembles no distribution. So **every** plugin of the population is
compared, the ones whose fragment now reads the produced file included. Those are the ones the artifact gate can only
hold out. On 2026-08-28 it reported **158 of 158 byte-identical, 0 differing**.

That is the coverage the produced-descriptor switch had taken away, and it is back. The earlier statement here, that no
arm could give it back, was wrong about the mechanism. The missing piece was a second producer inside the rule, not a
fragment that computes the text anyway.

Two negative controls of that gate, each proved both ways. An include placed at position 0 rather than at its own
position makes the content-order assertion refuse `intellij.database.plugin` outright, and the build fails. An embedded
descriptor written as text rather than as CDATA gives 43 identical and 115 differing. That is exactly the split between
the plugins that embed no content module and the ones that embed at least one.

## The artifact gate, and what it now compares

    ./build/dev-dist.cmd descriptors

It compares the descriptor a `dev_dist_plugin_descriptor` action wrote against the text a dev assembly recorded for the
same plugin. Since the swap, this binary produced the compared bytes. On 2026-08-28 it reported **115 of 115
byte-identical, 0 differing**, with 48 plugins held out by name and reason.

## Per-action cost, and why there is no worker

Measured on darwin arm64 on 2026-08-28, over `//build/dev-dist-descriptors:idea_plugin_descriptors` with the action's
output deleted and the caches bypassed (`--disk_cache= --noremote_accept_cached`):

| producer | 158 actions, wall | 158 actions, critical path | one action |
|---|---|---|---|
| this binary | 5.96 s | 0.81 s | 76 to 141 ms |
| the JVM tool | 29.2 s | 12.61 s | 472 to 764 ms |

The largest descriptor of the product, `intellij.database.plugin` with 116 content modules and a 470 KB output, is
137 ms. The smallest is 76 ms. So the cost is the sandbox and not the process start, and a worker cannot remove a
sandbox. `content-module-packer` is a worker because it runs ~2 500 actions; this rule runs one per plugin.
