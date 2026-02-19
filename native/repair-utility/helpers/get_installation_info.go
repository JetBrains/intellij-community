package helpers

import (
	"bufio"
	"encoding/json"
	"errors"
	"io/ioutil"
	"os"
	"path/filepath"
	"repair/logger"
	"runtime"
	"runtime/debug"
	"strings"
)

type IntallationInfoLaunchInner struct {
	Os                 string
	JavaExecutablePath string
}

func (ide *IDE) GetConfigurationDirectory() (configDir string) {
	configDir = ide.GetProperty("idea.config.path")
	if len(configDir) == 0 {
		configDir = ide.GetDefaultConfigurationDirectory()
	}
	if _, err := os.Create(configDir + "/test.tmp"); err == nil {
		_ = os.Remove(configDir + "/test.tmp")
		return filepath.Clean(configDir) + string(os.PathSeparator)
	} else if FileExists(configDir) {
		logger.ExitOnError(errors.New("Could not open configuration directory for writing \"" + configDir + "\". IDE requires writable directory to work"))
	} else {
		logger.ExitOnError(errors.New("Could not find IDE configuration directory. Most probably, this IDE has not been started yet."))
	}
	return ""
}
func (ide *IDE) GetDefaultConfigurationDirectory() (defaultConfigDir string) {
	installationInfo := ide.GetInfo()
	defaultConfigDir = strings.Replace(defaultConfigDirLocation[runtime.GOOS], "{dataDirectoryName}", installationInfo.DataDirectoryName, -1)
	defaultConfigDir = os.ExpandEnv(defaultConfigDir)
	return filepath.Clean(defaultConfigDir) + string(os.PathSeparator)
}

func (ide *IDE) GetIdeCustomPluginsDirectory() (pluginsDir string) {
	if len(ide.GetProperty("idea.plugins.path")) > 1 {
		pluginsDir = ide.GetProperty("idea.plugins.path")
	} else if len(GetIdeVmoptionByName("-Didea.plugins.path")) > 1 {
		pluginsDir = GetIdeVmoptionByName("-Didea.plugins.path")
	} else {
		pluginsDir = ide.GetConfigurationDirectory() + "plugins"
	}
	return filepath.Clean(pluginsDir) + string(os.PathSeparator)
}
func (ide *IDE) GetSystemDirectory() (systemDir string) {
	if len(ide.GetProperty("idea.system.path")) > 1 {
		systemDir = ide.GetProperty("idea.system.path")
	} else if len(GetIdeVmoptionByName("-Didea.system.path")) > 1 {
		systemDir = GetIdeVmoptionByName("-Didea.system.path")
	} else {
		systemDir = GetIdeDefaultSystemDir(ide.Binary)
	}
	return filepath.Clean(systemDir) + string(os.PathSeparator)
}

func GetIdeBundledPluginsDirectory(ideaBinary string) string {
	return strings.Replace(defaultBundledPluginsDirLocation[runtime.GOOS], "{ideaPackage}", GetIdeIdePackageByBinary(ideaBinary), -1)
}

func GetIdeDefaultSystemDir(ideaBinary string) (defaultSystemDir string) {
	installationInfo, err := GetIdeInfoByBinary(ideaBinary)
	logger.ExitWithExceptionOnError(err)
	defaultSystemDir = strings.Replace(defaultSystemDirLocation[runtime.GOOS], "{dataDirectoryName}", installationInfo.DataDirectoryName, -1)
	defaultSystemDir = os.ExpandEnv(defaultSystemDir)
	return filepath.Clean(defaultSystemDir) + string(os.PathSeparator)
}

func (ide *IDE) GetProperty(name string) (value string) {
	if len(ide.Binary) < 1 {
		logger.ExitWithExceptionOnError(errors.New("Trying to analyze undefined installation"))
	}
	if len(IdePropertiesMap) == 0 {
		IdePropertiesMap = GetIdeProperties(ide.Binary)
	}
	if _, ok := IdePropertiesMap[name]; ok {
		return IdePropertiesMap[name]
	}
	return ""
}
func GetIdePropertyByName(name string, ideaBinary string) (value string) {
	if len(IdePropertiesMap) == 0 {
		IdePropertiesMap = GetIdeProperties(ideaBinary)
	}
	if _, ok := IdePropertiesMap[name]; ok {
		return IdePropertiesMap[name]
	}
	return ""
}
func GetIdeBasefileName(ideaBinary string) string {
	for _, possibleBaseFileName := range possibleBaseFileNames {
		if strings.HasSuffix(ideaBinary, possibleBaseFileName) {
			return possibleBaseFileName
		}
	}
	return ""
}

