// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package worker

import (
	"bytes"
	"encoding/hex"
	"errors"
	"io"
	"reflect"
	"strings"
	"testing"
)

// The vectors in this file are written out byte by byte from the schema in wire.go rather than produced by the codec
// they test. That is the point: a round-trip test - encode with appendResponse, decode with decodeRequest - passes with
// two cancelling bugs, and two cancelling bugs are the normal failure mode of a hand-written varint. It is also how the
// rest of this tree holds a byte format: internal/xxh3 against 2 050 external reference vectors, internal/jarpack
// against frozen digests.
//
// What no vector here can prove is that Bazel encodes what we think it does. That is TestRealBazelRequestGolden, and the
// real build in the README's verification list.

// digest32 stands in for the raw 32-byte SHA-256 Bazel puts in an Input. proto `bytes`, not the 44-char base64 the JSON
// dialect delivered.
var digest32 = bytes.Repeat([]byte{0xAB}, 32)

func TestDecodeRequestVectors(t *testing.T) {
	// inputRecord is one `inputs` element: path "a/b" then a 32-byte digest. 5 + 34 = 39 bytes of sub-message.
	inputRecord := func() []byte {
		record := []byte{
			0x0A, 0x03, 'a', '/', 'b', // Input field 1 (path), wire 2, len 3
			0x12, 0x20, //                Input field 2 (digest), wire 2, len 32
		}
		return append(record, digest32...)
	}()

	tests := []struct {
		name string
		body []byte
		want Request
	}{
		{
			// The singleplex shape: request_id is absent, not sent as 0.
			name: "arguments only",
			body: append([]byte{0x0A, 0x0C}, "--flagfile=x"...),
			want: Request{Arguments: []string{"--flagfile=x"}},
		},
		{
			name: "arguments and request id",
			body: []byte{
				0x0A, 0x01, 'a', // field 1 (arguments), wire 2, len 1
				0x18, 0x07, //      field 3 (request_id), wire 0, value 7
			},
			want: Request{Arguments: []string{"a"}, RequestID: 7},
		},
		{
			// The captured request in TestRealBazelRequestGolden carries id 1500, so the two-byte form is the normal case
			// in a real build and the single-byte vectors above are the unusual one.
			name: "a request id past 127 takes two varint bytes",
			body: []byte{0x0A, 0x01, 'a', 0x18, 0xDC, 0x0B}, // 0x5C + (0x0B << 7) = 1500
			want: Request{Arguments: []string{"a"}, RequestID: 1500},
		},
		{
			name: "cancel",
			body: []byte{
				0x18, 0x07, // field 3 (request_id), wire 0, value 7
				0x20, 0x01, // field 4 (cancel), wire 0, true
			},
			want: Request{RequestID: 7, Cancel: true},
		},
		{
			// The vector that earns its keep: it proves the skip over `inputs` advances by exactly the record's length.
			// Off by one either way and the request_id behind it is misread or the parse fails outright.
			name: "inputs are skipped, and request id behind them survives",
			body: append(append([]byte{0x12, 0x27}, inputRecord...), 0x18, 0x07),
			want: Request{RequestID: 7},
		},
		{
			name: "two inputs are skipped",
			body: append(append(append(append([]byte{0x12, 0x27}, inputRecord...), 0x12, 0x27), inputRecord...), 0x18, 0x09),
			want: Request{RequestID: 9},
		},
		{
			name: "verbosity and sandboxDir are skipped",
			body: []byte{
				0x0A, 0x01, 'a', //      field 1 (arguments)
				0x28, 0x0A, //           field 5 (verbosity), wire 0, value 10
				0x32, 0x03, 's', '/', 'd', // field 6 (sandbox_dir), wire 2, len 3
				0x18, 0x07, //           field 3 (request_id)
			},
			want: Request{Arguments: []string{"a"}, RequestID: 7},
		},
		{
			// repeated, so it accumulates in order. Only one argument is ever sent today - the recipe always goes through
			// a param file - but the field is repeated and a change to that would send more.
			name: "two arguments accumulate in order",
			body: []byte{0x0A, 0x01, 'a', 0x0A, 0x01, 'b'},
			want: Request{Arguments: []string{"a", "b"}},
		},
		{
			// A known number arriving with the wrong wire type is not ours to interpret, so it takes the unknown-field
			// route. request_id stays zero rather than being read out of a length-delimited record.
			name: "a known field with the wrong wire type is skipped",
			body: []byte{
				0x1A, 0x01, 0x05, // field 3 (request_id) but wire 2
				0x0A, 0x01, 'a',
			},
			want: Request{Arguments: []string{"a"}},
		},
		{
			name: "empty body is a request of all defaults",
			body: []byte{},
			want: Request{},
		},
		// Forward compatibility, one vector per wire type. `sandbox_dir` was added to this message after `verbosity`, so
		// this is the rule that keeps a seventh field from breaking the build rather than a courtesy.
		{
			name: "unknown field 7, wire type 0",
			body: []byte{0x38, 0x96, 0x01, 0x18, 0x07}, // 7<<3|0 = 0x38, varint 150
			want: Request{RequestID: 7},
		},
		{
			name: "unknown field 7, wire type 1",
			body: []byte{0x39, 1, 2, 3, 4, 5, 6, 7, 8, 0x18, 0x07}, // 7<<3|1 = 0x39, eight fixed bytes
			want: Request{RequestID: 7},
		},
		{
			name: "unknown field 7, wire type 2",
			body: []byte{0x3A, 0x02, 'x', 'y', 0x18, 0x07}, // 7<<3|2 = 0x3A
			want: Request{RequestID: 7},
		},
		{
			name: "unknown field 7, wire type 5",
			body: []byte{0x3D, 1, 2, 3, 4, 0x18, 0x07}, // 7<<3|5 = 0x3D, four fixed bytes
			want: Request{RequestID: 7},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			got, err := decodeRequest(test.body)
			if err != nil {
				t.Fatalf("decoding % x: %v", test.body, err)
			}
			if !reflect.DeepEqual(got, test.want) {
				t.Errorf("decoded % x as %+v, want %+v", test.body, got, test.want)
			}
		})
	}
}

