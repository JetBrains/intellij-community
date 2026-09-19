// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"fmt"
	"hash/crc32"
	"os"
	"path/filepath"

	"jetbrains.com/content-module-packer/internal/nativelib"
)

// NativeSpec is the natives mode of one `output=` group: the jar leaves every native entry of one library out, and
// the entries of one target platform are written as files under Tree instead.
//
// This is what JarPackager does for a presigned library such as jna, pty4j, skiko or async-profiler. The native entry's
// name is claimed, so a later source cannot smuggle another copy in, but neither the bytes nor an index record are
// written, and the platform's files land under `lib/<lib>/`. A jar packed this way is byte-identical to the one
// JarPackager writes, and the tree is what the distribution loads the natives from.
type NativeSpec struct {
	// Tree is the directory the selected files are written into. It is created, never cleaned; a platform with no
	// matching entry leaves it empty.
	Tree string
	// Family and Arch are the target platform, read from `native-variant=` by nativelib.ParseVariant.
	Family nativelib.Family
	Arch   nativelib.Arch
	// LibName is the Maven artifact name that selects the native source among the `library=` lines, by
	// nativelib.LibNameFromFile of the jar's file name, and that decides the layout under Tree.
	LibName string
}

func (spec *NativeSpec) validate(output string) error {
	if spec.Tree == "" || spec.LibName == "" || !nativelib.ValidFamily(spec.Family) || !nativelib.ValidArch(spec.Arch) {
		return fmt.Errorf("%s: incomplete native tree specification", output)
	}
	return nil
}

// nativeSourceIndex is the position of the one `library=` source of the native library. Zero or two are an error,
// because the recipe would either write an empty tree for nothing or leave one library's natives in the jar.
func (s MergeSpec) nativeSourceIndex() (int, error) {
	index := -1
	for i, source := range s.Sources {
		if !source.Library || nativelib.LibNameFromFile(filepath.Base(source.Path)) != s.Native.LibName {
			continue
		}
		if index != -1 {
			return -1, fmt.Errorf("%s: two library sources of the native library %s: %s and %s",
				s.Output, s.Native.LibName, s.Sources[index].Path, source.Path)
		}
		index = i
	}
	if index == -1 {
		return -1, fmt.Errorf("%s: no library source of the native library %s", s.Output, s.Native.LibName)
	}
	return index, nil
}

// nativeMerge is the natives-mode state of one Merge: the native source's position, its opened jar, and its native
// entries in central-directory order, each name once.
type nativeMerge struct {
	index   int
	jar     *Jar
	entries []Entry
}

// reserve lists the native entries of the native source and claims each one in the source's overrides, so Merge
// records the name and writes nothing. An entry is native when the source's filter includes it and
// nativelib.IsNativeEntry accepts it. A source without one is an error, because natives mode has no use for it.
func (natives *nativeMerge) reserve(jar *Jar, source *Source) error {
	seen := make(map[string]struct{})
	overrides := make(map[string]EntryOverride, len(source.EntryOverrides))
	for name, override := range source.EntryOverrides {
		overrides[name] = override
	}
	for _, e := range jar.Entries {
		if _, dup := seen[e.Name]; dup || !source.Filter(e.Name) || !nativelib.IsNativeEntry(e.Name) {
			continue
		}
		seen[e.Name] = struct{}{}
		natives.entries = append(natives.entries, e)
		overrides[e.Name] = EntryOverride{Reserve: true}
	}
	if len(natives.entries) == 0 {
		return fmt.Errorf("%s holds no native entry", source.Path)
	}
	natives.jar = jar
	source.EntryOverrides = overrides
	return nil
}

// writeNativeTree writes the native entries of the target platform under the tree, after the jar is closed. The
// selection and the layout are nativelib's; the modes are JarPackager's: 0755 for a POSIX file without an extension,
// which is executed directly, and 0644 for everything else.
func (s MergeSpec) writeNativeTree(natives *nativeMerge) error {
	spec, sourcePath := s.Native, s.Sources[natives.index].Path
	if err := os.MkdirAll(spec.Tree, 0o755); err != nil {
		return err
	}
	names := make([]string, 0, len(natives.entries))
	byName := make(map[string]Entry, len(natives.entries))
	for _, e := range natives.entries {
		names = append(names, e.Name)
		byName[e.Name] = e
	}
	matches, err := nativelib.Select(names, spec.Family, spec.Arch)
	if err != nil {
		return fmt.Errorf("%s: %w", sourcePath, err)
	}
	claimed := make(map[string]string, len(matches))
	for _, match := range matches {
		relativePath, err := nativelib.RelativePath(spec.LibName, match.Arch, match.FileName(), match.Path)
		if err != nil {
			return fmt.Errorf("%s: %w", sourcePath, err)
		}
		// The path comes from an archive entry name and becomes a file path, so it is checked here even when the merge
		// itself does not validate names.
		if err := ValidateEntryName(relativePath); err != nil {
			return fmt.Errorf("%s: %s: %w", sourcePath, match.PathWithPrefix, err)
		}
		if previous, dup := claimed[relativePath]; dup {
			return fmt.Errorf("%s: two native entries select %q: %s and %s", sourcePath, relativePath, previous, match.PathWithPrefix)
		}
		claimed[relativePath] = match.PathWithPrefix
		e := byName[match.PathWithPrefix]
		data, err := natives.jar.Data(e)
		if err != nil {
			return fmt.Errorf("%s: %w", sourcePath, err)
		}
		if s.VerifyCRC && crc32.ChecksumIEEE(data) != e.CRC {
			return fmt.Errorf("%s: %s: source CRC does not match", sourcePath, e.Name)
		}
		mode := os.FileMode(0o644)
		if nativelib.IsExecutable(match.Family, match.FileName()) {
			mode = 0o755
		}
		target := filepath.Join(spec.Tree, filepath.FromSlash(relativePath))
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return err
		}
		if err := os.WriteFile(target, data, mode); err != nil {
			return err
		}
		// WriteFile's mode is subject to the umask, and the tree is inventoried by mode.
		if err := os.Chmod(target, mode); err != nil {
			return err
		}
	}
	return nil
}
