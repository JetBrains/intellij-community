// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Package worker speaks Bazel's persistent worker protocol, in its proto dialect.
//
// The wire format is in wire.go: a varint of the message size followed by exactly that many bytes, in both directions -
// `writeDelimitedTo` and `parseDelimitedFrom` on Bazel's side. It is decoded here by hand, tag by tag, with no protobuf
// dependency and no generated schema, because at this size the schema is four fields of a message that has not changed
// shape in years and the codec is eighty lines. That is also what the repository's other worker does:
// ../../../community/build/jvm-rules/worker-framework/protocol.kt dispatches on `tag.shr(3)` over the same field
// numbers and reaches for protobuf-java only as a byte-level codec, never for a generated message.
//
// Nothing in the action selects the dialect: proto is Bazel's default, so `requires-worker-protocol` is absent from the
// execution requirements in `@rules_jvm//rules/impl/content-module-packer-tool.bzl` rather than set - see the comment
// there. Every other worker in this repository is on the same default.
//
// Why a worker at all, for a binary that starts in milliseconds: the cost being amortised is not this process's startup
// but Bazel's per-spawn work, and at several thousand actions each doing about a millisecond of real work that is the
// whole build. See ../../content-module-packer/README.md for the measurements.
package worker

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
	"sync"
)

// Request is the part of one `WorkRequest` this worker acts on. The message carries three more fields and they are all
// skipped: `inputs`, which is most of its bytes and which nothing here reads (see decodeRequest); `sandbox_dir`, which
// is inert because the packing action deliberately does not declare `supports-multiplex-sandboxing`, so Bazel never
// sets it; and `verbosity`, which is non-zero only under `--worker_verbose` and which nothing consults. The field
// numbers of all six are in wire.go, so the whole schema stays legible at the point of dispatch, and restoring one is
// three lines.
type Request struct {
	Arguments []string
	RequestID int
	Cancel    bool
}

// Response is one `WorkResponse`. `Output` is what the action would have printed: Bazel shows it when the action fails,
// and a worker that prints to its own stderr instead loses the attribution to a request.
type Response struct {
	ExitCode     int
	Output       string
	RequestID    int
	WasCancelled bool
}

// Handler does one request's work. It must write everything it wants a human to read to out, and must return the exit
// code the action would have had. A cancelled context means Bazel no longer wants the result.
type Handler func(ctx context.Context, arguments []string, out io.Writer) int

// Run reads requests until stdin closes, handling each one concurrently, and returns when the stream ends.
//
// The first thing it does is take stdout away from the rest of the program. Stdout *is* the protocol: one stray
// `fmt.Println` anywhere in a dependency corrupts a response and Bazel fails the build with a parse error that names
// nothing. Pointing `os.Stdout` at stderr makes that impossible rather than a rule someone has to remember - and under
// this dialect it matters more than it did under JSON, where a stray byte was at least an invalid token. Here its first
// byte is a plausible varint length, and the framing has no resynchronisation point: it eats the next response whole.
func Run(handler Handler) error {
	responses := os.Stdout
	os.Stdout = os.Stderr
	return serve(os.Stdin, responses, handler)
}

// serve is Run with the streams named, so that a test can drive the protocol instead of the process.
func serve(in io.Reader, responses io.Writer, handler Handler) error {
	var mu sync.Mutex
	// Only ever touched under mu, so one buffer serves every response and grows to the largest output ever produced.
	var scratch []byte
	respond := func(response Response) {
		mu.Lock()
		defer mu.Unlock()
		scratch = appendResponse(scratch[:0], response)
		// One Write for the whole framed message. io.Writer offers no atomicity, and a length prefix followed by
		// another response's bytes is not a corrupt message but a corrupt *stream*: every frame after it is lost.
		// `responses` is an unbuffered *os.File, so this is one write(2) and needs no flush - and must not be wrapped
		// in a bufio.Writer, because Bazel blocks in parseDelimitedFrom on bytes that would sit in the buffer.
		if _, err := responses.Write(scratch); err != nil {
			// Nothing can be reported through a channel that is broken, and continuing would spin on the same error.
			fmt.Fprintf(os.Stderr, "ERROR: writing a work response: %v\n", err)
			os.Exit(1)
		}
	}

	var inFlight sync.WaitGroup
	var cancelMu sync.Mutex
	cancels := make(map[int]context.CancelFunc)

	reader := newRequestReader(in)
	for {
		body, err := reader.next()
		if err != nil {
			// next returns a bare io.EOF for a clean end of stream and wraps a truncated one as io.ErrUnexpectedEOF,
			// which is the difference between Bazel being finished with this worker and the stream having been cut.
			if errors.Is(err, io.EOF) {
				break
			}
			inFlight.Wait()
			return fmt.Errorf("reading a work request: %w", err)
		}
		request, err := decodeRequest(body)
		if err != nil {
			inFlight.Wait()
			return fmt.Errorf("decoding a work request: %w", err)
		}

		if request.Cancel {
			// The response for a cancelled request comes from the goroutine doing the work, so that a request which had
			// already finished still gets exactly one response. Bazel accepts either the normal one or `wasCancelled`.
			cancelMu.Lock()
			cancel := cancels[request.RequestID]
			cancelMu.Unlock()
			if cancel != nil {
				cancel()
			}
			continue
		}

		ctx, cancel := context.WithCancel(context.Background())
		cancelMu.Lock()
		cancels[request.RequestID] = cancel
		cancelMu.Unlock()

		inFlight.Add(1)
		go func(request Request, ctx context.Context, cancel context.CancelFunc) {
			defer inFlight.Done()
			defer cancel()
			var out strings.Builder
			code := handler(ctx, request.Arguments, &out)
			cancelMu.Lock()
			delete(cancels, request.RequestID)
			cancelMu.Unlock()
			if ctx.Err() != nil {
				respond(Response{RequestID: request.RequestID, WasCancelled: true})
				return
			}
			respond(Response{ExitCode: code, Output: out.String(), RequestID: request.RequestID})
		}(request, ctx, cancel)
	}

	inFlight.Wait()
	return nil
}

// IsWorkerStartup reports whether Bazel started this process as a persistent worker. The flag is passed on the command
// line rather than through the environment, and it is the only difference between the two modes.
func IsWorkerStartup(args []string) bool {
	for _, arg := range args {
		if arg == "--persistent_worker" {
			return true
		}
	}
	return false
}
