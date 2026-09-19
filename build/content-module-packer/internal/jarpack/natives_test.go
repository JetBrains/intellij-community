// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"bytes"
	"io/fs"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"testing"

	"jetbrains.com/content-module-packer/internal/nativelib"
)

// nativeLibrarySource is a presigned library the way JarPackager sees one: a Maven-named jar with one native per
// platform under a common prefix, and a class that has to stay in the jar.
func nativeLibrarySource(t *testing.T) string {
	t.Helper()
	return writeZipJar(t, "foo-1.2.3.jar",
		sourceEntry{name: "com/x/Foo.class", data: "class bytes"},
		sourceEntry{name: "com/x/darwin-aarch64/libfoo.dylib", data: "mac arm"},
		sourceEntry{name: "com/x/linux-x86-64/libfoo.so", data: "linux x64"},
		sourceEntry{name: "com/x/win32-x86-64/foo.dll", data: "windows x64"},
	)
}

func nativeSpec(t *testing.T, variant, lib string) *NativeSpec {
	t.Helper()
	family, arch, err := nativelib.ParseVariant(variant)
	if err != nil {
		t.Fatal(err)
	}
	return &NativeSpec{Tree: filepath.Join(t.TempDir(), "native"), Family: family, Arch: arch, LibName: lib}
}

