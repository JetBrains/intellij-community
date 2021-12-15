// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileFilters
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.PathUtilRt
import com.intellij.util.Processor
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import io.opentelemetry.api.trace.Span
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot

import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.concurrent.ForkJoinTask
import java.util.function.Supplier

@CompileStatic
final class WindowsDistributionBuilder extends OsSpecificDistributionBuilder {
  private final WindowsDistributionCustomizer customizer
  private final Path ideaProperties
  private final String patchedApplicationInfo
  private final Path icoFile

  WindowsDistributionBuilder(BuildContext buildContext, WindowsDistributionCustomizer customizer, Path ideaProperties, String patchedApplicationInfo) {
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

    buildContext.messages.progress("build distributions for Windows")
    buildContext.ant.copy(todir: distBinDir.toString()) {
      fileset(dir: "$buildContext.paths.communityHome/bin/win") {
        if (!buildContext.includeBreakGenLibraries()) {
          exclude(name: "breakgen*")
        }
      }
    }
    def pty4jNativeDir = BuildTasksImpl.unpackPty4jNative(buildContext, winDistPath, "win")
    BuildTasksImpl.generateBuildTxt(buildContext, winDistPath)
    BuildTasksImpl.copyDistFiles(buildContext, winDistPath)

    Files.writeString(distBinDir.resolve(ideaProperties.fileName), StringUtilRt.convertLineSeparators(Files.readString(ideaProperties), "\r\n"))

    if (icoFile != null) {
      Files.copy(icoFile, distBinDir.resolve("${buildContext.productProperties.baseFileName}.ico"), StandardCopyOption.REPLACE_EXISTING)
    }
    if (customizer.includeBatchLaunchers) {
      generateScripts(distBinDir)
    }
    generateVMOptions(distBinDir)
    buildWinLauncher(winDistPath)
    customizer.copyAdditionalFiles(buildContext, winDistPath.toString())
    FileFilter signFileFilter = createFileFilter("exe", "dll")
    for (Path nativeRoot : List.of(distBinDir, pty4jNativeDir)) {
      FileUtil.processFilesRecursively(nativeRoot.toFile(), new Processor<File>() {
        @Override
        boolean process(File file) {
          if (signFileFilter.accept(file)) {
            buildContext.executeStep(TracerManager.spanBuilder("sign").setAttribute("file", file.toString()), BuildOptions.WIN_SIGN_STEP) {
              buildContext.signFile(file.toPath(), BuildOptions.WIN_SIGN_OPTIONS)
            }
          }
          return true
        }
      })
    }
  }

