# trace

Records the spans of one run and writes them as Jaeger JSON, in the field order of the Kotlin
`JaegerJsonSpanExporter`. Port of the Go package `internal/span`.

`Tracer` is a `tracing_subscriber::Layer`. A tool instruments its code with the `tracing` macros. Without an installed
`Tracer` the macros do nothing, so a run without `--trace-file` needs no tracing branch.

```rust
let tracer = trace_file.as_ref().map(|_| trace::Tracer::new("content-module-packer"));
if let Some(tracer) = &tracer {
    tracer.install_global()?;
}
let root = tracing::info_span!("pack content modules", jars = specs.len());
// On a rayon worker, pass the parent explicitly: info_span!(parent: &root, "pack jar", jar = %name, bytes = Empty)
// Later: span.record("bytes", count); trace::fail(&span, &error);
drop(root);
if let (Some(tracer), Some(path)) = (&tracer, &trace_file) {
    tracer.write_file(path)?;
}
```

## Public items

| Item | Go original | Description |
|---|---|---|
| `struct Tracer` | `Tracer` | Collects the spans. `Clone` shares the state. Implements `Layer<S>` for every `S: Subscriber + for<'a> LookupSpan<'a>`. |
| `Tracer::new(impl Into<String>) -> Tracer` | `NewTracer` | Starts a trace with a random trace id. The service name names the producer in the merged trace. |
| `Tracer::install_global(&self) -> Result<(), SetGlobalDefaultError>` | none | Installs `Registry` with this layer as the global default. Every thread, rayon workers too, then records into this tracer. |
| `Tracer::dispatch(&self) -> tracing::Dispatch` | none | A `Registry` with this layer, for `tracing::dispatcher::with_default` in a test or a scoped thread. |
| `Tracer::write_file(&self, &Path) -> io::Result<()>` | `WriteFile` | Writes the document in one line, with no trailing newline. It creates the parent directory. A span that is still open ends at the write time. The error names the path. After an unsupported input it writes nothing and fails with `InvalidInput`. |
| `fail(&tracing::Span, &dyn Display)` | `Span.Fail` | Marks the span as failed and adds the `error.message` tag. |
| `OPERATION_NAME_FIELD = "otel.name"` | none | A span field with this name sets the operation name. Use it for a name that is not a `&'static str`. |
| `ERROR_MESSAGE_FIELD = "error.message"` | none | The tag that `fail` adds. |

## Mapping of the `tracing` data

- The span name is the operation name. The field `otel.name` replaces it.
- A span ends when its last handle drops (`on_close`). The first end is the only end.
- A field becomes a tag in the order of recording: the fields of the span macro first, then each `span.record`.
- A `&str`, `Display` (`%`) or `Debug` (`?`) value is type `string`. An `i64` or a `u64` is type `long`. Every tag value is a JSON string.
- The event of `fail` marks the span as failed and adds `error.message` as a tag.
- A failed span gets the tags `otel.status_code=ERROR` and `error=true` before all other tags.
- The file lists the spans in the order they started.
- `serde_json` writes the document. The tracer then escapes U+2028 and U+2029 as the Go writer did. The other bytes
  are the bytes of `serde_json`, which match Go `encoding/json` with `SetEscapeHTML(false)`.
- The process tag `time` is the start of the run as an HTTP date in UTC, for example `Sun, 09 Sep 2001 01:46:40 GMT`.
  The Go writer used the local time with a numeric offset. A person reads the tag, and no tool parses it.
- The trace id and the span ids come from `getrandom`.

## Supported subset

The Go original recorded only string and integer tags and had no events. A field of the type `bool`, `f64`, `i128`,
`u128`, `&[u8]` or `&dyn Error`, and every event other than the event of `fail`, are unsupported. The tracer keeps the
first such input, and `write_file` fails with a message that names the field and the span, or the event.
