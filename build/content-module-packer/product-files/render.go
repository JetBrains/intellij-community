package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
)

// launchModel is `ProductLaunchModel`. The Kotlin encoder leaves out every field that holds its default value, so a
// missing field is the zero value here.
type launchModel struct {
	ProductCode             string              `json:"productCode"`
	BuildNumber             string              `json:"buildNumber"`
	ProductName             string              `json:"productName"`
	Version                 string              `json:"version"`
	VersionSuffix           *string             `json:"versionSuffix"`
	EnvVarBaseName          string              `json:"envVarBaseName"`
	DataDirectoryName       string              `json:"dataDirectoryName"`
	SvgIcon                 bool                `json:"svgIcon"`
	ProductVendor           string              `json:"productVendor"`
	MajorVersionReleaseDate string              `json:"majorVersionReleaseDate"`
	MinRequiredJavaVersion  int                 `json:"minRequiredJavaVersion"`
	CustomProperties        []launchProperty    `json:"customProperties"`
	Flavors                 []string            `json:"flavors"`
	Jbr17                   bool                `json:"jbr17"`
	BaseFileName            string              `json:"baseFileName"`
	LanguageServer          bool                `json:"languageServer"`
	Launch                  launchCommand       `json:"launch"`
	CustomCommands          []customCommand     `json:"customCommands"`
	VMOptions               map[string][]string `json:"vmOptions"`
	IdeaProperties          ideaProperties      `json:"ideaProperties"`
}

type launchProperty struct {
	Key   string `json:"key"`
	Value string `json:"value"`
}

type launchCommand struct {
	MainClass             string       `json:"mainClass"`
	BootClassPathJarNames []string     `json:"bootClassPathJarNames"`
	JvmArguments          jvmArguments `json:"jvmArguments"`
	StdioRedirectArg      *string      `json:"stdioRedirectArg"`
	LinuxStartupWmClass   string       `json:"linuxStartupWmClass"`
}

type customCommand struct {
	Commands              []string          `json:"commands"`
	VMOptionsFilePath     map[string]string `json:"vmOptionsFilePath"`
	BootClassPathJarNames []string          `json:"bootClassPathJarNames"`
	JvmArguments          *jvmArguments     `json:"jvmArguments"`
	Qodana                bool              `json:"qodana"`
	MacJvmArguments       []string          `json:"macJvmArguments"`
	ExtraJvmArguments     []string          `json:"extraJvmArguments"`
	MainClass             *string           `json:"mainClass"`
	EnvVarBaseName        *string           `json:"envVarBaseName"`
	DataDirectoryName     *string           `json:"dataDirectoryName"`
}

type jvmArguments struct {
	XBootClassPathJarNames  []string `json:"xBootClassPathJarNames"`
	MultiRoutingFileSystem  bool     `json:"multiRoutingFileSystem"`
	CdsArchiveFileName      *string  `json:"cdsArchiveFileName"`
	ClassLoader             *string  `json:"classLoader"`
	VendorName              string   `json:"vendorName"`
	PathsSelector           string   `json:"pathsSelector"`
	Jna                     bool     `json:"jna"`
	Pty4j                   bool     `json:"pty4j"`
	Skiko                   bool     `json:"skiko"`
	RuntimeModuleRepository bool     `json:"runtimeModuleRepository"`
	RootModule              *string  `json:"rootModule"`
	ProductMode             *string  `json:"productMode"`
	PlatformPrefix          *string  `json:"platformPrefix"`
	Additional              []string `json:"additional"`
	Splash                  bool     `json:"splash"`
	NativeAccess            bool     `json:"nativeAccess"`
}

type ideaProperties struct {
	LanguageServerBase bool     `json:"languageServerBase"`
	Additions          []string `json:"additions"`
	SettingsDir        string   `json:"settingsDir"`
	Suffix             string   `json:"suffix"`
}

func parseLaunchModel(text []byte) (launchModel, error) {
	var model launchModel
	decoder := json.NewDecoder(bytes.NewReader(text))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&model); err != nil {
		return model, fmt.Errorf("cannot read the launch model: %w", err)
	}
	return model, nil
}

// OS names are `OsFamily.osName`, and architecture names are `JvmArchitecture.dirName`.
const (
	osMac     = "macOS"
	osLinux   = "Linux"
	osWindows = "Windows"
)

