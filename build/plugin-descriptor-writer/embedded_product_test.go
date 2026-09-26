// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"archive/zip"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

const embeddedProductTestData = "testdata/embedded_product"

// The expected files contain bytes captured from the Kotlin tool before its removal, without a final newline.
func TestEmbeddedProductMatchesKotlin(t *testing.T) {
	for _, flagfile := range []bool{false, true} {
		for _, source := range []string{"source.xml", "simple.xml"} {
			name := source + "/direct"
			if flagfile {
				name = source + "/flagfile"
			}
			t.Run(name, func(t *testing.T) {
				dir := t.TempDir()
				output := filepath.Join(dir, "out", "product.xml")
				arguments := []string{
					"--embedded-product", "--out=" + output,
					"--source=" + filepath.Join(embeddedProductTestData, source),
				}
				want := "simple.expected.xml"
				if source == "source.xml" {
					arguments = append(arguments, embeddedProductInputs(t, dir)...)
					want = "expected.xml"
				}
				if flagfile {
					// Bazel writes multiline arguments. Also accept blank lines and CRLF.
					path := requestFile(t, dir, arguments...)
					write(t, path, strings.ReplaceAll(read(t, path), "\n", "\r\n")+"\r\n")
					arguments = []string{"--flagfile=" + path}
				}
				if code := run(arguments); code != 0 {
					t.Fatalf("exit %d", code)
				}
				if got, expected := read(t, output), read(t, filepath.Join(embeddedProductTestData, want)); got != expected {
					t.Errorf("got:\n%s\nwant:\n%s", got, expected)
				}
			})
		}
	}
}

func TestEmbeddedProductJarSeedOverridesFileSeed(t *testing.T) {
	dir := t.TempDir()
	output := filepath.Join(dir, "product.xml")
	arguments := append([]string{
		"--embedded-product", "--out=" + output,
		"--source=" + filepath.Join(embeddedProductTestData, "source.xml"),
		"--descriptor=intellij.embedded.jar.xml=" + filepath.Join(embeddedProductTestData, "module.xml"),
	}, embeddedProductInputs(t, dir)...)
	if code := run(arguments); code != 0 {
		t.Fatalf("exit %d", code)
	}
	if got, expected := read(t, output), read(t, filepath.Join(embeddedProductTestData, "expected.xml")); got != expected {
		t.Errorf("got:\n%s\nwant:\n%s", got, expected)
	}
}

func TestEmbeddedProductRequest(t *testing.T) {
	parsed, err := parseEmbeddedProductRequest([]string{
		"--out=out/product.xml", "--source=source.xml", "--embedded-product", "",
		"--descriptor=META-INF/extra.xml=a file=1.xml",
		"--descriptor-in-jar=a.b.xml=first.jar", "--descriptor-in-jar=a.b.xml=second.jar",
		"--module=second", "--module=first", "--separate-jar=a.b", "--separate-jar=a.b",
	})
	if err != nil {
		t.Fatal(err)
	}
	want := embeddedProductRequest{
		output:           "out/product.xml",
		source:           "source.xml",
		descriptors:      map[string]string{"META-INF/extra.xml": "a file=1.xml"},
		descriptorsInJar: map[string][]string{"a.b.xml": {"first.jar", "second.jar"}},
		modules:          []string{"second", "first"},
		separateJar:      map[string]bool{"a.b": true},
	}
	if !reflect.DeepEqual(parsed, want) {
		t.Errorf("got %#v, want %#v", parsed, want)
	}
}

