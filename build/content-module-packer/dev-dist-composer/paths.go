package main

import (
	"fmt"
	"path"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"unicode/utf8"

	"golang.org/x/text/cases"
	"golang.org/x/text/language"
	"golang.org/x/text/unicode/norm"
)

// The Kotlin composer resolves paths through java.nio.file.Path. The helpers in this file reproduce the parts of that
// behavior the composer depends on.

const windows = runtime.GOOS == "windows"

// javaPath returns the string that `Path.of(value).toString()` gives. Java refuses a NUL character. It removes a
// repeated separator and a trailing separator. On Windows, it also changes each slash to a backslash.
func javaPath(value string) (string, error) {
	if strings.IndexByte(value, 0) >= 0 {
		return "", fmt.Errorf("Nul character not allowed: %s", value)
	}
	separator := byte('/')
	if windows {
		separator = '\\'
		value = strings.ReplaceAll(value, "/", `\`)
	}
	result := make([]byte, 0, len(value))
	for index := 0; index < len(value); index++ {
		// A Windows UNC path starts with two separators, and Java keeps both.
		if value[index] == separator && len(result) > 0 && result[len(result)-1] == separator && !(windows && index == 1) {
			continue
		}
		result = append(result, value[index])
	}
	if len(result) > 1 && result[len(result)-1] == separator && !(windows && len(result) == 3 && result[1] == ':') {
		result = result[:len(result)-1]
	}
	return string(result), nil
}

// hasJavaPathSpelling reports whether `Path.of(value)` keeps the spelling of value, with native or slash separators.
func hasJavaPathSpelling(value string) (bool, error) {
	parsed, err := javaPath(value)
	if err != nil {
		return false, err
	}
	return parsed == value || filepath.ToSlash(parsed) == value, nil
}

// hasDotName reports whether a name element of the Java path is "." or "..".
func hasDotName(javaPathValue string) bool {
	for _, name := range strings.Split(filepath.ToSlash(javaPathValue), "/") {
		if name == "." || name == ".." {
			return true
		}
	}
	return false
}

// absolutePath returns `Path.of(value).toAbsolutePath().normalize()`.
func absolutePath(value string) (string, error) {
	parsed, err := javaPath(value)
	if err != nil {
		return "", err
	}
	return filepath.Abs(parsed)
}

// realPath returns `Path.of(value).toRealPath()`, which follows every symbolic link.
func realPath(value string) (string, error) {
	absolute, err := absolutePath(value)
	if err != nil {
		return "", err
	}
	return evalSymlinks(absolute)
}

// pathStartsWith is `Path.startsWith` for two normalized paths. It compares whole name elements.
func pathStartsWith(value, prefix string) bool {
	if value == prefix {
		return true
	}
	if strings.HasSuffix(prefix, string(filepath.Separator)) {
		return strings.HasPrefix(value, prefix)
	}
	return strings.HasPrefix(value, prefix+string(filepath.Separator))
}

// resolveRelative is `base.resolve(relativePath)` for a validated relative path in slash form.
func resolveRelative(base, relativePath string) string {
	if relativePath == "" {
		return base
	}
	return filepath.Join(base, filepath.FromSlash(relativePath))
}

// substringBeforeLastSlash is Kotlin `substringBeforeLast('/', "")`.
func substringBeforeLastSlash(value string) string {
	if index := strings.LastIndexByte(value, '/'); index >= 0 {
		return value[:index]
	}
	return ""
}

// compareUTF16 is Java `String.compareTo`. It compares UTF-16 code units, so a supplementary character sorts before
// U+E000 to U+FFFF.
func compareUTF16(first, second string) int {
	for first != "" && second != "" {
		firstRune, firstSize := utf8.DecodeRuneInString(first)
		secondRune, secondSize := utf8.DecodeRuneInString(second)
		if firstRune != secondRune {
			if utf16Order(firstRune) < utf16Order(secondRune) {
				return -1
			}
			return 1
		}
		first, second = first[firstSize:], second[secondSize:]
	}
	switch {
	case first == second:
		return 0
	case first == "":
		return -1
	default:
		return 1
	}
}

func utf16Order(value rune) rune {
	if value >= 0xE000 && value <= 0xFFFF {
		return value + utf8.MaxRune + 1
	}
	return value
}

// Java `lowercase(Locale.ROOT)` and `uppercase(Locale.ROOT)` use the full Unicode case mappings with the final sigma
// rule. The x/text casers for the undetermined language do the same. A caser keeps state, so the mutex guards them.
var (
	identityMutex sync.Mutex
	rootLower     = cases.Lower(language.Und)
	rootUpper     = cases.Upper(language.Und)
)

// devBuildPathIdentity is the Kotlin devBuildPathIdentity (DevBuildPaths.kt). Two destinations with one identity
// collide on a case-insensitive or a normalizing file system.
func devBuildPathIdentity(value string) string {
	identityMutex.Lock()
	defer identityMutex.Unlock()
	folded := rootLower.String(rootUpper.String(rootLower.String(norm.NFC.String(value))))
	return norm.NFC.String(folded)
}

func validateDevBuildDirectorySpellings(paths []string) error {
	directories := make(map[string]string)
	for _, value := range paths {
		for parent := value; parent != ""; parent = substringBeforeLastSlash(parent) {
			identity := devBuildPathIdentity(parent)
			previous, exists := directories[identity]
			if !exists {
				directories[identity] = parent
			} else if previous != parent {
				return fmt.Errorf("Conflicting destination spellings '%s' and '%s'", previous, parent)
			}
		}
	}
	return nil
}

type distributionLink struct {
	path   string
	target string
}

// validateDevBuildLinks resolves every link chain and fails on a cycle or on a chain that leaves the distribution.
func validateDevBuildLinks(entries []distributionLink) error {
	links := make(map[string]distributionLink)
	identities := make([]string, 0, len(entries))
	for _, entry := range entries {
		if err := checkDevBuildDistributionLink(entry.path, entry.target); err != nil {
			return err
		}
		identity := devBuildPathIdentity(entry.path)
		if _, exists := links[identity]; exists {
			return fmt.Errorf("Duplicate distribution link '%s'", entry.path)
		}
		links[identity] = entry
		identities = append(identities, identity)
	}
	resolved := make(map[string][]string)
	var resolve func(parts []string, stack *[]string, active map[string]bool) error
	resolve = func(parts []string, stack *[]string, active map[string]bool) error {
		for _, part := range parts {
			switch part {
			case "", ".":
				continue
			case "..":
				if len(*stack) == 0 {
					return fmt.Errorf("Distribution link chain escapes the distribution")
				}
				*stack = (*stack)[:len(*stack)-1]
			default:
				*stack = append(*stack, part)
				identity := devBuildPathIdentity(strings.Join(*stack, "/"))
				link, exists := links[identity]
				if !exists {
					continue
				}
				if cached, exists := resolved[identity]; exists {
					*stack = append((*stack)[:0], cached...)
					continue
				}
				if active[identity] {
					return fmt.Errorf("Distribution link cycle at '%s'", link.path)
				}
				active[identity] = true
				parent := strings.Split(link.path, "/")
				*stack = append((*stack)[:0], parent[:len(parent)-1]...)
				if err := resolve(strings.Split(link.target, "/"), stack, active); err != nil {
					return err
				}
				delete(active, identity)
				resolved[identity] = append([]string(nil), *stack...)
			}
		}
		return nil
	}
	for _, identity := range identities {
		var destination []string
		if err := resolve(strings.Split(links[identity].path, "/"), &destination, make(map[string]bool)); err != nil {
			return err
		}
	}
	return nil
}

// checkDevBuildDistributionLink checks one distribution link. The target must be relative, and it must not leave the
// distribution from the directory of the link.
func checkDevBuildDistributionLink(name, target string) error {
	if err := validateDevBuildLocalPath(name); err != nil {
		return err
	}
	link, err := javaPath(target)
	if err != nil {
		return err
	}
	destination := path.Clean(path.Join(path.Dir(name), filepath.ToSlash(link)))
	if target == "" || filepath.IsAbs(link) || strings.ContainsAny(target, "\\:\x00") || destination == ".." ||
		strings.HasPrefix(destination, "../") {
		return fmt.Errorf("Dev-build component symbolic link '%s' escapes the distribution: %s", name, target)
	}
	return nil
}

func validateDevBuildLocalPath(value string) error {
	if !isLocalPath(value) {
		return fmt.Errorf("Invalid path in the local dev layout: %s", value)
	}
	return nil
}

func isLocalPath(value string) bool {
	if value == "" || strings.ContainsAny(value, "\\:\x00") {
		return false
	}
	for _, part := range strings.Split(value, "/") {
		if part == "" || part == "." || part == ".." {
			return false
		}
	}
	return true
}
