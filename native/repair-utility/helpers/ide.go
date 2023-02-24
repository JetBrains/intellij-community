package helpers

import (
  "encoding/json"
  "errors"
  "io/ioutil"
  "log"
  "os"
  "os/exec"
  "path/filepath"
  "repair/logger"
  "runtime"
  "strconv"
  "strings"
  "unicode"
)

type IdeInfo struct {
  Name              string `json:"name"`
  Version           string `json:"version"`
  BuildNumber       string `json:"buildNumber"`
  ProductCode       string `json:"productCode"`
  DataDirectoryName string `json:"dataDirectoryName"`
  IsRepairBundled   bool
  Launch            []struct {
    Os                     string   `json:"os"`
    Arch                   string   `json:"arch"`
    LauncherPath           string   `json:"launcherPath"`
    JavaExecutablePath     string   `json:"javaExecutablePath"`
    VmOptionsFilePath      string   `json:"vmOptionsFilePath"`
    BootClassPathJarNames  []string `json:"bootClassPathJarNames"`
    AdditionalJvmArguments []string `json:"additionalJvmArguments"`
  } `json:"launch"`
  BundledPlugins []string `json:"bundledPlugins"`
}
type IDE struct {
  Binary  string
  Package string
  Info    IdeInfo
}

func (ide *IDE) MarkRepairAsBundled() {
  ide.Info.IsRepairBundled = true
}
func (ide *IDE) IsRepairBundled() bool {
  return ide.Info.IsRepairBundled
}
func (ide *IDE) SetBinaryToWrokWith(ideaBinary string) {
  if _, err := GetIdeInfoByBinary(ideaBinary); err == nil {
    ide.Binary = ideaBinary
  }
}
func (ide *IDE) GetInfo() IdeInfo {
  if len(ide.Package) == 0 {
    ide.Package = GetIdePackageToWorkWith(ide.Binary)
  }
  if len(ide.Info.Name) == 0 {
    var fileContent []byte
    var err error
    fileContent, err = ioutil.ReadFile(ide.Package + IdeProductInfoRelatedToInstallationPath[runtime.GOOS])
    if err != nil {
      for operatingSystem, path := range IdeProductInfoRelatedToInstallationPath {
        if content, er := ioutil.ReadFile(ide.Package + path); er == nil {
          fileContent = content
          logger.WarningLogger.Printf("Could not find product-info.json for %s, but found it for %s ", runtime.GOOS, operatingSystem)
        }
      }
    }
    err = json.Unmarshal(fileContent, &ide.Info)
    if err == nil {
      return ide.Info
    }
  }
  return ide.Info
}
func (ide *IDE) GetManifestDownloadUrl() string {
  if len(EmbeededDownloadUrl) > 0 && CurrentIde.IsRepairBundled() {
    logger.DebugLogger.Println("Embedded download URL = " + EmbeededDownloadUrl)
    if !strings.Contains(EmbeededDownloadUrl, ".manifest") {
      EmbeededDownloadUrl = EmbeededDownloadUrl + ".manifest"
    }
    return EmbeededDownloadUrl
  }
  ideInfo := ide.GetInfo()
  ideBinary := filepath.Base(ideInfo.Launch[0].LauncherPath)
  ideBinary = strings.TrimSuffix(ideBinary, ".sh")
  ideBinary = strings.TrimSuffix(ideBinary, "64.exe")
  architectureSuffix := ""
  if runtime.GOOS == "darwin" && runtime.GOARCH == "arm64" {
    architectureSuffix = "-aarch64"
  }
  //For example https://download.jetbrains.com/idea/ideaIU-2022.2.1.exe.manifest
  url := downloadsURL + "/" +
    ideBinary + "/" +
    ideBinary +
    ideInfo.ProductCode + "-" + ideInfo.Version +
    architectureSuffix +
    archiveExtension[runtime.GOOS] +
    ".manifest"
  logger.DebugLogger.Println("No embedded download URL found. Generated download URL = " + url)
  return url
}

