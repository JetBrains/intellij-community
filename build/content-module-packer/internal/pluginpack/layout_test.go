package pluginpack

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
	"encoding/binary"
	"hash/crc32"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"github.com/klauspost/compress/zstd"

	"jetbrains.com/content-module-packer/internal/filemetadata"
)

// layoutTreeRecipe pairs one layout-tree operation with one tree asset. Mode 0644 declares tree mode normalization.
func layoutTreeRecipe(destination string, mode uint32, layout LayoutAssets) Recipe {
	excluded := false
	return Recipe{Version: TreeVersion, Plugin: "layout", LayoutSignature: "layout-v2",
		Assets:     []Asset{{Destination: destination, Producer: "remainder", Kind: "tree", ClassPath: &excluded, NormalizeTreeModes: mode == 0o644}},
		Operations: []Operation{{Kind: "layout-tree", Destination: destination, Mode: mode, Layout: &layout}}}
}

// layoutJarRecipe packs one jar from one layout source.
func layoutJarRecipe(layout LayoutAssets) Recipe {
	return Recipe{Version: Version, Plugin: "layout", LayoutSignature: "layout-v1",
		Assets: []Asset{{Destination: "lib/layout.jar", Producer: "remainder"}},
		Operations: []Operation{{Kind: "jar", Destination: "lib/layout.jar", Options: &JarOptions{Directories: "none"},
			Sources: []Source{{Kind: "layout", Manifest: "keep", Layout: &layout}}}}}
}

// layoutFileRecipe writes one file at destination from one layout-file operation.
func layoutFileRecipe(destination string, mode uint32, layout LayoutAssets) Recipe {
	excluded := false
	return Recipe{Version: Version, Plugin: "layout", LayoutSignature: "layout-v1",
		Assets:     []Asset{{Destination: destination, Producer: "remainder", ClassPath: &excluded}},
		Operations: []Operation{{Kind: "layout-file", Destination: destination, Mode: mode, Layout: &layout}}}
}

// gzipXMLArchive is the transform that writes the .xml entries of its archives as .gzip jar entries.
func gzipXMLArchive() *LayoutTransform {
	return &LayoutTransform{Kind: "gzip-xml-archive"}
}

// readGzip decompresses one gzip member.
func readGzip(t *testing.T, content string) string {
	t.Helper()
	reader, err := gzip.NewReader(strings.NewReader(content))
	if err != nil {
		t.Fatal(err)
	}
	decompressed, err := io.ReadAll(reader)
	if err != nil {
		t.Fatal(err)
	}
	return string(decompressed)
}

func fileArtifact(id, root string) Artifact {
	return Artifact{ID: id, Kind: "file", Root: root}
}

func directoryArtifact(id, root string) Artifact {
	return Artifact{ID: id, Kind: "directory", Root: root}
}

func archiveTree(strip int, mappings ...LayoutMapping) *LayoutTransform {
	return &LayoutTransform{Kind: "archive-tree", StripComponents: strip, Mappings: mappings}
}

func treeMap(mappings ...LayoutMapping) *LayoutTransform {
	return &LayoutTransform{Kind: "tree-map", Mappings: mappings}
}

// writeLayoutFailure plans the recipe, expects Write to fail with the message, and expects no published output.
func writeLayoutFailure(t *testing.T, recipe Recipe, catalogue Catalogue, message string) {
	t.Helper()
	execution, err := Plan(recipe, catalogue)
	if err != nil {
		t.Fatal(err)
	}
	root := t.TempDir()
	err = execution.Write(filepath.Join(root, "output"), filepath.Join(root, "inventory.json"))
	if err == nil || !strings.Contains(err.Error(), message) {
		t.Fatalf("expected a failure containing %q, got %v", message, err)
	}
	assertNoPublishedOutputs(t, root)
}

func assertContent(t *testing.T, file, want string) {
	t.Helper()
	info, err := os.Lstat(file)
	if err != nil || !info.Mode().IsRegular() {
		t.Fatalf("%s is not a regular file: %v", file, err)
	}
	if got := string(readTestFile(t, file)); got != want {
		t.Fatalf("%s holds %q, want %q", file, got, want)
	}
}

func assertMode(t *testing.T, file string, want os.FileMode) {
	t.Helper()
	info, err := os.Lstat(file)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != want {
		t.Fatalf("%s has mode %o, want %o", file, info.Mode().Perm(), want)
	}
}

func assertLink(t *testing.T, file, want string) {
	t.Helper()
	target, err := os.Readlink(file)
	if err != nil || target != want {
		t.Fatalf("%s links to %q (%v), want %q", file, target, err, want)
	}
}

func assertAbsent(t *testing.T, file string) {
	t.Helper()
	if _, err := os.Lstat(file); !os.IsNotExist(err) {
		t.Fatalf("%s exists: %v", file, err)
	}
}

type tarTestEntry struct {
	name    string
	content string
	mode    int64
	link    string
}

