# content-module-packer

Packs the `lib/` jars of a product's content modules, one jar per module, from already-built module and library jars.
It is the tool behind `content_module_jar` (`../../platform/build-scripts/bazel-rules/content_module_jar.bzl`) — 2 524
targets across the repository — which names it directly, as a private attribute.

Directly, because it lives here. The recipe used to be five attributes on the `jvm_library` itself, and the packer had
to be pushed in through a `--@rules_jvm//:content-module-packer` `label_flag` whose default in `rules_jvm` was a stub
that failed at execution time: `jvm_library` is a `rules_jvm` rule, and `rules_jvm` ships as a consumable archive that
may not name a label in a repository consuming it. Moving the packer into `@community//`, which both the community and
the ultimate tree can name, removed the flag, its stub, its `ContentModulePackerInfo` provider — a provider rather
than a plain label only because the two implementations had different action *shapes* — and its `.bazelrc` line.

## What is verified

Byte-identity with the Kotlin implementation it replaced (`@rules_jvm//content-module-packer`, deleted in the same
commit), because the distribution consumes these jars and a packer that drifts surfaces at class-load time in the IDE
and nowhere earlier:

- **192 of 192 jars byte-identical** when built as real Bazel actions over all 2 524 targets, compared against jars
  snapshotted from the Kotlin packer beforehand.
- **26 real recipes** taken from the live graph with `--verify-crc`, so every carried-over CRC was also proved to
  describe its data.
- **4 constructed recipes** covering what the real sample could not reach, because every library-merging recipe in it
  pointed at an `http_file` repo that was not materialised: library+module merge, the full library drop-filter,
  DEFLATED→inflate, first-source-wins duplicates, `keep-manifest`, and `rewrite-boot-class-path` with its index
  asymmetry. Both packers run on the same recipe; output compared byte for byte.
- The `__index__` hashes against the **2 050 reference vectors** in `internal/xxh3`, which are the same C reference
  values `XxHash3Test.java` holds the platform's own implementation to.

Those were one-off comparisons against an implementation that no longer exists. Three gates stand, and they are what a
change to this tool has to pass:

- **`./build/dev-dist.cmd jars` — 415 byte-identical, 0 differing.** The standing comparison against `JarPackager`, and
  the only thing that still holds the two producers of these bytes to each other.
- **`bazel test @community//build/content-module-packer/internal/worker:worker_test`**, also a second. It holds the
  worker protocol's wire format to byte vectors written out from the schema by hand, plus one framed `WorkRequest`
  captured from a live packing action -
  so a codec change is caught by something that shares no code with the codec. It does not replace the jar gates and
  they do not replace it: a vector cannot prove Bazel *encodes* what we think it does, and only a build that really
  packs can - which the gate above does, since every jar it compares came out of a live worker action. Conversely no jar
  comparison can reach a protocol bug that drops or misroutes a request rather than corrupting a jar.
- **`bazel test @community//build/content-module-packer/internal/jarpack:jarpack_test`**, which runs in a second. Six
  frozen digests over the recipes the real sample cannot reach, plus the structural claims - normalised headers, STORED
  everywhere, no extra fields, the
  index pointer in the end record - asserted by `archive/zip`, an implementation that shares no code with this writer.
  Any change to the reader, the writer or the index fails here before it reaches a distribution.

## Performance

Measured on this checkout, darwin arm64, 18 cores, 2 524 jars, warm tree. Every row is a real cold pack: the packer
binary genuinely changed, so all 2 524 actions re-keyed, and no row pays for an analysis-cache discard.

| configuration | wall clock | strategy |
| --- | --- | --- |
| one process per action, cached - what the first wiring did | 38.4 s | 2 524 `local` |
| multiplex worker, cached | 26.0 s | 2 524 `worker` |
| multiplex worker, `no-cache` | **6.6 s** | 2 524 `worker` |

Three things got it there, and only the first two moved the build.

