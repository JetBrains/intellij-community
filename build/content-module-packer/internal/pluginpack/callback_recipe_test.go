package pluginpack

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/json"
	"flag"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"

	"jetbrains.com/content-module-packer/internal/filemetadata"
)

var kotlinCallbackFixture = flag.String("kotlin-callback-fixture", "", "The declared original Kotlin callback fixture executable")
var kotlinCallbackPreparer = flag.String("kotlin-callback-preparer", "", "The declared full Kotlin preparation CLI executable")

// callbackFixtureProjection holds the operations of the fixture's plan file.
type callbackFixtureProjection struct {
	Operations []callbackFixtureOperation `json:"operations"`
}

type callbackFixtureOperation struct {
	ID            string          `json:"id"`
	Kind          string          `json:"kind"`
	Input         Reference       `json:"input"`
	Output        string          `json:"output"`
	Manifest      string          `json:"manifest"`
	LibraryLayout json.RawMessage `json:"libraryLayout"`
}

type callbackFixtureRun struct {
	preparer   string
	root       string
	catalogue  Catalogue
	recipe     Recipe
	serialized callbackFixtureProjection
	arguments  []string
}

func TestKotlinLibraryCallbackRecipeMatchesOriginalWriterAcrossExecutionRoots(test *testing.T) {
	fixture := prepareCallbackFixture(test)
	var owner struct {
		Filters []struct {
			Inputs []Reference `json:"inputs"`
		} `json:"filters"`
		Callbacks []struct {
			Prefix           string `json:"prefix"`
			TargetModuleName string `json:"targetModuleName"`
			Libraries        []struct {
				Roots []Reference `json:"roots"`
			} `json:"libraries"`
		} `json:"callbacks"`
		PatchOutputs []struct {
			Entries json.RawMessage `json:"entries"`
		} `json:"patchOutputs"`
	}
	if err := json.Unmarshal(fixture.serialized.Operations[0].LibraryLayout, &owner); err != nil {
		test.Fatal(err)
	}
	if len(owner.Filters) != 1 || !slices.Equal(owner.Filters[0].Inputs, []Reference{{Artifact: "first"}, {Artifact: "second"}}) {
		test.Fatalf("The original archive root order changed: %+v", owner.Filters)
	}
	if len(owner.Callbacks) != 3 || len(owner.PatchOutputs) != 2 {
		test.Fatalf("The repeated callbacks or dynamic outputs changed: %+v", owner)
	}
	for index, callback := range owner.Callbacks {
		prefix, module := "extensions/", "fixture.callback.main"
		if index == 0 {
			prefix, module = "other/", "fixture.callback.other"
		}
		if callback.Prefix != prefix || callback.TargetModuleName != module || len(callback.Libraries) != 1 ||
			!slices.Equal(callback.Libraries[0].Roots, []Reference{{Artifact: "library"}}) {
			test.Fatalf("The original callback binding changed: %+v", callback)
		}
	}
	for _, output := range owner.PatchOutputs {
		if string(output.Entries) != "null" {
			test.Fatal("The library fixture must discover its patch entries through the original callback")
		}
	}
	descriptor := readTestFile(test, callbackReferencePath(test, fixture.root, fixture.catalogue, Reference{Artifact: "descriptor"}))
	var original Recipe
	var originalCatalogue Catalogue
	for path, target := range map[string]any{
		"reference-prepared/recipe.json": &original, "reference-prepared/catalogue.json": &originalCatalogue,
	} {
		if err := ReadJSON(filepath.Join(fixture.root, path), target); err != nil {
			test.Fatal(err)
		}
	}
	if !reflect.DeepEqual(fixture.recipe, original) {
		test.Fatalf("The serialized recipe changed the original adapter sources:\n%+v\n%+v", fixture.recipe, original)
	}
	var preparedCatalogue Catalogue
	if err := ReadJSON(filepath.Join(fixture.root, "prepared/catalogue.json"), &preparedCatalogue); err != nil {
		test.Fatal(err)
	}
	for _, operation := range fixture.recipe.Operations {
		if operation.Kind != "jar" {
			test.Fatalf("Go must write each ordinary library jar: %+v", operation)
		}
		for _, source := range operation.Sources {
			for _, entry := range source.Entries {
				if entry.Input == nil {
					continue
				}
				actual := callbackReferencePath(test, fixture.root, preparedCatalogue, *entry.Input)
				expected := callbackReferencePath(test, fixture.root, originalCatalogue, *entry.Input)
				if !bytes.Equal(readTestFile(test, actual), readTestFile(test, expected)) {
					test.Fatalf("The serialized recipe changed the original patch or filtered entry: %s", entry.Name)
				}
			}
		}
	}
	wantSources := map[string][]string{
		"lib/merged.jar":        {"patched-other", "patched-main", "filtered-first", "filtered-second", "filtered-first"},
		"lib/module.jar":        {"patched-main", "filtered-first"},
		"lib/sibling/other.jar": {"patched-other", "filtered-first", "filtered-second"},
		"lib/single.jar":        {"filtered-first"},
	}
	for _, operation := range fixture.recipe.Operations {
		if operation.Destination == "lib/raw.jar" {
			continue
		}
		var sources []string
		for _, source := range operation.Sources {
			sources = append(sources, source.Prepared)
			if strings.HasPrefix(source.Prepared, "filtered-") {
				manifest := "drop"
				if operation.Destination == "lib/single.jar" {
					manifest = "keep"
				}
				if source.Manifest != manifest {
					test.Fatalf("The source manifest policy changed for %s: %+v", operation.Destination, source)
				}
				for _, entry := range source.Entries {
					if strings.HasPrefix(entry.Name, "drop/") {
						test.Fatalf("The original Java exclude retained %s", entry.Name)
					}
				}
			}
		}
		if !slices.Equal(sources, wantSources[operation.Destination]) {
			test.Fatalf("The callback or source order changed for %s: %v", operation.Destination, sources)
		}
	}
	root, _ := packCallbackFixture(test, fixture, 4)
	for _, name := range []string{"merged", "module", "sibling/other", "raw", "single"} {
		actualOrder, actualEntries := readArchive(test, filepath.Join(root, "plugin/lib", name+".jar"))
		expectedOrder, expectedEntries := readArchive(test, filepath.Join(root, "reference/lib", name+".jar"))
		if !slices.Equal(actualOrder, expectedOrder) || !reflect.DeepEqual(actualEntries, expectedEntries) {
			test.Fatalf("The original writer entry order or content changed for %s", name)
		}
	}
	_, merged := readArchive(test, filepath.Join(root, "plugin/lib/merged.jar"))
	_, single := readArchive(test, filepath.Join(root, "plugin/lib/single.jar"))
	if merged["META-INF/plugin.xml"] != string(descriptor) || merged["META-INF/MANIFEST.MF"] != "" || single["META-INF/MANIFEST.MF"] == "" {
		test.Fatal("The authoritative descriptor seed or source manifest behavior changed")
	}
}