func writeTarGz(t *testing.T, file string, entries ...tarTestEntry) {
	t.Helper()
	var buffer bytes.Buffer
	compressor := gzip.NewWriter(&buffer)
	writer := tar.NewWriter(compressor)
	for _, entry := range entries {
		header := &tar.Header{Name: entry.name, Mode: entry.mode, Typeflag: tar.TypeReg, Size: int64(len(entry.content))}
		if entry.link != "" {
			header.Typeflag, header.Linkname, header.Size = tar.TypeSymlink, entry.link, 0
		}
		if strings.HasSuffix(entry.name, "/") {
			header.Typeflag, header.Size = tar.TypeDir, 0
		}
		if err := writer.WriteHeader(header); err != nil {
			t.Fatal(err)
		}
		if _, err := writer.Write([]byte(entry.content)); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := compressor.Close(); err != nil {
		t.Fatal(err)
	}
	writeTestFile(t, file, buffer.Bytes())
}

// zipTestEntry states the creator platform of one entry. Creator 3 is Unix. Creator 19 is the MacOSX platform, and
// both readers treat it as Unix through its low nibble. Creator 10 is the Windows NTFS platform. It carries no mode
// and no link. An entry is STORED unless deflate is set.
type zipTestEntry struct {
	name    string
	content string
	mode    uint32
	symlink bool
	creator uint16
	deflate bool
}

func zipTestBytes(t *testing.T, entries ...zipTestEntry) []byte {
	t.Helper()
	var buffer bytes.Buffer
	writer := zip.NewWriter(&buffer)
	for _, entry := range entries {
		header := &zip.FileHeader{Name: entry.name, Method: zip.Store}
		if entry.deflate {
			header.Method = zip.Deflate
		}
		if entry.creator != 0 {
			unixMode := entry.mode | 0o100000
			if entry.symlink {
				unixMode = entry.mode | 0o120000
			}
			header.CreatorVersion = entry.creator << 8
			header.ExternalAttrs = unixMode << 16
		}
		output, err := writer.CreateHeader(header)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := output.Write([]byte(entry.content)); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

func writeZip(t *testing.T, file string, entries ...zipTestEntry) {
	t.Helper()
	writeTestFile(t, file, zipTestBytes(t, entries...))
}

// writeZstd writes the payload as one zstd frame.
func writeZstd(t *testing.T, file string, payload []byte) {
	t.Helper()
	var buffer bytes.Buffer
	encoder, err := zstd.NewWriter(&buffer)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := encoder.Write(payload); err != nil {
		t.Fatal(err)
	}
	if err := encoder.Close(); err != nil {
		t.Fatal(err)
	}
	writeTestFile(t, file, buffer.Bytes())
}

func transportLink(t *testing.T, link, backing string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(link), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(backing, link); err != nil {
		t.Fatal(err)
	}
}

// TestLayoutAssetsMatchTheKotlinExecutorCases holds one case per executor case of DevPluginLayoutAssetPreparationTest
// under the same name.
func TestLayoutAssetsMatchTheKotlinExecutorCases(t *testing.T) {
	t.Run("archive mappings select the first matching candidate and preserve the file mode", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "assets.tar.gz")
		writeTarGz(t, archive, tarTestEntry{name: "fallback/bin/tool", content: "fallback", mode: 0o644},
			tarTestEntry{name: "candidate/bin/tool", content: "selected", mode: 0o751})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0},
			Transform: archiveTree(0, LayoutMapping{Pattern: "candidate/**", StripComponents: 1}, LayoutMapping{})}}}
		output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}})
		assertContent(t, filepath.Join(output, "payload/bin/tool"), "selected")
		assertMode(t, filepath.Join(output, "payload/bin/tool"), 0o751)
		assertAbsent(t, filepath.Join(output, "payload/fallback"))
	})
	t.Run("archive modes remove group write and keep executable bits", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "assets.tar.gz")
		writeTarGz(t, archive, tarTestEntry{name: "data.txt", content: "data", mode: 0o664}, tarTestEntry{name: "bin/tool", content: "tool", mode: 0o751})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}
		output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}})
		assertMode(t, filepath.Join(output, "payload/data.txt"), 0o644)
		assertMode(t, filepath.Join(output, "payload/bin/tool"), 0o751)
	})
	t.Run("archive links remain links and cannot escape the prepared tree", func(t *testing.T) {
		root := t.TempDir()
		safe := filepath.Join(root, "safe.tar.gz")
		writeTarGz(t, safe, tarTestEntry{name: "bin/tool", content: "tool", mode: 0o755}, tarTestEntry{name: "bin/current", link: "tool"})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}
		output, inventory := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", safe)}})
		assertLink(t, filepath.Join(output, "payload/bin/current"), "tool")
		if !slices.ContainsFunc(inventory, func(entry filemetadata.Entry) bool {
			return entry.RelativePath == "payload/bin/current" && entry.Type == "symlink"
		}) {
			t.Fatalf("inventory misses the link: %+v", inventory)
		}
		unsafe := filepath.Join(root, "unsafe-link.tar.gz")
		writeTarGz(t, unsafe, tarTestEntry{name: "bin/current", link: "../../outside"})
		writeLayoutFailure(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", unsafe)}}, "escapes its tree")
	})
	t.Run("zip zstd archives supply selected terminal files", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "terminal.zip.zst")
		writeZstd(t, archive, zipTestBytes(t,
			zipTestEntry{name: "linux-x64/libghostty.so", content: "native", mode: 0o755, creator: 3},
			zipTestEntry{name: "linux-x64/libghostty.so.1", content: "libghostty.so", mode: 0o777, symlink: true, creator: 3},
			zipTestEntry{name: "darwin-aarch64/libghostty.dylib", content: "other", mode: 0o755, creator: 3}))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0},
			Transform: archiveTree(0, LayoutMapping{Pattern: "linux-x64/**", StripComponents: 1})}}}
		output, _ := writeExecution(t, layoutTreeRecipe("terminal", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}})
		assertContent(t, filepath.Join(output, "terminal/libghostty.so"), "native")
		assertMode(t, filepath.Join(output, "terminal/libghostty.so"), 0o644)
		assertContent(t, filepath.Join(output, "terminal/libghostty.so.1"), "libghostty.so")
		assertMode(t, filepath.Join(output, "terminal/libghostty.so.1"), 0o644)
		assertAbsent(t, filepath.Join(output, "terminal/libghostty.dylib"))
	})
	t.Run("archive preparation accepts a Bazel transport link", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "backing/assets.zip")
		writeZip(t, archive, zipTestEntry{name: "payload.txt", content: "payload"})
		transport := filepath.Join(root, "transport")
		transportLink(t, filepath.Join(transport, "assets.zip"), archive)
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive", Path: "assets.zip"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}
		output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("archive", transport)}})
		assertContent(t, filepath.Join(output, "payload/payload.txt"), "payload")
	})
	t.Run("archive preparation rejects a mismatched transport path", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "backing/other.zip")
		writeZip(t, archive, zipTestEntry{name: "payload.txt", content: "payload"})
		transport := filepath.Join(root, "transport")
		transportLink(t, filepath.Join(transport, "assets.zip"), archive)
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive", Path: "assets.zip"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}
		writeLayoutFailure(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("archive", transport)}}, "path conflicts")
	})
	t.Run("inline text produces one exact file without inputs", func(t *testing.T) {
		layout := LayoutAssets{Assets: []LayoutAsset{{Destination: "jre-build.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "21.0.7"}}}}
		output, _ := writeExecution(t, layoutJarRecipe(layout), Catalogue{Version: Version})
		names, entries := readArchive(t, filepath.Join(output, "lib/layout.jar"))
		if !slices.Equal(names, []string{"jre-build.txt", "__index__"}) || entries["jre-build.txt"] != "21.0.7" {
			t.Fatalf("jar entries differ: %v %v", names, entries)
		}
		output, _ = writeExecution(t, layoutTreeRecipe("jbr", 0, layout), Catalogue{Version: Version})
		assertContent(t, filepath.Join(output, "jbr/jre-build.txt"), "21.0.7")
		assertMode(t, filepath.Join(output, "jbr/jre-build.txt"), 0o644)
	})
	t.Run("tree mappings use declaration order and first source precedence", func(t *testing.T) {
		root := t.TempDir()
		first, second := filepath.Join(root, "first-tree"), filepath.Join(root, "second-tree")
		writeTestFile(t, filepath.Join(first, "jackson-core.jar"), []byte("first"))
		writeTestFile(t, filepath.Join(first, "ignored.txt"), []byte("ignored"))
		writeTestFile(t, filepath.Join(second, "jackson-core.jar"), []byte("second"))
		writeTestFile(t, filepath.Join(second, "jackson-data.jar"), []byte("data"))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "first"}, {Artifact: "second"}}, Assets: []LayoutAsset{{Destination: "lib", Sources: []int{0, 1},
			Transform: treeMap(LayoutMapping{Pattern: "jackson-*.jar"}, LayoutMapping{Pattern: "**", Destination: "fallback"})}}}
		catalogue := Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("first", first), directoryArtifact("second", second)}}
		output, _ := writeExecution(t, layoutTreeRecipe("libraries", 0, layout), catalogue)
		assertContent(t, filepath.Join(output, "libraries/lib/jackson-core.jar"), "first")
		assertContent(t, filepath.Join(output, "libraries/lib/jackson-data.jar"), "data")
		assertContent(t, filepath.Join(output, "libraries/lib/fallback/ignored.txt"), "ignored")
	})
	t.Run("declared mode overrides executable transport modes", func(t *testing.T) {
		root := t.TempDir()
		direct, tree := filepath.Join(root, "direct.jar"), filepath.Join(root, "tree")
		writeTestFile(t, direct, []byte("direct"))
		writeTestFile(t, filepath.Join(tree, "mapped.jar"), []byte("mapped"))
		for _, file := range []string{direct, filepath.Join(tree, "mapped.jar")} {
			if err := os.Chmod(file, 0o755); err != nil {
				t.Fatal(err)
			}
		}
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "direct"}, {Artifact: "tree"}}, Assets: []LayoutAsset{
			{Destination: "direct.jar", Sources: []int{0}, Mode: 0o644},
			{Destination: "mapped", Sources: []int{1}, Transform: treeMap(LayoutMapping{Pattern: "*.jar"}), Mode: 0o644},
		}}
		catalogue := Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("direct", direct), directoryArtifact("tree", tree)}}
		output, _ := writeExecution(t, layoutTreeRecipe("libraries", 0, layout), catalogue)
		assertMode(t, filepath.Join(output, "libraries/direct.jar"), 0o644)
		assertMode(t, filepath.Join(output, "libraries/mapped/mapped.jar"), 0o644)
	})
	t.Run("tree mappings materialize Bazel transport links", func(t *testing.T) {
		root := t.TempDir()
		writeTestFile(t, filepath.Join(root, "backing/nested/resource.txt"), []byte("resource"))
		transport := filepath.Join(root, "transport")
		transportLink(t, filepath.Join(transport, "nested/resource.txt"), filepath.Join(root, "backing/nested/resource.txt"))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: treeMap(LayoutMapping{})}}}
		output, _ := writeExecution(t, layoutTreeRecipe("resources", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", transport)}})
		assertContent(t, filepath.Join(output, "resources/nested/resource.txt"), "resource")
	})
	t.Run("direct tree copies materialize Bazel transport links", func(t *testing.T) {
		root := t.TempDir()
		writeTestFile(t, filepath.Join(root, "backing/nested/resource.txt"), []byte("resource"))
		transport := filepath.Join(root, "transport")
		transportLink(t, filepath.Join(transport, "nested/resource.txt"), filepath.Join(root, "backing/nested/resource.txt"))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}}}}
		output, _ := writeExecution(t, layoutTreeRecipe("resources", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", transport)}})
		assertContent(t, filepath.Join(output, "resources/nested/resource.txt"), "resource")
	})
	t.Run("direct tree copies preserve relative links", func(t *testing.T) {
		root := t.TempDir()
		source := filepath.Join(root, "source")
		writeTestFile(t, filepath.Join(source, "resource.txt"), []byte("resource"))
		if err := os.Symlink("resource.txt", filepath.Join(source, "current.txt")); err != nil {
			t.Fatal(err)
		}
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}}}}
		output, _ := writeExecution(t, layoutTreeRecipe("resources", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", source)}})
		assertLink(t, filepath.Join(output, "resources/current.txt"), "resource.txt")
	})
	t.Run("direct tree copies reject a mismatched transport path", func(t *testing.T) {
		root := t.TempDir()
		writeTestFile(t, filepath.Join(root, "backing/other/resource.txt"), []byte("resource"))
		transport := filepath.Join(root, "transport")
		transportLink(t, filepath.Join(transport, "nested/resource.txt"), filepath.Join(root, "backing/other/resource.txt"))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}}}}
		writeLayoutFailure(t, layoutTreeRecipe("resources", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", transport)}}, "path conflicts")
	})
	t.Run("localization mappings include only supported resource directories", func(t *testing.T) {
		root := t.TempDir()
		source := filepath.Join(root, "source")
		included := []string{
			"intellij.platform.lang/fileTemplates/empty/description.html",
			"intellij.platform.lang/inspectionDescriptions/Unused/description.html",
			"intellij.platform.lang/intentionDescriptions/Convert/description.html",
			"intellij.platform.lang/postfixTemplates/assert/description.html",
		}
		excluded := []string{
			"intellij.tide.impl/intensionDescriptions/Misspelled/description.html",
			"intellij.platform.lang/com/intellij/package.html",
			"intellij.platform.lang.impl/com/intellij/codeInsight/templates/first.template",
			"intellij.platform.lang.impl/com/intellij/codeInsight/templates/second.template",
		}
		for _, name := range slices.Concat(included, excluded) {
			writeTestFile(t, filepath.Join(source, filepath.FromSlash(name)), []byte("ja:"+name))
		}
		var mappings []LayoutMapping
		for _, directory := range []string{"fileTemplates", "inspectionDescriptions", "intentionDescriptions", "postfixTemplates"} {
			mappings = append(mappings, LayoutMapping{Pattern: "*/" + directory + "/**", StripComponents: 1})
		}
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "localization"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: treeMap(mappings...)}}}
		output, inventory := writeExecution(t, layoutTreeRecipe("localization", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("localization", source)}})
		for _, name := range included {
			_, relative, _ := strings.Cut(name, "/")
			assertContent(t, filepath.Join(output, "localization", filepath.FromSlash(relative)), "ja:"+name)
		}
		for _, entry := range inventory {
			if entry.Type == "file" && !strings.Contains(entry.RelativePath, "Descriptions/") && !strings.Contains(entry.RelativePath, "Templates/") {
				t.Fatalf("copied an excluded file: %s", entry.RelativePath)
			}
		}
	})
	t.Run("unsafe archive paths fail before writing outside the prepared tree", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "unsafe.zip")
		writeZip(t, archive, zipTestEntry{name: "../outside", content: "bad"})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}
		writeLayoutFailure(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}}, "unsafe archive path")
		assertAbsent(t, filepath.Join(root, "outside"))
	})
	t.Run("gzip XML archives accept JAR files and keep source order", func(t *testing.T) {
		root := t.TempDir()
		first, second := filepath.Join(root, "first.jar"), filepath.Join(root, "second.zip")
		writeZip(t, first, zipTestEntry{name: "a.xml", content: "a"}, zipTestEntry{name: "same.xml", content: "first"}, zipTestEntry{name: "dir/"})
		writeZip(t, second, zipTestEntry{name: "b.xml", content: "b"}, zipTestEntry{name: "same.xml", content: "second"})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "first"}, {Artifact: "second"}},
			Assets: []LayoutAsset{{Destination: "resources", Sources: []int{0, 1}, Transform: gzipXMLArchive()}}}
		output, _ := writeExecution(t, layoutJarRecipe(layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("first", first), fileArtifact("second", second)}})
		names, entries := readArchive(t, filepath.Join(output, "lib/layout.jar"))
		if !slices.Equal(names, []string{"resources/a.xml.gzip", "resources/same.xml.gzip", "resources/b.xml.gzip", "__index__"}) {
			t.Fatalf("jar entries differ: %v", names)
		}
		for name, want := range map[string]string{"resources/a.xml.gzip": "a", "resources/same.xml.gzip": "first", "resources/b.xml.gzip": "b"} {
			if got := readGzip(t, entries[name]); got != want {
				t.Fatalf("%s decompresses to %q, want %q", name, got, want)
			}
		}
		if header := entries["resources/a.xml.gzip"][:10]; header != "\x1f\x8b\x08\x00\x00\x00\x00\x00\x00\xff" {
			t.Fatalf("the gzip header carries a name, a time, or an extra flag: %x", header)
		}
	})
	t.Run("gzip XML archives keep the deflate stream of the source entry", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "resources.jar")
		large := strings.Repeat("<row/>", 20000)
		writeZip(t, archive, zipTestEntry{name: "deflated.xml", content: large, deflate: true}, zipTestEntry{name: "stored.xml", content: large},
			zipTestEntry{name: "empty.xml"})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Destination: "resources", Sources: []int{0}, Transform: gzipXMLArchive()}}}
		output, _ := writeExecution(t, layoutJarRecipe(layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}})
		_, entries := readArchive(t, filepath.Join(output, "lib/layout.jar"))
		trailer := binary.LittleEndian.AppendUint32(nil, crc32.ChecksumIEEE([]byte(large)))
		trailer = binary.LittleEndian.AppendUint32(trailer, uint32(len(large)))
		source, err := zip.OpenReader(archive)
		if err != nil {
			t.Fatal(err)
		}
		defer source.Close()
		raw, err := source.File[0].OpenRaw()
		if err != nil {
			t.Fatal(err)
		}
		deflated, err := io.ReadAll(raw)
		if err != nil {
			t.Fatal(err)
		}
		if got, want := entries["resources/deflated.xml.gzip"], string(gzipMemberHeader)+string(deflated)+string(trailer); got != want {
			t.Fatalf("the deflated member is not the source stream: %d bytes, want %d", len(got), len(want))
		}
		if got := readGzip(t, entries["resources/deflated.xml.gzip"]); got != large {
			t.Fatal("the deflated member decompresses to another payload")
		}
		stored := entries["resources/stored.xml.gzip"]
		if got, want := stored[len(stored)-8:], string(trailer); got != want {
			t.Fatalf("the stored member trailer is %x, want %x", got, want)
		}
		if len(stored) != len(gzipMemberHeader)+len(large)+2*5+8 {
			t.Fatalf("the stored member holds %d bytes, want two stored blocks", len(stored))
		}
		if got := readGzip(t, stored); got != large {
			t.Fatal("the stored member decompresses to another payload")
		}
		if got, want := entries["resources/empty.xml.gzip"], string(gzipMemberHeader)+"\x01\x00\x00\xff\xff"+"\x00\x00\x00\x00\x00\x00\x00\x00"; got != want {
			t.Fatalf("the empty member is %x, want %x", got, want)
		}
		if got := readGzip(t, entries["resources/empty.xml.gzip"]); got != "" {
			t.Fatalf("the empty member decompresses to %q", got)
		}
	})
	t.Run("gzip XML archives read zip and jar archives only", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "resources.tar.gz")
		writeTarGz(t, archive, tarTestEntry{name: "a.xml", content: "a", mode: 0o644})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Destination: "resources", Sources: []int{0}, Transform: gzipXMLArchive()}}}
		writeLayoutFailure(t, layoutJarRecipe(layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}}, "reads a zip or jar archive")
	})
	t.Run("gzip XML archives reject an entry that is not XML", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "resources.jar")
		writeZip(t, archive, zipTestEntry{name: "a.xml", content: "a"}, zipTestEntry{name: "notes.txt", content: "text"})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Destination: "resources", Sources: []int{0}, Transform: gzipXMLArchive()}}}
		writeLayoutFailure(t, layoutJarRecipe(layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}}, `unexpected file "notes.txt"`)
		linked := filepath.Join(root, "linked.jar")
		writeZip(t, linked, zipTestEntry{name: "a.xml", content: "b.xml", mode: 0o777, symlink: true, creator: 3})
		writeLayoutFailure(t, layoutJarRecipe(layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", linked)}}, `unexpected file "a.xml"`)
	})
	t.Run("a layout file holds inline text or one copied file", func(t *testing.T) {
		root := t.TempDir()
		inline := LayoutAssets{Assets: []LayoutAsset{{Destination: "jre-build.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "21.0.7"}}}}
		output, inventory := writeExecution(t, layoutFileRecipe("jre-build.txt", 0o644, inline), Catalogue{Version: Version})
		assertContent(t, filepath.Join(output, "jre-build.txt"), "21.0.7")
		assertMode(t, filepath.Join(output, "jre-build.txt"), 0o644)
		if len(inventory) != 1 || inventory[0].RelativePath != "jre-build.txt" || inventory[0].Type != "file" {
			t.Fatalf("inventory differs: %+v", inventory)
		}
		source := filepath.Join(root, "build.txt")
		writeTestFile(t, source, []byte("build"))
		if err := os.Chmod(source, 0o755); err != nil {
			t.Fatal(err)
		}
		copied := LayoutAssets{Inputs: []Reference{{Artifact: "build"}}, Assets: []LayoutAsset{{Destination: "bin/jre-build.txt", Sources: []int{0}}}}
		output, _ = writeExecution(t, layoutFileRecipe("bin/jre-build.txt", 0, copied), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("build", source)}})
		assertContent(t, filepath.Join(output, "bin/jre-build.txt"), "build")
		assertMode(t, filepath.Join(output, "bin/jre-build.txt"), 0o755)
		directory := filepath.Join(root, "tree")
		writeTestFile(t, filepath.Join(directory, "member.txt"), []byte("member"))
		if err := os.Symlink("member.txt", filepath.Join(directory, "link.txt")); err != nil {
			t.Fatal(err)
		}
		link := LayoutAssets{Inputs: []Reference{{Artifact: "tree", Path: "link.txt"}}, Assets: []LayoutAsset{{Destination: "jre-build.txt", Sources: []int{0}}}}
		writeLayoutFailure(t, layoutFileRecipe("jre-build.txt", 0, link), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", directory)}}, "cannot be the symbolic link")
	})
}

// TestLayoutArchiveReadersFollowTheKotlinReaderRules pins the zip creator rule, the streamed .zip.zst flattening,
// the link target spelling, and the duplicate policy of each reader.
func TestLayoutArchiveReadersFollowTheKotlinReaderRules(t *testing.T) {
	t.Run("a tar link target loses its trailing and repeated slashes", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "assets.tar.gz")
		writeTarGz(t, archive, tarTestEntry{name: "bin/tool", content: "tool", mode: 0o755},
			tarTestEntry{name: "bin/current", link: "tool/"}, tarTestEntry{name: "bin/latest", link: ".//tool"})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}
		output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}})
		assertLink(t, filepath.Join(output, "payload/bin/current"), "tool")
		assertLink(t, filepath.Join(output, "payload/bin/latest"), "./tool")
	})
	t.Run("a Unix creator carries modes and a relative link", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "assets.zip")
		writeZip(t, archive,
			zipTestEntry{name: "bin/", creator: 3, mode: 0o775},
			zipTestEntry{name: "bin/tool", content: "tool", mode: 0o775, creator: 3},
			zipTestEntry{name: "data.txt", content: "data", mode: 0o664, creator: 3},
			zipTestEntry{name: "bin/current", content: "tool", mode: 0o777, symlink: true, creator: 3})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}
		output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}})
		assertMode(t, filepath.Join(output, "payload/bin"), 0o755)
		assertMode(t, filepath.Join(output, "payload/bin/tool"), 0o755)
		assertMode(t, filepath.Join(output, "payload/data.txt"), 0o644)
		assertLink(t, filepath.Join(output, "payload/bin/current"), "tool")
	})
	t.Run("a non-Unix creator carries no mode and no link", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "assets.zip")
		writeZip(t, archive,
			zipTestEntry{name: "bin/tool", content: "tool", mode: 0o755, creator: 10},
			zipTestEntry{name: "bin/current", content: "tool", mode: 0o777, symlink: true, creator: 10})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}
		output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}})
		assertMode(t, filepath.Join(output, "payload/bin/tool"), 0o644)
		assertContent(t, filepath.Join(output, "payload/bin/current"), "tool")
		assertMode(t, filepath.Join(output, "payload/bin/current"), 0o644)
	})
	t.Run("a zip keeps the first entry of a shared destination", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "assets.zip")
		writeZip(t, archive, zipTestEntry{name: "a/x", content: "first"}, zipTestEntry{name: "b/x", content: "second"})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(1)}}}
		output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}})
		assertContent(t, filepath.Join(output, "payload/x"), "first")
	})
	t.Run("a zip zstd archive fails on a shared destination", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "assets.zip.zst")
		writeZstd(t, archive, zipTestBytes(t, zipTestEntry{name: "a/x", content: "first"}, zipTestEntry{name: "b/x", content: "second"}))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(1)}}}
		writeLayoutFailure(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}}, "duplicate archive destination")
	})
	t.Run("a strip count drops the entries above it", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "jcef.tar.gz")
		writeTarGz(t, archive, tarTestEntry{name: "jcef/", mode: 0o755}, tarTestEntry{name: "jcef/lib/", mode: 0o755}, tarTestEntry{name: "jcef/lib/libcef.so", content: "cef", mode: 0o644})
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(1)}}}
		output, inventory := writeExecution(t, layoutTreeRecipe("", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}})
		assertContent(t, filepath.Join(output, "lib/libcef.so"), "cef")
		assertAbsent(t, filepath.Join(output, "jcef"))
		if len(inventory) != 2 {
			t.Fatalf("inventory differs: %+v", inventory)
		}
	})
	t.Run("an unsupported archive name fails", func(t *testing.T) {
		root := t.TempDir()
		archive := filepath.Join(root, "assets.7z")
		writeTestFile(t, archive, []byte("not an archive"))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}
		writeLayoutFailure(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}}, "unsupported layout archive")
	})
}

