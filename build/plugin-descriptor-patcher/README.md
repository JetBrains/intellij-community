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
| `includes` | **owed**. `xi:include` needs the plugin's descriptor closure |
| `contentModules` | **owed**. The embedding needs the same closure and the content-module filter |
| `textPatcher` | **never**, for the reason `rawTextPatcher` never does |

The binary **refuses** a descriptor that would reach `includes` or `contentModules`, rather than writing a text that
silently lost an include or an embedded body. A wrong descriptor fails at class-load time inside the IDE, where nothing
here can see it.

**No Bazel rule runs this binary.** `dev_dist_plugin_descriptor` still runs the JVM tool, and that tool stays the
reference until the two structural stages land.

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

The artifact is not committed, because it is megabytes of one product's descriptors. Over `//build:idea_air_dist` the
three arms report 163 of 163 for `rawTextPatcher` to `reserialized`, 163 of 163 for `reserialized` to `stamps`, and 43
of 43 for the class (a) stamps text against `patched`, which is the text the assembly put in the plugin's main jar.

`<dir>/request.txt` states the stamp scalars, one `key=value` a line, because the artifact records no scalar. A
`key@<main module>=value` line states one plugin's deviation. Three plugins of this product need one, for a per-layout
version suffix. A wrong scalar is loud: it moves the `<version>` of every recorded plugin.

An artifact of the older schema recorded a byte count and no text. The gate then reports the stages it cannot answer
as absent and fails, rather than passing a case against nothing.

`./build/dev-dist.cmd descriptors` is the other half of the story: it compares the JVM tool's output against the same
recorded artifact. This package does not replace that gate and does not depend on it.
