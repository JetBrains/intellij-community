package main

import (
	"encoding/json"
	"path/filepath"
)

// LeaseIdentity names one lease. Every guarded call carries all three values.
type LeaseIdentity struct {
	Path        string `json:"path"`
	LeaseID     string `json:"lease_id"`
	LeaseHolder string `json:"lease_holder"`
}

// LeaseReceipt is the `out/treehouse/lease.json` document of an acquired workspace.
//
// Schema version 1 holds the identity and the acquisition time. Schema version 2 adds
// the captured `source_head`. The wrapper writes only version 2. It still reads a
// version-1 receipt, so an older lease stays returnable.
type LeaseReceipt struct {
	SchemaVersion int `json:"schema_version"`
	LeaseIdentity
	AcquiredAt string `json:"acquired_at"`
	SourceHead string `json:"source_head,omitempty"`
}

// fields renders the receipt as the failure details of a command.
func (r *LeaseReceipt) fields() map[string]any {
	out := map[string]any{
		"schema_version": r.SchemaVersion,
		"path":           r.Path,
		"lease_id":       r.LeaseID,
		"lease_holder":   r.LeaseHolder,
		"acquired_at":    r.AcquiredAt,
	}
	if r.SourceHead != "" {
		out["source_head"] = r.SourceHead
	}
	return out
}

// fields renders the identity as the failure details of a command.
func (i LeaseIdentity) fields() map[string]any {
	return map[string]any{
		"path":         i.Path,
		"lease_id":     i.LeaseID,
		"lease_holder": i.LeaseHolder,
	}
}

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

// schemaVersionOf accepts only the two supported versions. parseJSON keeps a number as
// json.Number, so `2` and `2.0` both pass and a string does not.
func schemaVersionOf(value any) (int, bool) {
	number, ok := value.(json.Number)
	if !ok {
		return 0, false
	}
	parsed, err := number.Float64()
	if err != nil {
		return 0, false
	}
	if parsed == 1 || parsed == 2 {
		return int(parsed), true
	}
	return 0, false
}

func parseReceipt(rt Runtime, value any) (*LeaseReceipt, error) {
	record, ok := value.(map[string]any)
	if !ok {
		return nil, &cliError{message: "The Treehouse lease receipt must be an object.", exitCode: 2}
	}
	version, ok := schemaVersionOf(record["schema_version"])
	if !ok {
		return nil, &cliError{
			message:  "The Treehouse lease receipt has an unsupported schema version.",
			exitCode: 2,
			details:  map[string]any{"schema_version": record["schema_version"]},
		}
	}

	const description = "Treehouse lease receipt"
	path, err := nonEmptyString(record["path"], "path", description)
	if err != nil {
		return nil, err
	}
	leaseID, err := nonEmptyString(record["lease_id"], "lease_id", description)
	if err != nil {
		return nil, err
	}
	leaseHolder, err := nonEmptyString(record["lease_holder"], "lease_holder", description)
	if err != nil {
		return nil, err
	}
	acquiredAt, err := nonEmptyString(record["acquired_at"], "acquired_at", description)
	if err != nil {
		return nil, err
	}
	receipt := &LeaseReceipt{
		SchemaVersion: version,
		LeaseIdentity: LeaseIdentity{
			Path:        absPath(rt, path),
			LeaseID:     leaseID,
			LeaseHolder: leaseHolder,
		},
		AcquiredAt: acquiredAt,
	}
	if version == 2 {
		sourceHead, err := nonEmptyString(record["source_head"], "source_head", description)
		if err != nil {
			return nil, err
		}
		receipt.SourceHead = sourceHead
	}
	return receipt, nil
}

// parseAllocation reads the `get --lease --json` document of the CLI.
func parseAllocation(rt Runtime, value any) (LeaseIdentity, error) {
	record, ok := value.(map[string]any)
	if !ok {
		return LeaseIdentity{}, &cliError{message: "Treehouse acquire JSON must be an object.", exitCode: 1}
	}
	const description = "Treehouse acquire result"
	path, err := nonEmptyString(record["path"], "path", description)
	if err != nil {
		return LeaseIdentity{}, err
	}
	leaseID, err := nonEmptyString(record["lease_id"], "lease_id", description)
	if err != nil {
		return LeaseIdentity{}, err
	}
	leaseHolder, err := nonEmptyString(record["lease_holder"], "lease_holder", description)
	if err != nil {
		return LeaseIdentity{}, err
	}
	return LeaseIdentity{Path: absPath(rt, path), LeaseID: leaseID, LeaseHolder: leaseHolder}, nil
}

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
