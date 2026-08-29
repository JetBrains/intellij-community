// Command treehouse wraps the Treehouse workspace lease lifecycle.
//
// It exposes only the safe leased lifecycle: `read status`, `write acquire` and
// `write return`. It never installs Treehouse, never creates an ad hoc Git worktree,
// and never passes `--force` to the Treehouse CLI.
//
// The grammar takes two tokens, an access word and an action word, so a Bash approval
// stays prefix-scoped. An approval of `read` cannot authorize a write.
//
// A success prints `{"ok":true,"data":...}` on stdout. A failure prints
// `{"ok":false,"error":...,"details":...}` on stderr and sets the exit code. Exit code 2
// marks a usage or precondition failure. Exit code 127 marks an unavailable CLI.
package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
)

// receiptRelativePath is where an acquired workspace holds its lease receipt.
// Both repository layouts ignore `out/`.
const receiptRelativePath = "out/treehouse/lease.json"

// cliError carries the message, the exit code and the JSON details of one failure.
type cliError struct {
	message  string
	exitCode int
	details  any
}

func (e *cliError) Error() string { return e.message }

// okEnvelope is the stdout document of a successful command.
type okEnvelope struct {
	OK   bool `json:"ok"`
	Data any  `json:"data"`
}

// errorEnvelope is the stderr document of a failed command.
type errorEnvelope struct {
	OK      bool   `json:"ok"`
	Error   string `json:"error"`
	Details any    `json:"details,omitempty"`
}

func usage() string {
	return `Usage:
  treehouse read status
  treehouse write acquire [--holder <session-id>]
  treehouse write return --workspace <leased-path> [--confirm-preserved]

The CLI uses only Treehouse's leased get/status/return lifecycle. It never installs Treehouse,
creates an ad hoc Git worktree, or invokes --force.`
}

// failUsage reports a usage or precondition failure with the full usage text.
func failUsage(message string) error {
	return &cliError{message: message + "\n\n" + usage(), exitCode: 2}
}

// nativeFailure reports the failure of one Treehouse CLI call. Exit code 127 means the
// CLI itself is unavailable, so the message tells the caller not to work around it.
func nativeFailure(operation string, result SpawnResult, details map[string]any) error {
	unavailable := result.ExitCode == 127
	stderr := strings.TrimSpace(result.Stderr)
	message := "Treehouse " + operation + " failed."
	switch {
	case unavailable:
		message = "Treehouse is unavailable. Do not install it or fall back to another workspace mechanism on your own initiative; if the user explicitly asked for a Git worktree for this task, use one instead."
	case stderr != "":
		message = "Treehouse " + operation + " failed: " + stderr
	}
	merged := map[string]any{}
	for key, value := range details {
		merged[key] = value
	}
	merged["native_exit_code"] = result.ExitCode
	merged["stdout"] = strings.TrimSpace(result.Stdout)
	merged["stderr"] = stderr
	code := result.ExitCode
	switch {
	case unavailable:
		code = 127
	case code == 0:
		code = 1
	}
	return &cliError{message: message, exitCode: code, details: merged}
}

// parseJSON decodes one CLI or receipt document. UseNumber keeps every number in its
// original text, so an unknown field survives a round trip unchanged.
func parseJSON(text string, description string) (any, error) {
	decoder := json.NewDecoder(strings.NewReader(text))
	decoder.UseNumber()
	var value any
	if err := decoder.Decode(&value); err != nil {
		return nil, &cliError{
			message:  description + " returned malformed JSON.",
			exitCode: 1,
			details: map[string]any{
				"output":      strings.TrimSpace(text),
				"parse_error": err.Error(),
			},
		}
	}
	return value, nil
}

func nonEmptyString(value any, field string, description string) (string, error) {
	text, ok := value.(string)
	if !ok || text == "" {
		return "", &cliError{
			message:  description + " is missing a non-empty " + field + ".",
			exitCode: 1,
			details:  map[string]any{"field": field, "value": value},
		}
	}
	return text, nil
}

// causeDetails renders one nested failure for the `cause` detail of an outer failure.
func causeDetails(err error) map[string]any {
	var cli *cliError
	if errors.As(err, &cli) {
		cause := map[string]any{"message": cli.message}
		if cli.details != nil {
			cause["details"] = cli.details
		}
		return cause
	}
	return map[string]any{"message": err.Error()}
}

