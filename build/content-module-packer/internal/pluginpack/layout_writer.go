package pluginpack

import (
	"fmt"
	"os"
	"path"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"unicode/utf8"
)

// layoutWriter receives the entries of one layout payload in write order.
type layoutWriter interface {
	directory(name string, mode uint32) error
	file(name string, content []byte, mode uint32) error
	symlink(name, target string) error
}

// layoutTreeWriter writes one tree. The first claim of a path wins. A claim of another kind fails.
// An implicit parent gets mode 0755. Mode zero means 0755 for a directory and 0644 for a file.
type layoutTreeWriter struct {
	root    string
	claimed map[string]string
	links   []layoutLink
}

type layoutLink struct {
	name   string
	target string
}

func newLayoutTreeWriter(root string) *layoutTreeWriter {
	return &layoutTreeWriter{root: root, claimed: make(map[string]string)}
}

func (writer *layoutTreeWriter) path(name string) string {
	return filepath.Join(writer.root, filepath.FromSlash(name))
}

// claim records the kind of a path. It reports whether this is the first claim.
func (writer *layoutTreeWriter) claim(name, kind string) (bool, error) {
	if err := validateRelativePath(name); err != nil {
		return false, err
	}
	previous, exists := writer.claimed[name]
	if !exists {
		writer.claimed[name] = kind
		return true, nil
	}
	if previous != kind {
		return false, fmt.Errorf("layout asset %q conflicts with a %s", name, previous)
	}
	return false, nil
}

func (writer *layoutTreeWriter) directory(name string, mode uint32) error {
	if name == "" {
		return os.Chmod(writer.root, os.FileMode(modeOr(mode, 0o755)))
	}
	first, err := writer.claim(name, "directory")
	if err != nil {
		return err
	}
	target := writer.path(name)
	info, err := os.Lstat(target)
	if err == nil {
		if !info.IsDir() {
			return fmt.Errorf("layout asset %q conflicts with a file", name)
		}
		if first {
			return os.Chmod(target, os.FileMode(modeOr(mode, 0o755)))
		}
		return nil
	}
	if !os.IsNotExist(err) {
		return err
	}
	if err := writer.createParents(name); err != nil {
		return err
	}
	if err := os.Mkdir(target, 0o755); err != nil {
		return err
	}
	return os.Chmod(target, os.FileMode(modeOr(mode, 0o755)))
}

