package main

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestLocalHomeCreatesDirectoriesWithoutRunfiles(test *testing.T) {
	parentMode, childMode := uint32(0o500), uint32(0o710)
	layout := localLayout{Version: 1, Files: []localLayoutFile{
		{Path: "resources/empty", Kind: "directory", Mode: &childMode},
		{Path: "resources", Kind: "directory", Mode: &parentMode},
		{Path: "current", SymlinkTarget: "resources/empty"},
	}}
	output := filepath.Join(test.TempDir(), "home")
	test.Cleanup(func() { os.Chmod(filepath.Join(output, "resources"), 0o755) })
	lookup := func(name string) (string, error) {
		test.Fatalf("unexpected directory runfile: %s", name)
		return "", nil
	}
	if err := materializeLocalHome(writeLocalLayoutTestFile(test, layout), output, lookup); err != nil {
		test.Fatal(err)
	}
	for name, mode := range map[string]uint32{"resources": parentMode, "resources/empty": childMode} {
		info, err := os.Lstat(filepath.Join(output, name))
		if err != nil || !info.IsDir() || uint32(info.Mode().Perm()) != mode {
			test.Fatalf("directory %s: %v: %v", name, info, err)
		}
	}
	if err := materializeLocalHome(writeLocalLayoutTestFile(test, layout), output, lookup); err == nil {
		test.Fatal("accepted stale local directories")
	}
	for _, invalid := range []localLayoutFile{
		{Path: "resources", Runfile: "_main/file"},
		{Path: "resources", SymlinkTarget: "elsewhere"},
		{Path: "Resources/other", Kind: "directory", Mode: &childMode},
		{Path: "resources/../outside", Kind: "directory", Mode: &childMode},
		{Path: "current/child", Kind: "directory", Mode: &childMode},
		{Path: "extra", Kind: "directory", Runfile: "_main/directory", Mode: &childMode},
	} {
		changed := layout
		changed.Files = append(append([]localLayoutFile{}, layout.Files...), invalid)
		if err := materializeLocalHome(writeLocalLayoutTestFile(test, changed), filepath.Join(test.TempDir(), "home"), lookup); err == nil {
			test.Fatalf("accepted invalid local directory: %+v", invalid)
		}
	}
}

func writeLocalLayoutTestFile(test *testing.T, layout localLayout) string {
	test.Helper()
	content, err := json.Marshal(layout)
	if err != nil {
		test.Fatal(err)
	}
	file := filepath.Join(test.TempDir(), "local-layout.json")
	if err := os.WriteFile(file, content, 0644); err != nil {
		test.Fatal(err)
	}
	return file
}

func TestLocalHomeLinksPayloadAndCopiesMetadata(test *testing.T) {
	if runtime.GOOS == "windows" {
		test.Skip("Windows launches use the self-contained distribution")
	}
	source := filepath.Join(test.TempDir(), "packed.jar")
	if err := os.WriteFile(source, []byte("before"), 0644); err != nil {
		test.Fatal(err)
	}
	layout := writeLocalLayoutTestFile(test, localLayout{
		Version: 1,
		Files: []localLayoutFile{
			{Path: "lib/packed.jar", Runfile: "_main/packed.jar"},
			{Path: "lib/current", SymlinkTarget: "packed.jar"},
			{Path: "lib/alias", SymlinkTarget: "../lib"},
		},
		Metadata: []string{"core-classpath.txt", "fingerprint.txt"},
	})
	for _, name := range []string{"core-classpath.txt", "fingerprint.txt"} {
		if err := os.WriteFile(filepath.Join(filepath.Dir(layout), name), []byte(name), 0644); err != nil {
			test.Fatal(err)
		}
	}
	lookup := func(name string) (string, error) {
		if name != "_main/packed.jar" {
			test.Fatalf("unexpected runfile: %s", name)
		}
		return source, nil
	}
	for _, value := range []string{"before", "after"} {
		if err := os.WriteFile(source, []byte(value), 0644); err != nil {
			test.Fatal(err)
		}
		home := filepath.Join(test.TempDir(), "home")
		if err := materializeLocalHome(layout, home, lookup); err != nil {
			test.Fatal(err)
		}
		linked, err := os.Readlink(filepath.Join(home, "lib/packed.jar"))
		if err != nil || linked != source {
			test.Fatalf("payload link = %q, error = %v", linked, err)
		}
		content, err := os.ReadFile(filepath.Join(home, "lib/alias/current"))
		if err != nil || string(content) != value {
			test.Fatalf("linked content = %q, error = %v", content, err)
		}
		metadata, err := os.Lstat(filepath.Join(home, "core-classpath.txt"))
		if err != nil || !metadata.Mode().IsRegular() {
			test.Fatalf("metadata is not an owned file: %v", err)
		}
		if err := os.RemoveAll(home); err != nil {
			test.Fatal(err)
		}
		if content, err := os.ReadFile(source); err != nil || string(content) != value {
			test.Fatalf("removing the home changed its source: %q, %v", content, err)
		}
	}
}

