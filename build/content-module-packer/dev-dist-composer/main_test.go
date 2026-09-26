package main

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func cliArgs(extra ...string) []string {
	return append([]string{
		"--composition-spec=composition.json", "--output-dir=out/dist", "--ide-config=out/dist.ide.config", "--fingerprint=out/dist.fingerprint",
	}, extra...)
}

// writeCLIFixture writes two components, the sources their manifests name, and the plugin classpath inputs. The paths
// are relative to the working directory, as Bazel gives them to an action.
func writeCLIFixture(test *testing.T, localLaunch bool) {
	test.Helper()
	writeTestFile(test, "fragments/core/lib/util.jar", "util")
	writeTestFile(test, "fragments/core/bin/idea.properties", "properties")
	writeTestFile(test, "inputs/packed.jar", "packed")
	writeTestBytes(test, "fragments/prefix.bin", []byte{3, 1})
	writeTestBytes(test, "fragments/plugins.part", []byte{7})
	writeTestFile(test, "fragments/core.json", `{"kind":"platform_core","platformPrefix":"idea","os":"linux","arch":"x64",`+
		`"additionalModules":[],"mainClass":"com.intellij.idea.Main","coreClassPath":["lib/util.jar","lib/app.jar"],"entries":[`+
		`{"relativePath":"bin/idea.properties","type":"component-file","hash":1,"source":"fragments/core/bin/idea.properties"},`+
		`{"relativePath":"lib/util.jar","type":"component-file","hash":2,"source":"fragments/core/lib/util.jar"}]}`)
	writeTestFile(test, "fragments/plugins.json", `{"kind":"plugins","platformPrefix":"idea","os":"","arch":"",`+
		`"additionalModules":["intellij.packed"],"mainClass":null,"coreClassPath":[],"pluginCount":1,"entries":[`+
		`{"relativePath":"plugins/packed/lib/packed.jar","type":"component-file","hash":3,"source":"inputs/packed.jar"}]}`)
	sourceRunfiles := "null"
	if localLaunch {
		sourceRunfiles = `{"fragments/core/bin/idea.properties":"_main/fragments/core/bin/idea.properties",` +
			`"fragments/core/lib/util.jar":"_main/fragments/core/lib/util.jar","inputs/packed.jar":"_main/inputs/packed.jar"}`
	}
	writeTestFile(test, "composition.json", `{"version":1,"expectedFragments":["platform_core","plugins"],`+
		`"additionalModules":["intellij.packed","intellij.bundled"],"components":[`+
		`{"manifest":"fragments/core.json","pluginClasspathPart":null},`+
		`{"manifest":"fragments/plugins.json","pluginClasspathPart":"fragments/plugins.part"}],`+
		`"pluginClasspathPrefix":"fragments/prefix.bin","sourceRunfiles":`+sourceRunfiles+`,"sourceDirectoryRunfiles":{},"sourceBindings":null}`)
}

func runCLI(test *testing.T, args []string) (int, string) {
	test.Helper()
	var errors bytes.Buffer
	code := run(args, &errors)
	return code, errors.String()
}

func TestComposeCLIWritesTheDistributionAndItsLaunchFiles(test *testing.T) {
	test.Chdir(tempDir(test))
	writeCLIFixture(test, false)
	writeTestFile(test, "out/dist/stale.txt", "stale")
	if code, errors := runCLI(test, cliArgs("--trace-file=out/spans.json")); code != 0 {
		test.Fatalf("exit = %d: %s", code, errors)
	}
	requireAbsent(test, "out/dist/stale.txt")
	for name, content := range map[string]string{
		"out/dist/lib/util.jar":                  "util",
		"out/dist/bin/idea.properties":           "properties",
		"out/dist/plugins/packed/lib/packed.jar": "packed",
		"out/dist/plugins/plugin-classpath.txt":  "\x03\x01\x00\x01\x07",
		"out/dist/core-classpath.txt":            "lib/util.jar\nlib/app.jar",
		"out/dist.ide.config":                    "home.path=dist\nmain.class.name=com.intellij.idea.Main\nplatform.prefix=idea\nadditional.modules=intellij.packed,intellij.bundled\n",
		"out/dist/fingerprint.txt":               readTestFile(test, "out/dist.fingerprint"),
	} {
		if actual := readTestFile(test, name); actual != content {
			test.Errorf("%s = %q, expected %q", name, actual, content)
		}
	}
	if fingerprint := readTestFile(test, "out/dist.fingerprint"); !strings.HasPrefix(fingerprint, "v5:") {
		test.Errorf("fingerprint = %q", fingerprint)
	}
	var trace struct {
		Data []struct {
			Spans []struct {
				OperationName string `json:"operationName"`
			} `json:"spans"`
		} `json:"data"`
	}
	if err := json.Unmarshal([]byte(readTestFile(test, "out/spans.json")), &trace); err != nil || len(trace.Data) != 1 ||
		len(trace.Data[0].Spans) != 3 || trace.Data[0].Spans[0].OperationName != jobName {
		test.Fatalf("trace = %+v, error = %v", trace, err)
	}
}

