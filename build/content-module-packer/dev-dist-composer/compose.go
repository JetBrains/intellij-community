package main

import (
	"encoding/binary"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"time"

	"jetbrains.com/content-module-packer/internal/span"
)

// devBuildComponent is the Kotlin DevBuildComponent. Its manifest names each file where it already is. An empty
// pluginClasspathPart is a component without plugin records; the path is absolute when set.
type devBuildComponent struct {
	manifest            *componentManifest
	pluginClasspathPart string
	sourceBindings      *componentSources
}

// composedBuild is the Kotlin ComposedDevBuild.
type composedBuild struct {
	platformPrefix    string
	mainClass         string
	additionalModules []string
	coreClassPath     []string
	fingerprint       string
}

// composeOptions holds the optional arguments of the Kotlin composeDevBuildComponents. A nil sourceRunfiles requests a
// full distribution, and a map requests launch metadata only. The keys of both maps are absolute paths.
type composeOptions struct {
	pluginClasspathPrefix   string
	expectedFragments       []string
	additionalModules       []string
	sourceRunfiles          *orderedMap
	sourceDirectoryRunfiles *orderedMap
	tracer                  *span.Tracer
	parent                  *span.Span
}

// composeComponents is the Kotlin composeDevBuildComponents. It checks that the components form one distribution,
// then assembles them at target.
func composeComponents(components []devBuildComponent, target string, options composeOptions) (*composedBuild, error) {
	if len(components) == 0 {
		return nil, fmt.Errorf("At least one dev-build component is required")
	}
	manifests := make([]*componentManifest, 0, len(components))
	kinds := make([]string, 0, len(components))
	for _, component := range components {
		for _, entry := range component.manifest.Entries {
			if err := validateDevBuildEntryMode(entry); err != nil {
				return nil, err
			}
		}
		manifests = append(manifests, component.manifest)
		kinds = append(kinds, component.manifest.Kind)
	}
	first := manifests[0]
	var mainClass *string
	var platform *componentManifest
	for _, manifest := range manifests {
		if mainClass == nil {
			mainClass = manifest.MainClass
		}
		if platform == nil && !manifest.platformNeutral() {
			platform = manifest
		}
	}
	if mainClass == nil {
		return nil, fmt.Errorf("No dev-build component declares an IDE main class: %s", strings.Join(kinds, ", "))
	}
	for _, manifest := range manifests[1:] {
		if manifest.PlatformPrefix != first.PlatformPrefix {
			return nil, fmt.Errorf("Dev-build components have different products: '%s' and '%s'", first.PlatformPrefix, manifest.PlatformPrefix)
		}
		if !manifest.platformNeutral() && platform != nil && (manifest.OS != platform.OS || manifest.Arch != platform.Arch) {
			return nil, fmt.Errorf("Dev-build components have different target platforms: '%s/%s' and '%s/%s'",
				platform.OS, platform.Arch, manifest.OS, manifest.Arch)
		}
		if manifest.MainClass != nil && *manifest.MainClass != *mainClass {
			return nil, fmt.Errorf("Dev-build components have different IDE main classes: '%s' and '%s'", *mainClass, *manifest.MainClass)
		}
	}

	var negative, missingParts []string
	for _, component := range components {
		count := fmt.Sprintf("%s (%d)", component.manifest.Kind, component.manifest.PluginCount)
		if component.manifest.PluginCount < 0 {
			negative = append(negative, count)
		}
		if component.manifest.PluginCount > 0 && component.pluginClasspathPart == "" {
			missingParts = append(missingParts, count)
		}
	}
	if len(negative) != 0 {
		return nil, fmt.Errorf("Dev-build components report a negative plugin count: %s", strings.Join(negative, ", "))
	}
	if len(missingParts) != 0 {
		return nil, fmt.Errorf("Dev-build components report plugins but provide no plugin-classpath records: %s", strings.Join(missingParts, ", "))
	}

	present, duplicates := countKinds(kinds)
	if len(duplicates) != 0 {
		return nil, fmt.Errorf("Dev-build fragment kinds must be unique, but these occur more than once: %s", strings.Join(duplicates, ", "))
	}
	if len(options.expectedFragments) != 0 {
		expected, duplicateExpected := countKinds(options.expectedFragments)
		if len(duplicateExpected) != 0 {
			return nil, fmt.Errorf("Expected dev-build fragment kinds must be unique, but these occur more than once: %s",
				strings.Join(duplicateExpected, ", "))
		}
		missing, unexpected := sortedDifference(expected, present), sortedDifference(present, expected)
		if len(missing) != 0 || len(unexpected) != 0 {
			var message strings.Builder
			message.WriteString("Dev-build fragments do not match the expected composition")
			if len(missing) != 0 {
				message.WriteString("; missing: " + strings.Join(missing, ", "))
			}
			if len(unexpected) != 0 {
				message.WriteString("; unexpected: " + strings.Join(unexpected, ", "))
			}
			message.WriteString("; present: " + strings.Join(sortedStrings(present), ", "))
			return nil, fmt.Errorf("%s", message.String())
		}
	}

	if err := os.MkdirAll(target, 0o777); err != nil {
		return nil, err
	}
	var sourceDirectories []string
	if options.sourceDirectoryRunfiles != nil {
		sourceDirectories = options.sourceDirectoryRunfiles.keys
	}
	if options.sourceRunfiles == nil {
		if err := mergeComponents(components, target, sourceDirectories, options.tracer, options.parent); err != nil {
			return nil, err
		}
	}

	pluginClasspathFile, err := composePluginClassPath(components, target, options.pluginClasspathPrefix)
	if err != nil {
		return nil, err
	}

	declaredModules := distinct(options.additionalModules)
	var assembled []string
	var coreClassPath []string
	for _, manifest := range manifests {
		assembled = append(assembled, manifest.AdditionalModules...)
		coreClassPath = append(coreClassPath, manifest.CoreClassPath...)
	}
	assembledModules := distinct(assembled)
	if undeclared := sortedDifference(assembledModules, declaredModules); len(undeclared) != 0 {
		return nil, fmt.Errorf("Dev-build components assembled plugin modules the distribution does not declare: %s\n  declared: %s\n  assembled: %s",
			kotlinList(undeclared), kotlinList(sortedStrings(declaredModules)), kotlinList(sortedStrings(assembledModules)))
	}
	coreClassPath = orderCoreClasspathEntries(coreClassPath)
	if options.sourceRunfiles != nil {
		if err := writeLocalLayout(components, target, options.sourceRunfiles, pluginClasspathFile != "", options.sourceDirectoryRunfiles); err != nil {
			return nil, err
		}
	}
	fingerprint, err := computeIdeFingerprintFromComponents(manifests, pluginClasspathFile, &declaredModules)
	if err != nil {
		return nil, err
	}
	return &composedBuild{
		platformPrefix:    first.PlatformPrefix,
		mainClass:         *mainClass,
		additionalModules: declaredModules,
		coreClassPath:     coreClassPath,
		fingerprint:       fingerprint,
	}, nil
}

