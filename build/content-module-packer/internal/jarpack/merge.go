// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"fmt"
	"hash/crc32"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"strings"
	"unicode"
	"unicode/utf8"
)

// Source is one input of a merge: a jar and which of its entries belong in the result, or one file and the name it
// takes.
//
// The filter is per source because the two kinds of jar input are filtered differently. A module output contributes
// almost everything it holds; a third-party library jar has to lose its licences, signatures and multi-release
// module-info entries, or several of them would collide on the same name and the survivors would ship for nothing.
//
// [Source.Name] selects the third kind. It is empty for a jar, and a jar's entry names come from the jar. It is set for
// a single file, whose bytes become one entry under that name, and then [Source.Filter] says nothing and is nil: a
// source of one entry has nothing to select. The dev distribution's produced plugin descriptor is that kind - the text
// lives in a file an action wrote, so the recipe names the file's label and this packer can pack it.
type Source struct {
	Path   string
	Filter func(string) bool
	// Name is the entry name a single-file source takes, and empty for a jar source.
	Name           string
	Patch          bool
	Reserve        bool
	Manifest       ManifestMode
	EntryOverrides map[string]EntryOverride
}

// ManifestMode sets the policy for one source. The empty value uses the merge policy.
type ManifestMode string

const (
	ManifestDrop                 ManifestMode = "drop"
	ManifestKeep                 ManifestMode = "keep"
	ManifestRewriteBootClassPath ManifestMode = "rewrite-boot-class-path"
	ManifestCoverageAgent        ManifestMode = "coverage-agent"
)

// EntryOverride replaces or reserves an archive entry at its original position.
// A reservation claims the name without writing the entry.
type EntryOverride struct {
	Path    string
	Reserve bool
}

func (source Source) manifestPolicy(spec MergeSpec) (bool, bool, error) {
	switch source.Manifest {
	case "":
		return spec.KeepManifest, false, nil
	case ManifestDrop:
		return false, false, nil
	case ManifestKeep:
		return true, false, nil
	case ManifestRewriteBootClassPath, ManifestCoverageAgent:
		return true, true, nil
	default:
		return false, false, fmt.Errorf("unknown manifest policy %q", source.Manifest)
	}
}

