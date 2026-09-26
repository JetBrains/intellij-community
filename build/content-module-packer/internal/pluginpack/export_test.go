package pluginpack

import (
	"os"
	"path/filepath"
	"testing"
)

// The derivation parity test lives in package pluginpack_test, because it imports internal/planfile, which imports
// this package. The names below hand it the fixture helpers and the golden reader of the internal tests.

type (
	KotlinGolden           = kotlinGolden
	KotlinPlanFile         = kotlinPlanFile
	KotlinPlanAsset        = kotlinPlanAsset
	KotlinJarRecipe        = kotlinJarRecipe
	KotlinJarSource        = kotlinJarSource
	KotlinJarWriter        = kotlinJarWriter
	KotlinPreparation      = kotlinPreparation
	KotlinPreparedManifest = kotlinPreparedManifest
)

var (
	OpenKotlinGolden            = openKotlinGolden
	JarEntryRecord              = jarEntryRecord
	Sha256Hex                   = sha256Hex
	ReadAssetRows               = readAssetRows
	KotlinLayoutSignature       = kotlinLayoutSignature
	KotlinModelSignature        = kotlinModelSignature
	KotlinModuleFilterOperation = kotlinModuleFilterOperation
	KotlinLayoutAssetsOperation = kotlinLayoutAssetsOperation
	KotlinJSON                  = kotlinJSON
	WriteExecution              = writeExecution
	MaterializationRecord       = materializationRecord
	RequireEqualRecords         = requireEqualRecords
	RequireInventoryMatchesTree = requireInventoryMatchesTree
	RequireRecordedPaths        = requireRecordedPaths
	WriteTestFile               = writeTestFile
	ReadTestFile                = readTestFile
	ChmodTestTree               = chmodTestTree
	FileArtifact                = fileArtifact
	DirectoryArtifact           = directoryArtifact
	ArchiveTree                 = archiveTree
	TreeMap                     = treeMap
)

// Check compares one fixture with the golden.
func (golden *kotlinGolden) Check(t *testing.T, fixture string, record []string) {
	t.Helper()
	golden.check(t, fixture, record)
}

// ArchiveTestFile writes a jar whose entries hold the names and contents in order.
func ArchiveTestFile(t *testing.T, file string, entries ...[2]string) {
	t.Helper()
	converted := make([]testEntry, 0, len(entries))
	for _, entry := range entries {
		converted = append(converted, testEntry{name: entry[0], data: entry[1]})
	}
	archiveFile(t, file, converted...)
}

// TarGzTestFile writes a tar.gz whose entries hold the names, contents and modes in order. A name with a trailing
// slash is a directory.
func TarGzTestFile(t *testing.T, file string, entries ...TarEntry) {
	t.Helper()
	converted := make([]tarTestEntry, 0, len(entries))
	for _, entry := range entries {
		converted = append(converted, tarTestEntry{name: entry.Name, content: entry.Content, mode: entry.Mode})
	}
	writeTarGz(t, file, converted...)
}

// TarEntry is one tar.gz entry of TarGzTestFile.
type TarEntry struct {
	Name    string
	Content string
	Mode    int64
}

// PluginRemainderPackerExecutable resolves the declared Go packer, or skips the test outside the Bazel target.
func PluginRemainderPackerExecutable(t *testing.T) string {
	t.Helper()
	if *pluginRemainderPacker == "" {
		t.Skip("Run the Bazel pluginpack_test target to include the declared plugin remainder packer")
	}
	executable := *pluginRemainderPacker
	if !filepath.IsAbs(executable) {
		executable = filepath.Join(os.Getenv("TEST_SRCDIR"), filepath.FromSlash(executable))
	}
	return executable
}
