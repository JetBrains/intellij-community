// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package jarpack

import (
	"os"
	"path/filepath"
	"testing"
)

func parseRecipe(t *testing.T, lines string) ([]MergeSpec, error) {
	t.Helper()
	path := filepath.Join(t.TempDir(), "recipe.params")
	if err := os.WriteFile(path, []byte(lines), 0o644); err != nil {
		t.Fatal(err)
	}
	return ParseFlagFile(path, "/exec/root")
}

func TestParseFlagFileGroupsByOutputAndKeepsSourceOrder(t *testing.T) {
	specs, err := parseRecipe(t, "output=out/a.jar\nkeep-manifest=true\nlibrary=lib/one.jar\nmodule=mod/a.jar\n"+
		"output=out/b.jar\nrewrite-boot-class-path=true\nmodule=mod/b.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if len(specs) != 2 {
		t.Fatalf("%d groups, want 2", len(specs))
	}
	if got, want := specs[0].Output, filepath.Join("/exec/root", "out/a.jar"); got != want {
		t.Errorf("output is %q, want %q resolved against the base directory", got, want)
	}
	if !specs[0].KeepManifest || specs[1].KeepManifest {
		t.Error("keep-manifest applied to the wrong group")
	}
	if specs[0].RewriteBootClassPath || !specs[1].RewriteBootClassPath {
		t.Error("rewrite-boot-class-path applied to the wrong group")
	}
	// The order is the precedence the merge uses, so it is part of the grammar rather than an accident of parsing.
	if got, want := specs[0].Sources[0].Path, filepath.Join("/exec/root", "lib/one.jar"); got != want {
		t.Errorf("first source is %q, want the library %q", got, want)
	}
}

func TestParseFlagFileRejectsWhatWouldChangeBytesSilently(t *testing.T) {
	for name, lines := range map[string]string{
		"an option before any output":      "module=mod/a.jar\n",
		"a line that is not an assignment": "output=out/a.jar\nmodule\n",
		"an unknown option":                "output=out/a.jar\nmodul=mod/a.jar\n",
		"a mis-spelled boolean":            "output=out/a.jar\nkeep-manifest=TRUE\nmodule=mod/a.jar\n",
		"a group with no source":           "output=out/a.jar\n",
		"the same output twice":            "output=out/a.jar\nmodule=mod/a.jar\noutput=out/a.jar\nmodule=mod/b.jar\n",
		"two trace destinations": "output=out/a.jar\ntrace-file=out/a.jar.spans.json\nmodule=mod/a.jar\n" +
			"output=out/b.jar\ntrace-file=out/b.jar.spans.json\nmodule=mod/b.jar\n",
	} {
		if _, err := parseRecipe(t, lines); err == nil {
			t.Errorf("%s was accepted", name)
		}
	}
}

func TestParseFlagFileReadsTheTraceDestination(t *testing.T) {
	// The line the packing rule writes, in the position it writes it: immediately after `output=`, because that is
	// where the group starts. It is a worker's only per-request channel - a `--trace-file=` on the spawn would belong to
	// the worker process and to its WorkerKey.
	specs, err := parseRecipe(t, "output=out/a.jar\ntrace-file=out/a.jar.spans.json\nmodule=mod/a.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if got, want := specs[0].TraceFile, filepath.Join("/exec/root", "out/a.jar.spans.json"); got != want {
		t.Errorf("trace-file is %q, want %q resolved against the base directory like every other path", got, want)
	}
	// And it changes nothing about the pack: it is not a source, and the jar is the same jar.
	if len(specs[0].Sources) != 1 {
		t.Errorf("trace-file was counted as a source: %v", specs[0].Sources)
	}

	absolute, err := parseRecipe(t, "output=out/a.jar\ntrace-file=/tmp/a.spans.json\nmodule=mod/a.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if got, want := absolute[0].TraceFile, "/tmp/a.spans.json"; got != want {
		t.Errorf("an absolute trace-file is %q, want %q untouched", got, want)
	}

	// The same destination twice is what a flag file concatenated from one action's command lines looks like; only two
	// *different* ones have no answer.
	repeated, err := parseRecipe(t, "output=out/a.jar\ntrace-file=out/a.spans.json\nmodule=mod/a.jar\n"+
		"output=out/b.jar\ntrace-file=out/a.spans.json\nmodule=mod/b.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if repeated[1].TraceFile != repeated[0].TraceFile {
		t.Error("the second group lost the destination")
	}

	// A recipe without one is the normal case: no flag, no line, no trace.
	none, err := parseRecipe(t, "output=out/a.jar\nmodule=mod/a.jar\n")
	if err != nil {
		t.Fatal(err)
	}
	if none[0].TraceFile != "" {
		t.Errorf("a recipe with no trace-file produced %q", none[0].TraceFile)
	}
}