func (writer *layoutTreeWriter) file(name string, content []byte, mode uint32) error {
	first, err := writer.claim(name, "file")
	if err != nil || !first {
		return err
	}
	if err := writer.createParents(name); err != nil {
		return err
	}
	target := writer.path(name)
	output, err := os.OpenFile(target, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	if _, err := output.Write(content); err != nil {
		output.Close()
		return err
	}
	if err := output.Close(); err != nil {
		return err
	}
	return os.Chmod(target, os.FileMode(modeOr(mode, 0o644)))
}

func (writer *layoutTreeWriter) symlink(name, target string) error {
	target = normalizeLayoutLinkTarget(target)
	if err := validateLayoutLink(name, target); err != nil {
		return err
	}
	first, err := writer.claim(name, "symlink")
	if err != nil || !first {
		return err
	}
	if err := writer.createParents(name); err != nil {
		return err
	}
	writer.links = append(writer.links, layoutLink{name: name, target: target})
	return nil
}

// finish creates the links after every other entry, so a link to a directory finds its target. Windows gives a link
// the kind of the target that exists when the link is created.
func (writer *layoutTreeWriter) finish() error {
	links, err := orderLinks(writer.links)
	if err != nil {
		return err
	}
	for _, link := range links {
		if err := os.Symlink(link.target, writer.path(link.name)); err != nil {
			return err
		}
	}
	return nil
}

// orderLinks sorts the links so that a link comes after every link its target path traverses. Windows gives a link
// the kind of the target that exists at creation, so a link created through a pending link would become a file link.
// Links that depend on nothing keep their order.
func orderLinks(links []layoutLink) ([]layoutLink, error) {
	pending := make(map[string]bool, len(links))
	for _, link := range links {
		pending[link.name] = true
	}
	ordered := make([]layoutLink, 0, len(links))
	for remaining := links; len(remaining) != 0; {
		var deferred []layoutLink
		for _, link := range remaining {
			if linkTraversesPending(link, pending) {
				deferred = append(deferred, link)
				continue
			}
			delete(pending, link.name)
			ordered = append(ordered, link)
		}
		if len(deferred) == len(remaining) {
			return nil, fmt.Errorf("symlink cycle at %q", deferred[0].name)
		}
		remaining = deferred
	}
	return ordered, nil
}

// linkTraversesPending reports whether the target path of the link passes through, or ends at, a pending link.
func linkTraversesPending(link layoutLink, pending map[string]bool) bool {
	current := path.Dir(link.name)
	for _, component := range strings.Split(link.target, "/") {
		switch component {
		case "", ".":
			continue
		case "..":
			current = path.Dir(current)
			continue
		}
		current = path.Join(current, component)
		if pending[current] {
			return true
		}
	}
	return false
}

// createParents creates the missing ancestors of name. The first existing ancestor must be a directory, not a link.
func (writer *layoutTreeWriter) createParents(name string) error {
	var missing []string
	for parent := path.Dir(name); parent != "."; parent = path.Dir(parent) {
		if writer.claimed[parent] == "symlink" {
			return fmt.Errorf("layout asset parent %q is not a directory", parent)
		}
		info, err := os.Lstat(writer.path(parent))
		if err == nil {
			if !info.IsDir() {
				return fmt.Errorf("layout asset parent %q is not a directory", parent)
			}
			break
		}
		if !os.IsNotExist(err) {
			return err
		}
		missing = append(missing, parent)
	}
	for _, directory := range slices.Backward(missing) {
		target := writer.path(directory)
		if err := os.Mkdir(target, 0o755); err != nil {
			return err
		}
		if err := os.Chmod(target, 0o755); err != nil {
			return err
		}
	}
	return nil
}

// normalizeLayoutLinkTarget spells a link target the way java.nio.file.Path.of does, because the Kotlin writer
// created every link through it. Repeated slashes collapse into one, and a trailing slash is removed.
// The components `.` and `..` stay as they are. A target of slashes only becomes `/`.
func normalizeLayoutLinkTarget(target string) string {
	if !strings.Contains(target, "//") && !strings.HasSuffix(target, "/") {
		return target
	}
	trimmed := strings.TrimRight(target, "/")
	if trimmed == "" {
		return "/"
	}
	var normalized strings.Builder
	previous := rune(0)
	for _, c := range trimmed {
		if c == '/' && previous == '/' {
			continue
		}
		normalized.WriteRune(c)
		previous = c
	}
	return normalized.String()
}

// validateLayoutLink accepts a relative link whose resolved target stays inside the tree.
func validateLayoutLink(name, target string) error {
	if target == "" || !utf8.ValidString(target) || path.IsAbs(target) || strings.ContainsAny(target, "\\:\x00\r\n") {
		return fmt.Errorf("layout asset link %q escapes its tree: %s", name, target)
	}
	resolved := path.Join(path.Dir(name), target)
	if resolved == ".." || strings.HasPrefix(resolved, "../") {
		return fmt.Errorf("layout asset link %q escapes its tree: %s", name, target)
	}
	return nil
}

type layoutEntry struct {
	name string
	file string
}

// layoutEntriesWriter writes the file entries of one jar source into numbered files.
// The first destination wins, a directory is a no-op, and a link fails.
type layoutEntriesWriter struct {
	root    string
	names   map[string]bool
	entries []layoutEntry
}

func newLayoutEntriesWriter(root string) *layoutEntriesWriter {
	return &layoutEntriesWriter{root: root, names: make(map[string]bool)}
}

func (writer *layoutEntriesWriter) directory(string, uint32) error {
	return nil
}

func (writer *layoutEntriesWriter) file(name string, content []byte, _ uint32) error {
	if err := validateRelativePath(name); err != nil {
		return err
	}
	if writer.names[name] {
		return nil
	}
	writer.names[name] = true
	file := filepath.Join(writer.root, strconv.Itoa(len(writer.entries)))
	if err := os.WriteFile(file, content, 0o644); err != nil {
		return err
	}
	writer.entries = append(writer.entries, layoutEntry{name: name, file: file})
	return nil
}

func (writer *layoutEntriesWriter) symlink(name, _ string) error {
	return fmt.Errorf("a jar layout asset cannot contain the symbolic link %q", name)
}

// layoutFileWriter writes the one file of a layout-file operation. A directory and a link fail.
type layoutFileWriter struct {
	path    string
	written bool
}

func (writer *layoutFileWriter) directory(name string, _ uint32) error {
	return fmt.Errorf("a layout file cannot be the directory %q", name)
}

func (writer *layoutFileWriter) file(name string, content []byte, mode uint32) error {
	if err := validateRelativePath(name); err != nil {
		return err
	}
	if writer.written {
		return fmt.Errorf("a layout file writes one file, not %q", name)
	}
	writer.written = true
	if err := os.WriteFile(writer.path, content, 0o600); err != nil {
		return err
	}
	return os.Chmod(writer.path, os.FileMode(modeOr(mode, 0o644)))
}

func (writer *layoutFileWriter) symlink(name, _ string) error {
	return fmt.Errorf("a layout file cannot be the symbolic link %q", name)
}
