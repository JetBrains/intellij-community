// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package worker

import (
	"bufio"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
)

// The schema, from
// https://github.com/bazelbuild/bazel/blob/master/src/main/protobuf/worker_protocol.proto - the same message
// ../../../community/build/jvm-rules/worker-framework/protocol.kt decodes by hand on the JVM side, and the same field
// numbers it hard-codes.
//
// Every number here is below 16, so every tag fits in one byte: `tag` relies on that and would silently truncate if a
// field were ever added past 15.
const (
	requestArguments  = 1 // repeated string
	requestInputs     = 2 // repeated Input - skipped, see decodeRequest
	requestRequestID  = 3 // int32
	requestCancel     = 4 // bool
	requestVerbosity  = 5 // int32
	requestSandboxDir = 6 // string

	responseExitCode     = 1 // int32
	responseOutput       = 2 // string
	responseRequestID    = 3 // int32
	responseWasCancelled = 4 // bool
)

// Wire types. 3 and 4 - start and end group - cannot appear in a proto3 schema, so they are an error rather than
// something to skip.
const (
	wireVarint  = 0
	wireFixed64 = 1
	wireBytes   = 2
	wireFixed32 = 5
)

// maxMessageSize is a sanity bound, not a protocol limit. The framing is a bare varint length, so one desynchronised
// byte turns the next prefix into an arbitrary number; without this, that byte becomes a multi-gigabyte allocation
// instead of an error that names the bytes which caused it.
const maxMessageSize = 64 << 20

// tag is a field's key byte: the number in the high bits, the wire type in the low three. Correct only while every
// field number is below 16, which is asserted by the constants above being what they are.
func tag(number, wire int) byte {
	return byte(number<<3 | wire)
}

// requestReader frames the request stream. There is exactly one of these and exactly one goroutine reading it - the
// serve loop - so the scratch buffer needs no lock.
type requestReader struct {
	in  *bufio.Reader
	buf []byte
}

// newRequestReader wraps in. The buffered reader is left at its default size deliberately: Bazel flushes after every
// request, so a read is bounded by when the next message arrives rather than by how much buffer is offered, and a
// larger one would not reduce the syscall count.
func newRequestReader(in io.Reader) *requestReader {
	return &requestReader{in: bufio.NewReader(in)}
}

// next reads one length-delimited WorkRequest and returns its body, which stays valid only until the following call.
//
// It returns io.EOF for a clean end of stream and nothing else: the first byte of the length prefix is read on its own
// so that "Bazel closed stdin" and "the stream stopped mid-message" are different errors, which is the same reason
// protocol.kt reads its first byte with input.read() before readRawVarint32.
func (r *requestReader) next() ([]byte, error) {
	first, err := r.in.ReadByte()
	if err != nil {
		// io.EOF here, and only here, means Bazel is done with this worker.
		return nil, err
	}
	if err := r.in.UnreadByte(); err != nil {
		return nil, err
	}

	size, err := binary.ReadUvarint(r.in)
	if err != nil {
		if errors.Is(err, io.EOF) {
			err = io.ErrUnexpectedEOF
		}
		return nil, fmt.Errorf("reading a work request length prefix (first byte %#02x): %w", first, err)
	}
	if size > maxMessageSize {
		return nil, fmt.Errorf("a work request claims %d bytes, past the %d-byte sanity bound: the stream is out of step, "+
			"and its first prefix byte was %#02x", size, maxMessageSize, first)
	}

	if uint64(cap(r.buf)) < size {
		r.buf = make([]byte, size)
	}
	body := r.buf[:size]
	if _, err := io.ReadFull(r.in, body); err != nil {
		if errors.Is(err, io.EOF) {
			err = io.ErrUnexpectedEOF
		}
		return nil, fmt.Errorf("reading a %d-byte work request: %w", size, err)
	}
	return body, nil
}

// decodeRequest reads the three fields serve acts on and steps over everything else.
//
// A field whose wire type is not the one the schema gives it is not ours to interpret, so it takes the same route as an
// unknown number: skipped. That route is not politeness. `sandbox_dir` was added to this message after `verbosity`, so a
// decoder that rejected an unrecognised number would break on the seventh field Bazel adds.
func decodeRequest(body []byte) (Request, error) {
	var request Request
	for len(body) != 0 {
		number, wire, rest, err := consumeTag(body)
		if err != nil {
			return Request{}, err
		}
		body = rest

		switch {
		case number == requestArguments && wire == wireBytes:
			value, rest, err := consumeBytes(body)
			if err != nil {
				return Request{}, fmt.Errorf("field %d (arguments): %w", number, err)
			}
			// string(value) copies, and that copy is what licenses the single reusable buffer in requestReader: nothing
			// in the returned Request aliases it, so the next request may be read while a handler still holds these.
			// unsafe.String over value would avoid the copy and mutate a running handler's arguments. Do not.
			request.Arguments = append(request.Arguments, string(value))
			body = rest

		case number == requestRequestID && wire == wireVarint:
			value, rest, err := consumeVarint(body)
			if err != nil {
				return Request{}, fmt.Errorf("field %d (requestId): %w", number, err)
			}
			request.RequestID = int(int32(value))
			body = rest

		case number == requestCancel && wire == wireVarint:
			value, rest, err := consumeVarint(body)
			if err != nil {
				return Request{}, fmt.Errorf("field %d (cancel): %w", number, err)
			}
			request.Cancel = value != 0
			body = rest

		case number == requestInputs:
			// Skipped, and it is most of the message: Bazel names every declared input of the action here with the
			// digest it computed for it. Nothing in this worker caches derived state across requests, so nothing wants
			// them - and skipping is one varint and a reslice per input where the JSON dialect had to lex every byte.
			//
			// Reading them would take an Input struct of {Path string; Digest []byte}, a nested field loop over the
			// sub-message bounded by this record's length - buf[:n] being the Go analogue of pushLimit/popLimit - and the
			// knowledge that `digest` is proto `bytes` and this Bazel fills it with 64 bytes of lowercase ASCII hex, not
			// the 32 raw bytes the field's type invites you to assume. Confirmed against a captured request in
			// wire_test.go; under the JSON dialect those 64 bytes arrived base64-encoded to 88 characters.
			fallthrough

		default:
			rest, err := skipField(body, wire)
			if err != nil {
				return Request{}, fmt.Errorf("skipping field %d: %w", number, err)
			}
			body = rest
		}
	}
	return request, nil
}

