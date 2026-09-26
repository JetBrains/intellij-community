//go:build windows

package pluginpack

import (
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"unsafe"
)

var getFinalPathNameByHandle = syscall.NewLazyDLL("kernel32.dll").NewProc("GetFinalPathNameByHandleW")

// evalSymlinks returns the path with every link and every junction resolved. It asks the file system for the final
// path of an opened handle, because filepath.EvalSymlinks stops at a junction since Go 1.23, and Bazel reaches every
// external repository through one. A missing path fails with an error that os.IsNotExist reports.
func evalSymlinks(path string) (string, error) {
	absolute, err := filepath.Abs(path)
	if err != nil {
		return "", err
	}
	name := absolute
	if !strings.HasPrefix(name, `\\`) {
		name = `\\?\` + name
	}
	pointer, err := syscall.UTF16PtrFromString(name)
	if err != nil {
		return "", &os.PathError{Op: "open", Path: path, Err: err}
	}
	share := uint32(syscall.FILE_SHARE_READ | syscall.FILE_SHARE_WRITE | syscall.FILE_SHARE_DELETE)
	handle, err := syscall.CreateFile(pointer, 0, share, nil, syscall.OPEN_EXISTING, syscall.FILE_FLAG_BACKUP_SEMANTICS, 0)
	if err != nil {
		return "", &os.PathError{Op: "open", Path: path, Err: err}
	}
	defer syscall.CloseHandle(handle)
	buffer := make([]uint16, syscall.MAX_LONG_PATH)
	for {
		length, _, err := getFinalPathNameByHandle.Call(uintptr(handle), uintptr(unsafe.Pointer(&buffer[0])), uintptr(len(buffer)), 0)
		if length == 0 {
			return "", &os.PathError{Op: "GetFinalPathNameByHandle", Path: path, Err: err}
		}
		if int(length) < len(buffer) {
			return stripFinalPathPrefix(syscall.UTF16ToString(buffer[:length])), nil
		}
		buffer = make([]uint16, length)
	}
}

// stripFinalPathPrefix removes the extended-length prefix the final path carries.
func stripFinalPathPrefix(final string) string {
	if rest, ok := strings.CutPrefix(final, `\\?\UNC\`); ok {
		return `\\` + rest
	}
	if rest, ok := strings.CutPrefix(final, `\\?\`); ok {
		return rest
	}
	return final
}
