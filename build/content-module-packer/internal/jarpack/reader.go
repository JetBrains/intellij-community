// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"bytes"
	"compress/flate"
	"encoding/binary"
	"fmt"
	"io"
	"unicode/utf8"
	"unsafe"

	"jetbrains.com/content-module-packer/internal/mmapfile"
)

// Entry is one central-directory record of a source jar, in the order the directory lists it.
//
// Name points into the source jar's mapping rather than into the heap, so it is valid only until the Jar is closed.
// Everything reading it - the filters, the index's package hashes - reads it during the merge, and the writer copies the
// names it keeps.
type Entry struct {
	Name     string
	CRC      uint32
	Size     uint32 // uncompressed
	compSize uint32
	method   uint16
	dataAt   int64
}

// Jar is a source jar opened for reading, holding its central directory.
//
// Hand-rolled rather than `archive/zip` for two reasons the index depends on. The IKV key is the hash of an entry's
// *raw name bytes*, which `archive/zip` does not expose - it hands back a decoded string after applying its own name
// handling. And the data offset has to come from the *local* header's extra-field length rather than the central
// directory's, because the two legitimately differ in jars produced by other tools.
//
// The jar is mapped rather than read entry by entry, which is what makes that second reason cheap: reading a local
// header is a memory read instead of a `pread` for two useful bytes. See internal/mmapfile for the measurement.
type Jar struct {
	file    *mmapfile.File
	data    []byte
	Entries []Entry
}

const (
	eocdSignature   = 0x06054b50
	cdSignature     = 0x02014b50
	methodStored    = 0
	methodDeflated  = 8
	maxLocalExtra   = 128
	eocdMinSize     = 22
	maxCommentBytes = 1 << 16
)

// OpenJar reads a jar's central directory, dropping the entries a distribution jar never inherits: directory records,
// and any `__index__` the source carried, since a fresh one is generated for the output.
func OpenJar(path string) (*Jar, error) {
	file, err := mmapfile.Open(path)
	if err != nil {
		return nil, err
	}
	jar, err := parseJar(path, file)
	if err != nil {
		file.Close()
		return nil, err
	}
	return jar, nil
}

