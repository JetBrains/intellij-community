package main

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"
)

// devDataLink is the workspace-relative path that every dev launcher, run configuration and tool names for the dev
// data of the IDEs it starts.
const devDataLink = "out/dev-data"

// workspaceMarker is the file in a dev-data root that names the real path of the checkout that owns the root.
const workspaceMarker = ".workspace"

// devDataRootVariable replaces the default parent of the dev-data roots, as `--output_user_root` does for Bazel.
const devDataRootVariable = "INTELLIJ_DEV_DATA_ROOT"

// keepError is a reason to keep the dev data inside the workspace for this launch. It is a warning, not a failure.
type keepError struct {
	message string
}

func (e *keepError) Error() string {
	return e.message
}

// devDataRoot returns the dev-data root of the checkout at [workspace], a real path, and false where the dev data stays
// inside the workspace. The root is on by default on macOS, where the IDE's writes under `out` make the next Bazel
// builds re-scan the source tree. Elsewhere it is on only with [devDataRootVariable], and never on Windows.
func devDataRoot(workspace string, getenv func(string) string, goos string) (string, bool) {
	if goos == "windows" {
		return "", false
	}
	parent := getenv(devDataRootVariable)
	if parent == "" {
		home := getenv("HOME")
		if goos != "darwin" || home == "" {
			return "", false
		}
		parent = filepath.Join(home, "Library", "Caches", "JetBrains", "MonorepoDevData")
	}
	sum := sha256.Sum256([]byte(filepath.ToSlash(workspace)))
	return filepath.Join(parent, filepath.Base(workspace)+"-"+hex.EncodeToString(sum[:4])), true
}

// ensureDevData makes [devDataLink] in [workspace] a symbolic link to the dev-data root of the checkout. It moves the
// dev data of an older launch out of the workspace first. It never fails a launch: when the link cannot be made, it
// writes a warning to [warnings], and the IDE uses the directory inside the workspace.
func ensureDevData(workspace string, getenv func(string) string, warnings io.Writer) {
	realWorkspace, err := filepath.EvalSymlinks(workspace)
	if err != nil {
		return
	}
	root, enabled := devDataRoot(realWorkspace, getenv, runtime.GOOS)
	if !enabled {
		return
	}
	link := filepath.Join(workspace, filepath.FromSlash(devDataLink))
	// Two launches can race for the same link. The loser sees the entry appear or disappear under it and looks again.
	for attempt := 0; attempt < 3; attempt++ {
		err = linkDevData(link, realWorkspace, root, warnings)
		if !errors.Is(err, fs.ErrExist) && !errors.Is(err, fs.ErrNotExist) {
			break
		}
	}
	if err != nil {
		fmt.Fprintf(warnings, "WARNING: the dev data stays in %s: %v\n", link, err)
	}
}

func linkDevData(link, workspace, root string, warnings io.Writer) error {
	if out, err := filepath.EvalSymlinks(filepath.Dir(link)); err == nil && !isUnder(out, workspace) {
		// The user keeps `out` outside the workspace already, so the IDE's writes do not reach Bazel's file watcher.
		return nil
	}
	info, err := os.Lstat(link)
	if errors.Is(err, fs.ErrNotExist) {
		return createDevDataLink(link, workspace, root, warnings)
	}
	if err != nil {
		return err
	}
	if info.Mode()&fs.ModeSymlink != 0 {
		// The link decides, not the formula, so a moved checkout keeps its data.
		target, err := os.Readlink(link)
		if err != nil {
			return err
		}
		if !filepath.IsAbs(target) {
			target = filepath.Join(filepath.Dir(link), target)
		}
		// A cache cleaner may have removed the target. The IDE then starts with an empty config, as on a first launch.
		if err := os.MkdirAll(target, 0o755); err != nil {
			return err
		}
		return writeWorkspaceMarker(target, workspace)
	}
	if !info.IsDir() {
		return &keepError{link + " is not a directory"}
	}
	return moveDevData(link, workspace, root)
}

func createDevDataLink(link, workspace, root string, warnings io.Writer) error {
	_, err := os.Stat(root)
	created := errors.Is(err, fs.ErrNotExist)
	if err := os.MkdirAll(root, 0o755); err != nil {
		return err
	}
	if err := writeWorkspaceMarker(root, workspace); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(link), 0o755); err != nil {
		return err
	}
	if err := os.Symlink(root, link); err != nil {
		return err
	}
	if created {
		reportOrphanRoots(filepath.Dir(root), root, warnings)
	}
	return nil
}

