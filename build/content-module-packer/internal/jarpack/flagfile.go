// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// ParseFlagFile reads the packer's argument grammar: one `output=` line per jar, followed by the `module=` and
// `library=` lines it is built from.
//
// A flag file rather than plain arguments because a product packs thousands of jars from thousands of inputs, which
// does not fit a command line. `output=` starts a group, so the file is ordered and that order is the precedence Merge
// uses for duplicates - every `library=` of a group comes before its `module=` lines, which is the order JarPackager
// writes. Paths are resolved against baseDir.
func ParseFlagFile(path string, baseDir string) ([]MergeSpec, error) {
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	var specs []MergeSpec
	var current *MergeSpec
	flush := func() {
		if current != nil {
			specs = append(specs, *current)
		}
	}
	resolve := func(p string) string {
		if filepath.IsAbs(p) {
			return p
		}
		return filepath.Join(baseDir, p)
	}

	for _, line := range strings.Split(string(content), "\n") {
		line = strings.TrimRight(line, "\r")
		if strings.TrimSpace(line) == "" {
			continue
		}
		option, value, found := strings.Cut(line, "=")
		if !found {
			return nil, fmt.Errorf("expected `option=value`, got %q", line)
		}
		if option != "output" && current == nil {
			return nil, fmt.Errorf("`%s` before any `output=`", line)
		}
		switch option {
		case "output":
			flush()
			current = &MergeSpec{Output: resolve(value)}
		case "keep-manifest":
			if current.KeepManifest, err = parseStrictBool(value); err != nil {
				return nil, err
			}
		case "rewrite-boot-class-path":
			if current.RewriteBootClassPath, err = parseStrictBool(value); err != nil {
				return nil, err
			}
		case "module":
			current.Sources = append(current.Sources, Source{Path: resolve(value), Filter: ModuleOutputNameFilter})
		case "library":
			current.Sources = append(current.Sources, Source{Path: resolve(value), Filter: LibraryNameFilter})
		default:
			return nil, fmt.Errorf("unknown option %q in %q", option, line)
		}
	}
	flush()

	seen := make(map[string]int, len(specs))
	for i, spec := range specs {
		if len(spec.Sources) == 0 {
			return nil, fmt.Errorf("no inputs for %q", spec.Output)
		}
		if prev, dup := seen[spec.Output]; dup {
			return nil, fmt.Errorf("%q is declared twice, at group %d and %d", spec.Output, prev, i)
		}
		seen[spec.Output] = i
	}
	return specs, nil
}

// parseStrictBool matches Kotlin's toBooleanStrict: only the two exact spellings, so a typo is an error rather than a
// silently false flag that would change the bytes.
func parseStrictBool(value string) (bool, error) {
	switch value {
	case "true":
		return true, nil
	case "false":
		return false, nil
	}
	return false, fmt.Errorf("expected `true` or `false`, got %q", value)
}
