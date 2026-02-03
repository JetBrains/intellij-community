package helpers

import (
	"archive/zip"
	"bufio"
	"bytes"
	"encoding/json"
	"encoding/xml"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"repair/logger"
	"strconv"
	"strings"
)

func downloadLatestCompatibleVersionOfPlugin(ideaBinary string, plugin PluginInfo) (downloadedZipPath string) {
	url := "https://plugins.jetbrains.com/plugin/download?" +
		"pluginId=" + GetDisabledPluginRecord(plugin) +
		"&version=" + RequestLatestCompatibleVersionFromMarketplace(ideaBinary, &plugin)

	downloadedZipPath = DownloadFile(url)
	return downloadedZipPath
}

func RequestLatestCompatibleVersionFromMarketplace(ideaBinary string, plugin *PluginInfo) string {
	ideInfo, _ := GetIdeInfoByBinary(ideaBinary)
	ideBuildNumber := ideInfo.ProductCode + "-" + ideInfo.BuildNumber
	type ServerResponse []struct {
		Id          int    `json:"id"`
		PluginId    int    `json:"pluginId"`
		PluginXmlId string `json:"pluginXmlId"`
		Version     string `json:"version"`
	}
	var m ServerResponse
	url := "https://plugins.jetbrains.com/api/search/compatibleUpdates"
	var jsonStr = []byte(`{"build":"` + ideBuildNumber + `","pluginXMLIds":["` + GetDisabledPluginRecord(*plugin) + `"]}`)
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonStr))
	logger.ExitWithExceptionOnError(err)
	req.Header.Set("Content-Type", "application/json")
	client := &http.Client{}
	resp, err := client.Do(req)
	logger.ExitWithExceptionOnError(err)
	defer resp.Body.Close()
	body, _ := ioutil.ReadAll(resp.Body)
	err = json.Unmarshal(body, &m)
	for _, newPlugin := range m {
		if newPlugin.PluginXmlId == plugin.PluginXmlId {
			plugin.MarketplaceId = newPlugin.PluginId
			return newPlugin.Version
		}
	}
	return ""
}

func DownloadFile(url string) (downloadPath string) {

	downloadFolder := GetAbsolutePath("downloads/")
	// Get the data
	resp, err := http.Get(url)
	logger.ExitWithExceptionOnError(err)
	finalURL := resp.Request.URL.Path
	downloadPath = downloadFolder + string(os.PathSeparator) + path.Base(finalURL)
	defer resp.Body.Close()

	// Create the
	if _, err := os.Stat(downloadFolder); os.IsNotExist(err) {
		_ = os.Mkdir(downloadFolder, 0755)
	}
	out, err := os.Create(downloadPath)
	logger.ExitWithExceptionOnError(err)
	defer out.Close()

	// Write the body to file
	_, err = io.Copy(out, resp.Body)
	logger.ExitWithExceptionOnError(err)
	return downloadPath
}

func GetPluginsToUpdateAndPluginsToDelete(ideaBinary string, incompatiblePluginsList []PluginInfo) (pluginsToUpdateList []PluginInfo, pluginsToDeleteList []PluginInfo) {
	for _, incompatiblePlugin := range incompatiblePluginsList {
		if CheckIfPluginHasCompatibleVersion(ideaBinary, &incompatiblePlugin) {
			pluginsToUpdateList = append(pluginsToUpdateList, incompatiblePlugin)
		} else {
			pluginsToDeleteList = append(pluginsToDeleteList, incompatiblePlugin)
		}
	}
	return pluginsToUpdateList, pluginsToDeleteList
}

func CheckIfPluginHasCompatibleVersion(ideaBinary string, plugin *PluginInfo) bool {
	newPluginVersion := RequestLatestCompatibleVersionFromMarketplace(ideaBinary, plugin)
	if (newPluginVersion != "") && (ConvertBuildNumberToFloat(plugin.Version) < ConvertBuildNumberToFloat(newPluginVersion)) {
		return true
	}
	return false
}

