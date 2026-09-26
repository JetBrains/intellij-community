package nativelib

import (
	"reflect"
	"strings"
	"testing"
)

// sqliteEntries are the native entries of org.sqlite:native in their central-directory order.
var sqliteEntries = []string{
	"sqlite/win-aarch64/sqliteij.dll", "sqlite/linux-x86_64/libsqliteij.so", "sqlite/win-x86_64/sqliteij.dll",
	"sqlite/linux-aarch64/libsqliteij.so", "sqlite/mac-x86_64/libsqliteij.jnilib", "sqlite/mac-aarch64/libsqliteij.jnilib",
}

func TestDetectOSFamilyFollowsTheKotlinRegexes(t *testing.T) {
	for _, testCase := range []struct {
		entry  string
		family Family
		prefix string
		ok     bool
	}{
		{"sqlite/win-aarch64/sqliteij.dll", Windows, "sqlite/", true},
		{"sqlite/mac-x86_64/libsqliteij.jnilib", MacOS, "sqlite/", true},
		{"sqlite/linux-aarch64/libsqliteij.so", Linux, "sqlite/", true},
		{"com/sun/jna/darwin-aarch64/libjnidispatch.jnilib", MacOS, "com/sun/jna/", true},
		{"com/sun/jna/win32-x86-64/jnidispatch.dll", Windows, "com/sun/jna/", true},
		{"com/sun/jna/linux-x86-64/libjnidispatch.so", Linux, "com/sun/jna/", true},
		{"resources/com/pty4j/native/linux/aarch64/libpty.so", Linux, "resources/com/pty4j/native/", true},
		{"darwin/libpty.dylib", MacOS, "", true},
		{"Windows/x64/foo.dll", Windows, "", true},
		{"macos-arm64/libskiko.dylib", MacOS, "", true},
		{"Linux-Android/libx.so", "", "", false},
		{"Linux-Musl/libx.so", "", "", false},
		{"META-INF/MANIFEST.MF", "", "", false},
		{"libskiko-macos-arm64.dylib", MacOS, "", true},
		{"skiko-windows-x64.dll", Windows, "", true},
		{"libskiko-linux-x64.so", Linux, "", true},
		{"libskiko-linux-musl-x64.so", "", "", false},
		{"icudtl.dat", Windows, "", true},
		{"libfoo.so", "", "", false},
	} {
		family, prefix, ok := DetectOSFamily(testCase.entry)
		if family != testCase.family || prefix != testCase.prefix || ok != testCase.ok {
			t.Errorf("%s: family=%q prefix=%q ok=%v, want %q %q %v", testCase.entry, family, prefix, ok, testCase.family, testCase.prefix, testCase.ok)
		}
	}
}

func TestDetermineArchFollowsTheKotlinRules(t *testing.T) {
	for _, testCase := range []struct {
		family Family
		entry  string
		arch   Arch
		ok     bool
	}{
		{Windows, "win-aarch64/sqliteij.dll", AArch64, true},
		{MacOS, "mac-x86_64/libsqliteij.jnilib", X64, true},
		{Linux, "linux/aarch64/libpty.so", AArch64, true},
		{Linux, "linux/x86-64/libpty.so", X64, true},
		{Linux, "linux-x86-64/libjnidispatch.so", X64, true},
		{Linux, "linux-arm64/libx.so", AArch64, true},
		{MacOS, "darwin/libpty.dylib", Universal, true},
		{Linux, "linux/libpty.so", X64, true},
		{Windows, "win32-x86/jnidispatch.dll", "", false},
		{Linux, "linux/sub/dir/libx.so", "", false},
		{MacOS, "libskiko-macos-arm64.dylib", AArch64, true},
		{Windows, "skiko-windows-x64.dll", X64, true},
		{Windows, "icudtl.dat", Universal, true},
		{Linux, "libfoo-linux.so", "", false},
	} {
		arch, ok := DetermineArch(testCase.family, testCase.entry)
		if arch != testCase.arch || ok != testCase.ok {
			t.Errorf("%s %s: arch=%q ok=%v, want %q %v", testCase.family, testCase.entry, arch, ok, testCase.arch, testCase.ok)
		}
	}
}

