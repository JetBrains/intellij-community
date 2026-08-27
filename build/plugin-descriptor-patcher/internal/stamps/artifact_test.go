// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package stamps_test

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sort"
	"testing"
)

// The reader of `*.patched-descriptors.json`, which `DevDistPatchedDescriptors` writes for a dev-distribution
// assembly. `population_test.go` takes its cases from it.
//
// ### Why this package holds a reader of its own
//
// `build/internal/content/descriptor.go` reads the same artifact, and `PluginDescriptor.StageText` states the same
// four rules. That file is in the ultimate Go module `jetbrains.com/ij-build`, and this one is in the community module
// `jetbrains.com/plugin-descriptor-patcher`. A community module cannot depend on an ultimate one, so the rules are
// stated twice on purpose. Both statements are tested: [TestStageTextCarriesForward] here, and
// `TestDescriptorStageTextCarriesForward` there.
//
// The reader is total rather than validating. A field it does not know is unread, and a malformed record fails the one
// case it belongs to instead of taking the other 162 with it.

// artifactStep is one row of a plugin's `steps`.
type artifactStep struct {
	Stage string `json:"stage"`
	Bytes int    `json:"bytes"`
	// Changed is absent on `source`, and Text is absent wherever another field holds the text.
	Changed *bool   `json:"changed"`
	Text    *string `json:"text"`
}

// artifactPlugin is one plugin's record. The fields this gate does not read are left out.
type artifactPlugin struct {
	MainModule string `json:"mainModule"`
	// Origin is absent for a descriptor the fragment patched itself, which is what every record of the older schema is.
	// A record of [originProduced] holds no step, because the fragment ran no stage of the patch: it read the file a
	// `dev_dist_plugin_descriptor` action wrote.
	Origin  string         `json:"origin"`
	Steps   []artifactStep `json:"steps"`
	Source  string         `json:"source"`
	Patched string         `json:"patched"`
}

// The `origin` values of `DevDistDescriptorOrigin`. An absent value is [originComputed].
const (
	originComputed = "computed"
	originProduced = "produced"
)

type artifactReport struct {
	Fragment string           `json:"fragment"`
	Plugins  []artifactPlugin `json:"plugins"`
}

// The stage names of `DevDistDescriptorStage`, in the order the steps run.
const (
	stageSource         = "source"
	stageRawTextPatcher = "rawTextPatcher"
	stageReserialized   = "reserialized"
	stageStamps         = "stamps"
	stageIncludes       = "includes"
	stageContentModules = "contentModules"
	stageTextPatcher    = "textPatcher"
)

// artifactSuffix is what a fragment names its descriptor report.
const artifactSuffix = ".patched-descriptors.json"

// readArtifact reads one `*.patched-descriptors.json`, or every one a directory holds.
func readArtifact(t *testing.T, path string) []artifactReport {
	t.Helper()
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	files := []string{path}
	if info.IsDir() {
		files, err = filepath.Glob(filepath.Join(path, "*"+artifactSuffix))
		if err != nil {
			t.Fatal(err)
		}
		if len(files) == 0 {
			t.Fatalf("%s holds no *%s. A build with the flag off writes none, so re-run the assembly with "+
				"--@community//platform/build-scripts/bazel-rules:dev_dist_patched_descriptors "+
				"--output_groups=+dev_dist_patched_descriptors", path, artifactSuffix)
		}
	}
	sort.Strings(files)
	reports := make([]artifactReport, 0, len(files))
	for _, file := range files {
		source, err := os.ReadFile(file)
		if err != nil {
			t.Fatal(err)
		}
		var report artifactReport
		if err := json.Unmarshal(source, &report); err != nil {
			t.Fatalf("%s: %v", file, err)
		}
		if report.Fragment == "" {
			t.Fatalf("%s states no fragment, so it is not a descriptor report", file)
		}
		reports = append(reports, report)
	}
	return reports
}

// stageText is the descriptor text one stage produced, and whether the artifact holds it.
//
// Four sources answer it, and the emitter's rule is that exactly one of them holds every stage's text:
//
//   - the record's `source` field, for the first stage;
//   - the step's own `text`, which the emitter states where nothing else holds the text;
//   - the text of the step ahead, for a step that changed nothing;
//   - the record's `patched` field, for the last step that changed the text.
//
// A `false` means the artifact does not hold that stage's text. An artifact of the older schema stated a byte count
// and no text, so it answers the source, the unchanged stages and the last changed stage, and nothing between them.
func (p *artifactPlugin) stageText(stage string) (string, bool) {
	index := -1
	for i := range p.Steps {
		if p.Steps[i].Stage == stage {
			index = i
			break
		}
	}
	if index < 0 {
		return "", false
	}
	for i := index; i > 0; i-- {
		step := &p.Steps[i]
		if step.Text != nil {
			return *step.Text, true
		}
		if step.Changed == nil {
			// no verdict, so neither rule below applies
			return "", false
		}
		if *step.Changed {
			if p.changedAfter(i) {
				// an older artifact, which stated no text for a stage a later stage changed again
				return "", false
			}
			return p.Patched, true
		}
		// the step changed nothing, so the step ahead of it holds this text
	}
	return p.Source, true
}

func (p *artifactPlugin) changedAfter(index int) bool {
	for i := index + 1; i < len(p.Steps); i++ {
		if p.Steps[i].Changed != nil && *p.Steps[i].Changed {
			return true
		}
	}
	return false
}

// changedAt reports whether the record states that this stage changed the text.
func (p *artifactPlugin) changedAt(stage string) bool {
	for i := range p.Steps {
		if p.Steps[i].Stage == stage {
			return p.Steps[i].Changed != nil && *p.Steps[i].Changed
		}
	}
	return false
}