func TestDecodeRequestRejectsMalformedBodies(t *testing.T) {
	tests := []struct {
		name string
		body []byte
	}{
		{"a tag with no value", []byte{0x0A}},
		{"a record running past the end", []byte{0x0A, 0x05, 'a'}},
		{"a varint that never terminates", []byte{0x18, 0xFF}},
		{"a varint past ten bytes", []byte{0x18, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01}},
		{"field number zero", []byte{0x00, 0x01}},
		{"wire type 3, a group start that cannot occur in proto3", []byte{0x0B, 0x01}},
		{"wire type 4", []byte{0x0C, 0x01}},
		{"an input record running past the end", []byte{0x12, 0x7F, 0x0A, 0x01, 'a'}},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			// The requirement is an error and not a panic: these bytes arrive from a pipe, and a panic in a worker takes
			// the build's error message with it.
			got, err := decodeRequest(test.body)
			if err == nil {
				t.Fatalf("decoding % x returned %+v, want an error", test.body, got)
			}
		})
	}
}

func TestRequestReaderFraming(t *testing.T) {
	t.Run("two messages back to back", func(t *testing.T) {
		// The reader keeps one buffer across requests, so a second message that fits in the first one's space is the
		// case where a stale length would go unnoticed.
		stream := []byte{
			0x03, 0x0A, 0x01, 'a', // 3 bytes: arguments ["a"]
			0x02, 0x18, 0x07, //      2 bytes: request_id 7
		}
		reader := newRequestReader(bytes.NewReader(stream))

		first, err := reader.next()
		if err != nil {
			t.Fatal(err)
		}
		if got, want := string(first), string([]byte{0x0A, 0x01, 'a'}); got != want {
			t.Errorf("first body is % x, want % x", got, want)
		}
		second, err := reader.next()
		if err != nil {
			t.Fatal(err)
		}
		if got, want := string(second), string([]byte{0x18, 0x07}); got != want {
			t.Errorf("second body is % x, want % x", got, want)
		}
		if _, err := reader.next(); !errors.Is(err, io.EOF) {
			t.Errorf("after the last message the error is %v, want io.EOF", err)
		}
	})

	t.Run("an empty stream is a clean end", func(t *testing.T) {
		// This and the truncation cases below being *different* errors is the whole reason the first prefix byte is read
		// on its own: serve breaks its loop on io.EOF and reports anything else as a failure.
		_, err := newRequestReader(bytes.NewReader(nil)).next()
		if !errors.Is(err, io.EOF) {
			t.Fatalf("error is %v, want io.EOF", err)
		}
		if errors.Is(err, io.ErrUnexpectedEOF) {
			t.Error("a clean end of stream was reported as a truncation")
		}
	})

	t.Run("a stream ending mid-varint is a truncation", func(t *testing.T) {
		_, err := newRequestReader(bytes.NewReader([]byte{0xFF})).next()
		if !errors.Is(err, io.ErrUnexpectedEOF) {
			t.Fatalf("error is %v, want io.ErrUnexpectedEOF", err)
		}
		if errors.Is(err, io.EOF) {
			t.Error("a truncated length prefix was reported as a clean end of stream")
		}
	})

	t.Run("a body shorter than its prefix promises is a truncation", func(t *testing.T) {
		_, err := newRequestReader(bytes.NewReader([]byte{0x05, 0x01, 0x02})).next()
		if !errors.Is(err, io.ErrUnexpectedEOF) {
			t.Fatalf("error is %v, want io.ErrUnexpectedEOF", err)
		}
	})

	t.Run("a prefix past the sanity bound names itself", func(t *testing.T) {
		// Without the bound this is a 4 GB allocation. With it, it is an error that says the stream is out of step,
		// which is the only useful thing to say once a bare-varint framing has lost sync.
		_, err := newRequestReader(bytes.NewReader([]byte{0xFF, 0xFF, 0xFF, 0xFF, 0x0F})).next()
		if err == nil {
			t.Fatal("a 4 GB length prefix was accepted")
		}
		if !strings.Contains(err.Error(), "out of step") {
			t.Errorf("error is %q, which does not say the stream is out of step", err)
		}
	})
}