// ValidateEntryName accepts a portable, relative file name. Directories and the generated index are not source entries.
func ValidateEntryName(name string) error {
	if name == "" || name == "." || path.Clean(name) != name || path.IsAbs(name) ||
		name == ".." || strings.HasPrefix(name, "../") || strings.ContainsAny(name, "\\:\x00\r\n") ||
		!utf8.ValidString(name) || len(name) > 65535 || name == IndexFileName {
		return fmt.Errorf("unsafe entry name %q", name)
	}
	return nil
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
	if err := s.validateSources(); err != nil {
		return nil, err
	}
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

	writer, err := NewWriterWithDirectoryMode(out, s.DirectoryMode)
	if err != nil {
		return nil, err
	}
	seen := make(map[string]struct{})
	var duplicates []string
	var entities []string

	for _, source := range sources {
		keepManifest, rewriteBootClassPath, _ := source.manifestPolicy(s)
		if source.Name != "" {
			if s.MergeEntities && source.Name == "META-INF/listOfEntities.txt" && !source.Patch && !source.Reserve {
				data, err := os.ReadFile(source.Path)
				if err != nil {
					return nil, err
				}
				entities = append(entities, trimEntityList(data))
				continue
			}
			dup, err := addFileSource(writer, source, seen, keepManifest, rewriteBootClassPath, target)
			if err != nil {
				return nil, err
			}
			if dup {
				duplicates = append(duplicates, source.Name)
			}
			continue
		}

		jar, err := OpenJar(source.Path)
		if err != nil {
			return nil, err
		}
		opened = append(opened, jar)
		unmatched := make(map[string]struct{}, len(source.EntryOverrides))
		for name := range source.EntryOverrides {
			unmatched[name] = struct{}{}
		}
		for _, e := range jar.Entries {
			if s.ValidateEntryNames {
				if err := ValidateEntryName(e.Name); err != nil {
					return nil, fmt.Errorf("%s: %w", source.Path, err)
				}
			}
			override, overridden := source.EntryOverrides[e.Name]
			delete(unmatched, e.Name)
			if s.RejectNativeEntries && isNativeEntry(e.Name) {
				return nil, fmt.Errorf("%s contains native entry %s; keep this jar with the Kotlin packer", source.Path, e.Name)
			}
			if s.MergeEntities && e.Name == "META-INF/listOfEntities.txt" {
				data, err := jar.Data(e)
				if err != nil {
					return nil, err
				}
				if s.VerifyCRC && crc32.ChecksumIEEE(data) != e.CRC {
					return nil, fmt.Errorf("%s: %s: source CRC does not match", source.Path, e.Name)
				}
				entities = append(entities, trimEntityList(data))
				continue
			}
			isRewrittenManifest := rewriteBootClassPath && e.Name == ManifestEntryName
			included := (keepManifest || isRewrittenManifest || e.Name != ManifestEntryName) && source.Filter(e.Name)
			if source.Manifest == ManifestCoverageAgent && e.Name == ManifestEntryName {
				included = true
			}
			if !included {
				if overridden {
					return nil, fmt.Errorf("%s: override of excluded entry %s", source.Path, e.Name)
				}
				continue
			}
			if _, dup := seen[e.Name]; dup {
				// Cloned because this escapes to the caller, which reports it after the mappings are gone.
				duplicates = append(duplicates, strings.Clone(e.Name))
				continue
			}
			seen[e.Name] = struct{}{}
			if override.Reserve {
				continue
			}

			var data []byte
			entryCRC := e.CRC
			if overridden {
				data, err = os.ReadFile(override.Path)
				entryCRC = crc32.ChecksumIEEE(data)
			} else {
				data, err = jar.Data(e)
			}
			if err != nil {
				return nil, err
			}
			if s.VerifyCRC && !overridden {
				// Carrying the source's CRC is only sound while the source's CRC is right. Nothing in a build
				// should pay for this check, but a parity run should: it is what turns "we copied the number"
				// into "the number describes these bytes".
				if actual := crc32.ChecksumIEEE(data); actual != e.CRC {
					return nil, fmt.Errorf("%s: %s: source CRC is %08x but its data hashes to %08x",
						source.Path, e.Name, e.CRC, actual)
				}
			}
			if isRewrittenManifest {
				data = rewriteSourceManifest(data, source.Manifest, target)
				// The content changed, so this is the one entry whose CRC cannot come from the source. It is
				// also kept out of the package index - see Writer.Add.
				if err := writer.Add(e.Name, data, crc32.ChecksumIEEE(data), false); err != nil {
					return nil, err
				}
				continue
			}
			if err := writer.Add(e.Name, data, entryCRC, true); err != nil {
				return nil, err
			}
		}
		if len(unmatched) != 0 {
			return nil, fmt.Errorf("%s: overrides reference missing entries", source.Path)
		}
	}

	if len(entities) != 0 {
		data := []byte(strings.Join(entities, "\n"))
		if err := writer.Add("META-INF/listOfEntities.txt", data, crc32.ChecksumIEEE(data), false); err != nil {
			return nil, err
		}
	}
	if err := writer.Close(); err != nil {
		return nil, err
	}
	return duplicates, out.Close()
}

func (s MergeSpec) validateSources() error {
	if !s.DirectoryMode.valid() {
		return fmt.Errorf("unknown directory mode %q", s.DirectoryMode)
	}
	for _, source := range s.Sources {
		if _, _, err := source.manifestPolicy(s); err != nil {
			return err
		}
		if source.Name != "" {
			if s.ValidateEntryNames {
				if err := ValidateEntryName(source.Name); err != nil {
					return err
				}
			}
			if len(source.EntryOverrides) != 0 || source.Filter != nil || (source.Reserve && (source.Path != "" || source.Patch)) || (!source.Reserve && source.Path == "") {
				return fmt.Errorf("invalid file source %q", source.Name)
			}
		} else if source.Filter == nil || source.Path == "" || source.Patch || source.Reserve {
			return fmt.Errorf("invalid archive source %q", source.Path)
		}
		for name, override := range source.EntryOverrides {
			if err := ValidateEntryName(name); err != nil {
				return err
			}
			if (override.Reserve && override.Path != "") || (!override.Reserve && override.Path == "") || name == ManifestEntryName || name == "META-INF/listOfEntities.txt" {
				return fmt.Errorf("invalid override of %q", name)
			}
		}
	}
	return nil
}