// TestLayoutTreeWriterAndEntriesWriterRules pins first-claim-wins, the kind conflict, the entries order, and the
// tree mode normalization of a layout-tree.
func TestLayoutTreeWriterAndEntriesWriterRules(t *testing.T) {
	t.Run("the first claim wins across assets and a kind conflict fails", func(t *testing.T) {
		root := t.TempDir()
		first, second := filepath.Join(root, "first"), filepath.Join(root, "second")
		writeTestFile(t, filepath.Join(first, "shared.txt"), []byte("first"))
		writeTestFile(t, filepath.Join(second, "shared.txt"), []byte("second"))
		writeTestFile(t, filepath.Join(second, "only.txt"), []byte("only"))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "first"}, {Artifact: "second"}}, Assets: []LayoutAsset{{Sources: []int{0}}, {Sources: []int{1}}}}
		catalogue := Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("first", first), directoryArtifact("second", second)}}
		output, _ := writeExecution(t, layoutTreeRecipe("overlay", 0, layout), catalogue)
		assertContent(t, filepath.Join(output, "overlay/shared.txt"), "first")
		assertContent(t, filepath.Join(output, "overlay/only.txt"), "only")

		if err := os.Mkdir(filepath.Join(second, "conflict"), 0o755); err != nil {
			t.Fatal(err)
		}
		writeTestFile(t, filepath.Join(first, "conflict"), []byte("a file"))
		writeLayoutFailure(t, layoutTreeRecipe("overlay", 0, layout), catalogue, "conflicts with a file")
	})
	t.Run("a layout-tree with mode 0644 clears group write", func(t *testing.T) {
		root := t.TempDir()
		source := filepath.Join(root, "source")
		writeTestFile(t, filepath.Join(source, "bin/tool"), []byte("tool"))
		for name, mode := range map[string]os.FileMode{"bin": 0o775, "bin/tool": 0o775} {
			if err := os.Chmod(filepath.Join(source, name), mode); err != nil {
				t.Fatal(err)
			}
		}
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}}}}
		catalogue := Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", source)}}
		output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), catalogue)
		assertMode(t, filepath.Join(output, "payload/bin"), 0o775)
		assertMode(t, filepath.Join(output, "payload/bin/tool"), 0o775)
		output, _ = writeExecution(t, layoutTreeRecipe("payload", 0o644, layout), catalogue)
		assertMode(t, filepath.Join(output, "payload/bin"), 0o755)
		assertMode(t, filepath.Join(output, "payload/bin/tool"), 0o755)
	})
	t.Run("layout entries take the first destination, skip directories, and refuse a link", func(t *testing.T) {
		root := t.TempDir()
		first, second := filepath.Join(root, "first"), filepath.Join(root, "second")
		writeTestFile(t, filepath.Join(first, "messages/Bundle.properties"), []byte("first"))
		writeTestFile(t, filepath.Join(second, "messages/Bundle.properties"), []byte("second"))
		writeTestFile(t, filepath.Join(second, "messages/Other.properties"), []byte("other"))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "first"}, {Artifact: "second"}}, Assets: []LayoutAsset{
			{Sources: []int{0, 1}, Transform: treeMap(LayoutMapping{})},
		}}
		catalogue := Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("first", first), directoryArtifact("second", second)}}
		output, _ := writeExecution(t, layoutJarRecipe(layout), catalogue)
		names, entries := readArchive(t, filepath.Join(output, "lib/layout.jar"))
		if !slices.Equal(names, []string{"messages/Bundle.properties", "messages/Other.properties", "__index__"}) || entries["messages/Bundle.properties"] != "first" {
			t.Fatalf("jar entries differ: %v %v", names, entries)
		}
		if err := os.Symlink("Bundle.properties", filepath.Join(second, "messages/Current.properties")); err != nil {
			t.Fatal(err)
		}
		writeLayoutFailure(t, layoutJarRecipe(layout), catalogue, "cannot contain the symbolic link")
	})
	t.Run("the scratch directory is gone after a write", func(t *testing.T) {
		root := t.TempDir()
		writeTestFile(t, filepath.Join(root, "source/resource.txt"), []byte("resource"))
		layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}}}}
		output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", filepath.Join(root, "source"))}})
		leftovers, err := filepath.Glob(filepath.Join(filepath.Dir(output), ".plugin-layout-*"))
		if err != nil || len(leftovers) != 0 {
			t.Fatalf("left a layout scratch directory: %v %v", leftovers, err)
		}
	})
}

