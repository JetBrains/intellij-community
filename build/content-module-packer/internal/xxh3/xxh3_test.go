// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package xxh3

import (
	"strings"
	"testing"
)

// TestHashBytesAgainstReference walks every length the reference table covers, which is what exercises the length
// classes XXH3 branches on - the 1-3, 4-8, 9-16, 17-128 and 129-240 short paths, and then the block loop past 240
// with its scrambling, including the 1 024-byte block boundary at length 1 025.
func TestHashBytesAgainstReference(t *testing.T) {
	data := make([]byte, len(referenceHashes))
	for i := range data {
		data[i] = byte(i)
	}
	for length, want := range referenceHashes {
		if got := HashBytes(data[:length]); got != want {
			t.Fatalf("HashBytes(src[:%d]) = %d, want %d", length, got, want)
		}
	}
}

// The vectors below are lifted from XxHash3Test.java, which asserts them against both the platform's own
// implementation and hash4j. Sharing the expectations is the point: these are the values the jars in the repository
// were indexed with.
func TestHashBytesStrings(t *testing.T) {
	for _, c := range []struct {
		in   string
		want int64
	}{
		{"com/intellij/profiler/async/windows/WinAsyncProfilerLocator", 2833214887294487028},
		{"test", -7004795540881933248},
		{"тест буковок", -2011715203481716521},
	} {
		if got := HashBytes([]byte(c.in)); got != c.want {
			t.Errorf("HashBytes(%q) = %d, want %d", c.in, got, c.want)
		}
	}
}

func TestHashCharsStrings(t *testing.T) {
	for _, c := range []struct {
		in   string
		want int64
	}{
		{"com/intellij/profiler/async/windows/WinAsyncProfilerLocator", -7916769887311287428},
		{"test", -1876252253805819900},
		{"тест буковок", -3590458601327935281},
	} {
		if got := HashChars(c.in); got != c.want {
			t.Errorf("HashChars(%q) = %d, want %d", c.in, got, c.want)
		}
	}
}

// TestHashCharsPackages mirrors `checkPackage`, which is how PackageIndexBuilder reaches this function: a package name
// arrives already slash-separated, as an entry name's parent directory.
func TestHashCharsPackages(t *testing.T) {
	for _, c := range []struct {
		in   string
		want int64
	}{
		{"com.intellij.util.lang", -9217824570049207139},
		{"com.intellij.idea", -635775336887217634},
		{"kotlin.coroutines.jvm.internal", -3930079881136890558},
	} {
		if got := HashChars(strings.ReplaceAll(c.in, ".", "/")); got != c.want {
			t.Errorf("HashChars(%q) = %d, want %d", c.in, got, c.want)
		}
	}
}

// TestTheTwoFormsDiffer guards the mistake this package exists to prevent. The two hashes must not be interchangeable
// even for pure ASCII, or a packer could use the wrong one everywhere and still produce byte-identical jars until the
// first non-ASCII name showed up.
func TestTheTwoFormsDiffer(t *testing.T) {
	for _, s := range []string{"", "a", "com/intellij/util/lang", "тест"} {
		if HashBytes([]byte(s)) == HashChars(s) && s != "" {
			t.Errorf("HashBytes and HashChars agree on %q; the UTF-8 and UTF-16LE encodings should differ", s)
		}
	}
	// The empty string is the one input where both feed zero bytes, so they legitimately agree.
	if HashBytes(nil) != HashChars("") {
		t.Errorf("empty input: HashBytes = %d, HashChars = %d; both hash zero bytes and must agree",
			HashBytes(nil), HashChars(""))
	}
}
