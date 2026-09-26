package main

import (
	"bytes"
	"testing"

	"jetbrains.com/content-module-packer/internal/pluginclasspath"
)

func TestPluginClassPathRecordMatchesTheJavaFormat(test *testing.T) {
	files := []sourcedFile{
		{RelativePath: "plugins/demo/lib/util.jar", classPath: true},
		{RelativePath: "plugins/demo/lib/modules/demo.split.jar"},
		{RelativePath: "plugins/demo/lib/demo.jar", classPath: true},
		{RelativePath: "plugins/demo/lib/util.jar", classPath: true},
	}
	descriptor := []byte("<idea-plugin/>\n")
	actual, err := pluginClassPathRecord("plugins/demo", descriptor, files)
	if err != nil {
		test.Fatal(err)
	}
	if expected := pluginClassPathFixture("demo", "lib/demo.jar", "lib/util.jar"); !bytes.Equal(actual, expected) {
		test.Fatalf("record = %x, want %x", actual, expected)
	}
	if err := validatePluginClassPath(actual, "plugins/demo", files); err != nil {
		test.Fatal(err)
	}
}

func TestPluginClassPathRecordKeepsTheDescriptorBytes(test *testing.T) {
	descriptor := []byte("<idea-plugin>\n  <id>demo</id>\n</idea-plugin>")
	files := []sourcedFile{{RelativePath: "plugins/démo😀/lib/😀.jar", classPath: true}}
	actual, err := pluginClassPathRecord("plugins/démo😀", descriptor, files)
	if err != nil {
		test.Fatal(err)
	}
	name := pluginclasspath.ModifiedUTF8("démo😀")
	expected := append([]byte{0, 1, 0, byte(len(name))}, name...)
	expected = append(expected, 0, 0, 0, byte(len(descriptor)))
	expected = append(expected, descriptor...)
	jar := pluginclasspath.ModifiedUTF8("lib/😀.jar")
	expected = append(append(expected, 0, byte(len(jar))), jar...)
	if !bytes.Equal(actual, expected) {
		test.Fatalf("record = %x, want %x", actual, expected)
	}
	if err := validatePluginClassPath(actual, "plugins/démo😀", files); err != nil {
		test.Fatal(err)
	}
}

func TestPluginClassPathRecordWithoutClassPathJars(test *testing.T) {
	files := []sourcedFile{{RelativePath: "plugins/demo/lib/modules/demo.split.jar"}}
	actual, err := pluginClassPathRecord("plugins/demo", []byte("<idea-plugin/>\n"), files)
	if err != nil {
		test.Fatal(err)
	}
	if expected := pluginClassPathFixture("demo"); !bytes.Equal(actual, expected) {
		test.Fatalf("record = %x, want %x", actual, expected)
	}
}