func TestEmbeddedProductRejectsInvalidRequests(t *testing.T) {
	for _, tt := range []struct {
		name      string
		arguments []string
		want      string
	}{
		{"no mode", []string{"--out=o", "--source=s"}, "--embedded-product is required"},
		{"no output", []string{"--embedded-product", "--source=s"}, "--out is required"},
		{"no source", []string{"--embedded-product", "--out=o"}, "--source is required"},
		{"empty output", []string{"--embedded-product", "--out=", "--source=s"}, "--out is required"},
		{"empty source", []string{"--embedded-product", "--out=o", "--source="}, "--source is required"},
		{"unknown option", []string{"--embedded-product", "--unknown=1"}, "unknown embedded product descriptor option"},
		{"plugin option", []string{"--embedded-product", "--build-number-file=b"}, "unknown embedded product descriptor option"},
		{"file pair", []string{"--embedded-product", "--descriptor=missing-separator"}, "a descriptor is"},
		{"file load path", []string{"--embedded-product", "--descriptor==file.xml"}, "a descriptor is"},
		{"jar pair", []string{"--embedded-product", "--descriptor-in-jar=missing-separator"}, "a descriptor is"},
		{"jar load path", []string{"--embedded-product", "--descriptor-in-jar==file.jar"}, "a descriptor is"},
	} {
		t.Run(tt.name, func(t *testing.T) {
			if _, err := parseEmbeddedProductRequest(tt.arguments); err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("got %v, want %q", err, tt.want)
			}
			if code := run(tt.arguments); code != 2 {
				t.Errorf("exit %d, want 2", code)
			}
		})
	}
}

