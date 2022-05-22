// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.TraceKt
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.PathUtilRt
import groovy.transform.CompileStatic
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlin.Pair
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGeneratorKt
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidatorKt
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.FileKt
import org.jetbrains.intellij.build.io.ProcessKt
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ForkJoinTask
import java.util.function.BiPredicate

import static org.jetbrains.intellij.build.TraceManager.spanBuilder

@CompileStatic
final class WindowsDistributionBuilder implements OsSpecificDistributionBuilder {
  private final WindowsDistributionCustomizer customizer
  private final Path ideaProperties
  private final String patchedApplicationInfo
  private final Path icoFile
  private final BuildContext context

  WindowsDistributionBuilder(BuildContext context, WindowsDistributionCustomizer customizer, Path ideaProperties, String patchedApplicationInfo) {
    this.context = context
    this.patchedApplicationInfo = patchedApplicationInfo
    this.customizer = customizer
    this.ideaProperties = ideaProperties

    String icoPath = (context.applicationInfo.isEAP() ? customizer.icoPathForEAP : null) ?: customizer.icoPath
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

    context.messages.progress("build distributions for Windows")

    FileSet binWin = new FileSet(context.paths.communityHomeDir.resolve("bin/win")).includeAll()
    if (!context.includeBreakGenLibraries()) {
      binWin.exclude("breakgen*")
    }
    binWin.copyToDir(distBinDir)

    def pty4jNativeDir = DistUtilKt.unpackPty4jNative(context, winDistPath, "win")
    DistUtilKt.generateBuildTxt(context, winDistPath)
    DistUtilKt.copyDistFiles(context, winDistPath)

    Files.writeString(distBinDir.resolve(ideaProperties.fileName), StringUtilRt.convertLineSeparators(Files.readString(ideaProperties), "\r\n"))

    if (icoFile != null) {
      Files.copy(icoFile, distBinDir.resolve("${context.productProperties.baseFileName}.ico"), StandardCopyOption.REPLACE_EXISTING)
    }
    if (customizer.includeBatchLaunchers) {
      generateScripts(distBinDir)
    }
    generateVMOptions(distBinDir)
    buildWinLauncher(winDistPath)
    customizer.copyAdditionalFiles(context, winDistPath.toString())
    List<Path> nativeFiles = new ArrayList<>()
    for (Path nativeRoot : List.of(distBinDir, pty4jNativeDir)) {
      Files.find(nativeRoot, Integer.MAX_VALUE, new BiPredicate<Path, BasicFileAttributes>() {
        @Override
        boolean test(Path file, BasicFileAttributes attributes) {
          if (attributes.isRegularFile()) {
            def path = file.toString()
            if (path.endsWith(".exe") || path.endsWith(".dll")) {
              nativeFiles.add(file)
            }
          }
          return false
        }
      })
    }

    for (it in customizer.getBinariesToSign(context)) {
      nativeFiles.add(winDistPath.resolve(it))
    }

    if (!nativeFiles.isEmpty()) {
      context.executeStep(spanBuilder("sign").setAttribute(AttributeKey.stringArrayKey("files"), nativeFiles.collect { it.toString() }), BuildOptions.WIN_SIGN_STEP, new Runnable() {
        @Override
        void run() {
          context.signFiles(nativeFiles, BuildOptions.WIN_SIGN_OPTIONS)
        }
      })
    }
  }

