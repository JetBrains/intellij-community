package helpers

import (
	"errors"
	"math/rand"
	"os"
	"path/filepath"
	"repair/logger"
	"strings"
	"time"
)

func IsInTests() bool {
	for _, arg := range os.Args {
		if strings.HasPrefix(arg, "-test.v") {
			return true
		}
	}
	return false
}
func GetRandomIdeInstallationBinaryOfBuild(version string) (ideaBinary string) {
	for i := 0; i < 20; i++ {
		possibleBinary := GetRandomIdeInstallationBinary()
		if info, _ := GetIdeInfoByBinary(possibleBinary); strings.Contains(info.BuildNumber, version) {
			return possibleBinary
		}
	}
	logger.ExitWithExceptionOnError(errors.New("Could not find IDE with " + version + " version"))
	return ""
}
func GetRandomIdeInstallationBinary() (ideaBinary string) {
	var err error
	var packagePath string
	if packagePath, err = GetRandomIdeInstallationPackage(); err == nil {
		if ideaBinary, err = GetIdeBinaryByPackage(packagePath); err == nil {
			logger.ConsoleLogger.Println("Idea Binary to work with:" + ideaBinary)
			CurrentIde.SetBinaryToWrokWith(ideaBinary)
			return ideaBinary
		}
	}
	logger.ExitWithExceptionOnError(err)
	return ""
}
func GetRandomIdeInstallationPackage() (packagePath string, err error) {
	var possiblePackages []string
	possiblePackages, err = FindInstalledIdePackages()
	if len(possiblePackages) > 0 {
		rand.Seed(time.Now().UnixNano())
		for {
			possibleackage := possiblePackages[rand.Intn(len(possiblePackages)-1)]
			if _, err := GetIdeInfoByPackage(possibleackage); err == nil {
				return possibleackage, nil
			}
		}
	}
	return "", errors.New("Could not find installed IDEs")
}
func GetAbsolutePath(relatedPath string) string {
	path, err := filepath.Abs(relatedPath)
	_, err = os.Stat(path)
	if err != nil {
		logger.WarningLogger.Println(err)
	}
	return path
}
func (ide *IDE) downloadAndInstallPlugins(plugins PluginInfoList) {
	for _, plugin := range plugins {
		ide.downloadAndInstallPlugin(plugin, plugin.Version)
	}
}
func downloadAndInstallPlugins(ideaBinary string, plugins PluginInfoList) {

}
func (ide *IDE) downloadAndInstallPlugin(plugin PluginInfo, version string) {
	zipFilePath := downloadPlugin(plugin, version)
	ide.installPlugin(zipFilePath)
}
func downloadPlugin(plugin PluginInfo, version string) (downloadedZipPath string) {
	url := "https://plugins.jetbrains.com/plugin/download?" +
		"pluginId=" + GetDisabledPluginRecord(plugin) +
		"&version=" + version
	downloadedZipPath = DownloadFile(url)
	return downloadedZipPath
}
func (ide *IDE) TemporarilyRemovePluginsDir(revert bool) {
	pluginsDir := strings.TrimSuffix(ide.GetIdeCustomPluginsDirectory(), "/")
	if revert {
		err := RemoveDir(pluginsDir)
		if FileExists(pluginsDir + ".old") {
			err = os.Rename(pluginsDir+".old", pluginsDir)
		}
		logger.ExitWithExceptionOnError(err)
	} else {
		if FileExists(pluginsDir) {
			err := os.Rename(pluginsDir, pluginsDir+".old")
			logger.ExitWithExceptionOnError(err)
		}
	}
}
func (ide *IDE) TemporarilyRenameDisabledPluginsFile(revert bool) {
	disabledPluginsFilename := ide.GetDisabledPluginsFilename()
	if FileExists(disabledPluginsFilename) && revert == false {
		err := MoveFile(disabledPluginsFilename, disabledPluginsFilename+".backup")
		logger.ExitWithExceptionOnError(err)
	}
	if FileExists(disabledPluginsFilename+".backup") && revert == true {
		err := MoveFile(disabledPluginsFilename+".backup", disabledPluginsFilename)
		logger.ExitWithExceptionOnError(err)
	}
	return
}
