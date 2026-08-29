package main

import (
	"strings"
)

// holderFrom resolves the lease holder: the option, then TREEHOUSE_LEASE_HOLDER, then a
// generated label. A blank holder is a usage failure, because the return guard needs it.
func holderFrom(rt Runtime, requested string, requestedPresent bool) (string, error) {
	holder := requested
	if !requestedPresent {
		if value, ok := rt.Env("TREEHOUSE_LEASE_HOLDER"); ok {
			holder = value
		} else {
			holder = "agent-" + rt.UUID()
		}
	}
	if strings.TrimSpace(holder) == "" {
		return "", failUsage("--holder must not be blank")
	}
	return holder, nil
}

// rollbackOutcome is the result of one rollback return.
type rollbackOutcome struct {
	SpawnResult
	state     leaseOutcome
	live      WorkspaceStatus
	statusErr error
}

// returned reports whether the live pool confirmed that the lease is gone.
func (o rollbackOutcome) returned() bool { return o.state == leaseReturned }

// rollbackMessages holds the three wordings one rollback site needs.
type rollbackMessages struct {
	// returned is the wording for a lease the live pool reports as gone.
	returned string
	// notReturned is the wording for a lease that is still open.
	notReturned string
	// unconfirmed is the wording for a live state that could not be read.
	unconfirmed string
}

// rollbackAcquire returns a lease the wrapper took but cannot use, then confirms the
// outcome against the live pool. A non-zero exit code counts as leaseHeld, because
// nothing returned the lease.
func rollbackAcquire(rt Runtime, identity LeaseIdentity) rollbackOutcome {
	spawned := rt.Spawn(returnCommand(identity), SpawnOptions{})
	if spawned.ExitCode != 0 {
		return rollbackOutcome{SpawnResult: spawned, state: leaseHeld}
	}
	state, live, err := confirmLeaseGone(rt, identity)
	return rollbackOutcome{SpawnResult: spawned, state: state, live: live, statusErr: err}
}

// rollbackFailure builds the failure of one rollback site. It picks the wording that
// matches the live lease state, and it adds the rollback details.
func rollbackFailure(outcome rollbackOutcome, messages rollbackMessages, details map[string]any) error {
	message := messages.notReturned
	switch outcome.state {
	case leaseReturned:
		message = messages.returned
	case leaseUnknown:
		message = messages.unconfirmed
	}
	details["rollback_exit_code"] = outcome.ExitCode
	details["rollback_stderr"] = strings.TrimSpace(outcome.Stderr)
	details["rollback_returned"] = outcome.returned()
	if outcome.live != nil {
		details["rollback_live_status"] = outcome.live.Status()
	}
	if outcome.statusErr != nil {
		details["rollback_status_error"] = causeDetails(outcome.statusErr)
	}
	return &cliError{message: message, exitCode: 1, details: details}
}