func TestAppendResponseVectors(t *testing.T) {
	longOutput := strings.Repeat("x", 200)

	tests := []struct {
		name     string
		response Response
		want     []byte
	}{
		{
			// Exit code 0 and an empty output are omitted; request_id is not.
			name:     "success",
			response: Response{RequestID: 7},
			want:     []byte{0x02, 0x18, 0x07},
		},
		{
			// The singleplex answer. Canonical proto3 would omit a zero request_id entirely; Bazel routes on it, so it
			// is written unconditionally and the message is never empty.
			name:     "request id zero is still written",
			response: Response{RequestID: 0},
			want:     []byte{0x02, 0x18, 0x00},
		},
		{
			// The leading 0x08 is the length; the second is field 1's tag. A coincidence, not a repetition.
			name:     "exit code, output and id",
			response: Response{ExitCode: 3, Output: "hi", RequestID: 7},
			want:     []byte{0x08, 0x08, 0x03, 0x12, 0x02, 'h', 'i', 0x18, 0x07},
		},
		{
			// The vector that stops the bug this codec is most likely to have: proto sign-extends int32 to 64 bits, so
			// -1 is ten bytes. A naive encoder writes one, Bazel reads a length prefix out of the next field, and the
			// build fails with a parse error naming nothing.
			name:     "a negative exit code sign-extends to ten bytes",
			response: Response{ExitCode: -1, RequestID: 7},
			want: []byte{
				0x0D,                                                             // 13 bytes follow
				0x08, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01, // field 1 = -1
				0x18, 0x07,
			},
		},
		{
			// The id Bazel routes on is two bytes for most of a real build, and it has to come back exactly as it went
			// out or the response is credited to another action.
			name:     "a request id past 127 comes back in two bytes",
			response: Response{RequestID: 1500},
			want:     []byte{0x03, 0x18, 0xDC, 0x0B},
		},
		{
			name:     "cancelled",
			response: Response{RequestID: 7, WasCancelled: true},
			want:     []byte{0x04, 0x18, 0x07, 0x20, 0x01},
		},
		{
			// The length is in bytes. A rune count would make this three where it is four, and the stream would desync.
			name:     "output length counts bytes, not runes",
			response: Response{Output: "é\x00z", RequestID: 7},
			want:     []byte{0x08, 0x12, 0x04, 0xC3, 0xA9, 0x00, 'z', 0x18, 0x07},
		},
		{
			// Both the field length and the frame length cross 127 here, so both are two-byte varints: 200 = 0xC8 0x01,
			// 205 = 0xCD 0x01. The median response never reaches this path; a duplicate-entry report does.
			name:     "an output past 127 bytes takes a two-byte length",
			response: Response{Output: longOutput, RequestID: 7},
			want:     append(append([]byte{0xCD, 0x01, 0x12, 0xC8, 0x01}, longOutput...), 0x18, 0x07),
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			got := appendResponse(nil, test.response)
			if !bytes.Equal(got, test.want) {
				t.Errorf("encoded %+v as\n % x\nwant\n % x", test.response, got, test.want)
			}
			// The length prefix has to describe the body, or Bazel reads the next response's bytes as this one's.
			if len(got) == 0 {
				t.Fatal("no bytes at all")
			}
			size, rest, err := consumeVarint(got)
			if err != nil {
				t.Fatalf("its own length prefix does not parse: %v", err)
			}
			if uint64(len(rest)) != size {
				t.Errorf("the prefix promises %d bytes and %d follow", size, len(rest))
			}
		})
	}
}

