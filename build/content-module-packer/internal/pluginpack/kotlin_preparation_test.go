package pluginpack

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"hash/crc32"
	"io"
	"io/fs"
	"maps"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"slices"
	"strings"
	"testing"
	"time"
	"unicode/utf16"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/xxh3"
)

var kotlinPreparer = flag.String("kotlin-preparer", "", "The declared Kotlin preparation executable")
var pluginRemainderPacker = flag.String("plugin-remainder-packer", "", "The declared Go plugin remainder executable")

const kotlinProjection = `{
  "version": 1,
  "plugin": "demo",
  "variant": "linux",
  "layoutSignature": "6yln12fn7aygix1ajef6zvpwn",
  "assets": [
    {"destination": "lib/content.jar", "inputs": ["content"], "recipe": {"sources": [{"input": "content", "kind": "module", "filter": "module-v1"}]}},
    {"destination": "lib/demo.jar", "inputs": ["main"], "recipe": {"sources": [{"input": "main", "kind": "module", "filter": "module-v1"}]}},
    {"destination": "lib/nested/custom.jar", "inputs": ["main"], "recipe": {"sources": [{"input": "main", "kind": "module", "filter": "module-v1"}]}},
    {"destination": "bin/tool", "inputs": ["native"], "mode": 493},
    {"destination": "bin/current", "inputs": [], "symlinkTarget": "./tool"}
  ],
  "reusableArtifacts": [{"label": "independent-content", "recipe": {"sources": [{"input": "content", "kind": "module", "filter": "module-v1"}]}}]
}`

const kotlinFilteredProjection = `{
  "plugin": "filtered-plugin", "variant": "linux",
  "layoutSignature": "c6yx54cclyu91esyjhzd6z0xe",
  "assets": [{"destination": "lib/main.jar", "inputs": ["filtered"], "recipe": {
    "sources": [{"input": "filtered", "kind": "prepared", "filter": "prepared"}], "writer": {"manifest": "drop"}
  }}],
  "preparations": [{"id": "filter", "inputs": ["raw"], "outputs": ["filtered"],
    "modelSignature": "4j4kth710fglswfsh1vosdrg4"}],
  "operations": [{"id": "filter", "input": {"artifact": "raw"}, "output": "filtered", "manifest": "drop", "excludes": ["drop/**"]}]
}`

// TestKotlinModuleFilterRecipeSurvivesDifferentExecutionRoots runs the Kotlin preparer on a module-filter plan. The
// preparer emits the archive source with the Java-glob excludes and lists the module jar as a remainder input. The Go
// packer then runs in another execution root, where the relative roots of the catalogue must resolve.
func TestKotlinModuleFilterRecipeSurvivesDifferentExecutionRoots(test *testing.T) {
	if *kotlinPreparer == "" {
		test.Skip("Run the Bazel pluginpack_test target to include the declared Kotlin preparer")
	}
	executable := *kotlinPreparer
	if !filepath.IsAbs(executable) {
		executable = filepath.Join(os.Getenv("TEST_SRCDIR"), filepath.FromSlash(executable))
	}
	for _, empty := range []bool{false, true} {
		name := "entries"
		if empty {
			name = "empty"
		}
		test.Run(name, func(test *testing.T) {
			preparationRoot := test.TempDir()
			packingRoot := test.TempDir()
			preparedPath := filepath.Join("out", "prepared-plugin")
			prepared := filepath.Join(preparationRoot, preparedPath)
			moduleJar := filepath.Join("raw", "module.jar")
			writeTestFile(test, filepath.Join(preparationRoot, "projection.json"), []byte(kotlinFilteredProjection))
			writeTestFile(test, filepath.Join(preparationRoot, "descriptor.xml"), []byte("<idea-plugin><id>filtered-plugin</id></idea-plugin>"))
			catalogue := Catalogue{Version: Version, Artifacts: []Artifact{{ID: "raw", Kind: "file", Root: "raw/module.jar"}}}
			data, err := json.Marshal(catalogue)
			if err != nil {
				test.Fatal(err)
			}
			writeTestFile(test, filepath.Join(preparationRoot, "catalogue.json"), data)
			entries := []testEntry{{"drop/Ignore.class", "excluded"}}
			if !empty {
				entries = append(entries, testEntry{"keep/Service.class", "retained"})
			}
			archiveFile(test, filepath.Join(preparationRoot, moduleJar), entries...)
			commandContext, cancel := context.WithTimeout(context.Background(), 90*time.Second)
			defer cancel()
			command := exec.CommandContext(commandContext, executable,
				"--projection=projection.json", "--catalogue=catalogue.json",
				"--descriptor=descriptor.xml", "--plugin-directory=plugins/filtered-plugin", "--output-dir="+preparedPath,
				"--callback-preparation=false", "--remainder-input=raw/module.jar",
			)
			command.Dir = preparationRoot
			if output, err := command.CombinedOutput(); err != nil {
				test.Fatalf("Kotlin preparation failed: %v\n%s", err, output)
			}
			if err := ReadJSON(filepath.Join(prepared, "catalogue.json"), &catalogue); err != nil {
				test.Fatal(err)
			}
			if len(catalogue.Artifacts) != 1 || catalogue.Artifacts[0].ID != "raw" || catalogue.Artifacts[0].Tree != nil ||
				filepath.IsAbs(catalogue.Artifacts[0].Root) {
				test.Fatalf("the module jar must be the one remainder input with its relative root: %+v", catalogue.Artifacts)
			}
			var recipe Recipe
			if err := ReadJSON(filepath.Join(prepared, "recipe.json"), &recipe); err != nil {
				test.Fatal(err)
			}
			want := []Operation{{Kind: "jar", Destination: "lib/main.jar", Mode: 0o644, Options: &JarOptions{Directories: "none"},
				Sources: []Source{{Kind: "archive", Input: &Reference{Artifact: "raw"}, Filter: "module", Manifest: "drop", Excludes: []string{"drop/**"}}}}}
			if actual, expected := canonicalOperations(test, recipe.Operations), canonicalOperations(test, want); actual != expected {
				test.Fatalf("Kotlin did not emit the Go archive source:\n%s\n%s", actual, expected)
			}
			if leftovers, err := os.ReadDir(filepath.Join(prepared, "prepared")); err != nil || len(leftovers) != 0 {
				test.Fatalf("a Go-executed operation must leave no prepared directory: %v, %v", leftovers, err)
			}
			// The packer runs in another execution root: only the module jar under its relative root reaches it.
			writeTestFile(test, filepath.Join(packingRoot, moduleJar), readTestFile(test, filepath.Join(preparationRoot, moduleJar)))
			if err := os.RemoveAll(preparationRoot); err != nil {
				test.Fatal(err)
			}
			test.Chdir(packingRoot)
			output, inventory := writeExecution(test, recipe, catalogue)
			_, contents := readArchive(test, filepath.Join(output, "lib/main.jar"))
			if _, present := contents["drop/Ignore.class"]; present {
				test.Fatal("Java-excluded entry reached the Go writer")
			}
			if content, present := contents["keep/Service.class"]; present == empty || (!empty && content != "retained") {
				test.Fatalf("prepared content differs: %v", contents)
			}
			if len(inventory) != 1 || inventory[0].RelativePath != "lib/main.jar" {
				test.Fatalf("unexpected remainder inventory: %+v", inventory)
			}
		})
	}
}