// countKinds returns the distinct values in the order of first occurrence and the sorted values that occur more than once.
func countKinds(values []string) (present []string, duplicates []string) {
	counts := make(map[string]int, len(values))
	for _, value := range values {
		if counts[value] == 0 {
			present = append(present, value)
		}
		counts[value]++
	}
	for _, value := range present {
		if counts[value] > 1 {
			duplicates = append(duplicates, value)
		}
	}
	return present, sortedStrings(duplicates)
}

// sortedDifference returns the values of first that second does not contain, in Java string order.
func sortedDifference(first, second []string) []string {
	var result []string
	for _, value := range first {
		if !slices.Contains(second, value) {
			result = append(result, value)
		}
	}
	return sortedStrings(result)
}

func sortedStrings(values []string) []string {
	result := slices.Clone(values)
	slices.SortStableFunc(result, compareUTF16)
	return result
}

// kotlinList renders a list as Kotlin `List.toString` does.
func kotlinList(values []string) string {
	return "[" + strings.Join(values, ", ") + "]"
}

// mergeComponents is the Kotlin mergeDevBuildComponents. It validates every destination of every component before
// it writes the first file.
func mergeComponents(components []devBuildComponent, target string, sourceDirectories []string, tracer *span.Tracer, parent *span.Span) error {
	var links []distributionLink
	var relativePaths []string
	var directories []componentEntry
	for _, component := range components {
		for _, entry := range component.manifest.Entries {
			if entry.SymlinkTarget != nil {
				links = append(links, distributionLink{entry.RelativePath, *entry.SymlinkTarget})
			}
			relativePaths = append(relativePaths, entry.RelativePath)
			if entry.Type == "directory" {
				directories = append(directories, entry)
			}
		}
	}
	if err := validateDevBuildLinks(links); err != nil {
		return err
	}
	reserved := []string{"core-classpath.txt", "fingerprint.txt", "local-layout.json", pluginClassPath}
	paths := newOrderedSet(reserved...)
	directoryPaths := make(map[string]bool, len(directories))
	for _, entry := range directories {
		directoryPaths[devBuildPathIdentity(entry.RelativePath)] = true
	}
	if err := validateDevBuildDirectorySpellings(append(relativePaths, reserved...)); err != nil {
		return err
	}
	for _, component := range components {
		for _, entry := range component.manifest.Entries {
			name := entry.RelativePath
			if !isLocalPath(name) {
				return fmt.Errorf("Dev-build component '%s' entry escapes the distribution: %s", component.manifest.Kind, name)
			}
			if !paths.add(devBuildPathIdentity(name)) {
				return fmt.Errorf("Dev-build components both provide '%s'", name)
			}
			if entry.SymlinkTarget == nil {
				continue
			}
			if err := checkDevBuildDistributionLink(name, *entry.SymlinkTarget); err != nil {
				return err
			}
			if spelling, err := hasJavaPathSpelling(*entry.SymlinkTarget); err != nil {
				return err
			} else if !spelling {
				return fmt.Errorf("The exporter cannot preserve symbolic link '%s' with target '%s'", name, *entry.SymlinkTarget)
			}
		}
	}
	if err := checkNoEntryBelowAnother(paths.values, directoryPaths); err != nil {
		return err
	}
	for _, component := range components {
		manifest := component.manifest
		// One span per component, so that a slow composition names the component that made it slow.
		activity := tracer.Start("merge dev build component", parent)
		activity.SetString("kind", manifest.Kind)
		merged, err := copyManifestOnlyComponent(manifest, target, sourceDirectories, component.sourceBindings)
		if err != nil {
			activity.Fail(err)
			activity.End()
			return err
		}
		activity.SetInt("fileCount", int64(merged.fileCount))
		activity.SetInt("byteCount", merged.byteCount)
		activity.End()
	}
	slices.SortStableFunc(directories, func(first, second componentEntry) int {
		return compareUTF16(second.RelativePath, first.RelativePath)
	})
	for _, entry := range directories {
		if err := setDistributionFileMode(resolveRelative(target, entry.RelativePath), false, entry.Mode); err != nil {
			return err
		}
	}
	return nil
}

