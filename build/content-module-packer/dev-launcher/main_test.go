package main

import (
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"slices"
	"strings"
	"testing"
)

func TestJavaPropertiesFollowPropertiesLoad(test *testing.T) {
	properties, err := parseJavaProperties("# comment\n! comment\n  a=1\nb : 2\nc 3\nd\\=e = x\\\n    y\nf=\\u0041\\tz\nempty\n")
	if err != nil {
		test.Fatal(err)
	}
	expected := map[string]string{"a": "1", "b": "2", "c": "3", "d=e": "xy", "f": "A\tz", "empty": ""}
	for key, value := range expected {
		if properties.values[key] != value {
			test.Errorf("%s = %q, want %q", key, properties.values[key], value)
		}
	}
	if !slices.Equal(properties.keys, []string{"a", "b", "c", "d=e", "f", "empty"}) {
		test.Errorf("order %v", properties.keys)
	}
	if _, err := parseJavaProperties("x=\\u00"); err == nil {
		test.Error("accepted a truncated \\u escape")
	}
}

func TestWrapperArgumentsFollowTheJavaStub(test *testing.T) {
	env := map[string]string{}
	getenv := func(name string) string { return env[name] }
	wrapper, program, err := parseWrapperArguments([]string{"--debug", "--jvm_flag=-Da=1", "mcpServer", "--jvm_flag=-Db=2", "--wrapper_script_flag=--jvm_flags=-Dc=3 -Dd=4"}, getenv)
	if err != nil {
		test.Fatal(err)
	}
	if !slices.Equal(wrapper.debugFlags, []string{"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"}) {
		test.Errorf("debug %v", wrapper.debugFlags)
	}
	if !slices.Equal(wrapper.jvmFlags, []string{"-Da=1", "-Dc=3", "-Dd=4"}) {
		test.Errorf("jvm flags %v", wrapper.jvmFlags)
	}
	if !slices.Equal(program, []string{"mcpServer", "--jvm_flag=-Db=2"}) {
		test.Errorf("program %v", program)
	}
	env["DEFAULT_JVM_DEBUG_SUSPEND"] = "n"
	wrapper, _, _ = parseWrapperArguments([]string{"--wrapper_script_flag=--debug=5006"}, getenv)
	if !slices.Equal(wrapper.debugFlags, []string{"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006"}) {
		test.Errorf("debug %v", wrapper.debugFlags)
	}
	if _, _, err := parseWrapperArguments([]string{"--wrapper_script_flag=--unknown"}, getenv); err == nil {
		test.Error("accepted an unknown wrapper flag")
	}
}

func TestBracesExpandAndBareMacrosStay(test *testing.T) {
	getenv := func(name string) string { return map[string]string{"BUILD_WORKSPACE_DIRECTORY": "/ws"}[name] }
	if got := expandBraces("-Dp=${BUILD_WORKSPACE_DIRECTORY}/out -Dq=$APP_PACKAGE/x ${MISSING}!", getenv); got != "-Dp=/ws/out -Dq=$APP_PACKAGE/x !" {
		test.Errorf("got %q", got)
	}
}

// writeDistribution writes a distribution home with the three files the properties come from.
func writeDistribution(test *testing.T, home string) {
	test.Helper()
	macro := "$IDE_HOME"
	if runtime.GOOS == "darwin" {
		macro = "$APP_PACKAGE/Contents"
	}
	files := map[string]string{
		"bin/idea.properties": "idea.config.path=${user.home}/config\nidea.platform.prefix=fromDistribution\n",
		"bin/idea.vmoptions":  "-Xmx2048m\n-Dsun.io.useCanonCaches=false\n-Dawt.toolkit.name=fromDistribution\n",
		"core-classpath.txt":  "lib/a.jar\n\nlib/b.jar\n",
	}
	info := map[string]any{"launch": []any{map[string]any{
		"additionalJvmArguments": []string{"-Xbootclasspath/a:" + macro + "/lib/nio-fs.jar", "-Djna.boot.library.path=" + macro + "/lib/jna"},
		"customCommands": []any{map[string]any{
			"commands":               []string{"ijLight"},
			"mainClass":              "com.example.LightMain",
			"additionalJvmArguments": []string{"-Dlight=" + macro + "/light", "-Xss4m"},
		}},
	}}}
	content, _ := json.Marshal(info)
	files["bin/product-info.json"] = string(content)
	for name, text := range files {
		file := filepath.Join(home, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(file), 0o755); err != nil {
			test.Fatal(err)
		}
		if err := os.WriteFile(file, []byte(text), 0o644); err != nil {
			test.Fatal(err)
		}
	}
}

