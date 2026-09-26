package pluginpack

import (
	"encoding/binary"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"slices"
	"strings"

	"jetbrains.com/content-module-packer/internal/filemetadata"
	"jetbrains.com/content-module-packer/internal/jarpack"
	"jetbrains.com/content-module-packer/internal/javaglob"
)

// layoutTransforms names the transform kinds this packer executes: archive-tree, gzip-xml-archive, and tree-map.
// A plain copy needs no transform. Plan refuses every other kind.
var layoutTransforms = map[string]bool{"archive-tree": true, "gzip-xml-archive": true, "tree-map": true}

func mappingPattern(mapping LayoutMapping) string {
	if mapping.Pattern == "" {
		return "**"
	}
	return mapping.Pattern
}

func modeOr(mode, fallback uint32) uint32 {
	if mode == 0 {
		return fallback
	}
	return mode
}

func joinLayoutPath(first, second string) string {
	switch {
	case first == "":
		return second
	case second == "":
		return first
	}
	return first + "/" + second
}

// stripLayoutPath removes the leading components. A path with too few components is dropped.
func stripLayoutPath(name string, components int) (string, bool) {
	parts := strings.Split(name, "/")
	if components >= len(parts) {
		return "", false
	}
	return strings.Join(parts[components:], "/"), true
}

// layoutScratch holds the trees, entries, and decoded archives the layout assets write before the remainder copies them.
// It lives beside the output and is removed before the stage rename.
type layoutScratch struct {
	root  string
	count int
}

func newLayoutScratch(recipe Recipe, output string) (*layoutScratch, error) {
	hasLayout := slices.ContainsFunc(recipe.Operations, func(operation Operation) bool {
		return operation.Layout != nil ||
			slices.ContainsFunc(operation.Sources, func(source Source) bool { return source.Layout != nil })
	})
	if !hasLayout {
		return &layoutScratch{}, nil
	}
	parent := filepath.Dir(output)
	if err := os.MkdirAll(parent, 0o755); err != nil {
		return nil, err
	}
	root, err := os.MkdirTemp(parent, ".plugin-layout-*")
	if err != nil {
		return nil, err
	}
	return &layoutScratch{root: root}, nil
}

func (scratch *layoutScratch) directory(prefix string) (string, error) {
	if scratch.root == "" {
		return "", fmt.Errorf("layout scratch is not available")
	}
	scratch.count++
	directory := filepath.Join(scratch.root, fmt.Sprintf("%s-%d", prefix, scratch.count))
	if err := os.Mkdir(directory, 0o755); err != nil {
		return "", err
	}
	return directory, os.Chmod(directory, 0o755)
}

func (scratch *layoutScratch) remove() error {
	if scratch.root == "" {
		return nil
	}
	root := scratch.root
	scratch.root = ""
	return removeWritableTree(root)
}

// removeWritableTree restores owner access on every directory first, because a layout asset can write a read-only directory.
func removeWritableTree(root string) error {
	filepath.WalkDir(root, func(name string, entry fs.DirEntry, err error) error {
		if err == nil && entry.IsDir() {
			os.Chmod(name, 0o700)
		}
		return nil
	})
	return os.RemoveAll(root)
}

// layoutExecutor writes the layout assets of one payload. Inputs resolve through the execution and its prepared-file cache.
type layoutExecutor struct {
	execution      *Execution
	resolveFile    func(*Reference) (string, error)
	scratch        *layoutScratch
	transportRoots map[string]string
}

type layoutInput struct {
	path   string
	info   os.FileInfo
	kind   string
	target string
}

// tree writes the layout assets of a layout-tree operation into a scratch directory and resolves it like a copy-tree.
func (executor *layoutExecutor) tree(operation Operation) ([]resolvedOperation, error) {
	root, err := executor.scratch.directory("tree")
	if err != nil {
		return nil, err
	}
	writer := newLayoutTreeWriter(root)
	if err := executor.execute(operation.Layout, writer); err != nil {
		return nil, err
	}
	if err := writer.finish(); err != nil {
		return nil, err
	}
	return resolveDirectoryTree(operation, root)
}