func TestKotlinProjectionPreparationAndGoBatch(test *testing.T) {
	if *kotlinPreparer == "" {
		test.Skip("Run the Bazel pluginpack_test target to include the declared Kotlin preparer")
	}
	executable := *kotlinPreparer
	if !filepath.IsAbs(executable) {
		executable = filepath.Join(os.Getenv("TEST_SRCDIR"), filepath.FromSlash(executable))
	}
	root := test.TempDir()
	prepared := filepath.Join(root, "preparation")
	module := filepath.Join(root, "raw-main.jar")
	native := filepath.Join(root, "native-input")
	writeTestFile(test, filepath.Join(root, "projection.json"), []byte(kotlinProjection))
	writeTestFile(test, filepath.Join(root, "descriptor.xml"), []byte("<idea-plugin><id>demo</id></idea-plugin>"))
	catalogue := Catalogue{Version: Version, Artifacts: []Artifact{
		{ID: "main", Kind: "file", Root: module},
		{ID: "native", Kind: "file", Root: native},
	}}
	data, err := json.Marshal(catalogue)
	if err != nil {
		test.Fatal(err)
	}
	writeTestFile(test, filepath.Join(root, "catalogue.json"), data)
	context, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	command := exec.CommandContext(context, executable,
		"--projection="+filepath.Join(root, "projection.json"),
		"--catalogue="+filepath.Join(root, "catalogue.json"),
		"--descriptor="+filepath.Join(root, "descriptor.xml"),
		"--plugin-directory="+filepath.Join(root, "demo"),
		"--output-dir="+prepared,
		"--remainder-input="+module,
		"--remainder-input="+native,
	)
	if output, err := command.CombinedOutput(); err != nil {
		test.Fatalf("Kotlin preparation failed: %v\n%s", err, output)
	}
	for _, source := range []string{module, native} {
		if _, err := os.Stat(source); !os.IsNotExist(err) {
			test.Fatalf("preparation unexpectedly materialized raw input %s: %v", source, err)
		}
	}
	archiveFile(test, module,
		testEntry{"com/", ""}, testEntry{"com/example/", ""},
		testEntry{"com/example/Service.class", "class bytes"},
		testEntry{"com/example/nested/Inner.class", "inner bytes"},
		testEntry{"messages/Bundle.properties", "key=value"},
		testEntry{"icon-robots.txt", "dropped: a build-time input"},
		testEntry{"com/example/icon-robots.txt", "dropped: same, nested"},
		testEntry{".unmodified", "dropped: compilation cache leftover"},
		testEntry{"classpath.index", "dropped: compilation cache leftover"},
		testEntry{"module-info.class", "dropped"},
		testEntry{"__index__", "dropped: a stale index is never inherited"},
		testEntry{"META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"},
	)
	writeTestFile(test, native, []byte("native bytes"))
	var recipe Recipe
	if err := ReadJSON(filepath.Join(prepared, "recipe.json"), &recipe); err != nil {
		test.Fatal(err)
	}
	if err := ReadJSON(filepath.Join(prepared, "catalogue.json"), &catalogue); err != nil {
		test.Fatal(err)
	}
	output, inventory := writeExecution(test, recipe, catalogue)
	for _, name := range []string{"lib/demo.jar", "lib/nested/custom.jar"} {
		digest := sha256.Sum256(readTestFile(test, filepath.Join(output, name)))
		if actual := hex.EncodeToString(digest[:]); actual != "622c52ad7098cee4a36c94665b8759f456f1d7cede38ce6872d28007536227eb" {
			test.Fatalf("%s differs from the Kotlin single-source golden: %s", name, actual)
		}
	}
	if _, err := os.Lstat(filepath.Join(output, "lib/content.jar")); !os.IsNotExist(err) {
		test.Fatalf("independent jar entered the remainder: %v", err)
	}
	if info, err := os.Stat(filepath.Join(output, "bin/tool")); err != nil || info.Mode().Perm() != 0o755 {
		test.Fatalf("native mode differs: %v: %v", info, err)
	}
	if target, err := os.Readlink(filepath.Join(output, "bin/current")); err != nil || target != "./tool" {
		test.Fatalf("link spelling differs: %q: %v", target, err)
	}
	if len(inventory) != 4 {
		test.Fatalf("expected only four remainder outputs, got %+v", inventory)
	}
	for _, entry := range inventory {
		actual, err := filemetadata.Inspect(filepath.Join(output, entry.RelativePath), entry.RelativePath)
		if err != nil || actual != entry {
			test.Fatalf("inventory differs: %+v, %+v: %v", entry, actual, err)
		}
	}
	classpath := bytes.NewReader(readTestFile(test, filepath.Join(prepared, "plugin-classpath.txt")))
	readShortString := func() string {
		var size uint16
		if err := binary.Read(classpath, binary.BigEndian, &size); err != nil {
			test.Fatal(err)
		}
		value := make([]byte, size)
		if _, err := io.ReadFull(classpath, value); err != nil {
			test.Fatal(err)
		}
		return string(value)
	}
	var count uint16
	if err := binary.Read(classpath, binary.BigEndian, &count); err != nil || count != 2 {
		test.Fatalf("invalid classpath count: %d: %v", count, err)
	}
	if name := readShortString(); name != "demo" {
		test.Fatalf("unexpected plugin directory: %s", name)
	}
	var descriptorSize uint32
	if err := binary.Read(classpath, binary.BigEndian, &descriptorSize); err != nil {
		test.Fatal(err)
	}
	if _, err := classpath.Seek(int64(descriptorSize), io.SeekCurrent); err != nil {
		test.Fatal(err)
	}
	if paths := []string{readShortString(), readShortString()}; !reflect.DeepEqual(paths, []string{"lib/demo.jar", "lib/content.jar"}) || classpath.Len() != 0 {
		test.Fatalf("classpath order differs: %v", paths)
	}
}

// The two parity tests below run the Kotlin preparer on a plan file. The preparer emits the Go operation into the
// recipe, and Go executes it from the raw inputs. The tests compare that result with the Go execution of a
// hand-written recipe. They also compare it with the golden under testdata. The golden is the Kotlin materialization
// of every fixture, frozen under testdata/<name>-golden-<date>.txt before the change that stopped the Kotlin
// materialization. Do not re-record the goldens from a later state of the preparer.

var recordKotlinGoldens = flag.String("record-kotlin-goldens", "", "Record the Kotlin materialization as goldens with this label, for example golden-2026-09-13")

// kotlinPreparerExecutable resolves the declared Kotlin preparer, or skips the test outside the Bazel target.
func kotlinPreparerExecutable(t *testing.T) string {
	t.Helper()
	if *kotlinPreparer == "" {
		t.Skip("Run the Bazel pluginpack_test target to include the declared Kotlin preparer")
	}
	executable := *kotlinPreparer
	if !filepath.IsAbs(executable) {
		executable = filepath.Join(os.Getenv("TEST_SRCDIR"), filepath.FromSlash(executable))
	}
	return executable
}

// kotlinPlanFile is the plan file the parity fixtures write. The Kotlin preparer recomputes the layout signature
// and every operation signature, and it refuses a stale file. The fixtures compute both the way
// pluginPackingLayoutSignature and devPluginPreparationOperationSignature do. A zero Mode is the default 420. An
// asset with a Module is the compact form of a module's own jar.
type kotlinPlanFile struct {
	Version           int                      `json:"version"`
	Plugin            string                   `json:"plugin"`
	Variant           string                   `json:"variant"`
	LayoutSignature   string                   `json:"layoutSignature"`
	Assets            []kotlinPlanAsset        `json:"assets"`
	Preparations      []kotlinPreparation      `json:"preparations,omitempty"`
	PreparationRoots  []string                 `json:"preparationRoots,omitempty"`
	ReusableArtifacts []kotlinReusableArtifact `json:"reusableArtifacts,omitempty"`
	Operations        []json.RawMessage        `json:"operations,omitempty"`
}

type kotlinPlanAsset struct {
	Module             string           `json:"module,omitempty"`
	Destination        string           `json:"destination,omitempty"`
	Inputs             []string         `json:"inputs"`
	Recipe             *kotlinJarRecipe `json:"recipe,omitempty"`
	Mode               int              `json:"mode,omitempty"`
	SymlinkTarget      *string          `json:"symlinkTarget,omitempty"`
	Kind               string           `json:"kind,omitempty"`
	ClassPath          *bool            `json:"classPath,omitempty"`
	NormalizeTreeModes bool             `json:"normalizeTreeModes,omitempty"`
	Scope              string           `json:"scope,omitempty"`
}

type kotlinJarRecipe struct {
	Sources []kotlinJarSource `json:"sources"`
	Writer  kotlinJarWriter   `json:"writer"`
}

type kotlinJarSource struct {
	Input            string                  `json:"input"`
	Kind             string                  `json:"kind"`
	Filter           string                  `json:"filter"`
	Entry            string                  `json:"entry,omitempty"`
	Expansion        []string                `json:"expansion,omitempty"`
	Options          []string                `json:"options,omitempty"`
	PreparedManifest *kotlinPreparedManifest `json:"preparedManifest,omitempty"`
}

type kotlinPreparedManifest struct {
	OriginalMeaningfulSourceCount *int     `json:"originalMeaningfulSourceCount,omitempty"`
	SourceManifestPolicies        []string `json:"sourceManifestPolicies"`
}

// kotlinJarWriter is the writer of a jar recipe. An empty Manifest is the default single-meaningful-source.
type kotlinJarWriter struct {
	Manifest             string `json:"manifest,omitempty"`
	MergeEntities        bool   `json:"mergeEntities"`
	DirectoryEntries     bool   `json:"directoryEntries,omitempty"`
	RewriteBootClassPath bool   `json:"rewriteBootClassPath,omitempty"`
	OutputName           string `json:"outputName,omitempty"`
}