// treeFiles lists the regular files under root with their modes, keyed by slash path.
func treeFiles(t *testing.T, root string) map[string]fs.FileMode {
	t.Helper()
	files := make(map[string]fs.FileMode)
	err := filepath.WalkDir(root, func(path string, entry fs.DirEntry, err error) error {
		if err != nil || entry.IsDir() {
			return err
		}
		info, err := entry.Info()
		if err != nil {
			return err
		}
		relative, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		files[filepath.ToSlash(relative)] = info.Mode().Perm()
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	return files
}

func TestParseFlagFileNativesModeTakesAllThreeLinesOrNone(t *testing.T) {
	specs, err := parseRecipe(t, "output=out/a.jar\nnative-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=jna\n"+
		"library=lib/jna-5.14.0.jar\nmodule=mod/a.jar\noutput=out/b.jar\nmodule=mod/b.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	want := &NativeSpec{Tree: filepath.Join("/exec/root", "out/native"), Family: nativelib.MacOS, Arch: nativelib.AArch64, LibName: "jna"}
	if specs[0].Native == nil || *specs[0].Native != *want {
		t.Errorf("native spec is %+v, want %+v", specs[0].Native, want)
	}
	if !specs[0].Sources[0].Library || specs[0].Sources[1].Library {
		t.Errorf("`library=` alone marks a library source: %+v", specs[0].Sources)
	}
	if specs[1].Native != nil {
		t.Errorf("the second group inherited the first one's natives mode: %+v", specs[1].Native)
	}
	absolute, err := parseRecipe(t, "output=out/a.jar\nnative-tree=/tmp/./native\nnative-variant=windows_x64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n")
	if err != nil || absolute[0].Native.Tree != "/tmp/native" || absolute[0].Native.Family != nativelib.Windows || absolute[0].Native.Arch != nativelib.X64 {
		t.Errorf("native spec is %+v, error = %v", absolute[0].Native, err)
	}
	for name, lines := range map[string]string{
		"a tree alone":              "output=out/a.jar\nnative-tree=out/native\nlibrary=lib/jna-5.14.0.jar\n",
		"a variant alone":           "output=out/a.jar\nnative-variant=darwin_aarch64\nlibrary=lib/jna-5.14.0.jar\n",
		"a lib alone":               "output=out/a.jar\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"a tree and a lib":          "output=out/a.jar\nnative-tree=out/native\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"an empty tree":             "output=out/a.jar\nnative-tree=\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"an unknown variant":        "output=out/a.jar\nnative-tree=out/native\nnative-variant=mac_arm64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"a tree twice":              "output=out/a.jar\nnative-tree=out/native\nnative-tree=out/other\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"a tree before any output":  "native-tree=out/native\noutput=out/a.jar\nlibrary=lib/jna-5.14.0.jar\n",
		"rejected natives as well":  "output=out/a.jar\nreject-native-entries=true\nnative-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"the tree is the jar":       "output=out/a.jar\nnative-tree=out/a.jar\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"the tree is the metadata":  "output=out/a.jar\nmetadata-file=out/a.json\nnative-tree=out/a.json\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"the tree is the trace":     "output=out/a.jar\ntrace-file=out/a.json\nnative-tree=out/a.json\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"the tree is an input":      "output=out/a.jar\nnative-tree=lib/jna-5.14.0.jar\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n",
		"two groups share the tree": "output=out/a.jar\nnative-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=jna\nlibrary=lib/jna-5.14.0.jar\n" + "output=out/b.jar\nnative-tree=out/native\nnative-variant=darwin_aarch64\nnative-lib=pty4j\nlibrary=lib/pty4j-0.13.jar\n",
	} {
		t.Run(name, func(t *testing.T) {
			if _, err := parseRecipe(t, lines); err == nil {
				t.Errorf("accepted a recipe that says one thing and packs another: %s", lines)
			}
		})
	}
}

// The line the packing rule writes must reach the bytes: the same recipe packed through the flag file and through a
// hand-built spec is the same jar and the same tree.
func TestParseFlagFileNativesModePacks(t *testing.T) {
	library := nativeLibrarySource(t)
	module := moduleSource(t, "module.jar")
	tree := filepath.Join(t.TempDir(), "native")
	specs, err := parseRecipe(t, "output=out/a.jar\nnative-tree="+tree+"\nnative-variant=linux_x64\nnative-lib=foo\nlibrary="+library+"\nmodule="+module+"\n")
	if err != nil {
		t.Fatal(err)
	}
	data, _ := pack(t, specs[0])
	if slices.ContainsFunc(entryNames(t, data), nativelib.IsNativeEntry) {
		t.Errorf("natives stayed in the jar: %v", entryNames(t, data))
	}
	if got := treeFiles(t, tree); len(got) != 1 || got["linux-x86-64/libfoo.so"] == 0 {
		t.Errorf("tree holds %v, want the linux x64 native alone", got)
	}
}

func TestNativesModeMovesThePlatformNativesOutOfTheJar(t *testing.T) {
	library := nativeLibrarySource(t)
	module := moduleSource(t, "module.jar")
	native := nativeSpec(t, "darwin_aarch64", "foo")
	data, duplicates := pack(t, MergeSpec{Output: "intellij.libraries.foo.jar", Native: native, Sources: []Source{
		{Path: library, Filter: LibraryNameFilter, Library: true},
		{Path: module, Filter: ModuleOutputNameFilter},
	}})
	if len(duplicates) != 0 {
		t.Errorf("no source shares a name, got duplicates %v", duplicates)
	}
	// Every native is out of the jar, not only the platform's: JarPackager leaves them all out of a presigned library.
	if got, want := strings.Join(entryNames(t, data), ","),
		"com/x/Foo.class,com/example/Service.class,com/example/nested/Inner.class,messages/Bundle.properties,__index__"; got != want {
		t.Errorf("entries are %q, want %q", got, want)
	}
	// And out of the index, whose payload ends with the names it indexes.
	index := readEntry(t, data, IndexFileName)
	if !strings.Contains(index, "com/x/Foo.class") || strings.Contains(index, "libfoo") || strings.Contains(index, "foo.dll") {
		t.Errorf("the index names the natives it does not hold: %q", index)
	}
	// The jar is the one a reservation of every native produces, byte for byte. That is the shape pluginpack has proved
	// against JarPackager.
	reserved, _ := pack(t, MergeSpec{Output: "intellij.libraries.foo.jar", Sources: []Source{
		{Path: library, Filter: LibraryNameFilter, EntryOverrides: map[string]EntryOverride{
			"com/x/darwin-aarch64/libfoo.dylib": {Reserve: true},
			"com/x/linux-x86-64/libfoo.so":      {Reserve: true},
			"com/x/win32-x86-64/foo.dll":        {Reserve: true},
		}},
		{Path: module, Filter: ModuleOutputNameFilter},
	}})
	if !bytes.Equal(data, reserved) {
		t.Error("natives mode changed the jar bytes against an explicit reservation of every native")
	}
	// The tree holds the platform's file alone, at the path nativelib gives it, as a library file.
	got := treeFiles(t, native.Tree)
	if len(got) != 1 {
		t.Fatalf("tree holds %v, want one file", got)
	}
	if mode, exists := got["darwin-aarch64/libfoo.dylib"]; !exists || (runtime.GOOS != "windows" && mode != 0o644) {
		t.Errorf("tree holds %v, want darwin-aarch64/libfoo.dylib as 0644", got)
	}
	content, err := os.ReadFile(filepath.Join(native.Tree, "darwin-aarch64", "libfoo.dylib"))
	if err != nil || string(content) != "mac arm" {
		t.Errorf("the native holds %q, error = %v", content, err)
	}
}

func TestNativesModeLaysTheTreeOutPerLibrary(t *testing.T) {
	// jna files go under the JVM architecture directory rather than under their own; the tree key the metadata records
	// for the dev distribution is `native/aarch64/libjnidispatch.jnilib`.
	jna := writeZipJar(t, "jna-5.14.0.jar",
		sourceEntry{name: "com/sun/jna/Native.class", data: "class"},
		sourceEntry{name: "com/sun/jna/darwin-aarch64/libjnidispatch.jnilib", data: "arm"},
		sourceEntry{name: "com/sun/jna/darwin-x86-64/libjnidispatch.jnilib", data: "intel"},
		sourceEntry{name: "com/sun/jna/linux-x86-64/libjnidispatch.so", data: "linux"},
	)
	native := nativeSpec(t, "darwin_aarch64", "jna")
	pack(t, MergeSpec{Output: "intellij.libraries.jna.jar", Native: native, Sources: []Source{{Path: jna, Filter: LibraryNameFilter, Library: true}}})
	if got := treeFiles(t, native.Tree); len(got) != 1 || got["aarch64/libjnidispatch.jnilib"] == 0 {
		t.Errorf("tree holds %v, want aarch64/libjnidispatch.jnilib alone", got)
	}
}

func TestNativesModeMarksAPosixFileWithoutAnExtensionExecutable(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("NTFS stores no executable bit")
	}
	pty4j := writeZipJar(t, "pty4j-0.13.4.jar",
		sourceEntry{name: "com/pty4j/PtyProcess.class", data: "class"},
		sourceEntry{name: "resources/com/pty4j/native/darwin/libpty.dylib", data: "lib"},
		sourceEntry{name: "resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper", data: "helper"},
		sourceEntry{name: "resources/com/pty4j/native/win/x86-64/winpty.dll", data: "dll"},
	)
	native := nativeSpec(t, "darwin_x64", "pty4j")
	data, _ := pack(t, MergeSpec{Output: "intellij.libraries.pty4j.jar", Native: native, Sources: []Source{{Path: pty4j, Filter: LibraryNameFilter, Library: true}}})
	if got, want := strings.Join(entryNames(t, data), ","), "com/pty4j/PtyProcess.class,__index__"; got != want {
		t.Errorf("entries are %q, want %q", got, want)
	}
	got := treeFiles(t, native.Tree)
	if len(got) != 2 || got["darwin/libpty.dylib"] != 0o644 || got["darwin/pty4j-unix-spawn-helper"] != 0o755 {
		t.Errorf("tree modes are %v, want the helper alone executable", got)
	}
}

func TestNativesModeWritesAnEmptyTreeForAPlatformWithoutANative(t *testing.T) {
	// A Windows-only native packed for macOS: the jar loses the entry all the same, and the tree exists with nothing in
	// it, because the rule declared the directory.
	library := writeZipJar(t, "foo-1.0.jar",
		sourceEntry{name: "com/x/Foo.class", data: "class"},
		sourceEntry{name: "com/x/win32-x86-64/foo.dll", data: "windows"},
	)
	native := nativeSpec(t, "darwin_aarch64", "foo")
	data, _ := pack(t, MergeSpec{Output: "intellij.libraries.foo.jar", Native: native, Sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}}})
	if got, want := strings.Join(entryNames(t, data), ","), "com/x/Foo.class,__index__"; got != want {
		t.Errorf("entries are %q, want %q", got, want)
	}
	entries, err := os.ReadDir(native.Tree)
	if err != nil || len(entries) != 0 {
		t.Errorf("tree holds %v, error = %v; want an empty directory", entries, err)
	}
}