// checkNoEntryBelowAnother fails when a destination has an ancestor that is a destination and not a directory.
func checkNoEntryBelowAnother(paths []string, directories map[string]bool) error {
	known := make(map[string]bool, len(paths))
	for _, value := range paths {
		known[value] = true
	}
	for _, value := range paths {
		for parent := substringBeforeLastSlash(value); parent != ""; parent = substringBeforeLastSlash(parent) {
			if known[parent] && !directories[parent] {
				return fmt.Errorf("Dev-build component entry '%s' is below another entry: %s", value, parent)
			}
		}
	}
	return nil
}

// orderedSet is a Kotlin LinkedHashSet of strings.
type orderedSet struct {
	values []string
	known  map[string]bool
}

func newOrderedSet(values ...string) *orderedSet {
	set := &orderedSet{known: make(map[string]bool)}
	for _, value := range values {
		set.add(value)
	}
	return set
}

func (set *orderedSet) add(value string) bool {
	if set.known[value] {
		return false
	}
	set.known[value] = true
	set.values = append(set.values, value)
	return true
}

// composePluginClassPath writes `plugins/plugin-classpath.txt` from the prefix of one component and the records of all
// of them. The plugin count between the two covers the whole distribution. It returns an empty path when no component
// has records.
func composePluginClassPath(components []devBuildComponent, target, prefix string) (string, error) {
	var kinds []string
	var pluginCount int32
	for _, component := range components {
		if component.pluginClasspathPart != "" {
			kinds = append(kinds, component.manifest.Kind)
		}
		pluginCount += component.manifest.PluginCount
	}
	if len(kinds) == 0 {
		return "", nil
	}
	if prefix == "" {
		return "", fmt.Errorf("Components contributed plugins (%s), so the plugin-classpath prefix is required", strings.Join(kinds, ", "))
	}
	file := resolveRelative(target, pluginClassPath)
	if err := os.MkdirAll(filepath.Dir(file), 0o777); err != nil {
		return "", err
	}
	content, err := os.ReadFile(prefix)
	if err != nil {
		return "", err
	}
	// DataOutputStream.writeShort writes the low 16 bits in big-endian order.
	content = binary.BigEndian.AppendUint16(content, uint16(pluginCount))
	for _, component := range components {
		if component.pluginClasspathPart == "" {
			continue
		}
		part, err := os.ReadFile(component.pluginClasspathPart)
		if err != nil {
			return "", err
		}
		content = append(content, part...)
	}
	return file, os.WriteFile(file, content, 0o666)
}

