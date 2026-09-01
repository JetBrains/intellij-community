package main

import (
	"strings"
)

func executeReturn(rt Runtime, workspaceOption string, confirmPreserved bool) (any, error) {
	workspace := absPath(rt, workspaceOption)
	// A wrapper that runs inside the leased workspace appears as a workspace process, and
	// so does its parent shell. The return would then always refuse itself.
	if pathContains(workspace, rt.Cwd()) {
		return nil, &cliError{
			message:  "Return must be run from outside the leased workspace.",
			exitCode: 2,
			details:  map[string]any{"cwd": rt.Cwd(), "workspace": workspace},
		}
	}

	receiptPath := receiptPathOf(workspace)
	text, readErr := rt.ReadTextFile(receiptPath)
	if readErr != nil {
		return nil, &cliError{
			message:  "No Treehouse lease receipt exists in the requested workspace.",
			exitCode: 2,
			details:  map[string]any{"path": workspace, "receipt_path": receiptPath},
		}
	}
	value, err := parseJSON(text, "Treehouse lease receipt")
	if err != nil {
		return nil, err
	}
	receipt, err := parseReceipt(rt, value)
	if err != nil {
		return nil, err
	}
	if receipt.Path != workspace {
		return nil, &cliError{
			message:  "The Treehouse lease receipt belongs to a different workspace.",
			exitCode: 2,
			details: map[string]any{
				"requested_path": workspace,
				"receipt_path":   receipt.Path,
				"lease_id":       receipt.LeaseID,
				"lease_holder":   receipt.LeaseHolder,
			},
		}
	}
	root, err := gitRootAt(rt, workspace, "requested workspace")
	if err != nil {
		return nil, err
	}
	if root != workspace {
		return nil, &cliError{
			message:  "The requested path is not the root of its Git workspace.",
			exitCode: 2,
			details:  map[string]any{"requested_path": workspace, "git_root": root},
		}
	}

	statuses, err := readStatus(rt)
	if err != nil {
		return nil, err
	}
	live := findWorkspace(statuses, func(entry WorkspaceStatus) bool {
		return absPath(rt, entry.Path()) == receipt.Path
	})
	if live == nil || live.LeaseID() != receipt.LeaseID || live.LeaseHolder() != receipt.LeaseHolder {
		return nil, &cliError{
			message:  "The local Treehouse receipt does not match the live lease; refusing to return it.",
			exitCode: 2,
			details:  map[string]any{"receipt": receipt, "live": live},
		}
	}
	if len(live.Processes()) > 0 {
		details := receipt.LeaseIdentity.fields()
		details["processes"] = live.Processes()
		return nil, &cliError{
			message:  "The Treehouse workspace still has live processes; stop them before returning it.",
			exitCode: 2,
			details:  details,
		}
	}

	changes, err := gitChanges(rt, receipt.Path)
	if err != nil {
		return nil, err
	}
	dirty := len(changes) > 0
	if dirty && !confirmPreserved {
		details := receipt.fields()
		details["changes"] = changes
		return nil, &cliError{
			message:  "The workspace is dirty. Preserve all intended work, then rerun with --confirm-preserved.",
			exitCode: 2,
			details:  details,
		}
	}

	// A dirty return meets the confirmation prompt of the CLI. returnPromptAnswer holds the
	// answer and the reason. The wrapper never passes `--force`.
	options := SpawnOptions{}
	if dirty {
		options.Stdin = returnPromptAnswer
	}
	result := rt.Spawn(returnCommand(receipt.LeaseIdentity), options)
	if result.ExitCode != 0 {
		return nil, nativeFailure("return", result, receipt.fields())
	}
	if err := verifyReturned(rt, receipt, result); err != nil {
		return nil, err
	}

	receiptRemoved := true
	if rt.RemoveFile(receiptPath) != nil {
		receiptRemoved = false
	}
	return ReturnResult{
		LeaseIdentity:  receipt.LeaseIdentity,
		Returned:       true,
		Dirty:          dirty,
		ReceiptRemoved: receiptRemoved,
	}, nil
}

// verifyReturned confirms against the live pool that the lease of the receipt is gone. A
// receipt that the wrapper removes on the exit code alone leaves a leased workspace that
// nothing can return. confirmLeaseGone holds the mechanics of the check.
func verifyReturned(rt Runtime, receipt *LeaseReceipt, result SpawnResult) error {
	state, live, err := confirmLeaseGone(rt, receipt.LeaseIdentity)
	if state == leaseReturned {
		return nil
	}
	if state == leaseUnknown {
		details := receipt.fields()
		details["receipt_removed"] = false
		details["native_exit_code"] = result.ExitCode
		details["cause"] = causeDetails(err)
		return &cliError{
			message:  "Treehouse reported a successful return, but the live lease state could not confirm it; retain this lease identity.",
			exitCode: 1,
			details:  details,
		}
	}
	details := receipt.fields()
	details["receipt_removed"] = false
	details["native_exit_code"] = result.ExitCode
	details["native_stderr"] = strings.TrimSpace(result.Stderr)
	details["live_status"] = live.Status()
	return &cliError{
		message:  "Treehouse exited without an error, but the workspace is still leased; the return was aborted. Retain this lease identity.",
		exitCode: 1,
		details:  details,
	}
}
