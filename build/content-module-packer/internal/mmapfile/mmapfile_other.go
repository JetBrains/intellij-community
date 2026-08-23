// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//go:build !unix

package mmapfile

import "os"

// Open reads path into the heap.
//
// Windows has file mapping too, but it is a different four-call dance and this is a build tool: what the mapping buys
// over one read is a copy of the file, not the syscall-per-entry that was the reason for this package - a single read
// already removes that. If a Windows packing run is ever measured and found wanting, this is the place to add
// `CreateFileMapping`.
func Open(path string) (*File, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	return &File{Data: data}, nil
}