type kotlinPreparation struct {
	ID             string   `json:"id"`
	Inputs         []string `json:"inputs"`
	Outputs        []string `json:"outputs"`
	ModelSignature string   `json:"modelSignature"`
	AlwaysRun      bool     `json:"alwaysRun,omitempty"`
}

// kotlinReusableArtifact states a module, the compact form, or a recipe. A zero Mode is the default 420.
type kotlinReusableArtifact struct {
	Label  string           `json:"label"`
	Module string           `json:"module,omitempty"`
	Recipe *kotlinJarRecipe `json:"recipe,omitempty"`
	Mode   int              `json:"mode,omitempty"`
}

// expanded returns the full form of an asset: the compact module form becomes the module jar asset, nil inputs are
// the recipe sources, and the defaults are filled in, the way the Kotlin codec decodes it. Nil inputs encode as null,
// which the codec reads as absent; a directory or a link asset states an empty list.
func (asset kotlinPlanAsset) expanded() kotlinPlanAsset {
	if asset.Module != "" {
		asset = kotlinPlanAsset{Destination: "lib/modules/" + asset.Module + ".jar", Inputs: []string{asset.Module}, Recipe: kotlinModuleJarRecipe(asset.Module)}
	}
	if asset.Inputs == nil && asset.Recipe != nil {
		for _, source := range asset.Recipe.Sources {
			asset.Inputs = append(asset.Inputs, source.Input)
		}
	}
	if asset.Mode == 0 {
		asset.Mode = 420
	}
	if asset.Kind == "" {
		asset.Kind = "file"
	}
	if asset.Scope == "" {
		asset.Scope = PluginScope
	}
	return asset
}

// kotlinModuleJarRecipe is the recipe of a module's own jar, the recipe the compact form stands for.
func kotlinModuleJarRecipe(module string) *kotlinJarRecipe {
	return &kotlinJarRecipe{Sources: []kotlinJarSource{{Input: module, Kind: "module", Filter: "module-v1"}}, Writer: kotlinJarWriter{MergeEntities: true}}
}

func (writer kotlinJarWriter) manifest() string {
	if writer.Manifest == "" {
		return "single-meaningful-source"
	}
	return writer.Manifest
}

func sha256Hex(data []byte) string {
	digest := sha256.Sum256(data)
	return hex.EncodeToString(digest[:])
}

// kotlinJSON encodes a value without the HTML escaping kotlinx.serialization never applies.
func kotlinJSON(t *testing.T, value any) string {
	t.Helper()
	var buffer bytes.Buffer
	encoder := json.NewEncoder(&buffer)
	encoder.SetEscapeHTML(false)
	if err := encoder.Encode(value); err != nil {
		t.Fatal(err)
	}
	return strings.TrimSuffix(buffer.String(), "\n")
}

// kotlinModelSignature hashes a preparation recipe of format 2 that holds one operation in its kotlinx encoding.
func kotlinModelSignature(operation string) string {
	var buffer hash4jStream
	buffer.putString(`{"version":2,"operations":[` + operation + `]}`)
	return xxh3.Signature128(buffer.Bytes())
}

// kotlinModuleFilterOperation is the kotlinx encoding of a module-filter operation, the text its signature hashes.
func kotlinModuleFilterOperation(t *testing.T, id, input, output, manifest string, excludes []string) string {
	t.Helper()
	if excludes == nil {
		excludes = []string{}
	}
	return fmt.Sprintf(`{"id":%s,"kind":"module-filter","input":{"artifact":%s,"path":""},"output":%s,"manifest":%s,"excludes":%s}`,
		kotlinJSON(t, id), kotlinJSON(t, input), kotlinJSON(t, output), kotlinJSON(t, manifest), kotlinJSON(t, excludes))
}

// kotlinLayoutAssetsOperation is the kotlinx encoding of a layout-assets operation over the Go payload. A default
// is written when its class encodes defaults and omitted when the field is marked never.
func kotlinLayoutAssetsOperation(t *testing.T, id, output, format, root string, layout LayoutAssets) string {
	t.Helper()
	inputs := make([]string, 0, len(layout.Inputs))
	for _, reference := range layout.Inputs {
		inputs = append(inputs, fmt.Sprintf(`{"artifact":%s,"path":%s}`, kotlinJSON(t, reference.Artifact), kotlinJSON(t, reference.Path)))
	}
	assets := make([]string, 0, len(layout.Assets))
	for _, asset := range layout.Assets {
		sources := asset.Sources
		if sources == nil {
			sources = []int{}
		}
		text := fmt.Sprintf(`{"destination":%s,"sources":%s`, kotlinJSON(t, asset.Destination), kotlinJSON(t, sources))
		if transform := asset.Transform; transform != nil {
			text += fmt.Sprintf(`,"transform":{"kind":%s`, kotlinJSON(t, transform.Kind))
			if transform.StripComponents != 0 {
				text += fmt.Sprintf(`,"stripComponents":%d`, transform.StripComponents)
			}
			if transform.Text != "" {
				text += `,"text":` + kotlinJSON(t, transform.Text)
			}
			if len(transform.Mappings) != 0 {
				mappings := make([]string, 0, len(transform.Mappings))
				for _, mapping := range transform.Mappings {
					mappings = append(mappings, fmt.Sprintf(`{"pattern":%s,"stripComponents":%d,"destination":%s}`,
						kotlinJSON(t, mappingPattern(mapping)), mapping.StripComponents, kotlinJSON(t, mapping.Destination)))
				}
				text += `,"mappings":[` + strings.Join(mappings, ",") + `]`
			}
			text += "}"
		}
		if asset.Mode != 0 {
			text += fmt.Sprintf(`,"mode":%d`, asset.Mode)
		}
		assets = append(assets, text+"}")
	}
	inputsField := ""
	if len(inputs) != 0 {
		inputsField = `,"inputs":[` + strings.Join(inputs, ",") + `]`
	}
	return fmt.Sprintf(`{"id":%s,"kind":"layout-assets"%s,"output":%s,"manifest":"keep","excludes":[],"layoutAssets":{"format":%s,"root":%s,"assets":[%s]}}`,
		kotlinJSON(t, id), inputsField, kotlinJSON(t, output), kotlinJSON(t, format), kotlinJSON(t, root), strings.Join(assets, ","))
}

// hash4jStream frames values the way a hash4j `HashStream` does, so `xxh3.Signature128` over its bytes equals the
// Kotlin `devDistSignature` over the same puts.
type hash4jStream struct{ bytes.Buffer }

func (stream *hash4jStream) putInt(value int) {
	binary.Write(&stream.Buffer, binary.LittleEndian, int32(value))
}

func (stream *hash4jStream) putBoolean(value bool) {
	if value {
		stream.WriteByte(1)
	} else {
		stream.WriteByte(0)
	}
}

// putString is hash4j's `putString`: the UTF-16 code units, then their count.
func (stream *hash4jStream) putString(value string) {
	units := utf16.Encode([]rune(value))
	for _, unit := range units {
		binary.Write(&stream.Buffer, binary.LittleEndian, unit)
	}
	stream.putInt(len(units))
}