func TestAppendResponseReusesTheBuffer(t *testing.T) {
	// serve keeps one scratch buffer for the process's life and truncates it per response. A leftover byte from a longer
	// previous response would corrupt the stream rather than the message.
	scratch := appendResponse(nil, Response{ExitCode: 3, Output: strings.Repeat("y", 300), RequestID: 11})
	scratch = appendResponse(scratch[:0], Response{RequestID: 7})
	if want := []byte{0x02, 0x18, 0x07}; !bytes.Equal(scratch, want) {
		t.Errorf("second response is % x, want % x", scratch, want)
	}
}

// realBazelRequest is one framed WorkRequest as Bazel actually sent it, captured from a live packing action.
//
//	target      @community//platform/util:util-ui, --output_groups=content_module_jar
//	bazel       JetBrains/9.1.0-jb_20260505_126, darwin arm64, multiplex worker
//	captured    2026-08-23, with a temporary io.TeeReader on the worker's stdin
//
// 628 bytes: a two-byte length prefix over a 626-byte body holding one 111-byte `--flagfile=` argument, three `inputs`
// records of 141, 192 and 168 bytes, and request_id 1500. Every one of those lengths and the id itself is a two-byte
// varint, which is what makes this vector worth more than the hand-written ones: the median shape a real build sends is
// nowhere near the single-byte cases that are easy to write out by hand.
//
// Two things it pins that nothing else can. Bazel's encoder, rather than our reading of the schema - the hand-written
// vectors above cannot tell the two apart. And that `digest` is 64 bytes of lowercase ASCII hex here, not the 32 raw
// bytes proto `bytes` invites you to assume; nothing reads it, but the comment in wire.go would otherwise be wrong.
const realBazelRequest = "" +
	"f2040a6f2d2d666c616766696c653d62617a656c2d6f75742f6a766d2d666173746275696c642f62696e2f6578746572" +
	"6e616c2f636f6d6d756e6974792b2f706c6174666f726d2f7574696c2f696e74656c6c696a2e706c6174666f726d2e75" +
	"74696c2e75692e6a61722d302e706172616d73128d010a4962617a656c2d6f75742f6a766d2d666173746275696c642f" +
	"62696e2f65787465726e616c2f636f6d6d756e6974792b2f706c6174666f726d2f7574696c2f7574696c2d75692e6a61" +
	"721240383130616532333364373636643832396139326165323533303434363765636661316232393965393932313362" +
	"3465653536316234663530646666303339396512c0010a7c62617a656c2d6f75742f64617277696e5f61726d36342d6f" +
	"70742d657865632d53542d3263356166373437653166382f62696e2f6275696c642f636f6e74656e742d6d6f64756c65" +
	"2d7061636b65722f636f6e74656e742d6d6f64756c652d7061636b65725f2f636f6e74656e742d6d6f64756c652d7061" +
	"636b65721240393137383861363331623135626235323431366365313332636364383437393838666563626463363962" +
	"3734313035333132316133633138383837353037653212a8010a6462617a656c2d6f75742f6a766d2d66617374627569" +
	"6c642f62696e2f65787465726e616c2f636f6d6d756e6974792b2f706c6174666f726d2f7574696c2f696e74656c6c69" +
	"6a2e706c6174666f726d2e7574696c2e75692e6a61722d302e706172616d731240376562363665363434633462303930" +
	"663937633136373135633032396364303164623838336164616334303962636665353539663737646136633237366134" +
	"3818dc0b"

