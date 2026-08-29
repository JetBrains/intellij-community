// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// What this file covers is the request surface: the option spelling the rule states, the scalars this binary computes
// from the build number, and the failure of a request the rule cannot state. The bytes are gated in
// `internal/descriptorxml`, `internal/stamps` and `internal/structural`, and over a whole product by
// `./build/dev-dist.cmd descriptors`.

// requestFile writes a parameter file of the shape `dev_dist_plugin_descriptor` passes, and returns its path.
func requestFile(t *testing.T, dir string, lines ...string) string {
	t.Helper()
	path := filepath.Join(dir, "arguments.txt")
	write(t, path, strings.Join(lines, "\n")+"\n")
	return path
}

// buildNumberFile writes the declared build-number file, which is `@community//:build.txt` in the rule.
func buildNumberFile(t *testing.T, dir string, value string) string {
	t.Helper()
	path := filepath.Join(dir, "build.txt")
	write(t, path, value+"\n")
	return path
}

func TestTheWholeRequestIsPatched(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	output := filepath.Join(dir, "out", "intellij.example.plugin.xml")
	write(t, source, `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude"><id>a</id>`+
		`<description>&lt;b&gt;x&lt;/b&gt;</description>`+
		`<xi:include href="extra.xml"/>`+
		`<content><module name="a.b"/><module name="dropped"/></content></idea-plugin>`)
	write(t, filepath.Join(dir, "extra.xml"), `<idea-plugin><extensions/></idea-plugin>`)
	write(t, filepath.Join(dir, "a.b.xml"), `<idea-plugin package="a.b"/>`)

	code := run([]string{"--flagfile=" + requestFile(t, dir,
		"--out="+output,
		"--main-module=intellij.example",
		"--directory-name=example",
		"--main-jar-name=example.jar",
		"--source="+source,
		"--build-number-file="+buildNumberFile(t, dir, "263.SNAPSHOT"),
		"--release-date=20260101",
		"--release-version=2026300",
		"--eap=true",
		"--exact-version=false",
		"--retain-product-descriptor=false",
		"--embed-content-modules=true",
		"--refused-content-module=dropped",
		"--separate-jar=a.b",
		"--plugin-descriptor=META-INF/extra.xml="+filepath.Join(dir, "extra.xml"),
		"--plugin-descriptor=a.b.xml="+filepath.Join(dir, "a.b.xml"),
		"--plugin-module=intellij.example",
		"--platform-module=intellij.platform.core",
	)})
	if code != 0 {
		t.Fatalf("exit %d", code)
	}

	// `263.SNAPSHOT` with the pinned build date gives the version. The range is computed from the **build number** and
	// not from that version (`patchPluginDescriptorFromPlan` of `DevDistPluginDescriptorMain.kt`), and `263.SNAPSHOT`
	// matches no numeric shape, so
	// both ends are the build number itself. That is what `//build:idea_air_dist` stamps today.
	want := `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <id>a</id>
  <version>263.99999999.0</version>
  <idea-version since-build="263.SNAPSHOT" until-build="263.SNAPSHOT" />
  <description><![CDATA[<b>x</b>]]></description>
  <extensions />
  <content>
    <module name="a.b"><![CDATA[<idea-plugin package="a.b" separate-jar="true" />]]></module>
  </content>
</idea-plugin>`
	if got := read(t, output); got != want {
		t.Errorf("got:\n%s\nwant:\n%s", got, want)
	}
}

// Plain arguments are accepted too, so the binary is runnable by hand
// (`readArgumentLines` of `DevDistPluginDescriptorMain.kt`).
func TestPlainArgumentsAreAccepted(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	output := filepath.Join(dir, "plugin.out.xml")
	write(t, source, "<idea-plugin><id>a</id></idea-plugin>")

	code := run([]string{
		"--out=" + output,
		"--main-module=intellij.example",
		"--source=" + source,
		"--build-number-file=" + buildNumberFile(t, dir, "263.100.5"),
		"--release-date=20260101",
		"--release-version=2026300",
	})
	if code != 0 {
		t.Fatalf("exit %d", code)
	}
	// No `.SNAPSHOT`, so the version is the build number. `--eap` is absent, so the range is
	// `NEWER_WITH_SAME_BASELINE`: the number without its last segment, and the baseline with a star.
	want := "<idea-plugin>\n  <id>a</id>\n  <version>263.100.5</version>\n" +
		"  <idea-version since-build=\"263.100\" until-build=\"263.*\" />\n</idea-plugin>"
	if got := read(t, output); got != want {
		t.Errorf("got:\n%s\nwant:\n%s", got, want)
	}
}

