package main

import (
	"testing"
)

// --- read status ---

func TestReadStatusReturnsStructuredWorkspaceAndProcessData(t *testing.T) {
	rt := newFakeRuntime(t)
	entry := withFields(LEASED, map[string]any{
		"processes": []any{map[string]any{"pid": 123, "command": "idea"}},
	})
	rt.push(jsonResult([]any{entry}))

	data, err := execute([]string{"read", "status"}, rt)
	output := requireSuccess(t, data, err)

	payload, ok := output.(map[string]any)
	if !ok {
		t.Fatalf("expected a map payload, but got %#v", output)
	}
	assertJSON(t, payload["workspaces"], mustJSON([]any{entry}))
	assertCommand(t, rt, 0, "treehouse", "status", "--json")
}

func TestReadStatusReportsAnUnavailableExecutable(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.push(result("", 127, "ENOENT"))

	data, err := execute([]string{"read", "status"}, rt)
	failure := requireFailure(t, data, err)

	assertExitCode(t, failure, 127)
	assertMessage(t, failure, "Do not install")
	assertSpawnCount(t, rt, 1)
}

func TestReadStatusRejectsMalformedJSON(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.push(result("not-json", 0, ""))

	data, err := execute([]string{"read", "status"}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "malformed JSON")
}

// --- write acquire ---

func TestWriteAcquireWritesItsReceiptIntoTheWorkspace(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareAcquire(rt, LEASED, SOURCE_HEAD)

	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
	output := requireSuccess(t, data, err)

	assertCommand(t, rt, 4, "treehouse", "get", "--lease", "--json", "--lease-holder", "agent-session")
	assertSubset(t, output, map[string]any{
		"path":          WORKSPACE,
		"lease_id":      "lease-1",
		"lease_holder":  "agent-session",
		"source_head":   SOURCE_HEAD,
		"prepared_head": SOURCE_HEAD,
		"receipt_path":  RECEIPT_PATH,
	})
	assertJSON(t, fromJSON(t, rt.files[RECEIPT_PATH]), mustJSON(map[string]any{
		"schema_version": 2,
		"path":           WORKSPACE,
		"lease_id":       "lease-1",
		"lease_holder":   "agent-session",
		"acquired_at":    "2026-08-06T10:00:00.000Z",
		"source_head":    SOURCE_HEAD,
	}))
	if !contains(rt.call(0).command, "core.fsmonitor=false") {
		t.Fatalf("spawn 0 does not disable the file-system monitor: %v", rt.call(0).command)
	}
	for index, command := range rt.commands() {
		if contains(command, "status") && contains(command, ROOT) {
			t.Fatalf("spawn %d runs a status of the source root: %v", index, command)
		}
		if contains(command, "checkout") {
			t.Fatalf("spawn %d checks out although the HEAD already matches: %v", index, command)
		}
	}
	assertQueueDrained(t, rt)
}

func TestWriteAcquireDetachesACleanWorkspaceAtTheSourceHead(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareAcquire(rt, LEASED, NATIVE_HEAD)

	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
	requireSuccess(t, data, err)

	expected := []string{
		"git", "-c", "core.fsmonitor=false", "-C", WORKSPACE,
		"checkout", "--force", "--detach", SOURCE_HEAD,
	}
	if !hasCommand(rt, expected) {
		t.Fatalf("no spawn detaches at the source HEAD: %v", rt.commands())
	}
	assertQueueDrained(t, rt)
}

