package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"
)

// The fixtures of the whole suite. ROOT is the source workspace, and WORKSPACE is the
// leased one that Treehouse hands out.
const (
	ROOT        = "/repo"
	WORKSPACE   = "/treehouse/1/repo"
	COMMON_DIR  = "/repo/.git"
	SOURCE_HEAD = "1111111111111111111111111111111111111111"
	NATIVE_HEAD = "2222222222222222222222222222222222222222"
)

// RECEIPT_PATH is where an acquired workspace holds its receipt.
var RECEIPT_PATH = filepath.Join(WORKSPACE, "out", "treehouse", "lease.json")

// AVAILABLE is one free pool entry, in the shape of `treehouse status --json`.
var AVAILABLE = map[string]any{
	"name":         "1",
	"path":         WORKSPACE,
	"status":       "available",
	"lease_id":     "",
	"lease_holder": "",
	"leased_at":    nil,
	"processes":    []any{},
}

// LEASED is the same entry with the lease of the suite on it.
var LEASED = withFields(AVAILABLE, map[string]any{
	"status":       "leased",
	"lease_id":     "lease-1",
	"lease_holder": "agent-session",
	"leased_at":    "2026-08-06T10:00:00Z",
})

// withFields copies a fixture and applies the overrides, so a test never mutates a
// shared map.
func withFields(base map[string]any, overrides map[string]any) map[string]any {
	merged := make(map[string]any, len(base)+len(overrides))
	for key, value := range base {
		merged[key] = value
	}
	for key, value := range overrides {
		merged[key] = value
	}
	return merged
}

// result builds one queued child-process outcome.
func result(stdout string, exitCode int, stderr string) SpawnResult {
	return SpawnResult{ExitCode: exitCode, Stdout: stdout, Stderr: stderr}
}

// jsonResult builds a successful outcome whose stdout is the JSON of the value.
func jsonResult(value any) SpawnResult {
	return result(mustJSON(value), 0, "")
}

func mustJSON(value any) string {
	text, err := json.Marshal(value)
	if err != nil {
		panic(err)
	}
	return string(text)
}

// receipt renders a schema-version-1 receipt document with the overrides applied.
func receipt(overrides map[string]any) string {
	return mustJSON(withFields(map[string]any{
		"schema_version": 1,
		"path":           WORKSPACE,
		"lease_id":       "lease-1",
		"lease_holder":   "agent-session",
		"acquired_at":    "2026-08-06T10:00:00.000Z",
	}, overrides))
}

// receiptV2 renders a schema-version-2 receipt document, which also carries the
// captured source HEAD.
func receiptV2(overrides map[string]any) string {
	return receipt(withFields(map[string]any{
		"schema_version": 2,
		"source_head":    SOURCE_HEAD,
	}, overrides))
}

// spawnCall is one recorded child-process launch.
type spawnCall struct {
	command []string
	options SpawnOptions
}

// FakeRuntime is the Runtime of a test. It answers every spawn from a queue and it
// records each command line in order.
type FakeRuntime struct {
	t          *testing.T
	cwd        string
	env        map[string]string
	isTTY      bool
	spawned    []spawnCall
	responses  []SpawnResult
	files      map[string]string
	removed    []string
	writeError error
	uuidValue  string
}

func newFakeRuntime(t *testing.T) *FakeRuntime {
	t.Helper()
	return &FakeRuntime{
		t:         t,
		cwd:       ROOT,
		env:       map[string]string{},
		files:     map[string]string{},
		removed:   []string{},
		uuidValue: "generated-uuid",
	}
}

// push queues one outcome for each following spawn, in order.
func (r *FakeRuntime) push(results ...SpawnResult) {
	r.responses = append(r.responses, results...)
}

func (r *FakeRuntime) Cwd() string { return r.cwd }

func (r *FakeRuntime) Env(name string) (string, bool) {
	value, ok := r.env[name]
	return value, ok
}

func (r *FakeRuntime) IsTTY() bool { return r.isTTY }

func (r *FakeRuntime) Now() time.Time {
	return time.Date(2026, time.August, 6, 10, 0, 0, 0, time.UTC)
}

func (r *FakeRuntime) UUID() string { return r.uuidValue }

func (r *FakeRuntime) ReadTextFile(path string) (string, error) {
	value, ok := r.files[path]
	if !ok {
		return "", fmt.Errorf("missing file: %s", path)
	}
	return value, nil
}

