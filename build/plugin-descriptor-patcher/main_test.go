// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// What this file covers is the argument surface and the refusal. The bytes are gated in `internal/descriptorxml` and
// `internal/stamps`, against the platform.

func TestAStampedDescriptorIsWritten(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	output := filepath.Join(dir, "out", "plugin.xml")
	write(t, source, "<idea-plugin><id>a</id><description>&lt;b&gt;x&lt;/b&gt;</description></idea-plugin>")

	code := run([]string{
		"--source=" + source, "--output=" + output,
		"--version=1.2.3", "--since-build=263", "--until-build=263.*",
		"--release-date=20260101", "--release-version=2026300", "--eap",
	})
	if code != 0 {
		t.Fatalf("exit %d", code)
	}
	want := "<idea-plugin>\n" +
		"  <id>a</id>\n" +
		"  <version>1.2.3</version>\n" +
		"  <idea-version since-build=\"263\" until-build=\"263.*\" />\n" +
		"  <description><![CDATA[<b>x</b>]]></description>\n" +
		"</idea-plugin>"
	if got := read(t, output); got != want {
		t.Errorf("got:\n%s\nwant:\n%s", got, want)
	}
}

func TestAFlagFileCarriesTheSameOptions(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	output := filepath.Join(dir, "plugin.out.xml")
	write(t, source, "<idea-plugin><id>a</id></idea-plugin>")
	flagFile := filepath.Join(dir, "arguments.txt")
	write(t, flagFile, "# a generated header\n\n--source="+source+"\n--output="+output+"\n--version=9\n--since-build=1\n--until-build=2\n")

	if code := run([]string{"--flagfile=" + flagFile}); code != 0 {
		t.Fatalf("exit %d", code)
	}
	want := "<idea-plugin>\n  <id>a</id>\n  <version>9</version>\n  <idea-version since-build=\"1\" until-build=\"2\" />\n</idea-plugin>"
	if got := read(t, output); got != want {
		t.Errorf("got:\n%s\nwant:\n%s", got, want)
	}
}

// The two unported stages must refuse, and the refusal must not leave an output behind. A half-patched descriptor that
// looks written is worse than a failed action.
func TestAnUnportedStageIsRefused(t *testing.T) {
	cases := map[string]string{
		"an xi:include": "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><id>a</id><xi:include href=\"h\"/></idea-plugin>",
		"an xi:include one level down": "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><id>a</id>" +
			"<extensions><xi:include href=\"h\"/></extensions></idea-plugin>",
		"a declared content module": "<idea-plugin><id>a</id><content><module name=\"a.b\"/></content></idea-plugin>",
	}
	for name, descriptor := range cases {
		t.Run(name, func(t *testing.T) {
			dir := t.TempDir()
			source := filepath.Join(dir, "plugin.xml")
			output := filepath.Join(dir, "plugin.out.xml")
			write(t, source, descriptor)
			if code := run([]string{"--source=" + source, "--output=" + output, "--version=1", "--since-build=1", "--until-build=2"}); code != 1 {
				t.Fatalf("exit %d, want 1", code)
			}
			if _, err := os.Stat(output); !os.IsNotExist(err) {
				t.Errorf("the refusal wrote %s", output)
			}
		})
	}
}

// An empty `<content>` reaches no unported stage. 42 of the 43 plugins the JVM tool already patches are of that shape.
func TestAnEmptyContentElementIsNotRefused(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	output := filepath.Join(dir, "plugin.out.xml")
	write(t, source, "<idea-plugin><id>a</id><content></content></idea-plugin>")
	if code := run([]string{"--source=" + source, "--output=" + output, "--version=1", "--since-build=1", "--until-build=2"}); code != 0 {
		t.Fatalf("exit %d", code)
	}
}

// A `<module` inside a CDATA section is prose, not a declaration. A text search would refuse this descriptor.
func TestAModuleInsideCdataIsNotADeclaration(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	output := filepath.Join(dir, "plugin.out.xml")
	write(t, source, "<idea-plugin><id>a</id><description><![CDATA[<content><module name=\"x\"/></content>]]></description></idea-plugin>")
	if code := run([]string{"--source=" + source, "--output=" + output, "--version=1", "--since-build=1", "--until-build=2"}); code != 0 {
		t.Fatalf("exit %d", code)
	}
	if got := read(t, output); !strings.Contains(got, "<![CDATA[<content><module name=\"x\"/></content>]]>") {
		t.Errorf("the CDATA section did not survive:\n%s", got)
	}
}

func TestMissingArgumentsAreRefused(t *testing.T) {
	if code := run([]string{"--source=x"}); code != 2 {
		t.Errorf("exit %d, want 2", code)
	}
	if code := run([]string{"--output=x"}); code != 2 {
		t.Errorf("exit %d, want 2", code)
	}
}

func TestAnUnreadableSourceFails(t *testing.T) {
	dir := t.TempDir()
	if code := run([]string{"--source=" + filepath.Join(dir, "absent.xml"), "--output=" + filepath.Join(dir, "o.xml")}); code != 1 {
		t.Errorf("exit %d, want 1", code)
	}
}

func TestAMalformedSourceFails(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "plugin.xml")
	write(t, source, "<idea-plugin><id>a</id>")
	if code := run([]string{"--source=" + source, "--output=" + filepath.Join(dir, "o.xml")}); code != 1 {
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