// prepareAcquiredWorkspace verifies the new lease and detaches the workspace at the exact
// HEAD of the caller. It performs no fetch, rebase, stash or file copy. The index, the
// working-tree changes and the untracked files of the caller stay behind on purpose.
func prepareAcquiredWorkspace(rt Runtime, allocation LeaseIdentity, sourceCommonDir string, sourceHead string) error {
	statuses, err := readStatus(rt)
	if err != nil {
		return err
	}
	live := findWorkspace(statuses, func(entry WorkspaceStatus) bool {
		return absPath(rt, entry.Path()) == allocation.Path
	})
	if live == nil || live.LeaseID() != allocation.LeaseID || live.LeaseHolder() != allocation.LeaseHolder {
		return &cliError{
			message:  "The acquired workspace does not match the live Treehouse lease.",
			exitCode: 1,
			details:  map[string]any{"allocation": allocation, "live": live},
		}
	}

	destinationRoot, err := gitRootAt(rt, allocation.Path, "acquired workspace")
	if err != nil {
		return err
	}
	if destinationRoot != allocation.Path {
		return &cliError{
			message:  "The acquired path is not the root of its Git workspace.",
			exitCode: 1,
			details:  map[string]any{"acquired_path": allocation.Path, "git_root": destinationRoot},
		}
	}
	destinationCommonDir, err := gitCommonDir(rt, destinationRoot, "acquired workspace")
	if err != nil {
		return err
	}
	if destinationCommonDir != sourceCommonDir {
		return &cliError{
			message:  "The acquired workspace does not share the source Git repository.",
			exitCode: 1,
			details: map[string]any{
				"source_git_common_dir":      sourceCommonDir,
				"destination_git_common_dir": destinationCommonDir,
			},
		}
	}

	initialChanges, err := gitChanges(rt, destinationRoot)
	if err != nil {
		return err
	}
	if len(initialChanges) > 0 {
		return &cliError{
			message:  "The acquired workspace is dirty before HEAD preparation.",
			exitCode: 1,
			details:  map[string]any{"path": destinationRoot, "changes": initialChanges},
		}
	}

	head, err := gitHead(rt, destinationRoot, "acquired workspace")
	if err != nil {
		return err
	}
	if head != sourceHead {
		checkout := gitDetach(rt, destinationRoot, sourceHead)
		if checkout.ExitCode != 0 {
			return &cliError{
				message:  "The acquired workspace could not be detached at the source HEAD.",
				exitCode: checkout.ExitCode,
				details: map[string]any{
					"path":        destinationRoot,
					"source_head": sourceHead,
					"stdout":      strings.TrimSpace(checkout.Stdout),
					"stderr":      strings.TrimSpace(checkout.Stderr),
				},
			}
		}
	}

	preparedHead, err := gitHead(rt, destinationRoot, "prepared workspace")
	if err != nil {
		return err
	}
	preparedChanges, err := gitChanges(rt, destinationRoot)
	if err != nil {
		return err
	}
	if preparedHead != sourceHead || len(preparedChanges) > 0 {
		return &cliError{
			message:  "The acquired workspace failed exact-HEAD verification.",
			exitCode: 1,
			details: map[string]any{
				"path":          destinationRoot,
				"source_head":   sourceHead,
				"prepared_head": preparedHead,
				"changes":       preparedChanges,
			},
		}
	}
	return nil
}