func GetIdeVmoptionByName(optionName string) (value string) {
	vmOptionsFile, err := GetVmOptionsFile()
	if err != nil {
		return ""
	}
	vmOptionsAsSlice := GetVmoptionsAsSlice(vmOptionsFile)
	return vmOptionsAsSlice[optionName]
}

//fixme: java properties (as ${user.home} could not be extended)
//possible solution is to call java binary to print list of resolved system properties.
func (ide *IDE) GetProperties() (collectedOptions map[string]string) {
	if len(ide.Binary) < 1 {
		logger.ExitWithExceptionOnError(errors.New("Trying to get properties of undefined IDE"))
	}
	var err error
	var ideaPackage string
	collectedOptions = make(map[string]string)
	ide.Binary, err = DetectInstallationByInnerPath(ide.Binary, true)
	ideaPackage, err = DetectInstallationByInnerPath(ide.Binary, false)
	InstallationInfo, err = GetIdeInfoByBinary(ide.Binary)
	logger.ExitWithExceptionOnError(err)
	for _, possibleIdeaPropertiesFileLocation := range getOsDependentDir(possibleIdeaPropertiesFileLocations) {
		possibleIdeaOptionsFile := strings.Replace(possibleIdeaPropertiesFileLocation, "{IDE_BasefileName}", strings.ToUpper(GetIdeBasefileName(ide.Binary)), -1)
		possibleIdeaOptionsFile = strings.Replace(possibleIdeaOptionsFile, "{dataDirectoryName}", InstallationInfo.DataDirectoryName, -1)
		possibleIdeaOptionsFile = strings.Replace(possibleIdeaOptionsFile, "{ideaPackage}", ideaPackage, -1)
		possibleIdeaOptionsFile = os.ExpandEnv(possibleIdeaOptionsFile)
		if FileExists(possibleIdeaOptionsFile) {
			logger.DebugLogger.Println("found idea.properties file at: \"" + possibleIdeaOptionsFile + "\"")
			fillIdePropertiesMap(possibleIdeaOptionsFile, collectedOptions)
		} else {
			logger.DebugLogger.Println("Checked " + possibleIdeaPropertiesFileLocation + ". There is no \"" + possibleIdeaOptionsFile + "\" file.")
		}
	}
	var listOfCollectedOptions string
	for option, value := range collectedOptions {
		listOfCollectedOptions = listOfCollectedOptions + option + "=" + value + "\n"
	}
	logger.DebugLogger.Println("Collected idea properties:\n" + listOfCollectedOptions)
	return collectedOptions
}
func GetIdeProperties(ideaBinary string) (collectedOptions map[string]string) {
	var err error
	var ideaPackage string
	collectedOptions = make(map[string]string)
	ideaBinary, err = DetectInstallationByInnerPath(ideaBinary, true)
	ideaPackage, err = DetectInstallationByInnerPath(ideaBinary, false)
	InstallationInfo, err = GetIdeInfoByBinary(ideaBinary)
	logger.ExitWithExceptionOnError(err)
	for _, possibleIdeaPropertiesFileLocation := range getOsDependentDir(possibleIdeaPropertiesFileLocations) {
		possibleIdeaOptionsFile := strings.Replace(possibleIdeaPropertiesFileLocation, "{IDE_BasefileName}", strings.ToUpper(GetIdeBasefileName(ideaBinary)), -1)
		possibleIdeaOptionsFile = strings.Replace(possibleIdeaOptionsFile, "{dataDirectoryName}", InstallationInfo.DataDirectoryName, -1)
		possibleIdeaOptionsFile = strings.Replace(possibleIdeaOptionsFile, "{ideaPackage}", ideaPackage, -1)
		possibleIdeaOptionsFile = os.ExpandEnv(possibleIdeaOptionsFile)
		if FileExists(possibleIdeaOptionsFile) {
			logger.DebugLogger.Println("found idea.properties file at: \"" + possibleIdeaOptionsFile + "\"")
			fillIdePropertiesMap(possibleIdeaOptionsFile, collectedOptions)
		} else {
			logger.DebugLogger.Println("Checked " + possibleIdeaPropertiesFileLocation + ". There is no \"" + possibleIdeaOptionsFile + "\" file.")
		}
	}
	var listOfCollectedOptions string
	for option, value := range collectedOptions {
		listOfCollectedOptions = listOfCollectedOptions + option + "=" + value + "\n"
	}
	logger.DebugLogger.Println("Collected idea properties:\n" + listOfCollectedOptions)
	return collectedOptions
}

