//! Records the spans of one run and writes them as Jaeger JSON.
//!
//! The file is the same file that the JVM part of the build writes through `JaegerJsonSpanExporter`, and the Jaeger UI
//! reads both. So the schema is the schema of the Kotlin writer, not of the OpenTelemetry specification:
//!
//! - Every tag `value` is a JSON string, also for the type `long` or `boolean`.
//! - `startTime` and `duration` are in microseconds, truncated. `startTimeNano` and `durationNano` follow them with the
//!   full precision.
//! - A span with no tags has no `tags` key, and a root span has no `references` key.
//!
//! [`Tracer`] is a [`Layer`]. A tool instruments its code with the `tracing` macros. Without an installed [`Tracer`]
//! the macros do nothing, so a run without `--trace-file` needs no tracing branch. See `API.md` for the mapping of the
//! `tracing` data.

use std::fmt;
use std::fs;
use std::io;
use std::path::Path;
use std::sync::{Arc, Mutex, MutexGuard};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use serde::Serialize;
use tracing::field::{Field, Visit};
use tracing::span::{Attributes, Id, Record};
use tracing::{Event, Level, Subscriber};
use tracing_subscriber::layer::{Context, Layer, SubscriberExt};
use tracing_subscriber::registry::LookupSpan;

#[cfg(test)]
mod tests;

/// A span field with this name sets the operation name. Use it for a name that is not a `&'static str`.
pub const OPERATION_NAME_FIELD: &str = "otel.name";

/// The tag that [`fail`] adds.
pub const ERROR_MESSAGE_FIELD: &str = "error.message";

/// The one process that a file describes. The Kotlin exporter uses the same id, and a merge of files renumbers it.
const PROCESS_ID: &str = "p1";

/// Collects the spans of one run and writes them once, at the end.
///
/// A clone shares the state, so a tool can install one clone and keep another to call [`Tracer::write_file`].
///
/// The tracer records only the inputs of the Go original. These are a span field of the type `&str`, `i64` or `u64`, a
/// field that `%` or `?` formats, and the event of [`fail`]. For any other field type or event, [`Tracer::write_file`]
/// fails with an error that names the first such input.
#[derive(Clone)]
pub struct Tracer {
    inner: Arc<Inner>,
}

struct Inner {
    service_name: String,
    /// The start of the run in nanoseconds since the epoch. The `time` tag of the process shows it as an HTTP date in
    /// UTC, which is also the Java `RFC_1123_DATE_TIME` form for UTC. A person reads the tag, and no tool parses it.
    started_at: i64,
    /// Returns the time in nanoseconds since the epoch. A test replaces it.
    clock: Box<dyn Fn() -> i64 + Send + Sync>,
    /// Returns a new span id. A test replaces it.
    new_span_id: Box<dyn Fn() -> String + Send + Sync>,
    trace_id: String,
    /// One lock guards the spans and the first unsupported input.
    state: Mutex<State>,
}

#[derive(Default)]
struct State {
    /// The spans in the order they started.
    spans: Vec<SpanRecord>,
    /// The first input that the tracer does not support. [`Tracer::write_file`] reports it.
    unsupported: Option<String>,
}

impl State {
    fn refuse(&mut self, input: Option<String>) {
        if self.unsupported.is_none() {
            self.unsupported = input;
        }
    }
}

struct SpanRecord {
    id: String,
    parent_id: Option<String>,
    name: String,
    start: i64,
    end: Option<i64>,
    failed: bool,
    tags: Vec<Tag>,
}

struct Tag {
    key: String,
    kind: &'static str,
    value: String,
}

/// The index of the record of a span in [`State::spans`]. The span extensions hold it.
struct RecordIndex(usize);

/// The document, in the field order of the Kotlin exporter.
#[derive(Serialize)]
struct Document<'a> {
    data: [TraceDocument<'a>; 1],
}

#[derive(Serialize)]
struct TraceDocument<'a> {
    #[serde(rename = "traceID")]
    trace_id: &'a str,
    processes: Processes<'a>,
    spans: Vec<SpanDocument<'a>>,
}

