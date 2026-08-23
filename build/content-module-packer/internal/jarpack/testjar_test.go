// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"archive/zip"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"hash/crc32"
	"io"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// The fixtures here are deliberately built by two different writers.
//
// `archive/zip` is an independent implementation, so a source jar it writes is the closest thing to a real Maven jar a
// test can hold: DEFLATED data, a data descriptor with a zero CRC in the local header, a general purpose flag word that
// is not zero, a non-zero modification time and the extended-timestamp extra field it appends of its own accord. That is
// what the reader has to survive, and none of it can be expressed by hand without restating the reader's own
// assumptions. The timestamp is fixed rather than `time.Now()` precisely so that all of it lands in the fixture and
// none of it lands in the digest - which is also the packer's own claim about these fields.
//
// writeRawJar exists for the one thing `archive/zip` cannot do: give an entry a *local* extra field that differs from
// its central one. That asymmetry is the whole reason OpenJar reads a local header at all, so it needs a fixture.

// fixedModified is any time that is not the zero value: `archive/zip` writes zeros and no extra field for that one, so
// a zero here would quietly drop half of what these fixtures are for.
var fixedModified = time.Date(2026, 3, 4, 5, 6, 8, 0, time.UTC)

type sourceEntry struct {
	name   string
	data   string
	stored bool
	// extra is written to both headers by `archive/zip`; a non-empty value is there to move the data offset.
	extra []byte
}

// writeZipJar writes a jar with `archive/zip`, in the order given, which is the order the central directory lists and
// therefore the order the packer must preserve.
func writeZipJar(t *testing.T, name string, entries ...sourceEntry) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), name)
	handle, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	writer := zip.NewWriter(handle)
	for _, entry := range entries {
		header := &zip.FileHeader{Name: entry.name, Method: zip.Deflate, Extra: entry.extra, Modified: fixedModified}
		if entry.stored {
			header.Method = zip.Store
		}
		out, err := writer.CreateHeader(header)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := out.Write([]byte(entry.data)); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := handle.Close(); err != nil {
		t.Fatal(err)
	}
	return path
}

type rawEntry struct {
	name         string
	data         string
	localExtra   []byte
	centralExtra []byte
}

// writeRawJar writes a STORED-only jar by hand, so that a local extra field can differ from the central one. Real jars
// produced by other tools do that, and an entry's data begins after the *local* field.
func writeRawJar(t *testing.T, name string, entries ...rawEntry) string {
	t.Helper()
	var out []byte
	type record struct {
		entry  rawEntry
		crc    uint32
		offset uint32
	}
	records := make([]record, 0, len(entries))
	for _, entry := range entries {
		data := []byte(entry.data)
		rec := record{entry: entry, crc: crc32Of(data), offset: uint32(len(out))}
		out = appendUint32(out, 0x04034b50)
		out = append(out, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0)
		out = appendUint32(out, rec.crc)
		out = appendUint32(out, uint32(len(data)))
		out = appendUint32(out, uint32(len(data)))
		out = appendUint16(out, uint16(len(entry.name)))
		out = appendUint16(out, uint16(len(entry.localExtra)))
		out = append(out, entry.name...)
		out = append(out, entry.localExtra...)
		out = append(out, data...)
		records = append(records, rec)
	}
	centralOffset := uint32(len(out))
	for _, rec := range records {
		data := []byte(rec.entry.data)
		out = appendUint32(out, 0x02014b50)
		out = append(out, 20, 0, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0)
		out = appendUint32(out, rec.crc)
		out = appendUint32(out, uint32(len(data)))
		out = appendUint32(out, uint32(len(data)))
		out = appendUint16(out, uint16(len(rec.entry.name)))
		out = appendUint16(out, uint16(len(rec.entry.centralExtra)))
		out = append(out, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
		out = appendUint32(out, rec.offset)
		out = append(out, rec.entry.name...)
		out = append(out, rec.entry.centralExtra...)
	}
	centralLength := uint32(len(out)) - centralOffset
	out = appendUint32(out, 0x06054b50)
	out = append(out, 0, 0, 0, 0)
	out = appendUint16(out, uint16(len(records)))
	out = appendUint16(out, uint16(len(records)))
	out = appendUint32(out, centralLength)
	out = appendUint32(out, centralOffset)
	out = appendUint16(out, 0)

	path := filepath.Join(t.TempDir(), name)
	if err := os.WriteFile(path, out, 0o644); err != nil {
		t.Fatal(err)
	}
	return path
}

// pack runs a recipe and returns the packed jar's bytes. Every case verifies CRCs: a carried CRC is only sound while it
// describes the data it was carried with, and a test is where that costs nothing.
func pack(t *testing.T, spec MergeSpec) ([]byte, []string) {
	t.Helper()
	spec.Output = filepath.Join(t.TempDir(), filepath.Base(spec.Output))
	spec.VerifyCRC = true
	duplicates, err := spec.Pack()
	if err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(spec.Output)
	if err != nil {
		t.Fatal(err)
	}
	return data, duplicates
}

func crc32Of(data []byte) uint32 { return crc32.ChecksumIEEE(data) }

func digest(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}

// entryNames reads the packed jar back with `archive/zip`, so the assertion is made by an implementation that shares no
// code with the writer.
func entryNames(t *testing.T, data []byte) []string {
	t.Helper()
	reader := openPacked(t, data)
	names := make([]string, 0, len(reader.File))
	for _, file := range reader.File {
		names = append(names, file.Name)
	}
	return names
}

func openPacked(t *testing.T, data []byte) *zip.Reader {
	t.Helper()
	reader, err := zip.NewReader(bytesReaderAt(data), int64(len(data)))
	if err != nil {
		t.Fatal(err)
	}
	return reader
}

// readEntry returns one entry's uncompressed content, read back through `archive/zip`.
func readEntry(t *testing.T, data []byte, name string) string {
	t.Helper()
	for _, file := range openPacked(t, data).File {
		if file.Name != name {
			continue
		}
		handle, err := file.Open()
		if err != nil {
			t.Fatal(err)
		}
		defer handle.Close()
		content, err := io.ReadAll(handle)
		if err != nil {
			t.Fatal(err)
		}
		return string(content)
	}
	t.Fatalf("%q is not in the packed jar", name)
	return ""
}

type bytesReaderAt []byte

func (b bytesReaderAt) ReadAt(p []byte, off int64) (int, error) {
	if off >= int64(len(b)) {
		return 0, os.ErrInvalid
	}
	return copy(p, b[off:]), nil
}

// indexPointer is the offset the 5-byte end-of-central-directory comment carries, which is what the platform's reader
// seeks back from. -1 means the jar carries no index.
func indexPointer(t *testing.T, data []byte) int32 {
	t.Helper()
	if len(data) < 27 {
		t.Fatalf("%d bytes cannot hold an end record with a comment", len(data))
	}
	tail := data[len(data)-27:]
	if binary.LittleEndian.Uint32(tail) != eocdSignature {
		t.Fatalf("no end-of-central-directory record 27 bytes from the end")
	}
	if got := binary.LittleEndian.Uint16(tail[20:]); got != 5 {
		t.Fatalf("comment length is %d, want 5", got)
	}
	if got := tail[22]; got != indexFormatVersion {
		t.Fatalf("index format version is %d, want %d", got, indexFormatVersion)
	}
	return int32(binary.LittleEndian.Uint32(tail[23:]))
}
