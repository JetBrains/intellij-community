# plugin-descriptor-writer

Writes plugin descriptors, embedded product descriptors, and application info for the embedded JetBrains Client.

This is the Go port of `applyPluginDescriptorPatch`
([`PluginXmlPatcher.kt`](../../platform/build-scripts/src/org/jetbrains/intellij/build/impl/PluginXmlPatcher.kt)).
[ADR 0006](../../../build/decisions/0006-content-module-in-jar-out-composer-places-it.md) assigns build execution to Go.
The generator and production assembly remain in Kotlin.

`content-module-packer` is the neighbour to read for the conventions: a deterministic writer, a hand-maintained
`BUILD.bazel`, and a byte gate against the code it replaces.

## The state of the port

The patch has seven stages, in the order `applyPluginDescriptorPatch` runs them.

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
one line. `marker:<literal>:<replacement>` states a plain replacement. The Go and Kotlin patches replace the first occurrence of a
plain string, because `checkedReplace` compiles the literal as a regular expression and Go's RE2 is not Java's
`Pattern`; the generator refuses a row that is not inert in both. An unknown shape fails the action.

The two structural stages read only declared descriptor files and jars. They load no JPS project model.
A required descriptor without a declared input fails the action. The failure names the load path and the declared inputs.

One plugin reads a descriptor no production source root holds: the Kotlin compiler ships
`META-INF/analysis-api/analysis-api-fir.xml` and five more inside library jars. The plan names the library container
that groups those jars, and it states the entry. The container's jars are the declared inputs, and the request holds one
`--plugin-descriptor-in-jar` row for each entry and jar, in the container's own jar order. The writer seeds the cache
from those rows and take the first jar that answers. A container whose jars all miss fails the action, and the failure
names every jar it asked. The load path is also the entry, because `toLoadPath` strips the leading `/`.

The stage that would silently lose bytes is the content-module filter, and the plan drives it. The plan states the
survivors in order. The action refuses a descriptor that lists them in another order: the two are joined by position,
and a wrong join takes the wrong `separate-jar` verdicts in silence.

## Operations

This binary executes three Bazel rules:

| Rule | Mode |
|---|---|
| [`dev_dist_plugin_descriptor`](../../platform/build-scripts/bazel-rules/dev_dist_plugin_descriptor.bzl) | No mode flag. Patches ordinary plugin descriptors. |
| [`dev_dist_embedded_product_descriptor`](../../platform/build-scripts/bazel-rules/dev_dist_embedded_product_descriptor.bzl) | `--embedded-product`. Resolves includes and embeds content modules. |
| [`dev_dist_frontend_application_info`](../../platform/build-scripts/bazel-rules/dev_dist_frontend_application_info.bzl) | `--application-info`. Applies product values to the client template. |

Each operation accepts direct arguments or a multiline `--flagfile`. The rules keep their arguments, output names, and action mnemonics.
The Kotlin executable is removed. There is no second producer.

## Windows manifest

The Windows exe embeds an application manifest with `requestedExecutionLevel level="asInvoker"`. Without a manifest,
Windows Installer Detection treats an exe whose name contains `patch`, `setup`, `install` or `update` as an installer
and demands elevation. A non-elevated Bazel action then fails with `CreateProcess` error 740. The manifest tells Windows
that the exe runs with the token of its parent, so the heuristic does not run and the name no longer matters. The name
still avoids the four words as a second guard.

`manifest.xml` is the source of truth. `manifest_windows_amd64.syso` and `manifest_windows_arm64.syso` are the COFF
resource objects the Go linker embeds. The `_windows_<arch>` suffix is a Go build constraint, so the Linux and macOS
builds do not see them. Regenerate both after a change to `manifest.xml`:

    cd community/build/plugin-descriptor-writer
    ../../tools/go.cmd run github.com/akavel/rsrc@v0.10.2 -manifest manifest.xml -arch amd64 -o manifest_windows_amd64.syso
    ../../tools/go.cmd run github.com/akavel/rsrc@v0.10.2 -manifest manifest.xml -arch arm64 -o manifest_windows_arm64.syso

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

    bazel test @community//build/plugin-descriptor-writer/... --test_arg=-test.v

The curated cases are the committed gate. Every expectation in them is a text the platform produced on a real
classpath, one construct a case.

The tests for the embedded product and application info compare bytes with saved Kotlin outputs, including the absent final newline.
`descriptor_rule_tests` also checks both rules, their Go executable, and their generated files.

## Where the structural stages are proved

The curated cases of `internal/structural` and `internal/descriptorxml` are the committed gate. Each case states one
rule, with a negative control per branch, so a failure names the rule. Two more guards run over real descriptors.
`//build:idea_dev_descriptor_leaf_build_test` builds a sample group of leaves, so a request the rule cannot state fails
before a distribution builds. `./build/dev-dist.cmd snapshot diff` compares every plugin main jar of a composed
distribution against a recorded baseline. That guard runs over one producer. It is a regression guard, not a second
producer.

**The snapshot is also the control for a wrong marker row.** The assembly computes the text in the arm that does not
read the produced descriptor (`fragment_reads` of the plan). A row that names `linux`/`x86_64` where the darwin variant
should name `mac`/`arm64` moves three files of the snapshot, and the diff names them.

Until 2026-09-14 the rule also ran a JVM reference producer over the same parameter file, and
`./build/dev-dist.cmd descriptors --two-producer` compared the two byte for byte. Its last figures are 174 of 174
byte-identical on two consecutive runs, recorded in `Item 5: the three removal conditions` of
`build/dev-dist-measurements.md`. IJAI-955 deleted the reference producer and the command together.

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