func getOsDependentDir(fromVariable map[string][]string) []string {
	if len(fromVariable[runtime.GOOS]) > 0 {
		return fromVariable[runtime.GOOS]
	}
	logger.ExitWithExceptionOnError(errors.New("This OS is not yet supported"))
	return nil
}

func fillIdePropertiesMap(ideaOptionsFile string, optionsMap map[string]string) {
	optionsSlice, err := ideaPropertiesFileToSliceOfStrings(ideaOptionsFile)
	logger.ExitWithExceptionOnError(err)
	for _, option := range optionsSlice {
		if idx := strings.IndexByte(option, '='); idx >= 0 {
			optionValue := option[idx+1:]
			optionValue = os.ExpandEnv(optionValue)
			optionName := option[:idx]
			if _, exist := optionsMap[optionName]; !exist {
				optionsMap[optionName] = optionValue
			}
		}

	}
}

func ideaPropertiesFileToSliceOfStrings(ideaPropertiesFile string) (properties []string, err error) {
	file, err := os.Open(ideaPropertiesFile)
	logger.ExitWithExceptionOnError(err)
	scanner := bufio.NewScanner(file)
	scanner.Split(bufio.ScanLines)
	var i int
	for scanner.Scan() {
		i++
		option := scanner.Text()
		if len(option) != 0 {
			if option[0] == '#' {
			} else {
				properties = append(properties, option)
			}
		}
	}
	err = file.Close()
	if err != nil {
		return nil, err
	}
	return properties, err

}

func GetIdeInfoByPackage(ideaPackage string) (parameterValue IdeInfo, err error) {
	var a IdeInfo
	var fileContent []byte
	fileContent, err = ioutil.ReadFile(ideaPackage + IdeProductInfoRelatedToInstallationPath[runtime.GOOS])
	if err != nil {
		for os, path := range IdeProductInfoRelatedToInstallationPath {
			if content, er := ioutil.ReadFile(ideaPackage + path); er == nil {
				fileContent = content
				logger.WarningLogger.Printf("Could not find product-info.json for %s, but found it for %s ", runtime.GOOS, os)
			}
		}
	}
	err = json.Unmarshal(fileContent, &a)
	return a, err
}
func GetIdeInfoByBinary(ideaBinary string) (parameterValue IdeInfo, err error) {
	return GetIdeInfoByPackage(GetIdeIdePackageByBinary(ideaBinary))
}

//GetIdeBinaryByPackage return the location of idea(idea.exe) executable inside the IDE installation folder.
//if ideaPackage == /Users/konstantin.annikov/Downloads/IntelliJ IDEA.app
//then idaBinary == /Users/konstantin.annikov/Downloads/IntelliJ IDEA.app/Contents/MacOS/idea
func GetIdeBinaryByPackage(ideaPackage string) (ideaBinary string, err error) {
	if len(CurrentIde.Binary) == 0 {
		logger.DebugLogger.Printf("detecting IDE binary by package. %s is being checked. Stack is: %s", ideaPackage, string(debug.Stack()))
		logger.DebugLogger.Printf("Binaries to check: %s", possibleBaseFileNames)
	}
	for _, possibleBaseFileName := range possibleBaseFileNames {
		for operatingSystem, path := range IdeBinaryRelatedToInstallationPath {
			currentBinaryToCheck := strings.Replace(path, "{possibleBaseFileName}", possibleBaseFileName, -1)
			ideaBinary = ideaPackage + currentBinaryToCheck
			if len(CurrentIde.Binary) == 0 {
				logger.DebugLogger.Printf("IDE binary to check: %s", ideaBinary)
			}
			if FileExists(ideaBinary) {
				if operatingSystem != runtime.GOOS {
					logger.WarningLogger.Printf("Provided path is for %s, but repair utility is running at %s ", operatingSystem, runtime.GOOS)
				}
				if len(CurrentIde.Binary) == 0 {
					logger.DebugLogger.Printf("Found %s binary at %s", ideaBinary, ideaPackage)
				}
				return filepath.Clean(ideaBinary), nil
			}
			_, err := os.Open(ideaBinary)
			if len(CurrentIde.Binary) == 0 {
				logger.DebugLogger.Printf("%s file does not exist, error: %s", ideaBinary, string(err.Error()))
			}
		}
	}
	return "", errors.New("Could not detect IDE binary in " + ideaPackage)
}

