package pluginclasspath

import (
	"bytes"
	"slices"
	"testing"
)

func TestPutMoreLikelyPluginJarsFirst(test *testing.T) {
	for _, scenario := range []struct {
		name      string
		pluginDir string
		input     []string
		expected  []string
	}{
		{"resources last", "demo", []string{"lib/resources_en.jar", "lib/demo.jar"}, []string{"lib/demo.jar", "lib/resources_en.jar"}},
		{"versioned last", "demo", []string{"lib/gson-2.8.0.jar", "lib/junit-m5.jar", "lib/completion-ranking.jar"},
			[]string{"lib/completion-ranking.jar", "lib/junit-m5.jar", "lib/gson-2.8.0.jar"}},
		{"plugin name first", "kotlin", []string{"lib/all-open.jar", "lib/Kotlin-plugin.jar"}, []string{"lib/Kotlin-plugin.jar", "lib/all-open.jar"}},
		{"idea suffix first", "demo", []string{"lib/util.jar", "lib/x-idea.jar"}, []string{"lib/x-idea.jar", "lib/util.jar"}},
		{"database plugin first", "database", []string{"lib/jdbc.jar", "lib/database-plugin.jar"}, []string{"lib/database-plugin.jar", "lib/jdbc.jar"}},
		{"shorter first", "android", []string{"lib/android-base-common.jar", "lib/android.jar"}, []string{"lib/android.jar", "lib/android-base-common.jar"}},
		{"stable for equal names", "demo", []string{"lib/bb.jar", "lib/aa.jar"}, []string{"lib/bb.jar", "lib/aa.jar"}},
		{"UTF-16 length", "demo", []string{"lib/ab.jar", "lib/😀.jar"}, []string{"lib/ab.jar", "lib/😀.jar"}},
		{"all rules", "demo",
			[]string{"lib/resources_en.jar", "lib/gson-2.8.0.jar", "lib/util.jar", "lib/x-idea.jar", "lib/database-plugin.jar", "lib/aaa.jar", "lib/junit-m5.jar", "lib/demo.jar"},
			[]string{"lib/demo.jar", "lib/x-idea.jar", "lib/database-plugin.jar", "lib/aaa.jar", "lib/util.jar", "lib/junit-m5.jar", "lib/gson-2.8.0.jar", "lib/resources_en.jar"}},
	} {
		test.Run(scenario.name, func(test *testing.T) {
			actual := slices.Clone(scenario.input)
			PutMoreLikelyPluginJarsFirst(scenario.pluginDir, actual)
			if !slices.Equal(actual, scenario.expected) {
				test.Fatalf("order = %v, want %v", actual, scenario.expected)
			}
		})
	}
}

func TestFileNameIsLikeVersionedLibraryName(test *testing.T) {
	for name, expected := range map[string]bool{
		"gson-2.8.0.jar":         true,
		"junit-m5.jar":           true,
		"junit-M5.jar":           true,
		"kotlin-stdlib-1.9.jar":  true,
		"completion-ranking.jar": false,
		"x-idea.jar":             false,
		"trailing-":              false,
		"plain.jar":              false,
		"a-m.jar":                false,
		"a-mx.jar":               false,
	} {
		if actual := fileNameIsLikeVersionedLibraryName(name); actual != expected {
			test.Errorf("%s: versioned = %v, want %v", name, actual, expected)
		}
	}
}

func TestOrderKeepsDistinctJarsAndSortsMoreThanOne(test *testing.T) {
	input := []string{"lib/util.jar", "lib/demo.jar", "lib/util.jar"}
	if actual := Order("demo", input); !slices.Equal(actual, []string{"lib/demo.jar", "lib/util.jar"}) {
		test.Fatalf("order = %v", actual)
	}
	if !slices.Equal(input, []string{"lib/util.jar", "lib/demo.jar", "lib/util.jar"}) {
		test.Fatalf("the input changed: %v", input)
	}
	if actual := Order("demo", []string{"lib/zz.jar"}); !slices.Equal(actual, []string{"lib/zz.jar"}) {
		test.Fatalf("one jar = %v", actual)
	}
	if actual := Order("demo", nil); len(actual) != 0 {
		test.Fatalf("no jar = %v", actual)
	}
}

func TestRecordMatchesTheJavaFormat(test *testing.T) {
	descriptor := []byte("<idea-plugin/>\n")
	actual, err := Record("demo", descriptor, []string{"lib/util.jar", "lib/demo.jar", "lib/util.jar"})
	if err != nil {
		test.Fatal(err)
	}
	expected := []byte{0, 2, 0, 4, 'd', 'e', 'm', 'o', 0, 0, 0, byte(len(descriptor))}
	expected = append(expected, descriptor...)
	for _, name := range []string{"lib/demo.jar", "lib/util.jar"} {
		expected = append(append(expected, 0, byte(len(name))), name...)
	}
	if !bytes.Equal(actual, expected) {
		test.Fatalf("record = %x, want %x", actual, expected)
	}
}

func TestRecordKeepsTheDescriptorBytesAndEncodesModifiedUTF8(test *testing.T) {
	descriptor := []byte("<idea-plugin>\n  <id>demo</id>\n</idea-plugin>")
	actual, err := Record("démo😀", descriptor, []string{"lib/😀.jar"})
	if err != nil {
		test.Fatal(err)
	}
	name := ModifiedUTF8("démo😀")
	expected := append([]byte{0, 1, 0, byte(len(name))}, name...)
	expected = append(expected, 0, 0, 0, byte(len(descriptor)))
	expected = append(expected, descriptor...)
	jar := ModifiedUTF8("lib/😀.jar")
	expected = append(append(expected, 0, byte(len(jar))), jar...)
	if !bytes.Equal(actual, expected) {
		test.Fatalf("record = %x, want %x", actual, expected)
	}
	if !bytes.Equal(ModifiedUTF8("\x00é😀"), []byte{0xc0, 0x80, 0xc3, 0xa9, 0xed, 0xa0, 0xbd, 0xed, 0xb8, 0x80}) {
		test.Fatal("modified UTF-8 differs from Java's writeUTF")
	}
}

func TestRecordWithoutClassPathJars(test *testing.T) {
	descriptor := []byte("<idea-plugin/>\n")
	actual, err := Record("demo", descriptor, nil)
	if err != nil {
		test.Fatal(err)
	}
	expected := append([]byte{0, 0, 0, 4, 'd', 'e', 'm', 'o', 0, 0, 0, byte(len(descriptor))}, descriptor...)
	if !bytes.Equal(actual, expected) {
		test.Fatalf("record = %x, want %x", actual, expected)
	}
}