func TestLocalHomeCopiesFilesThatNeedExecutePermission(test *testing.T) {
	if runtime.GOOS == "windows" {
		test.Skip("POSIX file modes are required")
	}
	source := filepath.Join(test.TempDir(), "native")
	if err := os.WriteFile(source, []byte("native bytes"), 0644); err != nil {
		test.Fatal(err)
	}
	layout := writeLocalLayoutTestFile(test, localLayout{Version: 1, Files: []localLayoutFile{
		{Path: "bin/native", Runfile: "_main/native", Executable: true},
	}})
	home := test.TempDir()
	if err := materializeLocalHome(layout, home, func(string) (string, error) { return source, nil }); err != nil {
		test.Fatal(err)
	}
	info, err := os.Lstat(filepath.Join(home, "bin/native"))
	if err != nil || !info.Mode().IsRegular() || info.Mode().Perm()&0111 == 0 {
		test.Fatalf("the executable was not copied: %v", err)
	}
	info, err = os.Stat(source)
	if err != nil || info.Mode().Perm()&0111 != 0 {
		test.Fatalf("the source mode changed: %v", err)
	}
}

func TestLocalHomeLinksDataWithBazelExecutableBits(test *testing.T) {
	if runtime.GOOS == "windows" {
		test.Skip("Windows launches use the self-contained distribution")
	}
	source := filepath.Join(test.TempDir(), "packed.jar")
	if err := os.WriteFile(source, []byte("jar bytes"), 0644); err != nil {
		test.Fatal(err)
	}
	if err := os.Chmod(source, 0555); err != nil {
		test.Fatal(err)
	}
	for _, file := range []localLayoutFile{
		{Path: "lib/packed.jar", Runfile: "_main/packed.jar"},
		{Path: "lib/packed.jar", Runfile: "_main/packed.jar", Executable: true},
		{Path: "lib/packed.jar", Runfile: "_main/packed.jar", Mode: new(uint32(0644))},
		{Path: "lib/packed.jar", Runfile: "_main/packed.jar", Executable: true, Mode: new(uint32(0755))},
	} {
		layout := writeLocalLayoutTestFile(test, localLayout{Version: 1, Files: []localLayoutFile{file}})
		home := test.TempDir()
		if err := materializeLocalHome(layout, home, func(string) (string, error) { return source, nil }); err != nil {
			test.Fatal(err)
		}
		if linked, err := os.Readlink(filepath.Join(home, "lib/packed.jar")); err != nil || linked != source {
			test.Fatalf("data was copied instead of linked: %q, %v", linked, err)
		}
		info, err := os.Stat(source)
		if err != nil || info.Mode().Perm() != 0555 {
			test.Fatalf("the normalized source mode changed: %v", err)
		}
	}
}

func TestLocalHomePreservesExactSourceModes(test *testing.T) {
	if runtime.GOOS == "windows" {
		test.Skip("Windows launches use the self-contained distribution")
	}
	for _, mode := range []uint32{0, 0600, 0640, 0750, 0755} {
		source := filepath.Join(test.TempDir(), "shared")
		if err := os.WriteFile(source, []byte("shared bytes"), 0600); err != nil {
			test.Fatal(err)
		}
		if err := os.Chmod(source, os.FileMode(mode)); err != nil {
			test.Fatal(err)
		}
		layout := writeLocalLayoutTestFile(test, localLayout{Version: 1, Files: []localLayoutFile{
			{Path: "bin/shared", Runfile: "_main/shared", Executable: mode&0111 != 0, Mode: &mode},
		}})
		home := test.TempDir()
		if err := materializeLocalHome(layout, home, func(string) (string, error) { return source, nil }); err != nil {
			test.Fatal(err)
		}
		if target, err := os.Readlink(filepath.Join(home, "bin/shared")); err != nil || target != source {
			test.Fatalf("payload was not linked: %q, %v", target, err)
		}
		info, err := os.Stat(source)
		if err != nil || uint32(info.Mode().Perm()) != mode {
			test.Fatalf("shared source mode changed: %v", err)
		}
	}
}