func GetIdePackageToWorkWith(ideaBinary string) (ideaPackage string) {
	if len(ideaPackageToWorkWith) > 0 {
		return ideaPackageToWorkWith
	} else {
		return GetIdeIdePackageByBinary(ideaBinary)
	}
}
func GetIdeIdePackageByBinary(ideaBinary string) (ideaPackage string) {
	var err error
	if ideaPackageToWorkWith, err = DetectInstallationByInnerPath(ideaBinary, false); err == nil {
		return ideaPackageToWorkWith
	} else {
		logger.ErrorLogger.Println("Could not get detect ide installation path by binary " + ideaBinary)
		return ""
	}
}

//If any part of providedPath is IDE installation path, DetectInstallationByInnerPath returns path or binary (based on returnBinary flag)
func DetectInstallationByInnerPath(providedPath string, returnBinary bool) (ideaBinary string, err error) {
	providedPath = filepath.Clean(providedPath)
	providedDeep := strings.Count(providedPath, string(os.PathSeparator))
	basePath := providedPath
	for i := 1; i < providedDeep; i++ {
		if ideaBinary, err := GetIdeBinaryByPackage(basePath); err == nil {
			if returnBinary {
				return ideaBinary, nil
			} else {
				return basePath, nil
			}
		}
		basePath = filepath.Dir(basePath)
	}
	return "", errors.New("Could not detect IDE by \"" + providedPath + "\" path")
}

func GetIdeaLogPath(ideaBinary string) (ideaLogPath string) {
	if GetIdeDefaultLogsDir(ideaBinary) != "" {
		return GetIdeDefaultLogsDir(ideaBinary) + "idea.log"
	} else {
		return ""
	}
}

func GetIdeDefaultLogsDir(ideaBinary string) (defaultLogsDir string) {
	if value := GetIdePropertyByName("idea.log.path", ideaBinary); len(value) != 0 {
		if FileExists(value) {
			return value
		} else {
			logger.FatalLogger.Fatal("'idea.log.path' property is defined, but directory does not exist")
		}
	}
	installationInfo, err := GetIdeInfoByBinary(ideaBinary)
	logger.ExitWithExceptionOnError(err)
	defaultLogsDir = strings.Replace(defaultLogsDirLocation[runtime.GOOS], "{dataDirectoryName}", installationInfo.DataDirectoryName, -1)
	defaultLogsDir = os.ExpandEnv(defaultLogsDir)
	if FileExists(defaultLogsDir) {
		return defaultLogsDir
	} else {
		logger.InfoLogger.Println(errors.New("Could not detect logs directory location"))
		return ""
	}
}

func GetVmOptionsFile() (string, error) {
	if len(vmOptionsFile) > 0 {
		return vmOptionsFile, nil
	} else {
		ideaBinary := CurrentIde.Binary
		for _, possibleVmOptionsLocation := range possibleVmOptionsFileLocation[runtime.GOOS] {
			possibleVmOptionsFile := os.ExpandEnv(possibleVmOptionsLocation)
			possibleVmOptionsFile = strings.Replace(possibleVmOptionsFile, "{ideaPackage}", GetIdeIdePackageByBinary(ideaBinary), -1)
			possibleVmOptionsFile = strings.Replace(possibleVmOptionsFile, "{IdeConfigDir}", CurrentIde.GetConfigurationDirectory(), -1)
			possibleVmOptionsFile = strings.Replace(possibleVmOptionsFile, "{IDE_BaseFilename}", GetIdeBasefileName(GetIdeaBinaryToWrokWith()), -1)

			if FileExists(possibleVmOptionsFile) {
				logger.DebugLogger.Println(".vmoptions file to work with: \"" + possibleVmOptionsFile + "\"")
				vmOptionsFile = possibleVmOptionsFile
				return possibleVmOptionsFile, nil
			} else {
				logger.DebugLogger.Println("Checked " + possibleVmOptionsLocation + ". There is no \"" + possibleVmOptionsFile + "\" file. Next location to check: ")
			}
		}
		err := errors.New("Couldn't detect custom .vmoptions file for " + ideaBinary + "\nThere is nothing to do with vmoptions aspect.")
		return "", err

	}
}
