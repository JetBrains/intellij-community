// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package worker

import (
	"context"
	"fmt"
	"io"
	"strings"
	"sync"
	"testing"
)

// appendTestRequest frames one WorkRequest the way Bazel would, and deliberately shares no code with wire.go. It handles
// only the small values these tests use - every length and id below 128, so every varint is one byte - and panics on
// anything larger, which is why it needs no varint loop to get wrong. A helper that called appendVarint would make these
// tests agree with a bug in it.
func appendTestRequest(dst []byte, requestID int, cancel bool, args ...string) []byte {
	small := func(what string, v int) byte {
		if v < 0 || v > 127 {
			panic(fmt.Sprintf("appendTestRequest: %s is %d, past the one-byte varint this helper writes", what, v))
		}
		return byte(v)
	}

	var body []byte
	for _, arg := range args {
		body = append(body, 0x0A, small("an argument length", len(arg))) // field 1 (arguments), wire 2
		body = append(body, arg...)
	}
	if requestID != 0 {
		// Absent rather than zero, which is what Bazel does for a singleplex worker.
		body = append(body, 0x18, small("a request id", requestID)) // field 3 (request_id), wire 0
	}
	if cancel {
		body = append(body, 0x20, 0x01) // field 4 (cancel), wire 0
	}
	dst = append(dst, small("a body length", len(body)))
	return append(dst, body...)
}

// readTestResponses walks the framed responses a run wrote, with its own length-prefix loop and its own field dispatch.
// The duplication against appendResponse is this test's whole value: a reader built on the writer would confirm that the
// writer agrees with itself.
func readTestResponses(t *testing.T, raw string) []Response {
	t.Helper()

	small := func(b []byte, what string) (int, []byte) {
		t.Helper()
		if len(b) == 0 {
			t.Fatalf("%s: no bytes left", what)
		}
		if b[0] > 127 {
			t.Fatalf("%s: %#02x is a multi-byte varint, which these tests are not supposed to produce", what, b[0])
		}
		return int(b[0]), b[1:]
	}

	remaining := []byte(raw)
	var responses []Response
	for len(remaining) != 0 {
		size, rest := small(remaining, "a response length prefix")
		if len(rest) < size {
			t.Fatalf("a response promises %d bytes and %d follow: % x", size, len(rest), raw)
		}
		body, remainder := rest[:size], rest[size:]
		remaining = remainder

		var response Response
		for len(body) != 0 {
			key := body[0]
			body = body[1:]
			switch key {
			case 0x08: // field 1 (exit_code), wire 0
				response.ExitCode, body = small(body, "an exit code")
			case 0x12: // field 2 (output), wire 2
				var length int
				length, body = small(body, "an output length")
				if len(body) < length {
					t.Fatalf("an output promises %d bytes and %d follow", length, len(body))
				}
				response.Output, body = string(body[:length]), body[length:]
			case 0x18: // field 3 (request_id), wire 0
				response.RequestID, body = small(body, "a request id")
			case 0x20: // field 4 (was_cancelled), wire 0
				var flag int
				flag, body = small(body, "a cancellation flag")
				response.WasCancelled = flag != 0
			default:
				t.Fatalf("unexpected field key %#02x in % x", key, raw)
			}
		}
		responses = append(responses, response)
	}
	return responses
}

func TestServeAnswersEveryRequestWithItsOwnId(t *testing.T) {
	requests := appendTestRequest(nil, 1, false, "a")
	requests = appendTestRequest(requests, 2, false, "b")

	var out strings.Builder
	err := serve(strings.NewReader(string(requests)), &out, func(ctx context.Context, args []string, w io.Writer) int {
		fmt.Fprintf(w, "handled %s", args[0])
		return 0
	})
	if err != nil {
		t.Fatal(err)
	}
	byID := map[int]Response{}
	for _, response := range readTestResponses(t, out.String()) {
		byID[response.RequestID] = response
	}
	if got, want := len(byID), 2; got != want {
		t.Fatalf("%d responses, want %d: % x", got, want, out.String())
	}
	if got, want := byID[1].Output, "handled a"; got != want {
		t.Errorf("request 1 output is %q, want %q", got, want)
	}
	if got, want := byID[2].Output, "handled b"; got != want {
		t.Errorf("request 2 output is %q, want %q", got, want)
	}
}