func TestEmbeddedProductFailuresWriteNoOutput(t *testing.T) {
	for _, tt := range []struct {
		name        string
		source      string
		descriptors map[string]string
		jars        map[string][]string
		want        []string
	}{
		{
			name: "missing source", want: []string{"source.xml"},
		},
		{
			name: "malformed source", source: "<idea-plugin>", want: []string{`the element "idea-plugin" does not end`},
		},
		{
			name:   "undeclared sibling include",
			source: `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="extra.xml"/></idea-plugin>`,
			want:   []string{"META-INF/extra.xml", "no declared descriptor"},
		},
		{
			name: "undeclared sibling module", source: `<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
			want: []string{"a.b.xml", "no declared descriptor"},
		},
		{
			name:   "prefilled module still needs a descriptor",
			source: `<idea-plugin><content><module name="a.b">existing</module></content></idea-plugin>`,
			want:   []string{"a.b.xml", "no declared descriptor"},
		},
		{
			name: "nameless module", source: `<idea-plugin><content><module/></content></idea-plugin>`,
			want: []string{"states no name"},
		},
		{
			name: "missing declared file", source: "<idea-plugin/>",
			descriptors: map[string]string{"unused.xml": "missing.xml"}, want: []string{"missing.xml"},
		},
		{
			name: "missing declared jar", source: "<idea-plugin/>",
			jars: map[string][]string{"unused.xml": {"missing.jar"}}, want: []string{"missing.jar"},
		},
		{
			name: "invalid jar", source: "<idea-plugin/>",
			jars: map[string][]string{"unused.xml": {"extra.xml"}}, want: []string{"not a valid zip file"},
		},
		{
			name: "missing jar entry", source: "<idea-plugin/>",
			jars: map[string][]string{"absent.xml": {"first.jar", "second.jar"}},
			want: []string{"no declared jar has the entry 'absent.xml'", "first.jar", "second.jar"},
		},
		{
			name:        "malformed include",
			source:      `<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="extra.xml"/></idea-plugin>`,
			descriptors: map[string]string{"META-INF/extra.xml": "malformed.xml"},
			want:        []string{`the element "idea-plugin" does not end`},
		},
		{
			name: "malformed module", source: `<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
			descriptors: map[string]string{"a.b.xml": "malformed.xml"}, want: []string{"a.b.xml", `the element "idea-plugin" does not end`},
		},
		{
			name: "missing nested include", source: `<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
			descriptors: map[string]string{"a.b.xml": "nested.xml"}, want: []string{"META-INF/extra.xml", "no declared descriptor"},
		},
		{
			name:        "prefilled module still resolves includes",
			source:      `<idea-plugin><content><module name="a.b">existing</module></content></idea-plugin>`,
			descriptors: map[string]string{"a.b.xml": "nested.xml"}, want: []string{"META-INF/extra.xml", "no declared descriptor"},
		},
	} {
		t.Run(tt.name, func(t *testing.T) {
			dir := t.TempDir()
			source := filepath.Join(dir, "source.xml")
			output := filepath.Join(dir, "out", "product.xml")
			if tt.source != "" {
				write(t, source, tt.source)
			}
			write(t, filepath.Join(dir, "extra.xml"), "<idea-plugin/>")
			write(t, filepath.Join(dir, "a.b.xml"), "<idea-plugin/>")
			write(t, filepath.Join(dir, "malformed.xml"), "<idea-plugin>")
			write(t, filepath.Join(dir, "nested.xml"),
				`<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="extra.xml"/></idea-plugin>`)
			descriptorJar(t, dir, "first.jar", map[string]string{"other.xml": "<idea-plugin/>"})
			descriptorJar(t, dir, "second.jar", map[string]string{"other.xml": "<idea-plugin/>"})
			arguments := []string{"--embedded-product", "--out=" + output, "--source=" + source}
			for loadPath, file := range tt.descriptors {
				arguments = append(arguments, "--descriptor="+loadPath+"="+filepath.Join(dir, file))
			}
			for loadPath, jars := range tt.jars {
				for _, jar := range jars {
					arguments = append(arguments, "--descriptor-in-jar="+loadPath+"="+filepath.Join(dir, jar))
				}
			}
			parsed, err := parseEmbeddedProductRequest(arguments)
			if err != nil {
				t.Fatal(err)
			}
			if _, err := resolveEmbeddedProduct(parsed); err == nil {
				t.Fatal("expected resolution to fail")
			} else {
				for _, want := range tt.want {
					if !strings.Contains(err.Error(), want) {
						t.Errorf("got %v, want %q", err, want)
					}
				}
			}
			if code := run(arguments); code != 1 {
				t.Errorf("exit %d, want 1", code)
			}
			if _, err := os.Stat(output); !os.IsNotExist(err) {
				t.Errorf("the failure wrote %s", output)
			}
		})
	}
}

func embeddedProductInputs(t *testing.T, dir string) []string {
	t.Helper()
	first := descriptorJar(t, dir, "first.jar", map[string]string{"unrelated.xml": "<idea-plugin/>"})
	second := descriptorJar(t, dir, "second.jar", map[string]string{
		"META-INF/nested.xml":       read(t, filepath.Join(embeddedProductTestData, "META-INF/nested.xml")),
		"intellij.embedded.jar.xml": read(t, filepath.Join(embeddedProductTestData, "intellij.embedded.jar.xml")),
	})
	third := descriptorJar(t, dir, "third.jar", map[string]string{
		"META-INF/nested.xml":       "<idea-plugin><wrong/></idea-plugin>",
		"intellij.embedded.jar.xml": "<idea-plugin package=\"wrong\"/>",
	})
	arguments := []string{
		"--module=intellij.embedded", "--module=intellij.embedded.file",
		"--separate-jar=intellij.embedded.existing", "--separate-jar=intellij.embedded.noPackage",
		"--separate-jar=intellij.embedded/fragment", "--separate-jar=intellij.embedded.jar",
		"--separate-jar=intellij.embedded.file",
	}
	for loadPath, file := range map[string]string{
		"META-INF/content.xml":              "content.xml",
		"META-INF/module-extensions.xml":    "module-extensions.xml",
		"intellij.embedded.existing.xml":    "module.xml",
		"intellij.embedded.noPackage.xml":   "no-package.xml",
		"intellij.embedded.fragment.xml":    "module.xml",
		"intellij.embedded.notSeparate.xml": "module.xml",
		"intellij.embedded.file.xml":        "module.xml",
	} {
		arguments = append(arguments, "--descriptor="+loadPath+"="+filepath.Join(embeddedProductTestData, file))
	}
	for _, loadPath := range []string{"META-INF/nested.xml", "intellij.embedded.jar.xml"} {
		for _, jar := range []string{first, second, third} {
			arguments = append(arguments, "--descriptor-in-jar="+loadPath+"="+jar)
		}
	}
	return arguments
}

func descriptorJar(t *testing.T, dir string, name string, entries map[string]string) string {
	t.Helper()
	path := filepath.Join(dir, name)
	file, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = file.Close() })
	writer := zip.NewWriter(file)
	for name, content := range entries {
		entry, err := writer.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write([]byte(content)); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
	return path
}