// kotlinLayoutSignature is pluginPackingLayoutSignature over the plan file in its full form.
func kotlinLayoutSignature(plan kotlinPlanFile) string {
	var buffer hash4jStream
	writeInt := buffer.putInt
	writeBool := buffer.putBoolean
	writeText := buffer.putString
	writeTexts := func(values []string) {
		writeInt(len(values))
		for _, value := range values {
			writeText(value)
		}
	}
	assets := make([]kotlinPlanAsset, 0, len(plan.Assets))
	for _, asset := range plan.Assets {
		assets = append(assets, asset.expanded())
	}
	scopedAssets := slices.ContainsFunc(assets, func(asset kotlinPlanAsset) bool { return asset.Scope != PluginScope })
	trees := slices.ContainsFunc(assets, func(asset kotlinPlanAsset) bool { return asset.Kind == "tree" })
	preparedManifests := trees || slices.ContainsFunc(assets, func(asset kotlinPlanAsset) bool {
		return asset.Recipe != nil && slices.ContainsFunc(asset.Recipe.Sources, func(source kotlinJarSource) bool { return source.PreparedManifest != nil })
	})
	classPathFacts := preparedManifests || slices.ContainsFunc(assets, func(asset kotlinPlanAsset) bool { return asset.ClassPath != nil && !*asset.ClassPath })
	directories := classPathFacts || slices.ContainsFunc(assets, func(asset kotlinPlanAsset) bool { return asset.Kind != "file" }) ||
		slices.ContainsFunc(plan.Preparations, func(preparation kotlinPreparation) bool { return preparation.AlwaysRun })
	switch {
	case scopedAssets:
		writeInt(6)
	case trees:
		writeInt(5)
	case preparedManifests:
		writeInt(4)
	case classPathFacts:
		writeInt(3)
	case directories:
		writeInt(2)
	default:
		writeInt(1)
	}
	writeText(plan.Plugin)
	writeText(plan.Variant)
	writeInt(len(assets))
	for _, asset := range assets {
		writeText(asset.Destination)
		if scopedAssets {
			writeText(asset.Scope)
		}
		if directories {
			writeText(asset.Kind)
		}
		if classPathFacts {
			writeBool(asset.ClassPath == nil || *asset.ClassPath)
		}
		writeInt(asset.Mode)
		writeBool(asset.SymlinkTarget != nil)
		if asset.SymlinkTarget != nil {
			writeText(*asset.SymlinkTarget)
		}
		writeTexts(asset.Inputs)
		writeBool(asset.Recipe != nil)
		if asset.Recipe != nil {
			writeInt(len(asset.Recipe.Sources))
			for _, source := range asset.Recipe.Sources {
				writeText(source.Input)
				writeText(source.Kind)
				writeText(source.Filter)
				writeText(source.Entry)
				writeTexts(source.Expansion)
				writeTexts(source.Options)
				if preparedManifests {
					writeBool(source.PreparedManifest != nil)
					if manifest := source.PreparedManifest; manifest != nil {
						writeInt(1)
						count := -1
						if manifest.OriginalMeaningfulSourceCount != nil {
							count = *manifest.OriginalMeaningfulSourceCount
						}
						writeInt(count)
						writeTexts(manifest.SourceManifestPolicies)
					}
				}
			}
			writeText(asset.Recipe.Writer.manifest())
			writeBool(asset.Recipe.Writer.MergeEntities)
			writeBool(asset.Recipe.Writer.DirectoryEntries)
			writeBool(asset.Recipe.Writer.RewriteBootClassPath)
			writeText(asset.Recipe.Writer.OutputName)
		}
	}
	writeInt(len(plan.Preparations))
	for _, preparation := range plan.Preparations {
		writeText(preparation.ID)
		writeTexts(preparation.Inputs)
		writeTexts(preparation.Outputs)
		writeText(preparation.ModelSignature)
		if directories {
			writeBool(preparation.AlwaysRun)
		}
	}
	writeTexts(plan.PreparationRoots)
	return xxh3.Signature128(buffer.Bytes())
}

// TestKotlinSignatureHelpersReproduceTheFixtureConstants pins the two signature helpers against the constants the
// Kotlin generator wrote into kotlinProjection and kotlinFilteredProjection.
func TestKotlinSignatureHelpersReproduceTheFixtureConstants(t *testing.T) {
	filter := kotlinModuleFilterOperation(t, "filter", "raw", "filtered", "drop", []string{"drop/**"})
	if got := kotlinModelSignature(filter); got != "4j4kth710fglswfsh1vosdrg4" {
		t.Fatalf("model signature differs: %s", got)
	}
	filtered := kotlinPlanFile{Version: Version, Plugin: "filtered-plugin", Variant: "linux",
		Assets: []kotlinPlanAsset{{Destination: "lib/main.jar", Inputs: []string{"filtered"}, Recipe: &kotlinJarRecipe{
			Sources: []kotlinJarSource{{Input: "filtered", Kind: "prepared", Filter: "prepared"}}, Writer: kotlinJarWriter{Manifest: "drop"}}}},
		Preparations: []kotlinPreparation{{ID: "filter", Inputs: []string{"raw"}, Outputs: []string{"filtered"}, ModelSignature: kotlinModelSignature(filter)}}}
	if got := kotlinLayoutSignature(filtered); got != "c6yx54cclyu91esyjhzd6z0xe" {
		t.Fatalf("layout signature of the filtered projection differs: %s", got)
	}
	var demo struct {
		Plugin, Variant string
		Assets          []struct {
			Destination   string
			Inputs        []string
			Recipe        *kotlinJarRecipe
			Mode          int
			SymlinkTarget *string
		}
	}
	if err := json.Unmarshal([]byte(kotlinProjection), &demo); err != nil {
		t.Fatal(err)
	}
	if len(demo.Assets) != 5 || demo.Assets[3].Mode != 493 || demo.Assets[4].SymlinkTarget == nil {
		t.Fatalf("the demo projection changed shape: %+v", demo.Assets)
	}
}

// kotlinPlan states one preparation whose operation is the kotlinx text, and signs the file.
func kotlinPlan(t *testing.T, version int, plugin string, assets []kotlinPlanAsset, preparation kotlinPreparation, operation string) kotlinPlanFile {
	t.Helper()
	preparation.ModelSignature = kotlinModelSignature(operation)
	plan := kotlinPlanFile{Version: version, Plugin: plugin, Assets: assets,
		Preparations: []kotlinPreparation{preparation}, Operations: []json.RawMessage{json.RawMessage(operation)}}
	plan.LayoutSignature = kotlinLayoutSignature(plan)
	return plan
}

// kotlinPreparationOutput is what the Kotlin preparer writes for one plan: the recipe, the remainder catalogue, the
// asset rows and the plugin classpath record. The prepared directory stays empty for a plan Go executes.
type kotlinPreparationOutput struct {
	Recipe    Recipe
	Catalogue Catalogue
	Assets    []Asset
	ClassPath []byte
	Directory string
}

// runKotlinPreparer runs the Kotlin preparer on a plan whose operations Go executes. Every raw input is then a
// remainder input. The plugin directory is plugins/<plugin>; descriptor is the classpath descriptor.
func runKotlinPreparer(t *testing.T, plan kotlinPlanFile, inputs Catalogue, descriptor []byte) kotlinPreparationOutput {
	t.Helper()
	executable := kotlinPreparerExecutable(t)
	root := t.TempDir()
	writeTestFile(t, filepath.Join(root, "projection.json"), []byte(kotlinJSON(t, plan)))
	writeTestFile(t, filepath.Join(root, "catalogue.json"), []byte(kotlinJSON(t, inputs)))
	writeTestFile(t, filepath.Join(root, "descriptor.xml"), descriptor)
	prepared := filepath.Join(root, "prepared-plugin")
	arguments := []string{
		"--projection=" + filepath.Join(root, "projection.json"),
		"--catalogue=" + filepath.Join(root, "catalogue.json"),
		"--descriptor=" + filepath.Join(root, "descriptor.xml"),
		"--plugin-directory=" + filepath.Join("plugins", plan.Plugin),
		"--output-dir=" + prepared,
		"--callback-preparation=false",
		fmt.Sprintf("--execution-version=%d", plan.Version),
	}
	for _, artifact := range inputs.Artifacts {
		arguments = append(arguments, "--remainder-input="+artifact.Root)
	}
	commandContext, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	command := exec.CommandContext(commandContext, executable, arguments...)
	command.Dir = root
	if output, err := command.CombinedOutput(); err != nil {
		t.Fatalf("Kotlin preparation failed: %v\n%s", err, output)
	}
	result := kotlinPreparationOutput{Directory: prepared}
	if err := ReadJSON(filepath.Join(prepared, "recipe.json"), &result.Recipe); err != nil {
		t.Fatal(err)
	}
	if err := ReadJSON(filepath.Join(prepared, "catalogue.json"), &result.Catalogue); err != nil {
		t.Fatal(err)
	}
	result.Assets = readAssetRows(t, filepath.Join(prepared, "assets.json"))
	result.ClassPath = readTestFile(t, filepath.Join(prepared, "plugin-classpath.txt"))
	return result
}

// readAssetRows decodes an assets.json, a JSON array, with the strictness of ReadJSON.
func readAssetRows(t *testing.T, file string) []Asset {
	t.Helper()
	decoder := json.NewDecoder(bytes.NewReader(readTestFile(t, file)))
	decoder.DisallowUnknownFields()
	var assets []Asset
	if err := decoder.Decode(&assets); err != nil {
		t.Fatal(err)
	}
	if _, err := decoder.Token(); err != io.EOF {
		t.Fatalf("%s: expected one JSON document", file)
	}
	return assets
}

