package main

import (
	"bytes"
	"encoding/json"
	"path/filepath"
)

// LeaseIdentity names one lease. Every guarded call carries all three values.
type LeaseIdentity struct {
	Path        string `json:"path"`
	LeaseID     string `json:"lease_id"`
	LeaseHolder string `json:"lease_holder"`
}

// LeaseReceipt is the `out/treehouse/lease.json` document of an acquired workspace. It
// holds the identity, the acquisition time and the captured `source_head`. The wrapper
// reads and writes schema version 2 only.
type LeaseReceipt struct {
	SchemaVersion int `json:"schema_version"`
	LeaseIdentity
	AcquiredAt string `json:"acquired_at"`
	SourceHead string `json:"source_head"`
}

// receiptSchemaVersion is the one schema version the wrapper accepts.
const receiptSchemaVersion = 2

// jsonFields renders a value as the object of its JSON tags, so a failure detail names
// each field the way the receipt does.
func jsonFields(value any) map[string]any {
	text, err := json.Marshal(value)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	decoder := json.NewDecoder(bytes.NewReader(text))
	decoder.UseNumber()
	var out map[string]any
	if err := decoder.Decode(&out); err != nil {
		return map[string]any{"error": err.Error()}
	}
	return out
}

// fields renders the receipt as the failure details of a command.
func (r *LeaseReceipt) fields() map[string]any { return jsonFields(r) }

// fields renders the identity as the failure details of a command.
func (i LeaseIdentity) fields() map[string]any { return jsonFields(i) }

// AcquireResult is the `data` payload of `write acquire`.
type AcquireResult struct {
	LeaseReceipt
	PreparedHead string `json:"prepared_head"`
	ReceiptPath  string `json:"receipt_path"`
}

// ReturnResult is the `data` payload of `write return`.
type ReturnResult struct {
	LeaseIdentity
	Returned       bool `json:"returned"`
	Dirty          bool `json:"dirty"`
	ReceiptRemoved bool `json:"receipt_removed"`
}

// receiptPathOf is the receipt path of one workspace.
func receiptPathOf(workspace string) string {
	return filepath.Join(workspace, receiptRelativePath)
}

// parseReceipt reads one receipt document. parseJSON keeps a number as json.Number, so
// `2` and `2.0` both pass the version check and a string does not.
func parseReceipt(rt Runtime, value any) (*LeaseReceipt, error) {
	record, ok := value.(map[string]any)
	if !ok {
		return nil, &cliError{message: "The Treehouse lease receipt must be an object.", exitCode: 2}
	}
	number, isNumber := record["schema_version"].(json.Number)
	version, err := number.Float64()
	if !isNumber || err != nil || version != receiptSchemaVersion {
		return nil, &cliError{
			message:  "The Treehouse lease receipt has an unsupported schema version.",
			exitCode: 2,
			details:  map[string]any{"schema_version": record["schema_version"]},
		}
	}

	values, err := requireStrings(record, "Treehouse lease receipt",
		"path", "lease_id", "lease_holder", "acquired_at", "source_head")
	if err != nil {
		return nil, err
	}
	return &LeaseReceipt{
		SchemaVersion: receiptSchemaVersion,
		LeaseIdentity: identityOf(rt, values),
		AcquiredAt:    values["acquired_at"],
		SourceHead:    values["source_head"],
	}, nil
}

// parseAllocation reads the `get --lease --json` document of the CLI.
func parseAllocation(rt Runtime, value any) (LeaseIdentity, error) {
	record, ok := value.(map[string]any)
	if !ok {
		return LeaseIdentity{}, &cliError{message: "Treehouse acquire JSON must be an object.", exitCode: 1}
	}
	values, err := requireStrings(record, "Treehouse acquire result", "path", "lease_id", "lease_holder")
	if err != nil {
		return LeaseIdentity{}, err
	}
	return identityOf(rt, values), nil
}

// identityOf builds the identity from the three checked fields of a document.
func identityOf(rt Runtime, values map[string]string) LeaseIdentity {
	return LeaseIdentity{
		Path:        absPath(rt, values["path"]),
		LeaseID:     values["lease_id"],
		LeaseHolder: values["lease_holder"],
	}
}

// returnPromptAnswer is what the CLI reads at "Worktree has uncommitted changes. Clean and
// return? [Y/n]". The wrapper writes the answer itself, because `--confirm-preserved` is
// already that answer and a session of an agent has no TTY. A lease that only a person at a
// terminal can return stays open forever.
//
// The answer is not a `--force`. The CLI still detaches the workspace and still honors both
// lease guards, and verifyReturned still reads the live pool for the outcome.
const returnPromptAnswer = "y\n"

// returnCommand always guards the return with both halves of the lease identity, so a
// lease that another holder took in the meantime stays untouched. It never passes
// `--force`.
func returnCommand(identity LeaseIdentity) []string {
	return []string{
		treehouseCommandName, "return", identity.Path,
		"--if-lease-id", identity.LeaseID,
		"--if-lease-holder", identity.LeaseHolder,
	}
}
