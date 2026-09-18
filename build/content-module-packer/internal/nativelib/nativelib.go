// Package nativelib ports the native-library selection rules of nativeLib.kt: the OS family of an archive entry, its
// architecture, the entries a target platform takes, and the path each one gets under lib/native.
package nativelib

import (
	"fmt"
	"path"
	"regexp"
	"strings"
)

// Family is the OS family of a native entry, spelled as the dev-dist variant spells it: darwin, linux, or windows.
type Family string

const (
	Windows Family = "windows"
	MacOS   Family = "darwin"
	Linux   Family = "linux"
)

// Arch is a JVM architecture. Universal marks a native file every architecture can use.
type Arch string

const (
	X64       Arch = "x64"
	AArch64   Arch = "aarch64"
	Universal Arch = ""
)

// DirName is JvmArchitecture.dirName, the directory a jna or async-profiler file takes.
func (arch Arch) DirName() string {
	if arch == X64 {
		return "amd64"
	}
	return string(arch)
}

// compatibleWith is NativeFileArchitecture.compatibleWithTarget: a universal file fits every target.
func (arch Arch) compatibleWith(target Arch) bool {
	return target == Universal || arch == Universal || arch == target
}

var (
	macOSDirless   = regexp.MustCompile(`(?i)(darwin|mac|macos|osx)`)
	windowsDirless = regexp.MustCompile(`(?i)(windows|win32-|win)`)
	linuxDirless   = regexp.MustCompile(`(?i)linux`)
	familyPattern  = regexp.MustCompile(`(?i)(^|-|/)((?P<macos>(darwin|mac|macos)[-/])|(?P<win>win32-|(win|windows)[-/])|(?P<android>Linux-(Android|Musl)/)|(?P<linux>linux[-/]))`)
	macosGroup     = familyPattern.SubexpIndex("macos")
	winGroup       = familyPattern.SubexpIndex("win")
	linuxGroup     = familyPattern.SubexpIndex("linux")
)

// DetectOSFamily is OsFamilyDetector.detectOsFamily. It returns the family of the entry and the path prefix before the
// family token, or false for an entry of no family, an Android or Musl entry included.
func DetectOSFamily(entryPath string) (Family, string, bool) {
	if !strings.Contains(entryPath, "/") {
		// A dirless native, like the skiko runtime files.
		switch {
		case macOSDirless.MatchString(entryPath):
			return MacOS, "", true
		case windowsDirless.MatchString(entryPath):
			return Windows, "", true
		case linuxDirless.MatchString(entryPath):
			if strings.Contains(entryPath, "musl") {
				return "", "", false
			}
			return Linux, "", true
		case entryPath == "icudtl.dat":
			return Windows, "", true
		}
		return "", "", false
	}
	match := familyPattern.FindStringSubmatchIndex(entryPath)
	if match == nil {
		return "", "", false
	}
	for _, candidate := range []struct {
		group  int
		family Family
	}{{macosGroup, MacOS}, {winGroup, Windows}, {linuxGroup, Linux}} {
		if start := match[2*candidate.group]; start >= 0 {
			return candidate.family, entryPath[:start], true
		}
	}
	return "", "", false
}

// DetermineArch is determineArch: the architecture of an entry from its directory, or false for an entry that names none.
func DetermineArch(family Family, entryPath string) (Arch, bool) {
	if !strings.Contains(entryPath, "/") {
		switch {
		case strings.Contains(entryPath, "x64"), strings.Contains(entryPath, "x86_64"), strings.Contains(entryPath, "win64"):
			return X64, true
		case strings.Contains(entryPath, "aarch64"), strings.Contains(entryPath, "arm64"):
			return AArch64, true
		case entryPath == "icudtl.dat":
			return Universal, true
		}
		return "", false
	}
	osAndArch := entryPath[:strings.Index(entryPath, "/")]
	slashes := strings.Count(entryPath, "/")
	switch {
	case strings.HasSuffix(osAndArch, "-aarch64"), strings.Contains(entryPath, "/aarch64/"), strings.Contains(osAndArch, "arm64"):
		return AArch64, true
	case strings.Contains(entryPath, "x86-64"), strings.Contains(entryPath, "x86_64"), strings.Contains(osAndArch, "x64"):
		return X64, true
	case family == MacOS && slashes == 1:
		return Universal, true
	case !strings.Contains(osAndArch, "-") && slashes == 1:
		return X64, true
	}
	return "", false
}