func TestNativesModeAcceptsAnExistingEmptyTree(t *testing.T) {
	library := nativeLibrarySource(t)
	native := nativeSpec(t, "linux_x64", "foo")
	if err := os.MkdirAll(native.Tree, 0o755); err != nil {
		t.Fatal(err)
	}
	pack(t, MergeSpec{Output: "intellij.libraries.foo.jar", Native: native, Sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}}})
	if got := treeFiles(t, native.Tree); len(got) != 1 || got["linux-x86-64/libfoo.so"] == 0 {
		t.Errorf("tree holds %v, want the linux x64 native alone", got)
	}
}

func TestNativesModeRefusesANonEmptyTree(t *testing.T) {
	// The collector inventories the tree as the pack's output, so a file the pack did not write must not be there.
	library := nativeLibrarySource(t)
	native := nativeSpec(t, "linux_x64", "foo")
	if err := os.MkdirAll(native.Tree, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(native.Tree, "stale"), []byte("stale"), 0o644); err != nil {
		t.Fatal(err)
	}
	output := filepath.Join(t.TempDir(), "out.jar")
	spec := MergeSpec{Output: output, Native: native, Sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}}}
	if _, err := spec.Pack(); err == nil || !strings.Contains(err.Error(), "is not empty: stale") {
		t.Fatalf("error = %v, want the stale tree refused", err)
	}
	// Refused before the jar, so a tree never has a jar it does not belong to.
	if _, err := os.Stat(output); !os.IsNotExist(err) {
		t.Errorf("the jar was written although the tree was refused: %v", err)
	}
	if got := treeFiles(t, native.Tree); len(got) != 1 || got["stale"] == 0 {
		t.Errorf("tree holds %v, want the stale file alone", got)
	}
}

