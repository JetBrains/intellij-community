package pluginpack

import (
	"archive/tar"
	"archive/zip"
	"bufio"
	"compress/gzip"
	"fmt"
	"io"
	"math"
	"os"
	"path/filepath"
	"strings"

	"github.com/klauspost/compress/zstd"
)

// zipCreatorUnix is the Unix platform of a zip creator byte. The Kotlin reader masks the byte to its low nibble, so
// creator 19, the MacOSX platform, is Unix too.
const zipCreatorUnix = 3

// layoutArchiveEntry is one archive member. Its name has one trailing slash removed and is never empty.
// content reads the bytes of a file entry. deflate reads the stored deflate stream of a zip file entry and is nil
// for a tar entry. Both are valid until the visitor moves to the next entry.
type layoutArchiveEntry struct {
	name    string
	kind    string
	mode    uint32
	target  string
	content func() ([]byte, error)
	deflate func() (deflateStream, error)
}

// deflateStream is one raw deflate stream with the CRC-32 and the size of its payload.
type deflateStream struct {
	data []byte
	crc  uint32
	size uint32
}

// layoutArchive visits an archive in the order the Kotlin reader used: the central directory of a zip, the stream of a tar.
type layoutArchive interface {
	visit(func(layoutArchiveEntry) error) error
	// rejectsDuplicates is true when the Kotlin reader used another entry order, so a repeated destination has no replay.
	rejectsDuplicates() bool
	close() error
}

// openLayoutArchive selects the reader by the lower-cased file name: .zip and .jar, .zip.zst, .tar.gz and .tgz.
func openLayoutArchive(file string, scratch *layoutScratch) (layoutArchive, error) {
	name := strings.ToLower(filepath.Base(file))
	switch {
	case strings.HasSuffix(name, ".zip"), strings.HasSuffix(name, ".jar"):
		reader, err := zip.OpenReader(file)
		if err != nil {
			return nil, err
		}
		return &zipLayoutArchive{reader: reader}, nil
	case strings.HasSuffix(name, ".zip.zst"):
		decoded, err := decodeZstdArchive(file, scratch)
		if err != nil {
			return nil, err
		}
		reader, err := zip.OpenReader(decoded)
		if err != nil {
			return nil, err
		}
		return &zipLayoutArchive{reader: reader, streamed: true}, nil
	case strings.HasSuffix(name, ".tar.gz"), strings.HasSuffix(name, ".tgz"):
		return &tarLayoutArchive{file: file}, nil
	}
	return nil, fmt.Errorf("unsupported layout archive %q", file)
}

// decodeZstdArchive writes the decoded zip into the scratch directory, so the central directory can be read.
func decodeZstdArchive(file string, scratch *layoutScratch) (string, error) {
	input, err := os.Open(file)
	if err != nil {
		return "", err
	}
	defer input.Close()
	decoder, err := zstd.NewReader(input)
	if err != nil {
		return "", err
	}
	defer decoder.Close()
	directory, err := scratch.directory("archive")
	if err != nil {
		return "", err
	}
	decoded := filepath.Join(directory, "archive.zip")
	output, err := os.OpenFile(decoded, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return "", err
	}
	if _, err := decoder.WriteTo(output); err != nil {
		output.Close()
		return "", fmt.Errorf("%s: %w", file, err)
	}
	return decoded, output.Close()
}

// zipLayoutArchive reads a zip in central-directory order.
// A plain zip honors the Unix creator. Mode bits and a link type come from the external attributes. They apply only
// when the low nibble of the creator byte is the Unix platform. A streamed zip, the .zip.zst case, has mode 0 and no
// links. The Kotlin stream reader saw no creator and no external attributes. A link-typed entry is a file that holds
// the target text.
type zipLayoutArchive struct {
	reader   *zip.ReadCloser
	streamed bool
}

func (archive *zipLayoutArchive) rejectsDuplicates() bool {
	return archive.streamed
}

func (archive *zipLayoutArchive) close() error {
	return archive.reader.Close()
}

