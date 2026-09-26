package helpers

import (
	"errors"
	"os"
	"repair/logger"
	"runtime"
)

var (
	PluginsList           PluginInfoList
	CustomPluginsList     PluginInfoList
	LogEntries            []LogEntry
	InstallationInfo      IdeInfo
	ideaPackageToWorkWith string
	ideaBinaryToWorkWith  string
	CurrentIde            IDE
	RuntimeInUse          RuntimeInfo
	vmOptionsFile         string
)

// Deprecated: use CurrentIde.Binary instead
func GetIdeaBinaryToWrokWith() string {
	if len(CurrentIde.Binary) > 0 {
		return CurrentIde.Binary
	} else {
		logger.ExitWithExceptionOnError(errors.New("There is no idea binary to work with"))
		return ""
	}
}
func UserHomeDir() string {
	if runtime.GOOS == "windows" {
		home := os.Getenv("HOMEDRIVE") + os.Getenv("HOMEPATH")
		if home == "" {
			home = os.Getenv("USERPROFILE")
		}
		return home
	}
	return os.Getenv("HOME")
}

// OS-dependent constants
var (
	EmbeededDownloadUrl     = ""
	downloadsURL            = "https://download.jetbrains.com"
	ManifestFilesExclusions = map[string][]string{
		"darwin":  {},
		"linux":   {},
		"windows": {".feed", ".symlink", "bin/Uninstall.exe.nsis"},
	}
	archiveExtension = map[string]string{
		"darwin":  ".dmg",
		"linux":   ".tar.gz",
		"windows": ".exe",
	}
	possibleBaseFileNames = []string{
	  "appcode", "clion", "datagrip", "dataspell", "goland", "idea",
	  "phpstorm", "pycharm", "rubymine", "webstorm", "rider", "Draft", "aqua",
	  "rustrover",
	}
	possibleBinariesPaths = map[string][]string{
		"darwin":  {"$HOME/Applications/*.app/Contents/MacOS/{possibleBaseFileName}", "/Applications/*.app/Contents/MacOS/{possibleBaseFileName}", "$HOME/Library/Application Support/JetBrains/Toolbox/apps/*/ch-*/*/*.app/Contents/MacOS/{possibleBaseFileName}"},
		"linux":   {"$HOME/.local/share/JetBrains/Toolbox/apps/*/ch-*/*/bin/{possibleBaseFileName}.sh", "$HOME/.local/share/JetBrains/Toolbox/apps/*/bin/{possibleBaseFileName}.sh"},
		"windows": {os.Getenv("HOMEDRIVE") + "/Program Files/JetBrains/*" + IdeBinaryRelatedToInstallationPath["windows"], os.Getenv("LOCALAPPDATA") + "/JetBrains/Toolbox/apps/*/ch-*/*" + IdeBinaryRelatedToInstallationPath["windows"], os.Getenv("LOCALAPPDATA") + "/Programs/*" + IdeBinaryRelatedToInstallationPath["windows"]},
	}
	IdeBinaryRelatedToInstallationPath = map[string]string{
		"darwin":  "/Contents/MacOS/{possibleBaseFileName}",
		"linux":   "/bin/{possibleBaseFileName}.sh",
		"windows": "/bin/{possibleBaseFileName}64.exe",
	}
	IdeProductInfoRelatedToInstallationPath = map[string]string{
		"darwin":  "/Contents/Resources/product-info.json",
		"linux":   "/product-info.json",
		"windows": "/product-info.json",
	}
	possibleIdeaPropertiesFileLocations = map[string][]string{
		"darwin":  {"${IDE_BasefileName}_PROPERTIES", UserHomeDir() + "/Library/Application Support/JetBrains/{dataDirectoryName}/idea.properties", UserHomeDir() + "/idea.properties", "{ideaPackage}/Contents/bin/idea.properties"},
		"linux":   {"${IDE_BasefileName}_PROPERTIES", UserHomeDir() + "/.config/JetBrains/{dataDirectoryName}/idea.properties", UserHomeDir() + "/idea.properties", "{ideaPackage}/bin/idea.properties"},
		"windows": {"${IDE_BasefileName}_PROPERTIES", defaultSystemDirLocation[runtime.GOOS] + "/idea.properties", UserHomeDir() + "/idea.properties", "{ideaPackage}/bin/idea.properties"},
	}
	IdeRuntimeBinaryRelatedToInstallationPath = map[string]string{
		"darwin":  "/Contents/jbr/Contents/Home/bin/java",
		"linux":   "/jbr/bin/java",
		"windows": "/jbr/bin/java.exe",
	}
	possibleRuntimeVariables = map[string][]string{
		"darwin":  {"${IDE_BASEFILENAME}_JDK"},
		"linux":   {"${IDE_BASEFILENAME}_JDK"},
		"windows": {"${IDE_BASEFILENAME}_JDK_64"},
	}
	possibleRuntimeConfiguraitonFileLocations = map[string][]string{
		"darwin":  {"{IdeConfigDir}/{IDE_BaseFilename}.jdk"},
		"linux":   {"{IdeConfigDir}/{IDE_BaseFilename}.jdk"},
		"windows": {"{IdeConfigDir}/{IDE_BaseFilename}64.exe.jdk"},
	}
	defaultLogsDirLocation = map[string]string{
		"darwin":  UserHomeDir() + "/Library/Logs/JetBrains/{dataDirectoryName}/",
		"linux":   UserHomeDir() + "/.cache/JetBrains/{dataDirectoryName}/log/",
		"windows": os.Getenv("LOCALAPPDATA") + "/JetBrains/{dataDirectoryName}/log/",
	}
	defaultSystemDirLocation = map[string]string{
		"darwin":  "${HOME}/Library/Caches/JetBrains/{dataDirectoryName}/",
		"linux":   "${HOME}/.cache/JetBrains/{dataDirectoryName}/",
		"windows": os.Getenv("LOCALAPPDATA") + "/JetBrains/{dataDirectoryName}/",
	}
	defaultConfigDirLocation = map[string]string{
		"darwin":  "${HOME}/Library/Application Support/JetBrains/{dataDirectoryName}/",
		"linux":   "${HOME}/.config/JetBrains/{dataDirectoryName}/",
		"windows": os.Getenv("APPDATA") + "/JetBrains/{dataDirectoryName}/",
	}
	defaultBundledPluginsDirLocation = map[string]string{
		"darwin":  "{ideaPackage}/Contents/plugins",
		"linux":   "{ideaPackage}/plugins",
		"windows": "{ideaPackage}/plugins",
	}
	//possibleVmOptionsFileLocation is the *ordered* list where .vmoptions files could be located
	possibleVmOptionsFileLocation = map[string][]string{
		"darwin":  {"$IDEA_VM_OPTIONS", "{ideaPackage}.vmoptions", "{IdeConfigDir}/idea.vmoptions"},
		"linux":   {"$IDEA_VM_OPTIONS", "{ideaPackage}.vmoptions", "{IdeConfigDir}/idea.vmoptions"},
		"windows": {"$IDEA64_VM_OPTIONS", "{ideaPackage}/bin/{IDE_BaseFilename}64.exe.vmoptions"},
	}
	IdePropertiesMap = map[string]string{}
)

type LogEntry struct {
	DateAndTime    string
	TimeSinceStart string
	Severity       string
	Class          string
	Header         string
	Body           string
}
type PluginInfo struct {
	Id            string `xml:"id"`
	Name          string `xml:"name"`
	Version       string `xml:"version"`
	Vendor        string `xml:"vendor"`
	MainJarPath   string
	PluginXmlId   string
	MarketplaceId int
	IsDisabled    bool
	IdeaVersion   IdeaVersion `xml:"idea-version"`
	isBundled     bool
	IsDeprecated  bool
}
type PluginInfoList []PluginInfo
type RuntimeInfo struct {
	BinaryPath          string
	BinaryPathDefinedAt string
	BinaryPathDefinedAs string
	Version             string
	Architecture        string
	IsBundled           bool
}