// kotlinMaterialization runs the Kotlin preparer on a plan whose one operation Go executes and returns the recipe
// and the catalogue it wrote.
func kotlinMaterialization(t *testing.T, plan kotlinPlanFile, inputs Catalogue) (Recipe, Catalogue) {
	t.Helper()
	output := runKotlinPreparer(t, plan, inputs, []byte("<idea-plugin><id>"+plan.Plugin+"</id></idea-plugin>"))
	return output.Recipe, output.Catalogue
}

// canonicalOperations renders operations with the Go encoder. The Kotlin encoder writes empty lists and default
// values; the Go encoder omits them, so both producers compare on the values the packer reads. An empty mapping
// pattern reads as "**".
func canonicalOperations(t *testing.T, operations []Operation) string {
	t.Helper()
	normalized := make([]Operation, 0, len(operations))
	for _, operation := range operations {
		operation.Layout = canonicalLayout(operation.Layout)
		sources := make([]Source, 0, len(operation.Sources))
		for _, source := range operation.Sources {
			source.Layout = canonicalLayout(source.Layout)
			sources = append(sources, source)
		}
		operation.Sources = sources
		normalized = append(normalized, operation)
	}
	data, err := json.MarshalIndent(normalized, "", " ")
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}

func canonicalLayout(layout *LayoutAssets) *LayoutAssets {
	if layout == nil {
		return nil
	}
	result := *layout
	result.Assets = slices.Clone(layout.Assets)
	for index := range result.Assets {
		transform := result.Assets[index].Transform
		if transform == nil {
			continue
		}
		copied := *transform
		copied.Mappings = slices.Clone(transform.Mappings)
		for mappingIndex := range copied.Mappings {
			if copied.Mappings[mappingIndex].Pattern == "" {
				copied.Mappings[mappingIndex].Pattern = "**"
			}
		}
		result.Assets[index].Transform = &copied
	}
	return &result
}

// requireSameRecipeRows pins the rows both producers must agree on: the version, the layout signature, the asset
// table, and the operations the Go packer executes. A Kotlin recipe that still materialized the operation, as a
// copy-tree or an anchored entries source, fails here.
func requireSameRecipeRows(t *testing.T, kotlin, recipe Recipe) {
	t.Helper()
	if kotlin.Version != recipe.Version || kotlin.Plugin != recipe.Plugin || kotlin.LayoutSignature != recipe.LayoutSignature ||
		!reflect.DeepEqual(kotlin.Assets, recipe.Assets) {
		t.Fatalf("the Kotlin recipe rows differ from the Go recipe:\n%+v\n%+v", kotlin, recipe)
	}
	if actual, expected := canonicalOperations(t, kotlin.Operations), canonicalOperations(t, recipe.Operations); actual != expected {
		t.Fatalf("the Kotlin operations differ from the Go operations:\n%s\n%s", actual, expected)
	}
}