func TestLocalHomeCopiesNoncanonicalModesWithoutChangingSource(test *testing.T) {
	if runtime.GOOS == "windows" {
		test.Skip("Windows launches use the self-contained distribution")
	}
	source := filepath.Join(test.TempDir(), "shared")
	if err := os.WriteFile(source, []byte("shared bytes"), 0600); err != nil {
		test.Fatal(err)
	}
	if err := os.Chmod(source, 0555); err != nil {
		test.Fatal(err)
	}
	for _, mode := range []uint32{0, 0600, 0640, 0700, 0750, 0777} {
		layout := writeLocalLayoutTestFile(test, localLayout{Version: 1, Files: []localLayoutFile{
			{Path: "bin/shared", Runfile: "_main/shared", Executable: mode&0111 != 0, Mode: &mode},
		}})
		home := test.TempDir()
		if err := materializeLocalHome(layout, home, func(string) (string, error) { return source, nil }); err != nil {
			test.Fatal(err)
		}
		destination := filepath.Join(home, "bin/shared")
		copied, err := os.Lstat(destination)
		if err != nil || !copied.Mode().IsRegular() || uint32(copied.Mode().Perm()) != mode || copied.Size() != int64(len("shared bytes")) {
			test.Fatalf("the private copy does not have mode %04o: %v, %v", mode, copied, err)
		}
		if mode&0400 != 0 {
			if content, err := os.ReadFile(destination); err != nil || string(content) != "shared bytes" {
				test.Fatalf("the private copy changed the payload: %q, %v", content, err)
			}
		}
		info, err := os.Stat(source)
		if err != nil || info.Mode().Perm() != 0555 || os.SameFile(copied, info) {
			test.Fatalf("shared source mode changed: %v", err)
		}
	}
}

func TestLocalHomeCopiesOnlyNoncanonicalNativeMode(test *testing.T) {
	if runtime.GOOS == "windows" {
		test.Skip("POSIX file modes are required")
	}
	sources := make(map[string]string)
	for _, name := range []string{"packed.jar", "native"} {
		source := filepath.Join(test.TempDir(), name)
		if err := os.WriteFile(source, []byte(name), 0644); err != nil {
			test.Fatal(err)
		}
		if err := os.Chmod(source, 0555); err != nil {
			test.Fatal(err)
		}
		sources["_main/"+name] = source
	}
	layout := writeLocalLayoutTestFile(test, localLayout{Version: 1, Files: []localLayoutFile{
		{Path: "lib/packed.jar", Runfile: "_main/packed.jar", Mode: new(uint32(0644))},
		{Path: "bin/native", Runfile: "_main/native", Executable: true, Mode: new(uint32(0750))},
	}})
	home := test.TempDir()
	if err := materializeLocalHome(layout, home, func(runfile string) (string, error) { return sources[runfile], nil }); err != nil {
		test.Fatal(err)
	}
	if target, err := os.Readlink(filepath.Join(home, "lib/packed.jar")); err != nil || target != sources["_main/packed.jar"] {
		test.Fatalf("the jar was copied instead of linked: %q, %v", target, err)
	}
	native := filepath.Join(home, "bin/native")
	info, err := os.Lstat(native)
	if err != nil || !info.Mode().IsRegular() || info.Mode().Perm() != 0750 {
		test.Fatalf("the native file was not copied with mode 0750: %v, %v", info, err)
	}
	if content, err := os.ReadFile(native); err != nil || string(content) != "native" {
		test.Fatalf("the native payload changed: %q, %v", content, err)
	}
	for runfile, source := range sources {
		info, err := os.Stat(source)
		if err != nil || info.Mode().Perm() != 0555 {
			test.Fatalf("shared source %s changed: %v", runfile, err)
		}
	}
}

func TestLocalHomeRejectsInvalidModeMetadata(test *testing.T) {
	for _, file := range []localLayoutFile{
		{Path: "file", Runfile: "_main/file", Mode: new(uint32(01000))},
		{Path: "file", Runfile: "_main/file", Mode: new(uint32(0750))},
		{Path: "file", Runfile: "_main/file", Executable: true, Mode: new(uint32(0644))},
		{Path: "link", SymlinkTarget: "file", Mode: new(uint32(0))},
	} {
		layout := writeLocalLayoutTestFile(test, localLayout{Version: 1, Files: []localLayoutFile{file}})
		err := materializeLocalHome(layout, test.TempDir(), func(string) (string, error) {
			test.Fatal("invalid mode metadata resolved a payload")
			return "", nil
		})
		if err == nil || !strings.Contains(err.Error(), "invalid mode metadata") {
			test.Fatalf("accepted invalid mode metadata: %#v, %v", file, err)
		}
	}
}