func prepareCallbackFixture(test *testing.T) callbackFixtureRun {
	test.Helper()
	if *kotlinCallbackFixture == "" {
		test.Skip("Run the Bazel pluginpack_test target to include the Kotlin callback fixture")
	}
	if *pluginRemainderPacker == "" {
		test.Fatal("The callback fixture requires the declared Go remainder executable")
	}
	preparer := *kotlinCallbackPreparer
	if preparer == "" {
		test.Fatal("The library fixture requires its declared preparation CLI")
	}
	root, err := filepath.EvalSymlinks(test.TempDir())
	if err != nil {
		test.Fatal(err)
	}
	fixture := callbackFixtureRun{root: root, preparer: preparer}
	runCallbackFixtureCommand(test, fixture.root, *kotlinCallbackFixture, "--root="+fixture.root, "--fixture=library")
	if _, err := os.Lstat(filepath.Join(fixture.root, "prepared/recipe.json")); !os.IsNotExist(err) {
		test.Fatalf("Fixture capture must leave preparation to the plan file: %v", err)
	}
	if err := json.Unmarshal(readTestFile(test, filepath.Join(fixture.root, "projection.json")), &fixture.serialized); err != nil {
		test.Fatal(err)
	}
	if len(fixture.serialized.Operations) == 0 {
		test.Fatalf("Expected callback operations in the plan file: %+v", fixture.serialized)
	}
	var kinds []string
	var owner json.RawMessage
	for _, operation := range fixture.serialized.Operations {
		configuration := operation.LibraryLayout
		if operation.Kind != "library-layout-filter" && operation.Kind != "library-layout-patches" {
			test.Fatalf("Unexpected library operation: %+v", operation)
		}
		if operation.Output != "" || operation.Manifest != "keep" || operation.Input.Artifact == "" {
			test.Fatalf("The callback envelope changed: %+v", operation)
		}
		if owner == nil {
			owner = configuration
		} else if !bytes.Equal(owner, configuration) {
			test.Fatal("Each callback operation must contain the complete owner configuration")
		}
		kinds = append(kinds, operation.Kind)
	}
	var configuration struct {
		Version    int `json:"version"`
		Operations []struct {
			ID string `json:"id"`
		} `json:"operations"`
	}
	if err := json.Unmarshal(owner, &configuration); err != nil {
		test.Fatal(err)
	}
	if configuration.Version != 1 || len(configuration.Operations) != len(fixture.serialized.Operations) {
		test.Fatalf("The callback owner version or operation count changed: %+v", configuration)
	}
	for index, operation := range configuration.Operations {
		if operation.ID != fixture.serialized.Operations[index].ID {
			test.Fatal("The shared operations changed the original owner order")
		}
	}
	if err := ReadJSON(filepath.Join(fixture.root, "catalogue.json"), &fixture.catalogue); err != nil {
		test.Fatal(err)
	}
	// The kinds `dev_plugin_preparation` runs through the callback preparer; the preparer checks the flag against the recipe.
	callbackPreparation := slices.ContainsFunc(kinds, func(kind string) bool {
		return kind == "library-layout-filter" || kind == "library-layout-patches" || kind == "native-presigned"
	})
	fixture.arguments = []string{"--projection=projection.json", "--catalogue=catalogue.json",
		"--descriptor=descriptor.xml", "--plugin-directory=plugin", "--output-dir=prepared",
		"--execution-version=1", "--callback-preparation=" + strconv.FormatBool(callbackPreparation)}
	var projection struct {
		Preparations []struct {
			Inputs  []string `json:"inputs"`
			Outputs []string `json:"outputs"`
		} `json:"preparations"`
	}
	if err := json.Unmarshal(readTestFile(test, filepath.Join(fixture.root, "projection.json")), &projection); err != nil {
		test.Fatal(err)
	}
	var remainderInputs []string
	if err := json.Unmarshal(readTestFile(test, filepath.Join(fixture.root, "remainder-inputs.json")), &remainderInputs); err != nil {
		test.Fatal(err)
	}
	prepared := make(map[string]bool)
	for _, preparation := range projection.Preparations {
		for _, output := range preparation.Outputs {
			prepared[output] = true
		}
	}
	callbackRoots, remainderRoots := make(map[string]bool), make(map[string]bool)
	addInput := func(flag, id string, roots map[string]bool) {
		root := callbackReferencePath(test, fixture.root, fixture.catalogue, Reference{Artifact: id})
		if !roots[root] {
			roots[root] = true
			fixture.arguments = append(fixture.arguments, flag+"="+root)
		}
	}
	for _, preparation := range projection.Preparations {
		for _, input := range preparation.Inputs {
			if prepared[input] {
				continue
			}
			library := slices.IndexFunc(fixture.catalogue.Libraries, func(library Library) bool { return library.ID == input })
			if library < 0 {
				addInput("--callback-input", input, callbackRoots)
			} else {
				for _, reference := range fixture.catalogue.Libraries[library].Files {
					addInput("--callback-input", reference.Artifact, callbackRoots)
				}
			}
		}
	}
	for _, input := range remainderInputs {
		addInput("--remainder-input", input, remainderRoots)
	}
	runCallbackFixtureCommand(test, fixture.root, fixture.preparer, fixture.arguments...)
	if err := ReadJSON(filepath.Join(fixture.root, "prepared/recipe.json"), &fixture.recipe); err != nil {
		test.Fatal(err)
	}
	return fixture
}