func TestLayoutTreeMapExcludes(t *testing.T) {
	files := []string{
		"root.pyc", "root.pyo", "keep.py", "nested/cache.pyc", "nested/deep/cache.pyo", "nested/keep.py",
		"tests/keep.py", "nested/tests/keep.py", "nested/deep/tests/keep.py", "ordinary/tests",
		"pydev/pydev_tests/keep.py", "nested/pydev/pydev_test2/keep.py", "nested/deep/pydev/pydev_test3/keep.py",
		"ordinary/pydev/pydev_tests", "other/pydev_test/keep.py",
	}
	tests := []struct {
		name              string
		excludes          []string
		directoryExcludes []string
		absent            []string
		directories       []string
	}{
		{name: "root file glob", excludes: []string{"*.pyc"}, absent: []string{"root.pyc"}},
		{name: "nested file glob", excludes: []string{"**/*.pyc"}, absent: []string{"nested/cache.pyc"}},
		{name: "brace globs", excludes: []string{"*.{pyc,pyo}", "**/*.{pyc,pyo}"},
			absent: []string{"root.pyc", "root.pyo", "nested/cache.pyc", "nested/deep/cache.pyo"}},
		{name: "file filters keep directories", excludes: []string{"tests", "**/tests"},
			absent: []string{"ordinary/tests"}, directories: []string{"tests", "nested/tests", "nested/deep/tests", "empty/tests"}},
		{name: "file subtree globs keep empty directories", excludes: []string{"tests/**", "**/tests/**"},
			absent:      []string{"tests/keep.py", "nested/tests/keep.py", "nested/deep/tests/keep.py"},
			directories: []string{"tests", "nested/tests", "nested/deep/tests", "empty/tests"}},
		{name: "directory filters keep ordinary files", directoryExcludes: []string{"tests", "**/tests", "pydev/pydev_test*", "**/pydev/pydev_test*"},
			absent: []string{"tests", "nested/tests", "nested/deep/tests", "empty/tests", "pydev/pydev_tests",
				"nested/pydev/pydev_test2", "nested/deep/pydev/pydev_test3"}},
		{name: "nested directory glob keeps the root directory", directoryExcludes: []string{"**/tests"},
			absent: []string{"nested/tests", "nested/deep/tests", "empty/tests"}, directories: []string{"tests"}},
		{name: "both filters", excludes: []string{"*.pyc", "**/*.pyc"}, directoryExcludes: []string{"tests", "**/tests"},
			absent: []string{"root.pyc", "nested/cache.pyc", "tests", "nested/tests", "nested/deep/tests", "empty/tests"}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			source := t.TempDir()
			for _, name := range files {
				writeTestFile(t, filepath.Join(source, filepath.FromSlash(name)), []byte(name))
			}
			if err := os.MkdirAll(filepath.Join(source, "empty/tests"), 0o755); err != nil {
				t.Fatal(err)
			}
			transform := treeMap(LayoutMapping{Destination: "mapped"})
			transform.Excludes, transform.DirectoryExcludes = test.excludes, test.directoryExcludes
			layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: transform}}}
			catalogue := Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", source)}}
			output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), catalogue)
			jarOutput, _ := writeExecution(t, layoutJarRecipe(layout), catalogue)
			_, entries := readArchive(t, filepath.Join(jarOutput, "lib/layout.jar"))
			for _, name := range test.absent {
				assertAbsent(t, filepath.Join(output, "payload/mapped", filepath.FromSlash(name)))
			}
			for _, name := range files {
				absent := slices.ContainsFunc(test.absent, func(excluded string) bool {
					return name == excluded || strings.HasPrefix(name, excluded+"/")
				})
				content, present := entries["mapped/"+name]
				if absent {
					if present {
						t.Errorf("excluded jar entry %s", name)
					}
				} else {
					assertContent(t, filepath.Join(output, "payload/mapped", filepath.FromSlash(name)), name)
					if !present || content != name {
						t.Errorf("jar entry %s = %q, present = %v", name, content, present)
					}
				}
			}
			for _, name := range test.directories {
				info, err := os.Stat(filepath.Join(output, "payload/mapped", filepath.FromSlash(name)))
				if err != nil || !info.IsDir() {
					t.Fatalf("missing directory %s: %v", name, err)
				}
			}
		})
	}
}

