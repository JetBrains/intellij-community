package main

import (
	"crypto/sha256"
	"encoding/hex"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"testing"
)

func TestDevDataRootIsPerCheckoutAndOnByDefaultOnlyOnMacOS(test *testing.T) {
	sum := sha256.Sum256([]byte("/Users/dev/projects/idea"))
	if hex.EncodeToString(sum[:4]) != "f7972983" {
		test.Fatalf("the test vector drifted: %x", sum[:4])
	}
	getenv := func(env map[string]string) func(string) string { return func(name string) string { return env[name] } }
	home := map[string]string{"HOME": "/Users/dev"}
	override := map[string]string{"HOME": "/Users/dev", devDataRootVariable: "/data/dd"}
	for _, check := range []struct {
		goos     string
		env      map[string]string
		expected string
	}{
		{"darwin", home, "/Users/dev/Library/Caches/JetBrains/MonorepoDevData/idea-f7972983"},
		{"darwin", override, "/data/dd/idea-f7972983"},
		{"linux", home, ""},
		{"linux", override, "/data/dd/idea-f7972983"},
		{"windows", override, ""},
	} {
		root, enabled := devDataRoot("/Users/dev/projects/idea", getenv(check.env), check.goos)
		if enabled != (check.expected != "") || root != filepath.FromSlash(check.expected) {
			test.Errorf("%s %v: root %q enabled %v, want %q", check.goos, check.env, root, enabled, check.expected)
		}
	}
}

// devDataFixture is a real workspace and a dev-data parent in temporary directories, with the parent set through
// [devDataRootVariable], so no test touches the user's cache.
type devDataFixture struct {
	workspace string
	parent    string
	getenv    func(string) string
}

func newDevDataFixture(test *testing.T) devDataFixture {
	if runtime.GOOS == "windows" {
		test.Skip("the dev data stays in the workspace on Windows")
	}
	// macOS temporary directories are under a symbolic link (/var -> /private/var), and the launcher hashes real paths.
	base, err := filepath.EvalSymlinks(test.TempDir())
	if err != nil {
		test.Fatal(err)
	}
	fixture := devDataFixture{workspace: filepath.Join(base, "idea"), parent: filepath.Join(base, "cache")}
	if err := os.MkdirAll(fixture.workspace, 0o755); err != nil {
		test.Fatal(err)
	}
	fixture.getenv = func(name string) string {
		if name == devDataRootVariable {
			return fixture.parent
		}
		return ""
	}
	return fixture
}

func (fixture devDataFixture) link() string {
	return filepath.Join(fixture.workspace, "out", "dev-data")
}

func (fixture devDataFixture) root(test *testing.T) string {
	root, enabled := devDataRoot(fixture.workspace, fixture.getenv, "linux")
	if !enabled {
		test.Fatal("the override does not enable the dev-data root")
	}
	return root
}

func (fixture devDataFixture) ensure(test *testing.T) string {
	var warnings strings.Builder
	ensureDevData(fixture.workspace, fixture.getenv, &warnings)
	return warnings.String()
}

func writeFile(test *testing.T, path, content string) {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		test.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		test.Fatal(err)
	}
}

func assertLink(test *testing.T, link, target string) {
	actual, err := os.Readlink(link)
	if err != nil {
		test.Fatalf("%s is not a link: %v", link, err)
	}
	if actual != target {
		test.Errorf("%s links to %s, want %s", link, actual, target)
	}
}

func assertContent(test *testing.T, path, expected string) {
	content, err := os.ReadFile(path)
	if err != nil {
		test.Fatal(err)
	}
	if string(content) != expected {
		test.Errorf("%s holds %q, want %q", path, content, expected)
	}
}

func TestEnsureDevDataLinksAFreshCheckout(test *testing.T) {
	fixture := newDevDataFixture(test)
	if warnings := fixture.ensure(test); warnings != "" {
		test.Errorf("warnings: %s", warnings)
	}
	root := fixture.root(test)
	assertLink(test, fixture.link(), root)
	assertContent(test, filepath.Join(root, workspaceMarker), fixture.workspace+"\n")
	// a second launch keeps the link
	fixture.ensure(test)
	assertLink(test, fixture.link(), root)
}

func TestEnsureDevDataKeepsAnExistingLinkAndRestoresItsTarget(test *testing.T) {
	fixture := newDevDataFixture(test)
	elsewhere := filepath.Join(filepath.Dir(fixture.workspace), "elsewhere")
	if err := os.MkdirAll(filepath.Dir(fixture.link()), 0o755); err != nil {
		test.Fatal(err)
	}
	if err := os.Symlink(elsewhere, fixture.link()); err != nil {
		test.Fatal(err)
	}
	// the link decides, not the formula: a moved checkout keeps its data
	fixture.ensure(test)
	assertLink(test, fixture.link(), elsewhere)
	assertContent(test, filepath.Join(elsewhere, workspaceMarker), fixture.workspace+"\n")
	if exists(fixture.root(test)) {
		test.Error("created the formula root beside an existing link")
	}
}

func TestEnsureDevDataMovesTheDirectoryOfAnOlderLaunch(test *testing.T) {
	fixture := newDevDataFixture(test)
	writeFile(test, filepath.Join(fixture.link(), "idea", "config", "options", "laf.xml"), "dark")
	if warnings := fixture.ensure(test); warnings != "" {
		test.Errorf("warnings: %s", warnings)
	}
	root := fixture.root(test)
	assertLink(test, fixture.link(), root)
	assertContent(test, filepath.Join(root, "idea", "config", "options", "laf.xml"), "dark")
	assertContent(test, filepath.Join(fixture.link(), "idea", "config", "options", "laf.xml"), "dark")
}