  @Override
  void buildArtifacts(@NotNull Path winAndArchSpecificDistPath, @NotNull JvmArchitecture arch) {
    copyFilesForOsDistribution(winAndArchSpecificDistPath, arch)

    ForkJoinTask<Path> zipPathTask = null
    String exePath = null
    Path jreDir = context.bundledRuntime.extract(BundledRuntimeImpl.getProductPrefix(context), OsFamily.WINDOWS, arch)

    Path vcRtDll = jreDir.resolve("jbr/bin/msvcp140.dll")
    if (!Files.exists(vcRtDll)) {
      context.messages.error(
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
      zipPathTask = createBuildWinZipTask(jreDirectoryPaths, ".win", winAndArchSpecificDistPath, customizer, context).fork()
    }

    context.executeStep("build Windows Exe Installer", BuildOptions.WINDOWS_EXE_INSTALLER_STEP, new Runnable() {
      @Override
      void run() {
        Path productJsonDir = context.paths.tempDir.resolve("win.dist.product-info.json.exe")
        ProductInfoValidatorKt.validateProductJson(generateProductJson(productJsonDir, jreDir != null, context),
                                                   "",
                                                   List.of(context.paths.distAllDir, winAndArchSpecificDistPath, jreDir),
                                                   List.of(),
                                                   context)

        exePath = new WinExeInstallerBuilder(context, customizer, jreDir).buildInstaller(winAndArchSpecificDistPath, productJsonDir, "", context).toString()
      }
    })

    Path zipPath = zipPathTask == null ? null : zipPathTask.join()
    if (context.options.isInDevelopmentMode() || zipPathTask == null || exePath == null) {
      return
    }

    if (!SystemInfoRt.isLinux) {
      Span.current().addEvent("comparing .zip and .exe is not supported on ${SystemInfoRt.OS_NAME}")
      return
    }

    Span.current().addEvent("compare ${zipPath.fileName} vs. ${PathUtilRt.getFileName(exePath)}")

    Path tempZip = Files.createTempDirectory(context.paths.tempDir, "zip-")
    Path tempExe = Files.createTempDirectory(context.paths.tempDir, "exe-")
    try {
      ProcessKt.runProcess(List.of("7z", "x", "-bd", exePath), tempExe, context.messages)
      ProcessKt.runProcess(List.of("unzip", "-q", zipPath.toString()), tempZip, context.messages)
      //noinspection SpellCheckingInspection
      NioFiles.deleteRecursively(tempExe.resolve("\$PLUGINSDIR"))

      ProcessKt.runProcess(List.of("diff", "-q", "-r", tempZip.toString(), tempExe.toString()), null, context.messages)
      RepairUtilityBuilder.generateManifest(context, tempExe, Path.of(exePath).fileName.toString())
    }
    finally {
      NioFiles.deleteRecursively(tempZip)
      NioFiles.deleteRecursively(tempExe)
    }
  }

  private void generateScripts(@NotNull Path distBinDir) {
    String fullName = context.applicationInfo.productName
    String baseName = context.productProperties.baseFileName
    String scriptName = "${baseName}.bat"
    String vmOptionsFileName = "${baseName}64.exe"

    List<String> classPathJars = context.bootClassPathJarNames
    String classPath = "SET \"CLASS_PATH=%IDE_HOME%\\lib\\${classPathJars.get(0)}\""
    for (int i = 1; i < classPathJars.size(); i++) {
      classPath += "\nSET \"CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\${classPathJars.get(i)}\""
    }

    List<String> additionalJvmArguments = context.additionalJvmArguments
    if (!context.XBootClassPathJarNames.isEmpty()) {
      additionalJvmArguments = new ArrayList<>(additionalJvmArguments)
      String bootCp = String.join(';', context.XBootClassPathJarNames.collect { "%IDE_HOME%\\lib\\${it}" })
      additionalJvmArguments.add('"-Xbootclasspath/a:' + bootCp + '"')
    }

    Path winScripts = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/win/scripts")
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
        new Pair<String, String>("product_uc", context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo)),
        new Pair<String, String>("product_vendor", context.applicationInfo.shortCompanyName),
        new Pair<String, String>("vm_options", vmOptionsFileName),
        new Pair<String, String>("system_selector", context.systemSelector),
        new Pair<String, String>("ide_jvm_args", additionalJvmArguments.join(' ')),
        new Pair<String, String>("class_path", classPath),
        new Pair<String, String>("base_name", baseName),
      ]
    )

    String inspectScript = context.productProperties.inspectCommandName
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
      context.patchInspectScript(targetPath)
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
    ProductProperties productProperties = context.productProperties
    String fileName = "${productProperties.baseFileName}64.exe.vmoptions"
    boolean isEAP = context.applicationInfo.isEAP()
    List<String> vmOptions = VmOptionsGenerator.computeVmOptions(isEAP, productProperties)
    Files.writeString(distBinDir.resolve(fileName), String.join('\r\n', vmOptions) + '\r\n', StandardCharsets.US_ASCII)
  }

  private void buildWinLauncher(Path winDistPath) {
    context.messages.block("Build Windows executable") {
      def executableBaseName = "${context.productProperties.baseFileName}64"
      Path launcherPropertiesPath = context.paths.tempDir.resolve("launcher.properties")
      def upperCaseProductName = context.applicationInfo.upperCaseProductName
      List<String> vmOptions = context.additionalJvmArguments + ['-Dide.native.launcher=true']
      def productName = context.applicationInfo.shortProductName
      String classPath = String.join(";", context.bootClassPathJarNames)
      String bootClassPath = String.join(";", context.XBootClassPathJarNames)
      def envVarBaseName = context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo)
      Path icoFilesDirectory = context.paths.tempDir.resolve("win-launcher-ico")
      Path appInfoForLauncher = generateApplicationInfoForLauncher(patchedApplicationInfo, icoFilesDirectory)
      Files.writeString(launcherPropertiesPath, """
        IDS_JDK_ONLY=$context.productProperties.toolsJarRequired
        IDS_JDK_ENV_VAR=${envVarBaseName}_JDK
        IDS_APP_TITLE=$productName Launcher
        IDS_VM_OPTIONS_PATH=%APPDATA%\\\\${context.applicationInfo.shortCompanyName}\\\\${context.systemSelector}
        IDS_VM_OPTION_ERRORFILE=-XX:ErrorFile=%USERPROFILE%\\\\java_error_in_${executableBaseName}_%p.log
        IDS_VM_OPTION_HEAPDUMPPATH=-XX:HeapDumpPath=%USERPROFILE%\\\\java_error_in_${executableBaseName}.hprof
        IDC_WINLAUNCHER=${upperCaseProductName}_LAUNCHER
        IDS_PROPS_ENV_VAR=${envVarBaseName}_PROPERTIES
        IDS_VM_OPTIONS_ENV_VAR=${envVarBaseName}_VM_OPTIONS
        IDS_ERROR_LAUNCHING_APP=Error launching ${productName}
        IDS_VM_OPTIONS=${vmOptions.join(' ')}
        IDS_CLASSPATH_LIBS=${classPath}
        IDS_BOOTCLASSPATH_LIBS=${bootClassPath}""".stripIndent().trim())

      def communityHome = "$context.paths.communityHome"
      String inputPath = "${communityHome}/platform/build-scripts/resources/win/launcher/WinLauncher.exe"
      Path outputPath = winDistPath.resolve("bin/${executableBaseName}.exe")
      List<JpsModule> resourceModules = List.of(
        context.findApplicationInfoModule(),
        context.findModule("intellij.platform.icons"),
        )

      List<String> classpath = new ArrayList<>()
      classpath.add("$communityHome/build/lib/launcher-generator.jar".toString())
      ["Guava", "commons-imaging"].each {
        context.project.libraryCollection.findLibrary(it).getFiles(JpsOrderRootType.COMPILED).each {
          classpath.add(it.absolutePath)
        }
      }
      resourceModules.collectMany { it.sourceRoots }.each { JpsModuleSourceRoot root ->
        classpath.add(root.file.absolutePath)
      }
      for (String p in context.productProperties.brandingResourcePaths) {
        classpath.add(p.toString())
      }
      classpath.add(icoFilesDirectory.toString())
      classpath.add(context.getModuleOutputDir(context.findRequiredModule("intellij.platform.util.jdom")).toString())

      BuildHelperKt.runJava(
        context,
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
    Path patchedFile = context.paths.tempDir.resolve("win-launcher-application-info.xml")
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
    return TraceKt.createTask(spanBuilder("build Windows ${zipNameSuffix}.zip distribution")
                                .setAttribute("targetFile", targetFile.toString())) {
      Path productJsonDir = context.paths.tempDir.resolve("win.dist.product-info.json.zip$zipNameSuffix")
      generateProductJson(productJsonDir, !jreDirectoryPaths.isEmpty(), context)

      String zipPrefix = customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)
      List<Path> dirs = List.of(context.paths.distAllDir, winDistPath, productJsonDir) + jreDirectoryPaths
      BuildHelperKt.zipWithPrefix(context, targetFile, dirs, zipPrefix, true)
      ProductInfoValidatorKt.checkInArchive(context, targetFile, zipPrefix)
      context.notifyArtifactWasBuilt(targetFile)
      return targetFile
    }
  }

  private static String generateProductJson(@NotNull Path targetDir, boolean isJreIncluded, BuildContext context) {
    String launcherPath = "bin/${context.productProperties.baseFileName}64.exe"
    String vmOptionsPath = "bin/${context.productProperties.baseFileName}64.exe.vmoptions"
    String javaExecutablePath = isJreIncluded ? "jbr/bin/java.exe" : null

    Path file = targetDir.resolve(ProductInfoGeneratorKt.PRODUCT_INFO_FILE_NAME)
    Files.createDirectories(targetDir)

    def json = ProductInfoGeneratorKt.generateMultiPlatformProductJson(
      "bin",
      context.getBuiltinModule(),
      List.of(
        new ProductInfoLaunchData(
          OsFamily.WINDOWS.osName,
          launcherPath,
          javaExecutablePath,
          vmOptionsPath,
          null,
          )
      ), context)
    Files.writeString(file, json)
    return json
  }

  @Override
  List<String> generateExecutableFilesPatterns(boolean includeJre) {
    return List.of()
  }

  @Override
  List<String> getArtifactNames(@NotNull BuildContext context) {
    return List.of()
  }
}
