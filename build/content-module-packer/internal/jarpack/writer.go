// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"bufio"
	"fmt"
	"hash/crc32"
	"io"
)

// Writer writes a distribution-shaped jar: every entry STORED, no directory entries, one generated `__index__` last,
// and a 5-byte end-of-central-directory comment pointing into it.
//
// It is not a general zip writer and deliberately cannot become one. The Kotlin `//zip` it reproduces carries a
// deflate path, three directory-entry modes, a memory-mapped data writer and unknown-size entries because it serves
// several callers; this serves one, so the whole layout is a pure function of (name, size, crc) per entry. Every
// header field that a general writer would fill in - version needed, general-purpose flags, modification time, extra
// fields - is a hard zero here, which is what `archive/zip` cannot be made to emit and why these headers are written
// by hand.
//
// Writing is strictly forward: offsets are known as they are reached, so there is one buffered pass and no seeking.
type Writer struct {
	w       *bufio.Writer
	offset  uint32
	entries []cdEntry
	index   *indexBuilder
	err     error
}

type cdEntry struct {
	nameBytes    []byte
	crc          uint32
	size         uint32
	headerOffset uint32
}

const (
	localHeaderSize   = 30
	centralHeaderSize = 46
	// maxEntries is where the Kotlin writer switches to zip64. It switches on the entry *count* alone and never on
	// size, so a jar over 4 GiB with few entries is silently truncated there; see Writer.Close for how that is
	// handled here instead.
	maxEntries = 65535
)

func NewWriter(out io.Writer) *Writer {
	return &Writer{w: bufio.NewWriterSize(out, 1<<20), index: newIndexBuilder()}
}

// Add appends one STORED entry.
//
// crc is taken from the caller rather than computed. That is not a shortcut: a zip CRC-32 is defined over the
// *uncompressed* data, which is exactly what is being stored, so a source jar's central-directory CRC is already the
// value this entry needs - even for a deflated source that was inflated on the way in. The Kotlin packer recomputes it
// on every entry and never even parses the source's, which is the one piece of work this design drops outright.
//
// addToPackageIndex is false only for a manifest whose Boot-Class-Path was rewritten: that entry belongs in the
// archive and in the index's entry table, but not in the package sets - the JVM reads it through the central
// directory, not through the index, and including it would be one entry of difference in every byte comparison.
func (w *Writer) Add(name string, data []byte, crc uint32, addToPackageIndex bool) error {
	if w.err != nil {
		return w.err
	}
	if len(data) > 1<<31-1 {
		return w.fail(fmt.Errorf("%s: entry is %d bytes, past what a 32-bit zip field holds", name, len(data)))
	}
	nameBytes := []byte(name)
	headerOffset := w.offset
	size := uint32(len(data))

	if err := w.writeLocalHeader(nameBytes, crc, size); err != nil {
		return err
	}
	if _, err := w.w.Write(data); err != nil {
		return w.fail(err)
	}
	if err := w.advance(uint64(size)); err != nil {
		return err
	}

	if addToPackageIndex {
		w.index.addFile(name)
	}
	dataOffset := int64(headerOffset) + localHeaderSize + int64(len(nameBytes))
	if err := w.index.add(ikvEntry{key: hashName(nameBytes), offset: dataOffset, size: int32(size)}, nameBytes); err != nil {
		return w.fail(err)
	}
	w.entries = append(w.entries, cdEntry{nameBytes: nameBytes, crc: crc, size: size, headerOffset: headerOffset})
	return nil
}

