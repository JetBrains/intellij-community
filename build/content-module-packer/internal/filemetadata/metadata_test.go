package filemetadata

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

func TestDirectoryMetadataHasNoHashAndAllowsChildren(test *testing.T) {
	entries := []Entry{{RelativePath: "resources", Type: "directory", Mode: 0o750}, {RelativePath: "resources/empty", Type: "directory", Mode: 0o700}}
	metadata := filepath.Join(test.TempDir(), "metadata.json")
	if err := Write(metadata, entries); err != nil {
		test.Fatal(err)
	}
	data, err := os.ReadFile(metadata)
	if err != nil || strings.Contains(string(data), `"hash"`) {
		test.Fatalf("directory metadata has a hash: %s: %v", data, err)
	}
	actual, err := Read(metadata)
	if err != nil || !reflect.DeepEqual(actual, entries) {
		test.Fatalf("directory round trip: %+v: %v", actual, err)
	}
	for _, changed := range []Entry{
		{RelativePath: "resources", Type: "directory", Mode: 0o750, Hash: 1},
		{RelativePath: "resources", Type: "directory", Mode: 0o750, Size: 1},
		{RelativePath: "resources", Type: "directory", Mode: 0o750, Executable: true},
		{RelativePath: "resources", Type: "directory", Mode: 0o750, SymlinkTarget: "other"},
	} {
		if _, err := Merge([]Entry{changed}); err == nil {
			test.Fatalf("accepted invalid directory: %+v", changed)
		}
	}
	for _, child := range []Entry{
		{RelativePath: "Resources/child", Type: "directory", Mode: 0o755},
		{RelativePath: "resources", Type: "file", Mode: 0o644},
	} {
		if _, err := Merge(entries, []Entry{child}); err == nil {
			test.Fatalf("accepted conflicting directory: %+v", child)
		}
	}
	data = []byte(strings.Replace(string(data), `"type":"directory"`, `"type":"directory","hash":0`, 1))
	if err := os.WriteFile(metadata, data, 0o644); err != nil {
		test.Fatal(err)
	}
	if _, err := Read(metadata); err == nil {
		test.Fatal("accepted directory hash field")
	}
}

func TestKotlinHashVectors(t *testing.T) {
	for _, vector := range []struct {
		size int
		hash int64
	}{
		{0, 3244421341483603138},
		{1, -2399747073602280719},
		{3, -737883702129266468},
		{240, 2788469911834355041},
		{241, -4155630063455057979},
		{262143, 9078738661776034622},
		{262144, -1692254647099917537},
		{262145, -2541306581069977202},
		{524288, 3157545227256347297},
		{524301, 8144707773225287728},
	} {
		data := make([]byte, vector.size)
		for index := range data {
			data[index] = byte(index*31 + 7)
		}
		source := filepath.Join(t.TempDir(), "input.jar")
		if err := os.WriteFile(source, data, 0644); err != nil {
			t.Fatal(err)
		}
		actual, err := HashFile(source)
		if err != nil || actual != vector.hash {
			t.Fatalf("size %d: hash = %d, want %d; error = %v", vector.size, actual, vector.hash, err)
		}
	}
}

func TestInventoryAndMergeWithoutPayload(t *testing.T) {
	root := filepath.Join(t.TempDir(), "payload")
	if err := os.MkdirAll(filepath.Join(root, "nested"), 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "tool"), []byte("tool bytes"), 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("../tool", filepath.Join(root, "nested", "link")); err != nil {
		t.Fatal(err)
	}
	entries, err := Inventory(root)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 3 || entries[0].Type != "directory" || entries[1].Type != "symlink" || entries[1].SymlinkTarget != "../tool" || !entries[2].Executable || entries[2].Mode != 0755 {
		t.Fatalf("entries = %#v", entries)
	}
	metadata := filepath.Join(t.TempDir(), "files.json")
	if err := Write(metadata, entries); err != nil {
		t.Fatal(err)
	}
	if err := os.RemoveAll(root); err != nil {
		t.Fatal(err)
	}
	read, err := Read(metadata)
	if err != nil || !reflect.DeepEqual(read, entries) {
		t.Fatalf("entries = %#v, error = %v", read, err)
	}
	merged, err := Merge(read, entries)
	if err != nil || !reflect.DeepEqual(merged, entries) {
		t.Fatalf("merged = %#v, error = %v", merged, err)
	}
}