func TestNativesModeRefusesAnUnsafeNativeEntryName(t *testing.T) {
	// The merge does not validate entry names on the flag-file path, so the native path is checked where it becomes a
	// file path. For a library without a layout rule the relative path is the entry name after the common prefix.
	library := writeZipJar(t, "foo-1.2.3.jar",
		sourceEntry{name: "com/x/Foo.class", data: "class"},
		sourceEntry{name: "com/x/linux-x86-64/../../evil.so", data: "escapes the tree"},
	)
	native := nativeSpec(t, "linux_x64", "foo")
	spec := MergeSpec{Output: filepath.Join(t.TempDir(), "out.jar"), Native: native, Sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}}}
	_, err := spec.Pack()
	if err == nil || !strings.Contains(err.Error(), "unsafe entry name") {
		t.Fatalf("error = %v, want the traversal refused", err)
	}
	if got := treeFiles(t, native.Tree); len(got) != 0 {
		t.Errorf("tree holds %v, want nothing written", got)
	}
	if _, err := os.Stat(filepath.Join(filepath.Dir(native.Tree), "evil.so")); !os.IsNotExist(err) {
		t.Errorf("a file escaped the tree: %v", err)
	}
}

func TestNativesModeRefusesAnExecutableOnAWindowsHost(t *testing.T) {
	// A Windows host records no executable bit, and the collector reads the mode from the tree.
	pty4j := writeZipJar(t, "pty4j-0.13.4.jar",
		sourceEntry{name: "com/pty4j/PtyProcess.class", data: "class"},
		sourceEntry{name: "resources/com/pty4j/native/linux/x86-64/libpty.so", data: "lib"},
		sourceEntry{name: "resources/com/pty4j/native/linux/x86-64/pty4j-unix-spawn-helper", data: "helper"},
	)
	native := nativeSpec(t, "linux_x64", "pty4j")
	spec := MergeSpec{Output: filepath.Join(t.TempDir(), "out.jar"), Native: native, Sources: []Source{{Path: pty4j, Filter: LibraryNameFilter, Library: true}}}
	_, err := spec.Pack()
	if runtime.GOOS != "windows" {
		if err != nil {
			t.Fatal(err)
		}
		if got := treeFiles(t, native.Tree); got["linux/x86-64/pty4j-unix-spawn-helper"] != 0o755 {
			t.Errorf("tree modes are %v, want the helper executable", got)
		}
		return
	}
	if err == nil || !strings.Contains(err.Error(), "executable native file cannot be written on a Windows host") {
		t.Fatalf("error = %v, want the executable refused", err)
	}
	if got := treeFiles(t, native.Tree); len(got) != 0 {
		t.Errorf("tree holds %v, want nothing written", got)
	}
}