func (archive *zipLayoutArchive) visit(visit func(layoutArchiveEntry) error) error {
	for _, file := range archive.reader.File {
		name, ok, err := normalizeLayoutArchiveName(file.Name)
		if err != nil {
			return err
		}
		if !ok {
			continue
		}
		entry := layoutArchiveEntry{name: name, kind: "file",
			content: func() ([]byte, error) { return readZipEntry(file) },
			deflate: func() (deflateStream, error) { return zipDeflateStream(file) }}
		if !archive.streamed && (file.CreatorVersion>>8)&0x0f == zipCreatorUnix {
			unixMode := file.ExternalAttrs >> 16
			entry.mode = unixMode & 0o777
			if unixMode&0o170000 == 0o120000 {
				entry.kind = "symlink"
			}
		}
		if entry.kind != "symlink" && strings.HasSuffix(file.Name, "/") {
			entry.kind = "directory"
		}
		if entry.kind == "symlink" {
			target, err := entry.content()
			if err != nil {
				return err
			}
			entry.target = string(target)
		}
		if err := visit(entry); err != nil {
			return err
		}
	}
	return nil
}

func readZipEntry(file *zip.File) ([]byte, error) {
	input, err := file.Open()
	if err != nil {
		return nil, err
	}
	defer input.Close()
	return io.ReadAll(input)
}

// zipDeflateStream returns the deflate stream of a zip entry as the archive stores it. A DEFLATED entry is copied.
// A STORED entry becomes stored deflate blocks. No deflater runs, so the bytes depend on the archive alone.
func zipDeflateStream(file *zip.File) (deflateStream, error) {
	if file.UncompressedSize64 > math.MaxUint32 {
		return deflateStream{}, fmt.Errorf("%s is larger than 4 GiB", file.Name)
	}
	input, err := file.OpenRaw()
	if err != nil {
		return deflateStream{}, err
	}
	data, err := io.ReadAll(input)
	if err != nil {
		return deflateStream{}, err
	}
	stream := deflateStream{crc: file.CRC32, size: uint32(file.UncompressedSize64)}
	switch file.Method {
	case zip.Deflate:
		stream.data = data
	case zip.Store:
		stream.data = storedDeflateBlocks(data)
	default:
		return deflateStream{}, fmt.Errorf("%s uses compression method %d, not deflate", file.Name, file.Method)
	}
	return stream, nil
}

// storedDeflateBlocks wraps the data in deflate blocks of type 0, each of at most 65535 bytes. The last block is final.
func storedDeflateBlocks(data []byte) []byte {
	blocks := make([]byte, 0, len(data)+5*(len(data)/math.MaxUint16+1))
	for {
		chunk := data
		if len(chunk) > math.MaxUint16 {
			chunk = data[:math.MaxUint16]
		}
		data = data[len(chunk):]
		final := byte(0)
		if len(data) == 0 {
			final = 1
		}
		length := uint16(len(chunk))
		blocks = append(blocks, final, byte(length), byte(length>>8), ^byte(length), ^byte(length>>8))
		blocks = append(blocks, chunk...)
		if final == 1 {
			return blocks
		}
	}
}

// tarLayoutArchive reads a gzip tar in stream order. Only the first gzip member is read, as the Kotlin reader did.
// A hard link is a file entry that holds the bytes of its target and the mode of the link header. The target must be
// a regular file of the same archive.
type tarLayoutArchive struct {
	file string
	// hardLinkTargets holds the normalized name of every hard link target an earlier visit met. The next visit keeps
	// the bytes of these entries, so a link reads its target from memory. A visit that meets a link before this set
	// names its target reads the archive again up to the target.
	hardLinkTargets map[string]bool
}

func (archive *tarLayoutArchive) rejectsDuplicates() bool {
	return false
}

func (archive *tarLayoutArchive) close() error {
	return nil
}

