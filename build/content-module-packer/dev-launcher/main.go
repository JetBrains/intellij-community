// dev-launcher starts an IDE from a composed dev distribution, as `bazel run //build:<launcher>` does.
//
// The launcher rule (`intellij_dev_launcher` in community/build/intellij_dev.bzl) bakes everything into
// `<launcher>.launch.json` beside this executable: the Java runtime, the distribution config, the local home tool
// and the JVM flags. The launcher reads nothing from the workspace. It links the distribution's local home, derives
// the distribution's system properties the way `PreBuiltDevMain` does, changes to `BUILD_WORKSPACE_DIRECTORY`, and
// replaces itself with the IDE's JVM, so the IDE runs with the launcher's process ID.
package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"
)

// launchManifest is `<launcher>.launch.json`. Every path is a runfiles path except [Home].
type launchManifest struct {
	Version       int      `json:"version"`
	Java          string   `json:"java"`
	IdeConfig     string   `json:"ideConfig"`
	LocalHomeTool string   `json:"localHomeTool"`
	BeforeRun     string   `json:"beforeRun,omitempty"`
	JvmFlags      []string `json:"jvmFlags"`
	// Home is the workspace-relative directory under which each launch links its local home.
	Home string `json:"home"`
}

// launch is the process the launcher becomes.
type launch struct {
	java string
	argv []string
	env  []string
	dir  string
}

func main() {
	prepared, err := prepare(os.Args, os.Getenv, os.Stderr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		os.Exit(1)
	}
	os.Exit(execute(prepared))
}

func prepare(args []string, getenv func(string) string, errors io.Writer) (launch, error) {
	self, err := filepath.Abs(args[0])
	if err != nil {
		return launch{}, err
	}
	manifest, err := readLaunchManifest(strings.TrimSuffix(self, ".exe") + ".launch.json")
	if err != nil {
		return launch{}, err
	}
	files, err := findRunfiles(self, getenv)
	if err != nil {
		return launch{}, err
	}
	wrapper, programArgs, err := parseWrapperArguments(args[1:], getenv)
	if err != nil {
		return launch{}, err
	}
	workspace := getenv("BUILD_WORKSPACE_DIRECTORY")
	expand := func(value string) string { return expandBraces(value, getenv) }

	commandLine := wrapper.debugFlags
	commandLine = append(commandLine, strings.Fields(getenv("JVM_FLAGS"))...)
	for _, flag := range manifest.JvmFlags {
		commandLine = append(commandLine, expand(flag))
	}
	commandLine = append(commandLine, wrapper.jvmFlags...)
	for index, argument := range programArgs {
		programArgs[index] = expand(argument)
	}

	configFile, err := files.rlocation(manifest.IdeConfig)
	if err != nil {
		return launch{}, err
	}
	distributionHome, mainClass, err := readIdeConfig(configFile)
	if err != nil {
		return launch{}, err
	}
	home := distributionHome
	if _, err := os.Stat(filepath.Join(distributionHome, "local-layout.json")); err == nil {
		home, err = linkLocalHome(files, manifest, distributionHome, workspace, getenv, errors)
		if err != nil {
			return launch{}, err
		}
	}

	info, err := readProductInfo(home)
	if err != nil {
		return launch{}, err
	}
	properties, err := distributionProperties(home, info)
	if err != nil {
		return launch{}, err
	}
	callerProperties := newOrderedProperties()
	for _, flag := range commandLine {
		putSystemProperty(callerProperties, flag)
	}
	if strings.EqualFold(callerProperties.values["idea.dev.mode.custom.command"], "true") {
		if len(programArgs) == 0 {
			return launch{}, fmt.Errorf("-Didea.dev.mode.custom.command=true needs the command as the first program argument")
		}
		commandMainClass, commandProperties, err := customCommand(home, info, programArgs[0])
		if err != nil {
			return launch{}, err
		}
		mainClass = commandMainClass
		for _, key := range commandProperties.keys {
			properties.put(key, commandProperties.values[key])
		}
	}

	classpath, err := readClasspath(home)
	if err != nil {
		return launch{}, err
	}
	java, err := files.rlocation(manifest.Java)
	if err != nil {
		return launch{}, err
	}
	argv := []string{java}
	argv = append(argv, commandLine...)
	// PreBuiltDevMain sets these three before the distribution's properties, which may override them.
	argv = append(argv, "-Didea.vendor.name=JetBrains", "-Didea.use.dev.build.server=true", "-Didea.home.path="+home)
	for _, key := range properties.keys {
		if _, set := callerProperties.values[key]; set && isCallerOwnedProperty(key) {
			continue
		}
		argv = append(argv, "-D"+key+"="+properties.values[key])
	}
	argv = append(argv, "-cp", strings.Join(classpath, string(os.PathListSeparator)), mainClass)
	argv = append(argv, programArgs...)

	env := append(os.Environ(), files.environment()...)
	if manifest.BeforeRun != "" {
		if err := runBeforeRun(files, manifest.BeforeRun, env, workspace); err != nil {
			return launch{}, err
		}
	}
	return launch{java: java, argv: argv, env: env, dir: workspace}, nil
}