// entries writes the file entries of a layout source and returns one single-file jar source per entry.
func (executor *layoutExecutor) entries(source Source) ([]jarpack.Source, error) {
	root, err := executor.scratch.directory("entries")
	if err != nil {
		return nil, err
	}
	writer := newLayoutEntriesWriter(root)
	if err := executor.execute(source.Layout, writer); err != nil {
		return nil, err
	}
	sources := make([]jarpack.Source, 0, len(writer.entries))
	for _, entry := range writer.entries {
		sources = append(sources, jarpack.Source{Path: entry.file, Name: entry.name, Manifest: jarpack.ManifestMode(source.Manifest)})
	}
	return sources, nil
}

func (executor *layoutExecutor) execute(layout *LayoutAssets, writer layoutWriter) error {
	for _, asset := range layout.Assets {
		inputs := make([]layoutInput, 0, len(asset.Sources))
		for _, index := range asset.Sources {
			input, err := executor.input(layout.Inputs[index])
			if err != nil {
				return err
			}
			inputs = append(inputs, input)
		}
		kind := ""
		if asset.Transform != nil {
			kind = asset.Transform.Kind
		}
		var err error
		switch kind {
		case "":
			err = executor.copyAsset(inputs[0], asset, writer)
		case "archive-tree":
			err = executor.extractArchive(inputs[0], asset, writer)
		case "gzip-xml-archive":
			err = executor.gzipXMLArchives(inputs, asset, writer)
		case "tree-map":
			err = executor.mapTrees(inputs, asset, writer)
		default:
			err = fmt.Errorf("layout transform %q is not executed", kind)
		}
		if err != nil {
			return fmt.Errorf("layout asset %q: %w", asset.Destination, err)
		}
	}
	return nil
}

// input resolves one layout input. A raw directory without a path is a directory. Every other input is a file,
// or a relative link when a raw directory member is one.
func (executor *layoutExecutor) input(reference Reference) (layoutInput, error) {
	artifact := executor.execution.artifacts[reference.Artifact]
	if artifact.Kind == "directory" && reference.Path == "" {
		root, err := layoutDirectory(artifact.Root)
		if err != nil {
			return layoutInput{}, fmt.Errorf("input %s: %w", artifact.ID, err)
		}
		return layoutInput{path: root, kind: "directory"}, nil
	}
	if artifact.Kind == "directory" && artifact.Tree == nil {
		input, handled, err := executor.directoryMember(artifact, reference)
		if handled || err != nil {
			return input, err
		}
	}
	file, err := executor.resolveFile(&reference)
	if err != nil {
		return layoutInput{}, err
	}
	info, err := os.Stat(file)
	if err != nil {
		return layoutInput{}, err
	}
	return layoutInput{path: file, info: info, kind: "file"}, nil
}

// directoryMember resolves a linked member of a raw directory. An absolute link is a Bazel transport file.
// A relative link stays a link. Any other member goes through the ordinary file resolution.
func (executor *layoutExecutor) directoryMember(artifact Artifact, reference Reference) (layoutInput, bool, error) {
	root, err := layoutDirectory(artifact.Root)
	if err != nil {
		return layoutInput{}, true, fmt.Errorf("input %s: %w", artifact.ID, err)
	}
	member := filepath.Join(root, filepath.FromSlash(reference.Path))
	info, err := os.Lstat(member)
	if err != nil || info.Mode()&os.ModeSymlink == 0 {
		return layoutInput{}, false, nil
	}
	target, err := filemetadata.ReadLinkTarget(member)
	if err != nil {
		return layoutInput{}, true, err
	}
	if !filepath.IsAbs(target) {
		return layoutInput{path: member, info: info, kind: "symlink", target: target}, true, nil
	}
	source, sourceInfo, transportRoot, err := resolveTransportFile(target, reference.Path, executor.transportRoots[artifact.ID])
	if err != nil {
		return layoutInput{}, true, fmt.Errorf("input %s/%s: %w", artifact.ID, reference.Path, err)
	}
	executor.transportRoots[artifact.ID] = transportRoot
	return layoutInput{path: source, info: sourceInfo, kind: "file"}, true, nil
}