// moveDevData moves the directory [link] to [root] and replaces it with a link. A root that holds entries already gets
// the entries it lacks. The move keeps the dev data in place when a dev IDE runs from it, when an entry exists on both
// sides, or when the root is on another volume.
func moveDevData(link, workspace, root string) error {
	if row := liveDevIdeRow(link); row != "" {
		return &keepError{"a dev IDE runs from " + filepath.Join(link, row) + ". Stop it to move the dev data out of the workspace"}
	}
	if linked, err := os.Stat(link); err == nil {
		if target, err := os.Stat(root); err == nil && os.SameFile(linked, target) {
			// A concurrent launch has moved the directory and made the link since this launch looked.
			return nil
		}
	}
	if err := os.MkdirAll(filepath.Dir(root), 0o755); err != nil {
		return err
	}
	entries, err := devDataEntries(root)
	if err != nil && !errors.Is(err, fs.ErrNotExist) {
		return err
	}
	if len(entries) == 0 {
		// A root with only its marker is empty. Rename replaces an empty directory but not one with a file in it.
		if err := os.Remove(filepath.Join(root, workspaceMarker)); err != nil && !errors.Is(err, fs.ErrNotExist) {
			return err
		}
		if err := os.Rename(link, root); err != nil {
			return renameError(err, link, root)
		}
	} else {
		legacy, err := devDataEntries(link)
		if err != nil {
			return err
		}
		var both []string
		for name := range legacy {
			if entries[name] {
				both = append(both, name)
			}
		}
		if len(both) > 0 {
			return &keepError{fmt.Sprintf("%s and %s both hold %s. Merge them by hand", link, root, strings.Join(both, ", "))}
		}
		for name := range legacy {
			if err := os.Rename(filepath.Join(link, name), filepath.Join(root, name)); err != nil && !errors.Is(err, fs.ErrNotExist) {
				return renameError(err, link, root)
			}
		}
		if err := os.Remove(link); err != nil {
			return err
		}
	}
	if err := writeWorkspaceMarker(root, workspace); err != nil {
		return err
	}
	return os.Symlink(root, link)
}

// devDataEntries returns the names of the entries in [dir] except [workspaceMarker].
func devDataEntries(dir string) (map[string]bool, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	names := map[string]bool{}
	for _, entry := range entries {
		if entry.Name() != workspaceMarker {
			names[entry.Name()] = true
		}
	}
	return names, nil
}

func renameError(err error, link, root string) error {
	if errors.Is(err, syscall.EXDEV) {
		return &keepError{fmt.Sprintf("%s is on another volume than %s. Stop the dev IDEs, then run: mv %s %s && ln -s %s %s", root, link, link, root, root, link)}
	}
	return err
}

// liveDevIdeRow returns the name of a row in [devData] whose IDE still runs, or an empty string. An IDE writes its
// process ID to `config/.lock` (`DirectoryLock`), and a launcher links its home under `homes/<pid>`.
func liveDevIdeRow(devData string) string {
	rows, err := os.ReadDir(devData)
	if err != nil {
		return ""
	}
	for _, row := range rows {
		if !row.IsDir() {
			continue
		}
		rowDir := filepath.Join(devData, row.Name())
		if lock, err := os.ReadFile(filepath.Join(rowDir, "config", ".lock")); err == nil {
			if pid, err := strconv.Atoi(strings.TrimSpace(string(lock))); err == nil && pid != os.Getpid() && processRuns(pid) {
				return row.Name()
			}
		}
		homes, _ := os.ReadDir(filepath.Join(rowDir, "homes"))
		for _, home := range homes {
			if pid, err := strconv.Atoi(home.Name()); err == nil && pid != os.Getpid() && processRuns(pid) {
				return row.Name()
			}
		}
	}
	return ""
}

func writeWorkspaceMarker(root, workspace string) error {
	marker := filepath.Join(root, workspaceMarker)
	if current, err := os.ReadFile(marker); err == nil && string(current) == workspace+"\n" {
		return nil
	}
	return os.WriteFile(marker, []byte(workspace+"\n"), 0o644)
}

// reportOrphanRoots names each root in [parent] whose checkout no longer exists. It removes nothing: a moved checkout
// that has not launched since the move still names its old path.
func reportOrphanRoots(parent, current string, warnings io.Writer) {
	entries, err := os.ReadDir(parent)
	if err != nil {
		return
	}
	for _, entry := range entries {
		root := filepath.Join(parent, entry.Name())
		if !entry.IsDir() || root == current {
			continue
		}
		marker, err := os.ReadFile(filepath.Join(root, workspaceMarker))
		if err != nil {
			continue
		}
		workspace := strings.TrimSpace(string(marker))
		if workspace == "" || exists(workspace) || !exists(filepath.Dir(workspace)) {
			continue
		}
		fmt.Fprintf(warnings, "NOTE: the dev data %s belongs to the checkout %s, which no longer exists. To free the space, run: rm -rf %s\n", root, workspace, root)
	}
}

func exists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

func isUnder(path, dir string) bool {
	relative, err := filepath.Rel(dir, path)
	return err == nil && relative != ".." && !strings.HasPrefix(relative, ".."+string(filepath.Separator))
}