// TestWriteAcquireForcesTheDetachButNeverForcesTheCLI pins both halves of the force rule
// at once. A case-only rename that a case-insensitive filesystem hides from `git status`
// makes Git refuse a plain checkout, so the detach carries `--force`. The Treehouse CLI
// still never receives `--force`.
func TestWriteAcquireForcesTheDetachButNeverForcesTheCLI(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareAcquire(rt, LEASED, NATIVE_HEAD)

	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
	requireSuccess(t, data, err)

	detach := []string{}
	for _, command := range rt.commands() {
		if command[0] == "git" && contains(command, "--detach") {
			detach = command
		}
	}
	if len(detach) == 0 {
		t.Fatalf("no recorded command line detaches the workspace: %v", rt.commands())
	}
	if !contains(detach, "--force") {
		t.Fatalf("the detach does not carry --force: %v", detach)
	}
	if !contains(detach, "checkout") || detach[len(detach)-1] != SOURCE_HEAD {
		t.Fatalf("the detach does not check out the source HEAD: %v", detach)
	}
	assertNoForce(t, rt)
	assertQueueDrained(t, rt)
}

func TestWriteAcquireUsesTheHolderEnvironmentVariable(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.env["TREEHOUSE_LEASE_HOLDER"] = "environment-session"
	prepareAcquire(rt, withFields(LEASED, map[string]any{"lease_holder": "environment-session"}), SOURCE_HEAD)

	data, err := execute([]string{"write", "acquire"}, rt)
	requireSuccess(t, data, err)

	command := rt.call(4).command
	if last := command[len(command)-1]; last != "environment-session" {
		t.Fatalf("the requested holder is %q, but %q was expected", last, "environment-session")
	}
}

func TestWriteAcquireGeneratesAStableHolder(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareAcquire(rt, withFields(LEASED, map[string]any{"lease_holder": "agent-generated-uuid"}), SOURCE_HEAD)

	data, err := execute([]string{"write", "acquire"}, rt)
	requireSuccess(t, data, err)

	command := rt.call(4).command
	if last := command[len(command)-1]; last != "agent-generated-uuid" {
		t.Fatalf("the requested holder is %q, but %q was expected", last, "agent-generated-uuid")
	}
	assertSubset(t, fromJSON(t, rt.files[RECEIPT_PATH]), map[string]any{"lease_holder": "agent-generated-uuid"})
}

func TestWriteAcquireRefusesAnAlreadyLeasedWorkspace(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.push(
		result(ROOT+"\n", 0, ""),
		result(SOURCE_HEAD+"\n", 0, ""),
		result(COMMON_DIR+"\n", 0, ""),
		jsonResult([]any{withFields(LEASED, map[string]any{"path": ROOT})}),
	)

	data, err := execute([]string{"write", "acquire"}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "already has an active")
	assertExitCode(t, failure, 2)
	assertSpawnCount(t, rt, 4)
}

func TestWriteAcquireReturnsTheLeaseWhenReceiptWritingFails(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareFailedReceiptWrite(rt)
	// The rollback return, then the live check of rollbackAcquire.
	rt.push(result("", 0, ""), jsonResult([]any{AVAILABLE}))

	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "workspace was returned")
	assertCommand(t, rt, 5,
		"treehouse", "return", WORKSPACE,
		"--if-lease-id", "lease-1",
		"--if-lease-holder", "agent-session",
	)
	assertNoForce(t, rt)
}

func TestWriteAcquireReturnsTheLeaseWhenExactHeadPreparationFails(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareFailedPreparation(rt)
	// The rollback return, then the live check of rollbackAcquire.
	rt.push(result("", 0, ""), jsonResult([]any{AVAILABLE}))

	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "lease was returned")
	assertSubset(t, failure.details, map[string]any{"source_head": SOURCE_HEAD, "receipt_removed": true})
	if _, present := rt.files[RECEIPT_PATH]; present {
		t.Fatalf("the receipt survived a completed rollback")
	}
	if !hasCommand(rt, returnCommand(LeaseIdentity{
		Path: WORKSPACE, LeaseID: "lease-1", LeaseHolder: "agent-session",
	})) {
		t.Fatalf("no spawn returns the lease: %v", rt.commands())
	}
	assertQueueDrained(t, rt)
}

