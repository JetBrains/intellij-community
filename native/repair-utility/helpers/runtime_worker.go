package helpers

import (
	"bufio"
	"errors"
	"io/ioutil"
	"os"
	"path/filepath"
	"repair/logger"
	"runtime"
	"strings"
)

func DetectRuntimeInUse(ideaBinary string) (runtimeInUse RuntimeInfo, err error) {
	runtimeInUse.BinaryPath, runtimeInUse.BinaryPathDefinedAt, runtimeInUse.BinaryPathDefinedAs = getRuntimeBinary(ideaBinary)
	logger.ExitWithExceptionOnError(err)
	runtimeInUse.IsBundled = checkIfRuntimeBundled(runtimeInUse.BinaryPath)
	runtimeInUse.Architecture = getRuntimeArchitecture(runtimeInUse.BinaryPath)
	runtimeInUse.Version = getRuntimeVersion(runtimeInUse.BinaryPath)
	return runtimeInUse, err
}

func getRuntimeVersion(runtimeBinaryPath string) string {
	output, err := GetJavaProperty(runtimeBinaryPath, "java.version")
	logger.ExitWithExceptionOnError(err)
	return output
}

func getRuntimeArchitecture(runtimeBinaryPath string) string {
	output, err := GetJavaProperty(runtimeBinaryPath, "sun.arch.data.model")
	logger.ExitWithExceptionOnError(err)
	return filepath.Clean(output)
}

func checkIfRuntimeBundled(path string) bool {
	if strings.Contains(path, IdeRuntimeBinaryRelatedToInstallationPath[runtime.GOOS]) {
		return true
	} else {
		return false
	}
}

func getRuntimeBinary(ideaBinary string) (binaryPath string, binaryPathDefinedAt string, binaryPathDefinedAs string) {
	for _, variable := range getOsDependentDir(possibleRuntimeVariables) {
		runtimeLocation := os.ExpandEnv(expandRuntimeDefaultLocation(variable))
		if runtimeLocation != "" {
			return findRuntimeBinaryInsidePath(runtimeLocation), "$IDEA_JDK", runtimeLocation
		}
	}
	for _, location := range getOsDependentDir(possibleRuntimeConfiguraitonFileLocations) {
		location := expandRuntimeDefaultLocation(location)
		if fileWithPossibleLocation, err := os.Open(location); err == nil {
			runtimeLocation := getRuntimePathFromFile(fileWithPossibleLocation)
			return findRuntimeBinaryInsidePath(runtimeLocation), location, runtimeLocation
		}
	}
	if possibleLocation, err := getBundledRuntimeBinary(ideaBinary); err == nil {
		return possibleLocation, "bundled", "bundled"
	}
	logger.ExitWithExceptionOnError(errors.New("could not detect IDE runtime definition"))
	return "", "", ""
}
func expandRuntimeDefaultLocation(dir string) (expandedDir string) {
	dir = strings.Replace(dir, "{IdeConfigDir}", CurrentIde.GetConfigurationDirectory(), -1)
	dir = strings.Replace(dir, "{IDE_BASEFILENAME}", strings.ToUpper(GetIdeBasefileName(GetIdeaBinaryToWrokWith())), -1)
	dir = strings.Replace(dir, "{IDE_BaseFilename}", GetIdeBasefileName(GetIdeaBinaryToWrokWith()), -1)
	dir = filepath.Clean(dir)
	return dir
}

func findRuntimeBinaryInsidePath(runtimePath string) (binaryPath string) {
	runtimePath = filepath.Clean(runtimePath)
	err := filepath.Walk(runtimePath,
		func(path string, info os.FileInfo, err error) error {
			if err == nil && strings.HasSuffix(path, "/bin/java") && !strings.Contains(path, "/jre") {
				binaryPath = path
				return nil
			}
			if err == nil && strings.HasSuffix(path, "\\bin\\java.exe") && !strings.Contains(path, "\\jre") && runtime.GOOS == "windows" {
				binaryPath = path
				return nil
			}
			return nil
		})
	if err != nil {
		logger.ExitWithExceptionOnError(err)
	}
	return binaryPath
}
func getRuntimePathFromFile(file *os.File) string {
	scanner := bufio.NewScanner(file)
	scanner.Split(bufio.ScanLines)
	for scanner.Scan() {
		definedLcoation := scanner.Text()
		if len(definedLcoation) != 0 {
			if FileExists(definedLcoation) {
				return definedLcoation
			}
		}
	}
	return ""
}