func TestLayoutTreeMapExcludesBeforeFirstClaim(t *testing.T) {
	root := t.TempDir()
	first, second := filepath.Join(root, "first"), filepath.Join(root, "second")
	writeTestFile(t, filepath.Join(first, "tests/keep.py"), []byte("excluded directory"))
	writeTestFile(t, filepath.Join(first, "drop/shared.txt"), []byte("excluded file"))
	writeTestFile(t, filepath.Join(second, "tests"), []byte("ordinary file"))
	writeTestFile(t, filepath.Join(second, "keep/shared.txt"), []byte("first retained file"))
	transform := treeMap(LayoutMapping{Pattern: "*/*.txt", StripComponents: 1}, LayoutMapping{})
	transform.Excludes = []string{"drop/**"}
	transform.DirectoryExcludes = []string{"tests"}
	layout := LayoutAssets{Inputs: []Reference{{Artifact: "first"}, {Artifact: "second"}}, Assets: []LayoutAsset{
		{Sources: []int{0, 1}, Transform: transform},
		{Destination: "shared.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "later asset"}},
	}}
	catalogue := Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("first", first), directoryArtifact("second", second)}}
	output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), catalogue)
	assertContent(t, filepath.Join(output, "payload/tests"), "ordinary file")
	assertContent(t, filepath.Join(output, "payload/shared.txt"), "first retained file")
	jarOutput, _ := writeExecution(t, layoutJarRecipe(layout), catalogue)
	_, entries := readArchive(t, filepath.Join(jarOutput, "lib/layout.jar"))
	if entries["tests"] != "ordinary file" || entries["shared.txt"] != "first retained file" {
		t.Fatalf("jar entries differ: %v", entries)
	}
}

