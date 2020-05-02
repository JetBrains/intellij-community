// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileFilters
import com.intellij.openapi.util.io.FileUtil
import groovy.xml.XmlUtil
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRoot

class WindowsDistributionBuilder extends OsSpecificDistributionBuilder {
  private final WindowsDistributionCustomizer customizer
  private final File ideaProperties
  private final File patchedApplicationInfo
  private final String icoPath

  WindowsDistributionBuilder(BuildContext buildContext, WindowsDistributionCustomizer customizer, File ideaProperties, File patchedApplicationInfo) {
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
  void copyFilesForOsDistribution(String winDistPath) {
    buildContext.messages.progress("Building distributions for $targetOs.osName")
    buildContext.ant.copy(todir: "$winDistPath/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/win") {
        if (!buildContext.includeBreakGenLibraries()) {
          exclude(name: "breakgen*")
        }
      }
    }
    BuildTasksImpl.unpackPty4jNative(buildContext, winDistPath, "win")
    BuildTasksImpl.generateBuildTxt(buildContext, winDistPath)

    buildContext.ant.copy(file: ideaProperties.path, todir: "$winDistPath/bin")
    buildContext.ant.fixcrlf(file: "$winDistPath/bin/idea.properties", eol: "dos")

    if (icoPath != null) {
      buildContext.ant.copy(file: icoPath, tofile: "$winDistPath/bin/${buildContext.productProperties.baseFileName}.ico")
    }
    if (customizer.includeBatchLaunchers) {
      generateScripts(winDistPath)
    }
    List<JvmArchitecture> architectures = customizer.include32BitLauncher ? JvmArchitecture.values() : [JvmArchitecture.x64]
    generateVMOptions(winDistPath, architectures)
    architectures.each {
      buildWinLauncher(it, winDistPath)
    }
    customizer.copyAdditionalFiles(buildContext, winDistPath)
    List<String> extensions = ["exe", "dll"]
    for (String extension : extensions) {
      new File(winDistPath, "bin").listFiles(FileFilters.filesWithExtension(extension))?.each {
        buildContext.signExeFile(it.absolutePath)
      }
    }
  }

  @Override
  void buildArtifacts(String winDistPath) {
    if (customizer.include32BitLauncher) {
      buildContext.bundledJreManager.repackageX86Jre(OsFamily.WINDOWS)
    }

    def jreDirectoryPath = buildContext.bundledJreManager.extractJre(OsFamily.WINDOWS)
    if (customizer.buildZipArchive) {
      buildWinZip([jreDirectoryPath], ".win", winDistPath)
    }

    buildContext.executeStep("Build Windows Exe Installer", BuildOptions.WINDOWS_EXE_INSTALLER_STEP) {
      def productJsonDir = new File(buildContext.paths.temp, "win.dist.product-info.json.exe").absolutePath
      generateProductJson(productJsonDir, jreDirectoryPath != null)
      new ProductInfoValidator(buildContext).validateInDirectory(productJsonDir, "", [winDistPath, jreDirectoryPath], [])
      new WinExeInstallerBuilder(buildContext, customizer, jreDirectoryPath)
        .buildInstaller(winDistPath, productJsonDir, '', buildContext.windowsDistributionCustomizer.include32BitLauncher)
    }
  }

  private void generateScripts(String winDistPath) {
    String fullName = buildContext.applicationInfo.productName
    String vmOptionsFileName = "${buildContext.productProperties.baseFileName}%BITS%.exe"

    String classPath = "SET CLASS_PATH=%IDE_HOME%\\lib\\${buildContext.bootClassPathJarNames[0]}\n"
    classPath += buildContext.bootClassPathJarNames[1..-1].collect { "SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\$it" }.join("\n")
    if (buildContext.productProperties.toolsJarRequired) {
      classPath += "\nSET CLASS_PATH=%CLASS_PATH%;%JDK%\\lib\\tools.jar"
    }

    def batName = "${buildContext.productProperties.baseFileName}.bat"
    buildContext.ant.copy(todir: "$winDistPath/bin") {
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
        filter(token: "script_name", value: batName)
      }
    }

    buildContext.ant.move(file: "$winDistPath/bin/executable-template.bat", tofile: "$winDistPath/bin/$batName")

    String inspectScript = buildContext.productProperties.inspectCommandName
    if (inspectScript != "inspect") {
      String targetPath = "$winDistPath/bin/${inspectScript}.bat"
      buildContext.ant.move(file: "$winDistPath/bin/inspect.bat", tofile: targetPath)
      buildContext.patchInspectScript(targetPath)
    }


