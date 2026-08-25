// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package span

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

// The expectations below are whole documents, written by hand, rather than a structure walked with a decoder. A decoder
// would agree with any field name and any nesting this package happened to produce; these bytes are the schema a merged
// build trace is read from, and the Kotlin exporter's field order is part of it.
//
// Every one of them was checked against JaegerJsonSpanExporter.kt: `value` is a JSON string even where `type` says
// `long` or `boolean`, `startTimeNano`/`durationNano` sit between `duration` and `tags`, a span with nothing to say
// omits `tags` and a root span omits `references`.

const testTraceID = "00112233445566778899aabbccddeeff"

// testTime is a round number so that every timestamp in an expectation can be read: microseconds since the epoch are
// 1000000000000000, and one tick of the test clock is a millisecond.
var testTime = time.Unix(1_000_000_000, 0).UTC()

// newTestTracer freezes the two things about a trace that are deliberately not reproducible: its ids and its clock. It
// reaches into unexported fields because that is cheaper than an injection point no production caller would use.
func newTestTracer(t *testing.T) *Tracer {
	t.Helper()

	tracer := NewTracer("test-packer")
	tracer.traceID = testTraceID
	tracer.startedAt = testTime

	ids := 0
	tracer.newSpanID = func() string {
		ids++
		return fmt.Sprintf("%016x", ids)
	}
	ticks := 0
	tracer.clock = func() time.Time {
		ticks++
		return testTime.Add(time.Duration(ticks) * time.Millisecond)
	}
	return tracer
}

