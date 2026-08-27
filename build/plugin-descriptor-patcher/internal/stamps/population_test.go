// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package stamps_test

import (
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"

	"jetbrains.com/plugin-descriptor-patcher/internal/descriptorxml"
	"jetbrains.com/plugin-descriptor-patcher/internal/stamps"
)

// The population gate: every recorded descriptor of a real product, one case each, compared by bytes.
//
// The curated cases in `stamps_test.go` and `roundtrip_test.go` state one construct each. This one states nothing and
// proves the whole population, which is the only way to find the construct nobody thought of.
//
// ### The fixture is the artifact a dev assembly writes
//
// `IJ_DESCRIPTOR_CASES` names a `*.patched-descriptors.json`, or a directory of them.
// `DevDistPatchedDescriptors` writes one per fragment, and it holds the text of every stage of the patch. So a change
// to `JDOMUtil` or to `doPatchPluginXml` reaches this gate through the assembly that produced the file, and no
// throwaway probe stands between them. `artifact_test.go` is the reader.
//
// Refresh the fixture with the assembly itself:
//
//	./bazel.cmd build //build:idea_air_dist \
//	  --@community//platform/build-scripts/bazel-rules:dev_dist_patched_descriptors \
//	  --output_groups=+dev_dist_patched_descriptors
//	cp out/bazel-bin/build/*.patched-descriptors.json <dir>/
//
// A later Bazel run with the flag off prunes the files from the symlink farm, so copy them out at once.
//
// ### The three arms
//
// | arm | what it compares | population |
// |---|---|---|
// | `rawTextPatcher` -> `reserialized` | `descriptorxml.Read` and `Write` against the recorded round trip | every plugin |
// | `reserialized` -> `stamps` | `stamps.Apply` against the recorded stamps text | every plugin |
// | `stamps` against `patched` | the stamps text a real assembly put in the plugin's main jar | class (a) only |
//
// The round trip starts at `rawTextPatcher` and not at `source`, because that is the text the platform hands to
// `JDOMUtil.load`. The two are the same text for every plugin whose layout states no raw lambda.
//
// The third arm is the one that is not a mirror. For a class (a) plugin the patch ends at the stamps stage, so
// `patched` **is** the stamps text, and the comparison is against bytes a real assembly wrote.
const casesVariable = "IJ_DESCRIPTOR_CASES"

// defaultRequest is what a fixture with no `request.txt` runs with.
//
// **A stamp scalar is not in the artifact.** `DevDistPatchedDescriptors.record` receives the plugin's identity and its
// stage texts, and no scalar of `PluginDescriptorPatchRequest`. So the scalars are stated beside the artifact, and a
// wrong one is loud rather than silent: it moves the `<version>` or the `<idea-version>` of every recorded plugin, and
// the stamps arm then reports 0 identical of 163.
//
// The values below are arbitrary and shared. A recorded artifact needs the assembly's own scalars, which
// `build/dev_dist_plugin_descriptors.bzl` and `@community//:build.txt` state. For `//build:idea_air_dist` today:
//
//	version=263.20260101.0  sinceBuild=263.SNAPSHOT  untilBuild=263.SNAPSHOT
//	releaseDate=20260101    releaseVersion=2026300   isEap=true
//
// ### A scalar is per plugin, and `<version>` is the one that shows it
//
// A `key@<main module>=<value>` line states one plugin's deviation. Three plugins of this product need one: the
// version of `intellij.jcef.plugin` and of `intellij.platform.daemon.plugin` carries the OS and the architecture, and
// the version of `intellij.kotlin.plugin` carries `-IJ`. A suffix is a per-layout value, so it is code and not data,
// exactly as `rawPluginXmlPatcher` is. Those three plugins are the class (c) population, which this binary refuses.
var defaultRequest = stamps.Request{
	Version:        "1.0.0",
	SinceBuild:     "263",
	UntilBuild:     "263.*",
	ReleaseDate:    "20260101",
	ReleaseVersion: "2026300",
	IsEap:          true,
}

// requestSet is the shared request and the per-plugin deviations of it.
type requestSet struct {
	shared  stamps.Request
	perName map[string]stamps.Request
}

// of is the request one plugin runs with.
func (r *requestSet) of(mainModule string) stamps.Request {
	if own, stated := r.perName[mainModule]; stated {
		return own
	}
	return r.shared
}

// readRequest reads `request.txt` beside the artifact, when there is one. A key the file states and this reader does
// not know fails the run, so a typed scalar is never silently the default.
func readRequest(t *testing.T, root string) requestSet {
	t.Helper()
	if info, err := os.Stat(root); err == nil && !info.IsDir() {
		root = filepath.Dir(root)
	}
	set := requestSet{shared: defaultRequest, perName: map[string]stamps.Request{}}
	source, err := os.ReadFile(filepath.Join(root, "request.txt"))
	if err != nil {
		if os.IsNotExist(err) {
			return set
		}
		t.Fatal(err)
	}
	// The file states a deviation only, so a scalar it does not name keeps its default. A shared line has to come
	// ahead of the per-plugin lines that deviate from it, and the reader states so rather than sorting the file.
	for _, line := range strings.Split(string(source), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, value, found := strings.Cut(line, "=")
		if !found {
			t.Fatalf("request.txt: %q is not a key=value line", line)
		}
		if name, mainModule, own := strings.Cut(key, "@"); own {
			deviation, stated := set.perName[mainModule]
			if !stated {
				deviation = set.shared
			}
			apply(t, &deviation, name, value, line)
			set.perName[mainModule] = deviation
			continue
		}
		apply(t, &set.shared, key, value, line)
	}
	return set
}