func TestRelativePathFollowsEachLibraryRule(t *testing.T) {
	for _, testCase := range []struct {
		libName  string
		arch     Arch
		fileName string
		entry    string
		want     string
	}{
		{"async-profiler", X64, "libasyncProfiler.so", "linux-x64/libasyncProfiler.so", "amd64/libasyncProfiler.so"},
		{"async-profiler", Universal, "libasyncProfiler.dylib", "macos/libasyncProfiler.dylib", "libasyncProfiler.dylib"},
		{"skiko-awt-runtime-all", AArch64, "libskiko-macos-arm64.dylib", "libskiko-macos-arm64.dylib", "libskiko-macos-arm64.dylib"},
		{"jna", AArch64, "libjnidispatch.jnilib", "darwin-aarch64/libjnidispatch.jnilib", "aarch64/libjnidispatch.jnilib"},
		{"jna", X64, "jnidispatch.dll", "win32-x86-64/jnidispatch.dll", "amd64/jnidispatch.dll"},
		{"native", AArch64, "sqliteij.dll", "win-aarch64/sqliteij.dll", "win-aarch64/sqliteij.dll"},
		{"pty4j", AArch64, "libpty.so", "linux/aarch64/libpty.so", "linux/aarch64/libpty.so"},
	} {
		got, err := RelativePath(testCase.libName, testCase.arch, testCase.fileName, testCase.entry)
		if err != nil || got != testCase.want {
			t.Errorf("%s %s: %q, %v, want %q", testCase.libName, testCase.entry, got, err, testCase.want)
		}
	}
	if _, err := RelativePath("jna", Universal, "libjnidispatch.jnilib", "darwin/libjnidispatch.jnilib"); err == nil {
		t.Fatal("a universal jna file has no directory")
	}
}

func TestLibNameFromFile(t *testing.T) {
	for file, want := range map[string]string{
		"native-3.42.0-jb.1.jar":              "native",
		"async-profiler-3.0-9-9d5c2f3.jar":    "async-profiler",
		"jna-5.14.0.jar":                      "jna",
		"skiko-awt-runtime-all-0.8.18.jar":    "skiko-awt-runtime-all",
		"pty4j-0.13.5.jar":                    "pty4j",
		"intellij-deps-rocksdbjni-10.8.3.jar": "intellij-deps-rocksdbjni",
		"sqlite-native.jar":                   "sqlite",
		"native.jar":                          "",
	} {
		if got := LibNameFromFile(file); got != want {
			t.Errorf("%s: %q, want %q", file, got, want)
		}
	}
}

func TestIsNativeEntryAndIsExecutable(t *testing.T) {
	for name, want := range map[string]bool{
		"sqlite/mac-aarch64/libsqliteij.jnilib": true, "a/b.dylib": true, "a/b.so": true, "a/b.tbd": true, "a/b.exe": true, "a/b.dll": true,
		"resources/com/pty4j/native/linux/x86-64/pty4j-unix-spawn-helper": true, "icudtl.dat": true,
		"sqlite/mac-aarch64/libsqliteij.jnilib.sha256": false, "META-INF/MANIFEST.MF": false, "a/b.class": false,
	} {
		if got := IsNativeEntry(name); got != want {
			t.Errorf("IsNativeEntry(%s) = %v, want %v", name, got, want)
		}
	}
	for _, testCase := range []struct {
		family   Family
		fileName string
		want     bool
	}{
		{Linux, "pty4j-unix-spawn-helper", true}, {MacOS, "pty4j-unix-spawn-helper", true}, {Windows, "helper", false},
		{Linux, "libpty.so", false}, {MacOS, "libsqliteij.jnilib", false}, {Windows, "sqliteij.dll", false},
	} {
		if got := IsExecutable(testCase.family, testCase.fileName); got != testCase.want {
			t.Errorf("IsExecutable(%s, %s) = %v, want %v", testCase.family, testCase.fileName, got, testCase.want)
		}
	}
}