func TestEnsureDevDataMergesIntoARootThatLacksTheEntries(test *testing.T) {
	fixture := newDevDataFixture(test)
	root := fixture.root(test)
	writeFile(test, filepath.Join(root, "rider", "config", "a.xml"), "rider")
	writeFile(test, filepath.Join(fixture.link(), "idea", "config", "b.xml"), "idea")
	fixture.ensure(test)
	assertLink(test, fixture.link(), root)
	assertContent(test, filepath.Join(root, "rider", "config", "a.xml"), "rider")
	assertContent(test, filepath.Join(root, "idea", "config", "b.xml"), "idea")
}

func TestEnsureDevDataKeepsTheDirectoryWhenBothSidesHoldARow(test *testing.T) {
	fixture := newDevDataFixture(test)
	writeFile(test, filepath.Join(fixture.root(test), "idea", "config", "a.xml"), "root")
	writeFile(test, filepath.Join(fixture.link(), "idea", "config", "a.xml"), "workspace")
	if warnings := fixture.ensure(test); !strings.Contains(warnings, "both hold idea") {
		test.Errorf("warnings: %q", warnings)
	}
	assertContent(test, filepath.Join(fixture.link(), "idea", "config", "a.xml"), "workspace")
}

func TestEnsureDevDataKeepsTheDirectoryOfARunningIde(test *testing.T) {
	fixture := newDevDataFixture(test)
	// the parent process of the test runs for sure, and the check ignores the launcher's own process ID
	writeFile(test, filepath.Join(fixture.link(), "idea", "config", ".lock"), strconv.Itoa(os.Getppid()))
	if warnings := fixture.ensure(test); !strings.Contains(warnings, "a dev IDE runs from") {
		test.Errorf("warnings: %q", warnings)
	}
	if info, err := os.Lstat(fixture.link()); err != nil || !info.IsDir() {
		test.Errorf("moved the dev data of a running IDE: %v %v", info, err)
	}
	// a stale lock does not keep the directory
	writeFile(test, filepath.Join(fixture.link(), "idea", "config", ".lock"), "999999999")
	fixture.ensure(test)
	assertLink(test, fixture.link(), fixture.root(test))
}

func TestEnsureDevDataLeavesAnOutOutsideTheWorkspace(test *testing.T) {
	fixture := newDevDataFixture(test)
	out := filepath.Join(filepath.Dir(fixture.workspace), "out-elsewhere")
	writeFile(test, filepath.Join(out, "dev-data", "idea", "config", "a.xml"), "x")
	if err := os.Symlink(out, filepath.Join(fixture.workspace, "out")); err != nil {
		test.Fatal(err)
	}
	fixture.ensure(test)
	if info, err := os.Lstat(filepath.Join(out, "dev-data")); err != nil || !info.IsDir() {
		test.Errorf("touched a dev-data directory outside the workspace: %v %v", info, err)
	}
	if exists(fixture.root(test)) {
		test.Error("created a root for an out outside the workspace")
	}
}

func TestConcurrentLaunchesMakeOneLink(test *testing.T) {
	fixture := newDevDataFixture(test)
	for index := 0; index < 20; index++ {
		writeFile(test, filepath.Join(fixture.link(), "row"+strconv.Itoa(index), "config", "a.xml"), strconv.Itoa(index))
	}
	var group sync.WaitGroup
	for range 8 {
		group.Add(1)
		go func() {
			defer group.Done()
			ensureDevData(fixture.workspace, fixture.getenv, io.Discard)
		}()
	}
	group.Wait()
	root := fixture.root(test)
	assertLink(test, fixture.link(), root)
	for index := 0; index < 20; index++ {
		assertContent(test, filepath.Join(root, "row"+strconv.Itoa(index), "config", "a.xml"), strconv.Itoa(index))
	}
}

func TestANewRootReportsTheRootsOfDeletedCheckouts(test *testing.T) {
	fixture := newDevDataFixture(test)
	base := filepath.Dir(fixture.workspace)
	writeFile(test, filepath.Join(fixture.parent, "gone-1", workspaceMarker), filepath.Join(base, "gone")+"\n")
	writeFile(test, filepath.Join(fixture.parent, "alive-2", workspaceMarker), fixture.workspace+"\n")
	// the parent of the checkout is missing, so its volume may be unmounted
	writeFile(test, filepath.Join(fixture.parent, "unmounted-3", workspaceMarker), "/Volumes/missing/checkout\n")
	warnings := fixture.ensure(test)
	if !strings.Contains(warnings, filepath.Join(fixture.parent, "gone-1")) {
		test.Errorf("did not report the root of a deleted checkout: %q", warnings)
	}
	if strings.Contains(warnings, "alive-2") || strings.Contains(warnings, "unmounted-3") {
		test.Errorf("reported a root that may still be in use: %q", warnings)
	}
	for _, name := range []string{"gone-1", "alive-2", "unmounted-3"} {
		if !exists(filepath.Join(fixture.parent, name)) {
			test.Errorf("removed %s", name)
		}
	}
}

func TestPrepareLinksTheDevDataBeforeTheHome(test *testing.T) {
	fixture := newDevDataFixture(test)
	self, _ := writeLauncher(test, nil)
	env := map[string]string{"BUILD_WORKSPACE_DIRECTORY": fixture.workspace, devDataRootVariable: fixture.parent}
	if _, err := prepare([]string{self}, func(name string) string { return env[name] }, io.Discard); err != nil {
		test.Fatal(err)
	}
	assertLink(test, fixture.link(), fixture.root(test))
}
