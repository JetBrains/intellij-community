# Local Disk Jar Cache

This directory implements the local on-disk cache for built jar payloads in build scripts.

The normative behavior contract is defined in [`SPEC.md`](./SPEC.md).

## File Map

```
LocalDiskJarCacheManager.kt
  - public manager entry points (`computeIfAbsent`, `cleanup`)
  - key computation
  - key-slot lock orchestration

LocalDiskJarCacheEntryStorage.kt
  - cache hit path (`tryUseCacheEntry`)
  - cache miss write path (`produceAndCache`)
  - target materialization (hard link or copy)

LocalDiskJarCacheMetadata.kt
  - metadata read/validate/decode
  - metadata write/encode
  - metadata to producer notification

LocalDiskJarCacheMaintenance.kt
  - periodic cleanup
  - two-pass stale entry deletion
  - legacy format purge

LocalDiskJarCacheCommon.kt
  - shared constants
  - path helpers
  - common fs helpers
```

## On-Disk Layout

```
<cacheDir>/
  v0/
    entries/
      <shard2>/
        <key>__<target-file-name>.jar
        <key>__<target-file-name>.jar.meta
        <key>__<target-file-name>.jar.mark     (optional)
    .last.cleanup.marker
    .cleanup.scan.cursor

  .legacy-format-purged.0
```

`<shard2>` is `key.take(2)` to spread entries across directories.

## Key Computation

`computeIfAbsent` computes a 128-bit key using `xxh3_128` over:

1. Source digests (`SourceAndCacheStrategy.updateAssetDigest`)
2. Source count
3. `CACHE_VERSION`
4. Target file name (`targetFile.fileName`)
5. Producer-specific digest (`SourceBuilder.updateDigest`)

## Why key includes target name

The cache intentionally includes target file name in the key and file name. This means
the same content built as `first.jar` and `second.jar` produces separate cache entries.

Rationale for this build pipeline:

- target-name collisions are easier to reason about when cache entries stay human-readable
- cross-name content dedup is low value in current packaging flows
- debugging is simpler because cache filenames map directly to expected output names

Example:

- `entries/ab/<key>__idea.jar`
- `entries/ab/<other-key>__idea-test.jar`

The final key string is:

```
<least-significant-64-as-unsigned-radix36>-<most-significant-64-as-unsigned-radix36>
```

## Request Flow

```
computeIfAbsent
  |
  +-- build key + entry paths
  |
  +-- optimistic hit (no file lock)
  |     - allowed only if useCacheAsTargetFile == false
  |     - skipped if `<key>__<name>.jar.mark` exists
  |     - validate metadata + payload
  |     - materialize target (link/copy)
  |     - touch metadata mtime (throttled in-memory, no read-before-touch)
  |
  +-- fallback under in-process per-key lock
        - re-check hit path (strict, delete invalid)
        - on miss: produceAndCache
```

## Locking Model

Per key, one in-process striped mutex layer (`StripedMutex`) is used.

`keySlot = leastSignificantBits(hash128) mod 4096`. This serializes operations per key slot inside one process.

Cross-process writes are lock-free: duplicate producers for the same key are allowed. Publication is `payload -> metadata`, where metadata is the commit marker.

## Metadata Semantics

`<key>__<name>.jar.meta` contains:

- magic
- schema version
- source count
- per-source: size, hash, optional native file names

Metadata file modification time is treated as last access timestamp for retention.

## Architecture Decisions

### Mtime Contract And Touch Throttle

- Metadata file `mtime` is the authoritative access-time signal for stale cleanup and external cache retention.
- Cache hits never read `mtime` before touching; they use in-memory throttling and periodic `setLastModifiedTime`.
- Marked entries bypass throttle to make reaccess visible immediately and avoid stale second-pass deletion.
- If metadata touch fails, cleanup applies a bounded in-memory grace window before treating the entry as stale.

### Cleanup Candidate Queue Plus Reserved Scan

- Cleanup consumes a bounded in-memory candidate queue for hot entries.
- Every cleanup run also reserves scan capacity for shard-window traversal (`.cleanup.scan.cursor`) so cold or misplaced entries are still discovered.
- Shard scan budget is adaptive (`max(64, ceil(10% of shard count))`) and still capped by current shard count.

### Metadata Safety Limits

- Native metadata decode is guarded by hard limits to prevent large allocations from corrupted files:
  - `maxNativeFileCount = 65_536`
  - `maxNativeFilesBlobSizeBytes = 8 MiB`
- Source count is validated against remaining metadata size before source-record array allocation.
- Violations are treated as invalid metadata and the cache entry is rebuilt.

### Temp File Name Cap

- Final entry names are capped to common filesystem limits.
- Temporary payload/metadata sibling file names are also capped to avoid miss-path failures on long target names.

## Cleanup Algorithm

Cleanup runs at most once per day (`.last.cleanup.marker`):

1. Drain a bounded queue of recently touched entry stems.
2. Always reserve a slice of each run for bounded shard-window scan (`.cleanup.scan.cursor`).
3. Delete malformed-key candidates as invalid entry garbage.
4. Lock each well-formed candidate key.
5. If metadata is missing, delete sibling entry files.
6. If stale and not marked, create `.mark`.
7. If stale and already marked, delete sibling entry files.
8. If fresh, remove mark file.

This is a two-pass stale deletion to avoid removing entries that become active again.

## Legacy Purge

At startup, once per epoch marker (`.legacy-format-purged.<version>`):

- Delete old version directories matching `v<digits>` except current.
- Delete legacy flat metadata files (`*.m`) and paired jars (`*.jar`).
- Delete old marker side files for those legacy entries.
- Delete legacy root cleanup marker if present.

## Versioning

`CACHE_VERSION` (`LocalDiskJarCacheManager.kt`) defines cache namespace.

Bump it when build-script semantics that affect jar contents change.

## Running Tests

Run `jarCache` tests from repository root with the build-scripts test module explicitly selected:

```bash
./tests.cmd \
  -Dintellij.build.test.main.module=intellij.platform.buildScripts.tests \
  '-Dintellij.build.test.patterns=org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheManagerTest;org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheCleanupTest'
```

Run a single class:

```bash
./tests.cmd \
  -Dintellij.build.test.main.module=intellij.platform.buildScripts.tests \
  -Dintellij.build.test.patterns=org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheCleanupTest
```