func packCallbackFixture(test *testing.T, fixture callbackFixtureRun, classpathCount uint16) (string, []filemetadata.Entry) {
	test.Helper()
	root := test.TempDir()
	var catalogue Catalogue
	if err := ReadJSON(filepath.Join(fixture.root, "prepared/catalogue.json"), &catalogue); err != nil {
		test.Fatal(err)
	}
	for _, directory := range []string{"prepared", "reference"} {
		copyResourceFixtureTree(test, filepath.Join(fixture.root, directory), filepath.Join(root, directory))
	}
	writeTestFile(test, filepath.Join(root, "reference-classpath.txt"), readTestFile(test, filepath.Join(fixture.root, "reference-classpath.txt")))
	retained := 0
	for _, artifact := range catalogue.Artifacts {
		if filepath.IsAbs(artifact.Root) || strings.Contains(artifact.Root, fixture.root) || strings.HasPrefix(artifact.Root, "../") {
			test.Fatalf("The callback leaked a preparation root: %+v", artifact)
		}
		if strings.HasPrefix(artifact.Root, "prepared/") {
			continue
		}
		retained++
		if !slices.ContainsFunc(fixture.catalogue.Artifacts, func(input Artifact) bool { return input.ID == artifact.ID && input.Root == artifact.Root }) {
			test.Fatalf("The ordinary jar lost its original input binding: %+v", artifact)
		}
		copyResourceFixtureTree(test, filepath.Join(fixture.root, artifact.Root), filepath.Join(root, artifact.Root))
	}
	if retained != 1 || len(fixture.catalogue.Artifacts) <= retained {
		test.Fatalf("Expected one retained raw jar and removed preparation inputs: retained=%d, original=%d", retained, len(fixture.catalogue.Artifacts))
	}
	for _, artifact := range fixture.catalogue.Artifacts {
		if slices.ContainsFunc(catalogue.Artifacts, func(input Artifact) bool { return input.ID == artifact.ID }) {
			continue
		}
		if _, err := os.Lstat(filepath.Join(root, artifact.Root)); !os.IsNotExist(err) {
			test.Fatalf("A preparation-only input reached Go: %+v: %v", artifact, err)
		}
	}
	if err := os.RemoveAll(fixture.root); err != nil {
		test.Fatal(err)
	}
	classpath := readTestFile(test, filepath.Join(root, "prepared/plugin-classpath.txt"))
	if !bytes.Equal(classpath, readTestFile(test, filepath.Join(root, "reference-classpath.txt"))) ||
		len(classpath) < 2 || binary.BigEndian.Uint16(classpath[:2]) != classpathCount {
		test.Fatalf("The original classpath changed: %x", classpath)
	}
	runCallbackFixtureCommand(test, root, *pluginRemainderPacker, "--recipe=prepared/recipe.json", "--catalogue=prepared/catalogue.json",
		"--output-dir=plugin", "--inventory=inventory.json")
	inventory, err := filemetadata.Read(filepath.Join(root, "inventory.json"))
	if err != nil {
		test.Fatal(err)
	}
	actual, err := filemetadata.Inventory(filepath.Join(root, "plugin"))
	if err != nil {
		test.Fatal(err)
	}
	expected, err := filemetadata.Inventory(filepath.Join(root, "reference"))
	if err != nil || !reflect.DeepEqual(actual, expected) {
		test.Fatalf("The original bytes, modes, directories, or links changed:\n%+v\n%+v\n%v", actual, expected, err)
	}
	for _, entry := range inventory {
		if !slices.Contains(actual, entry) {
			test.Fatalf("The Go inventory differs from the actual output: %+v", entry)
		}
	}
	for _, entry := range actual {
		if entry.Type == "file" && !bytes.Equal(readTestFile(test, filepath.Join(root, "plugin", entry.RelativePath)),
			readTestFile(test, filepath.Join(root, "reference", entry.RelativePath))) {
			test.Fatalf("The original writer bytes changed for %s", entry.RelativePath)
		}
	}
	return root, actual
}

func callbackReferencePath(test *testing.T, root string, catalogue Catalogue, reference Reference) string {
	test.Helper()
	for _, artifact := range catalogue.Artifacts {
		if artifact.ID == reference.Artifact {
			path := artifact.Root
			if !filepath.IsAbs(path) {
				path = filepath.Join(root, path)
			}
			return filepath.Join(path, filepath.FromSlash(reference.Path))
		}
	}
	test.Fatalf("The fixture reference has no declared input: %+v", reference)
	return ""
}

func runCallbackFixtureCommand(test *testing.T, root, executable string, arguments ...string) {
	test.Helper()
	if output, err := callbackFixtureCommand(test, root, executable, arguments...); err != nil {
		test.Fatalf("%s failed: %v\n%s", executable, err, output)
	}
}

func callbackFixtureCommand(test *testing.T, root, executable string, arguments ...string) ([]byte, error) {
	test.Helper()
	if !filepath.IsAbs(executable) {
		executable = filepath.Join(os.Getenv("TEST_SRCDIR"), filepath.FromSlash(executable))
	}
	commandContext, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	command := exec.CommandContext(commandContext, executable, arguments...)
	command.Dir = root
	return command.CombinedOutput()
}