// `--exact-version` pins both ends to the build number (`CompatibleBuildRange.EXACT` of `PluginXmlPatcher.kt`).
func TestAnExactVersionPinsBothEnds(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	output := filepath.Join(dir, "plugin.out.xml")
	write(t, source, "<idea-plugin><id>a</id></idea-plugin>")

	code := run([]string{
		"--out=" + output, "--main-module=intellij.example", "--source=" + source,
		"--build-number-file=" + buildNumberFile(t, dir, "263.100.5"),
		"--release-date=20260101", "--release-version=2026300",
		"--exact-version=true", "--eap=true",
	})
	if code != 0 {
		t.Fatalf("exit %d", code)
	}
	if got := read(t, output); !strings.Contains(got, `since-build="263.100.5" until-build="263.100.5"`) {
		t.Errorf("got:\n%s", got)
	}
}

// An option the parser does not know fails the run, which is what keeps the two producers on one spelling: a rule that
// grows an option reaches both binaries or neither (`parseDevDistPluginDescriptorRequest` of
// `DevDistPluginDescriptorMain.kt`).
func TestAnUnknownOptionIsRefused(t *testing.T) {
	if code := run([]string{"--not-an-option=1"}); code != 2 {
		t.Errorf("exit %d, want 2", code)
	}
}

// A boolean is strict, the way Kotlin's `toBooleanStrict` is.
func TestALooseBooleanIsRefused(t *testing.T) {
	if code := run([]string{"--eap=yes"}); code != 2 {
		t.Errorf("exit %d, want 2", code)
	}
}

func TestAMissingRequiredOptionIsRefused(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	write(t, source, "<idea-plugin/>")
	for name, arguments := range map[string][]string{
		"no --out":               {"--main-module=m", "--source=" + source, "--build-number-file=b"},
		"no --main-module":       {"--out=o", "--source=" + source, "--build-number-file=b"},
		"no --source":            {"--out=o", "--main-module=m", "--build-number-file=b"},
		"no --build-number-file": {"--out=o", "--main-module=m", "--source=" + source},
	} {
		t.Run(name, func(t *testing.T) {
			if code := run(arguments); code != 2 {
				t.Errorf("exit %d, want 2", code)
			}
		})
	}
}

// A descriptor pair that is not `<load path>=<file>` is refused (`putDescriptor` of `DevDistPluginDescriptorMain.kt`).
func TestAMalformedDescriptorPairIsRefused(t *testing.T) {
	if code := run([]string{"--plugin-descriptor=no-separator"}); code != 2 {
		t.Errorf("exit %d, want 2", code)
	}
	if code := run([]string{"--plugin-descriptor==only-a-file"}); code != 2 {
		t.Errorf("exit %d, want 2", code)
	}
}

// A failure of any stage must leave no output behind. A half-patched descriptor that looks written is worse than a
// failed action: a wrong descriptor fails at class-load time inside the IDE, where nothing here can see it.
func TestAFailedStageWritesNoOutput(t *testing.T) {
	dir := t.TempDir()
	for name, descriptor := range map[string]string{
		"an unresolvable include": `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">` +
			`<xi:include href="absent.xml"/></idea-plugin>`,
		"an undeclared content module": `<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
		"a malformed descriptor":       `<idea-plugin><id>a</id>`,
	} {
		t.Run(name, func(t *testing.T) {
			own := filepath.Join(dir, strings.ReplaceAll(name, " ", "-"))
			if err := os.MkdirAll(own, 0o755); err != nil {
				t.Fatal(err)
			}
			source := filepath.Join(own, "plugin.xml")
			output := filepath.Join(own, "plugin.out.xml")
			write(t, source, descriptor)
			code := run([]string{
				"--out=" + output, "--main-module=intellij.example", "--source=" + source,
				"--build-number-file=" + buildNumberFile(t, own, "263.100.5"),
				"--release-date=20260101", "--release-version=2026300",
			})
			if code != 1 {
				t.Fatalf("exit %d, want 1", code)
			}
			if _, err := os.Stat(output); !os.IsNotExist(err) {
				t.Errorf("the failure wrote %s", output)
			}
		})
	}
}

// A build number the platform's semantic-version check refuses must fail here too, rather than reaching a jar
// (`computePluginBuildNumber` of `SnapshotBuildNumber.kt`).
func TestABuildNumberThatIsNotSemanticIsRefused(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	write(t, source, "<idea-plugin/>")
	code := run([]string{
		"--out=" + filepath.Join(dir, "o.xml"), "--main-module=m", "--source=" + source,
		"--build-number-file=" + buildNumberFile(t, dir, "263.x.1"),
		"--release-date=20260101", "--release-version=2026300",
	})
	if code != 1 {
		t.Errorf("exit %d, want 1", code)
	}
}

func TestAnUnreadableSourceFails(t *testing.T) {
	dir := t.TempDir()
	code := run([]string{
		"--out=" + filepath.Join(dir, "o.xml"), "--main-module=m",
		"--source=" + filepath.Join(dir, "absent.xml"),
		"--build-number-file=" + buildNumberFile(t, dir, "263.100.5"),
		"--release-date=20260101", "--release-version=2026300",
	})
	if code != 1 {
		t.Errorf("exit %d, want 1", code)
	}
}

func write(t *testing.T, path string, content string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

func read(t *testing.T, path string) string {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return string(content)
}
