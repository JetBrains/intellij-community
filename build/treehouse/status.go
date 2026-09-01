package main

import (
	"fmt"
)

// WorkspaceStatus is one entry of the `status --json` array of the CLI.
//
// The type stays a map, so an unknown field such as `flavor` survives the round trip
// into the `read status` payload unchanged. parseStatus checks the type of every key the
// accessors read, so their type assertions cannot fail.
type WorkspaceStatus map[string]any

func (w WorkspaceStatus) Path() string        { return w["path"].(string) }
func (w WorkspaceStatus) Status() string      { return w["status"].(string) }
func (w WorkspaceStatus) LeaseID() string     { return w["lease_id"].(string) }
func (w WorkspaceStatus) LeaseHolder() string { return w["lease_holder"].(string) }
func (w WorkspaceStatus) Processes() []any    { return w["processes"].([]any) }

// parseStatus validates the array of the CLI. It rejects a missing or a wrongly typed
// key, because every later decision reads these values.
func parseStatus(value any) ([]WorkspaceStatus, error) {
	items, ok := value.([]any)
	if !ok {
		return nil, &cliError{message: "Treehouse status JSON must be an array.", exitCode: 1}
	}
	statuses := make([]WorkspaceStatus, 0, len(items))
	for index, item := range items {
		record, ok := item.(map[string]any)
		if !ok {
			return nil, &cliError{
				message:  fmt.Sprintf("Treehouse status entry %d must be an object.", index),
				exitCode: 1,
			}
		}
		_, hasLeaseID := record["lease_id"].(string)
		_, hasLeaseHolder := record["lease_holder"].(string)
		_, hasProcesses := record["processes"].([]any)
		if !hasLeaseID || !hasLeaseHolder || !hasProcesses {
			return nil, &cliError{
				message:  fmt.Sprintf("Treehouse status entry %d has an unsupported shape.", index),
				exitCode: 1,
				details:  map[string]any{"entry": record},
			}
		}
		description := fmt.Sprintf("Treehouse status entry %d", index)
		if _, err := requireStrings(record, description, "name", "path", "status"); err != nil {
			return nil, err
		}

		entry := WorkspaceStatus(record)
		if _, ok := record["leased_at"].(string); !ok {
			// A worktree with no lease reports null, and any other type becomes null too.
			entry["leased_at"] = nil
		}
		statuses = append(statuses, entry)
	}
	return statuses, nil
}

// readStatus is the only read of the live pool state.
func readStatus(rt Runtime) ([]WorkspaceStatus, error) {
	result := rt.Spawn([]string{treehouseCommandName, "status", "--json"}, SpawnOptions{})
	if result.ExitCode != 0 {
		return nil, nativeFailure("status", result, nil)
	}
	value, err := parseJSON(result.Stdout, "Treehouse status")
	if err != nil {
		return nil, err
	}
	return parseStatus(value)
}

// workspaceAt returns the entry of the live pool at one absolute path, or nil.
func workspaceAt(rt Runtime, statuses []WorkspaceStatus, path string) WorkspaceStatus {
	for _, entry := range statuses {
		if absPath(rt, entry.Path()) == path {
			return entry
		}
	}
	return nil
}

// liveLease returns the entry at the path of the identity, and whether that entry still
// holds the same lease ID and holder. The entry is nil when the path is absent.
func liveLease(rt Runtime, statuses []WorkspaceStatus, identity LeaseIdentity) (WorkspaceStatus, bool) {
	live := workspaceAt(rt, statuses, identity.Path)
	if live == nil {
		return nil, false
	}
	return live, live.LeaseID() == identity.LeaseID && live.LeaseHolder() == identity.LeaseHolder
}

// leaseOutcome is what the live pool says about one lease after a return.
type leaseOutcome int

const (
	// leaseReturned means that the live pool no longer holds this lease identity.
	leaseReturned leaseOutcome = iota
	// leaseHeld means that the same identity still holds the workspace.
	leaseHeld
	// leaseUnknown means that the live pool could not be read.
	leaseUnknown
)

// confirmLeaseGone asks the live pool whether one lease identity is gone. The live state
// is the authority on the outcome of a return, and the exit code is not.
//
// The CLI asks "Worktree has uncommitted changes. Clean and return?" when the tree is
// dirty. A "no" answer prints "Aborted." and exits 0, and the workspace keeps its lease.
// A non-interactive child reaches the end of its input at that prompt and aborts the same
// way. Another holder may also hold the workspace by now, so only the same identity means
// that the lease of this caller is still open.
//
// leaseHeld carries the live entry. leaseUnknown carries the read failure.
func confirmLeaseGone(rt Runtime, identity LeaseIdentity) (leaseOutcome, WorkspaceStatus, error) {
	statuses, err := readStatus(rt)
	if err != nil {
		return leaseUnknown, nil, err
	}
	live, held := liveLease(rt, statuses, identity)
	if !held {
		return leaseReturned, nil, nil
	}
	return leaseHeld, live, nil
}
