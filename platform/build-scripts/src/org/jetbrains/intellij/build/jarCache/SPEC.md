# Local Disk Jar Cache Specification

## Status

This document is the normative behavior contract for `org.jetbrains.intellij.build.jarCache`.
`README.md` is informational; if there is a conflict, this spec wins.

## Goals

- Reuse previously built jar payloads when source digests match.
- Keep cleanup bounded and eventually scan cold entries.
- Allow lock-free cross-process operation.

## Key And Entry Identity

`computeIfAbsent` computes a 128-bit key from:

1. source digests
2. source count
3. `CACHE_VERSION`
4. target file name
5. producer digest

Persisted key format:

```
<lsb-as-unsigned-radix36>-<msb-as-unsigned-radix36>
```

Entry stem format:

```
<key>__<sanitized-target-file-name>
```

Shard directory is `key.take(2).padEnd(2, '0')`.

## On-Disk Entry Layout

For entry stem `S` in shard `D`:

- payload: `D/S`
- metadata: `D/S.meta`
- stale mark: `D/S.mark` (optional)

Version root also stores:

- `.last.cleanup.marker`
- `.cleanup.scan.cursor`

## Reader Acceptance Rule

An entry is a hit only when all are true:

1. payload file exists
2. metadata file exists
3. metadata decodes and schema is supported
4. metadata source records match current sources (size/hash and native metadata constraints)

If any check fails, entry is treated as miss (and may be removed on strict path).

## Publication Protocol

Publication order is fixed:

1. produce payload into temp sibling
2. move temp payload to final payload path
3. serialize metadata into temp sibling
4. move temp metadata to final metadata path

Metadata publish is the commit marker. Readers never trust payload alone.

## Metadata Publish Failure Reconciliation

If metadata write/move throws `IOException` after payload is published:

1. retry bounded re-read of metadata validity (short backoff)
2. if valid metadata+payload appears, treat operation as success
3. otherwise fail the call

Rationale: another process may have completed publication for the same key.

## Concurrency Model

- In-process: per-key-slot serialization (`StripedMutex`) is required.
- Cross-process: no file lock is used; duplicate producer runs are allowed.

For `useCacheAsTargetFile=true`, returned cache path can disappear due to concurrent cleanup in another process.

## Metadata Touch And Retention

Metadata `mtime` is the retention signal (last access time).

On cache hit:

- touch is throttled in-memory (`MetadataTouchTracker`)
- marked entries bypass throttle

If touch fails:

- tracker records failure timestamp
- cleanup treats entry as fresh for bounded grace period
- after grace expires, normal stale logic applies

Cleanup also enforces a per-target version cap:

- target identity is the `<target>` suffix in `<key>__<target>`
- `<target>` is the sanitized/truncated target file name stored in entry stem
- keep at most 3 entries per target by metadata `mtime` (most recently used first)
- entries with active metadata-touch failure grace are treated as freshest for cap ranking
- overflow entries are deleted during cleanup even when not stale by age

## Cleanup Algorithm

Cleanup runs at most once per configured cleanup interval.

- default interval: `defaultCleanupEveryDuration` (1 day)
- dev mode uses 1 hour interval

Per run:

1. drain bounded in-memory candidate queue
2. reserve scan capacity for cursor-based shard traversal and keep scanning while candidate budget remains
3. compute per-target overflow from current candidate set (keep newest 3 per target)
4. inspect candidates and delete malformed-key garbage immediately
5. for valid keys, delete overflow entries or execute stale logic under in-process per-key-slot lock

Stale logic:

1. missing metadata or missing payload -> delete entry files
2. fresh metadata or recent-touch-failure grace -> clear `.mark`
3. stale and unmarked -> create `.mark`
4. stale and marked -> delete entry files

## Scan Coverage Guarantee

Cursor in `.cleanup.scan.cursor` advances each run. Cleanup traversal continues until candidate batch cap is reached
or all currently known shards are exhausted. With stable shard set and continued cleanup runs, every shard is revisited
within a finite number of runs.

## Non-Guarantees

- No cross-process exactly-once producer guarantee.
- No persistence for touch-failure grace across process restarts.
- If different producers generate different bytes for the same key, final payload is last-writer-wins.
- Per-target version cap is cleanup-time and candidate-driven, so temporary over-retention is possible between runs.