func TestDistributionPropertiesFollowGetIdeSystemProperties(test *testing.T) {
	home := test.TempDir()
	writeDistribution(test, home)
	info, err := readProductInfo(home)
	if err != nil {
		test.Fatal(err)
	}
	properties, err := distributionProperties(home, info)
	if err != nil {
		test.Fatal(err)
	}
	expected := map[string]string{
		"idea.config.path":      "${user.home}/config",
		"sun.io.useCanonCaches": "false",
		"jb.vmOptionsFile":      filepath.Join(home, "bin", "idea.vmoptions"),
		"jna.boot.library.path": home + "/lib/jna",
		"awt.toolkit.name":      "fromDistribution",
		"idea.platform.prefix":  "fromDistribution",
	}
	for key, value := range expected {
		if properties.values[key] != value {
			test.Errorf("%s = %q, want %q", key, properties.values[key], value)
		}
	}
	if _, present := properties.values["Xmx2048m"]; present {
		test.Error("a non -D vmoptions line became a property")
	}
	mainClass, command, err := customCommand(home, info, "ijLight")
	if err != nil || mainClass != "com.example.LightMain" || command.values["light"] != home+"/light" || len(command.keys) != 1 {
		test.Errorf("custom command %s %v %v", mainClass, command, err)
	}
	if _, _, err := customCommand(home, info, "other"); err == nil {
		test.Error("found a custom command that the distribution does not declare")
	}
}

// writeLauncher lays out a launcher, its manifest and its runfiles over a distribution without a local layout.
func writeLauncher(test *testing.T, jvmFlags []string) (string, string) {
	test.Helper()
	root := test.TempDir()
	self := filepath.Join(root, "bin", "idea")
	runfilesDir := self + ".runfiles"
	home := filepath.Join(runfilesDir, "_main", "build", "idea_dist_launch.metadata")
	writeDistribution(test, home)
	for name, text := range map[string]string{
		"_main/build/idea_dist_launch.ide.config": "home.path=idea_dist_launch.metadata\nmain.class.name=com.intellij.idea.Main\n",
		"jbr/bin/java": "",
	} {
		file := filepath.Join(runfilesDir, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(file), 0o755); err != nil {
			test.Fatal(err)
		}
		if err := os.WriteFile(file, []byte(text), 0o755); err != nil {
			test.Fatal(err)
		}
	}
	manifest, _ := json.Marshal(launchManifest{
		Version:       1,
		Java:          "jbr/bin/java",
		IdeConfig:     "_main/build/idea_dist_launch.ide.config",
		LocalHomeTool: "jbr/bin/java",
		JvmFlags:      jvmFlags,
		Home:          "out/dev-data/idea/homes",
	})
	if err := os.WriteFile(self+".launch.json", manifest, 0o644); err != nil {
		test.Fatal(err)
	}
	return self, home
}

func TestPrepareBuildsTheJavaCommandLine(test *testing.T) {
	self, home := writeLauncher(test, []string{"-Dawt.toolkit.name=auto", "-Didea.log.path=${BUILD_WORKSPACE_DIRECTORY}/log", "-Dsun.io.useCanonCaches=true"})
	env := map[string]string{"BUILD_WORKSPACE_DIRECTORY": "/ws"}
	prepared, err := prepare([]string{self, "--wrapper_script_flag=--jvm_flag=-Dextra=1", "arg"}, func(name string) string { return env[name] }, io.Discard)
	if err != nil {
		test.Fatal(err)
	}
	argv := strings.Join(prepared.argv, "\n")
	for _, expected := range []string{
		"-Dawt.toolkit.name=auto",
		"-Didea.log.path=/ws/log",
		"-Dextra=1",
		"-Didea.home.path=" + home,
		// the distribution wins over a flag the caller does not own
		"-Dsun.io.useCanonCaches=false",
		"-Didea.platform.prefix=fromDistribution",
		"com.intellij.idea.Main\narg",
		filepath.Join(home, "lib", "a.jar") + string(os.PathListSeparator) + filepath.Join(home, "lib", "b.jar"),
	} {
		if !strings.Contains(argv, expected) {
			test.Errorf("the command line misses %q:\n%s", expected, argv)
		}
	}
	// the caller owns awt.toolkit.name, so the distribution does not override it
	if strings.Contains(argv, "-Dawt.toolkit.name=fromDistribution") {
		test.Errorf("the distribution overrode a caller-owned property:\n%s", argv)
	}
	if prepared.dir != "/ws" || filepath.Base(prepared.java) != "java" {
		test.Errorf("dir %q java %q", prepared.dir, prepared.java)
	}
}

func TestPrepareStartsACustomCommand(test *testing.T) {
	self, home := writeLauncher(test, []string{"-Didea.dev.mode.custom.command=true"})
	prepared, err := prepare([]string{self, "ijLight", "/project"}, func(string) string { return "" }, io.Discard)
	if err != nil {
		test.Fatal(err)
	}
	argv := strings.Join(prepared.argv, "\n")
	if !strings.Contains(argv, "com.example.LightMain\nijLight\n/project") || !strings.Contains(argv, "-Dlight="+home+"/light") {
		test.Errorf("the custom command did not start:\n%s", argv)
	}
	if _, err := prepare([]string{self}, func(string) string { return "" }, io.Discard); err == nil {
		test.Error("started a custom command without its name")
	}
}
