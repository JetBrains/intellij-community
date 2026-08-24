// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"fmt"
	"slices"
	"unicode/utf16"

	"jetbrains.com/content-module-packer/internal/xxh3"
)

// indexBuilder accumulates the `__index__` entry the platform's class loader reads instead of walking the central
// directory. Ported from PackageIndexBuilder and IkvIndexBuilder in zip/src, in the one configuration the packer uses:
// AddDirEntriesMode.NONE, so directories are recorded *in the index* but never written as zip entries.
//
// Two things here decide bytes and are easy to get subtly wrong, so both are spelled out at their use site: which of
// the two hash encodings each field uses, and the sort order of the arrays.
type indexBuilder struct {
	// entries and names run in parallel, in insertion order: every written file entry as it is written, then the
	// registered directories. The `__index__` entry itself is deliberately absent - it is added to the central
	// directory after its own payload has been serialised, so it cannot describe itself.
	entries []ikvEntry
	names   [][]byte
	// byKey mirrors the Kotlin ObjectLinkedOpenHashSet, whose equality is the key alone: a collision is a hard
	// failure there, so it must be a hard failure here rather than a silent overwrite.
	byKey map[int64]int

	classPackages    map[int64]struct{}
	resourcePackages map[int64]struct{}
	dirsToRegister   map[string]struct{}
	dirOrder         []string
}

type ikvEntry struct {
	key    int64
	offset int64
	size   int32
}

func newIndexBuilder() *indexBuilder {
	return &indexBuilder{
		byKey:            make(map[int64]int),
		classPackages:    make(map[int64]struct{}),
		resourcePackages: make(map[int64]struct{}),
		dirsToRegister:   make(map[string]struct{}),
	}
}

func (b *indexBuilder) add(e ikvEntry, name []byte) error {
	if prev, dup := b.byKey[e.key]; dup {
		return fmt.Errorf("index key collision: %q and %q both hash to %d", b.names[prev], name, e.key)
	}
	b.byKey[e.key] = len(b.entries)
	b.entries = append(b.entries, e)
	b.names = append(b.names, name)
	return nil
}

// addFile records an entry's package for the index. The key is hashed from the *bytes*; the package is hashed from the
// *chars* - see the xxh3 package for why those are different inputs to the same function.
func (b *indexBuilder) addFile(name string) {
	packageHash := int64(0)
	if i := lastIndexByte(name, '/'); i != -1 {
		packageHash = xxh3.HashChars(name[:i])
	}
	if hasSuffix(name, ".class") {
		b.classPackages[packageHash] = struct{}{}
		// AddDirEntriesMode.NONE never registers a class directory, so nothing else to do.
		return
	}
	b.resourcePackages[packageHash] = struct{}{}
	b.registerDirs(name)
}

// registerDirs walks an entry's ancestor directories, stopping at the first one already registered. Only directories
// holding non-class files are registered, because those are the ones asked for at runtime - stubs, file templates.
func (b *indexBuilder) registerDirs(name string) {
	if hasSuffix(name, "/package.html") || name == ManifestEntryName {
		return
	}
	slash := lastIndexByte(name, '/')
	if slash == -1 {
		return
	}
	dir := name[:slash]
	for {
		if _, exists := b.dirsToRegister[dir]; exists {
			return
		}
		b.dirsToRegister[dir] = struct{}{}
		b.dirOrder = append(b.dirOrder, dir)
		b.resourcePackages[xxh3.HashChars(dir)] = struct{}{}

		slash = lastIndexByte(dir, '/')
		if slash == -1 {
			return
		}
		dir = dir[:slash]
	}
}

// finish adds the directory entries, which must happen after every file entry has been written so that the file
// entries occupy the head of the index in write order. Mirrors `writePackageIndex` for AddDirEntriesMode.NONE.
func (b *indexBuilder) finish() error {
	if len(b.resourcePackages) != 0 {
		// An empty package, so a request for the top-level directory resolves.
		b.resourcePackages[0] = struct{}{}
	}
	if len(b.dirsToRegister) == 0 {
		return nil
	}
	dirs := slices.Clone(b.dirOrder)
	// Java's Arrays.sort over String, i.e. UTF-16 code-unit order - not Go's byte order, which disagrees once a
	// non-BMP name is involved.
	slices.SortFunc(dirs, compareJavaString)
	for _, dir := range dirs {
		nameBytes := []byte(dir)
		// size -1 packs to 0xffffffff, which is how the reader tells a directory from a real entry.
		if err := b.add(ikvEntry{key: xxh3.HashBytes(nameBytes), offset: 0, size: -1}, nameBytes); err != nil {
			return err
		}
	}
	return nil
}