func TestMetadataRejectsConflictsAndUnsafePaths(t *testing.T) {
	entry := Entry{RelativePath: "lib/a.jar", Type: "file", Hash: 42, Size: 100, Mode: 0644}
	for _, field := range []string{"hash", "size", "mode", "child"} {
		t.Run(field, func(t *testing.T) {
			other := entry
			switch field {
			case "hash":
				other.Hash++
			case "size":
				other.Size++
			case "mode":
				other.Mode = 0600
			case "child":
				other.RelativePath += "/child"
			}
			if _, err := Merge([]Entry{entry}, []Entry{other}); err == nil || !strings.Contains(err.Error(), "conflicting") {
				t.Fatalf("conflict accepted: %v", err)
			}
		})
	}
	for _, name := range []string{"", "/lib/a.jar", "../a.jar", "lib/../a.jar", "lib/./a.jar", "lib//a.jar", `C:/a.jar`, `lib\a.jar`, "lib/a.jar/", "lib/\x00"} {
		if err := ValidatePath(name); err == nil {
			t.Errorf("unsafe path accepted: %q", name)
		}
	}
}

func TestReadRejectsInvalidMetadata(t *testing.T) {
	for _, text := range []string{
		`{"version":2,"entries":[]}`,
		`{"version":1,"entries":null}`,
		`{"version":1,"entries":[],"extra":true}`,
		`{"version":1,"entries":[]} {}`,
		`{"version":1,"entries":[{"relativePath":"a.jar","type":"file"}]}`,
		`{"version":1,"entries":[{"relativePath":"a.jar","type":"file","hash":1,"size":-1,"mode":420,"executable":false}]}`,
		`{"version":1,"entries":[{"relativePath":"a.jar","type":"file","hash":1,"size":1,"mode":493,"executable":false}]}`,
		`{"version":1,"entries":[{"relativePath":"a.jar","type":"unknown","hash":1,"size":1,"mode":420,"executable":false}]}`,
		"{\"version\":1,\"entries\":[],\"bad\":\"\xff\"}",
	} {
		source := filepath.Join(t.TempDir(), "metadata.json")
		if err := os.WriteFile(source, []byte(text), 0644); err != nil {
			t.Fatal(err)
		}
		if _, err := Read(source); err == nil {
			t.Errorf("invalid metadata accepted: %s", text)
		}
	}
}

func TestInventoryRejectsEscapingLinksAndSpecialRoots(t *testing.T) {
	for _, target := range []string{"../outside", "/outside", `C:/outside`, `nested\outside`} {
		root := t.TempDir()
		if err := os.Symlink(target, filepath.Join(root, "link")); err != nil {
			t.Fatal(err)
		}
		if _, err := Inventory(root); err == nil {
			t.Errorf("unsafe link accepted: %s", target)
		}
	}
	root := t.TempDir()
	link := filepath.Join(t.TempDir(), "linked-root")
	if err := os.Symlink(root, link); err != nil {
		t.Fatal(err)
	}
	if _, err := Inventory(link); err == nil {
		t.Fatal("accepted a symbolic link as the declared directory")
	}
}

