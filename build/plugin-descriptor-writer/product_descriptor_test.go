// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package main

import (
	"encoding/binary"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

const productDescriptorTestData = "testdata/product_descriptor"

// The expected file is cut from the `META-INF/PyCharmCorePlugin.xml` of `lib/intellij.pycharm.community.jar` that the
// Kotlin `platform_lib` fragment of `PyCharmCore` packed. It keeps the root, three children of the header and four
// content modules, in their order. The write of a root is the write of each child, so the cut is the Kotlin output for
// this source. The descriptors are copies of the module sources that the fragment read.
//
// The source states one module that the plan refuses. The refusal leaves no trace, because the reader drops the
// whitespace around it. No community product scrambles a content module, so the structural tests cover that case.
func TestProductDescriptorMatchesKotlin(t *testing.T) {
	for _, flagfile := range []bool{false, true} {
		name := "direct"
		if flagfile {
			name = "flagfile"
		}
		t.Run(name, func(t *testing.T) {
			dir := t.TempDir()
			output := filepath.Join(dir, "out", "plugin.xml")
			arguments := append([]string{
				"--product-descriptor", "--out=" + output,
				"--source=" + filepath.Join(productDescriptorTestData, "source.xml"),
				"--main-module=intellij.pycharm.community",
				"--refused-content-module=intellij.fixture.refused",
			}, productDescriptorInputs()...)
			if flagfile {
				path := requestFile(t, dir, arguments...)
				write(t, path, strings.ReplaceAll(read(t, path), "\n", "\r\n")+"\r\n")
				arguments = []string{"--flagfile=" + path}
			}
			if code := run(arguments); code != 0 {
				t.Fatalf("exit %d", code)
			}
			if got, expected := read(t, output), read(t, filepath.Join(productDescriptorTestData, "expected.xml")); got != expected {
				t.Errorf("got:\n%s\nwant:\n%s", got, expected)
			}
		})
	}
}

// The expected prefix descriptor is cut from the `plugin-classpath-prefix` that the Kotlin `platform_lib` fragment of
// `PyCharmCore` wrote, in the same way as `expected.xml`. The reload turns every embedded body into escaped text.
func TestPluginClassPathPrefixMatchesKotlin(t *testing.T) {
	dir := t.TempDir()
	prefix := filepath.Join(dir, "plugin-classpath-prefix")
	arguments := append([]string{
		"--product-descriptor", "--out=" + filepath.Join(dir, "plugin.xml"),
		"--source=" + filepath.Join(productDescriptorTestData, "source.xml"),
		"--main-module=intellij.pycharm.community",
		"--refused-content-module=intellij.fixture.refused",
		"--plugin-classpath-prefix=" + prefix,
	}, productDescriptorInputs()...)
	if code := run(arguments); code != 0 {
		t.Fatalf("exit %d", code)
	}
	content := read(t, prefix)
	descriptor := read(t, filepath.Join(productDescriptorTestData, "prefix.expected.xml"))
	if content[0] != pluginClassPathFormatVersion {
		t.Errorf("the format version is %d", content[0])
	}
	if size := binary.BigEndian.Uint32([]byte(content[1:5])); int(size) != len(descriptor) {
		t.Errorf("the size is %d, want %d", size, len(descriptor))
	}
	if got := content[5:]; got != descriptor {
		t.Errorf("got:\n%s\nwant:\n%s", got, descriptor)
	}
}

// The prefix embeds the descriptor of a scrambled module too, because `createCachedProductDescriptor` checks no
// scrambling. So the request declares the descriptor of every content module.
func TestPluginClassPathPrefixEmbedsAScrambledModule(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "source.xml")
	prefix := filepath.Join(dir, "prefix")
	write(t, source, `<idea-plugin><content><module name="a.b"/><module name="closed.source" loading="embedded"/></content></idea-plugin>`)
	write(t, filepath.Join(dir, "a.b.xml"), `<idea-plugin package="a.b"/>`)
	write(t, filepath.Join(dir, "closed.source.xml"), `<idea-plugin package="closed.source"/>`)
	code := run([]string{
		"--product-descriptor", "--out=" + filepath.Join(dir, "plugin.xml"), "--source=" + source, "--main-module=intellij.product",
		"--descriptor=a.b.xml=" + filepath.Join(dir, "a.b.xml"), "--descriptor=closed.source.xml=" + filepath.Join(dir, "closed.source.xml"),
		"--scrambled-content-module=closed.source", "--plugin-classpath-prefix=" + prefix,
		"--classpath-descriptor=" + filepath.Join(dir, "classpath.xml"),
	})
	if code != 0 {
		t.Fatalf("exit %d", code)
	}
	// The classpath descriptor is the descriptor of the prefix, with no header.
	if got, want := read(t, filepath.Join(dir, "classpath.xml")), read(t, prefix)[5:]; got != want {
		t.Errorf("classpath descriptor got:\n%s\nwant:\n%s", got, want)
	}
	if got, want := read(t, filepath.Join(dir, "plugin.xml")), `<idea-plugin>
  <content>
    <module name="a.b"><![CDATA[<idea-plugin package="a.b" />]]></module>
    <module name="closed.source" loading="embedded" />
  </content>
</idea-plugin>`; got != want {
		t.Errorf("descriptor got:\n%s\nwant:\n%s", got, want)
	}
	if got, want := read(t, prefix)[5:], `<idea-plugin>
  <content>
    <module name="a.b">&lt;idea-plugin package=&quot;a.b&quot; /&gt;</module>
    <module name="closed.source" loading="embedded"><![CDATA[<idea-plugin package="closed.source" />]]></module>
  </content>
</idea-plugin>`; got != want {
		t.Errorf("prefix got:\n%s\nwant:\n%s", got, want)
	}
}