  @Override
  void buildArtifacts(@NotNull Path winDistPath) {
    copyFilesForOsDistribution(winDistPath)

    ForkJoinTask<Path> zipPathTask = null
    String exePath = null
    Path jreDir = buildContext.bundledRuntime.extract(BundledRuntime.getProductPrefix(buildContext), OsFamily.WINDOWS, JvmArchitecture.x64)

    Path vcRtDll = jreDir.resolve("jbr/bin/msvcp140.dll")
    if (!Files.exists(vcRtDll)) {
      buildContext.messages.error(
        "VS C++ Runtime DLL (${vcRtDll.fileName}) not found in ${vcRtDll.parent}.\n" +
        "If JBR uses a newer version, please correct the path in this code and update Windows Launcher build configuration.\n" +
        "If DLL was relocated to another place, please correct the path in this code.")
    }

    BuildHelper.copyFileToDir(vcRtDll, winDistPath.resolve("bin"))

    if (customizer.buildZipArchive) {
      List<Path> jreDirectoryPaths
      if (customizer.zipArchiveWithBundledJre) {
        jreDirectoryPaths = List.of(jreDir)
      }
      else {
        jreDirectoryPaths = List.of()
      }
      zipPathTask = createBuildWinZipTask(jreDirectoryPaths, ".win", winDistPath, customizer, buildContext).fork()
    }

    buildContext.executeStep("build Windows Exe Installer", BuildOptions.WINDOWS_EXE_INSTALLER_STEP, new Runnable() {
      @Override
      void run() {
        Path productJsonDir = buildContext.paths.tempDir.resolve("win.dist.product-info.json.exe")
        generateProductJson(productJsonDir, jreDir != null, buildContext)
        new ProductInfoValidator(buildContext).validateInDirectory(productJsonDir, "", List.of(winDistPath, jreDir), [])
        exePath = new WinExeInstallerBuilder(buildContext, customizer, jreDir).buildInstaller(winDistPath, productJsonDir, "", buildContext).toString()
      }
    })

    Path zipPath = zipPathTask == null ? null : zipPathTask.join()
    if (buildContext.options.isInDevelopmentMode || zipPathTask == null || exePath == null) {
      return
    }

    if (!SystemInfoRt.isLinux) {
      Span.current().addEvent("comparing .zip and .exe is not supported on ${SystemInfoRt.OS_NAME}")
      return
    }

    Span.current().addEvent("compare ${zipPath.fileName} vs. ${PathUtilRt.getFileName(exePath)}")

    Path tempZip = Files.createTempDirectory(buildContext.paths.tempDir, "zip-")
    Path tempExe = Files.createTempDirectory(buildContext.paths.tempDir, "exe-")
    try {
      BuildHelper.runProcess(buildContext, List.of("7z", "x", "-bd", exePath), tempExe)
      BuildHelper.runProcess(buildContext, List.of("unzip", "-q", zipPath.toString()), tempZip)
      //noinspection SpellCheckingInspection
      NioFiles.deleteRecursively(tempExe.resolve("\$PLUGINSDIR"))

      BuildHelper.runProcess(buildContext, List.of("diff", "-q", "-r", tempZip.toString(), tempExe.toString()))
      RepairUtilityBuilder.generateManifest(buildContext, tempExe, Path.of(exePath).fileName.toString())
    }
    finally {
      NioFiles.deleteRecursively(tempZip)
      NioFiles.deleteRecursively(tempExe)
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void generateScripts(@NotNull Path distBinDir) {
    String fullName = buildContext.applicationInfo.productName
    String baseName = buildContext.productProperties.baseFileName
    String scriptName = "${baseName}.bat"
    String vmOptionsFileName = "${baseName}64.exe"

    List<String> classPathJars = buildContext.bootClassPathJarNames
    String classPath = "SET \"CLASS_PATH=%IDE_HOME%\\lib\\${classPathJars.get(0)}\""
    for (int i = 1; i < classPathJars.size(); i++) {
      classPath += "\nSET \"CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\${classPathJars.get(i)}\""
    }

    List<String> additionalJvmArguments = buildContext.additionalJvmArguments
    if (!buildContext.xBootClassPathJarNames.isEmpty()) {
      additionalJvmArguments = new ArrayList<>(additionalJvmArguments)
      String bootCp = String.join(';', buildContext.xBootClassPathJarNames.collect { "%IDE_HOME%\\lib\\${it}" })
      additionalJvmArguments.add('"-Xbootclasspath/a:' + bootCp + '"')
    }

    buildContext.ant.copy(todir: distBinDir.toString()) {
      fileset(dir: "$buildContext.paths.communityHome/platform/build-scripts/resources/win/scripts")

      filterset(begintoken: "@@", endtoken: "@@") {
        filter(token: "product_full", value: fullName)
        filter(token: "product_uc", value: buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo))
        filter(token: "product_vendor", value: buildContext.applicationInfo.shortCompanyName)
        filter(token: "vm_options", value: vmOptionsFileName)
        filter(token: "system_selector", value: buildContext.systemSelector)
        filter(token: "ide_jvm_args", value: additionalJvmArguments.join(' '))
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
  private void generateVMOptions(Path distBinDir) {
    String fileName = "${buildContext.productProperties.baseFileName}64.exe.vmoptions"
    List<String> vmOptions = VmOptionsGenerator.computeVmOptions(buildContext.applicationInfo.isEAP, buildContext.productProperties)
    Files.writeString(distBinDir.resolve(fileName), String.join('\r\n', vmOptions) + '\r\n', StandardCharsets.US_ASCII)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildWinLauncher(Path winDistPath) {
    buildContext.messages.block("Build Windows executable") {
      def executableBaseName = "${buildContext.productProperties.baseFileName}64"
      Path launcherPropertiesPath = buildContext.paths.tempDir.resolve("launcher.properties")
      def upperCaseProductName = buildContext.applicationInfo.upperCaseProductName
      List<String> vmOptions = buildContext.additionalJvmArguments + ['-Dide.native.launcher=true']
      def productName = buildContext.applicationInfo.shortProductName
      String classPath = buildContext.bootClassPathJarNames.join(";")
      String bootClassPath = buildContext.xBootClassPathJarNames.join(";")
      def envVarBaseName = buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo)
      Path icoFilesDirectory = buildContext.paths.tempDir.resolve("win-launcher-ico")
      Path appInfoForLauncher = generateApplicationInfoForLauncher(patchedApplicationInfo, icoFilesDirectory)
      Files.writeString(launcherPropertiesPath, """
        IDS_JDK_ONLY=$buildContext.productProperties.toolsJarRequired
        IDS_JDK_ENV_VAR=${envVarBaseName}_JDK
        IDS_APP_TITLE=$productName Launcher
        IDS_VM_OPTIONS_PATH=%APPDATA%\\\\${buildContext.applicationInfo.shortCompanyName}\\\\${buildContext.systemSelector}
        IDS_VM_OPTION_ERRORFILE=-XX:ErrorFile=%USERPROFILE%\\\\java_error_in_${executableBaseName}_%p.log
        IDS_VM_OPTION_HEAPDUMPPATH=-XX:HeapDumpPath=%USERPROFILE%\\\\java_error_in_${executableBaseName}.hprof
        IDC_WINLAUNCHER=${upperCaseProductName}_LAUNCHER
        IDS_PROPS_ENV_VAR=${envVarBaseName}_PROPERTIES
        IDS_VM_OPTIONS_ENV_VAR=${envVarBaseName}_VM_OPTIONS
        IDS_ERROR_LAUNCHING_APP=Error launching ${productName}
        IDS_VM_OPTIONS=${vmOptions.join(' ')}
        IDS_CLASSPATH_LIBS=${classPath}
        IDS_BOOTCLASSPATH_LIBS=${bootClassPath}""".stripIndent().trim())

      def communityHome = "$buildContext.paths.communityHome"
      String inputPath = "${communityHome}/platform/build-scripts/resources/win/launcher/WinLauncher.exe"
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
  private Path generateApplicationInfoForLauncher(@NotNull String appInfo, @NotNull Path icoFilesDirectory) {
    Path patchedFile = buildContext.paths.tempDir.resolve("win-launcher-application-info.xml")
    if (icoFile == null) {
      Files.writeString(patchedFile, appInfo)
      return patchedFile
    }

    Files.createDirectories(icoFilesDirectory)
    Files.copy(icoFile, icoFilesDirectory.resolve(icoFile.fileName), StandardCopyOption.REPLACE_EXISTING)
    Element root = JDOMUtil.load(appInfo)
    // do not use getChild - maybe null due to namespace
    Element iconElement = (Element)root.getContent().stream()
      .filter({ it instanceof Element && ((Element)it).getName() == "icon" })
      .findFirst()
      .orElseThrow({ new RuntimeException("`icon` element not found in $appInfo:\n${appInfo}") })

    iconElement.setAttribute("ico", icoFile.fileName.toString())
    JDOMUtil.write(root, patchedFile)
    return patchedFile
  }

  private static ForkJoinTask<Path> createBuildWinZipTask(List<Path> jreDirectoryPaths,
                                                          String zipNameSuffix,
                                                          Path winDistPath,
                                                          WindowsDistributionCustomizer customizer,
                                                          BuildContext context) {
    String baseName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber)
    Path targetFile = context.paths.artifactDir.resolve("${baseName}${zipNameSuffix}.zip")
    return BuildHelper.getInstance(context).createTask(TracerManager.spanBuilder("build Windows ${zipNameSuffix}.zip distribution")
                                                         .setAttribute("targetFile", targetFile.toString()), new Supplier<Path>() {
      @Override
      Path get() {
        Path productJsonDir = context.paths.tempDir.resolve("win.dist.product-info.json.zip$zipNameSuffix")
        generateProductJson(productJsonDir, !jreDirectoryPaths.isEmpty(), context)

        String zipPrefix = customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)
        List<Path> dirs = [context.paths.distAllDir, winDistPath, productJsonDir] + jreDirectoryPaths
        BuildHelper.zipWithPrefix(context, targetFile, dirs, zipPrefix, true)
        ProductInfoValidator.checkInArchive(context, targetFile, zipPrefix)
        context.notifyArtifactWasBuilt(targetFile)
        return targetFile
      }
    })
  }

  private static void generateProductJson(@NotNull Path targetDir, boolean isJreIncluded, BuildContext context) {
    String launcherPath = "bin/${context.productProperties.baseFileName}64.exe"
    String vmOptionsPath = "bin/${context.productProperties.baseFileName}64.exe.vmoptions"
    String javaExecutablePath = isJreIncluded ? "jbr/bin/java.exe" : null
    new ProductInfoGenerator(context)
      .generateProductJson(targetDir, "bin", null, launcherPath, javaExecutablePath, vmOptionsPath, OsFamily.WINDOWS)
  }

  private static @NotNull FileFilter createFileFilter(String... extensions) {
    List<FileFilter> filters = extensions.collect { FileFilters.filesWithExtension(it) }
    return new FileFilter() {
      @Override
      boolean accept(File pathname) {
        return filters.any { it.accept(pathname) }
      }
    }
  }
}
