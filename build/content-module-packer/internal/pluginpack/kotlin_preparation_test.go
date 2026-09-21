package pluginpack

import (
	"archive/zip"
	"bytes"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"hash/crc32"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
	"unicode/utf16"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/nativelib"
	"jetbrains.com/content-module-packer/internal/xxh3"
)

var pluginRemainderPacker = flag.String("plugin-remainder-packer", "", "The declared Go plugin remainder executable")
var sqliteNativeJar = flag.String("sqlite-native-jar", "", "The org.sqlite:native jar the native-select parity test selects from")

// The parity tests below pack a hand-written Go recipe and compare the result with the golden under testdata. A
// golden is the materialization of every fixture by the deleted Kotlin preparer, frozen under
// testdata/<name>-golden-<date>.txt before the Kotlin materialization stopped. The goldens are read-only: the Go
// packer is the only producer left, so a re-recording would compare Go with itself.

// kotlinPlanFile is the plan file the derivation fixtures write, in the shape PluginPackingProjectionEncoding.kt
// emits. The fixtures compute the layout signature and every operation signature the way
// pluginPackingLayoutSignature and devPluginPreparationOperationSignature do, so a fixture is a plan file
// plugin-model-tool --check would accept. A zero Mode is the default 420. An asset with a Module is the compact
// form of a module's own jar.
type kotlinPlanFile struct {
	Version          int                 `json:"version"`
	Plugin           string              `json:"plugin"`
	Variant          string              `json:"variant"`
	LayoutSignature  string              `json:"layoutSignature"`
	Assets           []kotlinPlanAsset   `json:"assets"`
	Preparations     []kotlinPreparation `json:"preparations,omitempty"`
	PreparationRoots []string            `json:"preparationRoots,omitempty"`
	Operations       []json.RawMessage   `json:"operations,omitempty"`
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
			if len(transform.Excludes) != 0 {
				text += `,"excludes":` + kotlinJSON(t, transform.Excludes)
			}
			if len(transform.DirectoryExcludes) != 0 {
				text += `,"directoryExcludes":` + kotlinJSON(t, transform.DirectoryExcludes)
			}
			if len(transform.Includes) != 0 {
				text += `,"includes":` + kotlinJSON(t, transform.Includes)
			}
			if len(transform.Executables) != 0 {
				text += `,"executables":` + kotlinJSON(t, transform.Executables)
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
// Kotlin generator wrote for the filtered demo projection.
func TestKotlinSignatureHelpersReproduceTheFixtureConstants(t *testing.T) {
	filter := kotlinModuleFilterOperation(t, "filter", "raw", "filtered", "drop", []string{"drop/**"})
	if got := kotlinModelSignature(filter); got != "4j4kth710fglswfsh1vosdrg4" {
		t.Fatalf("model signature differs: %s", got)
	}
	filtered := kotlinPlanFile{Version: Version, Plugin: "filtered-plugin", Variant: "linux",
		Assets: []kotlinPlanAsset{{Destination: "lib/main.jar", Inputs: []string{"filtered"}, Recipe: &kotlinJarRecipe{
			Sources: []kotlinJarSource{{Input: "filtered", Kind: "prepared", Filter: "prepared"}}, Writer: kotlinJarWriter{Manifest: "drop"}}}},
		Preparations: []kotlinPreparation{{ID: "filter", Inputs: []string{"raw"}, Outputs: []string{"filtered"}, ModelSignature: kotlinModelSignature(filter)}}}
	if got := kotlinLayoutSignature(filtered); got != "ardmbz5a2oe6vf6br6theud68" {
		t.Fatalf("layout signature of the filtered projection differs: %s", got)
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
// testdata/<name>-<label>.txt. The label states the recording date.
type kotlinGolden struct {
	name     string
	label    string
	fixtures map[string][]string
}

func openKotlinGolden(t *testing.T, name string) *kotlinGolden {
	t.Helper()
	golden := &kotlinGolden{name: name, fixtures: make(map[string][]string)}
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

// check compares one fixture with the golden.
func (golden *kotlinGolden) check(t *testing.T, fixture string, record []string) {
	t.Helper()
	want, present := golden.fixtures[fixture]
	if !present {
		t.Fatalf("fixture %q has no golden in testdata/%s-%s.txt", fixture, golden.name, golden.label)
	}
	requireEqualRecords(t, fixture+" against the golden", want, record)
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

// TestKotlinModuleFilterMaterializationMatchesGoExcludes packs the hand-written Go recipe of a module-filter
// operation, the archive source with the excludes. It requires the jar to be identical to the golden of the Kotlin
// materialization.
func TestKotlinModuleFilterMaterializationMatchesGoExcludes(t *testing.T) {
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
				recipe := Recipe{Version: Version, Plugin: plan.Plugin, LayoutSignature: plan.LayoutSignature,
					Assets: []Asset{{Destination: "lib/main.jar", Producer: "remainder"}},
					Operations: []Operation{{Kind: "jar", Destination: "lib/main.jar", Mode: 0o644, Options: &JarOptions{MergeEntities: true, Directories: "none"},
						Sources: []Source{{Kind: "archive", Input: &Reference{Artifact: "raw"}, Filter: "module", Manifest: manifest, Excludes: testCase.excludes}}}}}
				output, inventory := writeExecution(t, recipe, inputs)
				record := materializationRecord(t, output)
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
// entries fixture names its jar; a file fixture names its file. present lists the output paths the fixture exists
// for. hostOrder marks a jar whose entry order follows the readdir order of the host: the live comparison checks the
// order, the golden does not. decompress marks a jar of gzip entries: the golden holds the entry names and the
// decompressed payload, because the Go deflater writes other bytes than the JDK deflater.
type layoutParityFixture struct {
	format     string
	root       string
	normalize  bool
	hostOrder  bool
	decompress bool
	layout     LayoutAssets
	inputs     Catalogue
	present    []string
}

// jarEntryRecord lists the entries of a jar by name with their content digest, sorted by name. The generated index
// follows the entry order and is left out; jarpack's own tests guard it. With decompress, the digest is over the
// decompressed gzip payload of each entry.
func jarEntryRecord(t *testing.T, jar string, decompress bool) []string {
	t.Helper()
	names, entries := readArchive(t, jar)
	record := make([]string, 0, len(names))
	for _, name := range names {
		if name == "__index__" {
			continue
		}
		content := entries[name]
		if decompress {
			content = readGzip(t, content)
		}
		record = append(record, fmt.Sprintf("%s\tentry\t-\t%s", name, sha256Hex([]byte(content))))
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

// layoutParityFixtures are the layout-assets operations Go executes: one per transform, per archive reader rule, and
// per format, with the inputs written under the inputs directory of the fixture.
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
	{"gzip-xml-archive entries hold the XML of each archive in central-directory order", func(t *testing.T, inputs string) layoutParityFixture {
		// The first archive has a Unix directory entry and its files out of name order; the second repeats a name that
		// the first archive already claimed.
		first, second := filepath.Join(inputs, "dialects.jar"), filepath.Join(inputs, "more.zip")
		writeZip(t, first, zipTestEntry{name: "dialects/", creator: 3, mode: 0o755},
			zipTestEntry{name: "dialects/zeta.xml", content: "<zeta/>"}, zipTestEntry{name: "dialects/alpha.xml", content: "<alpha/>"},
			zipTestEntry{name: "shared.xml", content: "<first/>"})
		writeZip(t, second, zipTestEntry{name: "shared.xml", content: "<second/>"}, zipTestEntry{name: "beta.xml", content: "<beta/>"})
		return layoutParityFixture{format: "entries", root: "dialects.jar", decompress: true,
			layout: LayoutAssets{Inputs: []Reference{{Artifact: "first"}, {Artifact: "second"}}, Assets: []LayoutAsset{
				{Destination: "resources", Sources: []int{0, 1}, Transform: &LayoutTransform{Kind: "gzip-xml-archive"}}}},
			inputs:  Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("first", first), fileArtifact("second", second)}},
			present: []string{"lib/dialects.jar"}}
	}},
	{"a layout file holds inline text", func(t *testing.T, inputs string) layoutParityFixture {
		return layoutParityFixture{format: "file", root: "jre-build.txt",
			layout: LayoutAssets{Inputs: []Reference{}, Assets: []LayoutAsset{
				{Destination: "jre-build.txt", Transform: &LayoutTransform{Kind: "inline-text", Text: "21.0.7"}}}},
			inputs:  Catalogue{Version: Version, Artifacts: []Artifact{}},
			present: []string{"jre-build.txt"}}
	}},
	{"a layout file copies one file at the declared mode", func(t *testing.T, inputs string) layoutParityFixture {
		build := filepath.Join(inputs, "build.txt")
		writeTestFile(t, build, []byte("21.0.7"))
		chmodTestFile(t, build, 0o755)
		return layoutParityFixture{format: "file", root: "bin/jre-build.txt",
			layout:  LayoutAssets{Inputs: []Reference{{Artifact: "build"}}, Assets: []LayoutAsset{{Destination: "bin/jre-build.txt", Sources: []int{0}}}},
			inputs:  Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("build", build)}},
			present: []string{"bin/jre-build.txt"}}
	}},
}

func layoutInputIDs(layout LayoutAssets) []string {
	ids := []string{}
	for _, reference := range layout.Inputs {
		if !slices.Contains(ids, reference.Artifact) {
			ids = append(ids, reference.Artifact)
		}
	}
	return ids
}

// TestKotlinLayoutMaterializationMatchesGoTransforms packs the hand-written Go recipe of every layout-assets fixture:
// the layout-tree operation, the layout-file operation, or the layout source. Go executes it from the raw inputs. The
// test requires the same bytes, modes, and links as the golden of the Kotlin materialization.
func TestKotlinLayoutMaterializationMatchesGoTransforms(t *testing.T) {
	golden := openKotlinGolden(t, "kotlin-layout")
	const output = "layout-assets:output"
	excluded := false
	for _, definition := range layoutParityFixtures {
		t.Run(definition.name, func(t *testing.T) {
			fixture := definition.build(t, t.TempDir())
			preparation := kotlinPreparation{ID: "layout", Inputs: layoutInputIDs(fixture.layout), Outputs: []string{output}}
			var plan kotlinPlanFile
			var recipe Recipe
			if fixture.format == "file" {
				operation := kotlinLayoutAssetsOperation(t, "layout", output, "file", fixture.root, fixture.layout)
				plan = kotlinPlan(t, Version, "layout-plugin", []kotlinPlanAsset{{Destination: fixture.root, Inputs: []string{output}, ClassPath: &excluded}}, preparation, operation)
				recipe = Recipe{Version: Version, Plugin: plan.Plugin, LayoutSignature: plan.LayoutSignature,
					Assets:     []Asset{{Destination: fixture.root, Producer: "remainder", ClassPath: &excluded}},
					Operations: []Operation{{Kind: "layout-file", Destination: fixture.root, Mode: 0o644, Layout: &fixture.layout}}}
			} else if fixture.format == "tree" {
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
			goOutput, inventory := writeExecution(t, recipe, fixture.inputs)
			record := materializationRecord(t, goOutput)
			requireInventoryMatchesTree(t, goOutput, inventory)
			requireRecordedPaths(t, record, fixture.present)
			goldenRecord := record
			if fixture.hostOrder || fixture.decompress {
				goldenRecord = jarEntryRecord(t, filepath.Join(goOutput, "lib", fixture.root), fixture.decompress)
			}
			golden.check(t, definition.name, goldenRecord)
		})
	}
}

// sqliteNativeJarPath is the declared org.sqlite:native jar, the one native archive of intellij.platform.vcs.plugin.
func sqliteNativeJarPath(t *testing.T) string {
	t.Helper()
	if *sqliteNativeJar == "" {
		t.Skip("Run the Bazel pluginpack_test target to include the declared sqlite native jar")
	}
	jar := *sqliteNativeJar
	if !filepath.IsAbs(jar) {
		jar = filepath.Join(os.Getenv("TEST_SRCDIR"), filepath.FromSlash(jar))
	}
	return jar
}

// nativeSelectVariants are the six dev-dist platforms, the variants the vcs plan keeps one record for.
var nativeSelectVariants = []string{"darwin_aarch64", "darwin_x64", "linux_aarch64", "linux_x64", "windows_aarch64", "windows_x64"}

// TestNativeSelectMatchesTheFrozenKotlinSelection packs the sqlite native jar of intellij.platform.vcs.plugin for every
// platform the way the planfile package compiles a native-select operation: the jar keeps the archive with its native
// entries reserved, and the distribution tree lib/native holds the entries of the platform. The golden is the tree
// the Kotlin DevPluginPresignedNativeRecipeRuntime wrote and the jar entries it left, frozen from the dev-dist build of
// each platform before the Kotlin runtime was deleted.
func TestNativeSelectMatchesTheFrozenKotlinSelection(t *testing.T) {
	jar := sqliteNativeJarPath(t)
	golden := openKotlinGolden(t, "kotlin-native-select")
	const jarDestination, treeDestination = "lib/intellij.libraries.sqlite.native.jar", "lib/native"
	excluded := false
	for _, variant := range nativeSelectVariants {
		t.Run(variant, func(t *testing.T) {
			family, arch, err := nativelib.ParseVariant(variant)
			if err != nil {
				t.Fatal(err)
			}
			native := &Reference{Artifact: "native"}
			recipe := Recipe{Version: ScopedVersion, Plugin: "intellij.platform.vcs.plugin", LayoutSignature: "native-select-" + variant,
				Assets: []Asset{
					{Destination: jarDestination, Producer: "remainder"},
					{Destination: treeDestination, Producer: "remainder", Kind: "tree", ClassPath: &excluded, Scope: DistributionScope}},
				Operations: []Operation{
					{Kind: "jar", Destination: jarDestination, Mode: 0o644, Options: &JarOptions{MergeEntities: true, Directories: "none"},
						Sources: []Source{{Kind: "archive", Input: native, Filter: "library", Manifest: "keep", ReserveNatives: true}}},
					{Kind: "native-tree", Destination: treeDestination, Scope: DistributionScope, Input: native,
						Native: &NativeTarget{OS: string(family), Arch: string(arch)}}}}
			output, inventory := writeExecution(t, recipe, Catalogue{Version: Version, Artifacts: []Artifact{fileArtifact("native", jar)}})
			requireInventoryMatchesTree(t, output, inventory)
			treeRoot := TransportDestination(ScopedVersion, DistributionScope, treeDestination)
			var record []string
			for _, line := range materializationRecord(t, output) {
				if strings.HasPrefix(line, treeRoot) {
					record = append(record, line)
				}
			}
			requireRecordedPaths(t, record, []string{treeRoot})
			if len(record) != 3 {
				t.Fatalf("the sqlite jar holds one native per platform, the tree holds %d entries:\n%s", len(record), strings.Join(record, "\n"))
			}
			record = append(record, jarEntryRecord(t, filepath.Join(output, filepath.FromSlash(jarDestination)), false)...)
			for _, line := range record {
				if nativelib.IsNativeEntry(strings.Split(line, "\t")[0]) && strings.Contains(line, "\tentry\t") {
					t.Fatalf("the jar kept a native entry: %s", line)
				}
			}
			golden.check(t, variant, record)
		})
	}
}