func parseJar(path string, file *mmapfile.File) (*Jar, error) {
	data := file.Data
	size := int64(len(data))
	if size < eocdMinSize {
		return nil, fmt.Errorf("%s: %d bytes is too small to be a zip", path, size)
	}

	// Scan back for the end record. Our own jars carry a 5-byte comment, so the signature is never at the very end.
	tailLen := int64(eocdMinSize + maxCommentBytes)
	if tailLen > size {
		tailLen = size
	}
	tail := data[size-tailLen:]
	eocd := -1
	for i := len(tail) - eocdMinSize; i >= 0; i-- {
		if binary.LittleEndian.Uint32(tail[i:]) == eocdSignature {
			eocd = i
			break
		}
	}
	if eocd == -1 {
		return nil, fmt.Errorf("%s: no end-of-central-directory record", path)
	}
	count := int(binary.LittleEndian.Uint16(tail[eocd+10:]))
	cdLen := int64(binary.LittleEndian.Uint32(tail[eocd+12:]))
	cdOff := int64(binary.LittleEndian.Uint32(tail[eocd+16:]))
	if cdOff == 0xffffffff || count == 0xffff {
		return nil, fmt.Errorf("%s: zip64 source jars are not supported", path)
	}
	if cdOff < 0 || cdLen < 0 || cdOff+cdLen > size {
		return nil, fmt.Errorf("%s: the central directory at %d is %d bytes, past the end of a %d-byte file",
			path, cdOff, cdLen, size)
	}
	cd := data[cdOff : cdOff+cdLen]

	jar := &Jar{file: file, data: data, Entries: make([]Entry, 0, count)}
	for p := 0; p+centralHeaderSize <= len(cd); {
		if binary.LittleEndian.Uint32(cd[p:]) != cdSignature {
			return nil, fmt.Errorf("%s: bad central directory record at %d", path, p)
		}
		method := binary.LittleEndian.Uint16(cd[p+10:])
		crc := binary.LittleEndian.Uint32(cd[p+16:])
		compSize := binary.LittleEndian.Uint32(cd[p+20:])
		entrySize := binary.LittleEndian.Uint32(cd[p+24:])
		nameLen := int(binary.LittleEndian.Uint16(cd[p+28:]))
		extraLen := int(binary.LittleEndian.Uint16(cd[p+30:]))
		commentLen := int(binary.LittleEndian.Uint16(cd[p+32:]))
		headerOffset := int64(binary.LittleEndian.Uint32(cd[p+42:]))
		end := p + centralHeaderSize + nameLen + extraLen + commentLen
		if end > len(cd) {
			// A truncated or lying central directory is a corrupt input, not a bug here, and it must be reported as the
			// file it is rather than as a slice-bounds panic in a build log.
			return nil, fmt.Errorf("%s: central directory record at %d runs %d bytes past its end", path, p, end-len(cd))
		}
		nameBytes := cd[p+centralHeaderSize : p+centralHeaderSize+nameLen]
		p = end

		// A string over the mapping rather than a copy: this is read by the filters and hashed for the index, and the
		// writer copies the ones it keeps. `unsafe.String` is sound here because the mapping is read-only for its whole
		// life and the Entry cannot outlive it.
		name := unsafeString(nameBytes)
		if len(name) == 0 || name[len(name)-1] == '/' || name == IndexFileName {
			continue
		}
		// A name that is not valid UTF-8 would be decoded to U+FFFD by the Kotlin reader while Go keeps the raw
		// bytes, so the two would hash the same entry differently. Refuse rather than diverge silently.
		if !utf8.Valid(nameBytes) {
			return nil, fmt.Errorf("%s: entry name is not valid UTF-8: %q", path, nameBytes)
		}

		if headerOffset < 0 || headerOffset+localHeaderSize > size {
			return nil, fmt.Errorf("%s: %s: local header at %d is past the end of a %d-byte file",
				path, name, headerOffset, size)
		}
		localExtra := int(binary.LittleEndian.Uint16(data[headerOffset+28:]))
		if localExtra > maxLocalExtra {
			return nil, fmt.Errorf("%s: %s: local extra field is %d bytes", path, name, localExtra)
		}

		dataAt := headerOffset + localHeaderSize + int64(nameLen) + int64(localExtra)
		if dataAt < 0 || dataAt+int64(compSize) > size {
			return nil, fmt.Errorf("%s: %s: %d bytes of data at %d are past the end of a %d-byte file",
				path, name, compSize, dataAt, size)
		}

		jar.Entries = append(jar.Entries, Entry{
			Name:     name,
			CRC:      crc,
			Size:     entrySize,
			compSize: compSize,
			method:   method,
			dataAt:   dataAt,
		})
	}
	return jar, nil
}

func (j *Jar) Close() error { return j.file.Close() }

// Data returns an entry's uncompressed bytes, which the caller may read but must neither retain past Close nor modify.
//
// A stored entry - the common case, and the one where the source CRC can be carried straight through - is returned as a
// slice of the mapping, so it reaches the output writer without being copied into the heap first. A deflated entry has
// to be inflated, because the output stores everything: its compressed bytes cannot be passed through.
func (j *Jar) Data(e Entry) ([]byte, error) {
	switch e.method {
	case methodStored:
		if int64(e.Size) != int64(e.compSize) {
			return nil, fmt.Errorf("%s: stored entry says %d bytes compressed and %d uncompressed", e.Name, e.compSize, e.Size)
		}
		return j.data[e.dataAt : e.dataAt+int64(e.Size)], nil
	case methodDeflated:
		r := flate.NewReader(bytes.NewReader(j.data[e.dataAt : e.dataAt+int64(e.compSize)]))
		defer r.Close()
		var out bytes.Buffer
		out.Grow(int(e.Size))
		if _, err := io.Copy(&out, r); err != nil {
			return nil, fmt.Errorf("%s: inflating: %w", e.Name, err)
		}
		return out.Bytes(), nil
	default:
		return nil, fmt.Errorf("%s: unsupported compression method %d", e.Name, e.method)
	}
}

func unsafeString(b []byte) string {
	if len(b) == 0 {
		return ""
	}
	return unsafe.String(&b[0], len(b))
}
