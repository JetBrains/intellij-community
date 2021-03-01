// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileFilters
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot

import java.nio.file.*

@CompileStatic
final class WindowsDistributionBuilder extends OsSpecificDistributionBuilder {
  private final WindowsDistributionCustomizer customizer
  private final Path ideaProperties
  private final Path patchedApplicationInfo
  private final Path icoFile

  WindowsDistributionBuilder(BuildContext buildContext, WindowsDistributionCustomizer customizer, Path ideaProperties, Path patchedApplicationInfo) {
    super(buildContext)
    this.patchedApplicationInfo = patchedApplicationInfo
    this.customizer = customizer
    this.ideaProperties = ideaProperties

    String icoPath = (buildContext.applicationInfo.isEAP ? customizer.icoPathForEAP : null) ?: customizer.icoPath
    icoFile = icoPath == null ? null : Paths.get(icoPath)
  }

  @Override
  OsFamily getTargetOs() {
    return OsFamily.WINDOWS
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
  void copyFilesForOsDistribution(@NotNull Path winDistPath, JvmArchitecture arch = null) {
    Path distBinDir = winDistPath.resolve("bin")
    Files.createDirectories(distBinDir)

    buildContext.messages.progress("Building distributions for $targetOs.osName")
    buildContext.ant.copy(todir: distBinDir.toString()) {
      fileset(dir: "$buildContext.paths.communityHome/bin/win") {
        if (!buildContext.includeBreakGenLibraries()) {
          exclude(name: "breakgen*")
        }
      }
    }
    BuildTasksImpl.unpackPty4jNative(buildContext, winDistPath, "win")
    BuildTasksImpl.generateBuildTxt(buildContext, winDistPath)
    BuildTasksImpl.copyDistFiles(buildContext, winDistPath)

    Files.writeString(distBinDir.resolve(ideaProperties.fileName), StringUtilRt.convertLineSeparators(Files.readString(ideaProperties), "\r\n"))

    if (icoFile != null) {
      Files.copy(icoFile, distBinDir.resolve("${buildContext.productProperties.baseFileName}.ico"), StandardCopyOption.REPLACE_EXISTING)
    }
    if (customizer.includeBatchLaunchers) {
      generateScripts(distBinDir)
    }
    List<JvmArchitecture> architectures = customizer.include32BitLauncher ? List.of(JvmArchitecture.x32, JvmArchitecture.x64) : List.of(JvmArchitecture.x64)
    generateVMOptions(distBinDir, architectures)
    for (JvmArchitecture architecture : architectures) {
      buildWinLauncher(architecture, winDistPath)
    }
    customizer.copyAdditionalFiles(buildContext, winDistPath.toString())
    for (String extension : ["exe", "dll"]) {
      distBinDir.toFile().listFiles(FileFilters.filesWithExtension(extension))?.each {
        buildContext.signExeFile(it.absolutePath)
      }
    }
  }

  @Override
  void buildArtifacts(@NotNull Path winDistPath) {
    copyFilesForOsDistribution(winDistPath)
    if (customizer.include32BitLauncher) {
      buildContext.executeStep("Packaging x86 JRE for $OsFamily.WINDOWS", BuildOptions.WINDOWS_JRE_FOR_X86_STEP) {
        buildContext.bundledJreManager.repackageX86Jre(OsFamily.WINDOWS)
      }
    }

    String zipPath = null, exePath = null
    Path jreDir = buildContext.bundledJreManager.extractJre(OsFamily.WINDOWS)
    if (jreDir != null) {
      Path vcRtDll = jreDir.resolve("jbr/bin/msvcp140.dll")
      try {
        BuildHelper.copyFileToDir(vcRtDll, winDistPath.resolve("bin"))
      }
      catch (NoSuchFileException ignore) {
        buildContext.messages.error(
          "VS C++ Runtime DLL (${vcRtDll.fileName}) not found in ${vcRtDll.parent}.\n" +
          "If JBR uses a newer version, please correct the path in this code and update Windows Launcher build configuration.\n" +
          "If DLL was relocated to another place, please correct the path in this code.")
      }
    }

    if (customizer.buildZipArchive) {
      List<Path> jreDirectoryPaths = customizer.zipArchiveWithBundledJre ? [jreDir] : []
      zipPath = buildWinZip(jreDirectoryPaths, ".win", winDistPath)
    }

    buildContext.executeStep("Build Windows Exe Installer", BuildOptions.WINDOWS_EXE_INSTALLER_STEP) {
      Path productJsonDir = buildContext.paths.tempDir.resolve("win.dist.product-info.json.exe")
      generateProductJson(productJsonDir, jreDir != null)
      new ProductInfoValidator(buildContext).validateInDirectory(productJsonDir, "", [winDistPath.toString(), jreDir.toString()], [])
      exePath = new WinExeInstallerBuilder(buildContext, customizer, jreDir)
        .buildInstaller(winDistPath, productJsonDir, '', buildContext.windowsDistributionCustomizer.include32BitLauncher)
    }

    if (buildContext.options.isInDevelopmentMode || zipPath == null || exePath == null) {
      return
    }

    if (!SystemInfoRt.isLinux) {
      buildContext.messages.warning("Comparing .zip and .exe is not supported on ${SystemInfoRt.OS_NAME}")
      return
    }

    buildContext.messages.info("Comparing ${new File(zipPath).name} vs. ${new File(exePath).name} ...")

    Path tempZip = Files.createTempDirectory(buildContext.paths.tempDir, "zip-")
    Path tempExe = Files.createTempDirectory(buildContext.paths.tempDir, "exe-")
    try {
      BuildHelper.runProcess(buildContext, List.of("unzip", "-qq", zipPath), tempZip)
      BuildHelper.runProcess(buildContext, List.of("7z", "x", "-bd", exePath), tempExe)
      //noinspection SpellCheckingInspection
      FileUtil.delete(tempExe.resolve("\$PLUGINSDIR"))

      BuildHelper.runProcess(buildContext, List.of("diff", "-q", "-r", tempZip.toString(), tempExe.toString()))
    }
    finally {
      FileUtil.delete(tempZip)
      FileUtil.delete(tempExe)
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void generateScripts(@NotNull Path distBinDir) {
    String fullName = buildContext.applicationInfo.productName
    String baseName = buildContext.productProperties.baseFileName
    String scriptName = "${baseName}.bat"
    String vmOptionsFileName = "${baseName}%BITS%.exe"

    String classPath = "SET CLASS_PATH=%IDE_HOME%\\lib\\${buildContext.bootClassPathJarNames[0]}\n"
    classPath += buildContext.bootClassPathJarNames[1..-1].collect { "SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\$it" }.join("\n")
    if (buildContext.productProperties.toolsJarRequired) {
      classPath += "\nSET CLASS_PATH=%CLASS_PATH%;%JDK%\\lib\\tools.jar"
    }

    buildContext.ant.copy(todir: distBinDir.toString()) {
      fileset(dir: "$buildContext.paths.communityHome/platform/build-scripts/resources/win/scripts")

      filterset(begintoken: "@@", endtoken: "@@") {
        filter(token: "product_full", value: fullName)
        filter(token: "product_uc", value: buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo))
        filter(token: "product_vendor", value: buildContext.applicationInfo.shortCompanyName)
        filter(token: "vm_options", value: vmOptionsFileName)
        filter(token: "isEap", value: buildContext.applicationInfo.isEAP)
        filter(token: "system_selector", value: buildContext.systemSelector)
        filter(token: "ide_jvm_args", value: buildContext.additionalJvmArguments)
        filter(token: "class_path", value: classPath)
        filter(token: "script_name", value: scriptName)
        filter(token: "base_name", value: baseName)
      }
    }

    Files.move(distBinDir.resolve("executable-template.bat"), distBinDir.resolve(scriptName))
    String inspectScript = buildContext.productProperties.inspectCommandName
    if (inspectScript != "inspect") {
      Path targetPath = distBinDir.resolve("${inspectScript}.bat")
      Files.move(distBinDir.resolve("inspect.bat"), targetPath)
      buildContext.patchInspectScript(targetPath)
    }

    buildContext.ant.fixcrlf(srcdir: distBinDir.toString(), includes: "*.bat", eol: "dos")
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void generateVMOptions(@NotNull Path distBinDir, Collection<JvmArchitecture> architectures) {
    architectures.each {
      def fileName = "${buildContext.productProperties.baseFileName}${it.fileSuffix}.exe.vmoptions"
      def vmOptions = VmOptionsGenerator.computeVmOptions(it, buildContext.applicationInfo.isEAP, buildContext.productProperties)
      Files.writeString(distBinDir.resolve(fileName), vmOptions.join('\n') + '\n')
    }

    buildContext.ant.fixcrlf(srcdir: distBinDir.toString(), includes: "*.vmoptions", eol: "dos")
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildWinLauncher(JvmArchitecture arch, Path winDistPath) {
    buildContext.messages.block("Build Windows executable ${arch.name()}") {
      def executableBaseName = "${buildContext.productProperties.baseFileName}${arch.fileSuffix}"
      Path launcherPropertiesPath = buildContext.paths.tempDir.resolve("launcher${arch.fileSuffix}.properties")
      def upperCaseProductName = buildContext.applicationInfo.upperCaseProductName
      String vmOptions = (buildContext.additionalJvmArguments +
                          " -Dide.native.launcher=true" +
                          " -Didea.vendor.name=${buildContext.applicationInfo.shortCompanyName}" +
                          " -Didea.paths.selector=${buildContext.systemSelector}").trim()
      def productName = buildContext.applicationInfo.shortProductName
      String classPath = buildContext.bootClassPathJarNames.join(";")

      assert (arch in [JvmArchitecture.x32, JvmArchitecture.x64])
      String jdkEnvVarSuffix = arch == JvmArchitecture.x64 && customizer.include32BitLauncher ? "_64" : ""
      String vmOptionsEnvVarSuffix = arch == JvmArchitecture.x64 && customizer.include32BitLauncher ? "64" : ""
      def envVarBaseName = buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo)
      Path icoFilesDirectory = buildContext.paths.tempDir.resolve("win-launcher-ico")
      Path appInfoForLauncher = generateApplicationInfoForLauncher(patchedApplicationInfo, icoFilesDirectory)
      Files.writeString(launcherPropertiesPath, """
        IDS_JDK_ONLY=$buildContext.productProperties.toolsJarRequired
        IDS_JDK_ENV_VAR=${envVarBaseName}_JDK$jdkEnvVarSuffix
        IDS_APP_TITLE=$productName Launcher
        IDS_VM_OPTIONS_PATH=%APPDATA%\\\\${buildContext.applicationInfo.shortCompanyName}\\\\${buildContext.systemSelector}
        IDS_VM_OPTION_ERRORFILE=-XX:ErrorFile=%USERPROFILE%\\\\java_error_in_${executableBaseName}_%p.log
        IDS_VM_OPTION_HEAPDUMPPATH=-XX:HeapDumpPath=%USERPROFILE%\\\\java_error_in_${executableBaseName}.hprof
        IDC_WINLAUNCHER=${upperCaseProductName}_LAUNCHER
        IDS_PROPS_ENV_VAR=${envVarBaseName}_PROPERTIES
        IDS_VM_OPTIONS_ENV_VAR=$envVarBaseName${vmOptionsEnvVarSuffix}_VM_OPTIONS
        IDS_ERROR_LAUNCHING_APP=Error launching ${productName}
        IDS_VM_OPTIONS=${vmOptions}
        IDS_CLASSPATH_LIBS=${classPath}""".stripIndent().trim())

      def communityHome = "$buildContext.paths.communityHome"
      String inputPath = "$communityHome/bin/WinLauncher/WinLauncher${arch.fileSuffix}.exe"
      Path outputPath = winDistPath.resolve("bin/${executableBaseName}.exe")
      List<JpsModule> resourceModules = List.of(buildContext.findApplicationInfoModule(), buildContext.findModule("intellij.platform.icons"))
      buildContext.ant.java(classname: "com.pme.launcher.LauncherGeneratorMain", fork: "true", failonerror: "true") {
        sysproperty(key: "java.awt.headless", value: "true")
        arg(value: inputPath)
        arg(value: appInfoForLauncher.toString())
        arg(value: "$communityHome/native/WinLauncher/resource.h")
        arg(value: launcherPropertiesPath.toString())
        arg(value: outputPath.toString())
        classpath {
          pathelement(location: "$communityHome/build/lib/launcher-generator.jar")
          ["Guava", "JDOM", "commons-imaging"].each {
            buildContext.project.libraryCollection.findLibrary(it).getFiles(JpsOrderRootType.COMPILED).each {
              pathelement(location: it.absolutePath)
            }
          }
          resourceModules.collectMany { it.sourceRoots }.each { JpsModuleSourceRoot root ->
            pathelement(location: root.file.absolutePath)
          }
          buildContext.productProperties.brandingResourcePaths.each {
            pathelement(location: it)
          }
          pathelement(location: icoFilesDirectory.toString())
        }
      }
    }
  }

  /**
   * Generates ApplicationInfo.xml file for launcher generator which contains link to proper *.ico file.
   * //todo[nik] pass path to ico file to LauncherGeneratorMain directly (probably after IDEA-196705 is fixed).
   */
  private Path generateApplicationInfoForLauncher(@NotNull Path appInfoFile, @NotNull Path icoFilesDirectory) {
    if (icoFile == null) {
      return appInfoFile
    }

    Files.createDirectories(icoFilesDirectory)
    Files.copy(icoFile, icoFilesDirectory.resolve(icoFile.fileName), StandardCopyOption.REPLACE_EXISTING)
    Element root = JDOMUtil.load(appInfoFile)
    // do not use getChild - maybe null due to namespace
    Element iconElement = (Element)root.getContent().stream()
      .filter({ it instanceof Element && ((Element)it).getName() == "icon" })
      .findFirst()
      .orElseThrow({ new RuntimeException("`icon` element not found in $appInfoFile:\n${Files.readString(appInfoFile)}") })

    iconElement.setAttribute("ico", icoFile.fileName.toString())
    Path patchedFile = buildContext.paths.tempDir.resolve("win-launcher-application-info.xml")
    JDOMUtil.write(root, patchedFile)
    return patchedFile
  }

  private String buildWinZip(List<Path> jreDirectoryPaths, String zipNameSuffix, Path winDistPath) {
    return buildContext.messages.block("Build Windows ${zipNameSuffix}.zip distribution") {
      String baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
      Path targetFile = Paths.get(buildContext.paths.artifacts, "${baseName}${zipNameSuffix}.zip")
      buildContext.messages.progress("Building Windows $targetFile archive")
      Path productJsonDir = Paths.get(buildContext.paths.temp, "win.dist.product-info.json.zip$zipNameSuffix")
      generateProductJson(productJsonDir, !jreDirectoryPaths.isEmpty())

      String zipPrefix = customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
      List<Path> dirs = [Paths.get(buildContext.paths.distAll), winDistPath, productJsonDir] + jreDirectoryPaths
      BuildHelper.zip(buildContext, targetFile, dirs, zipPrefix)
      ProductInfoValidator.checkInArchive(buildContext, targetFile.toString(), zipPrefix)
      buildContext.notifyArtifactWasBuilt(targetFile)
      return targetFile
    }
  }

  private void generateProductJson(@NotNull Path targetDir, boolean isJreIncluded) {
    String launcherPath = "bin/${buildContext.productProperties.baseFileName}64.exe"
    String vmOptionsPath = "bin/${buildContext.productProperties.baseFileName}64.exe.vmoptions"
    String javaExecutablePath = isJreIncluded ? "jbr/bin/java.exe" : null
    new ProductInfoGenerator(buildContext)
      .generateProductJson(targetDir, "bin", null, launcherPath, javaExecutablePath, vmOptionsPath, OsFamily.WINDOWS)
  }
}