// mergedComponent is what one merged component turned out to be, for the span that measured it.
type mergedComponent struct {
	fileCount int
	byteCount int64
}

// copyManifestOnlyComponent is the Kotlin copyManifestOnlyComponent. It copies the files of a component that owns no
// tree from the sources that its manifest names. The manifest declares the executable flag, so a source mode never
// reaches the distribution.
func copyManifestOnlyComponent(manifest *componentManifest, target string, sourceDirectories []string, bindings *componentSources) (mergedComponent, error) {
	normalizedTarget := filepath.Clean(target)
	var byteCount int64
	var linkNames []string
	linkTargets := make(map[string]string)
	for _, entry := range manifest.Entries {
		destination := resolveRelative(normalizedTarget, entry.RelativePath)
		if !pathStartsWith(destination, normalizedTarget) || destination == normalizedTarget {
			return mergedComponent{}, fmt.Errorf("Dev-build component '%s' entry escapes the distribution: %s", manifest.Kind, entry.RelativePath)
		}
		info, statErr := os.Lstat(destination)
		if entry.Type == "directory" {
			if statErr == nil && !info.IsDir() {
				return mergedComponent{}, fmt.Errorf("Dev-build directory '%s' conflicts with a file or link", entry.RelativePath)
			}
			if err := os.MkdirAll(destination, 0o777); err != nil {
				return mergedComponent{}, err
			}
			continue
		}
		if statErr == nil {
			return mergedComponent{}, fmt.Errorf("Dev-build components both provide '%s'", entry.RelativePath)
		}
		if entry.SymlinkTarget != nil {
			if entry.Source != nil || entry.Type != "symlink" {
				return mergedComponent{}, fmt.Errorf("Dev-build component '%s' must declare the symbolic link '%s' without a file source",
					manifest.Kind, entry.RelativePath)
			}
			if err := checkDevBuildDistributionLink(entry.RelativePath, *entry.SymlinkTarget); err != nil {
				return mergedComponent{}, err
			}
			if _, exists := linkTargets[entry.RelativePath]; !exists {
				linkNames = append(linkNames, entry.RelativePath)
			}
			linkTargets[entry.RelativePath] = *entry.SymlinkTarget
			continue
		}
		if entry.Source == nil {
			return mergedComponent{}, fmt.Errorf("Dev-build component '%s' declares no tree, so '%s' must name where its bytes are",
				manifest.Kind, entry.RelativePath)
		}
		source := *entry.Source
		staged, err := javaPath(source)
		if err != nil {
			return mergedComponent{}, err
		}
		if spelling, _ := hasJavaPathSpelling(source); !spelling || hasDotName(staged) {
			return mergedComponent{}, fmt.Errorf("Dev-build component entry '%s' has an unsafe source: %s", entry.RelativePath, source)
		}
		var boundSource string
		if bindings != nil {
			if boundSource, err = bindings.resolve(staged); err != nil {
				return mergedComponent{}, err
			}
		}
		// A manifest may name a file that the composing action never declared, and then the sandbox does not have it.
		if _, err := os.Stat(stagedPath(staged)); err != nil {
			return mergedComponent{}, fmt.Errorf("Dev-build component '%s' names '%s' for '%s', but nothing is staged there"+
				" - the composing action has to declare that file as an input", manifest.Kind, source, entry.RelativePath)
		}
		// Follow the staging link of Bazel, as the tree walk does. A copy of the link would leak the execution root.
		sourceFile := boundSource
		if sourceFile == "" {
			if sourceFile, err = realPath(staged); err != nil {
				return mergedComponent{}, err
			}
		}
		absoluteSource, err := absolutePath(staged)
		if err != nil {
			return mergedComponent{}, err
		}
		sourceDirectory := ""
		for _, directory := range sourceDirectories {
			if absoluteSource != directory && pathStartsWith(absoluteSource, directory) && len(directory) > len(sourceDirectory) {
				sourceDirectory = directory
			}
		}
		physicalDirectory := ""
		if bindings != nil {
			if physicalDirectory, err = bindings.directory(staged); err != nil {
				return mergedComponent{}, err
			}
		}
		if physicalDirectory == "" && sourceDirectory != "" {
			if physicalDirectory, err = realPath(sourceDirectory); err != nil {
				return mergedComponent{}, err
			}
		}
		if physicalDirectory != "" && !pathStartsWith(sourceFile, physicalDirectory) {
			return mergedComponent{}, fmt.Errorf("Dev-build component entry '%s' escapes its declared source directory: %s", entry.RelativePath, source)
		}
		sourceInfo, err := os.Stat(sourceFile)
		if err != nil || !sourceInfo.Mode().IsRegular() {
			return mergedComponent{}, fmt.Errorf("Dev-build component entry '%s' does not name a regular file: %s", entry.RelativePath, source)
		}
		if err := os.MkdirAll(filepath.Dir(destination), 0o777); err != nil {
			return mergedComponent{}, err
		}
		byteCount += sourceInfo.Size()
		if err := copyWithAttributes(sourceFile, destination); err != nil {
			return mergedComponent{}, err
		}
		if err := setDistributionFileMode(destination, entry.Executable, entry.Mode); err != nil {
			return mergedComponent{}, err
		}
	}
	ordered, err := orderDevBuildLinks(linkNames, linkTargets)
	if err != nil {
		return mergedComponent{}, err
	}
	for _, name := range ordered {
		destination := resolveRelative(normalizedTarget, name)
		if err := os.MkdirAll(filepath.Dir(destination), 0o777); err != nil {
			return mergedComponent{}, err
		}
		if err := createSymbolicLink(destination, linkTargets[name]); err != nil {
			return mergedComponent{}, err
		}
	}
	return mergedComponent{fileCount: len(manifest.Entries), byteCount: byteCount}, nil
}

