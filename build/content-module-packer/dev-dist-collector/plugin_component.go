package main

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path"
	"path/filepath"
	"slices"
	"strings"
	"unicode/utf8"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/pluginclasspath"
	"jetbrains.com/content-module-packer/internal/pluginpack"
	"jetbrains.com/content-module-packer/internal/span"
)

// pluginComponentSpec has two shapes. The prepared shape names a remainder, an asset list and a ready classpath record.
// The packed shape names the final descriptor and the jars Bazel packed; a top-level `jars` key selects it, and the
// collector then writes the classpath record itself.
type pluginComponentSpec struct {
	Version         int                          `json:"version"`
	PluginDirectory string                       `json:"pluginDirectory"`
	Remainder       pluginComponentRemainder     `json:"remainder"`
	Assets          string                       `json:"assets"`
	Classpath       string                       `json:"classpath"`
	Independent     []pluginComponentIndependent `json:"independent"`
	Descriptor      string                       `json:"descriptor"`
	Jars            []pluginComponentJar         `json:"jars"`
}

type pluginComponentJar struct {
	Destination string `json:"destination"`
	Source      string `json:"source"`
	Metadata    string `json:"metadata"`
}

func (spec pluginComponentSpec) packed() bool {
	return spec.Jars != nil
}

type pluginComponentRemainder struct {
	Directory string `json:"directory"`
	Metadata  string `json:"metadata"`
}

type pluginComponentIndependent struct {
	Artifact     string `json:"artifact"`
	Source       string `json:"source"`
	Metadata     string `json:"metadata"`
	RelativePath string `json:"relativePath"`
}

// pluginComponentAsset is one row of the asset table of a remainder. The Kotlin preparation or the packing action
// writes the table. The collector decodes the row into the packer's own type, so both processes apply
// pluginpack.ValidateAssets to the same fields.
type pluginComponentAsset = pluginpack.Asset

