// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"archive/zip"
	"bytes"
	"encoding/binary"
	"slices"
	"strings"
	"testing"
)

// The output format is the whole point of writing these headers by hand: every field a general writer would fill in is a
// hard zero here, which is what makes the bytes a pure function of (name, size, crc) per entry. `archive/zip` cannot be
// made to emit that, so it is asserted rather than assumed.
func TestWriterEmitsNormalisedHeaders(t *testing.T) {
	var out bytes.Buffer
	writer := NewWriter(&out)
	if err := writer.Add("com/example/Service.class", []byte("class bytes"), crc32Of([]byte("class bytes")), true); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	data := out.Bytes()

	local := data[:localHeaderSize]
	for _, field := range []struct {
		name   string
		offset int
		size   int
	}{
		{"version needed to extract", 4, 2},
		{"general purpose flags", 6, 2},
		{"compression method", 8, 2},
		{"modification time and date", 10, 4},
		{"extra field length", 28, 2},
	} {
		if got := local[field.offset : field.offset+field.size]; !bytes.Equal(got, make([]byte, field.size)) {
			t.Errorf("local header %s is %v, want zero", field.name, got)
		}
	}

	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		t.Fatal(err)
	}
	for _, file := range reader.File {
		if file.Method != zip.Store {
			t.Errorf("%s is compressed with method %d, want STORED", file.Name, file.Method)
		}
		if len(file.Extra) != 0 {
			t.Errorf("%s carries a %d-byte extra field, want none", file.Name, len(file.Extra))
		}
		if file.ExternalAttrs != 0 {
			t.Errorf("%s carries external attributes %08x, want none - no unix mode reaches the archive",
				file.Name, file.ExternalAttrs)
		}
	}
}

func TestWriterPointsTheEndRecordCommentIntoTheIndex(t *testing.T) {
	var out bytes.Buffer
	writer := NewWriter(&out)
	// Both entries are classes on purpose: a resource would also register its directory, and a registered directory
	// occupies an entry-table slot of its own - see index_test.go - which is not what this assertion is about.
	names := []string{"com/example/Service.class", "com/example/Other.class"}
	for _, name := range names {
		if err := writer.Add(name, []byte(name), crc32Of([]byte(name)), true); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	data := out.Bytes()

	pointer := indexPointerOf(t, data)
	if pointer <= 0 || int(pointer) >= len(data) {
		t.Fatalf("index pointer is %d, outside a %d-byte jar", pointer, len(data))
	}
	// The pointer is the first byte past the entry table, inside the index payload - so the entry count sits immediately
	// before it, and reading it back is what proves the pointer is not merely plausible.
	count := binary.LittleEndian.Uint32(data[pointer-5:])
	if got, want := int(count), len(names); got != want {
		t.Errorf("the entry table before the pointer holds %d entries, want %d", got, want)
	}
}

func indexPointerOf(t *testing.T, data []byte) int32 {
	t.Helper()
	tail := data[len(data)-27:]
	if binary.LittleEndian.Uint32(tail) != eocdSignature {
		t.Fatal("no end-of-central-directory record 27 bytes from the end")
	}
	return int32(binary.LittleEndian.Uint32(tail[23:]))
}

func TestWriterDirectoryModesMatchKotlinIndexRecords(t *testing.T) {
	for _, mode := range []DirectoryMode{DirectoriesNone, DirectoriesResources, DirectoriesAll} {
		t.Run(string(mode), func(t *testing.T) {
			var output bytes.Buffer
			writer, err := NewWriterWithDirectoryMode(&output, mode)
			if err != nil {
				t.Fatal(err)
			}
			for _, name := range []string{"classes/Value.class", "resources/nested/value.txt"} {
				if err := writer.Add(name, []byte(name), crc32Of([]byte(name)), true); err != nil {
					t.Fatal(err)
				}
			}
			if err := writer.Close(); err != nil {
				t.Fatal(err)
			}
			var directories []string
			for _, name := range entryNames(t, output.Bytes()) {
				if strings.HasSuffix(name, "/") {
					directories = append(directories, name)
				}
			}
			var want []string
			if mode != DirectoriesNone {
				want = []string{"resources/", "resources/nested/"}
				if mode == DirectoriesAll {
					want = append([]string{"classes/"}, want...)
				}
			}
			if !slices.Equal(directories, want) {
				t.Fatalf("directories %v, want %v", directories, want)
			}
			for position, name := range writer.index.names {
				if !slices.Contains([]string{"classes", "resources", "resources/nested"}, string(name)) {
					continue
				}
				entry := writer.index.entries[position]
				if mode == DirectoriesNone {
					if entry.offset != 0 || entry.size != -1 {
						t.Fatalf("virtual directory record: %+v", entry)
					}
				} else if entry.offset != -1 || entry.size != 0 {
					t.Fatalf("real directory record: %+v", entry)
				}
			}
			pointer := indexPointerOf(t, output.Bytes())
			if count := binary.LittleEndian.Uint32(output.Bytes()[pointer-5:]); int(count) != len(writer.index.entries) {
				t.Fatalf("index pointer count %d", count)
			}
			reader, err := zip.NewReader(bytes.NewReader(output.Bytes()), int64(output.Len()))
			if err != nil {
				t.Fatal(err)
			}
			for _, file := range reader.File {
				if !strings.HasSuffix(file.Name, "/") {
					continue
				}
				dataOffset, err := file.DataOffset()
				if err != nil {
					t.Fatal(err)
				}
				header := output.Bytes()[int(dataOffset)-localHeaderSize-len(file.Name):]
				if !bytes.Equal(header[4:26], make([]byte, 22)) || file.ExternalAttrs != 0 || file.Flags != 0 || file.CRC32 != 0 {
					t.Fatalf("nonzero directory header: %+v", file.FileHeader)
				}
			}
		})
	}
}

func TestWriterRejectsUnknownDirectoryModesAndLongNames(t *testing.T) {
	if _, err := NewWriterWithDirectoryMode(&bytes.Buffer{}, "guess"); err == nil {
		t.Fatal("accepted an unknown directory mode")
	}
	writer := NewWriter(&bytes.Buffer{})
	if err := writer.Add(strings.Repeat("a", 65536), nil, 0, true); err == nil {
		t.Fatal("accepted an overflowing entry name")
	}
}