// platform is one `HOST_PLATFORMS` entry: the OS and the architecture a distribution is for.
type platform struct {
	os   string
	arch string
}

func parsePlatform(name string) (platform, error) {
	osToken, archToken, found := strings.Cut(name, "_")
	var result platform
	switch osToken {
	case "darwin":
		result.os = osMac
	case "linux":
		result.os = osLinux
	case "windows":
		result.os = osWindows
	default:
		found = false
	}
	switch archToken {
	case "x64":
		result.arch = "amd64"
	case "aarch64":
		result.arch = "aarch64"
	default:
		found = false
	}
	if !found {
		return result, fmt.Errorf("%q is not a host platform such as darwin_aarch64", name)
	}
	return result, nil
}

type launchFiles struct {
	buildTxt       string
	ideaProperties string
	vmOptions      string
	productInfo    string
}

func renderLaunchFiles(model launchModel, target platform, openedPackagesFile string, ideaPropertiesBase string) (launchFiles, error) {
	vmOptions, found := model.VMOptions[target.os]
	if !found {
		return launchFiles{}, fmt.Errorf("the model states no vmoptions for %s", target.os)
	}
	separator := "\n"
	if target.os == osWindows {
		separator = "\r\n"
	}
	var vmOptionsText strings.Builder
	for _, line := range vmOptions {
		for _, char := range line {
			if char > 0x7f {
				return launchFiles{}, fmt.Errorf("the vmoptions line %q is not ASCII", line)
			}
		}
		vmOptionsText.WriteString(line)
		vmOptionsText.WriteString(separator)
	}
	productInfo, err := renderProductInfo(model, target, openedPackages(openedPackagesFile, target.os))
	if err != nil {
		return launchFiles{}, err
	}
	return launchFiles{
		buildTxt:       model.ProductCode + "-" + model.BuildNumber,
		ideaProperties: renderIdeaProperties(model.IdeaProperties, ideaPropertiesBase),
		vmOptions:      vmOptionsText.String(),
		productInfo:    productInfo,
	}, nil
}

func renderIdeaProperties(properties ideaProperties, base string) string {
	var text strings.Builder
	text.WriteString(base)
	for _, addition := range properties.Additions {
		text.WriteByte('\n')
		text.WriteString(addition)
	}
	return strings.ReplaceAll(text.String(), "@@settings_dir@@", properties.SettingsDir) + properties.Suffix
}

// openedPackages is `JavaModuleOptions.readOptions` of the `OpenedPackages.txt` text for [os]: every line, minus the
// ones that name a package of another OS.
func openedPackages(text string, os string) []string {
	var exclusions []string
	if os != osWindows {
		exclusions = append(exclusions, "/sun.awt.windows")
	}
	if os != osMac {
		exclusions = append(exclusions, "/sun.lwawt", "/com.apple")
	}
	if os != osLinux {
		exclusions = append(exclusions, "/sun.awt.X11", "/com.sun.java.swing.plaf.gtk")
	}
	var result []string
	for _, line := range javaLines(text) {
		excluded := false
		for _, exclusion := range exclusions {
			if strings.Contains(line, exclusion) {
				excluded = true
				break
			}
		}
		if !excluded {
			result = append(result, line)
		}
	}
	return result
}

// javaLines splits text the way `java.nio.file.Files.lines` does: at `\n`, `\r\n` or `\r`, with no empty last line.
func javaLines(text string) []string {
	var lines []string
	for len(text) > 0 {
		end := strings.IndexAny(text, "\r\n")
		if end < 0 {
			lines = append(lines, text)
			break
		}
		lines = append(lines, text[:end])
		if text[end] == '\r' && end+1 < len(text) && text[end+1] == '\n' {
			end++
		}
		text = text[end+1:]
	}
	return lines
}