func (ide *IDE) GetDisabledPluginsFilename() string {
  return ide.GetConfigurationDirectory() + "disabled_plugins.txt"
}
func (ide *IDE) PluginsList() *PluginInfoList {
  if len(PluginsList) > 0 {
    return &PluginsList
  }
  pluginsDirs := ide.getPluginsDirectories()
  for _, pluginsDir := range pluginsDirs {
    ScanDirForPlugins(pluginsDir)
  }
  return &PluginsList
}
func (ide *IDE) CustomPluginsList() *PluginInfoList {
  if len(CustomPluginsList) > 0 {
    return &CustomPluginsList
  }
  if len(PluginsList) < 1 {
    PluginsList = *ide.PluginsList()
  }
  for _, plugin := range PluginsList {
    if !plugin.isBundled {
      CustomPluginsList = append(CustomPluginsList, plugin)
    }
  }
  return &CustomPluginsList
}
func (ide *IDE) DisableAllCustomPlugins(revert bool) {
  disabledPluginsFilename := ide.GetDisabledPluginsFilename()
  if revert {
    logger.InfoLogger.Println("Enabling plugins back...")
    logger.DebugLogger.Printf("Moving file %s to %s", disabledPluginsFilename+".backup", disabledPluginsFilename)
    err := MoveFile(disabledPluginsFilename+".backup", disabledPluginsFilename)
    logger.ExitWithExceptionOnError(err)
  } else {
    logger.InfoLogger.Println("Disabling all custom plugins...")
    logger.DebugLogger.Println("Creating the list of already disabled plugins and disabling the rest")
    if _, err := os.Stat(disabledPluginsFilename); err == nil {
      logger.DebugLogger.Printf("Moving file %s to %s", disabledPluginsFilename, disabledPluginsFilename+".backup")
      err = MoveFile(disabledPluginsFilename, disabledPluginsFilename+".backup")
      logger.ExitWithExceptionOnError(err)
    }
    CustomPluginsList = *CurrentIde.CustomPluginsList()
    ide.disablePlugins(&CustomPluginsList)
    logger.InfoLogger.Println("Custom plugins were disabled")
  }
  return
}
func (ide *IDE) RefreshPluginsList() {
  PluginsList = *new(PluginInfoList)
  CustomPluginsList = *new(PluginInfoList)
  PluginsList = *ide.PluginsList()
  CustomPluginsList = *ide.CustomPluginsList()
}

func (ide *IDE) getPluginsDirectories() (pluginsDirs []string) {
  pluginsDirs = append(pluginsDirs, GetIdeBundledPluginsDirectory(ide.Binary))
  pluginsDirs = append(pluginsDirs, ide.GetIdeCustomPluginsDirectory())
  //pluginsDirs = append(pluginsDirs, GetIdeCustomPluginsDirectory(ide.Binary))
  for i, pluginsDir := range pluginsDirs {
    pluginsDirs[i] = filepath.Clean(pluginsDir) + string(os.PathSeparator)
  }
  return pluginsDirs
}
func (ide *IDE) EnablePlugin(plugin PluginInfo) {
  disabledPluginsFilename := ide.GetDisabledPluginsFilename()
  disabledPluginRecord := GetDisabledPluginRecord(plugin)
  err := RemoveFromFile(disabledPluginRecord, disabledPluginsFilename)
  logger.ExitWithExceptionOnError(err)
  logger.ConsoleLogger.Printf("%s has been enabled", plugin.Name)
  return
}
func (ide *IDE) disablePlugins(pluginsList *PluginInfoList) {
  disabledPluginsFilename := ide.GetDisabledPluginsFilename()
  var allPluginsRecords string
  for _, plugin := range *pluginsList {
    allPluginsRecords = allPluginsRecords + "\n" + GetDisabledPluginRecord(plugin)
    plugin.IsDisabled = true
  }
  WriteToFile(allPluginsRecords, disabledPluginsFilename)
}
func (ide *IDE) DisablePlugin(plugin PluginInfo) {
  disabledPluginsFilename := ide.GetDisabledPluginsFilename()
  disabledPluginRecord := GetDisabledPluginRecord(plugin)
  WriteToFile(disabledPluginRecord, disabledPluginsFilename)
  logger.ConsoleLogger.Println("\"" + plugin.Name + "\" has been disabled")
  return
}
func (ide *IDE) updatePlugin(plugin PluginInfo) {
  ide.removePlugin(plugin)
  ide.installPlugin(downloadLatestCompatibleVersionOfPlugin(ide.Binary, plugin))
}
func (ide *IDE) updatePlugins(pluginsList []PluginInfo) {
  for _, plugin := range pluginsList {
    ide.updatePlugin(plugin)
    logger.ConsoleLogger.Println("\"" + plugin.Name + "\" has been updated to compatible version")
  }
}
func (ide *IDE) installPlugin(pluginZipPath string) {
  err := Unzip(pluginZipPath, ide.GetIdeCustomPluginsDirectory())
  logger.ExitWithExceptionOnError(err)
  err = os.Remove(pluginZipPath)
  logger.ExitWithExceptionOnError(err)
}
func (ide *IDE) removePlugins(plugins []PluginInfo) {
  for _, plugin := range plugins {
    ide.removePlugin(plugin)
  }
}
func (ide *IDE) removePlugin(plugin PluginInfo) {
  pluginsDir := ide.GetIdeCustomPluginsDirectory()
  pluginFiles := strings.TrimPrefix(plugin.MainJarPath, pluginsDir)
  pluginFiles = strings.TrimPrefix(pluginFiles, string(os.PathSeparator))
  if idx := strings.Index(pluginFiles, string(os.PathSeparator)); idx != -1 {
    pluginFiles = pluginFiles[:idx]
  }
  filesToRemove := pluginsDir + pluginFiles
  matches, _ := filepath.Glob(filesToRemove)
  for _, match := range matches {
    fileToRemove := GetAbsolutePath(match)
    err := os.RemoveAll(fileToRemove)
    logger.ExitWithExceptionOnError(err)
  }
}
func (ide *IDE) ClearSystemDirectory() {
  logger.InfoLogger.Println("Clearing system directory...")
  ideSystemDirectory := ide.GetSystemDirectory()
  err := RemoveContentOfDir(ideSystemDirectory)
  logger.ExitWithExceptionOnError(err)
}

