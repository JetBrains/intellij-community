// Package pluginclasspath writes the record of one plugin in `plugins/plugin-classpath.txt`.
//
// The dev-dist collector writes the record for the packed shape. The plugin remainder packer writes it for a plan
// whose preparation Go executes. Both call [Record], so one algorithm serves both shapes.
package pluginclasspath

import (
	"encoding/binary"
	"fmt"
	"math"
	"path"
	"slices"
	"strings"
	"unicode"
	"unicode/utf16"
)

// Record writes one plugin's record of `plugins/plugin-classpath.txt`.
//
// The layout is the Java `DataOutputStream` form of `writePluginClassPathEntryData` (`orderedAssets.kt`):
// `writeShort(count)`, `writeUTF(pluginDirName)`, `writeInt(len(descriptor))` and the descriptor bytes, then
// `writeUTF(relativePath)` per classpath jar. `writeUTF` is a big-endian uint16 length and Java's modified UTF-8.
// jars are the classpath jars relative to the plugin directory in their declared order. The record keeps the distinct
// jars in the order [Order] gives them.
func Record(pluginDirName string, descriptor []byte, jars []string) ([]byte, error) {
	names := Order(pluginDirName, jars)
	if len(names) > math.MaxUint16 {
		return nil, fmt.Errorf("plugin %s has too many classpath jars: %d", pluginDirName, len(names))
	}
	data := binary.BigEndian.AppendUint16(nil, uint16(len(names)))
	data, err := appendJavaUTF(data, pluginDirName)
	if err != nil {
		return nil, err
	}
	data = binary.BigEndian.AppendUint32(data, uint32(len(descriptor)))
	data = append(data, descriptor...)
	for _, name := range names {
		if data, err = appendJavaUTF(data, name); err != nil {
			return nil, err
		}
	}
	return data, nil
}

func appendJavaUTF(data []byte, value string) ([]byte, error) {
	encoded := ModifiedUTF8(value)
	if len(encoded) > math.MaxUint16 {
		return nil, fmt.Errorf("plugin classpath value is too long: %q", value)
	}
	data = binary.BigEndian.AppendUint16(data, uint16(len(encoded)))
	return append(data, encoded...), nil
}

// Order is `writeOrderedPluginClassPathEntry` (`orderedAssets.kt`): the distinct jars, and
// [PutMoreLikelyPluginJarsFirst] when there is more than one. The input is not modified.
func Order(pluginDirName string, jars []string) []string {
	names := make([]string, 0, len(jars))
	seen := make(map[string]bool, len(jars))
	for _, name := range jars {
		if seen[name] {
			continue
		}
		seen[name] = true
		names = append(names, name)
	}
	if len(names) > 1 {
		PutMoreLikelyPluginJarsFirst(pluginDirName, names)
	}
	return names
}

// PutMoreLikelyPluginJarsFirst is the comparator of the same name in `com.intellij.platform.util` (`plugin.kt`).
// Java's `Collections.sort` is stable, so the sort here is stable too.
func PutMoreLikelyPluginJarsFirst(pluginDirName string, files []string) {
	slices.SortStableFunc(files, func(first, second string) int {
		return compareLikelyPluginJars(pluginDirName, path.Base(first), path.Base(second))
	})
}

func compareLikelyPluginJars(pluginDirName, o1Name, o2Name string) int {
	// a) a `resources*` jar goes last
	o1StartsWithResources := strings.HasPrefix(o1Name, "resources")
	o2StartsWithResources := strings.HasPrefix(o2Name, "resources")
	if o2StartsWithResources != o1StartsWithResources {
		return orderLast(o2StartsWithResources)
	}
	// b) a versioned library name such as `gson-2.8.0.jar` goes last
	o1IsVersioned := fileNameIsLikeVersionedLibraryName(o1Name)
	o2IsVersioned := fileNameIsLikeVersionedLibraryName(o2Name)
	if o2IsVersioned != o1IsVersioned {
		return orderLast(o2IsVersioned)
	}
	// c) a name that starts with the plugin directory name goes first
	o1StartsWithNeededName := startsWithIgnoreCase(o1Name, pluginDirName)
	o2StartsWithNeededName := startsWithIgnoreCase(o2Name, pluginDirName)
	if o2StartsWithNeededName != o1StartsWithNeededName {
		return orderFirst(o2StartsWithNeededName)
	}
	o1EndsWithIdea := strings.HasSuffix(o1Name, "-idea.jar")
	o2EndsWithIdea := strings.HasSuffix(o2Name, "-idea.jar")
	if o2EndsWithIdea != o1EndsWithIdea {
		return orderFirst(o2EndsWithIdea)
	}
	o1IsDbPlugin := o1Name == "database-plugin.jar"
	o2IsDbPlugin := o2Name == "database-plugin.jar"
	if o2IsDbPlugin != o1IsDbPlugin {
		return orderFirst(o2IsDbPlugin)
	}
	// d) the shorter name goes first; Kotlin measures a name in UTF-16 units
	return javaLength(o1Name) - javaLength(o2Name)
}

// orderLast returns the comparison for a trait that puts a name last, given whether the second name has it.
func orderLast(secondHasTrait bool) int {
	if secondHasTrait {
		return -1
	}
	return 1
}

// orderFirst returns the comparison for a trait that puts a name first, given whether the second name has it.
func orderFirst(secondHasTrait bool) int {
	if secondHasTrait {
		return 1
	}
	return -1
}

// fileNameIsLikeVersionedLibraryName is the private function of the same name in `plugin.kt`: the text after the last
// `-` starts with a digit, or with `m` or `M` and then a digit.
func fileNameIsLikeVersionedLibraryName(name string) bool {
	runes := []rune(name)
	index := -1
	for position, character := range runes {
		if character == '-' {
			index = position
		}
	}
	if index == -1 || index+1 >= len(runes) {
		return false
	}
	next := runes[index+1]
	if unicode.IsDigit(next) {
		return true
	}
	return (next == 'm' || next == 'M') && index+2 < len(runes) && unicode.IsDigit(runes[index+2])
}

func startsWithIgnoreCase(name, prefix string) bool {
	runes := []rune(name)
	prefixRunes := []rune(prefix)
	if len(runes) < len(prefixRunes) {
		return false
	}
	return strings.EqualFold(string(runes[:len(prefixRunes)]), prefix)
}

func javaLength(value string) int {
	return len(utf16.Encode([]rune(value)))
}

// ModifiedUTF8 encodes a value the way Java's `DataOutputStream.writeUTF` does: per UTF-16 unit, with NUL as two
// bytes and a surrogate as three bytes.
func ModifiedUTF8(value string) []byte {
	var encoded []byte
	for _, unit := range utf16.Encode([]rune(value)) {
		switch {
		case unit != 0 && unit <= 0x7f:
			encoded = append(encoded, byte(unit))
		case unit <= 0x7ff:
			encoded = append(encoded, 0xc0|byte(unit>>6), 0x80|byte(unit&0x3f))
		default:
			encoded = append(encoded, 0xe0|byte(unit>>12), 0x80|byte(unit>>6&0x3f), 0x80|byte(unit&0x3f))
		}
	}
	return encoded
}
