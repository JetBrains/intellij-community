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
| `rawTextPatcher` | `internal/markers`. The plan's marker table, which is what a `DescriptorMarkerPatcher` states as data |
| `reserialized` | `internal/descriptorxml`. `JDOMUtil.load` and `JDOMUtil.write`, byte for byte |
| `stamps` | `internal/stamps`. `doPatchPluginXml`, plus the plan's version suffix |
| `includes` | `internal/structural`. `resolveIncludes` over a seeded descriptor cache |
| `contentModules` | `internal/structural`. `resolveAndEmbedContentModuleDescriptor`, driven by the plan |
| `textPatcher` | **never**. It runs after the stamps, over the text this body produced, so no table states it |

A layout whose raw patch is a lambda rather than a `DescriptorMarkerPatcher` is held out of the population by name. Two
marker row shapes exist. `os-arch:<osId>:<marketplaceName>` names the operating system and the architecture, and
`osArchDescriptorMarker` owns the replacement text - it holds a newline the request's parameter file could not carry on
one line. `marker:<literal>:<replacement>` states a plain replacement. Both producers replace the first occurrence of a
plain string, because `checkedReplace` compiles the literal as a regular expression and Go's RE2 is not Java's
`Pattern`; the generator refuses a row that is not inert in both. An unknown shape fails the action.

The two structural stages read **other** descriptors, and they read them from the files the action declares and from
nothing else. Every other step of the platform's search needs a JPS project model, and
`DevDistPluginDescriptorMain.kt` refuses to load one. So an include no declared file answers **fails**. The failure
names the load path and every declared one, because the fix is always a missing entry in the generated plan.

One plugin reads a descriptor no production source root holds: the Kotlin compiler ships
`META-INF/analysis-api/analysis-api-fir.xml` and five more inside library jars. The plan names the library container
that groups those jars, and it states the entry. The container's jars are the declared inputs, and the request holds one
`--plugin-descriptor-in-jar` row for each entry and jar, in the container's own jar order. Both producers seed the cache
from those rows and take the first jar that answers. A container whose jars all miss fails the action, and the failure
names every jar it asked. The load path is also the entry, because `toLoadPath` strips the leading `/`.

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

Write `<dir>/request.txt` too. The artifact carries no stamp scalar, so a fixture without that file runs with the
arbitrary defaults of `defaultRequest`, and the stamps arm then reports every record differing on its `<version>`. The
scalars of `//build:idea_air_dist` are in the `defaultRequest` doc comment.

The artifact is not committed, because it is megabytes of one product's descriptors.

**Which arm you build decides what this gate can cover.** A fragment that is handed a produced descriptor reads that
file and runs no stage of the patch, so its record states `"origin": "produced"` and holds no stage text. The gate
skips such a record by name and prints the count. Every fragment of `//build:idea_air_dist` now reads, so that is 162
of the 163, and the arms cover the one record the assembly still computes. Measured on 2026-08-28:

    skipped                        162 records, which the fragment read from a produced descriptor
    rawTextPatcher -> reserialized   1 identical, 0 differing, 0 absent of 1
    reserialized -> stamps           1 identical, 0 differing, 0 absent of 1
    stamps against patched           0 identical, 0 differing, 0 absent of 0
    the structural stages inert      0 identical, 0 differing, 0 absent of 0
    structural elsewhere             1 records, whose structural stages moved bytes

That one is `intellij.devkit`, the additional plugin the plan does not cover. `intellij.dev` left this set when its
descriptor got a label - see `containingBazelPackageLabel` in `platform/buildScripts/src/productLayout/devDistPluginDescriptorPlan.kt`.

To cover all 163, build the fixture from the arm that computes every descriptor. Set `fragment_reads` of the product
entry in `build/dev_dist_plugin_descriptors.bzl` to `"none"`, build, copy the files out, and restore the file. That one
key is the switch: it keeps every producer and takes only the declaration away, so the fragments run every stage of every
descriptor and the artifact holds every stage text. Emptying `descriptor_targets` instead removes the producer, and the two-producer
gate below then has nothing to compare. Measured on 2026-08-27 over one artifact of that shape, the gate reports 163 of
163 for `rawTextPatcher` to `reserialized`, 163 of 163 for `reserialized` to `stamps`, and 43 of 43 for the class (a)
stamps text against `patched`. That last text is what the assembly put in the plugin's main jar. Keep the artifact you
build there: the declaring arm overwrites it.

An artifact of that arm also needs a `version@<main module>` line for every plugin whose layout appends a version
suffix, because the record then carries the computed version and the gate's scalars are the plan's.

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
hold out. A plugin whose descriptor differs by operating system or architecture has one entry per layout variant, and
each variant is a pair of its own: the two files are joined by the whole path, not by the name they share. On 2026-08-28
it reported **173 of 173 byte-identical, 0 differing**, over all 163 bundled plugins.

**This gate cannot catch a wrong marker row**, because both producers read the same row. The control for the table is a
two-arm whole-distribution snapshot: the assembly computes the text in the arm that does not read. A row that names
`linux`/`x86_64` where the darwin variant should name `mac`/`arm64` moves three files of the snapshot, and the diff
names them.

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
same plugin. Every fragment now reads the produced file, so on 2026-08-28 it reported **0 compared** and held all 163
plugins out by name and reason, 161 of them under "the fragment read the produced descriptor". That is the state the
gate exists to reach, and it says so and names the two-producer mode. The gate still fails on a dropped declaration,
which puts its plugin straight back into `compared`, and on an over-declared one, which shows up as a target the
artifact does not claim.

A plugin bundled for one operating system alone is neither. `intellij.wsl.remoteSdk` has a target and no record in a
macOS artifact, and the gate reports it under "built for another platform" rather than as over-declaration. The file's
own directory depth states whether the plan restricted its entry, so the gate keeps no platform vocabulary of its own.

## Per-action cost, and why there is no worker

Measured on darwin arm64 on 2026-08-28, over `//build/dev-dist-descriptors:idea_plugin_descriptors` with the action's
output deleted and the caches bypassed (`--disk_cache= --noremote_accept_cached`):

| producer | 158 actions, wall | 158 actions, critical path | one action |
|---|---|---|---|
| this binary | 5.96 s | 0.81 s | 76 to 141 ms |
| the JVM tool | 29.2 s | 12.61 s | 472 to 764 ms |

The largest descriptor of the product, `intellij.database.plugin` with 116 content modules and a 470 KB output, is
137 ms. The smallest is 76 ms. So the cost is the sandbox and not the process start, and a worker cannot remove a
sandbox. `content-module-packer` is a worker because it runs ~2 500 actions; this rule runs one per plugin variant.

The population is 173 actions since 2026-08-28, because two plugins state one entry per (os, arch). The figures above
were taken over 158 and were not re-taken; a per-action cost that does not depend on the descriptor's size does not
depend on the population either.
