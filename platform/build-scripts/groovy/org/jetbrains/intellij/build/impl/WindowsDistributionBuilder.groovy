// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import io.opentelemetry.api.trace.Span
import kotlin.Pair
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.FileKt
import org.jetbrains.intellij.build.io.ProcessKt
import org.jetbrains.intellij.build.tasks.TraceKt
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ForkJoinTask

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

    String icoPath = (buildContext.applicationInfo.isEAP() ? customizer.icoPathForEAP : null) ?: customizer.icoPath
    icoFile = icoPath == null ? null : Paths.get(icoPath)
  }

  @Override
  OsFamily getTargetOs() {
    return OsFamily.WINDOWS
  }

  @Override
  void copyFilesForOsDistribution(@NotNull Path winDistPath, JvmArchitecture arch = null) {
    Path distBinDir = winDistPath.resolve("bin")
    Files.createDirectories(distBinDir)

    buildContext.messages.progress("build distributions for Windows")

    FileSet binWin = new FileSet(buildContext.paths.communityHomeDir.resolve("bin/win")).includeAll()
    if (!buildContext.includeBreakGenLibraries()) {
      binWin.exclude("breakgen*")
    }
    binWin.copyToDir(distBinDir)

    def pty4jNativeDir = DistUtilKt.unpackPty4jNative(buildContext, winDistPath, "win")
    DistUtilKt.generateBuildTxt(buildContext, winDistPath)
    DistUtilKt.copyDistFiles(buildContext, winDistPath)

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
              buildContext.signFiles(List.of(file.toPath()), BuildOptions.WIN_SIGN_OPTIONS)
            }
          }
          return true
        }
      })
    }
    customizer.getBinariesToSign(buildContext).each {
      def path = winDistPath.resolve(it)
      buildContext.executeStep(TracerManager.spanBuilder("sign").setAttribute("file", path.toString()), BuildOptions.WIN_SIGN_STEP) {
        buildContext.signFiles(List.of(path), BuildOptions.WIN_SIGN_OPTIONS)
      }
    }
  }

  @Override
  void buildArtifacts(@NotNull Path winAndArchSpecificDistPath, @NotNull JvmArchitecture arch) {
    copyFilesForOsDistribution(winAndArchSpecificDistPath, arch)

    ForkJoinTask<Path> zipPathTask = null
    String exePath = null
    Path jreDir = buildContext.bundledRuntime.extract(BundledRuntimeImpl.getProductPrefix(buildContext), OsFamily.WINDOWS, arch)

    Path vcRtDll = jreDir.resolve("jbr/bin/msvcp140.dll")
    if (!Files.exists(vcRtDll)) {
      buildContext.messages.error(
        "VS C++ Runtime DLL (${vcRtDll.fileName}) not found in ${vcRtDll.parent}.\n" +
        "If JBR uses a newer version, please correct the path in this code and update Windows Launcher build configuration.\n" +
        "If DLL was relocated to another place, please correct the path in this code.")
    }

    FileKt.copyFileToDir(vcRtDll, winAndArchSpecificDistPath.resolve("bin"))

    if (customizer.buildZipArchive) {
      List<Path> jreDirectoryPaths
      if (customizer.zipArchiveWithBundledJre) {
        jreDirectoryPaths = List.of(jreDir)
      }
      else {
        jreDirectoryPaths = List.of()
      }
      zipPathTask = createBuildWinZipTask(jreDirectoryPaths, ".win", winAndArchSpecificDistPath, customizer, buildContext).fork()
    }

    buildContext.executeStep("build Windows Exe Installer", BuildOptions.WINDOWS_EXE_INSTALLER_STEP, new Runnable() {
      @Override
      void run() {
        Path productJsonDir = buildContext.paths.tempDir.resolve("win.dist.product-info.json.exe")
        generateProductJson(productJsonDir, jreDir != null, buildContext)
        new ProductInfoValidator(buildContext).validateInDirectory(productJsonDir, "", List.of(winAndArchSpecificDistPath, jreDir), [])
        exePath = new WinExeInstallerBuilder(buildContext, customizer, jreDir).buildInstaller(winAndArchSpecificDistPath, productJsonDir, "", buildContext).toString()
      }
    })

    Path zipPath = zipPathTask == null ? null : zipPathTask.join()
    if (buildContext.options.isInDevelopmentMode() || zipPathTask == null || exePath == null) {
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
      ProcessKt.runProcess(List.of("7z", "x", "-bd", exePath), tempExe, buildContext.messages)
      ProcessKt.runProcess(List.of("unzip", "-q", zipPath.toString()), tempZip, buildContext.messages)
      //noinspection SpellCheckingInspection
      NioFiles.deleteRecursively(tempExe.resolve("\$PLUGINSDIR"))

      ProcessKt.runProcess(List.of("diff", "-q", "-r", tempZip.toString(), tempExe.toString()), null, buildContext.messages)
      RepairUtilityBuilder.generateManifest(buildContext, tempExe, Path.of(exePath).fileName.toString())
    }
    finally {
      NioFiles.deleteRecursively(tempZip)
      NioFiles.deleteRecursively(tempExe)
    }
  }

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
    if (!buildContext.XBootClassPathJarNames.isEmpty()) {
      additionalJvmArguments = new ArrayList<>(additionalJvmArguments)
      String bootCp = String.join(';', buildContext.XBootClassPathJarNames.collect { "%IDE_HOME%\\lib\\${it}" })
      additionalJvmArguments.add('"-Xbootclasspath/a:' + bootCp + '"')
    }

    Path winScripts = buildContext.paths.communityHomeDir.resolve("platform/build-scripts/resources/win/scripts")
    String[] actualScriptNames = winScripts.toFile().list().toSorted()
    String[] expectedScriptNames = ["executable-template.bat", "format.bat", "inspect.bat", "ltedit.bat"]
    if (actualScriptNames != expectedScriptNames) {
      throw new IllegalStateException("Expected script names '${expectedScriptNames.join(" ")}', but got '${actualScriptNames.join(" ")}' in $winScripts. Please review ${WindowsDistributionBuilder.class.name} and update accordingly")
    }

    FileKt.substituteTemplatePlaceholders(
      winScripts.resolve("executable-template.bat"),
      distBinDir.resolve(scriptName),
      "@@",
      [
        new Pair<String, String>("product_full", fullName),
        new Pair<String, String>("product_uc", buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo)),
        new Pair<String, String>("product_vendor", buildContext.applicationInfo.shortCompanyName),
        new Pair<String, String>("vm_options", vmOptionsFileName),
        new Pair<String, String>("system_selector", buildContext.systemSelector),
        new Pair<String, String>("ide_jvm_args", additionalJvmArguments.join(' ')),
        new Pair<String, String>("class_path", classPath),
        new Pair<String, String>("base_name", baseName),
      ]
    )

    String inspectScript = buildContext.productProperties.inspectCommandName
    for (String fileName : ["format.bat", "inspect.bat", "ltedit.bat"]) {
      Path sourceFile = winScripts.resolve(fileName)
      Path targetFile = distBinDir.resolve(fileName)

      FileKt.substituteTemplatePlaceholders(
        sourceFile,
        targetFile,
        "@@",
        [
          new Pair<String, String>("product_full", fullName),
          new Pair<String, String>("script_name", scriptName),
        ]
      )
    }

    if (inspectScript != "inspect") {
      Path targetPath = distBinDir.resolve("${inspectScript}.bat")
      Files.move(distBinDir.resolve("inspect.bat"), targetPath)
      buildContext.patchInspectScript(targetPath)
    }

    new FileSet(distBinDir)
      .include("*.bat")
      .enumerate()
      .each { file ->
        FileKt.transformFile(file, { target ->
          Files.writeString(target, toDosLineEndings(Files.readString(file)))
        })
      }
  }

  private static String toDosLineEndings(String x) {
    return x.replace("\r", "").replace("\n", "\r\n")
  }

  private void generateVMOptions(Path distBinDir) {
    ProductProperties productProperties = buildContext.productProperties
    String fileName = "${productProperties.baseFileName}64.exe.vmoptions"
    boolean isEAP = buildContext.applicationInfo.isEAP()
    List<String> vmOptions = VmOptionsGenerator.computeVmOptions(isEAP, productProperties)
    Files.writeString(distBinDir.resolve(fileName), String.join('\r\n', vmOptions) + '\r\n', StandardCharsets.US_ASCII)
  }

  private void buildWinLauncher(Path winDistPath) {
    buildContext.messages.block("Build Windows executable") {
      def executableBaseName = "${buildContext.productProperties.baseFileName}64"
      Path launcherPropertiesPath = buildContext.paths.tempDir.resolve("launcher.properties")
      def upperCaseProductName = buildContext.applicationInfo.upperCaseProductName
      List<String> vmOptions = buildContext.additionalJvmArguments + ['-Dide.native.launcher=true']
      def productName = buildContext.applicationInfo.shortProductName
      String classPath = String.join(";", buildContext.bootClassPathJarNames)
      String bootClassPath = String.join(";", buildContext.XBootClassPathJarNames)
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
      List<JpsModule> resourceModules = List.of(
        buildContext.findApplicationInfoModule(),
        buildContext.findModule("intellij.platform.icons"),
      )

      List<String> classpath = new ArrayList<>()
      classpath.add("$communityHome/build/lib/launcher-generator.jar".toString())
      ["Guava", "commons-imaging"].each {
        buildContext.project.libraryCollection.findLibrary(it).getFiles(JpsOrderRootType.COMPILED).each {
          classpath.add(it.absolutePath)
        }
      }
      resourceModules.collectMany { it.sourceRoots }.each { JpsModuleSourceRoot root ->
        classpath.add(root.file.absolutePath)
      }
      for (String p in buildContext.productProperties.brandingResourcePaths) {
        classpath.add(p.toString())
      }
      classpath.add(icoFilesDirectory.toString())
      classpath.add(buildContext.getModuleOutputDir(buildContext.findRequiredModule("intellij.platform.util.jdom")).toString())

      BuildHelperKt.runJava(
        buildContext,
        "com.pme.launcher.LauncherGeneratorMain",
        [
          inputPath,
          appInfoForLauncher.toString(),
          "$communityHome/native/WinLauncher/resource.h".toString(),
          launcherPropertiesPath.toString(),
          outputPath.toString(),
        ],
        ["-Djava.awt.headless=true"],
        classpath
      )
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
    return TraceKt.createTask(TracerManager.spanBuilder("build Windows ${zipNameSuffix}.zip distribution")
                                .setAttribute("targetFile", targetFile.toString())) {
      Path productJsonDir = context.paths.tempDir.resolve("win.dist.product-info.json.zip$zipNameSuffix")
      generateProductJson(productJsonDir, !jreDirectoryPaths.isEmpty(), context)

      String zipPrefix = customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)
      List<Path> dirs = [context.paths.distAllDir, winDistPath, productJsonDir] + jreDirectoryPaths
      BuildHelperKt.zipWithPrefix(context, targetFile, dirs, zipPrefix, true)
      ProductInfoValidator.checkInArchive(context, targetFile, zipPrefix)
      context.notifyArtifactWasBuilt(targetFile)
      return targetFile
    }
  }

  private static void generateProductJson(@NotNull Path targetDir, boolean isJreIncluded, BuildContext context) {
    String launcherPath = "bin/${context.productProperties.baseFileName}64.exe"
    String vmOptionsPath = "bin/${context.productProperties.baseFileName}64.exe.vmoptions"
    String javaExecutablePath = isJreIncluded ? "jbr/bin/java.exe" : null

    Path file = targetDir.resolve(ProductInfoGenerator.FILE_NAME)
    Files.createDirectories(targetDir)
    Files.write(file, new ProductInfoGenerator(context).generateMultiPlatformProductJson(
      "bin",
      context.getBuiltinModule(),
      [
        new ProductInfoLaunchData(
          os: OsFamily.WINDOWS.osName,
          launcherPath: launcherPath,
          javaExecutablePath: javaExecutablePath,
          vmOptionsFilePath: vmOptionsPath,
          startupWmClass: null,
          )
      ]
    ))
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
