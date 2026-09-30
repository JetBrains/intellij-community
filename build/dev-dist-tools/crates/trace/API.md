# trace

Records the spans of one run and writes them as Jaeger JSON, in the field order of the Kotlin
`JaegerJsonSpanExporter`. Port of the Go package `internal/span`.

A tool starts a root span from a `Tracer` and each child from its parent span. A disabled tracer gives inert spans, so
a run without `--trace-file` runs the same statements and records nothing. The API is written by hand, because a crate
dependency in the packer re-keys every packing action when the crate changes.

```rust
trace::run_traced(SERVICE_NAME, trace_file.as_deref(), FAILURE, stderr, |tracer, stderr| {
    let root = tracer.span("pack content modules");
    root.tag("jars", specs.len());
    // On a worker thread: let span = root.child("pack jar"); span.tag("jar", name); span.fail(&error);
    root.end();
    0
})
```

## Public items

| Item | Description |
|---|---|
| `run_traced(&str, Option<&Path>, failure: u8, &mut dyn Write, FnOnce(&Tracer, &mut dyn Write) -> u8) -> u8` | Runs the job with a tracer for the span file, or with a disabled tracer without one. Then it writes the file. When the write fails, it writes `ERROR: writing the span file: …` to the error stream and returns `failure`. Otherwise it returns the code of the job. |
| `struct Tracer` | Collects the spans. `Clone` shares the state. |
| `Tracer::new(impl Into<String>) -> Tracer` | Starts a trace with a random trace id. The service name names the producer in the merged trace. |
| `Tracer::disabled() -> Tracer` | A tracer that records nothing. It allocates nothing, and each of its spans is inert. |
| `Tracer::span(&self, impl Into<String>) -> Span` | Starts a root span. |
| `Tracer::write_file(&self, &Path) -> io::Result<()>` | Writes the document in one line, with no trailing newline. It creates the parent directory. A span that is still open ends at the write time. The error names the path. A disabled tracer writes no file. |
| `struct Span` | One span. It is `Send` and `Sync`, and it is not `Clone`. |
| `Span::child(&self, impl Into<String>) -> Span` | Starts a child of this span. The child of an inert span is inert. |
| `Span::tag(&self, &'static str, impl Into<TagValue>)` | Adds a tag. |
| `Span::fail(&self, &dyn Display)` | Marks the span as failed and adds the `error.message` tag. |
| `Span::end(self)` | Ends the span. A span that drops without this call ends at the drop. |
| `enum TagValue { Str(String), Long(i64) }` | The value of a tag. `From` converts `&str`, `String`, `i64`, `u64` and `usize`. A `u64` or a `usize` past `i64::MAX` saturates. |

The Go names of `Tracer::new`, `Tracer::write_file` and `Span::fail` were `NewTracer`, `WriteFile` and `Span.Fail`.

## Mapping to the document

- The name of the span is the operation name.
- A span ends once: at `end`, or when it drops.
- The tags are in the order of the `tag` and `fail` calls.
- A `TagValue::Str` is type `string`, and a `TagValue::Long` is type `long`. Every tag value is a JSON string.
- `fail` marks the span as failed and adds `error.message` as a tag.
- A failed span gets the tags `otel.status_code=ERROR` and `error=true` before all other tags. The type of `error` is
  `boolean`.
- A child has one `CHILD_OF` reference to its parent, with the trace id of the file. A root span has no `references`
  key, and a span with no tags has no `tags` key.
- `startTime` and `duration` are in microseconds, truncated. `startTimeNano` and `durationNano` follow them.
- The file lists the spans in the order they started.
- `serde_json` writes the document. The tracer then escapes U+2028 and U+2029 as the Go writer did. The other bytes
  are the bytes of `serde_json`, which match Go `encoding/json` with `SetEscapeHTML(false)`.
- The process tag `time` is the start of the run as an HTTP date in UTC, for example `Sun, 09 Sep 2001 01:46:40 GMT`.
  The Go writer used the local time with a numeric offset. A person reads the tag, and no tool parses it.
- The trace id and the span ids come from `getrandom`.

## Supported subset

The Go original recorded only string and integer tags and had no events. `TagValue` has only these two kinds, so a
boolean, a float or an event cannot reach the document. The API has no current span, so a span has no parent other
than the span that started it.