// apply sets one scalar of one request.
func apply(t *testing.T, request *stamps.Request, key string, value string, line string) {
	t.Helper()
	switch key {
	case "version":
		request.Version = value
	case "sinceBuild":
		request.SinceBuild = value
	case "untilBuild":
		request.UntilBuild = value
	case "releaseDate":
		request.ReleaseDate = value
	case "releaseVersion":
		request.ReleaseVersion = value
	case "toPublish":
		request.ToPublish = value == "true"
	case "retainProductDescriptor":
		request.RetainProductDescriptorForBundledPlugin = value == "true"
	case "isEap":
		request.IsEap = value == "true"
	default:
		t.Fatalf("request.txt: %q is not a scalar this gate knows", line)
	}
}

// arm counts one comparison. Absent counts a case the artifact cannot answer, which is a failure of the run: a case
// that runs against no expectation passes against nothing.
type arm struct {
	name      string
	same      int
	different int
	absent    int
}

func (a *arm) compare(t *testing.T, got string, want string, states bool) {
	t.Helper()
	if !states {
		a.absent++
		t.Errorf("%s: the artifact holds no expectation for this stage", a.name)
		return
	}
	if got == want {
		a.same++
		return
	}
	a.different++
	t.Errorf("%s differs: %s", a.name, firstDifference(got, want))
}

func (a *arm) report(t *testing.T, cases int) {
	t.Helper()
	t.Logf("%-28s %3d identical, %d differing, %d absent of %d", a.name, a.same, a.different, a.absent, cases)
}

func TestThePopulationRoundTripsAndStampsByteForByte(t *testing.T) {
	root := os.Getenv(casesVariable)
	if root == "" {
		t.Skipf("set %s to a *%s, or to a directory of them", casesVariable, artifactSuffix)
	}
	requests := readRequest(t, root)

	// One case per record, named by the plugin. A plugin an OS-specific layout builds twice holds two records, and the
	// emitter keeps both when they disagree, so the name takes a suffix rather than dropping one of them.
	type population struct {
		name   string
		plugin *artifactPlugin
	}
	var cases []population
	seen := map[string]int{}
	for _, report := range readArtifact(t, root) {
		for i := range report.Plugins {
			plugin := &report.Plugins[i]
			seen[plugin.MainModule]++
			name := plugin.MainModule
			if seen[name] > 1 {
				name = plugin.MainModule + "#" + itoa(seen[plugin.MainModule])
			}
			cases = append(cases, population{name: name, plugin: plugin})
		}
	}
	if len(cases) == 0 {
		t.Fatalf("%s holds no plugin record", root)
	}
	sort.Slice(cases, func(i int, j int) bool { return cases[i].name < cases[j].name })

	roundTrip := &arm{name: "rawTextPatcher -> reserialized"}
	stamped := &arm{name: "reserialized -> stamps"}
	// The class (a) arm is not a mirror: `patched` is the text a real assembly wrote into the plugin's main jar.
	assembled := &arm{name: "stamps against patched"}
	for _, it := range cases {
		t.Run(it.name, func(t *testing.T) {
			source, states := it.plugin.stageText(stageRawTextPatcher)
			if !states {
				t.Fatal("the artifact holds no `rawTextPatcher` text, so this record states no input")
			}
			element, err := descriptorxml.Read(source)
			if err != nil {
				t.Fatalf("read: %v", err)
			}

			want, states := it.plugin.stageText(stageReserialized)
			roundTrip.compare(t, descriptorxml.Write(element), want, states)

			stamps.Apply(element, requests.of(it.plugin.MainModule))
			patched := descriptorxml.Write(element)
			want, states = it.plugin.stageText(stageStamps)
			stamped.compare(t, patched, want, states)

			if it.plugin.isStampsOnly() {
				// `patched` is the stamps text of a class (a) plugin, and the record states it directly.
				if want != it.plugin.Patched {
					t.Errorf("class (a) states a `stamps` text that is not `patched`, so the artifact disagrees "+
						"with itself: %s", firstDifference(want, it.plugin.Patched))
				}
				assembled.compare(t, patched, it.plugin.Patched, true)
			}
		})
	}
	roundTrip.report(t, len(cases))
	stamped.report(t, len(cases))
	assembled.report(t, assembled.same+assembled.different+assembled.absent)
}

// firstDifference names where two texts part, with the line and both sides of it. A whole diff of a 40 KB descriptor
// tells a reader less than the one line that moved.
func firstDifference(got string, want string) string {
	limit := min(len(got), len(want))
	at := limit
	for i := 0; i < limit; i++ {
		if got[i] != want[i] {
			at = i
			break
		}
	}
	line := 1 + strings.Count(got[:at], "\n")
	return "at offset " + itoa(at) + ", line " + itoa(line) + ":\n  got : " + lineAt(got, at) + "\n  want: " + lineAt(want, at)
}

func lineAt(text string, at int) string {
	if at > len(text) {
		return "<end of text>"
	}
	start := strings.LastIndexByte(text[:at], '\n') + 1
	end := strings.IndexByte(text[start:], '\n')
	if end < 0 {
		return text[start:]
	}
	return text[start : start+end]
}

func itoa(value int) string {
	if value == 0 {
		return "0"
	}
	var digits []byte
	for value > 0 {
		digits = append([]byte{byte('0' + value%10)}, digits...)
		value /= 10
	}
	return string(digits)
}