// additionalJvmArguments is `renderAdditionalJvmArguments` of `ProductLaunchRenderer.kt`, for a launcher that is not a
// script and a distribution that is not portable.
func additionalJvmArguments(jvm jvmArguments, target platform, openedPackages []string, qodana bool) []string {
	var result []string
	macroName := "$IDE_HOME"
	switch target.os {
	case osWindows:
		macroName = "%IDE_HOME%"
	case osMac:
		macroName = "$APP_PACKAGE/Contents"
	}

	bootClassPathJarNames := append([]string(nil), jvm.XBootClassPathJarNames...)
	if !qodana && jvm.MultiRoutingFileSystem {
		bootClassPathJarNames = append(bootClassPathJarNames, "nio-fs.jar")
	}
	if len(bootClassPathJarNames) > 0 {
		pathSeparator, dirSeparator := ":", "/"
		if target.os == osWindows {
			pathSeparator, dirSeparator = ";", `\`
		}
		entries := make([]string, len(bootClassPathJarNames))
		for index, name := range bootClassPathJarNames {
			entries[index] = strings.Join([]string{macroName, "lib", name}, dirSeparator)
		}
		result = append(result, "-Xbootclasspath/a:"+strings.Join(entries, pathSeparator))
	}

	if jvm.CdsArchiveFileName != nil {
		cacheDir := "$IDE_CACHE_DIR/"
		if target.os == osWindows {
			cacheDir = `%IDE_CACHE_DIR%\`
		}
		result = append(result, "-XX:SharedArchiveFile="+cacheDir+*jvm.CdsArchiveFileName, "-XX:+AutoCreateSharedArchive")
	} else if jvm.ClassLoader != nil {
		result = append(result, "-Djava.system.class.loader="+*jvm.ClassLoader)
	}

	result = append(result, "-Didea.vendor.name="+jvm.VendorName, "-Didea.paths.selector="+jvm.PathsSelector)
	if jvm.Jna {
		result = append(result, "-Djna.boot.library.path="+macroName+"/lib/jna/"+target.arch, "-Djna.nosys=true", "-Djna.noclasspath=true")
	}
	if jvm.Pty4j {
		result = append(result, "-Dpty4j.preferred.native.folder="+macroName+"/lib/pty4j")
	}
	result = append(result, "-Dio.netty.allocator.type=pooled")
	if jvm.Skiko {
		result = append(result, "-Dskiko.library.path="+macroName+"/lib/skiko-awt-runtime-all")
	}
	if jvm.RuntimeModuleRepository {
		result = append(result, "-Dintellij.platform.runtime.repository.path="+macroName+"/modules/module-descriptors.dat")
	}
	if jvm.RootModule != nil {
		productMode := ""
		if jvm.ProductMode != nil {
			productMode = *jvm.ProductMode
		}
		result = append(result, "-Dintellij.platform.root.module="+*jvm.RootModule, "-Dintellij.platform.product.mode="+productMode)
	}
	if jvm.PlatformPrefix != nil {
		result = append(result, "-Didea.platform.prefix="+*jvm.PlatformPrefix)
	}
	result = append(result, jvm.Additional...)
	if jvm.Splash {
		result = append(result, "-Dsplash=true")
	}
	result = append(result, "-Daether.connector.resumeDownloads=false", "-Dcompose.swing.render.on.graphics=true")
	if jvm.NativeAccess {
		result = append(result, "--enable-native-access=ALL-UNNAMED")
	}
	return append(result, openedPackages...)
}

var majorReleaseDate = regexp.MustCompile(`^\d{8}$`)

// renderProductInfo renders `product-info.json` of a launch that bundles a runtime, with no built-in modules.
func renderProductInfo(model launchModel, target platform, openedPackages []string) (string, error) {
	if !majorReleaseDate.MatchString(model.MajorVersionReleaseDate) {
		return "", fmt.Errorf("the major release date %q is not yyyyMMdd", model.MajorVersionReleaseDate)
	}
	toRoot := ""
	if target.os == osMac && !model.LanguageServer {
		toRoot = "../"
	}
	var launcherPath, javaExecutablePath string
	switch target.os {
	case osMac:
		launcherDir := "../MacOS"
		if model.LanguageServer {
			launcherDir = "bin"
		}
		launcherPath = launcherDir + "/" + model.BaseFileName
		javaExecutablePath = toRoot + "jbr/Contents/Home/bin/java"
	case osLinux:
		launcherPath = "bin/" + model.BaseFileName
		javaExecutablePath = "jbr/bin/java"
	default:
		launcherPath = "bin/" + add64IfNeeded(model.BaseFileName, model.LanguageServer) + ".exe"
		javaExecutablePath = "jbr/bin/java.exe"
	}

	launch := jsonObject{}
	launch.string("os", target.os)
	launch.string("arch", target.arch)
	launch.string("launcherPath", launcherPath)
	launch.string("javaExecutablePath", javaExecutablePath)
	launch.string("vmOptionsFilePath", vmOptionsFilePath(target.os, model.BaseFileName, model.LanguageServer))
	if target.os == osLinux {
		launch.string("startupWmClass", model.Launch.LinuxStartupWmClass)
	}
	launch.strings("bootClassPathJarNames", model.Launch.BootClassPathJarNames)
	launch.strings("additionalJvmArguments", additionalJvmArguments(model.Launch.JvmArguments, target, openedPackages, false))
	launch.string("mainClass", model.Launch.MainClass)
	launch.optionalString("stdioRedirectArg", model.Launch.StdioRedirectArg)
	var commands []jsonValue
	for _, command := range model.CustomCommands {
		commands = append(commands, renderCustomCommand(command, target, openedPackages))
	}
	launch.array("customCommands", commands)

	info := jsonObject{}
	info.string("name", model.ProductName)
	info.string("version", model.Version)
	info.optionalString("versionSuffix", model.VersionSuffix)
	info.string("buildNumber", model.BuildNumber)
	info.string("productCode", model.ProductCode)
	info.string("envVarBaseName", model.EnvVarBaseName)
	info.string("dataDirectoryName", model.DataDirectoryName)
	if model.SvgIcon {
		info.string("svgIconPath", toRoot+"bin/"+model.BaseFileName+".svg")
	}
	info.string("productVendor", model.ProductVendor)
	info.string("majorVersionReleaseDate", model.MajorVersionReleaseDate)
	info.number("minRequiredJavaVersion", model.MinRequiredJavaVersion)
	info.add("launch", jsonArray{launch})
	var properties []jsonValue
	for _, property := range model.CustomProperties {
		value := jsonObject{}
		value.string("key", property.Key)
		value.string("value", property.Value)
		properties = append(properties, value)
	}
	info.array("customProperties", properties)
	var flavors []jsonValue
	if model.Jbr17 {
		flavors = append(flavors, flavor("jbr17"))
	}
	for _, id := range model.Flavors {
		flavors = append(flavors, flavor(id))
	}
	info.array("flavors", flavors)
	return info.render(""), nil
}

func renderCustomCommand(command customCommand, target platform, openedPackages []string) jsonObject {
	var arguments []string
	if command.JvmArguments != nil {
		arguments = append(arguments, additionalJvmArguments(*command.JvmArguments, target, openedPackages, command.Qodana)...)
	}
	if target.os == osMac {
		arguments = append(arguments, command.MacJvmArguments...)
	}
	arguments = append(arguments, command.ExtraJvmArguments...)

	result := jsonObject{}
	result.add("commands", stringArray(command.Commands))
	if path, found := command.VMOptionsFilePath[target.os]; found {
		result.string("vmOptionsFilePath", path)
	}
	result.strings("bootClassPathJarNames", command.BootClassPathJarNames)
	result.strings("additionalJvmArguments", arguments)
	result.optionalString("mainClass", command.MainClass)
	result.optionalString("envVarBaseName", command.EnvVarBaseName)
	result.optionalString("dataDirectoryName", command.DataDirectoryName)
	return result
}

func flavor(id string) jsonObject {
	result := jsonObject{}
	result.string("id", id)
	return result
}

// vmOptionsFilePath is `vmOptionsFilePath` of `ProductLaunchModel.kt` for a launch of the product itself.
func vmOptionsFilePath(os string, baseFileName string, languageServer bool) string {
	switch os {
	case osMac:
		if languageServer {
			return "bin/" + baseFileName + ".vmoptions"
		}
		return "../bin/" + baseFileName + ".vmoptions"
	case osLinux:
		return "bin/" + add64IfNeeded(baseFileName, languageServer) + ".vmoptions"
	default:
		return "bin/" + add64IfNeeded(baseFileName, languageServer) + ".exe.vmoptions"
	}
}

func add64IfNeeded(name string, languageServer bool) string {
	if languageServer {
		return name
	}
	return name + "64"
}