func TestWriteAcquireReturnsTheLeaseWhenItsLiveIdentityChanges(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareAcquireStart(rt, LEASED)
	rt.push(
		jsonResult([]any{withFields(LEASED, map[string]any{"lease_id": "different-lease"})}),
		// The rollback return, then the live check of rollbackAcquire.
		result("", 0, ""),
		jsonResult([]any{AVAILABLE}),
	)

	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "lease was returned")
	assertSubset(t, failure.details, map[string]any{"rollback_exit_code": 0, "rollback_returned": true})
	cause, ok := details(t, failure)["cause"].(map[string]any)
	if !ok {
		t.Fatalf("expected a cause map, but got %#v", details(t, failure)["cause"])
	}
	if cause["message"] != "The acquired workspace does not match the live Treehouse lease." {
		t.Fatalf("the cause message is %#v", cause["message"])
	}
}

func TestWriteAcquireRetainsTheReceiptWhenRollbackFails(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareAcquireStart(rt, LEASED)
	rt.push(
		jsonResult([]any{LEASED}),
		result(WORKSPACE+"\n", 0, ""),
		result("/different/.git\n", 0, ""),
		result("", 4, "lease changed"),
	)

	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "retain this lease identity")
	assertSubset(t, failure.details, map[string]any{
		"path":               WORKSPACE,
		"lease_id":           "lease-1",
		"rollback_exit_code": 4,
		"receipt_removed":    false,
	})
	assertSubset(t, fromJSON(t, rt.files[RECEIPT_PATH]), map[string]any{
		"schema_version": 2,
		"source_head":    SOURCE_HEAD,
	})
}

func TestWriteAcquireRecoversALeaseAfterMalformedOutput(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.push(
		result(ROOT+"\n", 0, ""),
		result(SOURCE_HEAD+"\n", 0, ""),
		result(COMMON_DIR+"\n", 0, ""),
		jsonResult([]any{AVAILABLE}),
		result("not-json", 0, ""),
		jsonResult([]any{LEASED}),
		// The rollback return, then the live check of rollbackAcquire.
		result("", 0, ""),
		jsonResult([]any{AVAILABLE}),
	)

	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "recovered lease was returned")
	assertJSON(t, rt.call(6).command[0:3], mustJSON([]string{"treehouse", "return", WORKSPACE}))
	assertQueueDrained(t, rt)
}

func TestWriteAcquireReportsTheRecoveredIdentityWhenRollbackFails(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.push(
		result(ROOT+"\n", 0, ""),
		result(SOURCE_HEAD+"\n", 0, ""),
		result(COMMON_DIR+"\n", 0, ""),
		jsonResult([]any{AVAILABLE}),
		result("not-json", 0, ""),
		jsonResult([]any{LEASED}),
		result("", 4, "lease changed"),
	)

	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "retain this lease identity")
	assertSubset(t, failure.details, map[string]any{
		"path":               WORKSPACE,
		"lease_id":           "lease-1",
		"lease_holder":       "agent-session",
		"rollback_exit_code": 4,
	})
}

// --- write return ---

func TestWriteReturnReturnsACleanWorkspaceWithBothLeaseGuards(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareReturn(rt, "")
	// The return call, then the live check of verifyReturned.
	rt.push(result("", 0, ""), jsonResult([]any{AVAILABLE}))

	data, err := execute([]string{"write", "return", "--workspace", WORKSPACE}, rt)
	output := requireSuccess(t, data, err)

	assertCommand(t, rt, 3,
		"treehouse", "return", WORKSPACE,
		"--if-lease-id", "lease-1",
		"--if-lease-holder", "agent-session",
	)
	if rt.call(3).options != (SpawnOptions{}) {
		t.Fatalf("the return of a clean workspace sends input: %#v", rt.call(3).options)
	}
	assertNoForce(t, rt)
	if !contains(rt.call(2).command, "core.fsmonitor=false") {
		t.Fatalf("spawn 2 does not disable the file-system monitor: %v", rt.call(2).command)
	}
	assertJSON(t, rt.removed, mustJSON([]string{RECEIPT_PATH}))
	assertSubset(t, output, map[string]any{"returned": true, "dirty": false, "receipt_removed": true})
	assertQueueDrained(t, rt)
}