// entryTableSize is IkvIndexBuilder.dataSize(): the key/value pairs, the count, and the vestigial "has size" byte.
func (b *indexBuilder) entryTableSize() int {
	return len(b.entries)*16 + 4 + 1
}

func (b *indexBuilder) payloadSize() int {
	nameSize := 0
	for _, n := range b.names {
		nameSize += len(n) + 2
	}
	// The 8 bytes are the two package counts, or a single zero long when both sets are empty.
	return b.entryTableSize() + 8 + (len(b.classPackages)+len(b.resourcePackages))*8 + nameSize
}

// payload serialises the index exactly as IkvIndexBuilder.write plus writeIndex do, all little-endian.
func (b *indexBuilder) payload() []byte {
	out := make([]byte, 0, b.payloadSize())
	for _, e := range b.entries {
		out = appendUint64(out, uint64(e.key))
		out = appendUint64(out, uint64(e.offset)<<32|uint64(uint32(e.size)))
	}
	out = appendUint32(out, uint32(len(b.entries)))
	out = append(out, 1)

	classes, resources := sortedSigned(b.classPackages), sortedSigned(b.resourcePackages)
	if len(classes)+len(resources) == 0 {
		out = appendUint64(out, 0)
	} else {
		out = appendUint32(out, uint32(len(classes)))
		out = appendUint32(out, uint32(len(resources)))
		for _, h := range classes {
			out = appendUint64(out, uint64(h))
		}
		for _, h := range resources {
			out = appendUint64(out, uint64(h))
		}
	}

	for _, n := range b.names {
		out = appendUint16(out, uint16(len(n)))
	}
	for _, n := range b.names {
		out = append(out, n...)
	}
	return out
}

// sortedSigned sorts as Java's long[] does - signed - which matters because half the hashes are negative.
func sortedSigned(set map[int64]struct{}) []int64 {
	out := make([]int64, 0, len(set))
	for k := range set {
		out = append(out, k)
	}
	slices.Sort(out)
	return out
}

// compareJavaString orders two strings the way java.lang.String.compareTo does: by UTF-16 code unit. For anything in
// the BMP this is the same as comparing runes, but a supplementary character encodes as a surrogate pair beginning
// 0xD800-0xDBFF, which sorts *below* U+E000-U+FFFF where a rune comparison would sort it above.
func compareJavaString(a, b string) int {
	ua, ub := utf16.Encode([]rune(a)), utf16.Encode([]rune(b))
	for i := 0; i < len(ua) && i < len(ub); i++ {
		if ua[i] != ub[i] {
			return int(ua[i]) - int(ub[i])
		}
	}
	return len(ua) - len(ub)
}

func lastIndexByte(s string, c byte) int {
	for i := len(s) - 1; i >= 0; i-- {
		if s[i] == c {
			return i
		}
	}
	return -1
}

func hasSuffix(s, suffix string) bool {
	return len(s) >= len(suffix) && s[len(s)-len(suffix):] == suffix
}

func appendUint16(b []byte, v uint16) []byte { return append(b, byte(v), byte(v>>8)) }
func appendUint32(b []byte, v uint32) []byte {
	return append(b, byte(v), byte(v>>8), byte(v>>16), byte(v>>24))
}
func appendUint64(b []byte, v uint64) []byte {
	return append(b, byte(v), byte(v>>8), byte(v>>16), byte(v>>24), byte(v>>32), byte(v>>40), byte(v>>48), byte(v>>56))
}

// hashName is the IKV key for an entry or directory: the hash of the name's raw bytes, not of its chars.
func hashName(nameBytes []byte) int64 { return xxh3.HashBytes(nameBytes) }