// stagedPath is the path that Java resolves for a staged source. Java resolves an empty path to the working directory.
func stagedPath(staged string) string {
	if staged == "" {
		return "."
	}
	return staged
}

// orderDevBuildLinks is the Kotlin orderDevBuildLinks. A link comes after every link that its target path traverses,
// because Windows gives a link the kind of the target that exists at creation. Independent links keep their order.
func orderDevBuildLinks(names []string, targets map[string]string) ([]string, error) {
	pending := make(map[string]bool, len(names))
	for _, name := range names {
		pending[name] = true
	}
	ordered := make([]string, 0, len(names))
	remaining := names
	for len(remaining) != 0 {
		var deferred []string
		for _, name := range remaining {
			if linkTraversesPending(name, targets[name], pending) {
				deferred = append(deferred, name)
			} else {
				delete(pending, name)
				ordered = append(ordered, name)
			}
		}
		if len(deferred) == len(remaining) {
			return nil, fmt.Errorf("Dev-build component symbolic link cycle at '%s'", deferred[0])
		}
		remaining = deferred
	}
	return ordered, nil
}

// linkTraversesPending reports whether the target path of the link passes through, or ends at, a pending link.
func linkTraversesPending(name, target string, pending map[string]bool) bool {
	current := substringBeforeLastSlash(name)
	for _, part := range strings.Split(target, "/") {
		switch part {
		case "", ".":
		case "..":
			current = substringBeforeLastSlash(current)
		default:
			if current == "" {
				current = part
			} else {
				current += "/" + part
			}
			if pending[current] {
				return true
			}
		}
	}
	return false
}