func TestWriteReturnRefusesAReceiptThatDoesNotMatchTheLiveLease(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.files[RECEIPT_PATH] = receipt(nil)
	rt.push(
		result(WORKSPACE+"\n", 0, ""),
		jsonResult([]any{withFields(LEASED, map[string]any{"lease_id": "different-lease"})}),
	)

	data, err := execute([]string{"write", "return", "--workspace", WORKSPACE}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "does not match")
	assertExitCode(t, failure, 2)
	assertSpawnCount(t, rt, 2)
}

func TestWriteReturnRequiresPreservationConfirmationForADirtyWorkspace(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareReturn(rt, " M changed.kt\n?? new.kt\n")

	data, err := execute([]string{"write", "return", "--workspace", WORKSPACE}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "--confirm-preserved")
	assertExitCode(t, failure, 2)
	assertSubset(t, failure.details, map[string]any{"changes": []string{"M changed.kt", "?? new.kt"}})
	if _, present := rt.files[RECEIPT_PATH]; !present {
		t.Fatalf("the receipt was removed although the return was refused")
	}
}

// A dirty return needs no TTY. The wrapper answers the confirmation prompt of the CLI,
// because --confirm-preserved is already that answer.
func TestWriteReturnAnswersThePromptOfAConfirmedDirtyReturn(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareReturn(rt, " M changed.kt\n")
	// The return call, then the live check of verifyReturned.
	rt.push(result("", 0, ""), jsonResult([]any{AVAILABLE}))

	data, err := execute([]string{
		"write", "return", "--workspace", WORKSPACE, "--confirm-preserved",
	}, rt)
	output := requireSuccess(t, data, err)

	if rt.call(3).options != (SpawnOptions{Stdin: "y\n"}) {
		t.Fatalf("the return of a dirty workspace does not answer the prompt: %#v", rt.call(3).options)
	}
	assertNoForce(t, rt)
	assertSubset(t, output, map[string]any{"returned": true, "dirty": true})
	assertQueueDrained(t, rt)
}

func TestWriteReturnRetainsTheReceiptWhenTreehouseFails(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareReturn(rt, "")
	rt.push(result("", 4, "lease changed"))

	data, err := execute([]string{"write", "return", "--workspace", WORKSPACE}, rt)
	failure := requireFailure(t, data, err)

	assertSubset(t, failure.details, map[string]any{
		"path":         WORKSPACE,
		"lease_id":     "lease-1",
		"lease_holder": "agent-session",
	})
	if _, present := rt.files[RECEIPT_PATH]; !present {
		t.Fatalf("the receipt was removed although the return failed")
	}
	assertJSON(t, rt.removed, "[]")
}

func TestWriteReturnRefusesAReceiptCopiedFromAnotherWorkspace(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.files[RECEIPT_PATH] = receipt(map[string]any{"path": ROOT})

	data, err := execute([]string{"write", "return", "--workspace", WORKSPACE}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "different workspace")
	assertExitCode(t, failure, 2)
	assertSpawnCount(t, rt, 0)
}

func TestWriteReturnRefusesToRunFromInsideTheLeasedWorkspace(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.cwd = WORKSPACE + "/plugins"

	data, err := execute([]string{"write", "return", "--workspace", WORKSPACE}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "outside the leased workspace")
	assertExitCode(t, failure, 2)
	assertSpawnCount(t, rt, 0)
}