func (r *FakeRuntime) WriteTextFile(path string, text string) error {
	if r.writeError != nil {
		return r.writeError
	}
	r.files[path] = text
	return nil
}

func (r *FakeRuntime) RemoveFile(path string) error {
	r.removed = append(r.removed, path)
	delete(r.files, path)
	return nil
}

func (r *FakeRuntime) Spawn(command []string, options SpawnOptions) SpawnResult {
	r.spawned = append(r.spawned, spawnCall{command: append([]string(nil), command...), options: options})
	if len(r.responses) == 0 {
		r.t.Fatalf("unexpected spawn %d: %v", len(r.spawned)-1, command)
		return SpawnResult{}
	}
	next := r.responses[0]
	r.responses = r.responses[1:]
	return next
}

// commands lists every recorded command line, in order.
func (r *FakeRuntime) commands() [][]string {
	out := make([][]string, 0, len(r.spawned))
	for _, call := range r.spawned {
		out = append(out, call.command)
	}
	return out
}

// call returns one recorded launch and fails the test when the index is out of range.
func (r *FakeRuntime) call(index int) spawnCall {
	r.t.Helper()
	if index >= len(r.spawned) {
		r.t.Fatalf("spawn %d is missing; the runtime recorded %d calls: %v", index, len(r.spawned), r.commands())
	}
	return r.spawned[index]
}

// prepareAcquireStart queues the source reads and the lease allocation of an acquire.
func prepareAcquireStart(rt *FakeRuntime, allocation map[string]any) {
	rt.push(
		result(ROOT+"\n", 0, ""),
		result(SOURCE_HEAD+"\n", 0, ""),
		result(COMMON_DIR+"\n", 0, ""),
		jsonResult([]any{AVAILABLE}),
		jsonResult(allocation),
	)
}

// prepareWorkspace queues the verification and the HEAD preparation of an acquire.
func prepareWorkspace(rt *FakeRuntime, allocation map[string]any, destinationHead string) {
	rt.push(
		jsonResult([]any{allocation}),
		result(WORKSPACE+"\n", 0, ""),
		result(COMMON_DIR+"\n", 0, ""),
		result("", 0, ""),
		result(destinationHead+"\n", 0, ""),
	)
	if destinationHead != SOURCE_HEAD {
		rt.push(result("", 0, ""))
	}
	rt.push(result(SOURCE_HEAD+"\n", 0, ""), result("", 0, ""))
}

// prepareAcquire queues a whole successful acquire.
func prepareAcquire(rt *FakeRuntime, allocation map[string]any, destinationHead string) {
	prepareAcquireStart(rt, allocation)
	prepareWorkspace(rt, allocation, destinationHead)
}

// prepareFailedReceiptWrite queues an acquire whose receipt write fails. The caller
// queues the rollback return and the live check that follows it.
func prepareFailedReceiptWrite(rt *FakeRuntime) {
	rt.writeError = &fakeWriteError{}
	prepareAcquireStart(rt, LEASED)
}

// prepareFailedPreparation queues an acquire whose HEAD preparation fails at the
// checkout. The caller queues the rollback return and the live check that follows it.
func prepareFailedPreparation(rt *FakeRuntime) {
	prepareAcquireStart(rt, LEASED)
	rt.push(
		jsonResult([]any{LEASED}),
		result(WORKSPACE+"\n", 0, ""),
		result(COMMON_DIR+"\n", 0, ""),
		result("", 0, ""),
		result(NATIVE_HEAD+"\n", 0, ""),
		result("", 4, "checkout failed"),
	)
}

// prepareReturn writes the receipt and queues the git root, the live status and the git
// status of a return. The caller queues the return call itself.
func prepareReturn(rt *FakeRuntime, gitStatus string) {
	rt.files[RECEIPT_PATH] = receipt(nil)
	rt.push(result(WORKSPACE+"\n", 0, ""), jsonResult([]any{LEASED}), result(gitStatus, 0, ""))
}

// requireFailure asserts that the command failed with a cliError and returns it.
func requireFailure(t *testing.T, data any, err error) *cliError {
	t.Helper()
	if err == nil {
		t.Fatalf("expected the command to fail, but it returned %#v", data)
	}
	var cli *cliError
	if !errors.As(err, &cli) {
		t.Fatalf("expected a cliError, but got %T: %v", err, err)
	}
	return cli
}

// requireSuccess asserts that the command succeeded and returns its payload.
func requireSuccess(t *testing.T, data any, err error) any {
	t.Helper()
	if err != nil {
		t.Fatalf("expected the command to succeed, but it failed: %v", err)
	}
	return data
}

