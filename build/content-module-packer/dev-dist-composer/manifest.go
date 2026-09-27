package main

import (
	"cmp"
	"encoding/binary"
	"fmt"
	"slices"
	"strconv"
	"strings"
	"unicode/utf16"

	"github.com/zeebo/xxh3"
	"jetbrains.com/content-module-packer/internal/filemetadata"
)

const (
	componentManifestVersion = 9
	ideFingerprintVersion    = "v5"
	componentFileEntryType   = "component-file"
	pluginClassPath          = "plugins/plugin-classpath.txt"
)

// componentEntry is one entry of a component manifest. A nil pointer is an absent field. The Kotlin
// DevBuildComponentEntry writes every field except Source and Mode.
type componentEntry struct {
	RelativePath  string
	Type          string
	Hash          *int64
	Executable    bool
	SymlinkTarget *string
	Source        *string
	Mode          *int64
}

// componentManifest is the Kotlin DevBuildComponentManifest. A nil MainClass is a component that contributes files
// and nothing else.
type componentManifest struct {
	Version           int64
	Kind              string
	PlatformPrefix    string
	OS                string
	Arch              string
	AdditionalModules []string
	MainClass         *string
	CoreClassPath     []string
	PluginCount       int32
	Entries           []componentEntry
}

// platformNeutral reports whether the component fits every target platform.
func (manifest *componentManifest) platformNeutral() bool {
	return manifest.OS == "" && manifest.Arch == ""
}

func readComponentManifest(file string) (*componentManifest, error) {
	data, err := readJSONFile(file)
	if err != nil {
		return nil, err
	}
	manifest, err := decodeComponentManifest(data)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	if manifest.Version != componentManifestVersion {
		return nil, fmt.Errorf("Unsupported dev-build component manifest version %d in %s", manifest.Version, file)
	}
	for _, entry := range manifest.Entries {
		if err := validateDevBuildEntryMode(entry); err != nil {
			return nil, err
		}
	}
	return manifest, nil
}

func decodeComponentManifest(data []byte) (*componentManifest, error) {
	object, err := decodeJSONObject(data, "org.jetbrains.intellij.build.dev.DevBuildComponentManifest",
		"version", "kind", "platformPrefix", "os", "arch", "additionalModules", "mainClass", "coreClassPath", "pluginCount", "entries")
	if err != nil {
		return nil, err
	}
	manifest := &componentManifest{Version: componentManifestVersion}
	if version, err := object.integer("version", 32, false); err != nil {
		return nil, err
	} else if version != nil {
		manifest.Version = *version
	}
	for _, field := range []struct {
		name        string
		destination *string
	}{{"kind", &manifest.Kind}, {"platformPrefix", &manifest.PlatformPrefix}, {"os", &manifest.OS}, {"arch", &manifest.Arch}} {
		if *field.destination, err = object.string(field.name); err != nil {
			return nil, err
		}
	}
	if manifest.AdditionalModules, err = object.stringList("additionalModules", true); err != nil {
		return nil, err
	}
	if manifest.MainClass, err = object.optionalString("mainClass", true); err != nil {
		return nil, err
	}
	if manifest.CoreClassPath, err = object.stringList("coreClassPath", true); err != nil {
		return nil, err
	}
	if pluginCount, err := object.integer("pluginCount", 32, false); err != nil {
		return nil, err
	} else if pluginCount != nil {
		manifest.PluginCount = int32(*pluginCount)
	}
	items, err := object.objectList("entries")
	if err != nil {
		return nil, err
	}
	manifest.Entries = make([]componentEntry, 0, len(items))
	for _, item := range items {
		entry, err := decodeComponentEntry(item)
		if err != nil {
			return nil, err
		}
		manifest.Entries = append(manifest.Entries, entry)
	}
	return manifest, nil
}

