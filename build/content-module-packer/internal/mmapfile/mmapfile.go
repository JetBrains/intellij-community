// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Package mmapfile reads a file as one addressable byte slice.
//
// It exists for the jar reader, where the alternative is a syscall per zip entry. A central directory record says where
// its entry's local header is but not how long that header's extra field is, so the data offset can only be had from the
// header itself - which as a `ReadAt` is one `pread` for two useful bytes, per entry, and then a second `pread` for the
// entry's data. Measured over the 2 200 real recipes in this repository, that was 3.65 s of the packer's 5.91 s of CPU;
// against a mapping both become memory reads, and a STORED entry is written to the output straight from the page cache
// with no intermediate buffer at all.
//
// `syscall` rather than `golang.org/x/sys/unix`: mmap is two calls that have not changed in decades, and the standard
// library already has them on every unix - so this costs no dependency edge.
package mmapfile

// File is a file's contents, addressable as one slice.
//
// Data is valid only until Close, and nothing read out of it may outlive that: a slice of it is a pointer into a
// mapping, so retaining one past Close is a use-after-unmap rather than a stale copy. Callers that keep bytes - the jar
// writer keeps every entry name - copy them.
type File struct {
	Data []byte
	// unmap is nil when the contents were read into the heap instead of mapped, which is the platform fallback.
	unmap func([]byte) error
}

// Close releases the mapping. Data must not be read afterwards.
func (f *File) Close() error {
	if f == nil || f.Data == nil {
		return nil
	}
	data, unmap := f.Data, f.unmap
	f.Data, f.unmap = nil, nil
	if unmap == nil {
		return nil
	}
	return unmap(data)
}
