// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Package span records what this process spent its time on and writes it as Jaeger JSON, the same file the JVM half of
// the build writes through `JaegerJsonSpanExporter`.
//
// It is a writer, not a tracing library. There is no context propagation, no sampling, no exporter and no OpenTelemetry
// dependency: a packing action starts a root span, one child per jar, and writes the file. That is the whole surface,
// and it is why this module still has exactly one dependency.
//
// The schema is deliberately the Kotlin exporter's rather than the OTel specification's, because the two disagree and
// the trace these files are merged into is read by the same Jaeger UI:
//   - every scalar tag `value` is a JSON *string*, whatever the `type` says. `writeStringProperty("value", v.toString())`
//     in the Kotlin writer, so `{"key":"sources","type":"long","value":"3"}` and not `"value":3`.
//   - `startTime` and `duration` are microseconds, truncated, and `startTimeNano`/`durationNano` carry the full
//     precision alongside them. Only the microsecond pair is Jaeger's; the nanosecond pair is the Kotlin writer's
//     addition and is reproduced here so a merged file is uniform.
//   - a span with no tags writes no `tags` key at all, and a root span writes no `references` key.
//
// See ../../README.md for the actions these spans describe.
package span

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"strconv"
	"sync"
	"time"
)

// processID is the only process a file written here describes. The Kotlin exporter hard-codes the same "p1", and a
// merge across producers has to renumber them either way.
const processID = "p1"

// Tracer collects the spans of one run and writes them once, at the end. A nil *Tracer is a working no-op, so a run
// without `--trace-file` takes the same code path with no tracing branch in it.
type Tracer struct {
	serviceName string
	// startedAt is what the process's `time` tag reports: when the run began, not when the file was written. Fixed at
	// construction so a re-read of the same trace never changes.
	startedAt time.Time

	// clock and newSpanID are fields rather than direct calls so that a test can freeze both. Nothing outside a test
	// replaces them.
	clock     func() time.Time
	newSpanID func() string

	// mu guards spans and every field of every Span in it. A packing action holds one span; a whole-tranche one-shot run
	// packs thousands of jars on GOMAXPROCS goroutines, and each of them starts, annotates and ends its own span.
	mu      sync.Mutex
	traceID string
	spans   []*Span
}

// Span is one timed operation. Every method tolerates a nil receiver, which is what a nil Tracer hands out.
type Span struct {
	tracer   *Tracer
	id       string
	parentID string
	name     string
	start    time.Time
	end      time.Time
	failed   bool
	tags     []tag
}

type tag struct {
	key, kind, value string
}

// NewTracer starts a trace. serviceName names the producer in the merged trace - the Kotlin build calls itself "build",
// so this one must not.
func NewTracer(serviceName string) *Tracer {
	return &Tracer{
		serviceName: serviceName,
		startedAt:   time.Now(),
		clock:       time.Now,
		newSpanID:   func() string { return randomHex(8) },
		traceID:     randomHex(16),
	}
}

// Start opens a span, optionally under parent. The returned span must be ended; one that is not gets its duration from
// the moment the file is written, so an early return leaves a span that is too long rather than a hole in the trace.
func (t *Tracer) Start(name string, parent *Span) *Span {
	if t == nil {
		return nil
	}
	span := &Span{tracer: t, id: t.newSpanID(), name: name, start: t.clock()}
	if parent != nil {
		span.parentID = parent.id
	}
	t.mu.Lock()
	defer t.mu.Unlock()
	// Appended at Start, so the file lists spans in the order they opened and the root comes first. Ending order would
	// put the root last and the fan-out in whatever order the scheduler produced.
	t.spans = append(t.spans, span)
	return span
}

// SetString records a `string` tag.
func (s *Span) SetString(key string, value string) {
	s.addTag(tag{key: key, kind: "string", value: value})
}

// SetInt records a `long` tag - the type name OTel gives a 64-bit integer, and therefore the one the Kotlin exporter
// writes. The value is still written as a JSON string; see the package comment.
func (s *Span) SetInt(key string, value int64) {
	s.addTag(tag{key: key, kind: "long", value: strconv.FormatInt(value, 10)})
}

// Fail marks the span as errored, which is what puts it in red in the Jaeger UI. err's message is recorded as a tag
// rather than as a log event: the Kotlin exporter can write events, and nothing here has any to write.
func (s *Span) Fail(err error) {
	if s == nil {
		return
	}
	s.tracer.mu.Lock()
	s.failed = true
	s.tracer.mu.Unlock()
	if err != nil {
		s.SetString("error.message", err.Error())
	}
}

// End stops the span. The first call wins, so an End in a defer and an End on the success path cannot disagree.
func (s *Span) End() {
	if s == nil {
		return
	}
	s.tracer.mu.Lock()
	defer s.tracer.mu.Unlock()
	if s.end.IsZero() {
		s.end = s.tracer.clock()
	}
}

func (s *Span) addTag(t tag) {
	if s == nil {
		return
	}
	s.tracer.mu.Lock()
	defer s.tracer.mu.Unlock()
	s.tags = append(s.tags, t)
}