1. **The worker protocol** (`internal/worker`, the proto dialect). The cost it amortises was never this binary's
   startup - a static Go binary starts in about two milliseconds, which is the reasoning that produced the one-shot
   design and the reasoning that was wrong - but Bazel's per-spawn envelope, which at this action count is the build.
   `--worker_max_multiplex_instances=PackContentModuleJar=HOST_CPUS*2` is part of it: that flag is how many requests a
   multiplex worker may be given in parallel, and left unset the packer is a worker pool of a handful.

   The dialect was JSON first and is proto now, and that switch is **not** in the table because its effect is below the
   table's resolution - the rows here are 6.6 s where `../../common.bazelrc` records 6.7 s for the same configuration,
   and the protocol is a slice of the envelope rather than of the packing. It was taken because proto is Bazel's
   default and what every other worker in this repository speaks, and because the decoder can then step over `inputs` -
   three quarters of every request's bytes, and read by nothing - instead of lexing them. What *is* measured is the
   decode itself, on a request captured from a real action: **57 ns and two allocations**, against a payload the JSON
   dialect had to materialise into a slice of structs and six strings. Do not go looking for that in a wall clock. It is
   `--worker_max_multiplex_instances` and `no-cache` that moved this build, and nothing else here has.
2. **Not caching the action at all** (`--modify_execution_info=PackContentModuleJar=+no-cache`, with the numbers in
   `../../common.bazelrc`). The remote leg was 16.7 s of the 26.0 s row for an action that does about a millisecond of
   work. This is why the scrubbing route was not taken: it would have turned those misses into *hits*, and a hit - a
   round trip plus the download of a 0.25-1 MB jar - cannot beat merging one locally. The disk leg is a loss too, by
   less: populating it costs 2.6 s and 3.3 GB per cold pack to save the 1.4 s a hit is worth, in the only case Bazel's
   own action cache does not already cover for free.
3. **Mapping the source jars** instead of reading them entry by entry. This one does *not* move the cold pack, and it is
   in the tree for the CPU rather than the wall clock: `pread` was 61.8 % of the packer's 5.91 s of CPU, one syscall per
   entry for two useful bytes of local header and a second for the data. The merge is now 2.44 s of CPU, and the whole
   tranche 0.55 s where it was 0.86 s.

**What is left is Bazel's envelope, and there is little in it.** The floor is 5.3 s - what the same build costs when
every jar is served from the disk cache and nothing is packed at all - against 6.6 s for packing them. So the merge is
about a second of the cold pack, and the remaining 5.3 s is per-action cost over 2 524 actions that
[ADR 0001](../decisions/0001-cacheable-dev-dist-content-module-jars.md) deliberately keeps at one action per module.
**Do not batch jars into fewer actions** to shrink it: that ADR rejects it with evidence, because one action per module
is the invalidation boundary the whole tranche exists for.

### How to measure it again

Four things about this that are easy to get wrong, and cost an afternoon each:

- **A comment-only edit does not re-key anything.** A Go build is reproducible, so the binary is byte-identical and all
  2 524 actions are action-cache hits. Re-keying needs a byte in the binary to move - a live string literal will do.
- **`--modify_execution_info` re-keys every action of a mnemonic** (execution info is in the action key), which makes it
  a convenient lever - but *changing* it discards the analysis cache, and re-analysing 2 524 targets was 8.2 s on its
  own. Hold the flag constant within a comparison, or the difference measured is the analysis.
- **It does not change the *cache* key.** A run with a new execution-info key and an unchanged binary is served from the
  disk cache, packs nothing, and looks fast. Point `--disk_cache` at an empty directory to force a genuine miss.
- **The one-shot mode is the profiler.** It accepts thousands of `output=` groups in one file, so the whole tranche
  profiles in one process with no Bazel in the loop:

      bazel aquery --output=text --include_param_files \
        'mnemonic("PackContentModuleJar", //... + @community//...)'

  gives every recipe as its expanded command line; concatenate them into one flag file, rewrite the `output=` paths so
  a run does not overwrite `bazel-out`, drop the groups whose sources are unmaterialised `http_file` repos, then run it
  from the exec root with `--cpuprofile=`. That flag exists for this and is refused in worker mode.