// isStampsOnly is class (a) of `dev-dist-measurements.md`: no per-layout lambda and no structural stage changed the
// text, so `patched` **is** the text the stamps stage produced.
func (p *artifactPlugin) isStampsOnly() bool {
	for _, stage := range []string{stageRawTextPatcher, stageTextPatcher, stageIncludes, stageContentModules} {
		if p.changedAt(stage) {
			return false
		}
	}
	return true
}

// The fixtures below are hand-written. A real report is megabytes of one product's descriptors, and a corpus in the
// tree would test the corpus rather than the reader.

func fixtureStep(stage string, bytes int, changed *bool, text *string) artifactStep {
	return artifactStep{Stage: stage, Bytes: bytes, Changed: changed, Text: text}
}

func boolean(value bool) *bool { return &value }

func text(value string) *string { return &value }

func TestStageTextCarriesForward(t *testing.T) {
	plugin := artifactPlugin{
		MainModule: "intellij.example",
		Source:     "<idea-plugin/>",
		Patched:    "<idea-plugin><version>1</version></idea-plugin>",
		Steps: []artifactStep{
			fixtureStep(stageSource, 14, nil, nil),
			// changed nothing, so the step ahead holds the text
			fixtureStep(stageRawTextPatcher, 14, boolean(false), nil),
			// changed the text, and no other field holds it
			fixtureStep(stageReserialized, 15, boolean(true), text("<idea-plugin />")),
			// the last step that changed the text, so `patched` holds it
			fixtureStep(stageStamps, 47, boolean(true), nil),
			fixtureStep(stageIncludes, 47, boolean(false), nil),
			fixtureStep(stageContentModules, 47, boolean(false), nil),
			fixtureStep(stageTextPatcher, 47, boolean(false), nil),
		},
	}

	for i, want := range []string{
		plugin.Source, plugin.Source, "<idea-plugin />", plugin.Patched, plugin.Patched, plugin.Patched, plugin.Patched,
	} {
		step := plugin.Steps[i]
		got, states := plugin.stageText(step.Stage)
		if !states {
			t.Errorf("%s: the artifact must hold this text", step.Stage)
			continue
		}
		if got != want {
			t.Errorf("%s: got %q, want %q", step.Stage, got, want)
		}
		// the recovered text has to be the size the record states for that stage
		if len(got) != step.Bytes {
			t.Errorf("%s: the recovered text is %d bytes and the record states %d", step.Stage, len(got), step.Bytes)
		}
	}
	if _, states := plugin.stageText("aStageNoRecordHolds"); states {
		t.Error("a stage the record does not hold must answer absent")
	}
	if !plugin.isStampsOnly() {
		t.Error("no lambda and no structural stage changed this text, so it is class (a)")
	}
}

// An artifact of the older schema stated no text at all. A stage it cannot answer must read as absent, and never as an
// empty descriptor: that is the difference between a skipped case and a case that passes against nothing.
func TestStageTextIsAbsentInAnOlderArtifact(t *testing.T) {
	plugin := artifactPlugin{
		MainModule: "intellij.example",
		Source:     "<idea-plugin/>",
		Patched:    "<idea-plugin><version>1</version></idea-plugin>",
		Steps: []artifactStep{
			fixtureStep(stageSource, 14, nil, nil),
			fixtureStep(stageRawTextPatcher, 14, boolean(false), nil),
			fixtureStep(stageReserialized, 15, boolean(true), nil),
			fixtureStep(stageStamps, 47, boolean(true), nil),
			fixtureStep(stageIncludes, 47, boolean(false), nil),
			fixtureStep(stageContentModules, 47, boolean(false), nil),
			fixtureStep(stageTextPatcher, 47, boolean(false), nil),
		},
	}

	if got, states := plugin.stageText(stageReserialized); states {
		t.Errorf("`reserialized` must read as absent, got %q", got)
	}
	if got, states := plugin.stageText(stageStamps); !states || got != plugin.Patched {
		t.Errorf("`stamps` is the last changed step, so it is `patched`: got %q and %t", got, states)
	}
	if got, states := plugin.stageText(stageRawTextPatcher); !states || got != plugin.Source {
		t.Errorf("a step that changed nothing carries the source: got %q and %t", got, states)
	}
}

// A step that changed the text and states one is answered from the step, whatever the steps after it did.
func TestAStatedTextOutranksTheCarryForward(t *testing.T) {
	plugin := artifactPlugin{
		Source:  "<idea-plugin/>",
		Patched: "<idea-plugin><version>1</version></idea-plugin>",
		Steps: []artifactStep{
			fixtureStep(stageSource, 14, nil, nil),
			fixtureStep(stageRawTextPatcher, 16, boolean(true), text("<idea-plugin  />")),
			fixtureStep(stageReserialized, 15, boolean(true), text("<idea-plugin />")),
			fixtureStep(stageStamps, 47, boolean(true), nil),
		},
	}

	for _, it := range []struct {
		stage string
		want  string
	}{
		{stageRawTextPatcher, "<idea-plugin  />"},
		{stageReserialized, "<idea-plugin />"},
		{stageStamps, plugin.Patched},
	} {
		got, states := plugin.stageText(it.stage)
		if !states || got != it.want {
			t.Errorf("%s: got %q and %t, want %q", it.stage, got, states, it.want)
		}
	}
	if plugin.isStampsOnly() {
		t.Error("the raw text patcher changed the text, so this is class (c)")
	}
}