func decodeComponentEntry(data []byte) (entry componentEntry, err error) {
	object, err := decodeJSONObject(data, "org.jetbrains.intellij.build.dev.DevBuildComponentEntry",
		"relativePath", "type", "hash", "executable", "symlinkTarget", "source", "mode")
	if err != nil {
		return entry, err
	}
	if entry.RelativePath, err = object.string("relativePath"); err != nil {
		return entry, err
	}
	if entry.Type, err = object.string("type"); err != nil {
		return entry, err
	}
	if entry.Hash, err = object.integer("hash", 64, true); err != nil {
		return entry, err
	}
	if entry.Executable, err = object.boolean("executable"); err != nil {
		return entry, err
	}
	if entry.SymlinkTarget, err = object.optionalString("symlinkTarget", false); err != nil {
		return entry, err
	}
	if entry.Source, err = object.optionalString("source", false); err != nil {
		return entry, err
	}
	entry.Mode, err = object.integer("mode", 32, true)
	return entry, err
}

func validateDevBuildEntryMode(entry componentEntry) error {
	if entry.Type == "directory" {
		if entry.Hash != nil || entry.Source != nil || entry.SymlinkTarget != nil || entry.Executable || !validMode(entry.Mode) {
			return fmt.Errorf("Invalid directory entry '%s'", entry.RelativePath)
		}
		return nil
	}
	if entry.Hash == nil {
		return fmt.Errorf("Dev-build component entry '%s' requires a hash", entry.RelativePath)
	}
	if entry.Mode == nil {
		return nil
	}
	mode := *entry.Mode
	if !validMode(entry.Mode) || entry.SymlinkTarget != nil || entry.Type != componentFileEntryType || entry.Executable != (mode&0o111 != 0) {
		return fmt.Errorf("Dev-build component entry '%s' has an invalid or conflicting file mode: %d", entry.RelativePath, mode)
	}
	return nil
}

func validMode(mode *int64) bool {
	return mode != nil && *mode >= 0 && *mode <= 0o777
}

// conventionalMode is the mode an entry without an exact mode gets.
func conventionalMode(executable bool) int64 {
	if executable {
		return 0o755
	}
	return 0o644
}

// fingerprintEntry is the Kotlin IdeFingerprintEntry.
type fingerprintEntry struct {
	relativePath string
	entryType    string
	hash         int64
	executable   bool
}

// computeIdeFingerprintFromComponents computes the IDE fingerprint of the components. A nil declaredModules means that
// the caller has no declaration, and then the sum over the components applies.
func computeIdeFingerprintFromComponents(components []*componentManifest, pluginClasspathFile string, declaredModules *[]string) (string, error) {
	if len(components) == 0 {
		return "", fmt.Errorf("At least one dev-build component manifest is required")
	}
	first := components[0]
	var mainClass *string
	platform := first
	platformFound := false
	var modules, coreClassPath []string
	for _, component := range components {
		if mainClass == nil {
			mainClass = component.MainClass
		}
		if !platformFound && !component.platformNeutral() {
			platform, platformFound = component, true
		}
		modules = append(modules, component.AdditionalModules...)
		coreClassPath = append(coreClassPath, component.CoreClassPath...)
	}
	if mainClass == nil {
		return "", fmt.Errorf("No dev-build component declares an IDE main class")
	}
	if declaredModules != nil {
		modules = *declaredModules
	} else {
		modules = distinct(modules)
	}
	var entries []fingerprintEntry
	for _, component := range components {
		for _, entry := range component.Entries {
			entries = append(entries, fingerprintEntry{entry.RelativePath, entry.Type, valueOrZero(entry.Hash), entry.Executable})
		}
	}
	for _, component := range components {
		for _, entry := range component.Entries {
			if err := validateDevBuildEntryMode(entry); err != nil {
				return "", err
			}
			if entry.Mode == nil {
				continue
			}
			if entry.Type == "directory" {
				entries = append(entries, fingerprintEntry{entry.RelativePath, "directory-mode", *entry.Mode, false})
			} else if *entry.Mode != conventionalMode(entry.Executable) {
				entries = append(entries, fingerprintEntry{entry.RelativePath, "file-mode", *entry.Mode, false})
			}
		}
	}
	entries = append(entries,
		fingerprintEntry{"<dev-ide-config>", "launch-metadata",
			computeDevBuildLaunchMetadataHash(first.PlatformPrefix, platform.OS, platform.Arch, *mainClass, modules), false},
		fingerprintEntry{"core-classpath.txt", "generated-core-classpath",
			int64(xxh3.HashString(strings.Join(orderCoreClasspathEntries(coreClassPath), "\n"))), false},
	)
	if pluginClasspathFile != "" {
		hash, err := filemetadata.HashFile(pluginClasspathFile)
		if err != nil {
			return "", err
		}
		entries = append(entries, fingerprintEntry{pluginClassPath, "generated-plugin-classpath", hash, false})
	}
	return computeIdeFingerprint(entries), nil
}

