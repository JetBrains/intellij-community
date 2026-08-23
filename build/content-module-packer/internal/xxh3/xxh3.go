// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Package xxh3 produces the two hashes an IntelliJ distribution jar's `__index__` is keyed by.
//
// Both are XXH3-64 with seed 0 over the same 192-byte FARSH secret as the reference implementation, and the
// distinction that matters is *what bytes go in*. `PackageIndexBuilder` hashes an entry or directory name two
// different ways: as UTF-8 bytes for the IKV keys, and as UTF-16 code units for the class/resource package sets - and
// for an ASCII name those are different byte sequences that happen to be hashed by the same function. Naming them
// apart here is the whole point of this package: a packer that used one form for both would produce byte-identical
// jars for every ASCII name in the repository and diverge on the first non-ASCII resource path.
//
// The algorithm itself is github.com/zeebo/xxh3 rather than a port. It is bit-exact with the reference (upstream
// froze XXH3's output at v0.8.0), carries assembly for amd64 and arm64, and needs no cgo - and the alternative was
// owning several hundred lines of length-class bit-twiddling inside the one tool whose entire contract is writing
// byte-identical archives. What is *not* delegated is the proof: `xxh3_test.go` checks this package against the same
// 2 050 reference vectors the platform's own Java implementation is checked against.
package xxh3

import (
	"unicode/utf16"

	"github.com/zeebo/xxh3"
)

// HashBytes is hash4j's `Hashing.xxh3_64().hashBytesToLong`, which adds no framing of its own - the digest is over
// exactly the bytes given. This is the form the IKV keys and the directory-name entries use.
//
// The result is signed because every consumer of it is: the index stores int64 keys and sorts the package arrays with
// Java's signed comparison, so treating a hash as unsigned anywhere would reorder the arrays and change the bytes.
func HashBytes(data []byte) int64 {
	return int64(xxh3.Hash(data))
}

// HashChars is hash4j's `Hashing.xxh3_64().hashCharsToLong`, the form the class and resource package sets use.
//
// A Java string is a sequence of UTF-16 code units, and `putChars` feeds each one as two little-endian bytes with no
// length appended - that last part is what separates it from `putString`, which appends the length and would give a
// different digest. So the input here is 2*len(codeUnits) bytes, not the UTF-8 encoding of s.
//
// Only well-formed input reaches this: the reader rejects an entry name that is not valid UTF-8 rather than let Go's
// raw bytes and Java's U+FFFD replacement disagree silently, so the round trip through runes cannot lose anything a
// jar we would agree about could carry.
func HashChars(s string) int64 {
	units := utf16.Encode([]rune(s))
	buf := make([]byte, 2*len(units))
	for i, u := range units {
		buf[2*i] = byte(u)
		buf[2*i+1] = byte(u >> 8)
	}
	return int64(xxh3.Hash(buf))
}
