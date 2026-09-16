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
	if err := os.WriteFile(source, []byte("jar bytes"), 0755); err != nil {
		test.Fatal(err)
	}
	layout := writeLocalLayoutTestFile(test, localLayout{Version: 1, Files: []localLayoutFile{
		{Path: "lib/packed.jar", Runfile: "_main/packed.jar"},
	}})
	home := test.TempDir()
	if err := materializeLocalHome(layout, home, func(string) (string, error) { return source, nil }); err != nil {
		test.Fatal(err)
	}
	if linked, err := os.Readlink(filepath.Join(home, "lib/packed.jar")); err != nil || linked != source {
		test.Fatalf("data was copied instead of linked: %q, %v", linked, err)
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