func TestValidateLinksExpandsBeforeParentComponents(t *testing.T) {
	for _, test := range []struct {
		name    string
		links   map[string]string
		message string
	}{
		{"root alias escape", map[string]string{"current": ".", "escape": "current/../outside"}, "escapes"},
		{"case alias escape", map[string]string{"current": ".", "escape": "CURRENT/../outside"}, "escapes"},
		{"NFC alias escape", map[string]string{"caf\u00e9": ".", "escape": "cafe\u0301/../outside"}, "escapes"},
		{"NFD alias escape", map[string]string{"cafe\u0301": ".", "escape": "CAF\u00c9/../outside"}, "escapes"},
		{"full lowercase alias escape", map[string]string{"\u0130": ".", "escape": "i\u0307/../outside"}, "escapes"},
		{"Kelvin alias escape", map[string]string{"k": ".", "escape": "\u212a/../outside"}, "escapes"},
		{"Greek alias escape", map[string]string{"\u03c3": ".", "escape": "\u03c2/../outside"}, "escapes"},
		{"Greek alias chain escape", map[string]string{"\u03c3": ".", "middle": "\u03c2", "escape": "MIDDLE/../outside"}, "escapes"},
		{"nested case alias escape", map[string]string{"nested/current": "..", "escape": "NESTED/CURRENT/../outside"}, "escapes"},
		{"nested alias escape", map[string]string{"nested/current": "..", "nested/escape": "current/../outside"}, "escapes"},
		{"chain escape", map[string]string{"current": ".", "middle": "current", "escape": "middle/../outside"}, "escapes"},
		{"cycle before parent", map[string]string{"a": "b/../file", "b": "a"}, "cycle"},
		{"self cycle before parent", map[string]string{"a": "a/../file"}, "cycle"},
		{"nested cycle before parent", map[string]string{"a": "nested/b/../file", "nested/b": "../a"}, "cycle"},
		{"direct cycle", map[string]string{"a": "b", "b": "a"}, "cycle"},
		{"case alias cycle", map[string]string{"a": "B/../file", "b": "A"}, "cycle"},
		{"NFC alias cycle", map[string]string{"a": "BE\u0301/../file", "b\u00e9": "A"}, "cycle"},
		{"Greek alias cycle", map[string]string{"a": "\u03c2/../file", "\u03c3": "A"}, "cycle"},
		{"absolute target", map[string]string{"a": "/outside"}, "invalid"},
		{"escaping destination", map[string]string{"../a": "inside"}, "invalid"},
		{"link parent collision", map[string]string{"a": "inside", "a/b": "file"}, "conflicting"},
		{"case alias collision", map[string]string{"a": "inside", "A": "inside"}, "conflicting"},
		{"case alias parent collision", map[string]string{"a": "inside", "A/b": "file"}, "conflicting"},
		{"NFC alias collision", map[string]string{"caf\u00e9": "inside", "cafe\u0301": "inside"}, "conflicting"},
		{"NFC alias parent collision", map[string]string{"caf\u00e9": "inside", "CAFE\u0301/b": "file"}, "conflicting"},
	} {
		t.Run(test.name, func(t *testing.T) {
			if err := ValidateLinks(test.links); err == nil || !strings.Contains(err.Error(), test.message) {
				t.Fatalf("ValidateLinks(%v) = %v, want %s", test.links, err, test.message)
			}
		})
	}
}

func TestValidateLinksAcceptsSafeAliasesWithoutFilesystemAccess(t *testing.T) {
	for _, links := range []map[string]string{
		nil,
		{"current": "."},
		{"current": ".", "safe": "current/current/inside"},
		{"current": ".", "safe": "CURRENT/current/inside"},
		{"caf\u00e9": ".", "safe": "CAFE\u0301/caf\u00e9/inside"},
		{"\u03c3": ".", "safe": "\u03c2/\u03a3/inside"},
		{"caf\u00e9/current": "..", "safe": "CAFE\u0301/CURRENT/file"},
		{"\u65e5\u672c\u8a9e/current": "..", "safe": "\u65e5\u672c\u8a9e/current/file"},
		{"current": ".", "safe": "current-other/../inside"},
		{"current": "directory/nested", "safe": "current/../file"},
		{"nested/current": "..", "safe": "nested/current/file"},
		{"nested/current": "..", "safe": "NESTED/CURRENT/file"},
		{"alias": "./modules/../modules/separate.jar"},
		{"first": "second", "second": "third", "third": "missing-file"},
	} {
		if err := ValidateLinks(links); err != nil {
			t.Errorf("safe graph %v: %v", links, err)
		}
	}
}

func TestValidateLinksCachesSharedExpansions(t *testing.T) {
	for _, prefixes := range [][2]string{{"link", "LINK"}, {"l\u00ednk", "LI\u0301NK"}} {
		links := map[string]string{prefixes[0] + "200": "."}
		for index := 199; index >= 0; index-- {
			next := fmt.Sprintf("%s%03d", prefixes[0], index+1)
			alias := fmt.Sprintf("%s%03d", prefixes[1], index+1)
			links[fmt.Sprintf("%s%03d", prefixes[0], index)] = next + "/" + alias
		}
		if err := ValidateLinks(links); err != nil {
			t.Fatal(err)
		}
	}
}

