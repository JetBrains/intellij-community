package helpers

import (
	"os"
	"path/filepath"
	"repair/logger"
	"strconv"
	"strings"
)

func SelectIdeaBinary() (path string, err error) {
	logger.InfoLogger.Println("----------Scanning system for IDE installations----------")
	var installedIdes []string
	installedIdes, err = FindInstalledIdePackages()
	if err != nil {
		return "", err
	}

	for i, idePackage := range installedIdes {
		info, _ := GetIdeInfoByPackage(idePackage)
		logger.ConsoleLogger.Printf("[%v] %v %v (%v-%v) - %v \n", i, info.Name, info.Version, info.ProductCode, info.BuildNumber, beautifyPackageName(idePackage))
	}
	logger.ConsoleLogger.Printf("Please choose one of the above installations to perform operations with\n" +
		"You can either type the digit, or define absolute path to the IDE installation")

	input, err := ReadUserInput()
	input = strings.TrimSpace(input)
	if number, convertible := strconv.Atoi(input); convertible == nil && number < len(installedIdes) {
		path, err = GetIdeBinaryByPackage(installedIdes[number])
		logger.ExitWithExceptionOnError(err)
	} else if number >= len(installedIdes) {
		logger.ConsoleLogger.Fatal(input + " is out of range. Please use 1.." + strconv.Itoa(len(installedIdes)-1) + " to choose the IDE to work with")
	} else {
		path = strings.TrimSpace(input)
	}
	path = filepath.Clean(path)
	_, err = os.Open(path)
	if err != nil {
		logger.DebugLogger.Println(err)
		logger.ConsoleLogger.Fatal("Seems you tried to enter the path to the IDE binary, but there is no IDE binary in the \"" + path + "\" path")
	}
	logger.DebugLogger.Println("IDE binary to work with:" + path)
	return path, err
}
func beautifyPackageName(idePackage string) string {
	if strings.Contains(idePackage, "oolbox") {
		return "From Toolbox App"
	}
	return idePackage
}
func FindIdeInstallationsByMask(path string) (foundIdePackages []string, err error) {
	for _, possibleBaseFileName := range possibleBaseFileNames {
		currentPath := strings.Replace(path, "{possibleBaseFileName}", possibleBaseFileName, -1)
		matches, _ := filepath.Glob(os.ExpandEnv(currentPath))
		for _, match := range matches {
			match = GetIdeIdePackageByBinary(match)
			foundIdePackages = append(foundIdePackages, match)
		}
	}
	return foundIdePackages, err
}
func FindInstalledIdePackages() (installedIdes []string, err error) {
	for _, path := range getOsDependentDir(possibleBinariesPaths) {
		var foundInstallations []string
		foundInstallations, err = FindIdeInstallationsByMask(path)
		installedIdes = append(installedIdes, foundInstallations...)
	}
	return installedIdes, err
}