func ScanDirForPlugins(pluginsDir string) {
	jarFilesPaths := []string{pluginsDir + "/*/lib/*.jar", pluginsDir + "/*.jar"}
	for _, jarFilesPath := range jarFilesPaths {
		jarFiles, _ := filepath.Glob(jarFilesPath)
		for _, jarFile := range jarFiles {
			pluginDescriptionXmlContent := getPluginDescriptionFileContent(jarFile)
			if pluginDescriptionXmlContent != nil {
				currentPlugin := parsePluginXml(pluginDescriptionXmlContent)
				currentPlugin.MainJarPath = jarFile
				disabledPluginRecord := GetDisabledPluginRecord(currentPlugin)
				currentPlugin.IsDisabled = checkIfPluginDisabled(disabledPluginRecord)
				currentPlugin.PluginXmlId = GetDisabledPluginRecord(currentPlugin)
				currentPlugin.isBundled = strings.HasPrefix(currentPlugin.MainJarPath, GetIdeBundledPluginsDirectory(GetIdeaBinaryToWrokWith()))
				//todo: include XMLs defined in plugin.xml
				if len(currentPlugin.PluginXmlId) > 1 {
					PluginsList = append(PluginsList, currentPlugin)
				}
			}
		}
	}
}
func GetDisabledPluginRecord(plugin PluginInfo) string {
	if len(plugin.PluginXmlId) > 0 {
		return plugin.PluginXmlId
	} else if len(plugin.Id) > 0 {
		return plugin.Id
	} else {
		return plugin.Name
	}
}
func ConvertBuildNumberToFloat(buildNumber string) (build float64) {
	if buildNumber == "" {
		buildNumber = "*.*"
	}
	buildNumber = strings.Replace(buildNumber, "*", "9999", 2)
	if strings.Count(buildNumber, ".") >= 2 {
		buildNumberSplit := strings.Split(buildNumber, ".")
		buildNumber = buildNumberSplit[0] + "." + buildNumberSplit[1]
	}
	build, err := strconv.ParseFloat(buildNumber, 64)
	if err != nil {
		logger.DebugLogger.Println(err)
	}
	return build
}
func parsePluginXml(pluginDescriptionXmlContent []byte) PluginInfo {
	var currentPluginInfo PluginInfo
	_ = xml.Unmarshal(pluginDescriptionXmlContent, &currentPluginInfo)
	return currentPluginInfo
}
func checkIfPluginDisabled(plugin string) bool {
	disabledPluginsFile := CurrentIde.GetDisabledPluginsFilename()
	parseDisabledPluginsFile(disabledPluginsFile)
	return sliceOfStringsContains(disabledPlugins, plugin)
}
func sliceOfStringsContains(slice []string, value string) (contains bool) {
	for _, element := range slice {
		if value == element {
			contains = true
			break
		}
	}
	return contains
}
func parseDisabledPluginsFile(disabledPluginsFile string) {
	if len(disabledPlugins) == 0 {
		file, err := os.Open(disabledPluginsFile)
		if err == nil {
			scanner := bufio.NewScanner(file)
			scanner.Split(bufio.ScanLines)
			for scanner.Scan() {
				if len(scanner.Text()) > 0 {
					disabledPlugins = append(disabledPlugins, scanner.Text())
				}
			}
		}
	}
}
func getPluginDescriptionFileContent(jarFile string) []byte {
	var err error
	var file *os.File
	var fi os.FileInfo
	var r *zip.Reader
	file, err = os.Open(jarFile)
	logger.ExitWithExceptionOnError(err)
	defer file.Close()
	fi, err = file.Stat()
	logger.ExitWithExceptionOnError(err)
	r, err = zip.NewReader(file, fi.Size())
	logger.ExitWithExceptionOnError(err)
	for _, f := range r.File {
		var name = f.Name
		if name == "META-INF/plugin.xml" {
			file, err := f.Open()
			logger.ExitWithExceptionOnError(err)
			content, err := ioutil.ReadAll(file)
			//Logger.DebugLogger.Println("Found " + jarFile + name + " file")
			return content
		}
	}
	return nil
}