// materializationRecord lists every entry of a written plugin directory in path order. A line holds the path, the
// kind, the mode, and the content digest or the link target, tab-separated.
func materializationRecord(t *testing.T, root string) []string {
	t.Helper()
	var record []string
	err := filepath.WalkDir(root, func(name string, entry fs.DirEntry, err error) error {
		if err != nil || name == root {
			return err
		}
		relative, err := filepath.Rel(root, name)
		if err != nil {
			return err
		}
		relative = filepath.ToSlash(relative)
		info, err := entry.Info()
		if err != nil {
			return err
		}
		switch {
		case info.Mode().IsRegular():
			record = append(record, fmt.Sprintf("%s\tfile\t%04o\t%s", relative, info.Mode().Perm(), sha256Hex(readTestFile(t, name))))
		case info.Mode()&os.ModeSymlink != 0:
			target, err := os.Readlink(name)
			if err != nil {
				return err
			}
			record = append(record, fmt.Sprintf("%s\tsymlink\t-\t%s", relative, target))
		case info.IsDir():
			record = append(record, fmt.Sprintf("%s\tdirectory\t%04o\t-", relative, info.Mode().Perm()))
		default:
			return fmt.Errorf("unsupported entry %s", name)
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	return record
}

func requireEqualRecords(t *testing.T, what string, first, second []string) {
	t.Helper()
	if slices.Equal(first, second) {
		return
	}
	var onlyFirst, onlySecond []string
	for _, line := range first {
		if !slices.Contains(second, line) {
			onlyFirst = append(onlyFirst, line)
		}
	}
	for _, line := range second {
		if !slices.Contains(first, line) {
			onlySecond = append(onlySecond, line)
		}
	}
	t.Fatalf("%s differs\nonly in the first:\n%s\nonly in the second:\n%s", what, strings.Join(onlyFirst, "\n"), strings.Join(onlySecond, "\n"))
}

// requireSameJar compares two jars by entry order and content before the byte comparison, for a readable failure.
func requireSameJar(t *testing.T, kotlinJar, goJar string) {
	t.Helper()
	kotlinNames, kotlinEntries := readArchive(t, kotlinJar)
	names, entries := readArchive(t, goJar)
	if !slices.Equal(kotlinNames, names) {
		t.Fatalf("jar entries differ\nKotlin: %v\nGo:     %v", kotlinNames, names)
	}
	for _, name := range names {
		if kotlinEntries[name] != entries[name] {
			t.Fatalf("entry %s differs between the Kotlin jar and the Go jar", name)
		}
	}
}

// requireInventoryMatchesTree pins every inventory row to the file it describes.
func requireInventoryMatchesTree(t *testing.T, output string, inventory []filemetadata.Entry) {
	t.Helper()
	for _, entry := range inventory {
		actual, err := filemetadata.Inspect(filepath.Join(output, entry.RelativePath), entry.RelativePath)
		if err != nil || actual != entry {
			t.Fatalf("inventory differs from the tree: %+v, %+v: %v", entry, actual, err)
		}
	}
}

// requireRecordedPaths pins that a fixture produced what it was written for, so an empty tree cannot pass parity.
func requireRecordedPaths(t *testing.T, record []string, paths []string) {
	t.Helper()
	for _, path := range paths {
		if !slices.ContainsFunc(record, func(line string) bool { return strings.HasPrefix(line, path+"\t") }) {
			t.Fatalf("%s is missing from the output:\n%s", path, strings.Join(record, "\n"))
		}
	}
}

// kotlinGolden is the record of the Kotlin materialization of every fixture, frozen at the end of Stage 1 under
// testdata/<name>-<label>.txt. The label states the recording date. While recording, check stores the record and
// write saves the file.
type kotlinGolden struct {
	name     string
	label    string
	fixtures map[string][]string
}

func openKotlinGolden(t *testing.T, name string) *kotlinGolden {
	t.Helper()
	golden := &kotlinGolden{name: name, label: *recordKotlinGoldens, fixtures: make(map[string][]string)}
	if golden.label != "" {
		return golden
	}
	matches, err := filepath.Glob(filepath.Join("testdata", name+"-*.txt"))
	if err != nil || len(matches) != 1 {
		t.Fatalf("expected one golden testdata/%s-<label>.txt, found %v: %v", name, matches, err)
	}
	golden.label = strings.TrimSuffix(strings.TrimPrefix(filepath.Base(matches[0]), name+"-"), ".txt")
	for _, line := range strings.Split(string(readTestFile(t, matches[0])), "\n") {
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		fixture, entry, found := strings.Cut(line, "\t")
		if !found {
			t.Fatalf("%s: malformed line %q", matches[0], line)
		}
		golden.fixtures[fixture] = append(golden.fixtures[fixture], entry)
	}
	return golden
}

// check compares one fixture with the golden, or stores its record while recording.
func (golden *kotlinGolden) check(t *testing.T, fixture string, record []string) {
	t.Helper()
	if *recordKotlinGoldens != "" {
		golden.fixtures[fixture] = record
		return
	}
	want, present := golden.fixtures[fixture]
	if !present {
		t.Fatalf("fixture %q has no golden in testdata/%s-%s.txt; record it with -record-kotlin-goldens=<label>", fixture, golden.name, golden.label)
	}
	requireEqualRecords(t, fixture+" against the golden", want, record)
}

// write saves the recorded goldens under TEST_UNDECLARED_OUTPUTS_DIR, or under testdata outside Bazel.
func (golden *kotlinGolden) write(t *testing.T) {
	t.Helper()
	if *recordKotlinGoldens == "" {
		return
	}
	directory := os.Getenv("TEST_UNDECLARED_OUTPUTS_DIR")
	if directory == "" {
		directory = "testdata"
	}
	lines := []string{
		fmt.Sprintf("# %s: the Kotlin preparer's materialization, %s, recorded by pluginpack_test -record-kotlin-goldens.", golden.name, golden.label),
		"# fixture\tpath\tkind (directory, file, symlink, or a jar entry when the jar order follows the host)\tmode\tsha256 or link target",
	}
	for _, fixture := range slices.Sorted(maps.Keys(golden.fixtures)) {
		for _, entry := range golden.fixtures[fixture] {
			lines = append(lines, fixture+"\t"+entry)
		}
	}
	writeTestFile(t, filepath.Join(directory, fmt.Sprintf("%s-%s.txt", golden.name, golden.label)), []byte(strings.Join(lines, "\n")+"\n"))
}

// moduleFilterFixtureJar is the module output the module-filter cases filter. It holds the names the selection
// treats specially: the entity list, the manifest, icon-robots.txt, a nested glob target, and an anchored prefix.
var moduleFilterFixtureJar = []testEntry{
	{"keep/Service.class", "retained"},
	{"drop/Ignore.class", "excluded"},
	{"keep/drop/Kept.class", "kept: the glob is anchored"},
	{"META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n"},
	{"META-INF/listOfEntities.txt", "keep.Service\n"},
	{"META-INF/services/x.Y", "impl"},
	{"icon-robots.txt", "robots"},
	{"keep/icon-robots.txt", "robots"},
	{"a/x.txt", "ax"},
	{"a/b/x.txt", "abx"},
	{"x.txt", "x"},
	{"a/y.txt", "ay"},
}

// TestKotlinModuleFilterMaterializationMatchesGoExcludes runs the Kotlin module-filter preparation, which emits the
// Go archive source with the excludes, and packs that recipe with Go. It requires the jar to be byte-identical to
// the jar of the hand-written Go recipe and to the golden of the Kotlin materialization.
func TestKotlinModuleFilterMaterializationMatchesGoExcludes(t *testing.T) {
	kotlinPreparerExecutable(t)
	golden := openKotlinGolden(t, "kotlin-module-filter")
	cases := []struct {
		name        string
		excludes    []string
		directories bool
		absent      []string
		present     []string
	}{
		{"an anchored prefix glob", []string{"drop/**"}, false,
			[]string{"drop/Ignore.class"}, []string{"keep/drop/Kept.class", "META-INF/listOfEntities.txt"}},
		{"a nested glob", []string{"**/x.txt"}, false,
			[]string{"a/x.txt", "a/b/x.txt"}, []string{"x.txt", "a/y.txt"}},
		{"listOfEntities under an excluded prefix", []string{"META-INF/**"}, false,
			[]string{"META-INF/services/x.Y", "META-INF/MANIFEST.MF"}, []string{"META-INF/listOfEntities.txt"}},
		{"no declared excludes", nil, false,
			[]string{"icon-robots.txt", "keep/icon-robots.txt"}, []string{"drop/Ignore.class"}},
		{"a jar with directory entries", []string{"drop/**"}, true,
			[]string{"keep/", "drop/", "META-INF/", "drop/Ignore.class"}, []string{"keep/Service.class"}},
	}
	for _, testCase := range cases {
		for _, manifest := range []string{"keep", "drop"} {
			name := testCase.name + " with manifest " + manifest
			t.Run(name, func(t *testing.T) {
				jar := filepath.Join(t.TempDir(), "module.jar")
				entries := moduleFilterFixtureJar
				if testCase.directories {
					entries = slices.Concat([]testEntry{{"keep/", ""}, {"drop/", ""}, {"META-INF/", ""}}, entries)
				}
				archiveFile(t, jar, entries...)
				inputs := Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("raw", jar)}}
				operation := kotlinModuleFilterOperation(t, "filter", "raw", "filtered", manifest, testCase.excludes)
				plan := kotlinPlan(t, Version, "filtered-plugin", []kotlinPlanAsset{{Destination: "lib/main.jar", Inputs: []string{"filtered"},
					Recipe: &kotlinJarRecipe{Sources: []kotlinJarSource{{Input: "filtered", Kind: "prepared", Filter: "prepared"}},
						Writer: kotlinJarWriter{Manifest: manifest, MergeEntities: true}}}},
					kotlinPreparation{ID: "filter", Inputs: []string{"raw"}, Outputs: []string{"filtered"}}, operation)
				kotlinRecipe, kotlinCatalogue := kotlinMaterialization(t, plan, inputs)
				kotlinOutput, kotlinInventory := writeExecution(t, kotlinRecipe, kotlinCatalogue)

				recipe := Recipe{Version: Version, Plugin: plan.Plugin, LayoutSignature: plan.LayoutSignature,
					Assets: []Asset{{Destination: "lib/main.jar", Producer: "remainder"}},
					Operations: []Operation{{Kind: "jar", Destination: "lib/main.jar", Mode: 0o644, Options: &JarOptions{MergeEntities: true, Directories: "none"},
						Sources: []Source{{Kind: "archive", Input: &Reference{Artifact: "raw"}, Filter: "module", Manifest: manifest, Excludes: testCase.excludes}}}}}
				requireSameRecipeRows(t, kotlinRecipe, recipe)
				output, inventory := writeExecution(t, recipe, inputs)
				requireSameJar(t, filepath.Join(kotlinOutput, "lib/main.jar"), filepath.Join(output, "lib/main.jar"))
				kotlinRecord, record := materializationRecord(t, kotlinOutput), materializationRecord(t, output)
				requireEqualRecords(t, "the hand-written Go recipe against the Kotlin-emitted recipe", kotlinRecord, record)
				if !reflect.DeepEqual(kotlinInventory, inventory) {
					t.Fatalf("inventories differ:\n%+v\n%+v", kotlinInventory, inventory)
				}
				requireInventoryMatchesTree(t, output, inventory)
				names, _ := readArchive(t, filepath.Join(output, "lib/main.jar"))
				for _, entry := range testCase.absent {
					if slices.Contains(names, entry) {
						t.Fatalf("%s survived the excludes: %v", entry, names)
					}
				}
				for _, entry := range testCase.present {
					if !slices.Contains(names, entry) {
						t.Fatalf("%s is missing: %v", entry, names)
					}
				}
				manifestExcluded := slices.Contains(testCase.absent, "META-INF/MANIFEST.MF")
				if slices.Contains(names, "META-INF/MANIFEST.MF") != (manifest == "keep" && !manifestExcluded) {
					t.Fatalf("manifest policy %s produced %v", manifest, names)
				}
				golden.check(t, name, record)
			})
		}
	}
	golden.write(t)
}

// storedZipBytes writes STORED entries with their sizes and CRC in the local header and no data descriptor, the
// shape the Kotlin stream reader of a .zip.zst accepts. Creator 3 carries Unix modes and links.
func storedZipBytes(t *testing.T, entries ...zipTestEntry) []byte {
	t.Helper()
	var buffer bytes.Buffer
	writer := zip.NewWriter(&buffer)
	for _, entry := range entries {
		content := []byte(entry.content)
		header := &zip.FileHeader{Name: entry.name, Method: zip.Store, CRC32: crc32.ChecksumIEEE(content),
			CompressedSize64: uint64(len(content)), UncompressedSize64: uint64(len(content))}
		if entry.creator != 0 {
			unixMode := entry.mode | 0o100000
			switch {
			case entry.symlink:
				unixMode = entry.mode | 0o120000
			case strings.HasSuffix(entry.name, "/"):
				unixMode = entry.mode | 0o040000
			}
			header.CreatorVersion = entry.creator << 8
			header.ExternalAttrs = unixMode << 16
		}
		output, err := writer.CreateRaw(header)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := output.Write(content); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

func chmodTestFile(t *testing.T, file string, mode os.FileMode) {
	t.Helper()
	if err := os.Chmod(file, mode); err != nil {
		t.Fatal(err)
	}
}

// chmodTestTree sets every directory of a source tree to 0755 and every regular file to 0644. The modes the golden
// records are then fixture facts and not umask facts. A fixture applies its own modes after this call.
func chmodTestTree(t *testing.T, root string) {
	t.Helper()
	err := filepath.WalkDir(root, func(name string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		switch {
		case entry.IsDir():
			return os.Chmod(name, 0o755)
		case entry.Type().IsRegular():
			return os.Chmod(name, 0o644)
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
}

// layoutParityFixture is one layout-assets operation with its raw inputs on disk. A tree fixture names its root; an
// entries fixture names its jar. present lists the output paths the fixture exists for. hostOrder marks a jar whose
// entry order follows the readdir order of the host: the live comparison checks the order, the golden does not.
type layoutParityFixture struct {
	format    string
	root      string
	normalize bool
	hostOrder bool
	layout    LayoutAssets
	inputs    Catalogue
	present   []string
}

// jarEntryRecord lists the entries of a jar by name with their content digest, sorted by name. The generated index
// follows the entry order and is left out; jarpack's own tests guard it.
func jarEntryRecord(t *testing.T, jar string) []string {
	t.Helper()
	names, entries := readArchive(t, jar)
	record := make([]string, 0, len(names))
	for _, name := range names {
		if name == "__index__" {
			continue
		}
		record = append(record, fmt.Sprintf("%s\tentry\t-\t%s", name, sha256Hex([]byte(entries[name]))))
	}
	slices.Sort(record)
	return record
}

// treeMapFixture maps two directories with the localization mapping shape. The first directory is written in
// non-lexical order so the readdir order reaches the jar; the second holds a Bazel transport link and a losing name.
func treeMapFixture(t *testing.T, inputs, format string) layoutParityFixture {
	t.Helper()
	properties, resources := filepath.Join(inputs, "properties"), filepath.Join(inputs, "resources")
	for _, name := range []string{"zeta.properties", "alpha.properties", "mid.properties", "notes.txt", "nested/deep.properties"} {
		writeTestFile(t, filepath.Join(properties, filepath.FromSlash(name)), []byte("properties:"+name))
	}
	writeTestFile(t, filepath.Join(resources, "zeta.properties"), []byte("resources: the first source wins"))
	writeTestFile(t, filepath.Join(inputs, "backing/nested/resource.txt"), []byte("transported"))
	transportLink(t, filepath.Join(resources, "nested/resource.txt"), filepath.Join(inputs, "backing/nested/resource.txt"))
	for _, root := range []string{properties, resources, filepath.Join(inputs, "backing")} {
		chmodTestTree(t, root)
	}
	chmodTestFile(t, filepath.Join(properties, "mid.properties"), 0o640)
	layout := LayoutAssets{Inputs: []Reference{{Artifact: "properties"}, {Artifact: "resources"}}, Assets: []LayoutAsset{{Sources: []int{0, 1},
		Transform: treeMap(LayoutMapping{Pattern: "*.properties", Destination: "messages"}, LayoutMapping{})}}}
	fixture := layoutParityFixture{format: format, root: "resources", layout: layout,
		inputs: Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("properties", properties), directoryArtifact("resources", resources)}}}
	if format == "entries" {
		fixture.root = "localization.jar"
		fixture.hostOrder = true
		fixture.present = []string{"lib/localization.jar"}
	} else {
		fixture.present = []string{"resources/messages/zeta.properties", "resources/messages/mid.properties", "resources/notes.txt",
			"resources/nested/deep.properties", "resources/nested/resource.txt"}
	}
	return fixture
}

// layoutParityFixtures are the layout-assets operations Go executes: one per transform and per archive reader rule,
// with the inputs written under the inputs directory of the fixture.
// The kind gzip-xml-archive has no fixture, because it stays a Kotlin preparation.
var layoutParityFixtures = []struct {
	name  string
	build func(t *testing.T, inputs string) layoutParityFixture
}{
	{"archive-tree from a tar.gz keeps modes, a link, and an empty directory", func(t *testing.T, inputs string) layoutParityFixture {
		// The link latest carries a trailing slash, which the Kotlin writer removed through Path.of.
		archive := filepath.Join(inputs, "assets.tar.gz")
		writeTarGz(t, archive,
			tarTestEntry{name: "top/", mode: 0o755}, tarTestEntry{name: "top/bin/", mode: 0o755},
			tarTestEntry{name: "top/bin/tool", content: "tool", mode: 0o751}, tarTestEntry{name: "top/bin/current", link: "tool"},
			tarTestEntry{name: "top/bin/latest", link: "tool/"},
			tarTestEntry{name: "top/data.txt", content: "data", mode: 0o664}, tarTestEntry{name: "top/private.txt", content: "secret", mode: 0o600},
			tarTestEntry{name: "top/docs/", mode: 0o750}, tarTestEntry{name: "top/docs/readme.md", content: "readme", mode: 0o644},
			tarTestEntry{name: "top/empty/", mode: 0o755})
		return layoutParityFixture{format: "tree", root: "payload",
			layout: LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0}, Transform: archiveTree(1)}}},
			inputs: Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}},
			present: []string{"payload/bin/tool", "payload/bin/current", "payload/bin/latest", "payload/data.txt", "payload/private.txt",
				"payload/docs/readme.md", "payload/empty"}}
	}},
	{"zip creators: Unix and MacOSX carry modes and a link, Windows is flattened", func(t *testing.T, inputs string) layoutParityFixture {
		unix, macos, windows := filepath.Join(inputs, "unix.zip"), filepath.Join(inputs, "macos.zip"), filepath.Join(inputs, "windows.zip")
		writeTestFile(t, unix, storedZipBytes(t,
			zipTestEntry{name: "bin/", mode: 0o775, creator: 3}, zipTestEntry{name: "bin/tool", content: "tool", mode: 0o775, creator: 3},
			zipTestEntry{name: "data.txt", content: "data", mode: 0o664, creator: 3},
			zipTestEntry{name: "bin/current", content: "tool", mode: 0o777, symlink: true, creator: 3}))
		writeTestFile(t, macos, storedZipBytes(t,
			zipTestEntry{name: "bin/tool", content: "tool", mode: 0o755, creator: 19},
			zipTestEntry{name: "bin/current", content: "tool", mode: 0o777, symlink: true, creator: 19}))
		writeTestFile(t, windows, storedZipBytes(t,
			zipTestEntry{name: "bin/tool", content: "tool", mode: 0o755, creator: 10},
			zipTestEntry{name: "bin/current", content: "tool", mode: 0o777, symlink: true, creator: 10}))
		return layoutParityFixture{format: "tree", root: "archives",
			layout: LayoutAssets{Inputs: []Reference{{Artifact: "unix"}, {Artifact: "macos"}, {Artifact: "windows"}}, Assets: []LayoutAsset{
				{Destination: "unix", Sources: []int{0}, Transform: archiveTree(0)},
				{Destination: "macos", Sources: []int{1}, Transform: archiveTree(0)},
				{Destination: "windows", Sources: []int{2}, Transform: archiveTree(0)}}},
			inputs: Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("unix", unix), fileArtifact("macos", macos), fileArtifact("windows", windows)}},
			present: []string{"archives/unix/bin/tool", "archives/unix/bin/current", "archives/unix/data.txt",
				"archives/macos/bin/tool", "archives/macos/bin/current", "archives/windows/bin/tool", "archives/windows/bin/current"}}
	}},
	{"zip.zst modes and a link-typed entry become 0644 files", func(t *testing.T, inputs string) layoutParityFixture {
		archive := filepath.Join(inputs, "libghostty.zip.zst")
		writeZstd(t, archive, storedZipBytes(t,
			zipTestEntry{name: "linux-x64/", mode: 0o755, creator: 3},
			zipTestEntry{name: "linux-x64/libghostty.so", content: "native", mode: 0o755, creator: 3},
			zipTestEntry{name: "linux-x64/libghostty.so.1", content: "libghostty.so", mode: 0o777, symlink: true, creator: 3},
			zipTestEntry{name: "darwin-aarch64/libghostty.dylib", content: "other", mode: 0o755, creator: 3}))
		return layoutParityFixture{format: "tree", root: "terminal",
			layout: LayoutAssets{Inputs: []Reference{{Artifact: "archive"}}, Assets: []LayoutAsset{{Sources: []int{0},
				Transform: archiveTree(0, LayoutMapping{Pattern: "linux-x64/**", StripComponents: 1})}}},
			inputs:  Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("archive", archive)}},
			present: []string{"terminal/libghostty.so", "terminal/libghostty.so.1"}}
	}},
	{"strip and mapping selection with normalized tree modes", func(t *testing.T, inputs string) layoutParityFixture {
		selected, fallback := filepath.Join(inputs, "selected.tar.gz"), filepath.Join(inputs, "fallback.tar.gz")
		writeTarGz(t, selected,
			tarTestEntry{name: "release/", mode: 0o755}, tarTestEntry{name: "release/jcef/", mode: 0o755}, tarTestEntry{name: "release/jcef/lib/", mode: 0o755},
			tarTestEntry{name: "release/jcef/lib/libcef.so", content: "cef", mode: 0o775}, tarTestEntry{name: "release/jcef/README", content: "readme", mode: 0o664},
			tarTestEntry{name: "release/other/", mode: 0o755}, tarTestEntry{name: "release/other/x", content: "x", mode: 0o644})
		writeTarGz(t, fallback,
			tarTestEntry{name: "release/", mode: 0o755}, tarTestEntry{name: "release/docs/", mode: 0o755},
			tarTestEntry{name: "release/docs/guide", content: "guide", mode: 0o644})
		mappings := []LayoutMapping{{Pattern: "jcef/**", StripComponents: 1}, {}}
		return layoutParityFixture{format: "tree", root: "jcef", normalize: true,
			layout: LayoutAssets{Inputs: []Reference{{Artifact: "selected"}, {Artifact: "fallback"}}, Assets: []LayoutAsset{
				{Sources: []int{0}, Transform: archiveTree(1, mappings...)},
				{Destination: "fallback", Sources: []int{1}, Transform: archiveTree(1, mappings...)}}},
			inputs:  Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("selected", selected), fileArtifact("fallback", fallback)}},
			present: []string{"jcef/lib/libcef.so", "jcef/README", "jcef/fallback/docs/guide"}}
	}},
	{"tree-map entries keep the readdir order, the first source, and a transport link", func(t *testing.T, inputs string) layoutParityFixture {
		return treeMapFixture(t, inputs, "entries")
	}},
	{"tree-map tree keeps source modes, the first source, and a transport link", func(t *testing.T, inputs string) layoutParityFixture {
		return treeMapFixture(t, inputs, "tree")
	}},
	{"plain overlay of two trees keeps the first claim and a relative link", func(t *testing.T, inputs string) layoutParityFixture {
		first, second := filepath.Join(inputs, "first"), filepath.Join(inputs, "second")
		writeTestFile(t, filepath.Join(first, "shared.txt"), []byte("first"))
		writeTestFile(t, filepath.Join(first, "a.txt"), []byte("a"))
		if err := os.Symlink("a.txt", filepath.Join(first, "link.txt")); err != nil {
			t.Fatal(err)
		}
		writeTestFile(t, filepath.Join(first, "sub/inner.txt"), []byte("inner"))
		writeTestFile(t, filepath.Join(second, "shared.txt"), []byte("second"))
		writeTestFile(t, filepath.Join(second, "b.txt"), []byte("b"))
		writeTestFile(t, filepath.Join(second, "bin/tool"), []byte("tool"))
		chmodTestTree(t, first)
		chmodTestTree(t, second)
		chmodTestFile(t, filepath.Join(first, "sub/inner.txt"), 0o600)
		chmodTestFile(t, filepath.Join(first, "sub"), 0o750)
		chmodTestFile(t, filepath.Join(second, "bin/tool"), 0o755)
		return layoutParityFixture{format: "tree", root: "overlay",
			layout:  LayoutAssets{Inputs: []Reference{{Artifact: "first"}, {Artifact: "second"}}, Assets: []LayoutAsset{{Sources: []int{0}}, {Sources: []int{1}}}},
			inputs:  Catalogue{Version: Version, Artifacts: []Artifact{directoryArtifact("first", first), directoryArtifact("second", second)}},
			present: []string{"overlay/shared.txt", "overlay/a.txt", "overlay/link.txt", "overlay/sub/inner.txt", "overlay/b.txt", "overlay/bin/tool"}}
	}},
	{"inline text and plain file copies inside a tree", func(t *testing.T, inputs string) layoutParityFixture {
		launcher, tool := filepath.Join(inputs, "launcher"), filepath.Join(inputs, "tool.jar")
		writeTestFile(t, launcher, []byte("launcher"))
		writeTestFile(t, tool, []byte("tool"))
		chmodTestFile(t, launcher, 0o755)
		chmodTestFile(t, tool, 0o755)
		return layoutParityFixture{format: "tree", root: "jbr",
			layout: LayoutAssets{Inputs: []Reference{{Artifact: "launcher"}, {Artifact: "tool"}}, Assets: []LayoutAsset{
				{Destination: "jre-build.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "21.0.7"}},
				{Destination: "bin/launcher", Sources: []int{0}},
				{Destination: "lib/tool.jar", Sources: []int{1}, Mode: 0o644}}},
			inputs:  Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("launcher", launcher), fileArtifact("tool", tool)}},
			present: []string{"jbr/jre-build.txt", "jbr/bin/launcher", "jbr/lib/tool.jar"}}
	}},
}

