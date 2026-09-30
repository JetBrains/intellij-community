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
//! A tool starts a root [`Span`] from a [`Tracer`] and each child from its parent span. A [`Tracer::disabled`] tracer
//! gives inert spans, so a run without `--trace-file` runs the same statements and records nothing. [`run_traced`]
//! holds the set-up that every traced tool shares.
//!
//! The span API is written by hand, because a crate dependency in the packer re-keys every packing action when the
//! crate changes. See `API.md` for the mapping of the calls to the document.

use std::fmt::Display;
use std::fs;
use std::io::{self, Write};
use std::path::Path;
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use serde::Serialize;

#[cfg(test)]
mod tests;

/// The tag that [`Span::fail`] adds.
const ERROR_MESSAGE_TAG: &str = "error.message";

/// The one process that a file describes. The Kotlin exporter uses the same id, and a merge of files renumbers it.
const PROCESS_ID: &str = "p1";

/// Runs `job` with a tracer for `trace_file`, then writes the span file. Every traced tool starts this way.
///
/// Without `trace_file` the tracer is disabled. `job` gets the tracer and `errors`, and it returns the exit code of the
/// tool. The action declares the span file as an output, so a run that cannot write the file fails, and Bazel does not
/// look for a file that is not there. Then the function writes `ERROR: writing the span file: …` to `errors` and
/// returns `failure`.
pub fn run_traced(
    service_name: &str,
    trace_file: Option<&Path>,
    failure: u8,
    errors: &mut dyn Write,
    job: impl FnOnce(&Tracer, &mut dyn Write) -> u8,
) -> u8 {
    let tracer = match trace_file {
        Some(_) => Tracer::new(service_name),
        None => Tracer::disabled(),
    };
    let code = job(&tracer, errors);
    if let Some(trace_file) = trace_file
        && let Err(error) = tracer.write_file(trace_file)
    {
        let _ = writeln!(errors, "ERROR: writing the span file: {error}");
        return failure;
    }
    code
}

/// Collects the spans of one run and writes them once, at the end.
///
/// A clone shares the state. A disabled tracer records nothing: each of its spans is inert, and
/// [`Tracer::write_file`] writes no file.
#[derive(Clone)]
pub struct Tracer {
    /// `None` for a disabled tracer.
    inner: Option<Arc<Inner>>,
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
    /// The spans in the order they started. A [`Span`] holds the index of its record.
    spans: Mutex<Vec<SpanRecord>>,
}

struct SpanRecord {
    id: String,
    /// The index of the record of the parent span.
    parent: Option<usize>,
    name: String,
    start: i64,
    end: Option<i64>,
    failed: bool,
    tags: Vec<Tag>,
}

struct Tag {
    key: &'static str,
    kind: &'static str,
    value: String,
}

/// The value of a tag: a string, or an integer that the document writes with the type `long`.
///
/// The Go original recorded only these two kinds, so no other type converts into a tag value.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum TagValue {
    Str(String),
    Long(i64),
}

impl From<&str> for TagValue {
    fn from(value: &str) -> Self {
        Self::Str(value.to_owned())
    }
}

impl From<String> for TagValue {
    fn from(value: String) -> Self {
        Self::Str(value)
    }
}

impl From<i64> for TagValue {
    fn from(value: i64) -> Self {
        Self::Long(value)
    }
}

/// A count or a byte size. A value past `i64::MAX` saturates.
impl From<u64> for TagValue {
    fn from(value: u64) -> Self {
        Self::Long(i64::try_from(value).unwrap_or(i64::MAX))
    }
}

/// A count. A value past `i64::MAX` saturates.
impl From<usize> for TagValue {
    fn from(value: usize) -> Self {
        Self::Long(i64::try_from(value).unwrap_or(i64::MAX))
    }
}

impl Tag {
    fn new(key: &'static str, value: TagValue) -> Self {
        let (kind, value) = match value {
            TagValue::Str(text) => ("string", text),
            TagValue::Long(number) => ("long", number.to_string()),
        };
        Self { key, kind, value }
    }
}

/// One span of a [`Tracer`]. It ends once: at [`Span::end`], or when it drops.
///
/// A span is `Send` and `Sync`, so a worker thread starts a child from a span of its caller. The API has no current
/// span, so each child names its parent.
#[must_use = "a span ends when it drops"]
pub struct Span {
    /// The tracer and the index of the record, or `None` for a span of a disabled tracer.
    record: Option<(Arc<Inner>, usize)>,
}

impl Span {
    /// Starts a child of this span.
    pub fn child(&self, name: impl Into<String>) -> Self {
        match &self.record {
            Some((inner, index)) => inner.start(name.into(), Some(*index)),
            None => Self { record: None },
        }
    }

