//! The expectations are whole documents, written by hand. A decoder would accept any field name and any nesting. The
//! reader of a merged build trace uses these bytes as the schema, and the field order of the Kotlin exporter is part
//! of it. Each expectation came from the former test `internal/span/span_test.go`, except the `time` tag.

use std::collections::HashSet;
use std::fs;
use std::sync::atomic::{AtomicI64, AtomicU64, Ordering};

use super::*;

const TEST_TRACE_ID: &str = "00112233445566778899aabbccddeeff";

/// 1 000 000 000 seconds since the epoch, so every timestamp of an expectation is readable. One tick of the test clock
/// is a millisecond.
const TEST_TIME: i64 = 1_000_000_000 * 1_000_000_000;

/// The prologue of the document, which every expectation repeats.
const TEST_PROCESS: &str = concat!(
    r#""processes":{"p1":{"serviceName":"test-packer","tags":["#,
    r#"{"key":"time","type":"string","value":"Sun, 09 Sep 2001 01:46:40 GMT"}]}}"#
);

/// Returns a tracer with fixed ids and a clock that moves one millisecond at each call.
fn new_test_tracer() -> Tracer {
    let ids = AtomicU64::new(0);
    let ticks = AtomicI64::new(0);
    Tracer::with_parts(
        "test-packer".to_owned(),
        TEST_TIME,
        Box::new(move || TEST_TIME + (ticks.fetch_add(1, Ordering::SeqCst) + 1) * 1_000_000),
        Box::new(move || format!("{:016x}", ids.fetch_add(1, Ordering::SeqCst) + 1)),
        TEST_TRACE_ID.to_owned(),
    )
}

/// The state of a tracer that records.
fn inner(tracer: &Tracer) -> &Inner {
    tracer.inner.as_deref().expect("a tracer that records")
}

/// Returns the document of `tracer` as text.
fn encoded(tracer: &Tracer) -> String {
    String::from_utf8(inner(tracer).encode()).unwrap()
}

