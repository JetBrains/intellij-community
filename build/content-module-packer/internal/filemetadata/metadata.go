package filemetadata

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"unicode/utf8"

	"github.com/zeebo/xxh3"
	"golang.org/x/text/cases"
	"golang.org/x/text/unicode/norm"
)

const Version = 1
const BlockSize = 256 * 1024

type Entry struct {
	RelativePath  string `json:"relativePath"`
	Type          string `json:"type"`
	Hash          int64  `json:"hash"`
	Size          int64  `json:"size"`
	Mode          uint32 `json:"mode"`
	Executable    bool   `json:"executable"`
	SymlinkTarget string `json:"symlinkTarget,omitempty"`
}

type manifest struct {
	Version int     `json:"version"`
	Entries []Entry `json:"entries"`
}

func (entry Entry) MarshalJSON() ([]byte, error) {
	type encodedEntry Entry
	if entry.Type != "directory" {
		return json.Marshal(encodedEntry(entry))
	}
	return json.Marshal(struct {
		encodedEntry
		Hash *int64 `json:"hash,omitempty"`
	}{encodedEntry: encodedEntry(entry)})
}

// HashFile computes the xxh3 content hash that the Kotlin manifest writer computes for the same file. The hash frames
// each 256 KiB block with its 4-byte little-endian length. The Kotlin computeDevBuildContentHash
// (DevBuildComponentManifest.kt) feeds each block through hash4j putByteArray, which appends the array length.
// TestKotlinHashVectors pins the agreement.
func HashFile(source string) (int64, error) {
	file, err := os.Open(source)
	if err != nil {
		return 0, err
	}
	defer file.Close()
	buffer := make([]byte, BlockSize+4)
	hasher := xxh3.New()
	for {
		count, err := io.ReadFull(file, buffer[:BlockSize])
		if err != nil && err != io.EOF && err != io.ErrUnexpectedEOF {
			return 0, err
		}
		if count != 0 {
			binary.LittleEndian.PutUint32(buffer[count:count+4], uint32(count))
			hasher.Write(buffer[:count+4])
		}
		if err != nil {
			return int64(hasher.Sum64()), nil
		}
	}
}

// Permissions returns the permission bits of info. NTFS stores no POSIX mode, and Go reports 0666 for a file and
// 0777 for a directory there. On Windows a directory is 0755 and every other entry is 0644, the conventional modes.
func Permissions(info fs.FileInfo) uint32 {
	if runtime.GOOS == "windows" {
		if info.IsDir() {
			return 0o755
		}
		return 0o644
	}
	return uint32(info.Mode().Perm())
}

func Inspect(source, relativePath string) (Entry, error) {
	entry := Entry{RelativePath: relativePath, Type: "file"}
	if err := ValidatePath(relativePath); err != nil {
		return entry, err
	}
	info, err := os.Lstat(source)
	if err != nil {
		return entry, err
	}
	entry.Mode = Permissions(info)
	entry.Size = info.Size()
	entry.Executable = entry.Mode&0111 != 0
	switch {
	case info.IsDir():
		entry.Type = "directory"
		entry.Size, entry.Executable = 0, false
	case info.Mode().IsRegular():
		entry.Hash, err = HashFile(source)
	case info.Mode()&os.ModeSymlink != 0:
		entry.Type = "symlink"
		entry.Size, entry.Mode, entry.Executable = 0, 0, false
		entry.SymlinkTarget, err = ReadLinkTarget(source)
		entry.Hash = int64(xxh3.HashString(entry.SymlinkTarget))
	default:
		return entry, fmt.Errorf("not a regular file or symbolic link: %s", source)
	}
	if err == nil {
		err = validateEntry(entry)
	}
	return entry, err
}

func Inventory(root string) ([]Entry, error) {
	info, err := os.Lstat(root)
	if err != nil {
		return nil, err
	}
	if !info.IsDir() {
		return nil, fmt.Errorf("inventory root is not a directory: %s", root)
	}
	entries := []Entry{}
	err = filepath.WalkDir(root, func(source string, item fs.DirEntry, walkError error) error {
		if walkError != nil {
			return walkError
		}
		if source == root {
			return nil
		}
		relativePath, err := filepath.Rel(root, source)
		if err != nil {
			return err
		}
		entry, err := Inspect(source, filepath.ToSlash(relativePath))
		if err == nil {
			entries = append(entries, entry)
		}
		return err
	})
	if err != nil {
		return nil, err
	}
	return Merge(entries)
}