// consumeTag reads one field key.
func consumeTag(b []byte) (number int, wire int, rest []byte, err error) {
	value, rest, err := consumeVarint(b)
	if err != nil {
		return 0, 0, nil, fmt.Errorf("reading a field tag: %w", err)
	}
	number = int(value >> 3)
	if number == 0 {
		return 0, 0, nil, errors.New("reading a field tag: field number 0 is not a valid field")
	}
	return number, int(value & 7), rest, nil
}

// consumeVarint reads a base-128 varint, least significant group first.
func consumeVarint(b []byte) (uint64, []byte, error) {
	var value uint64
	// Ten groups of seven bits is 70, so the tenth byte carries the 64th bit and nothing may follow it.
	for shift := 0; shift < 70; shift += 7 {
		if len(b) == 0 {
			return 0, nil, io.ErrUnexpectedEOF
		}
		octet := b[0]
		b = b[1:]
		value |= uint64(octet&0x7F) << shift
		if octet < 0x80 {
			return value, b, nil
		}
	}
	return 0, nil, errors.New("a varint ran past ten bytes")
}

// consumeBytes reads a length-delimited record and returns it as a window into b, not a copy.
func consumeBytes(b []byte) (value []byte, rest []byte, err error) {
	size, rest, err := consumeVarint(b)
	if err != nil {
		return nil, nil, err
	}
	if size > uint64(len(rest)) {
		return nil, nil, fmt.Errorf("a %d-byte record runs past the %d bytes that remain", size, len(rest))
	}
	return rest[:size], rest[size:], nil
}

// skipField advances over one field's value, whose length is a function of the wire type alone - which is the whole
// reason an unknown field can be skipped at all.
func skipField(b []byte, wire int) ([]byte, error) {
	switch wire {
	case wireVarint:
		_, rest, err := consumeVarint(b)
		return rest, err
	case wireFixed64:
		if len(b) < 8 {
			return nil, io.ErrUnexpectedEOF
		}
		return b[8:], nil
	case wireBytes:
		_, rest, err := consumeBytes(b)
		return rest, err
	case wireFixed32:
		if len(b) < 4 {
			return nil, io.ErrUnexpectedEOF
		}
		return b[4:], nil
	default:
		return nil, fmt.Errorf("wire type %d has no length this decoder can determine; 3 and 4 cannot occur in a proto3 message", wire)
	}
}

// appendResponse frames one WorkResponse onto dst. The sizes are computed first so that the length prefix is written
// once and dst grows once - the shape WorkRequestHandler.kt uses on the JVM side, for the same reason.
//
// WorkRequestHandler.kt's pre-allocated emergency buffer has no analogue here on purpose: it exists because a JVM
// OutOfMemoryError is catchable and you can still want to answer a request, where a Go allocation failure is a fatal
// runtime error and there is nothing left to answer with.
func appendResponse(dst []byte, response Response) []byte {
	size := 0
	if response.ExitCode != 0 {
		size += 1 + varintSize(int32ToVarint(response.ExitCode))
	}
	if response.Output != "" {
		size += 1 + varintSize(uint64(len(response.Output))) + len(response.Output)
	}
	// requestId is written even when it is zero. Canonical proto3 would omit it, but Bazel routes on it and protocol.kt
	// writes it unconditionally; two unconditional bytes are cheaper than the question.
	size += 1 + varintSize(int32ToVarint(response.RequestID))
	if response.WasCancelled {
		size += 2
	}

	dst = appendVarint(dst, uint64(size))
	if response.ExitCode != 0 {
		dst = appendVarint(append(dst, tag(responseExitCode, wireVarint)), int32ToVarint(response.ExitCode))
	}
	if response.Output != "" {
		dst = appendVarint(append(dst, tag(responseOutput, wireBytes)), uint64(len(response.Output)))
		dst = append(dst, response.Output...)
	}
	dst = appendVarint(append(dst, tag(responseRequestID, wireVarint)), int32ToVarint(response.RequestID))
	if response.WasCancelled {
		dst = append(dst, tag(responseWasCancelled, wireVarint), 1)
	}
	return dst
}

// int32ToVarint is where a hand-written encoder goes wrong. proto encodes int32 by sign-extending it to 64 bits, so -1
// is ten bytes and not one. The exit codes this worker produces are 0 to 3, but Handler returns an int and the wire
// format does not get to depend on that.
func int32ToVarint(v int) uint64 {
	return uint64(int64(int32(v)))
}

func appendVarint(dst []byte, v uint64) []byte {
	for v >= 0x80 {
		dst = append(dst, byte(v)|0x80)
		v >>= 7
	}
	return append(dst, byte(v))
}

func varintSize(v uint64) int {
	n := 1
	for v >= 0x80 {
		v >>= 7
		n++
	}
	return n
}