// details returns the failure details as a map.
func details(t *testing.T, err *cliError) map[string]any {
	t.Helper()
	value, ok := err.details.(map[string]any)
	if !ok {
		t.Fatalf("expected map details, but got %T: %#v", err.details, err.details)
	}
	return value
}

// asJSON renders any value as a generic JSON document, so a comparison ignores the Go
// type of a number and the order of a key.
func asJSON(t *testing.T, value any) any {
	t.Helper()
	text, err := json.Marshal(value)
	if err != nil {
		t.Fatalf("cannot marshal %#v: %v", value, err)
	}
	return fromJSON(t, string(text))
}

func fromJSON(t *testing.T, text string) any {
	t.Helper()
	var value any
	if err := json.Unmarshal([]byte(text), &value); err != nil {
		t.Fatalf("cannot parse %q: %v", text, err)
	}
	return value
}

// assertJSON compares a value against the expected JSON document.
func assertJSON(t *testing.T, actual any, expected string) {
	t.Helper()
	got := asJSON(t, actual)
	want := fromJSON(t, expected)
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("JSON mismatch:\n got: %s\nwant: %s", mustJSON(got), mustJSON(want))
	}
}

// assertSubset compares only the named keys of a JSON object, the way toMatchObject did.
func assertSubset(t *testing.T, actual any, expected map[string]any) {
	t.Helper()
	record, ok := asJSON(t, actual).(map[string]any)
	if !ok {
		t.Fatalf("expected a JSON object, but got %#v", actual)
	}
	for key, want := range expected {
		got, present := record[key]
		if !present {
			t.Fatalf("key %q is missing from %s", key, mustJSON(record))
		}
		wanted := asJSON(t, want)
		if !reflect.DeepEqual(got, wanted) {
			t.Fatalf("key %q is %s, but %s was expected", key, mustJSON(got), mustJSON(wanted))
		}
	}
}

// assertCommand compares one recorded command line.
func assertCommand(t *testing.T, rt *FakeRuntime, index int, expected ...string) {
	t.Helper()
	got := rt.call(index).command
	if !reflect.DeepEqual(got, expected) {
		t.Fatalf("spawn %d is %v, but %v was expected", index, got, expected)
	}
}

// assertSpawnCount compares the number of recorded launches.
func assertSpawnCount(t *testing.T, rt *FakeRuntime, expected int) {
	t.Helper()
	if len(rt.spawned) != expected {
		t.Fatalf("the runtime recorded %d launches, but %d was expected: %v", len(rt.spawned), expected, rt.commands())
	}
}

// assertQueueDrained asserts that the command consumed every queued outcome. A leftover
// outcome means that a spawn the test expects never happened.
func assertQueueDrained(t *testing.T, rt *FakeRuntime) {
	t.Helper()
	if len(rt.responses) != 0 {
		t.Fatalf("%d queued outcomes stayed unused after %d launches: %v", len(rt.responses), len(rt.spawned), rt.commands())
	}
}

// assertNoForce asserts that no recorded Treehouse CLI command line carries `--force`.
// A Git command line is a separate case, because gitDetach forces its own checkout.
func assertNoForce(t *testing.T, rt *FakeRuntime) {
	t.Helper()
	for index, command := range rt.commands() {
		if len(command) != 0 && command[0] != treehouseCommandName {
			continue
		}
		if contains(command, "--force") {
			t.Fatalf("spawn %d passes --force to the Treehouse CLI: %v", index, command)
		}
	}
}

func contains(values []string, wanted string) bool {
	for _, value := range values {
		if value == wanted {
			return true
		}
	}
	return false
}

// hasCommand reports whether one recorded command line equals the expected one.
func hasCommand(rt *FakeRuntime, expected []string) bool {
	for _, command := range rt.commands() {
		if reflect.DeepEqual(command, expected) {
			return true
		}
	}
	return false
}

// assertMessage asserts that the failure message holds the fragment.
func assertMessage(t *testing.T, err *cliError, fragment string) {
	t.Helper()
	if !strings.Contains(err.message, fragment) {
		t.Fatalf("the message %q does not hold %q", err.message, fragment)
	}
}

// assertExitCode asserts the exit code of a failure.
func assertExitCode(t *testing.T, err *cliError, expected int) {
	t.Helper()
	if err.exitCode != expected {
		t.Fatalf("the exit code is %d, but %d was expected; the message is %q", err.exitCode, expected, err.message)
	}
}