fn document(spans: &str) -> String {
    format!(r#"{{"data":[{{"traceID":"{TEST_TRACE_ID}",{TEST_PROCESS},"spans":[{spans}]}}]}}"#)
}

#[test]
fn a_trace_with_no_spans_is_still_a_whole_document() {
    // A failed action writes this. The action declares the file, so the file must exist and must parse.
    assert_eq!(encoded(&new_test_tracer()), document(""));
}

#[test]
fn a_span_carries_its_tags_in_the_order_they_were_set() {
    let tracer = new_test_tracer();
    let span = tracer.span("pack jar");
    span.tag("jar", "app.jar");
    span.tag("sources", 3i64);
    span.tag("bytes", 4096i64);
    span.end();

    let expected = document(concat!(
        r#"{"traceID":"00112233445566778899aabbccddeeff","spanID":"0000000000000001","operationName":"pack jar","processID":"p1","#,
        r#""startTime":1000000000001000,"duration":1000,"startTimeNano":1000000000001000000,"durationNano":1000000,"#,
        r#""tags":[{"key":"jar","type":"string","value":"app.jar"},"#,
        r#"{"key":"sources","type":"long","value":"3"},"#,
        r#"{"key":"bytes","type":"long","value":"4096"}]}"#
    ));
    assert_eq!(encoded(&tracer), expected);
}

#[test]
fn a_child_refers_to_its_parent_within_the_same_trace() {
    let tracer = new_test_tracer();
    let root = tracer.span("pack content modules");
    let child = root.child("pack jar");
    child.end();
    root.end();

    // The root comes first because the file lists the spans in the order they started. For the same reason the
    // duration of the root covers the duration of the child.
    let expected = document(concat!(
        r#"{"traceID":"00112233445566778899aabbccddeeff","spanID":"0000000000000001","operationName":"pack content modules","#,
        r#""processID":"p1","startTime":1000000000001000,"duration":3000,"#,
        r#""startTimeNano":1000000000001000000,"durationNano":3000000},"#,
        r#"{"traceID":"00112233445566778899aabbccddeeff","spanID":"0000000000000002","operationName":"pack jar","processID":"p1","#,
        r#""startTime":1000000000002000,"duration":1000,"startTimeNano":1000000000002000000,"durationNano":1000000,"#,
        r#""references":[{"refType":"CHILD_OF","traceID":"00112233445566778899aabbccddeeff","spanID":"0000000000000001"}]}"#
    ));
    assert_eq!(encoded(&tracer), expected);
}

/// The API has no current span, so a span refers to the span that started it and to no other.
#[test]
fn a_grandchild_refers_to_its_own_parent() {
    let tracer = new_test_tracer();
    let root = tracer.span("pack content modules");
    let child = root.child("pack jar");
    drop(child.child("inventory packing output"));
    let encoded = encoded(&tracer);
    assert!(encoded.contains(r#""spanID":"0000000000000001"}]"#), "{encoded}");
    assert!(encoded.contains(r#""spanID":"0000000000000002"}]"#), "{encoded}");
}

#[test]
fn a_failed_span_carries_the_kotlin_writers_status_tags_first() {
    let tracer = new_test_tracer();
    let span = tracer.span("pack jar");
    span.tag("jar", "app.jar");
    span.fail(&"no inputs");
    span.end();

    // The writer emits `otel.status_code` and `error` first, whenever `fail` was called. `error` has the type
    // `boolean` and the string value "true": the Kotlin writer writes it so, and the Jaeger UI reads it so.
    let expected = concat!(
        r#""tags":[{"key":"otel.status_code","type":"string","value":"ERROR"},"#,
        r#"{"key":"error","type":"boolean","value":"true"},"#,
        r#"{"key":"jar","type":"string","value":"app.jar"},"#,
        r#"{"key":"error.message","type":"string","value":"no inputs"}]"#
    );
    let encoded = encoded(&tracer);
    assert!(encoded.contains(expected), "\n got {encoded}\nwant a span that contains {expected}");
}

#[test]
fn a_span_never_ended_is_closed_at_write_time() {
    let tracer = new_test_tracer();
    // Tick 1 starts the span, and the encode is tick 2.
    let span = tracer.span("pack jar");
    let encoded = encoded(&tracer);
    assert!(
        encoded.contains(r#""duration":1000,"#),
        "an open span must end at the write: {encoded}"
    );
    drop(span);
}

/// The `End` of the former tool was idempotent. `end` consumes the span, so a span ends once, and a later write keeps
/// that end.
#[test]
fn an_ended_span_keeps_its_end_time() {
    let tracer = new_test_tracer();
    // Tick 1 starts the span, tick 2 ends it, and the encode is tick 3.
    tracer.span("pack jar").end();
    let encoded = encoded(&tracer);
    assert!(encoded.contains(r#""duration":1000,"#), "the span must end once: {encoded}");
}

#[test]
fn nothing_is_escaped_that_jackson_would_not_escape() {
    let tracer = new_test_tracer();
    // A jar name cannot hold these characters, but an error message can.
    tracer.span("pack jar").tag("error.message", "a<b && c>d");
    let encoded = encoded(&tracer);
    assert!(encoded.contains(r#""value":"a<b && c>d""#), "HTML escaping is on: {encoded}");
    assert!(!encoded.contains("\\u00"), "something was escaped: {encoded}");
}

#[test]
fn a_disabled_tracer_records_nothing_and_writes_no_file() {
    // A run without `--trace-file` runs the same statements on a disabled tracer.
    let tracer = Tracer::disabled();
    let root = tracer.span("pack content modules");
    let span = root.child("pack jar");
    span.tag("jar", "app.jar");
    span.tag("bytes", 1i64);
    span.fail(&"boom");
    assert!(span.record.is_none(), "a child of an inert span records");
    span.end();
    root.end();

    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("trace.json");
    tracer.write_file(&path).unwrap();
    assert!(!path.exists(), "a disabled tracer wrote {}", path.display());
}

#[test]
fn write_file_creates_the_parent_directory() {
    let tracer = new_test_tracer();
    tracer.span("pack jar").end();

    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("bazel-out").join("app.jar.spans.json");
    tracer.write_file(&path).unwrap();
    let content = fs::read_to_string(&path).unwrap();
    // The last byte is the closing brace, with no trailing newline, as the Kotlin writer leaves the file.
    assert!(content.ends_with('}'), "the file must end with the document: {content:?}");
    assert!(!content.contains('\n'), "the document is one line: {content:?}");
    serde_json::from_str::<serde_json::Value>(&content).unwrap();
}

#[test]
fn write_file_reports_an_unwritable_path_instead_of_panicking() {
    let directory = tempfile::tempdir().unwrap();
    let blocked = directory.path().join("a-file");
    fs::write(&blocked, "").unwrap();

    let error = new_test_tracer().write_file(&blocked.join("trace.json")).unwrap_err();
    let message = error.to_string();
    assert!(
        message.contains("trace.json") || message.contains("a-file"),
        "the error must name the path: {message}"
    );
}

#[test]
fn ids_are_distinct_across_tracers_and_spans() {
    // A build writes one file per action, and the merge of the files has only these ids to tell two actions apart.
    let first = Tracer::new("packer");
    let second = Tracer::new("packer");
    let (first, second) = (inner(&first), inner(&second));
    assert_ne!(first.trace_id, second.trace_id, "two runs share a trace id");
    assert_eq!(first.trace_id.len(), 32, "{}", first.trace_id);

    let mut seen = HashSet::new();
    for _ in 0..1000 {
        let id = (first.new_span_id)();
        assert_eq!(id.len(), 16, "a span id is 16 hex characters: {id}");
        assert!(id.bytes().all(|byte| byte.is_ascii_hexdigit()), "{id}");
        assert!(seen.insert(id.clone()), "span id {id} was handed out twice");
    }
}

#[test]
fn concurrent_spans_are_all_recorded() {
    // A whole-tranche run packs thousands of jars on many threads, and each thread starts a child of the root.
    let tracer = Tracer::new("packer");
    let root = tracer.span("pack content modules");
    std::thread::scope(|scope| {
        for index in 0..64usize {
            let root = &root;
            scope.spawn(move || {
                let span = root.child("pack jar");
                span.tag("jar", format!("{index}.jar"));
                span.tag("sources", index);
            });
        }
    });
    root.end();

    {
        let spans = inner(&tracer).spans();
        assert_eq!(spans.len(), 65);
        assert!(spans.iter().all(|span| span.end.is_some()));
        assert!(spans[1..].iter().all(|span| span.parent == Some(0)));
    }
    let written = encoded(&tracer);
    for index in 0..64 {
        let expected = format!(r#""value":"{index}.jar""#);
        assert!(written.contains(&expected), "{expected} is missing from the document");
    }
}

#[test]
fn tag_values_map_to_the_tag_types_of_the_go_original() {
    let tracer = new_test_tracer();
    let job = String::from("dev-dist-composer");
    let span = tracer.span(job);
    span.tag("kind", "files");
    span.tag("path", format!("{:?}", "a\"b"));
    span.tag("count", 7u64);
    span.tag("delta", -2i64);
    span.fail(&"no inputs");
    span.end();
    let expected = concat!(
        r#""operationName":"dev-dist-composer","#,
        r#""processID":"p1","startTime":1000000000001000,"duration":1000,"#,
        r#""startTimeNano":1000000000001000000,"durationNano":1000000,"tags":["#,
        r#"{"key":"otel.status_code","type":"string","value":"ERROR"},"#,
        r#"{"key":"error","type":"boolean","value":"true"},"#,
        r#"{"key":"kind","type":"string","value":"files"},"#,
        r#"{"key":"path","type":"string","value":"\"a\\\"b\""},"#,
        r#"{"key":"count","type":"long","value":"7"},"#,
        r#"{"key":"delta","type":"long","value":"-2"},"#,
        r#"{"key":"error.message","type":"string","value":"no inputs"}]}"#
    );
    let encoded = encoded(&tracer);
    assert!(encoded.contains(expected), "\n got {encoded}\nwant {expected}");
}

#[test]
fn a_count_past_the_long_range_saturates() {
    assert_eq!(TagValue::from(7usize), TagValue::Long(7));
    assert_eq!(TagValue::from(u64::MAX), TagValue::Long(i64::MAX));
    assert_eq!(TagValue::from(usize::MAX), TagValue::Long(i64::MAX));
}

/// The expectation is the output of Go `encoding/json` with `SetEscapeHTML(false)`, as the Go tracer wrote it.
#[test]
fn a_special_name_is_escaped_as_the_go_encoder_escapes_it() {
    let tracer = new_test_tracer();
    tracer.span("q\"b\\s\u{8}\u{c}\n\r\t\u{1}\u{1f}\u{7f}<>&\u{2028}\u{2029}é").end();
    let expected = document(concat!(
        r#"{"traceID":"00112233445566778899aabbccddeeff","spanID":"0000000000000001","#,
        "\"operationName\":\"q\\\"b\\\\s\\b\\f\\n\\r\\t\\u0001\\u001f\u{7f}<>&\\u2028\\u2029é\",",
        r#""processID":"p1","startTime":1000000000001000,"duration":1000,"#,
        r#""startTimeNano":1000000000001000000,"durationNano":1000000}"#
    ));
    assert_eq!(encoded(&tracer), expected);
}

#[test]
fn run_traced_writes_the_span_file_after_the_job() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("out").join("tool.spans.json");
    let mut errors = Vec::new();
    let code = run_traced("test-tool", Some(&path), 3, &mut errors, |tracer, _| {
        tracer.span("run tool").tag("files", 2usize);
        0
    });
    assert_eq!(code, 0, "{}", String::from_utf8_lossy(&errors));
    assert!(errors.is_empty(), "{}", String::from_utf8_lossy(&errors));
    let content = fs::read_to_string(&path).unwrap();
    assert!(
        content.contains(r#""serviceName":"test-tool""#) && content.contains(r#""operationName":"run tool""#),
        "{content}"
    );
}

#[test]
fn run_traced_without_a_span_file_runs_a_disabled_tracer() {
    let mut errors = Vec::new();
    let code = run_traced("test-tool", None, 3, &mut errors, |tracer, errors| {
        assert!(tracer.inner.is_none(), "a run without a span file records");
        let _ = write!(errors, "ERROR: boom");
        1
    });
    assert_eq!(code, 1, "the code of the job must pass through");
    assert_eq!(String::from_utf8(errors).unwrap(), "ERROR: boom");
}

/// The action declares the span file, so a run that cannot write it fails even when the job succeeded.
#[test]
fn run_traced_fails_with_the_failure_code_when_the_span_file_cannot_be_written() {
    let directory = tempfile::tempdir().unwrap();
    let blocked = directory.path().join("a-file");
    fs::write(&blocked, "").unwrap();
    let mut errors = Vec::new();
    let code = run_traced("test-tool", Some(&blocked.join("trace.json")), 3, &mut errors, |_, _| 0);
    assert_eq!(code, 3);
    let errors = String::from_utf8(errors).unwrap();
    assert!(
        errors.starts_with("ERROR: writing the span file: ") && errors.contains("a-file") && errors.ends_with('\n'),
        "{errors:?}"
    );
}