func readLaunchManifest(file string) (launchManifest, error) {
	var result launchManifest
	content, err := os.Open(file)
	if err != nil {
		return result, fmt.Errorf("read the launch manifest: %w", err)
	}
	defer content.Close()
	decoder := json.NewDecoder(content)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&result); err != nil {
		return result, fmt.Errorf("read the launch manifest %s: %w", file, err)
	}
	if result.Version != 1 {
		return result, fmt.Errorf("unsupported launch manifest version: %d", result.Version)
	}
	return result, nil
}

// readIdeConfig reads the DevIdeConfig file: the distribution home, relative to the file, and the IDE main class.
func readIdeConfig(file string) (string, string, error) {
	content, err := os.ReadFile(file)
	if err != nil {
		return "", "", err
	}
	properties, err := parseJavaProperties(string(content))
	if err != nil {
		return "", "", err
	}
	home, mainClass := properties.values["home.path"], properties.values["main.class.name"]
	if home == "" || mainClass == "" {
		return "", "", fmt.Errorf("%s states no home.path or no main.class.name", file)
	}
	if !filepath.IsAbs(home) {
		home = filepath.Join(filepath.Dir(file), filepath.FromSlash(home))
	}
	return home, mainClass, nil
}

func readClasspath(home string) ([]string, error) {
	lines, err := readLines(filepath.Join(home, "core-classpath.txt"))
	if err != nil {
		return nil, err
	}
	var result []string
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if !filepath.IsAbs(line) {
			line = filepath.Join(home, filepath.FromSlash(line))
		}
		result = append(result, line)
	}
	return result, nil
}

// isCallerOwnedProperty names the properties a launcher's command line keeps against the distribution's own, as
// `PreBuiltDevMain.isCallerOwnedProperty` does.
func isCallerOwnedProperty(name string) bool {
	lower := strings.ToLower(name)
	return strings.HasPrefix(lower, "rider.") || strings.HasPrefix(lower, "resharper.") ||
		name == "idea.platform.prefix" || name == "idea.suppressed.plugins.set.selector" || name == "awt.toolkit.name"
}

// wrapperArguments are the java stub's own options, see java_stub_template.txt of rules_java.
type wrapperArguments struct {
	debugFlags []string
	jvmFlags   []string
}