func Read(source string) ([]Entry, error) {
	data, err := os.ReadFile(source)
	if err != nil {
		return nil, err
	}
	if !utf8.Valid(data) {
		return nil, fmt.Errorf("%s: metadata is not valid UTF-8", source)
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	var record manifest
	if err := decoder.Decode(&record); err != nil {
		return nil, fmt.Errorf("%s: %w", source, err)
	}
	if err := decoder.Decode(new(any)); err != io.EOF {
		return nil, fmt.Errorf("%s: unexpected data after the metadata", source)
	}
	if record.Version != Version || record.Entries == nil {
		return nil, fmt.Errorf("%s: expected metadata version %d and an entries array", source, Version)
	}
	var fields struct {
		Entries []map[string]json.RawMessage `json:"entries"`
	}
	if err := json.Unmarshal(data, &fields); err != nil {
		return nil, err
	}
	for _, entry := range fields.Entries {
		required := []string{"relativePath", "type", "size", "mode", "executable"}
		if string(entry["type"]) == `"directory"` {
			if _, exists := entry["hash"]; exists {
				return nil, fmt.Errorf("%s: directory metadata must not have a hash", source)
			}
		} else {
			required = append(required, "hash")
		}
		for _, name := range required {
			value, exists := entry[name]
			if !exists || bytes.Equal(value, []byte("null")) {
				return nil, fmt.Errorf("%s: metadata entry requires %s", source, name)
			}
		}
	}
	entries, err := Merge(record.Entries)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", source, err)
	}
	return entries, nil
}

func Write(destination string, entries []Entry) error {
	entries, err := Merge(entries)
	if err != nil {
		return err
	}
	data, err := json.Marshal(manifest{Version: Version, Entries: entries})
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(destination), 0755); err != nil {
		return err
	}
	return os.WriteFile(destination, append(data, '\n'), 0644)
}

func Merge(groups ...[]Entry) ([]Entry, error) {
	byPath := make(map[string]Entry)
	spellings := make(map[string]string)
	links := make(map[string]string)
	for _, entries := range groups {
		for _, entry := range entries {
			if err := validateEntry(entry); err != nil {
				return nil, err
			}
			if previous, exists := byPath[entry.RelativePath]; exists && previous != entry {
				return nil, fmt.Errorf("conflicting metadata for %s", entry.RelativePath)
			}
			byPath[entry.RelativePath] = entry
			for prefix := entry.RelativePath; prefix != "."; prefix = path.Dir(prefix) {
				identity := PathIdentity(prefix)
				if previous, exists := spellings[identity]; exists && previous != prefix {
					return nil, fmt.Errorf("conflicting destinations: %s and %s", previous, prefix)
				}
				spellings[identity] = prefix
			}
			if entry.Type == "symlink" {
				links[entry.RelativePath] = entry.SymlinkTarget
			}
		}
	}
	result := make([]Entry, 0, len(byPath))
	for name, entry := range byPath {
		for parent := path.Dir(name); parent != "."; parent = path.Dir(parent) {
			if ancestor, exists := byPath[parent]; exists && ancestor.Type != "directory" {
				return nil, fmt.Errorf("conflicting destinations: %s contains %s", parent, name)
			}
		}
		result = append(result, entry)
	}
	if err := ValidateLinks(links); err != nil {
		return nil, err
	}
	slices.SortFunc(result, func(first, second Entry) int { return strings.Compare(first.RelativePath, second.RelativePath) })
	return result, nil
}

