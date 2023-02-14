package helpers

import (
	"io/ioutil"
	"os"
	"os/exec"
	"repair/logger"
	"runtime"
	"strings"
)

func GetJavaProperty(javaBinary string, property string) (output string, err error) {
	javaText := "class Main {" +
		"public static void main(String[] ideBinary) {" +
		"System.out.print(System.getProperty(\"" + property + "\"));" +
		"}" +
		"}"
	return RunJavaProgram(javaBinary, javaText)

}
func RunJavaProgram(javaBinary string, javaText string) (output string, err error) {
	if runtime.GOOS == "windows" {
		javaBinary = strings.TrimRight(javaBinary, ".exe")
	}
	javacBinary := javaBinary + "c"
	if runtime.GOOS == "windows" {
		javacBinary = javacBinary + ".exe"
	}
	className := "Main"
	err = ioutil.WriteFile(className+".java", []byte(javaText), 0644)
	logger.ExitWithExceptionOnError(err)
	_, err = exec.Command(javacBinary, className+".java").CombinedOutput()
	logger.ExitWithExceptionOnError(err)
	outputBytes, err := exec.Command(javaBinary, className).CombinedOutput()
	logger.ExitWithExceptionOnError(err)
	output = string(outputBytes)
	err = os.Remove(className + ".class")
	logger.WriteToLogOnError(err, logger.DebugLogger)
	err = os.Remove(className + ".java")
	logger.WriteToLogOnError(err, logger.DebugLogger)
	return output, nil
}