// createSymbolicLink is `Files.createSymbolicLink(destination, Path.of(target))`.
func createSymbolicLink(destination, target string) error {
	link, err := javaPath(target)
	if err != nil {
		return err
	}
	return os.Symlink(link, destination)
}

// copyWithAttributes is `Files.copy(source, destination, COPY_ATTRIBUTES)` for a source that is not a link. It keeps
// the permission bits and the modification time. For a directory, it creates an empty directory.
func copyWithAttributes(source, destination string) error {
	info, err := os.Stat(source)
	if err != nil {
		return err
	}
	mode := info.Mode() & (fs.ModePerm | fs.ModeSetuid | fs.ModeSetgid | fs.ModeSticky)
	if info.IsDir() {
		if err := os.Mkdir(destination, 0o700); err != nil {
			return err
		}
	} else if !info.Mode().IsRegular() {
		// Java copies a special file as a special file. A read of a named pipe can block, so this refuses it.
		return fmt.Errorf("not a regular file or directory: %s", source)
	} else {
		if err := copyFileContent(source, destination, mode); err != nil {
			return err
		}
	}
	if err := os.Chmod(destination, mode); err != nil {
		return err
	}
	return os.Chtimes(destination, time.Time{}, info.ModTime())
}

func copyFileContent(source, destination string, mode fs.FileMode) error {
	input, err := os.Open(source)
	if err != nil {
		return err
	}
	defer input.Close()
	output, err := os.OpenFile(destination, os.O_WRONLY|os.O_CREATE|os.O_EXCL, mode.Perm())
	if err != nil {
		return err
	}
	_, copyError := io.Copy(output, input)
	closeError := output.Close()
	if copyError != nil {
		return copyError
	}
	return closeError
}

// setDistributionFileMode applies the declared permission bits. Without an exact mode, a file gets 0755 or 0644 from
// its executable flag. Java cannot set POSIX permissions on Windows and ignores that, so this does the same.
func setDistributionFileMode(target string, executable bool, mode *int64) error {
	if windows {
		return nil
	}
	permissions := fs.FileMode(conventionalMode(executable))
	if mode != nil {
		permissions = fs.FileMode(*mode) & fs.ModePerm
	}
	return os.Chmod(target, permissions)
}

// orderCoreClasspathEntries is the Kotlin orderCoreClasspathEntries (classpath.kt). The leading jars come first in a
// fixed order. The other entries follow in the order of Java `Path` on Unix and of Java `String` on Windows.
func orderCoreClasspathEntries(entries []string) []string {
	remaining := slices.Clone(entries)
	result := make([]string, 0, len(entries))
	for _, jar := range []string{"lib/platform-loader.jar", "lib/util-8.jar", "lib/util.jar", "lib/product-backend.jar"} {
		if index := slices.Index(remaining, jar); index >= 0 {
			remaining = slices.Delete(remaining, index, index+1)
			result = append(result, jar)
		}
	}
	if windows {
		slices.SortStableFunc(remaining, compareUTF16)
	} else {
		// UnixPath.compareTo compares the bytes of the normalized path.
		slices.SortStableFunc(remaining, func(first, second string) int {
			firstPath, _ := javaPath(first)
			secondPath, _ := javaPath(second)
			return strings.Compare(firstPath, secondPath)
		})
	}
	return append(result, remaining...)
}