func TestSelectTakesTheEntriesOfOnePlatform(t *testing.T) {
	for variant, want := range map[string]Match{
		"darwin_aarch64":  {PathWithPrefix: "sqlite/mac-aarch64/libsqliteij.jnilib", Path: "mac-aarch64/libsqliteij.jnilib", Family: MacOS, Arch: AArch64},
		"darwin_x64":      {PathWithPrefix: "sqlite/mac-x86_64/libsqliteij.jnilib", Path: "mac-x86_64/libsqliteij.jnilib", Family: MacOS, Arch: X64},
		"linux_aarch64":   {PathWithPrefix: "sqlite/linux-aarch64/libsqliteij.so", Path: "linux-aarch64/libsqliteij.so", Family: Linux, Arch: AArch64},
		"linux_x64":       {PathWithPrefix: "sqlite/linux-x86_64/libsqliteij.so", Path: "linux-x86_64/libsqliteij.so", Family: Linux, Arch: X64},
		"windows_aarch64": {PathWithPrefix: "sqlite/win-aarch64/sqliteij.dll", Path: "win-aarch64/sqliteij.dll", Family: Windows, Arch: AArch64},
		"windows_x64":     {PathWithPrefix: "sqlite/win-x86_64/sqliteij.dll", Path: "win-x86_64/sqliteij.dll", Family: Windows, Arch: X64},
	} {
		family, arch, err := ParseVariant(variant)
		if err != nil {
			t.Fatal(err)
		}
		matches, err := Select(sqliteEntries, family, arch)
		if err != nil || !reflect.DeepEqual(matches, []Match{want}) {
			t.Errorf("%s: %+v, %v, want %+v", variant, matches, err, want)
		}
		if matches[0].FileName() != want.Path[strings.LastIndex(want.Path, "/")+1:] {
			t.Errorf("%s: file name %q", variant, matches[0].FileName())
		}
	}
	// A universal file serves both architectures; a Musl entry and an entry of no architecture are skipped.
	pty4j := []string{"resources/com/pty4j/native/darwin/libpty.dylib", "resources/com/pty4j/native/linux/aarch64/libpty.so",
		"resources/com/pty4j/native/linux/x86-64/pty4j-unix-spawn-helper", "resources/com/pty4j/native/Linux-Musl/libpty.so",
		"resources/com/pty4j/native/win/x86/winpty.dll"}
	for _, arch := range []Arch{X64, AArch64} {
		matches, err := Select(pty4j, MacOS, arch)
		if err != nil || len(matches) != 1 || matches[0].Path != "darwin/libpty.dylib" || matches[0].Arch != Universal {
			t.Fatalf("macOS %s: %+v, %v", arch, matches, err)
		}
	}
	matches, err := Select(pty4j, Linux, X64)
	if err != nil || len(matches) != 1 || matches[0].Path != "linux/x86-64/pty4j-unix-spawn-helper" {
		t.Fatalf("linux x64: %+v, %v", matches, err)
	}
	if matches, err := Select(pty4j, Windows, X64); err != nil || len(matches) != 0 {
		t.Fatalf("a 32-bit Windows file has no architecture: %+v, %v", matches, err)
	}
	// rocksdbjni keeps every native at the jar root, and ships more Linux flavours than the six platforms take.
	rocksdb := []string{"librocksdbjni-linux-aarch64-musl.so", "librocksdbjni-linux-aarch64.so", "librocksdbjni-linux-ppc64le-musl.so",
		"librocksdbjni-linux-ppc64le.so", "librocksdbjni-linux-riscv64.so", "librocksdbjni-linux-s390x-musl.so", "librocksdbjni-linux-s390x.so",
		"librocksdbjni-linux32-musl.so", "librocksdbjni-linux32.so", "librocksdbjni-linux64-musl.so", "librocksdbjni-linux64.so",
		"librocksdbjni-osx-arm64.jnilib", "librocksdbjni-osx-x86_64.jnilib", "librocksdbjni-win-arm64.dll", "librocksdbjni-win64.dll"}
	for variant, want := range map[string]string{
		"darwin_aarch64": "librocksdbjni-osx-arm64.jnilib", "darwin_x64": "librocksdbjni-osx-x86_64.jnilib",
		"linux_aarch64": "librocksdbjni-linux-aarch64.so", "linux_x64": "librocksdbjni-linux64.so",
		"windows_aarch64": "librocksdbjni-win-arm64.dll", "windows_x64": "librocksdbjni-win64.dll",
	} {
		family, arch, err := ParseVariant(variant)
		if err != nil {
			t.Fatal(err)
		}
		matches, err := Select(rocksdb, family, arch)
		if err != nil || len(matches) != 1 || matches[0].Path != want {
			t.Errorf("rocksdbjni %s: %+v, %v, want %s alone", variant, matches, err, want)
			continue
		}
		if path, err := RelativePath("intellij-deps-rocksdbjni", matches[0].Arch, matches[0].FileName(), matches[0].Path); err != nil || path != want {
			t.Errorf("rocksdbjni %s: relative path %q, %v, want %q", variant, path, err, want)
		}
	}
	if _, err := Select([]string{"a/linux-x64/libx.so", "b/linux-x64/libx.so"}, Linux, X64); err == nil || !strings.Contains(err.Error(), "common path prefix") {
		t.Fatalf("two prefixes: %v", err)
	}
}

func TestParseVariant(t *testing.T) {
	for variant, want := range map[string][2]string{
		"darwin_aarch64": {"darwin", "aarch64"}, "linux_x64": {"linux", "x64"}, "windows_aarch64": {"windows", "aarch64"},
	} {
		family, arch, err := ParseVariant(variant)
		if err != nil || string(family) != want[0] || string(arch) != want[1] {
			t.Errorf("%s: %q %q %v", variant, family, arch, err)
		}
	}
	for _, variant := range []string{"", "linux", "linux_", "_x64", "mac_x64", "windows_arm64", "{platform}"} {
		if _, _, err := ParseVariant(variant); err == nil {
			t.Errorf("accepted %q", variant)
		}
	}
}
