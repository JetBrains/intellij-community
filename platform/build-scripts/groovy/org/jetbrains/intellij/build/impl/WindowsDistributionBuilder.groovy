// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl


import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileFilters
import com.intellij.openapi.util.text.StringUtilRt
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.xml.XmlUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.w3c.dom.Node

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@CompileStatic
final class WindowsDistributionBuilder extends OsSpecificDistributionBuilder {
  private final WindowsDistributionCustomizer customizer
  private final Path ideaProperties
  private final Path patchedApplicationInfo
  private final String icoPath

  WindowsDistributionBuilder(BuildContext buildContext, WindowsDistributionCustomizer customizer, Path ideaProperties, Path patchedApplicationInfo) {
    super(buildContext)
    this.patchedApplicationInfo = patchedApplicationInfo
    this.customizer = customizer
    this.ideaProperties = ideaProperties
    icoPath = (buildContext.applicationInfo.isEAP ? customizer.icoPathForEAP : null) ?: customizer.icoPath
  }

  @Override
  OsFamily getTargetOs() {
    return OsFamily.WINDOWS
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
  void copyFilesForOsDistribution(@NotNull Path winDistPath) {
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
    BuildTasksImpl.copyResourceFiles(buildContext, winDistPath)

    Files.writeString(distBinDir.resolve(ideaProperties.fileName), StringUtilRt.convertLineSeparators(Files.readString(ideaProperties), "'\r\n"))

    if (icoPath != null) {
      Files.copy(Paths.get(icoPath), distBinDir.resolve("${buildContext.productProperties.baseFileName}.ico"), StandardCopyOption.REPLACE_EXISTING)
    }
    if (customizer.includeBatchLaunchers) {
      generateScripts(winDistPath)
    }
    List<JvmArchitecture> architectures = customizer.include32BitLauncher ? List.of(JvmArchitecture.values()) : List.of(JvmArchitecture.x64)
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
  @CompileStatic(TypeCheckingMode.SKIP)
  void buildArtifacts(@NotNull Path winDistPath) {
    if (customizer.include32BitLauncher) {
      buildContext.bundledJreManager.repackageX86Jre(OsFamily.WINDOWS)
    }

    String zipPath = null, exePath = null
    String jreDirectoryPath = buildContext.bundledJreManager.extractJre(OsFamily.WINDOWS)

    if (jreDirectoryPath != null) {
      File vcRtDll = new File(jreDirectoryPath, "jbr/bin/msvcp140.dll")
      if (!vcRtDll.exists()) {
        buildContext.messages.error(
          "VS C++ Runtime DLL (${vcRtDll.name}) not found in ${vcRtDll.parent}.\n" +
          "If JBR uses a newer version, please correct the path in this code and update Windows Launcher build configuration.\n" +
          "If DLL was relocated to another place, please correct the path in this code.")
      }
      buildContext.ant.copy(file: vcRtDll, toDir: "$winDistPath/bin")
    }

    if (customizer.buildZipArchive) {
      def jreDirectoryPaths = customizer.zipArchiveWithBundledJre ? [jreDirectoryPath] : []
      zipPath = buildWinZip(jreDirectoryPaths, ".win", winDistPath)
    }

    buildContext.executeStep("Build Windows Exe Installer", BuildOptions.WINDOWS_EXE_INSTALLER_STEP) {
      def productJsonDir = new File(buildContext.paths.temp, "win.dist.product-info.json.exe").absolutePath
      generateProductJson(productJsonDir, jreDirectoryPath != null)
      new ProductInfoValidator(buildContext).validateInDirectory(productJsonDir, "", [winDistPath.toString(), jreDirectoryPath], [])
      exePath = new WinExeInstallerBuilder(buildContext, customizer, jreDirectoryPath)
        .buildInstaller(winDistPath.toString(), productJsonDir, '', buildContext.windowsDistributionCustomizer.include32BitLauncher)
    }

    if (!buildContext.options.isInDevelopmentMode && zipPath != null && exePath != null) {
      if (SystemInfoRt.isLinux) {
        buildContext.messages.info("Comparing ${new File(zipPath).name} vs. ${new File(exePath).name} ...")

        File tempZip = new File(buildContext.paths.temp, "__zip")
        buildContext.ant.mkdir(dir: tempZip)
        buildContext.ant.exec(executable: "unzip", dir: tempZip, failOnError: true) {
          arg(value: "-qq")
          arg(value: zipPath)
        }

        File tempExe = new File(buildContext.paths.temp, "__exe")
        buildContext.ant.mkdir(dir: tempExe)
        buildContext.ant.exec(executable: "7z", dir: tempExe, failOnError: true) {
          arg(value: "x")
          arg(value: "-bd")
          arg(value: exePath)
        }
        if (new File("${tempExe}/\$PLUGINSDIR").exists()) {
          buildContext.ant.delete(dir: "${tempExe}/\$PLUGINSDIR")
        }

        buildContext.ant.exec(executable: "diff", failOnError: true) {
          arg(value: "-q")
          arg(value: "-r")
          arg(value: tempZip.path)
          arg(value: tempExe.path)
        }

        buildContext.ant.delete(dir: tempZip)
        buildContext.ant.delete(dir: tempExe)
      }
      else {
        buildContext.messages.warning("Comparing .zip and .exe is not supported on ${SystemInfoRt.OS_NAME}")
      }
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


    buildContext.ant.fixcrlf(srcdir: "$winDistPath/bin", includes: "*.bat", eol: "dos")
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
      def launcherPropertiesPath = "${buildContext.paths.temp}/launcher${arch.fileSuffix}.properties"
      def upperCaseProductName = buildContext.applicationInfo.upperCaseProductName
      String vmOptions = (buildContext.additionalJvmArguments +
                          " -Dide.native.launcher=true" +
                          " -Didea.vendor.name=${buildContext.applicationInfo.shortCompanyName}" +
                          " -Didea.paths.selector=${buildContext.systemSelector}").trim()
      def productName = buildContext.applicationInfo.shortProductName
      String classPath = buildContext.bootClassPathJarNames.join(";")

      String jdkEnvVarSuffix = arch == JvmArchitecture.x64 && customizer.include32BitLauncher ? "_64" : ""
      String vmOptionsEnvVarSuffix = arch == JvmArchitecture.x64 && customizer.include32BitLauncher ? "64" : ""
      def envVarBaseName = buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo)
      Path icoFilesDirectory = Paths.get(buildContext.paths.temp, "win-launcher-ico")
      Path appInfoForLauncher = generateApplicationInfoForLauncher(patchedApplicationInfo, icoFilesDirectory)
      new File(launcherPropertiesPath).text = """
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
        IDS_CLASSPATH_LIBS=${classPath}""".stripIndent().trim()

      def communityHome = "$buildContext.paths.communityHome"
      String inputPath = "$communityHome/bin/WinLauncher/WinLauncher${arch.fileSuffix}.exe"
      Path outputPath = winDistPath.resolve("bin/${executableBaseName}.exe")
      List<JpsModule> resourceModules = List.of(buildContext.findApplicationInfoModule(), buildContext.findModule("intellij.platform.icons"))
      buildContext.ant.java(classname: "com.pme.launcher.LauncherGeneratorMain", fork: "true", failonerror: "true") {
        sysproperty(key: "java.awt.headless", value: "true")
        arg(value: inputPath)
        arg(value: appInfoForLauncher.toString())
        arg(value: "$communityHome/native/WinLauncher/WinLauncher/resource.h")
        arg(value: launcherPropertiesPath)
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
  @CompileStatic(TypeCheckingMode.SKIP)
  private Path generateApplicationInfoForLauncher(@NotNull Path applicationInfoFile, @NotNull Path icoFilesDirectory) {
    Files.createDirectories(icoFilesDirectory)
    if (icoPath == null) {
      return applicationInfoFile
    }

    Path icoFile = Paths.get(icoPath)
    Files.copy(icoFile, icoFilesDirectory.resolve(icoFile.fileName), StandardCopyOption.REPLACE_EXISTING)
    def root = Files.newBufferedReader(applicationInfoFile).withCloseable { new XmlParser().parse(it) }
    Node iconNode = root.icon.first()
    iconNode.@ico = icoFile.name
    Path patchedFile = Paths.get(buildContext.paths.temp, "win-launcher-application-info.xml")
    Files.newBufferedWriter(patchedFile).withCloseable {
      XmlUtil.serialize(root, it)
    }
    return patchedFile
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private String buildWinZip(List<String> jreDirectoryPaths, String zipNameSuffix, Path winDistPath) {
    buildContext.messages.block("Build Windows ${zipNameSuffix}.zip distribution") {
      def baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
      def targetPath = "${buildContext.paths.artifacts}/${baseName}${zipNameSuffix}.zip"
      def zipPrefix = customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
      List<String> dirs = [buildContext.paths.distAll, winDistPath.toString()] + jreDirectoryPaths
      buildContext.messages.progress("Building Windows $targetPath archive")
      Path productJsonDir = Paths.get(buildContext.paths.temp, "win.dist.product-info.json.zip$zipNameSuffix")
      generateProductJson(productJsonDir, !jreDirectoryPaths.isEmpty())
      dirs.add(productJsonDir.toAbsolutePath().toString())
      buildContext.ant.zip(zipfile: targetPath) {
        dirs.each {
          zipfileset(dir: it, prefix: zipPrefix)
        }
      }

      new ProductInfoValidator(buildContext).checkInArchive(targetPath, zipPrefix)
      buildContext.notifyArtifactBuilt(targetPath)
      return targetPath
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