// layoutDirectory accepts a directory root and returns its physical path.
//
// A Bazel sandbox may mount an input directory as a symlink to the real artifact. That root is accepted: the
// resolved path must be a directory. A relative link inside a tree stays a layout entry of its own, see
// directoryMember and transportEntry.
func layoutDirectory(root string) (string, error) {
	root, err := filepath.Abs(root)
	if err != nil {
		return "", err
	}
	resolved, err := evalSymlinks(root)
	if err != nil {
		return "", fmt.Errorf("layout source is not a directory: %s", root)
	}
	info, err := os.Lstat(resolved)
	if err != nil || !info.IsDir() {
		return "", fmt.Errorf("layout source is not a directory: %s", root)
	}
	return resolved, nil
}

// copyAsset writes a plain copy. A directory is copied with every descendant in byte-sorted path order.
func (executor *layoutExecutor) copyAsset(input layoutInput, asset LayoutAsset, writer layoutWriter) error {
	switch input.kind {
	case "symlink":
		return writer.symlink(asset.Destination, input.target)
	case "file":
		return copyLayoutEntry(input.path, input.info, asset.Destination, asset.Mode, writer)
	}
	info, err := os.Lstat(input.path)
	if err != nil {
		return err
	}
	if err := writer.directory(asset.Destination, modeOr(asset.Mode, filemetadata.Permissions(info))); err != nil {
		return err
	}
	var entries []layoutTreeEntry
	if err := walkLayoutTree(input.path, func(entry layoutTreeEntry) error {
		entries = append(entries, entry)
		return nil
	}); err != nil {
		return err
	}
	slices.SortFunc(entries, func(first, second layoutTreeEntry) int { return strings.Compare(first.relative, second.relative) })
	transportRoot := ""
	for _, entry := range entries {
		source, info, err := executor.transportEntry(entry, &transportRoot)
		if err != nil {
			return err
		}
		if err := copyLayoutEntry(source, info, joinLayoutPath(asset.Destination, entry.relative), asset.Mode, writer); err != nil {
			return err
		}
	}
	return nil
}

// transportEntry replaces an absolute link by the Bazel transport file it names. Every other entry is returned as is.
func (executor *layoutExecutor) transportEntry(entry layoutTreeEntry, transportRoot *string) (string, os.FileInfo, error) {
	if entry.info.Mode()&os.ModeSymlink == 0 {
		return entry.full, entry.info, nil
	}
	target, err := filemetadata.ReadLinkTarget(entry.full)
	if err != nil {
		return "", nil, err
	}
	if !filepath.IsAbs(target) {
		return entry.full, entry.info, nil
	}
	source, info, root, err := resolveTransportFile(target, entry.relative, *transportRoot)
	if err != nil {
		return "", nil, err
	}
	*transportRoot = root
	return source, info, nil
}

// copyLayoutEntry writes one file, link, or directory. Mode zero keeps the source mode.
func copyLayoutEntry(source string, info os.FileInfo, destination string, mode uint32, writer layoutWriter) error {
	switch {
	case info.Mode().IsRegular():
		content, err := os.ReadFile(source)
		if err != nil {
			return err
		}
		return writer.file(destination, content, modeOr(mode, filemetadata.Permissions(info)))
	case info.Mode()&os.ModeSymlink != 0:
		target, err := filemetadata.ReadLinkTarget(source)
		if err != nil {
			return err
		}
		return writer.symlink(destination, target)
	case info.IsDir():
		return writer.directory(destination, modeOr(mode, filemetadata.Permissions(info)))
	}
	return fmt.Errorf("unsupported layout source: %s", source)
}