// singleValue takes the one value of an option and returns every other token. It
// rejects a repeated option and an option with no value.
func singleValue(tokens []string, name string) (string, bool, []string, error) {
	rest := make([]string, 0, len(tokens))
	value := ""
	present := false
	for index := 0; index < len(tokens); index++ {
		if tokens[index] != name {
			rest = append(rest, tokens[index])
			continue
		}
		if present {
			return "", false, nil, failUsage(name + " may be passed only once")
		}
		index++
		if index >= len(tokens) || strings.HasPrefix(tokens[index], "--") {
			return "", false, nil, failUsage(name + " requires a value")
		}
		value = tokens[index]
		present = true
	}
	return value, present, rest, nil
}

func execute(argv []string, rt Runtime) (any, error) {
	for _, argument := range argv {
		if argument == "--help" || argument == "-h" {
			return map[string]any{"usage": usage()}, nil
		}
	}
	access, action := "", ""
	var tokens []string
	if len(argv) > 0 {
		access = argv[0]
	}
	if len(argv) > 1 {
		action = argv[1]
	}
	if len(argv) > 2 {
		tokens = argv[2:]
	}

	if access == "read" && action == "status" {
		if len(tokens) > 0 {
			return nil, failUsage("Unknown option: " + tokens[0])
		}
		workspaces, err := readStatus(rt)
		if err != nil {
			return nil, err
		}
		return map[string]any{"workspaces": workspaces}, nil
	}
	if access == "write" && action == "acquire" {
		holder, present, rest, err := singleValue(tokens, "--holder")
		if err != nil {
			return nil, err
		}
		if len(rest) > 0 {
			return nil, failUsage("Unknown option: " + rest[0])
		}
		return executeAcquire(rt, holder, present)
	}
	if access == "write" && action == "return" {
		workspace, present, rest, err := singleValue(tokens, "--workspace")
		if err != nil {
			return nil, err
		}
		if !present {
			return nil, failUsage("--workspace is required")
		}
		confirmCount := 0
		for _, token := range rest {
			if token != "--confirm-preserved" {
				return nil, failUsage("Unknown option: " + token)
			}
			confirmCount++
		}
		if confirmCount > 1 {
			return nil, failUsage("--confirm-preserved may be passed only once")
		}
		return executeReturn(rt, workspace, confirmCount == 1)
	}
	return nil, failUsage("Expected 'read status', 'write acquire', or 'write return'")
}

func errorResult(err error) (int, errorEnvelope) {
	var cli *cliError
	if errors.As(err, &cli) {
		return cli.exitCode, errorEnvelope{Error: cli.message, Details: cli.details}
	}
	return 1, errorEnvelope{Error: err.Error()}
}

// renderJSON pretty-prints one document. The encoder keeps `<`, `>` and `&` as
// themselves, and it renders the whole document before anything reaches the stream.
func renderJSON(value any) ([]byte, error) {
	buffer := &bytes.Buffer{}
	encoder := json.NewEncoder(buffer)
	encoder.SetEscapeHTML(false)
	encoder.SetIndent("", "  ")
	if err := encoder.Encode(value); err != nil {
		return nil, err
	}
	return buffer.Bytes(), nil
}

func emitJSON(out io.Writer, value any) error {
	text, err := renderJSON(value)
	if err != nil {
		return err
	}
	_, err = out.Write(text)
	return err
}

// processExitCode clamps a code to the range a process can report. A signalled child
// reports -1, which os.Exit cannot pass on. The `native_exit_code` detail keeps the
// unclamped value.
func processExitCode(code int) int {
	if code <= 0 || code > 255 {
		return 1
	}
	return code
}

func main() {
	rt := newOSRuntime()
	data, err := execute(os.Args[1:], rt)
	if err == nil {
		err = emitJSON(os.Stdout, okEnvelope{OK: true, Data: data})
	}
	if err != nil {
		code, output := errorResult(err)
		if emitErr := emitJSON(os.Stderr, output); emitErr != nil {
			fmt.Fprintln(os.Stderr, emitErr)
		}
		os.Exit(processExitCode(code))
	}
}
