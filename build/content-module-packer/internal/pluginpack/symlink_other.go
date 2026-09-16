//go:build !windows

package pluginpack

import "path/filepath"

// evalSymlinks returns the path with every link resolved.
func evalSymlinks(path string) (string, error) {
	return filepath.EvalSymlinks(path)
}