// A scrambled content module keeps an empty `<module/>`, and the request declares no descriptor for it. IDEA scrambles
// five modules, and a language server scrambles seven.
func TestProductDescriptorKeepsAScrambledModuleEmpty(t *testing.T) {
	dir := t.TempDir()
	source := filepath.Join(dir, "source.xml")
	output := filepath.Join(dir, "plugin.xml")
	write(t, source, `<idea-plugin><content namespace="jetbrains"><module name="a.b"/>`+
		`<module name="closed.source" loading="embedded"/></content></idea-plugin>`)
	write(t, filepath.Join(dir, "a.b.xml"), `<idea-plugin package="a.b"/>`)
	code := run([]string{
		"--product-descriptor", "--out=" + output, "--source=" + source, "--main-module=intellij.product",
		"--descriptor=a.b.xml=" + filepath.Join(dir, "a.b.xml"), "--scrambled-content-module=closed.source",
	})
	if code != 0 {
		t.Fatalf("exit %d", code)
	}
	// The product descriptor takes no `separate-jar` attribute, although the embedded descriptor states a package.
	if got, want := read(t, output), `<idea-plugin>
  <content namespace="jetbrains">
    <module name="a.b"><![CDATA[<idea-plugin package="a.b" />]]></module>
    <module name="closed.source" loading="embedded" />
  </content>
</idea-plugin>`; got != want {
		t.Errorf("got:\n%s\nwant:\n%s", got, want)
	}
}

func productDescriptorInputs() []string {
	var arguments []string
	for _, loadPath := range []string{
		"intellij.libraries.blockmap.xml",
		"intellij.libraries.sqlite.xml",
		"intellij.platform.debugger.content.xml",
		"intellij.platform.debugger.xml",
		"intellij.platform.ide.osCertificates.xml",
	} {
		arguments = append(arguments, "--descriptor="+loadPath+"="+filepath.Join(productDescriptorTestData, loadPath))
	}
	return append(arguments, "--module=intellij.pycharm.community", "--module=intellij.platform.debugger")
}

func TestProductDescriptorRequest(t *testing.T) {
	parsed, err := parseProductDescriptorRequest([]string{
		"--out=out/plugin.xml", "--source=source.xml", "--product-descriptor", "", "--main-module=intellij.product",
		"--descriptor=a.b.xml=a file=1.xml", "--descriptor-in-jar=META-INF/c.xml=first.jar",
		"--module=second", "--module=first",
		"--refused-content-module=x", "--refused-content-module=y",
		"--scrambled-content-module=a.b", "--scrambled-content-module=a.b",
	})
	if err != nil {
		t.Fatal(err)
	}
	want := productDescriptorRequest{
		embeddedProductRequest: embeddedProductRequest{
			output:           "out/plugin.xml",
			source:           "source.xml",
			descriptors:      map[string]string{"a.b.xml": "a file=1.xml"},
			descriptorsInJar: map[string][]string{"META-INF/c.xml": {"first.jar"}},
			modules:          []string{"second", "first"},
		},
		mainModule: "intellij.product",
		refused:    []string{"x", "y"},
		scrambled:  map[string]bool{"a.b": true},
	}
	if !reflect.DeepEqual(parsed, want) {
		t.Errorf("got %#v, want %#v", parsed, want)
	}
}