/// The map of processes. Its one key is [`PROCESS_ID`].
#[derive(Serialize)]
struct Processes<'a> {
    p1: ProcessDocument<'a>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ProcessDocument<'a> {
    service_name: &'a str,
    tags: [TagDocument<'a>; 1],
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SpanDocument<'a> {
    #[serde(rename = "traceID")]
    trace_id: &'a str,
    #[serde(rename = "spanID")]
    span_id: &'a str,
    operation_name: &'a str,
    #[serde(rename = "processID")]
    process_id: &'a str,
    start_time: i64,
    duration: i64,
    start_time_nano: i64,
    duration_nano: i64,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    tags: Vec<TagDocument<'a>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    references: Option<[ReferenceDocument<'a>; 1]>,
}

#[derive(Serialize)]
struct TagDocument<'a> {
    key: &'a str,
    #[serde(rename = "type")]
    kind: &'a str,
    value: &'a str,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ReferenceDocument<'a> {
    ref_type: &'a str,
    #[serde(rename = "traceID")]
    trace_id: &'a str,
    #[serde(rename = "spanID")]
    span_id: &'a str,
}

impl Tracer {
    /// Starts a trace with a random trace id.
    ///
    /// `service_name` names the producer in the merged trace. The Kotlin build calls itself `build`, so a tool must use
    /// another name.
    pub fn new(service_name: impl Into<String>) -> Self {
        let anchor = Instant::now();
        let started_at = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_or(0, |elapsed| i64::try_from(elapsed.as_nanos()).unwrap_or(i64::MAX));
        Self::with_parts(
            service_name.into(),
            started_at,
            Box::new(move || started_at.saturating_add(i64::try_from(anchor.elapsed().as_nanos()).unwrap_or(i64::MAX))),
            Box::new(|| format!("{:016x}", random_u64())),
            format!("{:016x}{:016x}", random_u64(), random_u64()),
        )
    }

    fn with_parts(
        service_name: String,
        started_at: i64,
        clock: Box<dyn Fn() -> i64 + Send + Sync>,
        new_span_id: Box<dyn Fn() -> String + Send + Sync>,
        trace_id: String,
    ) -> Self {
        Self {
            inner: Arc::new(Inner {
                service_name,
                started_at,
                clock,
                new_span_id,
                trace_id,
                state: Mutex::new(State::default()),
            }),
        }
    }

    /// Installs a `Registry` with this tracer as the global default subscriber.
    ///
    /// Every thread then records into this tracer, also a rayon worker. A span on a worker does not see the span of the
    /// caller, so give it an explicit parent: `info_span!(parent: &root, "pack jar")`.
    pub fn install_global(&self) -> Result<(), tracing::subscriber::SetGlobalDefaultError> {
        tracing::subscriber::set_global_default(tracing_subscriber::registry().with(self.clone()))
    }

    /// Returns a `Registry` with this tracer, for `tracing::dispatcher::with_default` in a test or a scoped thread.
    pub fn dispatch(&self) -> tracing::Dispatch {
        tracing::Dispatch::new(tracing_subscriber::registry().with(self.clone()))
    }

