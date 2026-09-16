package pluginpack

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"compress/gzip"
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
// and no link.
type zipTestEntry struct {
	name    string
	content string
	mode    uint32
	symlink bool
	creator uint16
}

func zipTestBytes(t *testing.T, entries ...zipTestEntry) []byte {
	t.Helper()
	var buffer bytes.Buffer
	writer := zip.NewWriter(&buffer)
	for _, entry := range entries {
		header := &zip.FileHeader{Name: entry.name, Method: zip.Store}
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
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "only a layout-tree operation carries layout assets"},
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
		{"archive-tree on a layout source", layoutJarRecipe(LayoutAssets{Inputs: []Reference{{Artifact: "archive"}},
			Assets: []LayoutAsset{{Destination: "out", Sources: []int{0}, Transform: archiveTree(0)}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{file}}, "archive-tree requires one archive file and a tree output"},
		{"archive-tree on a directory", layoutTreeRecipe("payload", 0, LayoutAssets{Inputs: []Reference{{Artifact: "tree"}},
			Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(0)}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{directory}}, "archive-tree requires one archive file"},
		// The kind gzip-xml-archive stays a Kotlin preparation. Go refuses it like every other kind it does not execute.
		{"gzip-xml-archive is not executed by Go", layoutJarRecipe(LayoutAssets{Inputs: []Reference{{Artifact: "archive"}},
			Assets: []LayoutAsset{{Destination: "resources", Sources: []int{0}, Transform: &LayoutTransform{Kind: "gzip-xml-archive"}}}}),
			Catalogue{Version: Version, Artifacts: []Artifact{file}}, "unsupported layout transform"},
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
			Catalogue{Version: Version, Artifacts: []Artifact{file}}, "only a tree or a mapped entry asset"},
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
// destination in a tree, and a directory input for a plain copy. Planning reads no file.
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
	entries := LayoutAssets{Inputs: []Reference{{Artifact: "tree"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: treeMap(LayoutMapping{})}}}
	if _, err := Plan(layoutJarRecipe(entries), Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("tree", "missing-tree")}}); err != nil {
		t.Fatal(err)
	}
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
