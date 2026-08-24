// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"fmt"
	"hash/crc32"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

// Source is one input of a merge: a jar, and which of its entries belong in the result.
//
// The filter is per source because the two kinds of input are filtered differently. A module output contributes almost
// everything it holds; a third-party library jar has to lose its licences, signatures and multi-release module-info
// entries, or several of them would collide on the same name and the survivors would ship for nothing.
type Source struct {
	Path   string
	Filter func(string) bool
}

// Merge writes target from the entries of sources and returns the names more than one source offered.
//
// **First source wins**, so the caller's order is the precedence: to reproduce what the in-process JarPackager writes,
// every library jar must come before every module output. Duplicates are expected - two libraries can legitimately
// carry the same META-INF/services entry - so a collision is reported rather than fatal.
//
// keepManifest carries the one policy an entry name cannot express: a jar merging several sources must not keep any
// manifest, because the survivor would describe only one of them, while a jar built from a single meaningful source
// keeps its own. rewriteBootClassPath is the only thing that ever changes an entry's *content*; when set, the first
// manifest offered survives whatever keepManifest says, because a manifest that has to be rewritten is one that has to
// survive.
func (s MergeSpec) Merge() ([]string, error) {
	target, sources := s.Output, s.Sources
	keepManifest, rewriteBootClassPath := s.KeepManifest, s.RewriteBootClassPath
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return nil, err
	}
	out, err := os.Create(target)
	if err != nil {
		return nil, err
	}
	defer out.Close()

	// Every source stays mapped until the output is closed, rather than being closed as it is consumed. An entry's name
	// is a string over its source's mapping, and three things outlive the source that offered it: the duplicate set,
	// which spans all of them; the index's directory list, which is serialised at Writer.Close; and the index's package
	// hashes. Unmapping as we go would make each of those a use-after-unmap - it segfaults, reliably, in the middle of
	// the index - and the alternative of copying every name back is the allocation per entry that the mapping removed.
	// A mapping costs address space, not memory, and the widest recipe in the repository merges a few dozen jars.
	opened := make([]*Jar, 0, len(sources))
	defer func() {
		for _, jar := range opened {
			jar.Close()
		}
	}()

	writer := NewWriter(out)
	seen := make(map[string]struct{})
	var duplicates []string

	for _, source := range sources {
		jar, err := OpenJar(source.Path)
		if err != nil {
			return nil, err
		}
		opened = append(opened, jar)
		for _, e := range jar.Entries {
			isRewrittenManifest := rewriteBootClassPath && e.Name == ManifestEntryName
			if !(keepManifest || isRewrittenManifest || e.Name != ManifestEntryName) || !source.Filter(e.Name) {
				continue
			}
			if _, dup := seen[e.Name]; dup {
				// Cloned because this escapes to the caller, which reports it after the mappings are gone.
				duplicates = append(duplicates, strings.Clone(e.Name))
				continue
			}
			seen[e.Name] = struct{}{}

			data, err := jar.Data(e)
			if err != nil {
				return nil, err
			}
			if s.VerifyCRC {
				// Carrying the source's CRC is only sound while the source's CRC is right. Nothing in a build
				// should pay for this check, but a parity run should: it is what turns "we copied the number"
				// into "the number describes these bytes".
				if actual := crc32.ChecksumIEEE(data); actual != e.CRC {
					return nil, fmt.Errorf("%s: %s: source CRC is %08x but its data hashes to %08x",
						source.Path, e.Name, e.CRC, actual)
				}
			}
			if isRewrittenManifest {
				data = rewriteBootClassPathAttribute(data, filepath.Base(target))
				// The content changed, so this is the one entry whose CRC cannot come from the source. It is
				// also kept out of the package index - see Writer.Add.
				if err := writer.Add(e.Name, data, crc32.ChecksumIEEE(data), false); err != nil {
					return nil, err
				}
				continue
			}
			if err := writer.Add(e.Name, data, e.CRC, true); err != nil {
				return nil, err
			}
		}
	}

	if err := writer.Close(); err != nil {
		return nil, err
	}
	return duplicates, nil
}

var bootClassPathPattern = regexp.MustCompile(`Boot-Class-Path:[^\r\n]*`)

// rewriteBootClassPathAttribute points a coverage agent's manifest at the jar it is actually in. The agent
// instruments from any class loader, which needs that attribute to name its own jar - and merging it into
// lib/<module>.jar renames it. A manifest without the attribute is returned untouched, bytes and all.
func rewriteBootClassPathAttribute(data []byte, targetJarName string) []byte {
	if !bootClassPathPattern.Match(data) {
		return data
	}
	return bootClassPathPattern.ReplaceAll(data, []byte("Boot-Class-Path: "+targetJarName))
}

// MergeSpec is one `output=` group of a flag file: a jar and the recipe it is built from.
type MergeSpec struct {
	Output               string
	Sources              []Source
	KeepManifest         bool
	RewriteBootClassPath bool
	// VerifyCRC recomputes every entry's CRC rather than carrying the source's, and fails on a mismatch. Off in a
	// build, on in a parity run.
	VerifyCRC bool
}

// Pack writes the jar this spec describes.
func (s MergeSpec) Pack() ([]string, error) {
	if len(s.Sources) == 0 {
		return nil, fmt.Errorf("no inputs for %q", s.Output)
	}
	return s.Merge()
}