func executeAcquire(rt Runtime, holderOption string, holderPresent bool) (any, error) {
	root, err := gitRoot(rt)
	if err != nil {
		return nil, err
	}
	sourceHead, err := gitHead(rt, root, "source workspace")
	if err != nil {
		return nil, err
	}
	sourceCommonDir, err := gitCommonDir(rt, root, "source workspace")
	if err != nil {
		return nil, err
	}
	statuses, err := readStatus(rt)
	if err != nil {
		return nil, err
	}
	current := findWorkspace(statuses, func(entry WorkspaceStatus) bool {
		return absPath(rt, entry.Path()) == root && entry.LeaseID() != ""
	})
	if current != nil {
		return nil, &cliError{
			message:  "The current workspace already has an active Treehouse lease.",
			exitCode: 2,
			details: map[string]any{
				"path":         current.Path(),
				"lease_id":     current.LeaseID(),
				"lease_holder": current.LeaseHolder(),
			},
		}
	}

	holder, err := holderFrom(rt, holderOption, holderPresent)
	if err != nil {
		return nil, err
	}
	result := rt.Spawn([]string{treehouseCommandName, "get", "--lease", "--json", "--lease-holder", holder}, SpawnOptions{})
	if result.ExitCode != 0 {
		return nil, nativeFailure("acquire", result, map[string]any{"lease_holder": holder})
	}

	allocation, allocationErr := parseAllocationOutput(rt, result.Stdout)
	if allocationErr != nil {
		// The CLI may hold a lease that the wrapper cannot name. One live entry with this
		// holder recovers the identity, so the lease can go back.
		recovered, recoverErr := recoverSingleLease(rt, holder)
		if recoverErr != nil {
			return nil, recoverErr
		}
		if recovered == nil {
			return nil, allocationErr
		}
		rollback := rollbackAcquire(rt, *recovered)
		details := recovered.fields()
		details["cause"] = allocationErr.Error()
		return nil, rollbackFailure(rollback, rollbackMessages{
			returned:    "Treehouse acquired a lease but returned unusable JSON; the recovered lease was returned.",
			notReturned: "Treehouse acquired a lease but returned unusable JSON, and the recovered lease could not be returned; retain this lease identity.",
			unconfirmed: "Treehouse acquired a lease but returned unusable JSON. The return of the recovered lease could not be confirmed; retain this lease identity.",
		}, details)
	}

	if allocation.LeaseHolder != holder {
		rollback := rollbackAcquire(rt, allocation)
		details := allocation.fields()
		details["requested_holder"] = holder
		return nil, rollbackFailure(rollback, rollbackMessages{
			returned:    "Treehouse recorded a different lease holder; the lease was returned.",
			notReturned: "Treehouse recorded a different lease holder and the lease could not be returned; retain this lease identity.",
			unconfirmed: "Treehouse recorded a different lease holder. The return of the lease could not be confirmed; retain this lease identity.",
		}, details)
	}

	receipt := &LeaseReceipt{
		SchemaVersion: 2,
		LeaseIdentity: allocation,
		AcquiredAt:    isoTimestamp(rt.Now()),
		SourceHead:    sourceHead,
	}
	receiptPath := receiptPathOf(receipt.Path)
	text, err := renderJSON(receipt)
	if err != nil {
		return nil, err
	}
	if writeErr := rt.WriteTextFile(receiptPath, string(text)); writeErr != nil {
		rollback := rollbackAcquire(rt, receipt.LeaseIdentity)
		details := receipt.fields()
		details["receipt_path"] = receiptPath
		details["write_error"] = writeErr.Error()
		return nil, rollbackFailure(rollback, rollbackMessages{
			returned:    "The lease receipt could not be written, so the acquired workspace was returned.",
			notReturned: "The lease receipt could not be written and the acquired workspace could not be returned; retain this lease identity.",
			unconfirmed: "The lease receipt could not be written. The return of the acquired workspace could not be confirmed; retain this lease identity.",
		}, details)
	}

	if prepareErr := prepareAcquiredWorkspace(rt, receipt.LeaseIdentity, sourceCommonDir, sourceHead); prepareErr != nil {
		rollback := rollbackAcquire(rt, receipt.LeaseIdentity)
		receiptRemoved := false
		if rollback.returned() {
			// The lease is already back. A stale receipt is worth reporting, but it must
			// not mask the preparation failure.
			if rt.RemoveFile(receiptPath) == nil {
				receiptRemoved = true
			}
		}
		details := receipt.fields()
		details["receipt_path"] = receiptPath
		details["receipt_removed"] = receiptRemoved
		details["cause"] = causeDetails(prepareErr)
		return nil, rollbackFailure(rollback, rollbackMessages{
			returned:    "The acquired workspace could not be prepared at the source HEAD, so the lease was returned.",
			notReturned: "The acquired workspace could not be prepared at the source HEAD and the lease could not be returned; retain this lease identity.",
			unconfirmed: "The acquired workspace could not be prepared at the source HEAD. The return of the lease could not be confirmed; retain this lease identity.",
		}, details)
	}

	return AcquireResult{LeaseReceipt: *receipt, PreparedHead: sourceHead, ReceiptPath: receiptPath}, nil
}

// parseAllocationOutput decodes and validates the acquire output of the CLI.
func parseAllocationOutput(rt Runtime, stdout string) (LeaseIdentity, error) {
	value, err := parseJSON(stdout, "Treehouse acquire")
	if err != nil {
		return LeaseIdentity{}, err
	}
	return parseAllocation(rt, value)
}

// recoverSingleLease finds the lease of one holder in the live pool. It returns nil when
// the count is not exactly one, because a guess would return the wrong workspace.
func recoverSingleLease(rt Runtime, holder string) (*LeaseIdentity, error) {
	statuses, err := readStatus(rt)
	if err != nil {
		return nil, err
	}
	var matches []WorkspaceStatus
	for _, entry := range statuses {
		if entry.LeaseHolder() == holder && entry.LeaseID() != "" {
			matches = append(matches, entry)
		}
	}
	if len(matches) != 1 {
		return nil, nil
	}
	return &LeaseIdentity{
		Path:        matches[0].Path(),
		LeaseID:     matches[0].LeaseID(),
		LeaseHolder: matches[0].LeaseHolder(),
	}, nil
}