func TestLinkGraphsAreValidatedAcrossMetadataBoundaries(t *testing.T) {
	for name, links := range map[string]map[string]string{
		"escape":       {"current": ".", "escape": "current/../outside"},
		"case escape":  {"current": ".", "escape": "CURRENT/../outside"},
		"NFC escape":   {"caf\u00e9": ".", "escape": "CAFE\u0301/../outside"},
		"Greek escape": {"\u03c3": ".", "escape": "\u03c2/../outside"},
		"cycle":        {"a": "b/../file", "b": "a"},
		"case cycle":   {"a": "B/../file", "b": "A"},
		"NFC cycle":    {"a": "BE\u0301/../file", "b\u00e9": "A"},
	} {
		t.Run(name, func(t *testing.T) {
			payload := t.TempDir()
			metadata := t.TempDir()
			var groups [][]Entry
			var combined []Entry
			for relativePath, target := range links {
				source := filepath.Join(payload, relativePath)
				if err := os.Symlink(target, source); err != nil {
					t.Fatal(err)
				}
				entry, err := Inspect(source, relativePath)
				if err != nil {
					t.Fatal(err)
				}
				group := []Entry{entry}
				if err := Write(filepath.Join(metadata, relativePath+".json"), group); err != nil {
					t.Fatalf("the isolated link must be valid: %v", err)
				}
				groups = append(groups, group)
				combined = append(combined, entry)
			}
			if _, err := Inventory(payload); err == nil {
				t.Fatal("inventory accepted an unsafe link graph")
			}
			if err := os.RemoveAll(payload); err != nil {
				t.Fatal(err)
			}
			for _, entries := range groups {
				if _, err := Read(filepath.Join(metadata, entries[0].RelativePath+".json")); err != nil {
					t.Fatalf("reading the isolated link requires no payload: %v", err)
				}
			}
			if _, err := Merge(groups...); err == nil {
				t.Fatal("merging inventories accepted an unsafe link graph")
			}
			destination := filepath.Join(metadata, "combined.json")
			if err := os.WriteFile(destination, []byte("unchanged"), 0644); err != nil {
				t.Fatal(err)
			}
			if err := Write(destination, combined); err == nil {
				t.Fatal("writing metadata accepted an unsafe link graph")
			}
			content, err := os.ReadFile(destination)
			if err != nil || string(content) != "unchanged" {
				t.Fatalf("a rejected graph changed the output: %q, %v", content, err)
			}
			data, err := json.Marshal(manifest{Version: Version, Entries: combined})
			if err != nil {
				t.Fatal(err)
			}
			if err := os.WriteFile(destination, data, 0644); err != nil {
				t.Fatal(err)
			}
			if _, err := Read(destination); err == nil {
				t.Fatal("reading metadata accepted an unsafe link graph")
			}
		})
	}
}

func TestPathIdentityNormalizesUnicodeAliases(t *testing.T) {
	for source, expected := range map[string]string{
		"CAFE\u0301":         "caf\u00e9",
		"\u212a":             "k",
		"\u0130":             "i\u0307",
		"\u039f\u03a3":       "\u03bf\u03c3",
		"\u03c2":             "\u03c3",
		"Stra\u00dfe":        "strasse",
		"\u65e5\u672c\u8a9e": "\u65e5\u672c\u8a9e",
	} {
		if actual := PathIdentity(source); actual != expected {
			t.Errorf("PathIdentity(%q) = %q, want %q", source, actual, expected)
		}
	}
}

func TestMetadataPreservesUnicodeLinkSpellings(t *testing.T) {
	for _, link := range []struct {
		name   string
		target string
	}{
		{"caf\u00e9", "."},
		{"cafe\u0301", "."},
		{"escape", "CAF\u00c9/../outside"},
		{"escape", "CAFE\u0301/../outside"},
		{"escape", "\u212a/../outside"},
		{"\u65e5\u672c\u8a9e", "lib/\u8cc7\u6e90"},
	} {
		t.Run(link.name+"-"+link.target, func(t *testing.T) {
			links := map[string]string{link.name: link.target}
			if err := ValidateLinks(links); err != nil {
				t.Fatalf("valid Unicode link path was rejected: %v", err)
			}
			payload := t.TempDir()
			source := filepath.Join(payload, link.name)
			if err := os.Symlink(link.target, source); err != nil {
				t.Fatal(err)
			}
			entry, err := Inspect(source, link.name)
			if err != nil {
				t.Fatal(err)
			}
			inventory, err := Inventory(payload)
			if err != nil || len(inventory) != 1 || inventory[0].SymlinkTarget != link.target {
				t.Fatalf("inventory changed the link target: %+v, %v", inventory, err)
			}
			if err := os.RemoveAll(payload); err != nil {
				t.Fatal(err)
			}
			entries := []Entry{entry}
			if merged, err := Merge(entries); err != nil || !reflect.DeepEqual(merged, entries) {
				t.Fatalf("merge changed the link spelling: %+v, %v", merged, err)
			}
			destination := filepath.Join(t.TempDir(), "metadata.json")
			if err := Write(destination, entries); err != nil {
				t.Fatal(err)
			}
			if actual, err := Read(destination); err != nil || !reflect.DeepEqual(actual, entries) {
				t.Fatalf("metadata changed the link spelling: %+v, %v", actual, err)
			}
		})
	}
}