func TestLayoutTreeMapExcludesPreserveModesAndLinks(t *testing.T) {
	source := t.TempDir()
	writeTestFile(t, filepath.Join(source, "keep/tool"), []byte("tool"))
	if err := os.Chmod(filepath.Join(source, "keep/tool"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("keep", filepath.Join(source, "tests")); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink("absent", filepath.Join(source, "drop.pyc")); err != nil {
		t.Fatal(err)
	}
	transform := treeMap(LayoutMapping{})
	transform.Excludes = []string{"*.pyc"}
	transform.DirectoryExcludes = []string{"tests"}
	layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: transform}}}
	output, _ := writeExecution(t, layoutTreeRecipe("payload", 0, layout), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", source)}})
	assertLink(t, filepath.Join(output, "payload/tests"), "keep")
	assertMode(t, filepath.Join(output, "payload/keep/tool"), 0o755)
	assertAbsent(t, filepath.Join(output, "payload/drop.pyc"))
}

func TestLayoutTreeMapExcludesValidation(t *testing.T) {
	for _, directory := range []bool{false, true} {
		for _, pattern := range []string{"[", "{a", "\\", "[z-a]"} {
			transform := treeMap(LayoutMapping{})
			if directory {
				transform.DirectoryExcludes = []string{pattern}
			} else {
				transform.Excludes = []string{pattern}
			}
			layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: transform}}}
			for _, recipe := range []Recipe{layoutTreeRecipe("payload", 0, layout), layoutJarRecipe(layout)} {
				_, err := Plan(recipe, Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", t.TempDir())}})
				if err == nil || !strings.Contains(err.Error(), "invalid exclude") {
					t.Fatalf("accepted invalid exclude %q (directory = %v) on an empty tree: %v", pattern, directory, err)
				}
			}
		}
		for _, kind := range []string{"archive-tree", "inline-text"} {
			transform := &LayoutTransform{Kind: kind}
			asset := LayoutAsset{Destination: "out", Transform: transform}
			if kind == "archive-tree" {
				asset.Sources = []int{0}
			}
			if err := validateLayoutAsset(asset, layoutTreeFormat, []string{"file"}); err != nil {
				t.Fatal(err)
			}
			if directory {
				transform.DirectoryExcludes = []string{"tests"}
			} else {
				transform.Excludes = []string{"*.pyc"}
			}
			if err := validateLayoutAsset(asset, layoutTreeFormat, []string{"file"}); err == nil || !strings.Contains(err.Error(), "excludes require tree-map") {
				t.Fatalf("accepted excludes on %s (directory = %v): %v", kind, directory, err)
			}
		}
	}
}