type layoutTreeEntry struct {
	relative string
	full     string
	info     os.FileInfo
}

// walkLayoutTree visits a directory in pre-order and raw directory order, the order of Kotlin's Files.walk.
// It reads every directory through (*os.File).ReadDir, which does not sort. It does not follow links.
// A visitor can return fs.SkipDir for a directory to skip its descendants.
func walkLayoutTree(root string, visit func(layoutTreeEntry) error) error {
	var walk func(directory, prefix string) error
	walk = func(directory, prefix string) error {
		handle, err := os.Open(directory)
		if err != nil {
			return err
		}
		entries, err := handle.ReadDir(-1)
		handle.Close()
		if err != nil {
			return err
		}
		for _, entry := range entries {
			info, err := entry.Info()
			if err != nil {
				return err
			}
			item := layoutTreeEntry{relative: prefix + entry.Name(), full: filepath.Join(directory, entry.Name()), info: info}
			if err := visit(item); err != nil {
				if err == fs.SkipDir && info.IsDir() {
					continue
				}
				return err
			}
			if info.IsDir() {
				if err := walk(item.full, item.relative+"/"); err != nil {
					return err
				}
			}
		}
		return nil
	}
	return walk(root, "")
}

func compileLayoutExcludes(patterns []string) ([]javaglob.Matcher, error) {
	matchers := make([]javaglob.Matcher, 0, len(patterns))
	for _, pattern := range patterns {
		matcher, err := javaglob.Compile(pattern)
		if err != nil {
			return nil, fmt.Errorf("invalid exclude: %w", err)
		}
		matchers = append(matchers, matcher)
	}
	return matchers, nil
}

// layoutIncludeRule is one Includes pattern. A leading `!` in the pattern text makes it an exclude.
type layoutIncludeRule struct {
	matcher javaglob.Matcher
	exclude bool
}

func compileLayoutIncludes(patterns []string) ([]layoutIncludeRule, error) {
	rules := make([]layoutIncludeRule, 0, len(patterns))
	for _, pattern := range patterns {
		exclude := strings.HasPrefix(pattern, "!")
		glob := strings.TrimPrefix(pattern, "!")
		if glob == "" {
			return nil, fmt.Errorf("invalid include: empty pattern %q", pattern)
		}
		matcher, err := javaglob.Compile(glob)
		if err != nil {
			return nil, fmt.Errorf("invalid include: %w", err)
		}
		rules = append(rules, layoutIncludeRule{matcher: matcher, exclude: exclude})
	}
	return rules, nil
}

// includesEntry applies the Includes rules to one relative path. The last matching rule decides. Without a match, the
// entry is written when every rule excludes, and dropped otherwise. No rule at all writes every entry.
func includesEntry(rules []layoutIncludeRule, name string) bool {
	included := !slices.ContainsFunc(rules, func(rule layoutIncludeRule) bool { return !rule.exclude })
	for _, rule := range rules {
		if rule.matcher.Match(name) {
			included = !rule.exclude
		}
	}
	return included
}

func compileLayoutExecutables(patterns []string) ([]javaglob.Matcher, error) {
	matchers := make([]javaglob.Matcher, 0, len(patterns))
	for _, pattern := range patterns {
		matcher, err := javaglob.Compile(pattern)
		if err != nil {
			return nil, fmt.Errorf("invalid executable pattern: %w", err)
		}
		matchers = append(matchers, matcher)
	}
	return matchers, nil
}

// executableMode adds the executable bits to the mode of a regular file whose relative path an Executables pattern matches.
func executableMode(mode uint32, name string, executables []javaglob.Matcher) uint32 {
	if slices.ContainsFunc(executables, func(matcher javaglob.Matcher) bool { return matcher.Match(name) }) {
		return mode | 0o111
	}
	return mode
}