    /// Writes the trace to `path` and creates the parent directory.
    ///
    /// The document is one line with no trailing newline, as the Kotlin writer leaves it. A span that is still open
    /// ends at the time of the write. The error names the path. When the run recorded an unsupported input, the
    /// function writes nothing and returns an error of the kind [`io::ErrorKind::InvalidInput`] that names the input.
    pub fn write_file(&self, path: &Path) -> io::Result<()> {
        let named = |error: io::Error| io::Error::new(error.kind(), format!("{}: {error}", path.display()));
        let data = self
            .encode()
            .map_err(|message| named(io::Error::new(io::ErrorKind::InvalidInput, message)))?;
        if let Some(parent) = path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
            fs::create_dir_all(parent).map_err(named)?;
        }
        fs::write(path, data).map_err(named)
    }

    fn state(&self) -> MutexGuard<'_, State> {
        self.inner.state.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
    }

    fn encode(&self) -> Result<Vec<u8>, String> {
        let state = self.state();
        if let Some(input) = &state.unsupported {
            return Err(format!("unsupported trace input: {input}"));
        }
        let now = (self.inner.clock)();
        let trace_id = self.inner.trace_id.as_str();
        let started_at = u64::try_from(self.inner.started_at).unwrap_or(0);
        let time = httpdate::fmt_http_date(UNIX_EPOCH + Duration::from_nanos(started_at));
        let spans = state
            .spans
            .iter()
            .map(|span| {
                let elapsed = span.end.unwrap_or(now) - span.start;
                let mut tags = Vec::with_capacity(span.tags.len() + 2);
                if span.failed {
                    // The Kotlin exporter writes both tags, in this order, for a failed span.
                    tags.push(TagDocument {
                        key: "otel.status_code",
                        kind: "string",
                        value: "ERROR",
                    });
                    tags.push(TagDocument {
                        key: "error",
                        kind: "boolean",
                        value: "true",
                    });
                }
                tags.extend(span.tags.iter().map(|tag| TagDocument {
                    key: &tag.key,
                    kind: tag.kind,
                    value: &tag.value,
                }));
                SpanDocument {
                    trace_id,
                    span_id: &span.id,
                    operation_name: &span.name,
                    process_id: PROCESS_ID,
                    start_time: span.start / 1_000,
                    duration: elapsed / 1_000,
                    start_time_nano: span.start,
                    duration_nano: elapsed,
                    tags,
                    // OpenTelemetry has no parent in another trace, so the reference holds the trace id of the span.
                    references: span.parent_id.as_deref().map(|parent_id| {
                        [ReferenceDocument {
                            ref_type: "CHILD_OF",
                            trace_id,
                            span_id: parent_id,
                        }]
                    }),
                }
            })
            .collect();
        let document = Document {
            data: [TraceDocument {
                trace_id,
                processes: Processes {
                    p1: ProcessDocument {
                        service_name: &self.inner.service_name,
                        tags: [TagDocument {
                            key: "time",
                            kind: "string",
                            value: &time,
                        }],
                    },
                },
                spans,
            }],
        };
        let text = serde_json::to_string(&document).expect("a trace document has no map with non-string keys");
        // Go `encoding/json` escaped U+2028 and U+2029, and `serde_json` does not. The two characters occur only in a
        // JSON string, so this replace gives the bytes of the Go writer.
        Ok(text.replace('\u{2028}', "\\u2028").replace('\u{2029}', "\\u2029").into_bytes())
    }

    fn record_index<S>(id: &Id, context: &Context<'_, S>) -> Option<usize>
    where
        S: Subscriber + for<'a> LookupSpan<'a>,
    {
        context.span(id)?.extensions().get::<RecordIndex>().map(|index| index.0)
    }
}

impl<S> Layer<S> for Tracer
where
    S: Subscriber + for<'a> LookupSpan<'a>,
{
    fn on_new_span(&self, attributes: &Attributes<'_>, id: &Id, context: Context<'_, S>) {
        let Some(span) = context.span(id) else {
            return;
        };
        let parent_index = span
            .parent()
            .and_then(|parent| parent.extensions().get::<RecordIndex>().map(|index| index.0));
        let mut visitor = TagVisitor::default();
        attributes.record(&mut visitor);
        let name = visitor.name.unwrap_or_else(|| attributes.metadata().name().to_owned());
        let unsupported = visitor.unsupported.map(|input| format!("{input}, in the span {name:?}"));
        let span_id = (self.inner.new_span_id)();
        let start = (self.inner.clock)();
        let index = {
            let mut state = self.state();
            state.refuse(unsupported);
            let parent_id = parent_index.map(|index| state.spans[index].id.clone());
            state.spans.push(SpanRecord {
                id: span_id,
                parent_id,
                name,
                start,
                end: None,
                failed: false,
                tags: visitor.tags,
            });
            state.spans.len() - 1
        };
        span.extensions_mut().insert(RecordIndex(index));
    }

    fn on_record(&self, id: &Id, values: &Record<'_>, context: Context<'_, S>) {
        let Some(index) = Self::record_index(id, &context) else {
            return;
        };
        let mut visitor = TagVisitor::default();
        values.record(&mut visitor);
        let mut state = self.state();
        let unsupported = visitor
            .unsupported
            .map(|input| format!("{input}, in the span {:?}", state.spans[index].name));
        state.refuse(unsupported);
        let span = &mut state.spans[index];
        if let Some(name) = visitor.name {
            span.name = name;
        }
        span.tags.append(&mut visitor.tags);
    }

    fn on_event(&self, event: &Event<'_>, context: Context<'_, S>) {
        let metadata = event.metadata();
        if metadata.target() != module_path!() || *metadata.level() != Level::ERROR {
            self.state().refuse(Some(format!(
                "the {} of the target {}: the trace records only the event of `trace::fail`",
                metadata.name(),
                metadata.target()
            )));
            return;
        }
        let Some(index) = context
            .event_span(event)
            .and_then(|span| span.extensions().get::<RecordIndex>().map(|index| index.0))
        else {
            return;
        };
        let mut visitor = TagVisitor::default();
        event.record(&mut visitor);
        let mut state = self.state();
        let span = &mut state.spans[index];
        span.failed = true;
        span.tags.append(&mut visitor.tags);
    }

    fn on_close(&self, id: Id, context: Context<'_, S>) {
        let Some(index) = Self::record_index(&id, &context) else {
            return;
        };
        let end = (self.inner.clock)();
        self.state().spans[index].end.get_or_insert(end);
    }
}