func TestLayoutPlanRejectsInvalidPayloads(t *testing.T) {
	excluded := false
	directory := directoryArtifact("tree", "tree")
	file := fileArtifact("archive", "archive.zip")
	owned := directoryArtifact("owned", "owned")
	owned.Tree = &OwnedTree{Version: TreeVersion, Artifact: "owned", Plugin: "layout", LayoutSignature: "layout-v2", RootMode: 0o755, Entries: []filemetadata.Entry{}}
	plainCopy := func(inputs ...Reference) LayoutAssets {
		return LayoutAssets{Inputs: inputs, Assets: []LayoutAsset{{Destination: "copy", Sources: []int{0}}}}
	}
	tests := []struct {
		name      string
		recipe    Recipe
		catalogue Catalogue
		message   string
	}{
		{"layout on a copy-tree", Recipe{Version: TreeVersion, Plugin: "layout", LayoutSignature: "v",
			Assets:     []Asset{{Destination: "payload", Producer: "remainder", Kind: "tree", ClassPath: &excluded}},
			Operations: []Operation{{Kind: "copy-tree", Destination: "payload", Input: &Reference{Artifact: "tree"}, Layout: &LayoutAssets{}}}},
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "only a layout-tree or a layout-file operation carries layout assets"},
		{"layout-tree on a file asset", Recipe{Version: TreeVersion, Plugin: "layout", LayoutSignature: "v",
			Assets:     []Asset{{Destination: "payload", Producer: "remainder"}},
			Operations: []Operation{{Kind: "layout-tree", Destination: "payload", Layout: &LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}}}}}}},
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "stale asset kind"},
		{"layout-tree mode without normalization", func() Recipe {
			recipe := layoutTreeRecipe("payload", 0o644, plainCopy(Reference{Artifact: "tree"}))
			recipe.Assets[0].NormalizeTreeModes = false
			return recipe
		}(), Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "stale tree mode policy"},
		{"layout-tree without layout", Recipe{Version: TreeVersion, Plugin: "layout", LayoutSignature: "v",
			Assets:     []Asset{{Destination: "payload", Producer: "remainder", Kind: "tree", ClassPath: &excluded}},
			Operations: []Operation{{Kind: "layout-tree", Destination: "payload"}}},
			Catalogue{Version: Version}, "layout-tree requires"},
		{"layout-tree in version 1", func() Recipe {
			recipe := layoutTreeRecipe("payload", 0, plainCopy(Reference{Artifact: "tree"}))
			recipe.Version = Version
			return recipe
		}(), Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "requires version 2 or 3"},
		{"layout source with the drop manifest", func() Recipe {
			recipe := layoutJarRecipe(plainCopy(Reference{Artifact: "archive"}))
			recipe.Operations[0].Sources[0].Manifest = "drop"
			return recipe
		}(), Catalogue{Version: Version, Artifacts: []Artifact{file}}, "keep manifest policy"},
		{"layout on an archive source", func() Recipe {
			recipe := layoutJarRecipe(plainCopy(Reference{Artifact: "archive"}))
			recipe.Operations[0].Sources[0].Kind, recipe.Operations[0].Sources[0].Filter = "archive", "all"
			recipe.Operations[0].Sources[0].Input = &Reference{Artifact: "archive"}
			return recipe
		}(), Catalogue{Version: Version, Artifacts: []Artifact{file}}, "only a layout source carries layout assets"},
		{"archive-tree on a directory", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}},
			Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "archive-tree requires one archive file"},
		{"gzip-xml-archive in a tree", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "archive"}},
			Assets: []LayoutAsset{{Destination: "resources", Sources: []int{0}, Transform: gzipXMLArchive()}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{file}}, "gzip-xml-archive requires"},
		{"gzip-xml-archive over a directory", layoutJarRecipe(LayoutAssets{Inputs: []Reference{{Artifact: "tree"}},
			Assets: []LayoutAsset{{Destination: "resources", Sources: []int{0}, Transform: gzipXMLArchive()}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "gzip-xml-archive requires"},
		{"gzip-xml-archive without a source", layoutJarRecipe(LayoutAssets{Assets: []LayoutAsset{{Destination: "resources", Transform: gzipXMLArchive()}}}),
			Catalogue{Version: Version}, "gzip-xml-archive requires"},
		{"layout-file with two assets", layoutFileRecipe("x.txt", 0o644, LayoutAssets{Assets: []LayoutAsset{
			{Destination: "x.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "a"}}, {Destination: "x.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "b"}}}}),
			Catalogue{Version: Version}, "one layout asset at its destination"},
		{"layout-file at another destination", layoutFileRecipe("x.txt", 0o644, LayoutAssets{Assets: []LayoutAsset{{Destination: "y.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "a"}}}}),
			Catalogue{Version: Version}, "one layout asset at its destination"},
		{"layout-file of a directory", layoutFileRecipe("x.txt", 0o644, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Destination: "x.txt", Sources: []int{0}}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "plain copy of one file or an inline text"},
		{"layout-file of an archive", layoutFileRecipe("x.txt", 0o644, LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Destination: "x.txt", Sources: []int{0}, Transform: archiveTree(0)}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{file}}, "plain copy of one file or an inline text"},
		{"layout-file on a tree asset", func() Recipe {
			recipe := layoutFileRecipe("x.txt", 0o644, LayoutAssets{Assets: []LayoutAsset{{Destination: "x.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "a"}}}})
			recipe.Version = TreeVersion
			recipe.Assets[0].Kind = "tree"
			return recipe
		}(), Catalogue{Version: Version}, "stale asset kind"},
		{"unknown transform", layoutTreeRecipe("payload", 0, LayoutAssets{Assets: []LayoutAsset{{Destination: "x", Transform: &LayoutTransform{Kind: "rename"}}}}),
			Catalogue{Version: Version}, "unsupported layout transform"},
		{"source index out of range", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{1}}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "invalid source index"},
		{"two sources for a plain copy", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0, 0}}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "a plain copy requires one source"},
		{"inline-text with a newline", layoutTreeRecipe("payload", 0, LayoutAssets{Assets: []LayoutAsset{{Destination: "x", Transform: &LayoutTransform{Kind: "inline-text", Text: "a\nb"}}}}),
			Catalogue{Version: Version}, "inline-text requires"},
		{"inline-text with a source", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "archive"}},
			Assets: []LayoutAsset{{Destination: "x", Sources: []int{0}, Transform: &LayoutTransform{Kind: "inline-text", Text: "a"}}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{file}}, "inline-text requires"},
		{"tree-map without mappings", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}},
			Assets: []LayoutAsset{{Sources: []int{0}, Transform: &LayoutTransform{Kind: "tree-map"}}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "tree-map requires"},
		{"tree-map over a file", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "archive"}},
			Assets: []LayoutAsset{{Sources: []int{0}, Transform: treeMap(LayoutMapping{})}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{file}}, "tree-map requires"},
		{"tree-map over an owned tree", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "owned"}},
			Assets: []LayoutAsset{{Sources: []int{0}, Transform: treeMap(LayoutMapping{})}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{owned}}, "must be a raw directory"},
		{"invalid mapping pattern", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}},
			Assets: []LayoutAsset{{Sources: []int{0}, Transform: treeMap(LayoutMapping{Pattern: "{a"})}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "invalid mapping pattern"},
		{"root destination for a plain jar entry", layoutJarRecipe(LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{file}}, "can use its output root"},
		{"root destination for an inline jar entry", layoutJarRecipe(LayoutAssets{Assets: []LayoutAsset{{Transform: &LayoutTransform{Kind: "inline-text", Text: "21"}}}}),
			Catalogue{Version: Version}, "can use its output root"},
		{"unsafe destination", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Destination: "../x", Sources: []int{0}}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "unsafe relative path"},
		{"invalid mode", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}, Mode: 0o1000}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "invalid mode"},
		{"no assets", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "at least one asset"},
		{"unresolved input", layoutTreeRecipe("payload", 0, plainCopy(Reference{Artifact: "missing"})),
			Catalogue{Version: Version}, "unresolved layout input"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			_, err := Plan(test.recipe, test.catalogue)
			if err == nil || !strings.Contains(err.Error(), test.message) {
				t.Fatalf("expected a plan failure containing %q, got %v", test.message, err)
			}
		})
	}
}