func TestLocalHomeRejectsInvalidLayouts(test *testing.T) {
	cases := []localLayout{
		{Version: 2},
		{Version: 1, Metadata: []string{"../outside"}},
		{Version: 1, Metadata: []string{"fingerprint.txt", "fingerprint.txt"}},
	}
	for _, name := range []string{"", "/absolute", "../outside", "dir/../outside", "dir//file", `C:\outside`, `dir\file`} {
		cases = append(cases, localLayout{Version: 1, Files: []localLayoutFile{{Path: name, Runfile: "_main/file"}}})
	}
	for _, target := range []string{"/outside", "../../outside", `C:\outside`} {
		cases = append(cases, localLayout{Version: 1, Files: []localLayoutFile{{Path: "lib/link", SymlinkTarget: target}}})
	}
	cases = append(cases,
		localLayout{Version: 1, Files: []localLayoutFile{{Path: "lib/file"}}},
		localLayout{Version: 1, Files: []localLayoutFile{{Path: "lib/file", Runfile: "../outside"}}},
		localLayout{Version: 1, Files: []localLayoutFile{{Path: "lib/file", Runfile: "_main/file", SymlinkTarget: "file"}}},
		localLayout{Version: 1, Files: []localLayoutFile{{Path: "lib", Runfile: "_main/file"}, {Path: "lib/file", Runfile: "_main/file"}}},
		localLayout{Version: 1, Files: []localLayoutFile{{Path: "lib", SymlinkTarget: "other"}, {Path: "lib/file", Runfile: "_main/file"}}},
		localLayout{Version: 1, Files: []localLayoutFile{{Path: "file", Runfile: "_main/file"}, {Path: "file", Runfile: "_main/file"}}},
		localLayout{Version: 1, Metadata: []string{"fingerprint.txt"}, Files: []localLayoutFile{{Path: "fingerprint.txt", Runfile: "_main/file"}}},
	)
	for _, layout := range cases {
		home := filepath.Join(test.TempDir(), "home")
		err := materializeLocalHome(writeLocalLayoutTestFile(test, layout), home, func(string) (string, error) {
			test.Fatal("an invalid layout attempted to resolve payload")
			return "", nil
		})
		if err == nil {
			test.Fatalf("accepted invalid layout: %#v", layout)
		}
		if _, err := os.Stat(home); !os.IsNotExist(err) {
			test.Fatalf("invalid layout created the home: %#v", layout)
		}
	}
}

func TestLocalHomeRefusesExistingContents(test *testing.T) {
	home := test.TempDir()
	file := filepath.Join(home, "keep")
	if err := os.WriteFile(file, []byte("keep"), 0644); err != nil {
		test.Fatal(err)
	}
	err := materializeLocalHome(writeLocalLayoutTestFile(test, localLayout{Version: 1}), home, nil)
	requireError(test, err, "must be empty")
	if content, err := os.ReadFile(file); err != nil || string(content) != "keep" {
		test.Fatalf("existing content changed: %q, %v", content, err)
	}
}

func TestLocalRunfilesLookup(test *testing.T) {
	root := test.TempDir()
	if err := os.Mkdir(filepath.Join(root, "_main"), 0755); err != nil {
		test.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "_main/file"), []byte("bytes"), 0644); err != nil {
		test.Fatal(err)
	}
	test.Setenv("JAVA_RUNFILES", root)
	test.Setenv("RUNFILES_DIR", "")
	test.Setenv("RUNFILES_MANIFEST_FILE", "")
	lookup, err := localRunfilesLookup()
	if err != nil {
		test.Fatal(err)
	}
	resolved, err := lookup("_main/file")
	if err != nil || resolved != filepath.Join(root, "_main/file") {
		test.Fatalf("directory lookup = %q, %v", resolved, err)
	}
	test.Setenv("JAVA_RUNFILES", "")
	manifest := filepath.Join(root, "MANIFEST")
	if err := os.WriteFile(manifest, []byte("_main/tree "+root+"\n"), 0644); err != nil {
		test.Fatal(err)
	}
	test.Setenv("RUNFILES_MANIFEST_FILE", manifest)
	lookup, err = localRunfilesLookup()
	if err != nil {
		test.Fatal(err)
	}
	resolved, err = lookup("_main/tree/_main/file")
	if err != nil || resolved != filepath.Join(root, "_main/file") {
		test.Fatalf("tree manifest lookup = %q, %v", resolved, err)
	}
	_, err = lookup("_main/absent")
	requireError(test, err, "missing local dev runfile")
}

func TestLocalHomeCommandRejectsBadOptions(test *testing.T) {
	for _, args := range [][]string{{}, {"--unknown=value"}, {"--layout="}, {"--layout=a", "--layout=b", "--output-dir=home"}} {
		var output, errors bytes.Buffer
		if code := run(append([]string{"local-home"}, args...), &output, &errors); code != 2 || !strings.Contains(errors.String(), "ERROR:") {
			test.Fatalf("code = %d, output = %s, errors = %s", code, output.String(), errors.String())
		}
	}
}