func TestWriteReturnRefusesAWorkspaceWithLiveProcesses(t *testing.T) {
	rt := newFakeRuntime(t)
	rt.files[RECEIPT_PATH] = receiptV2(nil)
	rt.push(
		result(WORKSPACE+"\n", 0, ""),
		jsonResult([]any{withFields(LEASED, map[string]any{
			"processes": []any{map[string]any{"pid": 42, "name": "bazel"}},
		})}),
	)

	data, err := execute([]string{"write", "return", "--workspace", WORKSPACE}, rt)
	failure := requireFailure(t, data, err)

	assertMessage(t, failure, "live processes")
	assertExitCode(t, failure, 2)
	assertSubset(t, failure.details, map[string]any{
		"processes": []any{map[string]any{"pid": 42, "name": "bazel"}},
	})
	assertSpawnCount(t, rt, 2)
}

// --- the command surface ---

func TestCommandSurfaceRejectsDestructiveOperationsAndForce(t *testing.T) {
	rt := newFakeRuntime(t)
	cases := []struct {
		name string
		argv []string
	}{
		{"an unknown write action", []string{"write", "destroy"}},
		{"a force option", []string{"write", "return", "--workspace", WORKSPACE, "--force"}},
		{"a missing workspace", []string{"write", "return"}},
	}
	for _, item := range cases {
		t.Run(item.name, func(t *testing.T) {
			data, err := execute(item.argv, rt)
			assertExitCode(t, requireFailure(t, data, err), 2)
		})
	}
	assertSpawnCount(t, rt, 0)
}

// --- the aborted-return check, which the wrapper added on top of the TypeScript ---

func TestWriteReturnFailsWhenTheLeaseSurvivesASuccessfulExit(t *testing.T) {
	rt := newFakeRuntime(t)
	prepareReturn(rt, "")
	// The CLI aborts its own dirty-return prompt and still exits 0.
	rt.push(result("", 0, "Aborted."), jsonResult([]any{LEASED}))

	data, err := execute([]string{"write", "return", "--workspace", WORKSPACE}, rt)
	failure := requireFailure(t, data, err)

	assertExitCode(t, failure, 1)
	assertMessage(t, failure, "still leased")
	assertMessage(t, failure, "Retain this lease identity")
	assertSubset(t, failure.details, map[string]any{
		"path":             WORKSPACE,
		"lease_id":         "lease-1",
		"lease_holder":     "agent-session",
		"receipt_removed":  false,
		"native_exit_code": 0,
		"native_stderr":    "Aborted.",
		"live_status":      "leased",
	})
	if _, present := rt.files[RECEIPT_PATH]; !present {
		t.Fatalf("the receipt was removed although the lease survived")
	}
	assertJSON(t, rt.removed, "[]")
	assertQueueDrained(t, rt)
}

func TestWriteReturnSucceedsWhenTheLeaseIsGone(t *testing.T) {
	cases := []struct {
		name string
		live []any
	}{
		{"the entry became available", []any{AVAILABLE}},
		{"the entry is absent", []any{}},
		{"another holder took the slot", []any{withFields(LEASED, map[string]any{
			"lease_id":     "lease-2",
			"lease_holder": "other-session",
		})}},
	}
	for _, item := range cases {
		t.Run(item.name, func(t *testing.T) {
			rt := newFakeRuntime(t)
			prepareReturn(rt, "")
			rt.push(result("", 0, ""), jsonResult(item.live))

			data, err := execute([]string{"write", "return", "--workspace", WORKSPACE}, rt)
			output := requireSuccess(t, data, err)

			assertSubset(t, output, map[string]any{"returned": true, "receipt_removed": true})
			assertJSON(t, rt.removed, mustJSON([]string{RECEIPT_PATH}))
			if _, present := rt.files[RECEIPT_PATH]; present {
				t.Fatalf("the receipt survived a completed return")
			}
			assertQueueDrained(t, rt)
		})
	}
}

// --- the aborted-rollback check of an acquire ---