func decodeRealBazelRequest(tb testing.TB) Request {
	tb.Helper()
	framed, err := hex.DecodeString(realBazelRequest)
	if err != nil {
		tb.Fatal(err)
	}
	body, err := newRequestReader(bytes.NewReader(framed)).next()
	if err != nil {
		tb.Fatal(err)
	}
	request, err := decodeRequest(body)
	if err != nil {
		tb.Fatal(err)
	}
	return request
}

func TestRealBazelRequestGolden(t *testing.T) {
	request := decodeRealBazelRequest(t)

	if len(request.Arguments) != 1 {
		t.Fatalf("%d arguments, want exactly 1: the recipe always goes through a param file", len(request.Arguments))
	}
	if !strings.HasPrefix(request.Arguments[0], "--flagfile=") {
		t.Errorf("the argument is %q, want a --flagfile=", request.Arguments[0])
	}
	if !strings.HasSuffix(request.Arguments[0], ".params") {
		t.Errorf("the argument is %q, want it to name a param file", request.Arguments[0])
	}
	if got, want := request.RequestID, 1500; got != want {
		t.Errorf("request id is %d, want %d", got, want)
	}
	if request.Cancel {
		t.Error("a packing request was read as a cancellation")
	}

	// The inputs are 501 of the 626 body bytes and not one of them is materialised. If a future change starts reading
	// them, this is the number that says how much it costs.
	framed, err := hex.DecodeString(realBazelRequest)
	if err != nil {
		t.Fatal(err)
	}
	if skipped := len(framed) - len(request.Arguments[0]); skipped < len(framed)/2 {
		t.Errorf("only %d of %d bytes were skipped, so this vector no longer has the shape it is here for", skipped, len(framed))
	}
}

func BenchmarkDecodeRequest(b *testing.B) {
	// The only honest instrument for the Go side of this change: --cpuprofile is refused in worker mode and the one-shot
	// path never enters this package, so a profile of a real build cannot see the decoder at all.
	framed, err := hex.DecodeString(realBazelRequest)
	if err != nil {
		b.Fatal(err)
	}
	body := framed[2:] // past the length prefix; requestReader's framing is measured by the test, not here
	b.SetBytes(int64(len(body)))
	b.ReportAllocs()
	for b.Loop() {
		if _, err := decodeRequest(body); err != nil {
			b.Fatal(err)
		}
	}
}