func (ide *IDE) AskUserAndUpdatePlugins(pluginsToUpdateList PluginInfoList) {
  if len(pluginsToUpdateList) > 0 {
    logger.ConsoleLogger.Printf("The following plugins could be updated:\n" + pluginsToUpdateList.ToString())
    question := "Update the above plugins?"
    options := []string{"y", "n"}
    if AskQuestionWithOptions(question, options) == "y" {
      logger.ConsoleLogger.Println("Updaing plugins...")
      ide.updatePlugins(pluginsToUpdateList)
    }
  }
}
func (ide *IDE) AskUserAndDisablePlugins(pluginsToDisableList PluginInfoList) {
  if len(pluginsToDisableList) > 0 {
    alertMessage := "The following plugins should be disabled as "
    function := GetCallerFunction()
    if strings.HasSuffix(function, "RunPluginsAspect") {
      alertMessage = alertMessage + "there is no compatible version for the IDE"
    } else if strings.HasSuffix(function, "RunLogAspect") {
      alertMessage = alertMessage + "there are errors from these plugins in idea.log"
    }
    logger.ConsoleLogger.Println(alertMessage+"\n", pluginsToDisableList.ToString())
    question := "Disable the above plugins?"
    options := []string{"y", "n"}
    if AskQuestionWithOptions(question, options) == "y" {
      logger.ConsoleLogger.Println("Disabling the above plugins...")
      ide.disablePlugins(&pluginsToDisableList)
    }
  }
}
func (ide *IDE) AskUserAndClearSystemDirectory() {
  question := "Clear system directory as a general troubleshooting step? \n\t Note: Local history will be removed!"
  options := []string{"y", "n"}
  if AskQuestionWithOptions(question, options) == "y" {
    ide.ClearSystemDirectory()
  }
}
func (ide *IDE) AskUserToRunIdeAndCheckTheIssue() bool {
  question := "Let's try to start the IDE to check if the issue is still here?"
  options := []string{"y", "n"}
  if ForceAskQuestionWithOptions(question, options) == "y" {
    process := ide.RunIde()
    question = "The issue still here?"
    options = []string{"y", "n"}
    if AskQuestionWithOptions(question, options) == "y" {
      logger.InfoLogger.Println("Shutting down the IDE and continue repairing")
      err := process.Kill()
      logger.WriteToLogOnError(err, logger.WarningLogger)
      return true
    } else {
      return false
    }
  } else {
    question = "Then restart the IDE manually. Can the issue be reproduced now?"
    options = []string{"y", "n"}
    if ForceAskQuestionWithOptions(question, options) == "y" {
      return true
    } else {
      return false
    }
  }
}

func (ide *IDE) RunIde() *os.Process {
  cmd := exec.Command(ide.Binary)
  err := cmd.Start()
  if err != nil {
    log.Fatal(err)
  }
  logger.DebugLogger.Println("Started the IDE with PID " + strconv.Itoa(cmd.Process.Pid))
  return cmd.Process
}

func (ide *IDE) CheckIfPathIsAllowed(path string) {
  if !isASCII(path) {
    logger.FatalLogger.Fatal(errors.New("The path contains non-ASCII characters. IDE cannot run here: " + path))
  }
}

func isASCII(s string) bool {
  for _, c := range s {
    if c > unicode.MaxASCII {
      return false
    }
  }
  return true
}
