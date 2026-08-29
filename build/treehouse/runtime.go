package main

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	goruntime "runtime"
	"strings"
	"time"
)

// treehouseCommandName is the placeholder that stands for the Treehouse CLI in a
// spawned command. Runtime.Spawn replaces it with the resolved binary path.
const treehouseCommandName = "treehouse"

// treehouseCLIRlocation is the runfiles path of the Treehouse CLI binary. BUILD.bazel
// injects the value with `x_defs`. An empty value leaves only the TREEHOUSE_CLI_BIN
// override.
var treehouseCLIRlocation string

// SpawnResult holds the outcome of one child process.
type SpawnResult struct {
	ExitCode int
	Stdout   string
	Stderr   string
}

// SpawnOptions selects the standard-stream wiring of a child process.
type SpawnOptions struct {
	// Interactive gives the child the wrapper's own streams, so a prompt of the child
	// is answerable. The captured Stdout and Stderr are then empty.
	Interactive bool
}

// Runtime holds every effect of the wrapper. A test replaces it with a fake.
type Runtime interface {
	Cwd() string
	Env(name string) (string, bool)
	// IsTTY reports whether a person can answer a prompt of a child process.
	IsTTY() bool
	Now() time.Time
	UUID() string
	ReadTextFile(path string) (string, error)
	WriteTextFile(path string, text string) error
	RemoveFile(path string) error
	Spawn(command []string, options SpawnOptions) SpawnResult
}

// osRuntime is the Runtime of the real process.
type osRuntime struct {
	cwd string
}

func newOSRuntime() *osRuntime {
	cwd, err := os.Getwd()
	if err != nil {
		cwd = "."
	}
	return &osRuntime{cwd: cwd}
}

func (r *osRuntime) Cwd() string { return r.cwd }

func (r *osRuntime) Env(name string) (string, bool) { return os.LookupEnv(name) }

// IsTTY requires both streams to be a character device, because the child of an
// interactive spawn reads the prompt answer from one and writes the prompt to the other.
func (r *osRuntime) IsTTY() bool {
	return isCharDevice(os.Stdin) && isCharDevice(os.Stdout)
}

func isCharDevice(file *os.File) bool {
	info, err := file.Stat()
	if err != nil {
		return false
	}
	return info.Mode()&os.ModeCharDevice != 0
}

func (r *osRuntime) Now() time.Time { return time.Now() }

// UUID returns a version-4 UUID. The standard library has no UUID type, so the bytes
// come from crypto/rand and take the version and the variant bits by hand.
func (r *osRuntime) UUID() string {
	var bytes [16]byte
	// crypto/rand.Read fills the buffer or panics. It never reports an error.
	_, _ = rand.Read(bytes[:])
	bytes[6] = (bytes[6] & 0x0f) | 0x40
	bytes[8] = (bytes[8] & 0x3f) | 0x80
	text := hex.EncodeToString(bytes[:])
	return text[0:8] + "-" + text[8:12] + "-" + text[12:16] + "-" + text[16:20] + "-" + text[20:32]
}

func (r *osRuntime) ReadTextFile(path string) (string, error) {
	text, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	return string(text), nil
}

func (r *osRuntime) WriteTextFile(path string, text string) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, []byte(text), 0o644)
}

// RemoveFile removes a file and accepts a file that is already gone.
func (r *osRuntime) RemoveFile(path string) error {
	if err := os.Remove(path); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	return nil
}

func (r *osRuntime) Spawn(command []string, options SpawnOptions) SpawnResult {
	if len(command) == 0 {
		return SpawnResult{ExitCode: 127, Stderr: "the command is empty"}
	}
	argv := append([]string(nil), command...)
	isCLI := argv[0] == treehouseCommandName
	if isCLI {
		path, err := treehouseCLIPath()
		if err != nil {
			// Exit code 127 makes this an "unavailable" failure, the same code a shell
			// reports for a command it cannot find.
			return SpawnResult{ExitCode: 127, Stderr: err.Error()}
		}
		argv[0] = path
	}

	child := exec.Command(argv[0], argv[1:]...)
	if isCLI {
		// The CLI skips its update check when this variable is 1. The wrapper always
		// wants the check off, so it sets the variable even when the caller did not.
		child.Env = append(os.Environ(), "TREEHOUSE_NO_UPDATE_CHECK=1")
	}
	stdout := &strings.Builder{}
	stderr := &strings.Builder{}
	if options.Interactive {
		child.Stdin = os.Stdin
		child.Stdout = os.Stdout
		child.Stderr = os.Stderr
	} else {
		child.Stdout = stdout
		child.Stderr = stderr
	}

	err := child.Run()
	result := SpawnResult{Stdout: stdout.String(), Stderr: stderr.String()}
	if err == nil {
		return result
	}
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		result.ExitCode = exitErr.ExitCode()
		return result
	}
	// The child never started, for example because the binary is missing.
	return SpawnResult{ExitCode: 127, Stdout: result.Stdout, Stderr: err.Error()}
}