func encodeTrace(t *testing.T, tracer *Tracer) string {
	t.Helper()

	data, err := tracer.encode()
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

// process is the document's prologue, which every expectation below repeats verbatim.
const testProcess = `"processes":{"p1":{"serviceName":"test-packer","tags":[` +
	`{"key":"time","type":"string","value":"Sun, 09 Sep 2001 01:46:40 +0000"}]}}`

func TestATraceWithNoSpansIsStillAWholeDocument(t *testing.T) {
	// The empty case matters because it is what a failed action writes: the file was declared, so it has to exist and
	// has to parse. An open `spans` array with nothing in it is what the Kotlin writer leaves behind too.
	want := `{"data":[{"traceID":"` + testTraceID + `",` + testProcess + `,"spans":[]}]}`
	if got := encodeTrace(t, newTestTracer(t)); got != want {
		t.Errorf("\n got %s\nwant %s", got, want)
	}
}

func TestASpanCarriesItsTagsInTheOrderTheyWereSet(t *testing.T) {
	tracer := newTestTracer(t)
	span := tracer.Start("pack jar", nil)
	span.SetString("jar", "app.jar")
	span.SetInt("sources", 3)
	span.SetInt("bytes", 4096)
	span.End()

	want := `{"data":[{"traceID":"` + testTraceID + `",` + testProcess + `,"spans":[` +
		`{"traceID":"` + testTraceID + `","spanID":"0000000000000001","operationName":"pack jar","processID":"p1",` +
		`"startTime":1000000000001000,"duration":1000,"startTimeNano":1000000000001000000,"durationNano":1000000,` +
		`"tags":[{"key":"jar","type":"string","value":"app.jar"},` +
		`{"key":"sources","type":"long","value":"3"},` +
		`{"key":"bytes","type":"long","value":"4096"}]}]}]}`
	if got := encodeTrace(t, tracer); got != want {
		t.Errorf("\n got %s\nwant %s", got, want)
	}
}

func TestAChildRefersToItsParentWithinTheSameTrace(t *testing.T) {
	tracer := newTestTracer(t)
	root := tracer.Start("pack content modules", nil)
	child := tracer.Start("pack jar", root)
	child.End()
	root.End()

	// The root is written first because spans are recorded when they open, not when they close - which is also why the
	// root's duration covers the child's.
	want := `{"data":[{"traceID":"` + testTraceID + `",` + testProcess + `,"spans":[` +
		`{"traceID":"` + testTraceID + `","spanID":"0000000000000001","operationName":"pack content modules",` +
		`"processID":"p1","startTime":1000000000001000,"duration":3000,` +
		`"startTimeNano":1000000000001000000,"durationNano":3000000},` +
		`{"traceID":"` + testTraceID + `","spanID":"0000000000000002","operationName":"pack jar","processID":"p1",` +
		`"startTime":1000000000002000,"duration":1000,"startTimeNano":1000000000002000000,"durationNano":1000000,` +
		`"references":[{"refType":"CHILD_OF","traceID":"` + testTraceID + `","spanID":"0000000000000001"}]}]}]}`
	if got := encodeTrace(t, tracer); got != want {
		t.Errorf("\n got %s\nwant %s", got, want)
	}
}

func TestAFailedSpanCarriesTheKotlinWritersStatusTagsFirst(t *testing.T) {
	tracer := newTestTracer(t)
	span := tracer.Start("pack jar", nil)
	span.SetString("jar", "app.jar")
	span.Fail(errors.New("no inputs"))
	span.End()

	// `otel.status_code` and `error` are emitted at write time and therefore precede every tag the caller set,
	// whenever Fail was called. `error` is typed `boolean` and valued with the *string* "true": the Kotlin writer
	// writes it that way and the Jaeger UI reads it that way.
	want := `"tags":[{"key":"otel.status_code","type":"string","value":"ERROR"},` +
		`{"key":"error","type":"boolean","value":"true"},` +
		`{"key":"jar","type":"string","value":"app.jar"},` +
		`{"key":"error.message","type":"string","value":"no inputs"}]`
	if got := encodeTrace(t, tracer); !strings.Contains(got, want) {
		t.Errorf("\n got %s\nwant a span containing %s", got, want)
	}
}

func TestASpanNeverEndedIsClosedAtWriteTime(t *testing.T) {
	tracer := newTestTracer(t)
	tracer.Start("pack jar", nil) // tick 1; the encode below is tick 2

	if got, want := encodeTrace(t, tracer), `"duration":1000,`; !strings.Contains(got, want) {
		t.Errorf("an unended span should be closed when the file is written\n got %s\nwant %s", got, want)
	}
}

func TestEndIsIdempotent(t *testing.T) {
	tracer := newTestTracer(t)
	span := tracer.Start("pack jar", nil)
	span.End() // tick 2
	span.End() // tick 3, and must not move the end

	if got, want := encodeTrace(t, tracer), `"duration":1000,`; !strings.Contains(got, want) {
		t.Errorf("a second End must not extend the span\n got %s\nwant %s", got, want)
	}
}

func TestNothingIsEscapedThatJacksonWouldNotEscape(t *testing.T) {
	tracer := newTestTracer(t)
	span := tracer.Start("pack jar", nil)
	// A jar name cannot contain these, but an error message can, and `encoding/json` escapes all three by default.
	span.SetString("error.message", "a<b && c>d")
	span.End()

	got := encodeTrace(t, tracer)
	if !strings.Contains(got, `"value":"a<b && c>d"`) {
		t.Errorf("HTML escaping is on: %s", got)
	}
	if strings.Contains(got, `\u00`) {
		t.Errorf("something was escaped: %s", got)
	}
}

func TestANilTracerIsAWorkingNoOp(t *testing.T) {
	var tracer *Tracer

	// The whole point: a run without --trace-file executes the same statements.
	root := tracer.Start("pack content modules", nil)
	span := tracer.Start("pack jar", root)
	span.SetString("jar", "app.jar")
	span.SetInt("bytes", 1)
	span.Fail(errors.New("boom"))
	span.End()
	root.End()

	if root != nil || span != nil {
		t.Fatal("a nil tracer must hand out nil spans")
	}
	directory := t.TempDir()
	if err := tracer.WriteFile(filepath.Join(directory, "unwanted", "trace.json")); err != nil {
		t.Fatal(err)
	}
	// Not even the parent directory: a no-op tracer touches the file system not at all.
	if entries, err := os.ReadDir(directory); err != nil || len(entries) != 0 {
		t.Fatalf("a nil tracer wrote something: %v, %v", entries, err)
	}
}

func TestWriteFileCreatesTheParentDirectory(t *testing.T) {
	tracer := newTestTracer(t)
	tracer.Start("pack jar", nil).End()

	path := filepath.Join(t.TempDir(), "bazel-out", "app.jar.spans.json")
	if err := tracer.WriteFile(path); err != nil {
		t.Fatal(err)
	}
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	// The file's last byte is the closing brace: no trailing newline, which is what the Kotlin writer leaves.
	if len(content) == 0 || content[len(content)-1] != '}' {
		t.Errorf("the file must end with the document: %q", string(content))
	}
	if strings.Contains(string(content), "\n") {
		t.Errorf("the document is one line: %q", string(content))
	}
}

func TestWriteFileReportsAnUnwritablePathInsteadOfPanicking(t *testing.T) {
	// A panic in a worker loses the request that caused it, and a message on stdout corrupts the protocol, so the only
	// way out of here is a returned error.
	blocked := filepath.Join(t.TempDir(), "a-file")
	if err := os.WriteFile(blocked, nil, 0o644); err != nil {
		t.Fatal(err)
	}

	tracer := newTestTracer(t)
	err := tracer.WriteFile(filepath.Join(blocked, "trace.json"))
	if err == nil {
		t.Fatal("expected an error for a path under a regular file")
	}
	if !strings.Contains(err.Error(), "trace.json") && !strings.Contains(err.Error(), "a-file") {
		t.Errorf("the error should name the path: %v", err)
	}
}

func TestIdsAreDistinctAcrossTracersAndSpans(t *testing.T) {
	// Not decoration: a build writes one of these files per action, and the merge that puts them in one trace has
	// nothing but these ids to tell two actions' spans apart.
	first, second := NewTracer("packer"), NewTracer("packer")
	if first.traceID == second.traceID {
		t.Error("two runs share a trace id")
	}
	if len(first.traceID) != 32 {
		t.Errorf("a trace id is 32 hex characters, got %q", first.traceID)
	}

	seen := make(map[string]struct{})
	for range 1000 {
		id := first.newSpanID()
		if len(id) != 16 {
			t.Fatalf("a span id is 16 hex characters, got %q", id)
		}
		if _, dup := seen[id]; dup {
			t.Fatalf("span id %q was handed out twice", id)
		}
		seen[id] = struct{}{}
	}
}

func TestConcurrentSpansAreAllRecorded(t *testing.T) {
	// A whole-tranche one-shot run packs thousands of jars on GOMAXPROCS goroutines, each annotating its own span while
	// the others annotate theirs. Run this one under -race to get the point of it.
	tracer := NewTracer("packer")
	root := tracer.Start("pack content modules", nil)

	var wg sync.WaitGroup
	for i := range 64 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			span := tracer.Start("pack jar", root)
			defer span.End()
			span.SetString("jar", fmt.Sprintf("%d.jar", i))
			span.SetInt("sources", int64(i))
		}()
	}
	wg.Wait()
	root.End()

	if got := len(tracer.spans); got != 65 {
		t.Fatalf("recorded %d spans, want 65", got)
	}
	written := encodeTrace(t, tracer)
	for i := range 64 {
		if want := fmt.Sprintf(`"value":"%d.jar"`, i); !strings.Contains(written, want) {
			t.Errorf("%s is missing from the document", want)
		}
	}
}
