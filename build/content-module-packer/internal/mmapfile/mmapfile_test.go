// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package mmapfile

import (
	"os"
	"path/filepath"
	"testing"
)

func TestOpenReadsTheWholeFile(t *testing.T) {
	path := filepath.Join(t.TempDir(), "content")
	want := "the bytes, all of them"
	if err := os.WriteFile(path, []byte(want), 0o644); err != nil {
		t.Fatal(err)
	}
	file, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	if got := string(file.Data); got != want {
		t.Errorf("mapped %q, want %q", got, want)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
	// Close is idempotent, because a reader that closes on every error path will reach it twice.
	if err := file.Close(); err != nil {
		t.Errorf("closing twice: %v", err)
	}
}

func TestOpenAcceptsAnEmptyFile(t *testing.T) {
	// mmap of zero bytes is EINVAL, so this is the one size the mapping cannot be asked for - and a caller checking a
	// file is long enough to be a zip wants its own error, not that one.
	path := filepath.Join(t.TempDir(), "empty")
	if err := os.WriteFile(path, nil, 0o644); err != nil {
		t.Fatal(err)
	}
	file, err := Open(path)
	if err != nil {
		t.Fatalf("an empty file: %v", err)
	}
	if len(file.Data) != 0 {
		t.Errorf("mapped %d bytes of an empty file", len(file.Data))
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
}

func TestOpenReportsAMissingFile(t *testing.T) {
	if _, err := Open(filepath.Join(t.TempDir(), "absent")); err == nil {
		t.Error("opening a file that does not exist succeeded")
	}
}
