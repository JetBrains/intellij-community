// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//go:build unix

package mmapfile

import (
	"fmt"
	"os"
	"syscall"
)

// Open maps path read-only.
//
// The descriptor is closed immediately: a mapping keeps its own reference to the file, so holding the descriptor as well
// would only spend one per jar being merged - and a merge holds several at once.
func Open(path string) (*File, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return nil, err
	}
	size := info.Size()
	if size == 0 {
		// mmap of nothing is EINVAL, and a caller that wants a length check should get its own error, not this one.
		return &File{Data: []byte{}}, nil
	}
	if size != int64(int(size)) {
		return nil, fmt.Errorf("%s: %d bytes does not fit an address space", path, size)
	}
	data, err := syscall.Mmap(int(file.Fd()), 0, int(size), syscall.PROT_READ, syscall.MAP_SHARED)
	if err != nil {
		return nil, fmt.Errorf("%s: mmap: %w", path, err)
	}
	return &File{Data: data, unmap: syscall.Munmap}, nil
}