// WriteFile writes the trace, creating the parent directory. A nil Tracer writes nothing and reports no error.
//
// It never writes to stdout and never panics: in a worker, stdout is the protocol and a panic loses the request that
// caused it, so an unwritable path has to come back as an error the caller can report against its own request.
func (t *Tracer) WriteFile(path string) error {
	if t == nil {
		return nil
	}
	data, err := t.encode()
	if err != nil {
		return err
	}
	if dir := filepath.Dir(path); dir != "" {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return err
		}
	}
	return os.WriteFile(path, data, 0o644)
}

// The document, in the field order the Kotlin exporter writes. `omitempty` on tags and references is that writer's
// "only if there is something to say", not a size optimisation.
type document struct {
	Data []traceDocument `json:"data"`
}

type traceDocument struct {
	TraceID   string                     `json:"traceID"`
	Processes map[string]processDocument `json:"processes"`
	Spans     []spanDocument             `json:"spans"`
}

type processDocument struct {
	ServiceName string        `json:"serviceName"`
	Tags        []tagDocument `json:"tags"`
}

type spanDocument struct {
	TraceID       string              `json:"traceID"`
	SpanID        string              `json:"spanID"`
	OperationName string              `json:"operationName"`
	ProcessID     string              `json:"processID"`
	StartTime     int64               `json:"startTime"`
	Duration      int64               `json:"duration"`
	StartTimeNano int64               `json:"startTimeNano"`
	DurationNano  int64               `json:"durationNano"`
	Tags          []tagDocument       `json:"tags,omitempty"`
	References    []referenceDocument `json:"references,omitempty"`
}

type tagDocument struct {
	Key   string `json:"key"`
	Type  string `json:"type"`
	Value string `json:"value"`
}

type referenceDocument struct {
	RefType string `json:"refType"`
	TraceID string `json:"traceID"`
	SpanID  string `json:"spanID"`
}

func (t *Tracer) encode() ([]byte, error) {
	t.mu.Lock()
	defer t.mu.Unlock()

	now := t.clock()
	spans := make([]spanDocument, 0, len(t.spans))
	for _, s := range t.spans {
		end := s.end
		if end.IsZero() {
			end = now
		}
		elapsed := end.Sub(s.start)

		tags := make([]tagDocument, 0, len(s.tags)+2)
		if s.failed {
			// Both tags, in this order, are what the Kotlin exporter writes for an errored span - and `error` is a
			// `boolean` whose value is still the string "true".
			tags = append(tags,
				tagDocument{Key: "otel.status_code", Type: "string", Value: "ERROR"},
				tagDocument{Key: "error", Type: "boolean", Value: "true"})
		}
		for _, tag := range s.tags {
			tags = append(tags, tagDocument{Key: tag.key, Type: tag.kind, Value: tag.value})
		}
		if len(tags) == 0 {
			tags = nil
		}

		var references []referenceDocument
		if s.parentID != "" {
			// A parent in another trace cannot be expressed: Jaeger's own model allows it, OpenTelemetry's does not,
			// and the Kotlin exporter writes the span's own trace id here for that reason.
			references = []referenceDocument{{RefType: "CHILD_OF", TraceID: t.traceID, SpanID: s.parentID}}
		}

		spans = append(spans, spanDocument{
			TraceID:       t.traceID,
			SpanID:        s.id,
			OperationName: s.name,
			ProcessID:     processID,
			StartTime:     s.start.UnixMicro(),
			Duration:      elapsed.Microseconds(),
			StartTimeNano: s.start.UnixNano(),
			DurationNano:  elapsed.Nanoseconds(),
			Tags:          tags,
			References:    references,
		})
	}

	doc := document{Data: []traceDocument{{
		TraceID: t.traceID,
		Processes: map[string]processDocument{processID: {
			ServiceName: t.serviceName,
			Tags: []tagDocument{{
				Key: "time", Type: "string",
				// RFC 1123 with a numeric offset, which is what Java's RFC_1123_DATE_TIME produces for a zone that is
				// not UTC. Read by a human, parsed by nothing.
				Value: t.startedAt.Format(time.RFC1123Z),
			}},
		}},
		Spans: spans,
	}}}

	var buffer bytes.Buffer
	encoder := json.NewEncoder(&buffer)
	// Jackson does not escape `<`, `>` or `&`, and an operation name or a jar path may contain any of them. Without
	// this, the same span written by the two halves of the build would differ byte for byte.
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(doc); err != nil {
		return nil, err
	}
	// Encode appends a newline; the Kotlin writer's last byte is the closing brace.
	return bytes.TrimRight(buffer.Bytes(), "\n"), nil
}

// randomHex is where the ids come from, and they are random rather than counted for one reason: every span file in a
// build is written by a different process. A counter would hand the same `0000000000000001` to all several thousand
// packing actions, and the merge that puts them in one trace would collapse them onto each other. Nothing reads a span
// file during the build - the action is `+no-cache` and the file is a pure side output - so its bytes not being
// reproducible costs the build nothing.
//
// crypto/rand rather than math/rand because it needs no seeding and cannot be seeded identically by two processes that
// started in the same millisecond. Its Read never fails; the documented contract is that it panics instead of
// returning an error, so there is nothing here to handle.
func randomHex(size int) string {
	buffer := make([]byte, size)
	rand.Read(buffer)
	return hex.EncodeToString(buffer)
}