func computeDevBuildLaunchMetadataHash(platformPrefix, os, arch, mainClass string, additionalModules []string) int64 {
	var stream hashStream
	stream.putString("dev-launch-v1")
	stream.putString(platformPrefix)
	stream.putString(os)
	stream.putString(arch)
	stream.putString(mainClass)
	stream.putInt(int32(len(additionalModules)))
	for _, module := range additionalModules {
		stream.putString(module)
	}
	return int64(xxh3.Hash(stream.data))
}

// computeIdeFingerprint is the Kotlin computeIdeFingerprint (IdeFingerprint.kt). It hashes the sorted entries and
// renders the unsigned hash in base 36.
func computeIdeFingerprint(entries []fingerprintEntry) string {
	sorted := slices.Clone(entries)
	slices.SortStableFunc(sorted, func(first, second fingerprintEntry) int {
		if result := compareUTF16(first.relativePath, second.relativePath); result != 0 {
			return result
		}
		if result := compareUTF16(first.entryType, second.entryType); result != 0 {
			return result
		}
		if result := cmp.Compare(first.hash, second.hash); result != 0 {
			return result
		}
		return cmp.Compare(boolInt(first.executable), boolInt(second.executable))
	})
	var stream hashStream
	stream.putString(ideFingerprintVersion)
	stream.putInt(int32(len(sorted)))
	for _, entry := range sorted {
		stream.putString(entry.relativePath)
		stream.putString(entry.entryType)
		stream.putLong(entry.hash)
		stream.putInt(boolInt(entry.executable))
	}
	return ideFingerprintVersion + ":" + strconv.FormatUint(xxh3.Hash(stream.data), 36)
}

// hashStream collects the bytes that a hash4j HashStream feeds to xxh3. The xxh3 value of a stream is the xxh3 value
// of the concatenated bytes.
type hashStream struct {
	data []byte
}

// putString feeds the UTF-16 code units of value, two little-endian bytes each, and then the unit count.
func (stream *hashStream) putString(value string) {
	count := 0
	for _, character := range value {
		if character >= 0x10000 {
			high, low := utf16.EncodeRune(character)
			stream.data = binary.LittleEndian.AppendUint16(stream.data, uint16(high))
			stream.data = binary.LittleEndian.AppendUint16(stream.data, uint16(low))
			count += 2
		} else {
			stream.data = binary.LittleEndian.AppendUint16(stream.data, uint16(character))
			count++
		}
	}
	stream.putInt(int32(count))
}

func (stream *hashStream) putInt(value int32) {
	stream.data = binary.LittleEndian.AppendUint32(stream.data, uint32(value))
}

func (stream *hashStream) putLong(value int64) {
	stream.data = binary.LittleEndian.AppendUint64(stream.data, uint64(value))
}

func boolInt(value bool) int32 {
	if value {
		return 1
	}
	return 0
}

func valueOrZero(value *int64) int64 {
	if value == nil {
		return 0
	}
	return *value
}

// distinct keeps the first occurrence of each value, as a Kotlin LinkedHashSet does.
func distinct(values []string) []string {
	seen := make(map[string]bool, len(values))
	result := make([]string, 0, len(values))
	for _, value := range values {
		if !seen[value] {
			seen[value] = true
			result = append(result, value)
		}
	}
	return result
}