// mapTrees copies the entries of every source directory that a mapping selects. The first mapping per entry wins.
func (executor *layoutExecutor) mapTrees(inputs []layoutInput, asset LayoutAsset, writer layoutWriter) error {
	excludes, err := compileLayoutExcludes(asset.Transform.Excludes)
	if err != nil {
		return err
	}
	directoryExcludes, err := compileLayoutExcludes(asset.Transform.DirectoryExcludes)
	if err != nil {
		return err
	}
	executables, err := compileLayoutExecutables(asset.Transform.Executables)
	if err != nil {
		return err
	}
	mappings := asset.Transform.Mappings
	matchers := make([]javaglob.Matcher, 0, len(mappings))
	for _, mapping := range mappings {
		matcher, err := javaglob.Compile(mappingPattern(mapping))
		if err != nil {
			return err
		}
		matchers = append(matchers, matcher)
	}
	for _, input := range inputs {
		if input.kind != "directory" {
			return fmt.Errorf("a tree-map source must be a directory: %s", input.path)
		}
		transportRoot := ""
		err := walkLayoutTree(input.path, func(entry layoutTreeEntry) error {
			if entry.info.IsDir() {
				if slices.ContainsFunc(directoryExcludes, func(matcher javaglob.Matcher) bool { return matcher.Match(entry.relative) }) {
					return fs.SkipDir
				}
			} else if slices.ContainsFunc(excludes, func(matcher javaglob.Matcher) bool { return matcher.Match(entry.relative) }) {
				return nil
			}
			index := slices.IndexFunc(matchers, func(matcher javaglob.Matcher) bool { return matcher.Match(entry.relative) })
			if index < 0 {
				return nil
			}
			remainder, ok := stripLayoutPath(entry.relative, mappings[index].StripComponents)
			if !ok {
				return nil
			}
			target := joinLayoutPath(asset.Destination, joinLayoutPath(mappings[index].Destination, remainder))
			source, info, err := executor.transportEntry(entry, &transportRoot)
			if err != nil {
				return err
			}
			mode := asset.Mode
			if info.Mode().IsRegular() && len(executables) != 0 {
				mode = executableMode(modeOr(mode, filemetadata.Permissions(info)), entry.relative, executables)
			}
			return copyLayoutEntry(source, info, target, mode, writer)
		})
		if err != nil {
			return err
		}
	}
	return nil
}