func TestServeCarriesTheHandlerExitCode(t *testing.T) {
	var out strings.Builder
	requests := appendTestRequest(nil, 0, false, "x")
	if err := serve(strings.NewReader(string(requests)), &out, func(context.Context, []string, io.Writer) int {
		return 3
	}); err != nil {
		t.Fatal(err)
	}
	responses := readTestResponses(t, out.String())
	if len(responses) != 1 {
		t.Fatalf("%d responses, want 1", len(responses))
	}
	// A singleplex request carries no requestId at all, so the response must not invent one.
	if responses[0].RequestID != 0 {
		t.Errorf("response requestId is %d, want 0", responses[0].RequestID)
	}
	if got, want := responses[0].ExitCode, 3; got != want {
		t.Errorf("exit code is %d, want %d", got, want)
	}
}

func TestServeRunsRequestsConcurrently(t *testing.T) {
	// A multiplex worker that answered one request at a time would be a worker pool of one, which is the whole reason
	// the protocol carries an id. Both handlers have to be running at once for this to return.
	both := make(chan struct{})
	var once sync.Once
	var arrived int
	var mu sync.Mutex
	var out strings.Builder

	requests := appendTestRequest(nil, 1, false)
	requests = appendTestRequest(requests, 2, false)

	err := serve(strings.NewReader(string(requests)), &out,
		func(ctx context.Context, args []string, w io.Writer) int {
			mu.Lock()
			arrived++
			if arrived == 2 {
				once.Do(func() { close(both) })
			}
			mu.Unlock()
			<-both
			return 0
		})
	if err != nil {
		t.Fatal(err)
	}
	if got := len(readTestResponses(t, out.String())); got != 2 {
		t.Errorf("%d responses, want 2", got)
	}
}

func TestServeReportsACancelledRequestAsCancelled(t *testing.T) {
	// The cancel request itself gets no response of its own: the one response for that id comes from the work, which is
	// what keeps the count at one per request even when the work had already finished.
	started := make(chan struct{})
	requests, writer := io.Pipe()
	var out strings.Builder
	done := make(chan error, 1)
	go func() {
		done <- serve(requests, &out, func(ctx context.Context, args []string, w io.Writer) int {
			close(started)
			<-ctx.Done()
			return 0
		})
	}()
	// One complete frame per Write, so the reader blocks until each message is whole - which is what this test wants and
	// what the framing gives it for free.
	if _, err := writer.Write(appendTestRequest(nil, 7, false)); err != nil {
		t.Fatal(err)
	}
	<-started
	if _, err := writer.Write(appendTestRequest(nil, 7, true)); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := <-done; err != nil {
		t.Fatal(err)
	}
	responses := readTestResponses(t, out.String())
	if len(responses) != 1 {
		t.Fatalf("%d responses, want exactly 1: % x", len(responses), out.String())
	}
	if !responses[0].WasCancelled || responses[0].RequestID != 7 {
		t.Errorf("response is %+v, want request 7 reported as cancelled", responses[0])
	}
}

func TestServeReportsATruncatedStream(t *testing.T) {
	// A length prefix promising more than arrives is not an end of stream, and serve must not treat it as one: silently
	// returning nil would leave Bazel waiting for a response that is never coming.
	var out strings.Builder
	err := serve(strings.NewReader("\x05\x01\x02"), &out, func(context.Context, []string, io.Writer) int {
		t.Error("the handler ran on a truncated request")
		return 0
	})
	if err == nil {
		t.Fatal("a truncated stream was reported as a clean end")
	}
	if !strings.Contains(err.Error(), "work request") {
		t.Errorf("error is %q, which does not say which side of the protocol failed", err)
	}
}

func TestIsWorkerStartup(t *testing.T) {
	if IsWorkerStartup([]string{"--flagfile=recipe.params"}) {
		t.Error("a one-shot command line was taken for a worker startup")
	}
	if !IsWorkerStartup([]string{"--persistent_worker"}) {
		t.Error("--persistent_worker was not recognised")
	}
}