// treehouseCLIPath resolves the Treehouse CLI binary. It never searches PATH, so a
// Treehouse installation on the machine cannot replace the pinned binary.
//
// TREEHOUSE_CLI_BIN wins. It is the override of the launcher and of a test. The
// runfiles path that BUILD.bazel injects comes second.
func treehouseCLIPath() (string, error) {
	reasons := []string{}
	if override, ok := os.LookupEnv("TREEHOUSE_CLI_BIN"); ok && override != "" {
		if isExecutableFile(override) {
			return override, nil
		}
		reasons = append(reasons, "TREEHOUSE_CLI_BIN="+override+" is not an executable file")
	}
	for _, candidate := range runfilesCandidates(treehouseCLIRlocation) {
		if isExecutableFile(candidate) {
			return candidate, nil
		}
		reasons = append(reasons, candidate+" is not an executable file")
	}
	if len(reasons) == 0 {
		reasons = append(reasons, "TREEHOUSE_CLI_BIN is not set and the runfiles path is empty")
	}
	return "", errors.New("the Treehouse CLI binary could not be resolved: " + strings.Join(reasons, "; "))
}

// runfilesCandidates lists the paths one runfiles entry can have.
//
// Two forms exist, and this repository needs both. A runfiles *tree* is a directory of
// symlinks, and an entry is the rlocation path joined onto it. A runfiles *manifest* is a
// text file that maps each rlocation path to an absolute path, and an entry is a lookup.
//
// The manifest is not a fallback here, it is the normal case. `community/common.bazelrc`
// sets `build --nobuild_runfile_links`, so `bazel build` writes the manifest beside the
// binary and never builds the tree. The tree exists under `bazel test` and `bazel run`.
// The manifest must not be traded for `--build_runfile_links` in the launcher: that flag
// discards the Bazel analysis cache on every alternation with a plain build, and an agent
// acquires a workspace and then builds.
//
// The order is: RUNFILES_DIR, RUNFILES_MANIFEST_FILE, then the tree and the manifest that
// sit beside the running binary. The binary can be a symlink, so the target of the link
// gives a second pair.
func runfilesCandidates(rlocation string) []string {
	if rlocation == "" {
		return nil
	}
	if dir, ok := os.LookupEnv("RUNFILES_DIR"); ok && dir != "" {
		return []string{filepath.Join(dir, rlocation)}
	}
	if manifest, ok := os.LookupEnv("RUNFILES_MANIFEST_FILE"); ok && manifest != "" {
		if path := manifestLookup(manifest, rlocation); path != "" {
			return []string{path}
		}
	}
	executable, err := os.Executable()
	if err != nil {
		return nil
	}
	bases := []string{executable}
	if resolved, err := filepath.EvalSymlinks(executable); err == nil && resolved != executable {
		bases = append(bases, resolved)
	}
	var candidates []string
	for _, base := range bases {
		candidates = append(candidates, filepath.Join(base+".runfiles", rlocation))
		if path := manifestLookup(base+".runfiles_manifest", rlocation); path != "" {
			candidates = append(candidates, path)
		}
	}
	return candidates
}

// manifestLookup returns the absolute path one runfiles manifest gives an rlocation path,
// or "" when the file or the entry is absent.
//
// A line is "<rlocation> <absolute path>". Bazel escapes a path that holds a space or a
// newline, and it marks such a line with a leading space. No target of this tool has such
// a path, so an escaped line is skipped rather than decoded.
func manifestLookup(manifest string, rlocation string) string {
	content, err := os.ReadFile(manifest)
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(content), "\n") {
		line = strings.TrimSuffix(line, "\r")
		if line == "" || strings.HasPrefix(line, " ") {
			continue
		}
		key, value, found := strings.Cut(line, " ")
		if found && key == rlocation {
			return value
		}
	}
	return ""
}

// isExecutableFile reports whether a path is a file the process can start. Windows keeps
// no execute bit, so there the file must only exist.
func isExecutableFile(path string) bool {
	info, err := os.Stat(path)
	if err != nil || info.IsDir() {
		return false
	}
	if goruntime.GOOS == "windows" {
		return true
	}
	return info.Mode().Perm()&0o111 != 0
}

// isoTimestamp formats a time the way JavaScript's Date.toISOString does: UTC, three
// fraction digits, and a "Z" suffix.
func isoTimestamp(value time.Time) string {
	return value.UTC().Format("2006-01-02T15:04:05.000Z07:00")
}