func runPluginComponent(opts options, output, errors io.Writer) (exitCode int) {
	var spec pluginComponentSpec
	if err := readPluginMetadata(opts.pluginComponent, &spec); err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	validate := validatePluginComponentSpec
	if spec.packed() {
		validate = validatePackedPluginComponentSpec
	}
	if err := validate(opts, spec); err != nil {
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	var tracer *span.Tracer
	if opts.traceFile != "" {
		tracer = span.NewTracer("collect plugin component metadata")
	}
	root := tracer.Start("collect plugin component metadata", nil)
	root.SetString("kind", opts.kind)
	root.SetInt("byteCount", 0)
	defer func() {
		root.End()
		if err := tracer.WriteFile(opts.traceFile); err != nil {
			fmt.Fprintf(errors, "ERROR: writing the span file: %v\n", err)
			exitCode = 1
		}
	}()
	files, classpath, err := collectPluginComponentAndClassPath(spec, tracer, root)
	if err == nil {
		err = validatePluginClassPath(classpath, spec.PluginDirectory, files)
	}
	if err == nil {
		err = writeManifest(opts, files, tracer, root)
	}
	if err == nil {
		err = os.MkdirAll(filepath.Dir(opts.pluginClasspath), 0755)
	}
	if err == nil {
		err = os.WriteFile(opts.pluginClasspath, classpath, 0644)
	}
	if err != nil {
		root.Fail(err)
		fmt.Fprintf(errors, "ERROR: %v\n", err)
		return 1
	}
	fmt.Fprintf(output, "Dev distribution component '%s' named %d plugin files in %s\n", opts.kind, len(files), opts.manifest)
	return 0
}

// collectPluginComponentAndClassPath returns the component files and the plugin's classpath record.
// The prepared shape ships the record; the packed shape has the collector write it.
func collectPluginComponentAndClassPath(spec pluginComponentSpec, tracer *span.Tracer, parent *span.Span) ([]sourcedFile, []byte, error) {
	if spec.packed() {
		files, err := collectPackedPluginComponent(spec, tracer, parent)
		if err != nil {
			return nil, nil, err
		}
		descriptor, err := os.ReadFile(spec.Descriptor)
		if err != nil {
			return nil, nil, err
		}
		classpath, err := pluginClassPathRecord(spec.PluginDirectory, descriptor, files)
		return files, classpath, err
	}
	files, err := collectPluginComponent(spec, tracer, parent)
	if err != nil {
		return nil, nil, err
	}
	classpath, err := os.ReadFile(spec.Classpath)
	return files, classpath, err
}

func readPluginMetadata(source string, destination any) error {
	data, err := os.ReadFile(source)
	if err != nil {
		return err
	}
	if !utf8.Valid(data) {
		return fmt.Errorf("%s is not valid UTF-8", source)
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(destination); err != nil {
		return fmt.Errorf("%s: %w", source, err)
	}
	if err := decoder.Decode(new(any)); err != io.EOF {
		return fmt.Errorf("%s: unexpected data after metadata", source)
	}
	return nil
}

func validatePluginComponentSpec(opts options, spec pluginComponentSpec) error {
	if spec.Version < pluginpack.Version || spec.Version > pluginpack.ScopedVersion {
		return fmt.Errorf("unsupported plugin component version: %d", spec.Version)
	}
	if err := validatePluginDirectory(spec.PluginDirectory); err != nil {
		return err
	}
	metadata := []string{opts.pluginComponent, spec.Assets, spec.Classpath, spec.Remainder.Metadata}
	payload := []string{spec.Remainder.Directory}
	identifiers := make(map[string]bool)
	for _, artifact := range spec.Independent {
		if isBlank(artifact.Artifact) || identifiers[artifact.Artifact] {
			return fmt.Errorf("empty or duplicate independent artifact ID: %q", artifact.Artifact)
		}
		identifiers[artifact.Artifact] = true
		if err := filemetadata.ValidatePath(artifact.RelativePath); err != nil {
			return err
		}
		metadata = append(metadata, artifact.Metadata)
		payload = append(payload, artifact.Source)
	}
	if err := validatePluginArtifactPaths(opts, metadata, payload); err != nil {
		return err
	}
	for _, artifact := range spec.Independent {
		if pluginArtifactOverlap(artifact.Source, spec.Remainder.Directory) {
			return fmt.Errorf("independent artifact %s overlaps the remainder", artifact.Artifact)
		}
	}
	return nil
}

func validatePackedPluginComponentSpec(opts options, spec pluginComponentSpec) error {
	if spec.Version != pluginpack.Version {
		return fmt.Errorf("unsupported packed plugin component version: %d", spec.Version)
	}
	if err := validatePluginDirectory(spec.PluginDirectory); err != nil {
		return err
	}
	if spec.Remainder != (pluginComponentRemainder{}) || spec.Assets != "" || spec.Classpath != "" || len(spec.Independent) != 0 {
		return fmt.Errorf("a packed plugin component names only its descriptor and jars")
	}
	if len(spec.Jars) == 0 {
		return fmt.Errorf("a packed plugin component requires at least one jar")
	}
	metadata := []string{opts.pluginComponent, spec.Descriptor}
	payload := make([]string, 0, len(spec.Jars))
	destinations := make(map[string]string, len(spec.Jars))
	for _, jar := range spec.Jars {
		if err := filemetadata.ValidatePath(jar.Destination); err != nil {
			return err
		}
		identity := filemetadata.PathIdentity(jar.Destination)
		if previous, exists := destinations[identity]; exists {
			return fmt.Errorf("conflicting plugin destinations: %s and %s", previous, jar.Destination)
		}
		destinations[identity] = jar.Destination
		metadata = append(metadata, jar.Metadata)
		payload = append(payload, jar.Source)
	}
	return validatePluginArtifactPaths(opts, metadata, payload)
}

func validatePluginDirectory(pluginDirectory string) error {
	if err := filemetadata.ValidatePath(pluginDirectory); err != nil || !strings.HasPrefix(pluginDirectory, "plugins/") || strings.Count(pluginDirectory, "/") != 1 {
		return fmt.Errorf("pluginDirectory must name plugins/<directory>: %q", pluginDirectory)
	}
	return nil
}

// validatePluginArtifactPaths checks that every declared path is safe and that no metadata input, payload artifact
// and output alias another.
func validatePluginArtifactPaths(opts options, metadata, payload []string) error {
	outputs := []string{opts.manifest, opts.pluginClasspath}
	if opts.traceFile != "" {
		outputs = append(outputs, opts.traceFile)
	}
	for _, source := range append(append(slices.Clone(metadata), payload...), outputs...) {
		if err := validateDeclaredArtifactPath(source); err != nil {
			return err
		}
	}
	for _, source := range metadata {
		for _, artifact := range payload {
			if pluginArtifactOverlap(source, artifact) {
				return fmt.Errorf("metadata input %s overlaps payload artifact %s", source, artifact)
			}
		}
	}
	for index, destination := range outputs {
		for _, source := range append(slices.Clone(metadata), outputs[:index]...) {
			if pluginArtifactOverlap(destination, source) {
				return fmt.Errorf("output %s conflicts with metadata path %s", destination, source)
			}
		}
		for _, artifact := range payload {
			if pluginArtifactOverlap(destination, artifact) {
				return fmt.Errorf("output %s overlaps payload artifact %s", destination, artifact)
			}
		}
	}
	return nil
}

// validateDeclaredArtifactPath accepts a clean slash path, relative or absolute, with no ".." segment.
// Bazel declares every path in slash form, so the check never converts and a backslash is a defect.
func validateDeclaredArtifactPath(source string) error {
	if isBlank(source) || strings.ContainsAny(source, "\\\x00\r\n") || path.Clean(source) != source || source == "." || source == "/" {
		return fmt.Errorf("invalid declared artifact path: %q", source)
	}
	for _, part := range strings.Split(source, "/") {
		if part == ".." {
			return fmt.Errorf("invalid declared artifact path: %q", source)
		}
	}
	return nil
}

func pluginArtifactOverlap(source, artifact string) bool {
	sourcePath, _ := filepath.Abs(source)
	artifactPath, _ := filepath.Abs(artifact)
	sourcePath = filemetadata.PathIdentity(filepath.ToSlash(sourcePath))
	artifactPath = filemetadata.PathIdentity(filepath.ToSlash(artifactPath))
	return sourcePath == artifactPath || strings.HasPrefix(sourcePath, artifactPath+"/") || strings.HasPrefix(artifactPath, sourcePath+"/")
}

func pluginComponentAssetScope(scope string) string {
	if scope == "" {
		return pluginpack.PluginScope
	}
	return scope
}

func pluginComponentDestination(pluginDirectory, scope, destination string) string {
	if pluginComponentAssetScope(scope) == pluginpack.DistributionScope {
		return destination
	}
	return pluginDirectory + "/" + destination
}

func pluginTreeLogicalDestination(version int, asset pluginComponentAsset, transportDestination string) (string, error) {
	root := pluginpack.TransportDestination(version, asset.Scope, asset.Destination)
	if root == "" {
		return transportDestination, nil
	}
	if transportDestination == root {
		return asset.Destination, nil
	}
	prefix := root + "/"
	if !strings.HasPrefix(transportDestination, prefix) {
		return "", fmt.Errorf("tree entry %s is outside %s", transportDestination, root)
	}
	relative := strings.TrimPrefix(transportDestination, prefix)
	if asset.Destination == "" {
		return relative, nil
	}
	return asset.Destination + "/" + relative, nil
}

func collectPluginComponent(spec pluginComponentSpec, tracer *span.Tracer, parent *span.Span) (files []sourcedFile, err error) {
	activity := tracer.Start("merge plugin component metadata", parent)
	activity.SetInt("byteCount", 0)
	defer func() {
		if err != nil {
			activity.Fail(err)
		}
		activity.End()
	}()
	var assets []pluginComponentAsset
	if err := readPluginMetadata(spec.Assets, &assets); err != nil {
		return nil, err
	}
	if assets == nil {
		return nil, fmt.Errorf("expected an ordered plugin asset array")
	}
	if err := validatePluginComponentAssets(spec.Version, assets); err != nil {
		return nil, err
	}
	remainder, err := filemetadata.Read(spec.Remainder.Metadata)
	if err != nil {
		return nil, err
	}
	if spec.Version >= pluginpack.TreeVersion {
		var record struct {
			Version int                  `json:"version"`
			Entries []filemetadata.Entry `json:"entries"`
		}
		if err := readPluginMetadata(spec.Remainder.Metadata, &record); err != nil {
			return nil, err
		}
		if len(record.Entries) != len(remainder) {
			return nil, fmt.Errorf("duplicate remainder inventory entries")
		}
	}
	remaining := make(map[string]filemetadata.Entry, len(remainder))
	for _, entry := range remainder {
		remaining[entry.RelativePath] = entry
	}
	independent := make(map[string]sourcedFile, len(spec.Independent))
	bySource := make(map[string]filemetadata.Entry)
	for _, artifact := range spec.Independent {
		entries, err := filemetadata.Read(artifact.Metadata)
		if err != nil {
			return nil, err
		}
		if len(entries) != 1 || entries[0].RelativePath != artifact.RelativePath || entries[0].Type != "file" {
			return nil, fmt.Errorf("independent artifact %s requires metadata for exactly one regular file %s", artifact.Artifact, artifact.RelativePath)
		}
		entry := entries[0]
		source, _ := filepath.Abs(artifact.Source)
		identity := filemetadata.PathIdentity(source)
		if previous, exists := bySource[identity]; exists && previous != entry {
			return nil, fmt.Errorf("conflicting metadata for independent source %s", artifact.Source)
		}
		bySource[identity] = entry
		independent[artifact.Artifact] = sourcedFile{Source: artifact.Source, metadata: &entry}
	}
	claimedRemainder := make(map[int]filemetadata.Entry)
	for index, asset := range assets {
		if asset.Kind == "tree" || asset.Producer != "remainder" {
			continue
		}
		transportDestination := pluginpack.TransportDestination(spec.Version, asset.Scope, asset.Destination)
		entry, exists := remaining[transportDestination]
		if !exists || asset.Artifact != "" || (asset.Kind == "directory") != (entry.Type == "directory") {
			return nil, fmt.Errorf("stale remainder ownership for %s", asset.Destination)
		}
		claimedRemainder[index] = entry
		delete(remaining, transportDestination)
	}
	treeIndexes := make([]int, 0)
	for index, asset := range assets {
		if asset.Kind == "tree" {
			treeIndexes = append(treeIndexes, index)
		}
	}
	slices.SortStableFunc(treeIndexes, func(first, second int) int {
		firstLength := len(pluginpack.TransportDestination(spec.Version, assets[first].Scope, assets[first].Destination))
		secondLength := len(pluginpack.TransportDestination(spec.Version, assets[second].Scope, assets[second].Destination))
		switch {
		case firstLength > secondLength:
			return -1
		case firstLength < secondLength:
			return 1
		default:
			return 0
		}
	})
	treeEntries := make(map[int][]filemetadata.Entry, len(treeIndexes))
	for _, assetIndex := range treeIndexes {
		available := make([]filemetadata.Entry, 0, len(remaining))
		for _, entry := range remainder {
			if _, exists := remaining[entry.RelativePath]; exists {
				available = append(available, entry)
			}
		}
		asset := assets[assetIndex]
		transportDestination := pluginpack.TransportDestination(spec.Version, asset.Scope, asset.Destination)
		owned, err := pluginTreeInventory(transportDestination, available)
		if err != nil {
			return nil, err
		}
		for _, entry := range owned {
			delete(remaining, entry.RelativePath)
		}
		treeEntries[assetIndex] = owned
	}
	used := make(map[string]bool)
	entries := make([]filemetadata.Entry, 0, len(assets))
	for assetIndex, asset := range assets {
		if asset.Kind == "tree" {
			for _, entry := range treeEntries[assetIndex] {
				logicalDestination, err := pluginTreeLogicalDestination(spec.Version, asset, entry.RelativePath)
				if err != nil {
					return nil, err
				}
				file := sourcedFile{
					Source:       path.Join(spec.Remainder.Directory, entry.RelativePath),
					RelativePath: pluginComponentDestination(spec.PluginDirectory, asset.Scope, logicalDestination), metadata: &entry,
				}
				if entry.Type == "symlink" {
					file.symlinkSource = file.Source
				} else {
					file.mode = &entry.Mode
				}
				entry.RelativePath = file.RelativePath
				entries = append(entries, entry)
				files = append(files, file)
			}
			continue
		}
		var file sourcedFile
		switch asset.Producer {
		case "remainder":
			entry, exists := claimedRemainder[assetIndex]
			if !exists {
				return nil, fmt.Errorf("stale remainder ownership for %s", asset.Destination)
			}
			transportDestination := pluginpack.TransportDestination(spec.Version, asset.Scope, asset.Destination)
			file.Source = path.Join(spec.Remainder.Directory, transportDestination)
			file.metadata = &entry
			if entry.Type == "symlink" {
				file.symlinkSource = file.Source
			}
		case "independent":
			var exists bool
			file, exists = independent[asset.Artifact]
			if !exists || asset.Kind == "directory" {
				return nil, fmt.Errorf("missing independent artifact %s for %s", asset.Artifact, asset.Destination)
			}
			used[asset.Artifact] = true
		default:
			return nil, fmt.Errorf("unknown asset producer %q", asset.Producer)
		}
		file.RelativePath = pluginComponentDestination(spec.PluginDirectory, asset.Scope, asset.Destination)
		file.classPath = pluginComponentAssetScope(asset.Scope) == pluginpack.PluginScope && asset.Kind != "directory" &&
			(asset.ClassPath == nil || *asset.ClassPath) && isPluginLibJar(asset.Destination)
		entry := *file.metadata
		if entry.Type == "file" || entry.Type == "directory" {
			file.mode = &entry.Mode
		}
		entry.RelativePath = file.RelativePath
		entries = append(entries, entry)
		files = append(files, file)
	}
	if len(remaining) != 0 || len(used) != len(independent) {
		return nil, fmt.Errorf("stale plugin ownership: %d unclaimed remainder outputs, %d unused independent artifacts", len(remaining), len(independent)-len(used))
	}
	ownedDestinations := make(map[string]string, len(entries))
	for _, entry := range entries {
		identity := filemetadata.PathIdentity(entry.RelativePath)
		if previous, exists := ownedDestinations[identity]; exists {
			return nil, fmt.Errorf("conflicting plugin destinations: %s and %s", previous, entry.RelativePath)
		}
		ownedDestinations[identity] = entry.RelativePath
	}
	if _, err := filemetadata.Merge(entries); err != nil {
		return nil, err
	}
	activity.SetInt("fileCount", int64(len(files)))
	return files, nil
}

// collectPackedPluginComponent lists the jars of a packed plugin in their declared order. It reads only metadata.
func collectPackedPluginComponent(spec pluginComponentSpec, tracer *span.Tracer, parent *span.Span) (files []sourcedFile, err error) {
	activity := tracer.Start("collect packed plugin jars", parent)
	activity.SetInt("byteCount", 0)
	defer func() {
		if err != nil {
			activity.Fail(err)
		}
		activity.End()
	}()
	entries := make([]filemetadata.Entry, 0, len(spec.Jars))
	for _, jar := range spec.Jars {
		entry, err := readPackedJarMetadata(jar)
		if err != nil {
			return nil, err
		}
		file := sourcedFile{
			Source:       jar.Source,
			RelativePath: spec.PluginDirectory + "/" + jar.Destination,
			metadata:     &entry,
			mode:         &entry.Mode,
			classPath:    isPluginLibJar(jar.Destination),
		}
		entry.RelativePath = file.RelativePath
		entries = append(entries, entry)
		files = append(files, file)
	}
	if _, err := filemetadata.Merge(entries); err != nil {
		return nil, err
	}
	activity.SetInt("fileCount", int64(len(files)))
	return files, nil
}

// readPackedJarMetadata reads the one-file inventory the packer wrote next to the jar. The packer names the entry
// after the jar file, so a metadata file paired with another jar is refused.
func readPackedJarMetadata(jar pluginComponentJar) (filemetadata.Entry, error) {
	entries, err := filemetadata.Read(jar.Metadata)
	if err != nil {
		return filemetadata.Entry{}, err
	}
	if len(entries) != 1 || entries[0].Type != "file" || entries[0].RelativePath != path.Base(filepath.ToSlash(jar.Source)) {
		return filemetadata.Entry{}, fmt.Errorf("packed jar %s requires metadata for exactly one regular file", jar.Source)
	}
	return entries[0], nil
}

// isPluginLibJar reports whether a plugin-relative destination is `lib/<name>.jar`, the shape the plugin classpath lists.
func isPluginLibJar(destination string) bool {
	return strings.HasPrefix(destination, "lib/") && strings.Count(destination, "/") == 1 && strings.HasSuffix(destination, ".jar")
}

// validatePluginComponentAssets applies the shared asset rules, then the two rules only the collector holds. A tree
// names no independent artifact, and no file asset lies inside a tree of the same scope.
func validatePluginComponentAssets(version int, assets []pluginComponentAsset) error {
	if _, err := pluginpack.ValidateAssets(version, assets, filemetadata.PathIdentity, true); err != nil {
		return err
	}
	for _, asset := range assets {
		if asset.Kind == "tree" && asset.Artifact != "" {
			return fmt.Errorf("tree %s must not name an independent artifact", asset.Destination)
		}
	}
	for treeIndex, tree := range assets {
		if tree.Kind != "tree" {
			continue
		}
		root := filemetadata.PathIdentity(tree.Destination)
		for assetIndex, asset := range assets {
			if assetIndex == treeIndex {
				continue
			}
			if pluginComponentAssetScope(asset.Scope) != pluginComponentAssetScope(tree.Scope) {
				continue
			}
			name := filemetadata.PathIdentity(asset.Destination)
			if (asset.Kind == "" || asset.Kind == "file") && pluginTreeContains(name, root) {
				return fmt.Errorf("asset %s overlaps tree %s", asset.Destination, tree.Destination)
			}
		}
	}
	return nil
}

func pluginTreeContains(root, name string) bool {
	return root == "" || name == root || strings.HasPrefix(name, root+"/")
}

// pluginTreeInventory runs the shared link-graph walk a second time, because the collector validates the packer's
// produced inventory and does not trust the producer.
func pluginTreeInventory(root string, inventory []filemetadata.Entry) ([]filemetadata.Entry, error) {
	var owned []filemetadata.Entry
	nodes := make(map[string]filemetadata.Entry)
	links := make(map[string]string)
	if root == "" {
		nodes["."] = filemetadata.Entry{Type: "directory"}
	}
	for _, entry := range inventory {
		if root != "" && entry.RelativePath != root && !strings.HasPrefix(entry.RelativePath, root+"/") {
			continue
		}
		owned = append(owned, entry)
		name := entry.RelativePath
		if root != "" {
			name = "."
			if entry.RelativePath != root {
				name = strings.TrimPrefix(entry.RelativePath, root+"/")
			}
		}
		nodes[name] = entry
		if entry.Type == "symlink" {
			links[name] = entry.SymlinkTarget
		}
	}
	if root != "" && nodes["."].Type != "directory" {
		if len(owned) == 0 {
			return owned, nil
		}
		return nil, fmt.Errorf("tree %s requires root directory metadata", root)
	}
	if err := filemetadata.ValidateLinks(links); err != nil {
		return nil, err
	}
	directories := make(map[string]bool, len(nodes))
	for name, entry := range nodes {
		directories[name] = entry.Type == "directory"
	}
	if err := pluginpack.ValidateLinkGraph(directories, links); err != nil {
		return nil, err
	}
	return owned, nil
}

func validatePluginClassPath(data []byte, pluginDirectory string, files []sourcedFile) error {
	expected := make(map[string]bool)
	for _, file := range files {
		if file.classPath {
			name := strings.TrimPrefix(file.RelativePath, pluginDirectory+"/")
			expected[string(pluginclasspath.ModifiedUTF8(name))] = true
		}
	}
	input := bytes.NewReader(data)
	readName := func() ([]byte, error) {
		var size uint16
		if err := binary.Read(input, binary.BigEndian, &size); err != nil {
			return nil, err
		}
		name := make([]byte, int(size))
		_, err := io.ReadFull(input, name)
		return name, err
	}
	var count uint16
	if err := binary.Read(input, binary.BigEndian, &count); err != nil || int(count) != len(expected) {
		return fmt.Errorf("plugin classpath count does not match the declared assets")
	}
	name, err := readName()
	if err != nil || !bytes.Equal(name, pluginclasspath.ModifiedUTF8(path.Base(pluginDirectory))) {
		return fmt.Errorf("plugin classpath names the wrong directory")
	}
	var descriptorSize uint32
	if err := binary.Read(input, binary.BigEndian, &descriptorSize); err != nil || uint64(descriptorSize) > uint64(input.Len()) {
		return fmt.Errorf("plugin classpath has an invalid descriptor size")
	}
	if _, err := input.Seek(int64(descriptorSize), io.SeekCurrent); err != nil {
		return fmt.Errorf("plugin classpath descriptor: %w", err)
	}
	for range count {
		name, err := readName()
		if err != nil || !expected[string(name)] {
			return fmt.Errorf("plugin classpath contains an undeclared, excluded, or repeated asset")
		}
		delete(expected, string(name))
	}
	if len(expected) != 0 || input.Len() != 0 {
		return fmt.Errorf("plugin classpath has missing assets or trailing data")
	}
	return nil
}