func ValidateLinks(links map[string]string) error {
	names := make([]string, 0, len(links))
	namesByIdentity := make(map[string]string, len(links))
	for name := range links {
		names = append(names, name)
	}
	slices.Sort(names)
	for _, name := range names {
		if err := ValidatePath(name); err != nil {
			return err
		}
		if err := validateLinkTarget(name, links[name]); err != nil {
			return err
		}
		identity := PathIdentity(name)
		if previous, exists := namesByIdentity[identity]; exists {
			return fmt.Errorf("conflicting link destinations: %s and %s", previous, name)
		}
		namesByIdentity[identity] = name
	}
	for _, name := range names {
		for parent := path.Dir(name); parent != "."; parent = path.Dir(parent) {
			if _, exists := namesByIdentity[PathIdentity(parent)]; exists {
				return fmt.Errorf("conflicting link destinations: %s contains %s", parent, name)
			}
		}
	}
	active := make(map[string]bool)
	resolved := make(map[string][]string)
	var resolve func([]string, []string) ([]string, error)
	resolve = func(parts, stack []string) ([]string, error) {
		for _, part := range parts {
			switch part {
			case "", ".":
				continue
			case "..":
				if len(stack) == 0 {
					return nil, fmt.Errorf("symbolic link chain escapes the directory")
				}
				stack = stack[:len(stack)-1]
			default:
				stack = append(stack, part)
				identity := PathIdentity(strings.Join(stack, "/"))
				name, exists := namesByIdentity[identity]
				if !exists {
					continue
				}
				if active[identity] {
					return nil, fmt.Errorf("symbolic link cycle at %s", name)
				}
				if cached, exists := resolved[identity]; exists {
					stack = slices.Clone(cached)
					continue
				}
				active[identity] = true
				parent := strings.Split(name, "/")
				var err error
				stack, err = resolve(strings.Split(links[name], "/"), parent[:len(parent)-1])
				if err != nil {
					return nil, err
				}
				delete(active, identity)
				resolved[identity] = slices.Clone(stack)
			}
		}
		return stack, nil
	}
	for _, name := range names {
		if _, err := resolve(strings.Split(name, "/"), nil); err != nil {
			return fmt.Errorf("%s: %w", name, err)
		}
	}
	return nil
}

func PathIdentity(value string) string {
	return norm.NFC.String(cases.Fold().String(norm.NFC.String(value)))
}

func ValidatePath(name string) error {
	if name == "" || strings.ContainsAny(name, "\\:\x00") {
		return fmt.Errorf("invalid relative path: %q", name)
	}
	for _, part := range strings.Split(name, "/") {
		if part == "" || part == "." || part == ".." {
			return fmt.Errorf("invalid relative path: %q", name)
		}
	}
	return nil
}

// ReadLinkTarget reads the target of the link at source. A relative target comes back in slash form, the form the
// metadata and the archives hold, because Windows stores it with backslashes. An absolute target keeps the host form.
func ReadLinkTarget(source string) (string, error) {
	target, err := os.Readlink(source)
	if err != nil || filepath.IsAbs(target) {
		return target, err
	}
	return filepath.ToSlash(target), nil
}

func validateEntry(entry Entry) error {
	if err := ValidatePath(entry.RelativePath); err != nil {
		return err
	}
	if entry.Size < 0 || entry.Mode > 0777 || entry.Executable != (entry.Type != "directory" && entry.Mode&0111 != 0) {
		return fmt.Errorf("invalid size or mode for %s", entry.RelativePath)
	}
	switch entry.Type {
	case "directory":
		if entry.Hash != 0 || entry.Size != 0 || entry.SymlinkTarget != "" {
			return fmt.Errorf("invalid directory metadata for %s", entry.RelativePath)
		}
	case "file":
		if entry.SymlinkTarget != "" {
			return fmt.Errorf("file metadata has a link target: %s", entry.RelativePath)
		}
	case "symlink":
		if entry.Hash != int64(xxh3.HashString(entry.SymlinkTarget)) || entry.Size != 0 || entry.Mode != 0 {
			return fmt.Errorf("invalid symbolic link metadata for %s", entry.RelativePath)
		}
		return validateLinkTarget(entry.RelativePath, entry.SymlinkTarget)
	default:
		return fmt.Errorf("unknown metadata type %q for %s", entry.Type, entry.RelativePath)
	}
	return nil
}

func validateLinkTarget(name, target string) error {
	if target == "" || strings.ContainsAny(target, "\\:\x00") || path.IsAbs(target) {
		return fmt.Errorf("invalid symbolic link target for %s", name)
	}
	resolved := path.Join(path.Dir(name), target)
	if resolved == ".." || strings.HasPrefix(resolved, "../") {
		return fmt.Errorf("symbolic link escapes the directory: %s", name)
	}
	return nil
}