func layoutInputIDs(layout LayoutAssets) []string {
	var ids []string
	for _, reference := range layout.Inputs {
		if !slices.Contains(ids, reference.Artifact) {
			ids = append(ids, reference.Artifact)
		}
	}
	return ids
}

// TestKotlinLayoutMaterializationMatchesGoTransforms runs the Kotlin layout-assets preparation on every fixture. The
// preparer emits the layout-tree operation or the layout source, and Go executes it from the raw inputs. The test
// requires the same bytes, modes, links, and inventory as the hand-written Go recipe and as the golden of the Kotlin
// materialization.
func TestKotlinLayoutMaterializationMatchesGoTransforms(t *testing.T) {
	kotlinPreparerExecutable(t)
	golden := openKotlinGolden(t, "kotlin-layout")
	const output = "layout-assets:output"
	excluded := false
	for _, definition := range layoutParityFixtures {
		t.Run(definition.name, func(t *testing.T) {
			fixture := definition.build(t, t.TempDir())
			preparation := kotlinPreparation{ID: "layout", Inputs: layoutInputIDs(fixture.layout), Outputs: []string{output}}
			var plan kotlinPlanFile
			var recipe Recipe
			if fixture.format == "tree" {
				operation := kotlinLayoutAssetsOperation(t, "layout", output, "tree", fixture.root, fixture.layout)
				plan = kotlinPlan(t, TreeVersion, "layout-plugin", []kotlinPlanAsset{{Destination: fixture.root, Inputs: []string{output},
					Kind: "tree", ClassPath: &excluded, NormalizeTreeModes: fixture.normalize}}, preparation, operation)
				var mode uint32
				if fixture.normalize {
					mode = 0o644
				}
				recipe = Recipe{Version: TreeVersion, Plugin: plan.Plugin, LayoutSignature: plan.LayoutSignature,
					Assets:     []Asset{{Destination: fixture.root, Producer: "remainder", Kind: "tree", ClassPath: &excluded, NormalizeTreeModes: fixture.normalize}},
					Operations: []Operation{{Kind: "layout-tree", Destination: fixture.root, Mode: mode, Layout: &fixture.layout}}}
			} else {
				jar := "lib/" + fixture.root
				operation := kotlinLayoutAssetsOperation(t, "layout", output, "entries", "", fixture.layout)
				plan = kotlinPlan(t, Version, "layout-plugin", []kotlinPlanAsset{{Destination: jar, Inputs: []string{output},
					Recipe: &kotlinJarRecipe{Sources: []kotlinJarSource{{Input: output, Kind: "prepared", Filter: "prepared"}},
						Writer: kotlinJarWriter{Manifest: "drop", MergeEntities: true}}}}, preparation, operation)
				recipe = Recipe{Version: Version, Plugin: plan.Plugin, LayoutSignature: plan.LayoutSignature,
					Assets: []Asset{{Destination: jar, Producer: "remainder"}},
					Operations: []Operation{{Kind: "jar", Destination: jar, Mode: 0o644, Options: &JarOptions{MergeEntities: true, Directories: "none"},
						Sources: []Source{{Kind: "layout", Manifest: "keep", Layout: &fixture.layout}}}}}
			}
			kotlinRecipe, kotlinCatalogue := kotlinMaterialization(t, plan, fixture.inputs)
			kotlinOutput, kotlinInventory := writeExecution(t, kotlinRecipe, kotlinCatalogue)
			requireSameRecipeRows(t, kotlinRecipe, recipe)
			goOutput, inventory := writeExecution(t, recipe, fixture.inputs)
			if fixture.format == "entries" {
				requireSameJar(t, filepath.Join(kotlinOutput, "lib", fixture.root), filepath.Join(goOutput, "lib", fixture.root))
			}
			kotlinRecord, record := materializationRecord(t, kotlinOutput), materializationRecord(t, goOutput)
			requireEqualRecords(t, "the hand-written Go recipe against the Kotlin-emitted recipe", kotlinRecord, record)
			if !reflect.DeepEqual(kotlinInventory, inventory) {
				t.Fatalf("inventories differ:\n%+v\n%+v", kotlinInventory, inventory)
			}
			requireInventoryMatchesTree(t, goOutput, inventory)
			requireRecordedPaths(t, record, fixture.present)
			goldenRecord := record
			if fixture.hostOrder {
				goldenRecord = jarEntryRecord(t, filepath.Join(goOutput, "lib", fixture.root))
			}
			golden.check(t, definition.name, goldenRecord)
		})
	}
	golden.write(t)
}