// extractArchive writes the entries of one archive. One mapping serves the whole archive: the first mapping in
// declaration order that matches any stripped entry name. With mappings, an entry no mapping matches is dropped.
// The Includes rules drop an entry before the mapping is selected, so a dropped entry selects no mapping.
func (executor *layoutExecutor) extractArchive(input layoutInput, asset LayoutAsset, writer layoutWriter) error {
	if input.kind != "file" {
		return fmt.Errorf("archive-tree requires an archive file: %s", input.path)
	}
	transform := asset.Transform
	includes, err := compileLayoutIncludes(transform.Includes)
	if err != nil {
		return err
	}
	executables, err := compileLayoutExecutables(transform.Executables)
	if err != nil {
		return err
	}
	archive, err := openLayoutArchive(input.path, executor.scratch)
	if err != nil {
		return err
	}
	defer archive.close()
	var names []string
	if err := archive.visit(func(entry layoutArchiveEntry) error {
		if stripped, ok := stripLayoutPath(entry.name, transform.StripComponents); ok && includesEntry(includes, stripped) {
			names = append(names, stripped)
		}
		return nil
	}); err != nil {
		return err
	}
	selected := -1
	var matcher javaglob.Matcher
	for index, mapping := range transform.Mappings {
		candidate, err := javaglob.Compile(mappingPattern(mapping))
		if err != nil {
			return err
		}
		if slices.ContainsFunc(names, candidate.Match) {
			selected, matcher = index, candidate
			break
		}
	}
	written := make(map[string]bool)
	return archive.visit(func(entry layoutArchiveEntry) error {
		stripped, ok := stripLayoutPath(entry.name, transform.StripComponents)
		if !ok || !includesEntry(includes, stripped) {
			return nil
		}
		mapped := stripped
		if len(transform.Mappings) != 0 {
			if selected < 0 || !matcher.Match(stripped) {
				return nil
			}
			remainder, ok := stripLayoutPath(stripped, transform.Mappings[selected].StripComponents)
			if !ok {
				return nil
			}
			mapped = joinLayoutPath(transform.Mappings[selected].Destination, remainder)
		}
		target := joinLayoutPath(asset.Destination, mapped)
		if archive.rejectsDuplicates() {
			if written[target] {
				return fmt.Errorf("duplicate archive destination %q in %s", target, input.path)
			}
			written[target] = true
		}
		mode := asset.Mode
		if mode == 0 {
			mode = entry.mode & 0o755
		}
		switch entry.kind {
		case "directory":
			return writer.directory(target, modeOr(mode, 0o755))
		case "file":
			content, err := entry.content()
			if err != nil {
				return fmt.Errorf("%s: %s: %w", input.path, entry.name, err)
			}
			return writer.file(target, content, executableMode(modeOr(mode, 0o644), stripped, executables))
		case "symlink":
			return writer.symlink(target, entry.target)
		}
		return fmt.Errorf("unknown archive entry kind %q", entry.kind)
	})
}

// gzipXMLArchives reads the .xml entries of every source archive in central-directory order and writes each one as
// <destination>/<name>.gzip. A source is a .zip or a .jar. A file that is not XML, and a link, fail with the archive name.
// The entry keeps the deflate stream of the archive, so the Kotlin build and this packer write the same bytes.
func (executor *layoutExecutor) gzipXMLArchives(inputs []layoutInput, asset LayoutAsset, writer layoutWriter) error {
	for _, input := range inputs {
		name := strings.ToLower(filepath.Base(input.path))
		if input.kind != "file" || !strings.HasSuffix(name, ".zip") && !strings.HasSuffix(name, ".jar") {
			return fmt.Errorf("a gzip-xml-archive transform reads a zip or jar archive: %s", input.path)
		}
		archive, err := openLayoutArchive(input.path, executor.scratch)
		if err != nil {
			return err
		}
		err = archive.visit(func(entry layoutArchiveEntry) error {
			switch {
			case entry.kind == "directory":
				return nil
			case entry.kind != "file" || !strings.HasSuffix(entry.name, ".xml"):
				return fmt.Errorf("unexpected file %q in %s", entry.name, input.path)
			}
			stream, err := entry.deflate()
			if err != nil {
				return fmt.Errorf("%s: %s: %w", input.path, entry.name, err)
			}
			return writer.file(joinLayoutPath(asset.Destination, entry.name+".gzip"), gzipMember(stream), 0o644)
		})
		archive.close()
		if err != nil {
			return err
		}
	}
	return nil
}

// gzipMemberHeader starts one gzip member with the deflate method, no flags, a zero modification time, no extra
// flags, and the unknown operating system.
var gzipMemberHeader = []byte{0x1f, 0x8b, 8, 0, 0, 0, 0, 0, 0, 255}

// gzipMember wraps the deflate stream as one gzip member: the header, the stream, the CRC-32 and the payload size.
func gzipMember(stream deflateStream) []byte {
	member := make([]byte, 0, len(gzipMemberHeader)+len(stream.data)+8)
	member = append(member, gzipMemberHeader...)
	member = append(member, stream.data...)
	member = binary.LittleEndian.AppendUint32(member, stream.crc)
	return binary.LittleEndian.AppendUint32(member, stream.size)
}