    /// Adds a tag. The document lists the tags in the order of these calls.
    pub fn tag(&self, key: &'static str, value: impl Into<TagValue>) {
        if let Some((inner, index)) = &self.record {
            let tag = Tag::new(key, value.into());
            inner.spans()[*index].tags.push(tag);
        }
    }

    /// Marks the span as failed and adds `message` as the `error.message` tag.
    ///
    /// A failed span gets the tags `otel.status_code` and `error` before all other tags, as the Kotlin exporter writes
    /// them. The Jaeger UI then shows the span in red.
    pub fn fail(&self, message: &dyn Display) {
        if let Some((inner, index)) = &self.record {
            let tag = Tag::new(ERROR_MESSAGE_TAG, TagValue::Str(message.to_string()));
            let mut spans = inner.spans();
            let record = &mut spans[*index];
            record.failed = true;
            record.tags.push(tag);
        }
    }

    /// Ends the span. A span that drops without this call ends at the drop.
    pub fn end(self) {
        drop(self);
    }
}

impl Drop for Span {
    fn drop(&mut self) {
        if let Some((inner, index)) = &self.record {
            let end = (inner.clock)();
            inner.spans()[*index].end = Some(end);
        }
    }
}

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

    /// Returns a tracer that records nothing, for a run without a span file.
    pub const fn disabled() -> Self {
        Self { inner: None }
    }

    fn with_parts(
        service_name: String,
        started_at: i64,
        clock: Box<dyn Fn() -> i64 + Send + Sync>,
        new_span_id: Box<dyn Fn() -> String + Send + Sync>,
        trace_id: String,
    ) -> Self {
        Self {
            inner: Some(Arc::new(Inner {
                service_name,
                started_at,
                clock,
                new_span_id,
                trace_id,
                spans: Mutex::new(Vec::new()),
            })),
        }
    }

    /// Starts a root span.
    pub fn span(&self, name: impl Into<String>) -> Span {
        match &self.inner {
            Some(inner) => inner.start(name.into(), None),
            None => Span { record: None },
        }
    }

    /// Writes the trace to `path` and creates the parent directory. A disabled tracer writes no file.
    ///
    /// The document is one line with no trailing newline, as the Kotlin writer leaves it. A span that is still open
    /// ends at the time of the write. The error names the path.
    pub fn write_file(&self, path: &Path) -> io::Result<()> {
        let Some(inner) = &self.inner else {
            return Ok(());
        };
        let named = |error: io::Error| io::Error::new(error.kind(), format!("{}: {error}", path.display()));
        let data = inner.encode();
        if let Some(parent) = path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
            fs::create_dir_all(parent).map_err(named)?;
        }
        fs::write(path, data).map_err(named)
    }
}

impl Inner {
    fn spans(&self) -> MutexGuard<'_, Vec<SpanRecord>> {
        self.spans.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// Starts a span with the next id. The clock runs under the lock, so the file order is the start order.
    fn start(self: &Arc<Self>, name: String, parent: Option<usize>) -> Span {
        let id = (self.new_span_id)();
        let index = {
            let mut spans = self.spans();
            let start = (self.clock)();
            spans.push(SpanRecord {
                id,
                parent,
                name,
                start,
                end: None,
                failed: false,
                tags: Vec::new(),
            });
            spans.len() - 1
        };
        Span {
            record: Some((Arc::clone(self), index)),
        }
    }

    fn encode(&self) -> Vec<u8> {
        let spans = self.spans();
        let now = (self.clock)();
        let trace_id = self.trace_id.as_str();
        let started_at = u64::try_from(self.started_at).unwrap_or(0);
        let time = httpdate::fmt_http_date(UNIX_EPOCH + Duration::from_nanos(started_at));
        let span_documents = spans
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
                    key: tag.key,
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
                    references: span.parent.map(|parent| {
                        [ReferenceDocument {
                            ref_type: "CHILD_OF",
                            trace_id,
                            span_id: &spans[parent].id,
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
                        service_name: &self.service_name,
                        tags: [TagDocument {
                            key: "time",
                            kind: "string",
                            value: &time,
                        }],
                    },
                },
                spans: span_documents,
            }],
        };
        let text = serde_json::to_string(&document).expect("a trace document has no map with non-string keys");
        // Go `encoding/json` escaped U+2028 and U+2029, and `serde_json` does not. The two characters occur only in a
        // JSON string, so this replace gives the bytes of the Go writer.
        text.replace('\u{2028}', "\\u2028").replace('\u{2029}', "\\u2029").into_bytes()
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