func TestProductDescriptorRejectsInvalidRequests(t *testing.T) {
	valid := []string{"--product-descriptor", "--out=o", "--source=s", "--main-module=m"}
	for _, tt := range []struct {
		name      string
		arguments []string
		want      string
	}{
		{"no mode", []string{"--out=o", "--source=s", "--main-module=m"}, "--product-descriptor is required"},
		{"other mode", []string{"--embedded-product", "--out=o"}, "--product-descriptor is required"},
		{"no output", []string{"--product-descriptor", "--source=s", "--main-module=m"}, "--out is required"},
		{"no source", []string{"--product-descriptor", "--out=o", "--main-module=m"}, "--source is required"},
		{"no main module", []string{"--product-descriptor", "--out=o", "--source=s"}, "--main-module is required"},
		{"unknown option", append(valid, "--unknown=1"), "unknown product descriptor option"},
		{"embedded product option", append(valid, "--separate-jar=a.b"), "unknown product descriptor option"},
		{"plugin option", append(valid, "--platform-descriptor=a.xml=a.xml"), "unknown product descriptor option"},
		{"file pair", append(valid, "--descriptor=missing-separator"), "a descriptor is"},
		{"jar pair", append(valid, "--descriptor-in-jar==file.jar"), "a descriptor is"},
	} {
		t.Run(tt.name, func(t *testing.T) {
			if _, err := parseProductDescriptorRequest(tt.arguments); err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("got %v, want %q", err, tt.want)
			}
		})
	}
	if code := run(append(valid, "--unknown=1")); code != 2 {
		t.Errorf("exit %d, want 2", code)
	}
}

func TestProductDescriptorFailuresWriteNoOutput(t *testing.T) {
	for _, tt := range []struct {
		name    string
		source  string
		options []string
		want    []string
	}{
		{
			name:   "undeclared module",
			source: `<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
			want:   []string{"a.b.xml", "no declared descriptor"},
		},
		{
			name:    "unmatched refusal",
			source:  `<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
			options: []string{"--descriptor=a.b.xml=a.b.xml", "--refused-content-module=absent"},
			want:    []string{"intellij.product", "refuses the content modules [absent]"},
		},
		{
			name:    "unmatched scrambled module",
			source:  `<idea-plugin><content><module name="a.b"/></content></idea-plugin>`,
			options: []string{"--descriptor=a.b.xml=a.b.xml", "--scrambled-content-module=absent"},
			want:    []string{"intellij.product", "scrambles the content modules [absent]"},
		},
		{
			name:    "refused scrambled module",
			source:  `<idea-plugin><content><module name="a.b"/><module name="c.d"/></content></idea-plugin>`,
			options: []string{"--descriptor=a.b.xml=a.b.xml", "--refused-content-module=c.d", "--scrambled-content-module=c.d"},
			want:    []string{"scrambles the content modules [c.d]"},
		},
	} {
		t.Run(tt.name, func(t *testing.T) {
			dir := t.TempDir()
			source := filepath.Join(dir, "source.xml")
			output := filepath.Join(dir, "out", "plugin.xml")
			write(t, source, tt.source)
			write(t, filepath.Join(dir, "a.b.xml"), "<idea-plugin/>")
			arguments := []string{"--product-descriptor", "--out=" + output, "--source=" + source, "--main-module=intellij.product"}
			for _, option := range tt.options {
				if strings.HasPrefix(option, "--descriptor=") {
					loadPath, file, _ := strings.Cut(strings.TrimPrefix(option, "--descriptor="), "=")
					option = "--descriptor=" + loadPath + "=" + filepath.Join(dir, file)
				}
				arguments = append(arguments, option)
			}
			parsed, err := parseProductDescriptorRequest(arguments)
			if err != nil {
				t.Fatal(err)
			}
			if _, err := resolveProductDescriptor(parsed); err == nil {
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
