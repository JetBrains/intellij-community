package main

import (
	"path/filepath"
	"strings"
)

// gitCommand builds one Git command for a path. `core.fsmonitor=false` keeps the command
// from starting or using a file-system monitor daemon of the workspace.
func gitCommand(path string, args ...string) []string {
	command := []string{"git", "-c", "core.fsmonitor=false", "-C", path}
	return append(command, args...)
}

// absPath makes a path absolute against the working directory of the runtime, the way
// Node's path.resolve does against the process directory.
func absPath(rt Runtime, path string) string {
	if filepath.IsAbs(path) {
		return filepath.Clean(path)
	}
	return filepath.Join(rt.Cwd(), path)
}

func gitRoot(rt Runtime) (string, error) {
	return gitRootAt(rt, rt.Cwd(), "current directory")
}

func gitRootAt(rt Runtime, path string, description string) (string, error) {
	result := rt.Spawn(gitCommand(path, "rev-parse", "--show-toplevel"), SpawnOptions{})
	if result.ExitCode != 0 {
		return "", &cliError{
			message:  "The " + description + " is not inside a Git workspace.",
			exitCode: 2,
			details:  map[string]any{"path": path, "stderr": strings.TrimSpace(result.Stderr)},
		}
	}
	return absPath(rt, strings.TrimSpace(result.Stdout)), nil
}

func gitHead(rt Runtime, path string, description string) (string, error) {
	result := rt.Spawn(gitCommand(path, "rev-parse", "--verify", "HEAD^{commit}"), SpawnOptions{})
	if result.ExitCode != 0 {
		return "", &cliError{
			message:  "The " + description + " does not have a valid HEAD commit.",
			exitCode: 2,
			details:  map[string]any{"path": path, "stderr": strings.TrimSpace(result.Stderr)},
		}
	}
	return nonEmptyString(strings.TrimSpace(result.Stdout), "HEAD", description)
}

// gitCommonDir resolves the shared Git directory. Two worktrees of one repository report
// the same value, so it proves that an acquired workspace belongs to the source
// repository.
func gitCommonDir(rt Runtime, path string, description string) (string, error) {
	result := rt.Spawn(gitCommand(path, "rev-parse", "--git-common-dir"), SpawnOptions{})
	if result.ExitCode != 0 {
		return "", &cliError{
			message:  "The " + description + " Git common directory could not be resolved.",
			exitCode: 2,
			details:  map[string]any{"path": path, "stderr": strings.TrimSpace(result.Stderr)},
		}
	}
	text, err := nonEmptyString(strings.TrimSpace(result.Stdout), "Git common directory", description)
	if err != nil {
		return "", err
	}
	if filepath.IsAbs(text) {
		return filepath.Clean(text), nil
	}
	// Git prints a path that is relative to the directory it ran in.
	return filepath.Join(path, text), nil
}

// gitDetach moves a workspace onto one commit with a detached HEAD.
//
// The command carries `--force`, because a case-insensitive filesystem hides a case-only
// rename from `git status`. Git still sees both spellings and refuses a plain checkout,
// so the detach of a workspace that every check reports as clean aborts.
//
// Two checks make the force safe. prepareAcquiredWorkspace calls gitChanges before the
// detach and refuses a dirty workspace. It reads HEAD and calls gitChanges again after
// the detach, and it fails if either one disagrees with the source HEAD. So the force
// overrides the refusal of Git only on an entry that `git status` cannot report.
//
// This is the `--force` of Git. The wrapper still never passes `--force` to the Treehouse
// CLI.
func gitDetach(rt Runtime, path string, commit string) SpawnResult {
	return rt.Spawn(gitCommand(path, "checkout", "--force", "--detach", commit), SpawnOptions{})
}

// gitChanges lists every modified, staged and untracked path of a workspace.
func gitChanges(rt Runtime, path string) ([]string, error) {
	result := rt.Spawn(gitCommand(path, "status", "--porcelain=v1", "--untracked-files=all"), SpawnOptions{})
	if result.ExitCode != 0 {
		return nil, &cliError{
			message:  "Git status failed.",
			exitCode: result.ExitCode,
			details:  map[string]any{"path": path, "stderr": strings.TrimSpace(result.Stderr)},
		}
	}
	changes := []string{}
	for _, line := range strings.Split(strings.TrimSpace(result.Stdout), "\n") {
		if line != "" {
			changes = append(changes, line)
		}
	}
	return changes, nil
}

// pathContains reports whether the child path is the parent path or sits below it. Both
// arguments must already be absolute.
func pathContains(parent string, child string) bool {
	relative, err := filepath.Rel(filepath.Clean(parent), filepath.Clean(child))
	if err != nil {
		return false
	}
	if relative == "." {
		return true
	}
	return relative != ".." &&
		!strings.HasPrefix(relative, ".."+string(filepath.Separator)) &&
		!filepath.IsAbs(relative)
}