/// Marks `span` as failed and adds `message` as the `error.message` tag.
///
/// A failed span gets the tags `otel.status_code` and `error` before all other tags, as the Kotlin exporter writes
/// them. The Jaeger UI then shows the span in red.
/// A count or a byte size as the `i64` value of a span field. A value past `i64::MAX` saturates.
pub fn count<T: TryInto<i64>>(value: T) -> i64 {
    value.try_into().unwrap_or(i64::MAX)
}

pub fn fail(span: &tracing::Span, message: &dyn fmt::Display) {
    tracing::error!(parent: span, error.message = %message);
}

/// Turns the fields of a span or an event into tags.
#[derive(Default)]
struct TagVisitor {
    name: Option<String>,
    tags: Vec<Tag>,
    /// The first field with a type that the Go original did not record.
    unsupported: Option<String>,
}

impl TagVisitor {
    fn add(&mut self, field: &Field, kind: &'static str, value: String) {
        if field.name() == OPERATION_NAME_FIELD {
            self.name = Some(value);
        } else {
            self.tags.push(Tag {
                key: field.name().to_owned(),
                kind,
                value,
            });
        }
    }

    fn refuse(&mut self, field: &Field, type_name: &str) {
        self.unsupported.get_or_insert_with(|| {
            format!(
                "the field {} has the type {type_name}, and a field must be a string, an i64 or a u64",
                field.name()
            )
        });
    }
}

impl Visit for TagVisitor {
    fn record_i64(&mut self, field: &Field, value: i64) {
        self.add(field, "long", value.to_string());
    }

    fn record_u64(&mut self, field: &Field, value: u64) {
        self.add(field, "long", value.to_string());
    }

    fn record_str(&mut self, field: &Field, value: &str) {
        self.add(field, "string", value.to_owned());
    }

    /// Records a field that `%` or `?` formats. `tracing` gives both to this method.
    fn record_debug(&mut self, field: &Field, value: &dyn fmt::Debug) {
        self.add(field, "string", format!("{value:?}"));
    }

    fn record_f64(&mut self, field: &Field, _value: f64) {
        self.refuse(field, "f64");
    }

    fn record_i128(&mut self, field: &Field, _value: i128) {
        self.refuse(field, "i128");
    }

    fn record_u128(&mut self, field: &Field, _value: u128) {
        self.refuse(field, "u128");
    }

    fn record_bool(&mut self, field: &Field, _value: bool) {
        self.refuse(field, "bool");
    }

    fn record_bytes(&mut self, field: &Field, _value: &[u8]) {
        self.refuse(field, "&[u8]");
    }

    fn record_error(&mut self, field: &Field, _value: &(dyn std::error::Error + 'static)) {
        self.refuse(field, "&dyn Error");
    }
}

/// Returns 64 random bits from the operating system.
///
/// The ids are random, not counted, because a different process writes each span file of a build. A counter would
/// give all packing actions the same ids, and the merge of the files would put their spans on each other. Go
/// `crypto/rand.Read` also stops the program when the operating system gives no random bytes.
fn random_u64() -> u64 {
    getrandom::u64().expect("the operating system must give random bytes")
}