func TestComposeCLIWritesLocalLaunchMetadataWithoutPayload(test *testing.T) {
	test.Chdir(tempDir(test))
	writeCLIFixture(test, true)
	if code, errors := runCLI(test, cliArgs()); code != 0 {
		test.Fatalf("exit = %d: %s", code, errors)
	}
	requireAbsent(test, "out/dist/lib")
	requireLayout(test, "out/dist", `"runfile":"_main/fragments/core/lib/util.jar"`, `"runfile":"_main/inputs/packed.jar"`,
		`"metadata":["core-classpath.txt","fingerprint.txt","plugins/plugin-classpath.txt"]`)
	if readTestFile(test, "out/dist/plugins/plugin-classpath.txt") != "\x03\x01\x00\x01\x07" {
		test.Fatal("the local metadata has no plugin classpath")
	}
}

func TestComposeCLIRejectsInvalidOptions(test *testing.T) {
	test.Chdir(tempDir(test))
	writeCLIFixture(test, false)
	for _, invalid := range []struct {
		args    []string
		message string
	}{
		{[]string{"composition.json"}, "Expected an option in the '--key=value' form, but got 'composition.json'"},
		{cliArgs("--trace-file=a", "--trace-file=b"), "--trace-file must be specified at most once, but got 2 values: [a, b]"},
		{cliArgs("--zeta=1", "--alpha"), "Unknown options: --alpha, --zeta"},
		{[]string{"--composition-spec=composition.json", "--output-dir="}, "--output-dir is required (no value and no fallback available)"},
		{[]string{"--output-dir=out"}, "--composition-spec is required"},
		{[]string{"--composition-spec=absent.json"}, "absent.json"},
	} {
		code, errors := runCLI(test, invalid.args)
		if code != 1 || !strings.Contains(errors, invalid.message) {
			test.Errorf("%q: exit = %d, errors = %q", invalid.args, code, errors)
		}
	}
	requireAbsent(test, "out")
}

func TestComposeCLIRejectsSourceBindingsForLocalLaunchMetadata(test *testing.T) {
	test.Chdir(tempDir(test))
	writeCLIFixture(test, true)
	spec := strings.Replace(readTestFile(test, "composition.json"), `"sourceBindings":null`, `"sourceBindings":"bindings.jsonl"`, 1)
	writeTestFile(test, "composition.json", spec)
	if code, errors := runCLI(test, cliArgs()); code != 1 || !strings.Contains(errors, "Local launch metadata must not expand source bindings") {
		test.Fatalf("exit = %d, errors = %q", code, errors)
	}
}

func TestWriteDevIdeConfig(test *testing.T) {
	directory := tempDir(test)
	for _, sample := range []struct {
		config, home, homePath string
	}{
		{filepath.Join(directory, "a/dist.ide.config"), filepath.Join(directory, "a/dist"), "dist"},
		{filepath.Join(directory, "b/dist.ide.config"), filepath.Join(directory, "b/nested/dist"), "nested/dist"},
		{filepath.Join(directory, "c/dist.ide.config"), filepath.Join(directory, "c"), ""},
		{filepath.Join(directory, "d/dist.ide.config"), filepath.Join(directory, "other/dist"), filepath.ToSlash(filepath.Join(directory, "other/dist"))},
		{filepath.Join(directory, "e/dist.ide.config"), filepath.Join(directory, "ee/dist"), filepath.ToSlash(filepath.Join(directory, "ee/dist"))},
	} {
		if err := writeDevIdeConfig(sample.config, sample.home, "Main", "idea", []string{"a", "b"}); err != nil {
			test.Fatal(err)
		}
		expected := "home.path=" + sample.homePath + "\nmain.class.name=Main\nplatform.prefix=idea\nadditional.modules=a,b\n"
		if actual := readTestFile(test, sample.config); actual != expected {
			test.Errorf("config = %q, expected %q", actual, expected)
		}
	}
	if err := writeDevIdeConfig(filepath.Join(directory, "f.config"), directory, "Main", "idea", nil); err != nil {
		test.Fatal(err)
	}
	if actual := readTestFile(test, filepath.Join(directory, "f.config")); !strings.HasSuffix(actual, "additional.modules=\n") {
		test.Errorf("config = %q", actual)
	}
	if err := os.Remove(filepath.Join(directory, "f.config")); err != nil {
		test.Fatal(err)
	}
}