    buildContext.ant.fixcrlf(srcdir: "$winDistPath/bin", includes: "*.bat", eol: "dos")
  }

  private void generateVMOptions(String winDistPath, Collection<JvmArchitecture> architectures) {
    architectures.each {
      def fileName = "${buildContext.productProperties.baseFileName}${it.fileSuffix}.exe.vmoptions"
      def vmOptions = VmOptionsGenerator.computeVmOptions(it, buildContext.applicationInfo.isEAP, buildContext.productProperties)
      new File(winDistPath, "bin/$fileName").text = vmOptions.join("\n") + "\n"
    }

    buildContext.ant.fixcrlf(srcdir: "$winDistPath/bin", includes: "*.vmoptions", eol: "dos")
  }

  private void buildWinLauncher(JvmArchitecture arch, String winDistPath) {
    buildContext.messages.block("Build Windows executable ${arch.name()}") {
      String exeFileName = "${buildContext.productProperties.baseFileName}${arch.fileSuffix}.exe"
      def launcherPropertiesPath = "${buildContext.paths.temp}/launcher${arch.fileSuffix}.properties"
      def upperCaseProductName = buildContext.applicationInfo.upperCaseProductName
      def lowerCaseProductName = buildContext.applicationInfo.shortProductName.toLowerCase()
      String vmOptions = "$buildContext.additionalJvmArguments -Dide.native.launcher=true -Didea.paths.selector=${buildContext.systemSelector}".trim()
      def productName = buildContext.applicationInfo.shortProductName
      String classPath = buildContext.bootClassPathJarNames.join(";")

      String jdkEnvVarSuffix = arch == JvmArchitecture.x64 && customizer.include32BitLauncher ? "_64" : ""
      String vmOptionsEnvVarSuffix = arch == JvmArchitecture.x64 && customizer.include32BitLauncher ? "64" : ""
      def envVarBaseName = buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo)
      File icoFilesDirectory = new File(buildContext.paths.temp, "win-launcher-ico")
      File appInfoForLauncher = generateApplicationInfoForLauncher(patchedApplicationInfo, icoFilesDirectory)
      new File(launcherPropertiesPath).text = """
        IDS_JDK_ONLY=$buildContext.productProperties.toolsJarRequired
        IDS_JDK_ENV_VAR=${envVarBaseName}_JDK$jdkEnvVarSuffix
        IDS_APP_TITLE=$productName Launcher
        IDS_VM_OPTIONS_PATH=%APPDATA%\\\\${buildContext.applicationInfo.shortCompanyName}\\\\${buildContext.systemSelector}
        IDS_VM_OPTION_ERRORFILE=-XX:ErrorFile=%USERPROFILE%\\\\java_error_in_${lowerCaseProductName}_%p.log
        IDS_VM_OPTION_HEAPDUMPPATH=-XX:HeapDumpPath=%USERPROFILE%\\\\java_error_in_${lowerCaseProductName}.hprof
        IDC_WINLAUNCHER=${upperCaseProductName}_LAUNCHER
        IDS_PROPS_ENV_VAR=${envVarBaseName}_PROPERTIES
        IDS_VM_OPTIONS_ENV_VAR=$envVarBaseName${vmOptionsEnvVarSuffix}_VM_OPTIONS
        IDS_ERROR_LAUNCHING_APP=Error launching ${productName}
        IDS_VM_OPTIONS=${vmOptions}
        IDS_CLASSPATH_LIBS=${classPath}""".stripIndent().trim()

      def communityHome = "$buildContext.paths.communityHome"
      String inputPath = "$communityHome/bin/WinLauncher/WinLauncher${arch.fileSuffix}.exe"
      def outputPath = "$winDistPath/bin/$exeFileName"
      def resourceModules = [buildContext.findApplicationInfoModule(), buildContext.findModule("intellij.platform.icons")]
      buildContext.ant.java(classname: "com.pme.launcher.LauncherGeneratorMain", fork: "true", failonerror: "true") {
        sysproperty(key: "java.awt.headless", value: "true")
        arg(value: inputPath)
        arg(value: appInfoForLauncher.absolutePath)
        arg(value: "$communityHome/native/WinLauncher/WinLauncher/resource.h")
        arg(value: launcherPropertiesPath)
        arg(value: outputPath)
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
          pathelement(location: icoFilesDirectory.absolutePath)
        }
      }
    }
  }

  /**
   * Generates ApplicationInfo.xml file for launcher generator which contains link to proper *.ico file.
   * //todo[nik] pass path to ico file to LauncherGeneratorMain directly (probably after IDEA-196705 is fixed).
   */
  File generateApplicationInfoForLauncher(File applicationInfoFile, File icoFilesDirectory) {
    FileUtil.createDirectory(icoFilesDirectory)
    if (icoPath == null) {
      return applicationInfoFile
    }

    def icoFile = new File(icoPath)
    buildContext.ant.copy(file: icoPath, todir: icoFilesDirectory.absolutePath)
    def root = new XmlParser().parse(applicationInfoFile)
    def iconNode = root.icon.first()
    iconNode.@ico = icoFile.name
    def patchedFile = new File(buildContext.paths.temp, "win-launcher-application-info.xml")
    patchedFile.withWriter {
      XmlUtil.serialize(root, it)
    }
    return patchedFile
  }

  private void buildWinZip(List<String> jreDirectoryPaths, String zipNameSuffix, String winDistPath) {
    buildContext.messages.block("Build Windows ${zipNameSuffix}.zip distribution") {
      def baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
      def targetPath = "${buildContext.paths.artifacts}/${baseName}${zipNameSuffix}.zip"
      def zipPrefix = customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
      def dirs = [buildContext.paths.distAll, winDistPath] + jreDirectoryPaths
      buildContext.messages.progress("Building Windows $targetPath archive")
      def productJsonDir = new File(buildContext.paths.temp, "win.dist.product-info.json.zip$zipNameSuffix").absolutePath
      generateProductJson(productJsonDir, !jreDirectoryPaths.isEmpty())
      dirs += [productJsonDir]
      buildContext.ant.zip(zipfile: targetPath) {
        dirs.each {
          zipfileset(dir: it, prefix: zipPrefix)
        }
      }

      new ProductInfoValidator(buildContext).checkInArchive(targetPath, zipPrefix)
      buildContext.notifyArtifactBuilt(targetPath)
    }
  }

  private void generateProductJson(String targetDir, boolean isJreIncluded) {
    def launcherPath = "bin/${buildContext.productProperties.baseFileName}64.exe"
    def vmOptionsPath = "bin/${buildContext.productProperties.baseFileName}64.exe.vmoptions"
    def javaExecutablePath = isJreIncluded ? "jbr/bin/java.exe" : null
    new ProductInfoGenerator(buildContext)
      .generateProductJson(targetDir, "bin", null, launcherPath, javaExecutablePath, vmOptionsPath, OsFamily.WINDOWS)
  }
}