func trimEntityList(data []byte) string {
	return strings.TrimFunc(string(data), func(value rune) bool {
		return value != 0x85 && (unicode.IsSpace(value) || value >= 0x1c && value <= 0x1f)
	})
}

// addFileSource writes a single-file source as one entry, and reports whether an earlier source already took the name.
//
// It reads the whole file rather than mapping it. A single-file source is one small entry - the widest one in this
// product is a 600 KiB plugin descriptor - so there is nothing for a mapping to save, and the bytes must outlive the
// read anyway because Writer.Add keeps them until Close.
//
// The manifest rules of Merge apply unchanged, so a file source cannot smuggle a manifest past keepManifest. Its CRC is
// always computed, because no source states one for it, so this takes no VerifyCRC parameter.
func addFileSource(
	writer *Writer,
	source Source,
	seen map[string]struct{},
	keepManifest bool,
	rewriteBootClassPath bool,
	target string,
) (bool, error) {
	if source.Reserve {
		_, duplicate := seen[source.Name]
		seen[source.Name] = struct{}{}
		return duplicate, nil
	}
	isRewrittenManifest := rewriteBootClassPath && source.Name == ManifestEntryName
	if !source.Patch && source.Name == ManifestEntryName && !keepManifest && !isRewrittenManifest {
		return false, nil
	}
	if _, dup := seen[source.Name]; dup {
		if source.Patch {
			return false, fmt.Errorf("%s: patch %s must precede every source of that entry", target, source.Name)
		}
		return true, nil
	}
	seen[source.Name] = struct{}{}

	data, err := os.ReadFile(source.Path)
	if err != nil {
		return false, err
	}
	if isRewrittenManifest {
		data = rewriteSourceManifest(data, source.Manifest, target)
		return false, writer.Add(source.Name, data, crc32.ChecksumIEEE(data), false)
	}
	return false, writer.Add(source.Name, data, crc32.ChecksumIEEE(data), true)
}

var bootClassPathPattern = regexp.MustCompile(`Boot-Class-Path:[^\r\n]*`)
var coverageAgentPattern = regexp.MustCompile(`Boot-Class-Path: intellij-coverage-agent-\d+(\.\d+)*\.jar`)

func rewriteSourceManifest(data []byte, mode ManifestMode, target string) []byte {
	if mode == ManifestCoverageAgent {
		return coverageAgentPattern.ReplaceAll(data, []byte("Boot-Class-Path: intellij.platform.coverage.agent.jar"))
	}
	return rewriteBootClassPathAttribute(data, filepath.Base(target))
}

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
	Output              string
	Sources             []Source
	KeepManifest        bool
	MergeEntities       bool
	RejectNativeEntries bool
	MetadataFile        string
	DirectoryMode       DirectoryMode
	ValidateEntryNames  bool
	// TraceFile is where the *run* writes its spans, and nothing about packing reads it. It arrives in the flag file
	// because that is a worker's only per-request channel; see ParseFlagFile.
	TraceFile string
	// VerifyCRC recomputes every entry's CRC rather than carrying the source's, and fails on a mismatch. Off in a
	// build, on in a parity run.
	VerifyCRC bool
}

func isNativeEntry(name string) bool {
	switch filepath.Ext(name) {
	case ".so", ".dylib", ".jnilib", ".dll", ".exe":
		return true
	}
	return strings.HasSuffix(name, "pty4j-unix-spawn-helper") || strings.HasSuffix(name, "icudtl.dat")
}

// Pack writes the jar this spec describes.
func (s MergeSpec) Pack() ([]string, error) {
	if len(s.Sources) == 0 {
		return nil, fmt.Errorf("no inputs for %q", s.Output)
	}
	return s.Merge()
}