func getBundledRuntimeBinary(ideaBinary string) (runtimeBinary string, err error) {
	ideaPackage := GetIdeIdePackageByBinary(ideaBinary)
	runtimeBinary = ideaPackage + IdeRuntimeBinaryRelatedToInstallationPath[runtime.GOOS]
	if FileExists(runtimeBinary) {
		return runtimeBinary, nil
	} else {
		return "", err
	}
}

func PrepareEnvironent(wantRuntimeInUse RuntimeInfo) {
	if wantRuntimeInUse.BinaryPathDefinedAt == "$IDEA_JDK" {
		variable := strings.TrimPrefix(expandRuntimeDefaultLocation(getOsDependentDir(possibleRuntimeVariables)[0]), "$")
		err := os.Setenv(variable, wantRuntimeInUse.BinaryPathDefinedAs)
		logger.ExitWithExceptionOnError(err)
	}
	if strings.HasSuffix(wantRuntimeInUse.BinaryPathDefinedAt, "idea.jdk") {
		err := ioutil.WriteFile(wantRuntimeInUse.BinaryPathDefinedAt, []byte(wantRuntimeInUse.BinaryPathDefinedAs), 0644)
		logger.ExitWithExceptionOnError(err)
	}
}

func ClearEnvironemnt(wantRuntimeInUse RuntimeInfo) {
	if wantRuntimeInUse.BinaryPathDefinedAt == "$IDEA_JDK" {
		err := os.Unsetenv("IDEA_JDK")
		logger.ExitWithExceptionOnError(err)
	}
	if strings.HasSuffix(wantRuntimeInUse.BinaryPathDefinedAt, "idea.jdk") {
		err := os.Remove(wantRuntimeInUse.BinaryPathDefinedAt)
		logger.ExitWithExceptionOnError(err)
	}
}

func RuntimeIsBundled() error {
	if !RuntimeInUse.IsBundled {
		return errors.New("Non-bundled runtime is in use. JetBrains Runtime (JBR) is recommended runtime to start JetBrains IDEs")
	}
	return nil
}

func RuntimeArchitectureCorrectness() error {
	var is64Bit = uint64(^uintptr(0)) == ^uint64(0)
	if !is64Bit {
		if strings.HasPrefix(RuntimeInUse.Architecture, " 64") {
			return errors.New("IDE uses 64-bit runtime on 32-bit OS")
		}
	}
	return nil
}

func RuntimeVersionLessThan17(runtimeInfo RuntimeInfo) error {
	if !strings.HasPrefix(runtimeInfo.Version, "17") && !runtimeInfo.IsBundled {
		return errors.New("IDE uses java of version " + runtimeInfo.Version + ", but recomended version is 17")
	} else {
		return nil
	}
}

func ResetRuntimeInUse(runtime RuntimeInfo) (err error) {
	if runtime.BinaryPathDefinedAt == "bundled" {
		return nil
	} else if runtime.BinaryPathDefinedAt == "$IDEA_JDK" {
		err = os.Unsetenv("$IDEA_JDK")
	} else if strings.Contains(runtime.BinaryPathDefinedAt, "idea.jdk") {
		err = ioutil.WriteFile(runtime.BinaryPathDefinedAt+".old", []byte(runtime.BinaryPathDefinedAs), 0644)
		if err != nil {
			logger.ExitWithExceptionOnError(errors.New("Could not make a backup of " + runtime.BinaryPathDefinedAt + ": " + err.Error()))
		}
		err = os.Remove(runtime.BinaryPathDefinedAt)
		if err != nil {
			logger.ExitWithExceptionOnError(errors.New("Could not remove " + runtime.BinaryPathDefinedAt + ": " + err.Error()))
		}
	} else {
		err = errors.New("could not reset JDK")
	}
	return err
}

func IdeHasBundledRuntime(binaryToWorkWith string) bool {
	info, _ := GetIdeInfoByBinary(binaryToWorkWith)
	if len(info.Launch[0].JavaExecutablePath) > 0 {
		return true
	}
	return false
}

func SuggestToChangeIdeToOneWithBundledRuntime() {
	info, _ := GetIdeInfoByBinary(GetIdeaBinaryToWrokWith())
	logger.ConsoleLogger.Println("There are errors related to runtime in use, but there is no default runtime for your IDE installation. \n" +
		"Please install latest version of " + info.Name + "from https://jetbrains.com/")
}

func SuggestResettingRuntime() bool {
	question := "Reset runtime to default?"
	options := []string{"y", "n"}
	var answer string
	if !IsInTests() {
		answer = AskQuestionWithOptions(question, options)
	} else {
		answer = "n"
	}
	if answer == "y" {
		return true
	}
	return false
}