// parseWrapperArguments takes the java stub's wrapper options: the leading ones until the first other argument, and
// `--wrapper_script_flag=<option>` anywhere. The IDE's Bazel plugin debugs a launcher through them.
func parseWrapperArguments(args []string, getenv func(string) string) (wrapperArguments, []string, error) {
	var result wrapperArguments
	var programArgs []string
	debugPort := ""
	process := func(argument string) bool {
		switch {
		case argument == "--debug":
			debugPort = getenv("DEFAULT_JVM_DEBUG_PORT")
			if debugPort == "" {
				debugPort = "5005"
			}
		case strings.HasPrefix(argument, "--debug="):
			debugPort = strings.TrimPrefix(argument, "--debug=")
		case strings.HasPrefix(argument, "--jvm_flag="):
			result.jvmFlags = append(result.jvmFlags, strings.TrimPrefix(argument, "--jvm_flag="))
		case strings.HasPrefix(argument, "--jvm_flags="):
			result.jvmFlags = append(result.jvmFlags, strings.Fields(strings.TrimPrefix(argument, "--jvm_flags="))...)
		default:
			return false
		}
		return true
	}
	for _, argument := range args {
		if option, isWrapper := strings.CutPrefix(argument, "--wrapper_script_flag="); isWrapper {
			if !process(option) {
				return result, nil, fmt.Errorf("invalid wrapper argument '%s'", argument)
			}
		} else if len(programArgs) > 0 || !process(argument) {
			programArgs = append(programArgs, argument)
		}
	}
	if debugPort != "" {
		suspend := getenv("DEFAULT_JVM_DEBUG_SUSPEND")
		if suspend == "" {
			suspend = "y"
		}
		result.debugFlags = []string{"-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + suspend + ",address=" + debugPort}
	}
	return result, programArgs, nil
}

// expandBraces replaces every `${NAME}` with the environment variable NAME, as the java stub's shell expansion of its
// JVM flags does. A bare `$NAME` stays, so a value such as `$APP_PACKAGE` reaches the IDE unchanged.
func expandBraces(value string, getenv func(string) string) string {
	var builder strings.Builder
	for {
		start := strings.Index(value, "${")
		if start < 0 {
			builder.WriteString(value)
			return builder.String()
		}
		end := strings.IndexByte(value[start:], '}')
		if end < 0 {
			builder.WriteString(value)
			return builder.String()
		}
		builder.WriteString(value[:start])
		builder.WriteString(getenv(value[start+2 : start+end]))
		value = value[start+end+1:]
	}
}

// linkLocalHome links the local home of the distribution at [distributionHome] into a directory of its own under
// [workspace]/[launchManifest.Home], named after this process, and removes the homes of processes that no longer run.
// Without a workspace the home goes into a temporary directory.
func linkLocalHome(files runfiles, manifest launchManifest, distributionHome, workspace string, getenv func(string) string, errors io.Writer) (string, error) {
	tool, err := files.rlocation(manifest.LocalHomeTool)
	if err != nil {
		return "", err
	}
	var home string
	if workspace == "" {
		parent, err := os.MkdirTemp("", "idea-dev-home-")
		if err != nil {
			return "", err
		}
		home = filepath.Join(parent, "home")
	} else {
		homes := filepath.Join(workspace, filepath.FromSlash(manifest.Home))
		removeStaleHomes(homes, errors)
		home = filepath.Join(homes, strconv.Itoa(os.Getpid()))
		if err := os.RemoveAll(home); err != nil {
			return "", err
		}
	}
	command := exec.Command(tool, "local-home", "--layout="+filepath.Join(distributionHome, "local-layout.json"), "--output-dir="+home)
	command.Env = append(os.Environ(), files.environment()...)
	command.Stdout = io.Discard
	command.Stderr = errors
	if err := command.Run(); err != nil {
		return "", fmt.Errorf("cannot prepare the local dev home: %w", err)
	}
	return home, nil
}

// removeStaleHomes deletes each home under [homes] whose process no longer runs.
func removeStaleHomes(homes string, errors io.Writer) {
	entries, err := os.ReadDir(homes)
	if err != nil {
		return
	}
	for _, entry := range entries {
		pid, err := strconv.Atoi(entry.Name())
		if err != nil || !entry.IsDir() || pid == os.Getpid() || processRuns(pid) {
			continue
		}
		if err := os.RemoveAll(filepath.Join(homes, entry.Name())); err != nil {
			fmt.Fprintf(errors, "WARNING: cannot remove the stale dev home %s: %v\n", entry.Name(), err)
		}
	}
}

func processRuns(pid int) bool {
	process, err := os.FindProcess(pid)
	if err != nil {
		return false
	}
	if runtime.GOOS == "windows" {
		// FindProcess opens the process on Windows and fails for one that exited.
		process.Release()
		return true
	}
	return process.Signal(syscall.Signal(0)) == nil
}

// runBeforeRun runs the before-run executable [executable], a runfile, in the workspace, and fails when it fails.
func runBeforeRun(files runfiles, executable string, env []string, workspace string) error {
	program, err := files.rlocation(executable)
	if err != nil {
		return err
	}
	command := exec.Command(program)
	command.Env = env
	command.Dir = workspace
	command.Stdin, command.Stdout, command.Stderr = os.Stdin, os.Stdout, os.Stderr
	if err := command.Run(); err != nil {
		return fmt.Errorf("the before-run step %s failed: %w", executable, err)
	}
	return nil
}

// execute replaces this process with the IDE's JVM, or runs it as a child and returns its exit code where a process
// cannot replace itself.
func execute(prepared launch) int {
	if prepared.dir != "" {
		if err := os.Chdir(prepared.dir); err != nil {
			fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
			return 1
		}
	}
	if runtime.GOOS != "windows" {
		err := syscall.Exec(prepared.java, prepared.argv, prepared.env)
		fmt.Fprintf(os.Stderr, "ERROR: cannot start %s: %v\n", prepared.java, err)
		return 1
	}
	command := exec.Command(prepared.java, prepared.argv[1:]...)
	command.Env = prepared.env
	command.Stdin, command.Stdout, command.Stderr = os.Stdin, os.Stdout, os.Stderr
	if err := command.Run(); err != nil {
		if exitError, isExit := err.(*exec.ExitError); isExit {
			return exitError.ExitCode()
		}
		fmt.Fprintf(os.Stderr, "ERROR: cannot start %s: %v\n", prepared.java, err)
		return 1
	}
	return 0
}