func TestNativesModeRefusesWhatWouldLeaveANativeInTheJar(t *testing.T) {
	library := nativeLibrarySource(t)
	classesOnly := writeZipJar(t, "bar-2.0.jar", sourceEntry{name: "com/bar/Bar.class", data: "class"})
	moduleWithNative := writeZipJar(t, "module.jar",
		sourceEntry{name: "com/example/Service.class", data: "class"},
		sourceEntry{name: "com/example/linux-x86-64/libmodule.so", data: "a native of its own"},
	)
	libraryWithNative := writeZipJar(t, "other-3.0.jar", sourceEntry{name: "com/other/darwin/libother.dylib", data: "native"})
	nativeFile := fileSource(t, "libfile.so", "native")
	for name, test := range map[string]struct {
		sources []Source
		want    string
	}{
		"no library source of the native library": {
			sources: []Source{{Path: classesOnly, Filter: LibraryNameFilter, Library: true}},
			want:    "no library source",
		},
		"a module output named like the native library": {
			sources: []Source{{Path: library, Filter: LibraryNameFilter}},
			want:    "no library source",
		},
		"two library sources of the native library": {
			sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}, {Path: library, Filter: LibraryNameFilter, Library: true}},
			want:    "two library sources",
		},
		"a native library without a native": {
			sources: []Source{{Path: classesOnly, Filter: LibraryNameFilter, Library: true}},
			want:    "no native entry",
		},
		"a native in a module output": {
			sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}, {Path: moduleWithNative, Filter: ModuleOutputNameFilter}},
			want:    "contains native entry com/example/linux-x86-64/libmodule.so outside the native library foo",
		},
		"a native in another library": {
			sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}, {Path: libraryWithNative, Filter: LibraryNameFilter, Library: true}},
			want:    "contains native entry com/other/darwin/libother.dylib outside the native library foo",
		},
		"a native as a file source": {
			sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}, {Path: nativeFile, Name: "com/x/libfile.so"}},
			want:    "outside the native library foo",
		},
	} {
		t.Run(name, func(t *testing.T) {
			lib := "foo"
			if name == "a native library without a native" {
				lib = "bar"
			}
			spec := MergeSpec{Output: filepath.Join(t.TempDir(), "out.jar"), Native: nativeSpec(t, "linux_x64", lib), Sources: test.sources}
			_, err := spec.Pack()
			if err == nil || !strings.Contains(err.Error(), test.want) {
				t.Fatalf("error = %v, want %q", err, test.want)
			}
		})
	}
	// The one combination the flag file refuses is refused by a hand-built spec as well.
	spec := MergeSpec{Output: filepath.Join(t.TempDir(), "out.jar"), RejectNativeEntries: true, Native: nativeSpec(t, "linux_x64", "foo"),
		Sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}}}
	if _, err := spec.Pack(); err == nil || !strings.Contains(err.Error(), "cannot be combined") {
		t.Errorf("error = %v, want the combination refused", err)
	}
}

func TestNativesModeRefusesTwoEntriesSelectingOnePath(t *testing.T) {
	// async-profiler places a file under its architecture directory, so two entries of one architecture under two
	// spellings of the platform directory would land on one path, and the second would silently win.
	profiler := writeZipJar(t, "async-profiler-3.0.jar",
		sourceEntry{name: "bin/darwin-aarch64/libasyncProfiler.dylib", data: "one"},
		sourceEntry{name: "bin/darwin/aarch64/libasyncProfiler.dylib", data: "two"},
	)
	spec := MergeSpec{Output: filepath.Join(t.TempDir(), "out.jar"), Native: nativeSpec(t, "darwin_aarch64", "async-profiler"),
		Sources: []Source{{Path: profiler, Filter: LibraryNameFilter, Library: true}}}
	if _, err := spec.Pack(); err == nil || !strings.Contains(err.Error(), "two native entries select") {
		t.Fatalf("error = %v, want the collision refused", err)
	}
}

func TestNativesModeLeavesOtherRecipesUntouched(t *testing.T) {
	// A library with natives packed *without* natives mode keeps them, as before: nothing about the mode leaks into a
	// recipe that does not ask for it, which is what the golden digests gate.
	library := nativeLibrarySource(t)
	data, _ := pack(t, MergeSpec{Output: "intellij.example.jar", Sources: []Source{{Path: library, Filter: LibraryNameFilter, Library: true}}})
	if !slices.Contains(entryNames(t, data), "com/x/darwin-aarch64/libfoo.dylib") {
		t.Errorf("a recipe without natives mode lost a native: %v", entryNames(t, data))
	}
}
