//! The expectations are whole documents, written by hand. A decoder would accept any field name and any nesting. The
//! reader of a merged build trace uses these bytes as the schema, and the field order of the Kotlin exporter is part
//! of it. Each expectation matches the Go original `internal/span/span_test.go`, except the `time` tag.

use std::collections::HashSet;
use std::fs;
use std::io;
use std::sync::atomic::{AtomicI64, AtomicU64, Ordering};

use tracing::dispatcher::with_default;
use tracing::field::Empty;
use tracing::info_span;

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

/// Returns the document of `tracer` as text.
fn encoded(tracer: &Tracer) -> String {
    String::from_utf8(tracer.encode().unwrap()).unwrap()
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
    with_default(&tracer.dispatch(), || {
        let span = info_span!("pack jar", jar = "app.jar", sources = Empty, bytes = Empty);
        span.record("sources", 3);
        span.record("bytes", 4096);
    });

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
    with_default(&tracer.dispatch(), || {
        let root = info_span!("pack content modules");
        let child = info_span!(parent: &root, "pack jar");
        drop(child);
        drop(root);
    });

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

#[test]
fn a_contextual_parent_is_the_entered_span() {
    let tracer = new_test_tracer();
    with_default(&tracer.dispatch(), || {
        let root = info_span!("pack content modules");
        let _entered = root.enter();
        drop(info_span!("pack jar"));
    });
    assert!(
        encoded(&tracer).contains(r#""spanID":"0000000000000001"}]"#),
        "{}",
        encoded(&tracer)
    );
}

#[test]
fn a_failed_span_carries_the_kotlin_writers_status_tags_first() {
    let tracer = new_test_tracer();
    with_default(&tracer.dispatch(), || {
        let span = info_span!("pack jar", jar = "app.jar");
        fail(&span, &"no inputs");
    });

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
    with_default(&tracer.dispatch(), || {
        // Tick 1 starts the span, and the encode is tick 2.
        let span = info_span!("pack jar");
        let encoded = encoded(&tracer);
        assert!(
            encoded.contains(r#""duration":1000,"#),
            "an open span must end at the write: {encoded}"
        );
        drop(span);
    });
}

/// The Go `End` is idempotent. A `tracing` span ends when its last handle drops, and only the first end counts.
#[test]
fn a_span_ends_once() {
    let tracer = new_test_tracer();
    with_default(&tracer.dispatch(), || {
        let span = info_span!("pack jar");
        let clone = span.clone();
        drop(clone);
        drop(span.enter());
        drop(span);
    });
    let encoded = encoded(&tracer);
    assert!(encoded.contains(r#""duration":1000,"#), "the span must end once: {encoded}");
}

#[test]
fn nothing_is_escaped_that_jackson_would_not_escape() {
    let tracer = new_test_tracer();
    with_default(&tracer.dispatch(), || {
        // A jar name cannot hold these characters, but an error message can.
        drop(info_span!("pack jar", error.message = "a<b && c>d"));
    });
    let encoded = encoded(&tracer);
    assert!(encoded.contains(r#""value":"a<b && c>d""#), "HTML escaping is on: {encoded}");
    assert!(!encoded.contains("\\u00"), "something was escaped: {encoded}");
}

#[test]
fn spans_without_a_tracer_are_no_ops() {
    let tracer = new_test_tracer();
    // The call site in `fail` is shared with the other tests. A call site that registers while no dispatch is live can
    // keep the interest "never", so a live dispatch must exist. The thread still records into no dispatch.
    let _live = tracer.dispatch();
    // A run without `--trace-file` installs no tracer and runs the same statements.
    with_default(&tracing::Dispatch::none(), || {
        let root = info_span!("pack content modules");
        let span = info_span!(parent: &root, "pack jar", jar = "app.jar", bytes = Empty);
        span.record("bytes", 1);
        fail(&span, &"boom");
    });
    assert_eq!(encoded(&tracer), document(""));
}

#[test]
fn write_file_creates_the_parent_directory() {
    let tracer = new_test_tracer();
    with_default(&tracer.dispatch(), || drop(info_span!("pack jar")));

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
    assert_ne!(first.inner.trace_id, second.inner.trace_id, "two runs share a trace id");
    assert_eq!(first.inner.trace_id.len(), 32, "{}", first.inner.trace_id);

    let mut seen = HashSet::new();
    for _ in 0..1000 {
        let id = (first.inner.new_span_id)();
        assert_eq!(id.len(), 16, "a span id is 16 hex characters: {id}");
        assert!(id.bytes().all(|byte| byte.is_ascii_hexdigit()), "{id}");
        assert!(seen.insert(id.clone()), "span id {id} was handed out twice");
    }
}

#[test]
fn concurrent_spans_are_all_recorded() {
    // A whole-tranche run packs thousands of jars on many threads, and each thread records its own span.
    let tracer = Tracer::new("packer");
    let dispatch = tracer.dispatch();
    with_default(&dispatch, || {
        let root = info_span!("pack content modules");
        std::thread::scope(|scope| {
            for index in 0..64 {
                let dispatch = &dispatch;
                let root = &root;
                scope.spawn(move || {
                    with_default(dispatch, || {
                        let span = info_span!(parent: root, "pack jar", jar = %format!("{index}.jar"), sources = Empty);
                        span.record("sources", index);
                    });
                });
            }
        });
    });

    assert_eq!(tracer.state().spans.len(), 65);
    assert!(tracer.state().spans.iter().all(|span| span.end.is_some()));
    let root_id = tracer.state().spans[0].id.clone();
    assert!(
        tracer.state().spans[1..]
            .iter()
            .all(|span| span.parent_id.as_deref() == Some(root_id.as_str()))
    );
    let written = encoded(&tracer);
    for index in 0..64 {
        let expected = format!(r#""value":"{index}.jar""#);
        assert!(written.contains(&expected), "{expected} is missing from the document");
    }
}

#[test]
fn fields_map_to_the_tag_types_of_the_go_original() {
    let tracer = new_test_tracer();
    with_default(&tracer.dispatch(), || {
        let job = String::from("dev-dist-composer");
        let span = info_span!("job", otel.name = %job, kind = "files", path = ?"a\"b", count = 7u64, delta = -2i64);
        fail(&span, &"no inputs");
    });
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

/// The Go original recorded only string and integer tags. Each other field type makes the write fail, and the error
/// names the field, the type and the span.
#[test]
fn a_field_of_another_type_fails_the_write_and_names_the_field() {
    let cases: [(&str, fn()); 6] = [
        ("the field cached has the type bool", || drop(info_span!("pack jar", cached = true))),
        ("the field ratio has the type f64", || drop(info_span!("pack jar", ratio = 0.5))),
        ("the field big has the type i128", || drop(info_span!("pack jar", big = 1i128))),
        ("the field huge has the type u128", || drop(info_span!("pack jar", huge = 1u128))),
        ("the field data has the type &[u8]", || {
            drop(info_span!("pack jar", data = &b"abc"[..]));
        }),
        ("the field bytes has the type f64", || {
            let span = info_span!("pack jar", bytes = Empty);
            span.record("bytes", 1.5);
        }),
    ];
    for (message, instrument) in cases {
        let tracer = new_test_tracer();
        with_default(&tracer.dispatch(), instrument);
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("trace.json");
        let error = tracer.write_file(&path).unwrap_err();
        let text = error.to_string();
        assert_eq!(error.kind(), io::ErrorKind::InvalidInput, "{text}");
        assert!(
            text.contains(message) && text.contains("\"pack jar\"") && text.contains("trace.json"),
            "{message}: {text}"
        );
        assert!(!path.exists(), "{message}: a refused trace was written");
    }
}

#[test]
fn an_error_field_fails_the_write() {
    let tracer = new_test_tracer();
    let error = io::Error::other("boom");
    with_default(&tracer.dispatch(), || {
        drop(info_span!("pack jar", cause = &error as &(dyn std::error::Error + 'static)));
    });
    let message = tracer.encode().unwrap_err();
    assert!(message.contains("the field cause has the type &dyn Error"), "{message}");
}

/// The Go original had no events. Only the event of `fail` is in the subset.
#[test]
fn an_event_other_than_fail_fails_the_write_and_names_the_event() {
    let tracer = new_test_tracer();
    with_default(&tracer.dispatch(), || {
        let span = info_span!("pack jar");
        tracing::info!(parent: &span, files = 3);
    });
    let message = tracer.encode().unwrap_err();
    assert!(
        message.contains("event") && message.contains("tests.rs") && message.contains("trace::fail"),
        "{message}"
    );

    let tracer = new_test_tracer();
    with_default(&tracer.dispatch(), || tracing::error!(detail = "outside of fail"));
    assert!(tracer.encode().is_err(), "an error event outside of `fail` was accepted");
}

/// The expectation is the output of Go `encoding/json` with `SetEscapeHTML(false)`, as the Go tracer wrote it.
#[test]
fn a_special_name_is_escaped_as_the_go_encoder_escapes_it() {
    let tracer = new_test_tracer();
    with_default(&tracer.dispatch(), || {
        drop(info_span!(
            "probe",
            otel.name = "q\"b\\s\u{8}\u{c}\n\r\t\u{1}\u{1f}\u{7f}<>&\u{2028}\u{2029}é"
        ));
    });
    let expected = document(concat!(
        r#"{"traceID":"00112233445566778899aabbccddeeff","spanID":"0000000000000001","#,
        "\"operationName\":\"q\\\"b\\\\s\\b\\f\\n\\r\\t\\u0001\\u001f\u{7f}<>&\\u2028\\u2029é\",",
        r#""processID":"p1","startTime":1000000000001000,"duration":1000,"#,
        r#""startTimeNano":1000000000001000000,"durationNano":1000000}"#
    ));
    assert_eq!(encoded(&tracer), expected);
}