// TestLayoutPlanAcceptsThePlanFileShapes pins the compact shapes the plan files hold: an empty mapping, a root
// destination in a tree, a directory input for a plain copy, and the root-destination jar entries of a mapped tree,
// an extracted archive, and a copied directory. Planning reads no file.
func TestLayoutPlanAcceptsThePlanFileShapes(t *testing.T) {
	layout := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}, {Artifact: "archive"}}, Assets: []LayoutAsset{
		{Sources: []int{0}},
		{Sources: []int{0}, Transform: treeMap(LayoutMapping{Pattern: "*.properties", Destination: "messages"}, LayoutMapping{})},
		{Sources: []int{1}, Transform: archiveTree(1, LayoutMapping{StripComponents: 1})},
		{Destination: "jre-build.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "21"}},
	}}
	catalogue := Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", "missing-tree"), fileArtifact("archive", "missing.zip")}}
	if _, err := Plan(layoutTreeRecipe("", 0, layout), catalogue); err != nil {
		t.Fatal(err)
	}
	entries := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}, {Artifact: "archive"}}, Assets: []LayoutAsset{
		{Sources: []int{0}, Transform: treeMap(LayoutMapping{})},
		{Sources: []int{1}, Transform: archiveTree(0, LayoutMapping{Pattern: "META-INF/extensions/**"})},
		{Sources: []int{0}},
	}}
	if _, err := Plan(layoutJarRecipe(entries), catalogue); err != nil {
		t.Fatal(err)
	}
	gzip := LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: gzipXMLArchive()}}}
	if _, err := Plan(layoutJarRecipe(gzip), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", "missing.zip")}}); err != nil {
		t.Fatal(err)
	}
	file := LayoutAssets{Assets: []LayoutAsset{{Destination: "jre-build.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "21"}}}}
	if _, err := Plan(layoutFileRecipe("jre-build.txt", 0o644, file), Catalogue{Version: Version}); err != nil {
		t.Fatal(err)
	}
}

// nativeSelectRecipe is the recipe of a native-select operation for one platform: the jar keeps the archive with its
// natives reserved, and the distribution tree lib/native takes the natives of the platform.
func nativeSelectRecipe(os, arch string) Recipe {
	excluded := false
	native := &Reference{Artifact: "native"}
	return Recipe{Version: ScopedVersion, Plugin: "native", LayoutSignature: "native-" + os + "-" + arch,
		Assets: []Asset{
			{Destination: "lib/pty4j.jar", Producer: "remainder"},
			{Destination: "lib/native", Producer: "remainder", Kind: "tree", ClassPath: &excluded, Scope: DistributionScope}},
		Operations: []Operation{
			{Kind: "jar", Destination: "lib/pty4j.jar", Options: &JarOptions{Directories: "none"},
				Sources: []Source{{Kind: "archive", Input: native, Filter: "library", Manifest: "keep", ReserveNatives: true}}},
			{Kind: "native-tree", Destination: "lib/native", Scope: DistributionScope, Input: native, Native: &NativeTarget{OS: os, Arch: arch}}}}
}

// TestNativeTreeSelectsThePlatformNativesAndReservesThemInTheJar pins the rules of nativeLib.kt on a pty4j-shaped
// archive: a universal macOS file serves both architectures, a POSIX file without an extension is executable, a Musl
// entry and a 32-bit Windows entry are selected by no platform, and a platform without an entry gets no lib/native root.
// The jar keeps the class and the checksum, and drops every native entry on every platform.
func TestNativeTreeSelectsThePlatformNativesAndReservesThemInTheJar(t *testing.T) {
	jar := filepath.Join(t.TempDir(), "pty4j-0.13.5.jar")
	// The native entries carry the executable bit of the source jar; the tree gives them the mode of the rule.
	writeZip(t, jar,
		zipTestEntry{name: "com/pty4j/PtyProcess.class", content: "class"},
		zipTestEntry{name: "resources/com/pty4j/native/darwin/libpty.dylib", content: "darwin", mode: 0o755, creator: 3},
		zipTestEntry{name: "resources/com/pty4j/native/linux/x86-64/libpty.so", content: "linux x64", mode: 0o755, creator: 3},
		zipTestEntry{name: "resources/com/pty4j/native/linux/x86-64/pty4j-unix-spawn-helper", content: "helper x64", mode: 0o644, creator: 3},
		zipTestEntry{name: "resources/com/pty4j/native/linux/aarch64/libpty.so", content: "linux aarch64", mode: 0o755, creator: 3},
		zipTestEntry{name: "resources/com/pty4j/native/Linux-Musl/libpty.so", content: "musl", mode: 0o755, creator: 3},
		zipTestEntry{name: "resources/com/pty4j/native/win/x86/winpty.dll", content: "win32", mode: 0o755, creator: 3},
		zipTestEntry{name: "resources/com/pty4j/native/linux/x86-64/libpty.so.sha256", content: "checksum"})
	catalogue := Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("native", jar)}}
	treeRoot := TransportDestination(ScopedVersion, DistributionScope, "lib/native")
	for _, testCase := range []struct {
		os, arch string
		files    map[string]os.FileMode
	}{
		{"darwin", "aarch64", map[string]os.FileMode{"darwin/libpty.dylib": 0o644}},
		{"darwin", "x64", map[string]os.FileMode{"darwin/libpty.dylib": 0o644}},
		{"linux", "x64", map[string]os.FileMode{"linux/x86-64/libpty.so": 0o644, "linux/x86-64/pty4j-unix-spawn-helper": 0o755}},
		{"linux", "aarch64", map[string]os.FileMode{"linux/aarch64/libpty.so": 0o644}},
		{"windows", "x64", nil},
		{"windows", "aarch64", nil},
	} {
		t.Run(testCase.os+"_"+testCase.arch, func(t *testing.T) {
			output, inventory := writeExecution(t, nativeSelectRecipe(testCase.os, testCase.arch), catalogue)
			requireInventoryMatchesTree(t, output, inventory)
			var written []string
			if testCase.files == nil {
				assertAbsent(t, filepath.Join(output, filepath.FromSlash(treeRoot)))
			} else {
				assertMode(t, filepath.Join(output, filepath.FromSlash(treeRoot)), 0o755)
				for _, line := range materializationRecord(t, filepath.Join(output, filepath.FromSlash(treeRoot))) {
					if strings.Contains(line, "\tfile\t") {
						written = append(written, strings.Split(line, "\t")[0])
					}
				}
			}
			for name, mode := range testCase.files {
				file := filepath.Join(output, filepath.FromSlash(treeRoot), filepath.FromSlash(name))
				assertMode(t, file, mode)
				if !slices.Contains(written, name) {
					t.Fatalf("%s is missing from %v", name, written)
				}
			}
			if len(written) != len(testCase.files) {
				t.Fatalf("the tree holds %v, want %v", written, testCase.files)
			}
			names, entries := readArchive(t, filepath.Join(output, "lib/pty4j.jar"))
			want := []string{"com/pty4j/PtyProcess.class", "resources/com/pty4j/native/linux/x86-64/libpty.so.sha256", "__index__"}
			if !slices.Equal(names, want) || entries["com/pty4j/PtyProcess.class"] != "class" {
				t.Fatalf("the jar holds %v, want %v", names, want)
			}
		})
	}
	// The scope check does not read the tree: with no lib/native root, an inventory row for it is not written either.
	_, inventory := writeExecution(t, nativeSelectRecipe("windows", "x64"), catalogue)
	if slices.ContainsFunc(inventory, func(entry filemetadata.Entry) bool { return strings.HasPrefix(entry.RelativePath, treeRoot) }) {
		t.Fatalf("an omitted native tree reached the inventory: %+v", inventory)
	}
}

func TestNativeTreeRefusesAnArchiveWithoutNatives(t *testing.T) {
	jar := filepath.Join(t.TempDir(), "plain-1.0.jar")
	writeZip(t, jar, zipTestEntry{name: "com/Plain.class", content: "class"}, zipTestEntry{name: "a/b.so.sha256", content: "checksum"})
	writeLayoutFailure(t, nativeSelectRecipe("linux", "x64"), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("native", jar)}}, "has no native entries")
}

func TestNativeTreeRefusesTwoPathPrefixes(t *testing.T) {
	jar := filepath.Join(t.TempDir(), "mixed-1.0.jar")
	writeZip(t, jar, zipTestEntry{name: "a/linux-x86-64/libx.so", content: "a"}, zipTestEntry{name: "b/linux-x86-64/liby.so", content: "b"})
	writeLayoutFailure(t, nativeSelectRecipe("linux", "x64"), Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("native", jar)}}, "common path prefix")
}

// A link created through a link that does not exist yet becomes a file link on Windows, so the writer orders the
// links by the links their target paths traverse. The JCEF framework layout is the recorded case.
func TestOrderLinksCreatesATraversedLinkFirst(t *testing.T) {
	links := []layoutLink{
		{name: "jcef.framework/Frameworks", target: "Versions/Current/Frameworks"},
		{name: "jcef.framework/Resources", target: "Versions/Current/Resources"},
		{name: "jcef.framework/Versions/Current", target: "A"},
		{name: "jcef.framework/lib", target: "../shared/lib"},
		{name: "shared/lib", target: "lib-1"},
	}
	ordered, err := orderLinks(links)
	if err != nil {
		t.Fatal(err)
	}
	names := make([]string, 0, len(ordered))
	for _, link := range ordered {
		names = append(names, link.name)
	}
	want := []string{"jcef.framework/Versions/Current", "shared/lib", "jcef.framework/Frameworks", "jcef.framework/Resources", "jcef.framework/lib"}
	if !slices.Equal(names, want) {
		t.Fatalf("order = %q, want %q", names, want)
	}
	if _, err := orderLinks([]layoutLink{{name: "a", target: "b"}, {name: "b", target: "a"}}); err == nil || !strings.Contains(err.Error(), "symlink cycle") {
		t.Fatalf("cycle error = %v", err)
	}
}
