package helpers

import (
	"archive/zip"
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"os/exec"
	"path/filepath"
	"repair/logger"
	"runtime"
	"strings"
)

// DefaultEditor is vim because we're adults ;)
const DefaultEditor = "vim"

// GetPreferredEditorFromEnvironment returns the user's editor as defined by the
// `$EDITOR` environment variable, or the `DefaultEditor` if it is not set.
func GetPreferredEditorFromEnvironment() string {
	editor := os.Getenv("EDITOR")
	if editor == "" {
		return DefaultEditor
	}
	return editor
}
func RemoveContentOfDir(dir string) error {
	d, err := os.Open(dir)
	if err != nil {
		return err
	}
	defer d.Close()
	names, err := d.Readdirnames(-1)
	if err != nil {
		return err
	}
	for _, name := range names {
		err = os.RemoveAll(filepath.Join(dir, name))
		if err != nil {
			return err
		}
	}
	return nil
}
func RemoveDir(dir string) (err error) {
	err = RemoveContentOfDir(dir)
	err = os.Remove(dir)
	return err
}
func resolveEditorArguments(executable string, filename string) []string {
	args := []string{filename}

	if strings.Contains(executable, "Visual Studio Code.app") {
		args = append([]string{"--wait"}, args...)
	}
	// Other common editors
	return args
}

// OpenFileInEditor opens filename in a text editor.
func OpenFileInEditor(filename string) error {
	// Get the full executable path for the editor.
	executable, err := exec.LookPath(GetPreferredEditorFromEnvironment())
	if err != nil {
		return err
	}
	cmd := exec.Command(executable, resolveEditorArguments(executable, filename)...)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	return cmd.Run()
}
func WriteToFile(str string, filename string) {
	f, err := os.OpenFile(filename, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	logger.ExitWithExceptionOnError(err)
	_, err = f.WriteString("\n" + str + "\n")
	logger.ExitWithExceptionOnError(err)
	defer f.Close()
}
func RemoveFromFile(str string, filename string) error {
	read, err := ioutil.ReadFile(filename)
	logger.ExitWithExceptionOnError(err)
	newContents := strings.Replace(string(read), str, "", -1)
	err = ioutil.WriteFile(filename, []byte(newContents), 0)
	logger.ExitWithExceptionOnError(err)
	return nil
}
func MoveFile(src, dst string) error {
	sourceFileStat, err := os.Stat(src)
	if err != nil {
		return err
	}
	if !sourceFileStat.Mode().IsRegular() {
		return fmt.Errorf("%s is not a regular file", src)
	}
	source, err := os.Open(src)
	if err != nil {
		return err
	}
	source.Close()
	destination, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer destination.Close()
	_, err = io.Copy(destination, source)
	err = os.Remove(source.Name())
	return err
}
func Unzip(src, dest string) error {
	r, err := zip.OpenReader(src)
	if err != nil {
		return err
	}
	defer func() {
		if err := r.Close(); err != nil {
			panic(err)
		}
	}()

	_ = os.MkdirAll(dest, 0755)

	// Closure to address file descriptors issue with all the deferred .Close() methods
	extractAndWriteFile := func(f *zip.File) error {
		rc, err := f.Open()
		if err != nil {
			return err
		}
		defer func() {
			if err := rc.Close(); err != nil {
				panic(err)
			}
		}()

		path := filepath.Join(dest, f.Name)

		// Check for ZipSlip (Directory traversal)
		if !strings.HasPrefix(path, filepath.Clean(dest)+string(os.PathSeparator)) {
			return fmt.Errorf("illegal file path: %s", path)
		}

		if f.FileInfo().IsDir() {
			err = os.MkdirAll(path, f.Mode())
			logger.ExitWithExceptionOnError(err)
		} else {
			err = os.MkdirAll(filepath.Dir(path), 0755)
			logger.ExitWithExceptionOnError(err)
			f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
			if err != nil {
				return err
			}
			defer func() {
				if err := f.Close(); err != nil {
					panic(err)
				}
			}()

			_, err = io.Copy(f, rc)
			if err != nil {
				return err
			}
		}
		return nil
	}

	for _, f := range r.File {
		err := extractAndWriteFile(f)
		if err != nil {
			return err
		}
	}

	return nil
}
func FileExists(dir string) bool {
	//dir = filepath.Clean(dir)
	if _, err := os.Open(dir); err == nil && len(dir) > 2 {
		return true
	}
	return false
}
func getFrame(skipFrames int) runtime.Frame {
	targetFrameIndex := skipFrames + 2
	programCounters := make([]uintptr, targetFrameIndex+2)
	n := runtime.Callers(0, programCounters)
	frame := runtime.Frame{Function: "unknown"}
	if n > 0 {
		frames := runtime.CallersFrames(programCounters[:n])
		for more, frameIndex := true, 0; more && frameIndex <= targetFrameIndex; frameIndex++ {
			var frameCandidate runtime.Frame
			frameCandidate, more = frames.Next()
			if frameIndex == targetFrameIndex {
				frame = frameCandidate
			}
		}
	}

	return frame
}

// GetCallerFunction returns the name of function - caller.
func GetCallerFunction() string {
	// Skip GetCallerFunctionName and the function to get the caller of
	return getFrame(2).Function
}
func (ide *IDE) RemoveConfigurationDirectory() {
	configDir := ide.GetConfigurationDirectory()
	if FileExists(configDir) {
		if FileExists(configDir + ".old") {
			logger.WarningLogger.Printf("Backup of configuration directory (%s) already exists. ", configDir)
			if ForceAskQuestionWithOptions("Overwrite it?", []string{"y", "n"}) == "y" {
				RemoveContentOfDir(strings.TrimSuffix(configDir, "/") + ".old")
				os.Remove(strings.TrimSuffix(configDir, "/") + ".old")
			}
		}
		err := os.Rename(configDir, strings.TrimSuffix(configDir, "/")+".old")
		logger.ExitWithExceptionOnError(err)
	}
	logger.InfoLogger.Printf("Configuration directory %s been moved to %s.old", configDir, configDir[:len(configDir)])
}