// Close writes the generated index, the central directory and the end record.
func (w *Writer) Close() error {
	if w.err != nil {
		return w.err
	}
	if err := w.index.finish(); err != nil {
		return w.fail(err)
	}

	indexDataEnd := int32(-1)
	if len(w.entries) != 0 {
		payload := w.index.payload()
		nameBytes := []byte(IndexFileName)
		headerOffset := w.offset
		// The pointer the reader seeks back from: the first byte past the entry table, inside the payload, not the
		// end of the entry.
		indexDataEnd = int32(int64(headerOffset) + localHeaderSize + int64(len(nameBytes)) + int64(w.index.entryTableSize()))

		crc := crc32.ChecksumIEEE(payload)
		if err := w.writeLocalHeader(nameBytes, crc, uint32(len(payload))); err != nil {
			return err
		}
		if _, err := w.w.Write(payload); err != nil {
			return w.fail(err)
		}
		if err := w.advance(uint64(len(payload))); err != nil {
			return err
		}
		// The index describes every entry but itself, so its own record is appended after the payload is built.
		w.entries = append(w.entries, cdEntry{
			nameBytes: nameBytes, crc: crc, size: uint32(len(payload)), headerOffset: headerOffset,
		})
	}

	if len(w.entries) >= maxEntries {
		// Rather than reproduce the Kotlin zip64 tail, refuse: no content-module jar comes within an order of
		// magnitude of this, and a silent 32-bit truncation is what the original does here.
		return w.fail(fmt.Errorf("%d entries reaches the zip64 threshold, which this writer does not implement", len(w.entries)))
	}

	centralDirectoryOffset := w.offset
	for _, e := range w.entries {
		if err := w.writeCentralHeader(e); err != nil {
			return err
		}
	}
	centralDirectoryLength := w.offset - centralDirectoryOffset

	var eocd []byte
	eocd = appendUint32(eocd, 0x06054b50)
	eocd = append(eocd, 0, 0, 0, 0) // this disk, disk of central directory start
	eocd = appendUint16(eocd, uint16(len(w.entries)))
	eocd = appendUint16(eocd, uint16(len(w.entries)))
	eocd = appendUint32(eocd, centralDirectoryLength)
	eocd = appendUint32(eocd, centralDirectoryOffset)
	// The comment is the index pointer: a length of 5, the format version, then the offset. It is written even when
	// there is no index at all, with the offset left at -1.
	eocd = appendUint16(eocd, 5)
	eocd = append(eocd, indexFormatVersion)
	eocd = appendUint32(eocd, uint32(indexDataEnd))
	if _, err := w.w.Write(eocd); err != nil {
		return w.fail(err)
	}
	return w.w.Flush()
}

// indexFormatVersion is INDEX_FORMAT_VERSION in ZipIndexWriter; the reader refuses anything else.
const indexFormatVersion = 4

func (w *Writer) writeLocalHeader(nameBytes []byte, crc, size uint32) error {
	var h []byte
	h = appendUint32(h, 0x04034b50)
	h = append(h, 0, 0) // version needed to extract
	h = append(h, 0, 0) // general purpose flags: no data descriptor, and no UTF-8 flag even though names are UTF-8
	h = append(h, 0, 0) // method: STORED
	h = append(h, 0, 0, 0, 0) // modification time and date
	h = appendUint32(h, crc)
	h = appendUint32(h, size) // compressed size, equal to the uncompressed size
	h = appendUint32(h, size)
	h = appendUint16(h, uint16(len(nameBytes)))
	h = appendUint16(h, 0) // extra field length
	h = append(h, nameBytes...)
	if _, err := w.w.Write(h); err != nil {
		return w.fail(err)
	}
	return w.advance(uint64(len(h)))
}

func (w *Writer) writeCentralHeader(e cdEntry) error {
	var h []byte
	h = appendUint32(h, 0x02014b50)
	h = append(h, 0, 0, 0, 0, 0, 0) // version made by, version needed, flags
	h = append(h, 0, 0)             // method: STORED
	h = append(h, 0, 0, 0, 0)       // modification time and date
	h = appendUint32(h, e.crc)
	h = appendUint32(h, e.size) // compressed size
	h = appendUint32(h, e.size)
	h = appendUint16(h, uint16(len(e.nameBytes)))
	// extra length, comment length, disk number, internal attributes, external attributes - all zero, so no unix
	// mode bits reach the archive.
	h = append(h, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
	h = appendUint32(h, e.headerOffset)
	h = append(h, e.nameBytes...)
	if _, err := w.w.Write(h); err != nil {
		return w.fail(err)
	}
	return w.advance(uint64(len(h)))
}

// advance moves the write cursor, refusing to wrap the 32-bit offsets every header field here holds. The Kotlin
// writer has no such guard and would corrupt the archive silently.
func (w *Writer) advance(n uint64) error {
	next := uint64(w.offset) + n
	if next > 1<<32-1 {
		return w.fail(fmt.Errorf("output would exceed 4 GiB, past what a 32-bit zip offset holds"))
	}
	w.offset = uint32(next)
	return nil
}

func (w *Writer) fail(err error) error {
	if w.err == nil {
		w.err = err
	}
	return w.err
}