// RelativePath is nativeLibraryRelativePath: the path of a selected file under lib/native. Each library has its own rule.
func RelativePath(libName string, arch Arch, fileName, entryPath string) (string, error) {
	switch libName {
	case "async-profiler":
		if arch == Universal {
			return fileName, nil
		}
		return arch.DirName() + "/" + fileName, nil
	case "skiko-awt-runtime-all":
		return fileName, nil
	case "jna":
		if arch == Universal {
			return "", fmt.Errorf("a jna native file requires an architecture: %s", entryPath)
		}
		return arch.DirName() + "/" + fileName, nil
	}
	return entryPath, nil
}

// LibNameFromFile is getLibNameBySourceFile: the Maven artifact name before the first dash-separated part with a dot.
func LibNameFromFile(fileName string) string {
	parts := strings.Split(fileName, "-")
	var name []string
	for _, part := range parts {
		if strings.Contains(part, ".") {
			break
		}
		name = append(name, part)
	}
	return strings.Join(name, "-")
}

// IsNativeEntry is isNativeDistributionEntry: the archive entries the packer moves out of a jar into lib/native.
func IsNativeEntry(name string) bool {
	switch path.Ext(name) {
	case ".jnilib", ".dylib", ".so", ".tbd", ".exe", ".dll":
		return true
	}
	return strings.HasSuffix(name, "pty4j-unix-spawn-helper") || strings.HasSuffix(name, "icudtl.dat")
}

// IsExecutable states the executable bit of a selected file: a POSIX file without an extension is executed directly.
func IsExecutable(family Family, fileName string) bool {
	return family != Windows && !strings.Contains(fileName, ".")
}

// Match is one selected entry: its full name, the name after the common prefix, its family and its architecture.
type Match struct {
	PathWithPrefix string
	Path           string
	Family         Family
	Arch           Arch
}

// FileName is the last path component of the entry.
func (match Match) FileName() string {
	return match.Path[strings.LastIndex(match.Path, "/")+1:]
}

// Select is NativeFilesMatcher for one target platform. Every native entry must share one path prefix before its
// family token. An entry of another family, of an incompatible architecture, or of no architecture is skipped.
func Select(entries []string, family Family, arch Arch) ([]Match, error) {
	var matches []Match
	prefix, hasPrefix := "", false
	for _, entry := range entries {
		entryFamily, entryPrefix, ok := DetectOSFamily(entry)
		if !ok {
			continue
		}
		if hasPrefix && prefix != entryPrefix {
			return nil, fmt.Errorf("all native runtimes should have common path prefix; %q does not match %q", entry, prefix)
		}
		prefix, hasPrefix = entryPrefix, true
		if entryFamily != family {
			continue
		}
		entryPath := entry[len(prefix):]
		entryArch, ok := DetermineArch(entryFamily, entryPath)
		if !ok || !entryArch.compatibleWith(arch) {
			continue
		}
		matches = append(matches, Match{PathWithPrefix: entry, Path: entryPath, Family: entryFamily, Arch: entryArch})
	}
	return matches, nil
}

// ParseVariant reads a dev-dist platform id, `<os>_<arch>` with darwin for macOS, into its family and architecture.
func ParseVariant(variant string) (Family, Arch, error) {
	separator := strings.LastIndex(variant, "_")
	if separator <= 0 || separator == len(variant)-1 {
		return "", "", fmt.Errorf("unknown native target variant %q", variant)
	}
	family, arch := Family(variant[:separator]), Arch(variant[separator+1:])
	if !ValidFamily(family) || !ValidArch(arch) {
		return "", "", fmt.Errorf("unknown native target variant %q", variant)
	}
	return family, arch, nil
}

// ValidFamily reports whether the family is one of Families.
func ValidFamily(family Family) bool {
	return family == Windows || family == MacOS || family == Linux
}

// ValidArch reports whether the architecture is a target, not Universal.
func ValidArch(arch Arch) bool {
	return arch == X64 || arch == AArch64
}