// rollbackTriggers are two acquire failures that return the new lease. Each rollback
// site must trust the live pool, so every case below runs on both.
var rollbackTriggers = []struct {
	name    string
	prepare func(rt *FakeRuntime)
	// returnedFragment belongs to the message of a confirmed rollback.
	returnedFragment string
	// writesReceipt tells whether the trigger wrote a receipt before the rollback.
	writesReceipt bool
}{
	{
		name:             "a failed receipt write",
		prepare:          prepareFailedReceiptWrite,
		returnedFragment: "the acquired workspace was returned",
		writesReceipt:    false,
	},
	{
		name:             "a failed HEAD preparation",
		prepare:          prepareFailedPreparation,
		returnedFragment: "so the lease was returned",
		writesReceipt:    true,
	},
}

func TestWriteAcquireRollbackRemovesTheReceiptWhenTheLeaseIsGone(t *testing.T) {
	for _, trigger := range rollbackTriggers {
		t.Run(trigger.name, func(t *testing.T) {
			rt := newFakeRuntime(t)
			trigger.prepare(rt)
			rt.push(result("", 0, ""), jsonResult([]any{AVAILABLE}))

			data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
			failure := requireFailure(t, data, err)

			assertExitCode(t, failure, 1)
			assertMessage(t, failure, trigger.returnedFragment)
			assertSubset(t, failure.details, map[string]any{
				"rollback_exit_code": 0,
				"rollback_returned":  true,
			})
			if trigger.writesReceipt {
				assertSubset(t, failure.details, map[string]any{"receipt_removed": true})
				assertJSON(t, rt.removed, mustJSON([]string{RECEIPT_PATH}))
			}
			if _, present := rt.files[RECEIPT_PATH]; present {
				t.Fatalf("the receipt survived a confirmed rollback")
			}
			assertQueueDrained(t, rt)
		})
	}
}

// The rollback of an acquire can meet the same confirmation prompt, so it sends the same
// answer. A rollback that aborts at the prompt leaves a lease that nothing can return.
func TestWriteAcquireRollbackAnswersTheReturnPrompt(t *testing.T) {
	for _, trigger := range rollbackTriggers {
		t.Run(trigger.name, func(t *testing.T) {
			rt := newFakeRuntime(t)
			trigger.prepare(rt)
			// The rollback return, then the live check that confirms it.
			rt.push(result("", 0, ""), jsonResult([]any{AVAILABLE}))

			data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
			requireFailure(t, data, err)

			found := false
			for index, call := range rt.spawned {
				if len(call.command) < 2 || call.command[0] != treehouseCommandName || call.command[1] != "return" {
					continue
				}
				found = true
				if call.options != (SpawnOptions{Stdin: "y\n"}) {
					t.Fatalf("spawn %d does not answer the return prompt: %#v", index, call.options)
				}
			}
			if !found {
				t.Fatalf("no rollback return was recorded: %v", rt.commands())
			}
			assertNoForce(t, rt)
			assertQueueDrained(t, rt)
		})
	}
}

func TestWriteAcquireRollbackKeepsTheReceiptWhenTheLeaseSurvivesASuccessfulExit(t *testing.T) {
	for _, trigger := range rollbackTriggers {
		t.Run(trigger.name, func(t *testing.T) {
			rt := newFakeRuntime(t)
			trigger.prepare(rt)
			// The CLI aborts its own dirty-return prompt and still exits 0.
			rt.push(result("", 0, "Aborted."), jsonResult([]any{LEASED}))

			data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
			failure := requireFailure(t, data, err)

			assertExitCode(t, failure, 1)
			assertMessage(t, failure, "could not be returned; retain this lease identity")
			assertSubset(t, failure.details, map[string]any{
				"path":                 WORKSPACE,
				"lease_id":             "lease-1",
				"lease_holder":         "agent-session",
				"rollback_exit_code":   0,
				"rollback_returned":    false,
				"rollback_stderr":      "Aborted.",
				"rollback_live_status": "leased",
			})
			if trigger.writesReceipt {
				assertSubset(t, failure.details, map[string]any{"receipt_removed": false})
				if _, present := rt.files[RECEIPT_PATH]; !present {
					t.Fatalf("the receipt was removed although the lease survived")
				}
			}
			assertJSON(t, rt.removed, "[]")
			assertQueueDrained(t, rt)
		})
	}
}