func (archive *tarLayoutArchive) visit(visit func(layoutArchiveEntry) error) error {
	input, err := os.Open(archive.file)
	if err != nil {
		return err
	}
	defer input.Close()
	decompressed, err := gzip.NewReader(bufio.NewReader(input))
	if err != nil {
		return fmt.Errorf("%s: %w", archive.file, err)
	}
	defer decompressed.Close()
	decompressed.Multistream(false)
	reader := tar.NewReader(decompressed)
	// retained holds the bytes of every hard link target of this visit. resolved maps a hard link to its first file, so
	// a link to a link reads the file.
	retained := make(map[string][]byte)
	resolved := make(map[string]string)
	for {
		header, err := reader.Next()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return fmt.Errorf("%s: %w", archive.file, err)
		}
		name, ok, err := normalizeLayoutArchiveName(header.Name)
		if err != nil {
			return err
		}
		if !ok {
			continue
		}
		entry := layoutArchiveEntry{name: name, mode: uint32(header.Mode) & 0o777, content: func() ([]byte, error) { return io.ReadAll(reader) }}
		switch {
		case header.Typeflag == tar.TypeSymlink:
			entry.kind, entry.target = "symlink", header.Linkname
		case header.Typeflag == tar.TypeDir, header.Typeflag == tar.TypeReg && strings.HasSuffix(header.Name, "/"):
			entry.kind = "directory"
		case header.Typeflag == tar.TypeReg:
			entry.kind = "file"
			if archive.hardLinkTargets[name] {
				data, err := io.ReadAll(reader)
				if err != nil {
					return fmt.Errorf("%s: %s: %w", archive.file, header.Name, err)
				}
				retained[name] = data
				entry.content = func() ([]byte, error) { return data, nil }
			}
		case header.Typeflag == tar.TypeLink:
			target, ok, err := normalizeLayoutArchiveName(header.Linkname)
			if err != nil || !ok {
				return fmt.Errorf("hard link %q in %s has the unsafe target %q", header.Name, archive.file, header.Linkname)
			}
			if first, ok := resolved[target]; ok {
				target = first
			}
			resolved[name] = target
			if archive.hardLinkTargets == nil {
				archive.hardLinkTargets = make(map[string]bool)
			}
			archive.hardLinkTargets[target] = true
			entry.kind = "file"
			entry.content = func() ([]byte, error) {
				if data, ok := retained[target]; ok {
					return data, nil
				}
				return archive.readEntry(target)
			}
		default:
			return fmt.Errorf("unsupported archive entry %q in %s", header.Name, archive.file)
		}
		if err := visit(entry); err != nil {
			return err
		}
	}
}

// readEntry reads the archive again and returns the bytes of the regular file with the normalized name.
// It serves a hard link whose target this visit did not retain.
func (archive *tarLayoutArchive) readEntry(name string) ([]byte, error) {
	input, err := os.Open(archive.file)
	if err != nil {
		return nil, err
	}
	defer input.Close()
	decompressed, err := gzip.NewReader(bufio.NewReader(input))
	if err != nil {
		return nil, fmt.Errorf("%s: %w", archive.file, err)
	}
	defer decompressed.Close()
	decompressed.Multistream(false)
	reader := tar.NewReader(decompressed)
	for {
		header, err := reader.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, fmt.Errorf("%s: %w", archive.file, err)
		}
		current, ok, err := normalizeLayoutArchiveName(header.Name)
		if err != nil {
			return nil, err
		}
		if !ok || current != name {
			continue
		}
		if header.Typeflag != tar.TypeReg || strings.HasSuffix(header.Name, "/") {
			break
		}
		return io.ReadAll(reader)
	}
	return nil, fmt.Errorf("hard link target %q is not a file of %s", name, archive.file)
}

// normalizeLayoutArchiveName removes one trailing slash and every leading `./`, then validates the name before any write.
// An empty name is skipped, as is the `.` root entry of an archive created with `tar -c .`.
func normalizeLayoutArchiveName(name string) (string, bool, error) {
	name = strings.TrimSuffix(name, "/")
	for strings.HasPrefix(name, "./") {
		name = name[2:]
	}
	if name == "" || name == "." {
		return "", false, nil
	}
	if err := validateRelativePath(name); err != nil {
		return "", false, fmt.Errorf("unsafe archive path: %w", err)
	}
	return name, true, nil
}