func TestWriteAcquireRollbackKeepsTheReceiptWhenTheLiveStateCannotBeRead(t *testing.T) {
	for _, trigger := range rollbackTriggers {
		t.Run(trigger.name, func(t *testing.T) {
			rt := newFakeRuntime(t)
			trigger.prepare(rt)
			rt.push(result("", 0, ""), result("", 4, "pool unreachable"))

			data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
			failure := requireFailure(t, data, err)

			assertExitCode(t, failure, 1)
			assertMessage(t, failure, "could not be confirmed; retain this lease identity")
			assertSubset(t, failure.details, map[string]any{
				"path":               WORKSPACE,
				"lease_id":           "lease-1",
				"lease_holder":       "agent-session",
				"rollback_exit_code": 0,
				"rollback_returned":  false,
			})
			statusError, ok := details(t, failure)["rollback_status_error"].(map[string]any)
			if !ok {
				t.Fatalf("expected a rollback_status_error map, but got %#v", details(t, failure)["rollback_status_error"])
			}
			assertJSON(t, statusError["message"], mustJSON("Treehouse status failed: pool unreachable"))
			if trigger.writesReceipt {
				assertSubset(t, failure.details, map[string]any{"receipt_removed": false})
				if _, present := rt.files[RECEIPT_PATH]; !present {
					t.Fatalf("the receipt was removed although the live state is unknown")
				}
			}
			assertJSON(t, rt.removed, "[]")
			assertQueueDrained(t, rt)
		})
	}
}

func TestWriteAcquireRollbackKeepsTheReceiptWhenTheReturnFails(t *testing.T) {
	for _, trigger := range rollbackTriggers {
		t.Run(trigger.name, func(t *testing.T) {
			rt := newFakeRuntime(t)
			trigger.prepare(rt)
			rt.push(result("", 4, "lease changed"))

			data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, rt)
			failure := requireFailure(t, data, err)

			assertExitCode(t, failure, 1)
			assertMessage(t, failure, "could not be returned; retain this lease identity")
			assertSubset(t, failure.details, map[string]any{
				"path":               WORKSPACE,
				"lease_id":           "lease-1",
				"lease_holder":       "agent-session",
				"rollback_exit_code": 4,
				"rollback_returned":  false,
				"rollback_stderr":    "lease changed",
			})
			if trigger.writesReceipt {
				assertSubset(t, failure.details, map[string]any{"receipt_removed": false})
				if _, present := rt.files[RECEIPT_PATH]; !present {
					t.Fatalf("the receipt was removed although the return failed")
				}
			}
			assertJSON(t, rt.removed, "[]")
			assertNoForce(t, rt)
			// A failed return needs no live check, so the queue must be empty.
			assertQueueDrained(t, rt)
		})
	}
}

func TestTheWrapperNeverPassesForce(t *testing.T) {
	acquire := newFakeRuntime(t)
	prepareAcquire(acquire, LEASED, NATIVE_HEAD)
	data, err := execute([]string{"write", "acquire", "--holder", "agent-session"}, acquire)
	requireSuccess(t, data, err)
	assertNoForce(t, acquire)

	returned := newFakeRuntime(t)
	prepareReturn(returned, " M changed.kt\n")
	returned.push(result("", 0, ""), jsonResult([]any{AVAILABLE}))
	data, err = execute([]string{
		"write", "return", "--workspace", WORKSPACE, "--confirm-preserved",
	}, returned)
	requireSuccess(t, data, err)
	assertNoForce(t, returned)
}

// fakeWriteError is the error of a failed receipt write.
type fakeWriteError struct{}

func (e *fakeWriteError) Error() string { return "read-only filesystem" }
