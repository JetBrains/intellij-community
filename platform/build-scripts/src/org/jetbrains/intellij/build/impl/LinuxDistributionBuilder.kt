// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.intellij.build.impl.qodana.generateQodanaLaunchData
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.NativeBinaryDownloader
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder.Companion.suffix
import org.jetbrains.intellij.build.impl.client.ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS
import org.jetbrains.intellij.build.impl.client.createJetBrainsClientContextForLaunchers
import org.jetbrains.intellij.build.impl.productInfo.*
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.*
import org.jetbrains.intellij.build.telemetry.useWithScope
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.minutes

private const val NO_RUNTIME_SUFFIX = "-no-jbr"
private const val EXECUTABLE_TEMPLATE_NAME = "executable-template.sh"

class LinuxDistributionBuilder(
  override val context: BuildContext,
  private val customizer: LinuxDistributionCustomizer,
  private val ideaProperties: CharSequence?
) : OsSpecificDistributionBuilder {
  private val iconPngPath: Path?

  init {
    val iconPng = (if (context.applicationInfo.isEAP) customizer.iconPngPathForEAP else null) ?: customizer.iconPngPath
    iconPngPath = if (iconPng.isNullOrEmpty()) null else Path.of(iconPng)
  }

  override val targetOs: OsFamily
    get() = OsFamily.LINUX

  override suspend fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture) {
    spanBuilder("copy files for os distribution").setAttribute("os", targetOs.osName).setAttribute("arch", arch.name).useWithScope {
      withContext(Dispatchers.IO) {
        val distBinDir = targetPath.resolve("bin")
        val sourceBinDir = context.paths.communityHomeDir.resolve("bin/linux")
        addNativeLauncher(distBinDir, targetPath, arch)
        copyFileToDir(NativeBinaryDownloader.getRestarter(context, OsFamily.LINUX, arch), distBinDir)
        copyFileToDir(sourceBinDir.resolve("${arch.dirName}/fsnotifier"), distBinDir)
        generateBuildTxt(context, targetPath)
        copyDistFiles(context, targetPath, OsFamily.LINUX, arch)

        //todo converting line separators to unix-style make sense only when building Linux distributions under Windows on a local machine;
        // for real installers we need to checkout all text files with 'lf' separators anyway
        Files.writeString(distBinDir.resolve(PROPERTIES_FILE_NAME), ideaProperties!!.lineSequence().joinToString("\n"))

        if (iconPngPath != null) {
          Files.copy(iconPngPath, distBinDir.resolve("${context.productProperties.baseFileName}.png"), StandardCopyOption.REPLACE_EXISTING)
        }
        writeVmOptions(distBinDir)
        generateScripts(distBinDir, arch)
        createJetBrainsClientContextForLaunchers(context)?.let { clientContext ->
          writeLinuxVmOptions(distBinDir, clientContext)
          generateLauncherScript(distBinDir, arch, nonCustomizableJvmArgs = ADDITIONAL_EMBEDDED_CLIENT_VM_OPTIONS, clientContext)
        }
        generateReadme(targetPath)
        generateVersionMarker(targetPath)
        customizer.copyAdditionalFiles(context, targetPath, arch)
      }
    }
  }

  override suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    copyFilesForOsDistribution(osAndArchSpecificDistPath, arch)
    setLastModifiedTime(osAndArchSpecificDistPath, context)
    val executableFileMatchers = generateExecutableFilesMatchers(true, arch).keys
    updateExecutablePermissions(osAndArchSpecificDistPath, executableFileMatchers)
    context.executeStep(spanBuilder("Build Linux artifacts").setAttribute("arch", arch.name), BuildOptions.LINUX_ARTIFACTS_STEP) {
      if (customizer.buildArtifactWithoutRuntime) {
        launch {
          context.executeStep(
            spanBuilder("Build Linux .tar.gz without bundled Runtime")
              .setAttribute("arch", arch.name)
              .setAttribute("runtimeDir", ""),
            BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP
          ) { span ->
            if (context.options.buildStepsToSkip.contains("${BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP}_${arch.name}")) {
              span.addEvent("skip")
            }
            else {
              buildTarGz(arch, runtimeDir = null, unixDistPath = osAndArchSpecificDistPath, suffix = NO_RUNTIME_SUFFIX + suffix(arch))
            }
          }
        }
      }

      val runtimeDir = context.bundledRuntime.extract(os = OsFamily.LINUX, arch = arch)
      updateExecutablePermissions(runtimeDir, executableFileMatchers)
      val tarGzPath: Path? = context.executeStep(
        spanBuilder("Build Linux .tar.gz with bundled Runtime")
          .setAttribute("arch", arch.name)
          .setAttribute("runtimeDir", runtimeDir.toString()),
        "linux_tar_gz_${arch.name}"
      ) { _ ->
        buildTarGz(arch, runtimeDir, osAndArchSpecificDistPath, suffix(arch))
      }
      launch {
        if (arch == JvmArchitecture.x64) {
          buildSnapPackage(runtimeDir, osAndArchSpecificDistPath, arch)
        }
        else {
          // TODO: Add snap for aarch64
          Span.current().addEvent("skip building Snap packages for non-x64 arch")
        }
      }

      if (tarGzPath != null ) {
        context.executeStep(spanBuilder("bundle repair utility"), BuildOptions.REPAIR_UTILITY_BUNDLE_STEP) {
          val tempTar = Files.createTempDirectory(context.paths.tempDir, "tar-")
          try {
            unTar(tarGzPath, tempTar)
            RepairUtilityBuilder.generateManifest(context, unpackedDistribution = tempTar.resolve(rootDirectoryName), OsFamily.LINUX, arch)
          }
          finally {
            NioFiles.deleteRecursively(tempTar)
          }
        }
      }
    }
  }

  override fun writeProductInfoFile(targetDir: Path, arch: JvmArchitecture) {
    generateProductJson(targetDir, arch)
  }

  override fun writeVmOptions(distBinDir: Path): Path =
    writeLinuxVmOptions(distBinDir, context)

  private fun generateReadme(unixDistPath: Path) {
    val fullName = context.applicationInfo.fullProductName
    val sourceFile = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/Install-Linux-tar.txt")
    val targetFile = unixDistPath.resolve("Install-Linux-tar.txt")
    substituteTemplatePlaceholders(
      sourceFile, targetFile, "@@", listOf(
        "product_full" to fullName,
        "product" to context.productProperties.baseFileName,
        "product_vendor" to context.applicationInfo.shortCompanyName,
        "system_selector" to context.systemSelector
      ), convertToUnixLineEndings = true
    )
  }

  override fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture): List<String> =
    customizer.generateExecutableFilesPatterns(context, includeRuntime, arch)

  private val rootDirectoryName: String
    get() = customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)

  private val launcherFileName: String
    get() = "${context.productProperties.baseFileName}${if (customizer.useXPlatLauncher) "" else ".sh"}"

  private suspend fun buildTarGz(arch: JvmArchitecture, runtimeDir: Path?, unixDistPath: Path, suffix: String): Path = withContext(Dispatchers.IO) {
    val tarRoot = rootDirectoryName
    val tarName = context.productProperties.getBaseArtifactName(context) + suffix + ".tar.gz"
    val tarPath = context.paths.artifactDir.resolve(tarName)
    val dirs = mutableListOf(context.paths.distAllDir, unixDistPath)

    if (runtimeDir != null) {
      dirs.add(runtimeDir)
      val javaExecutablePath = "jbr/bin/java"
      check(Files.exists(runtimeDir.resolve(javaExecutablePath))) { "$javaExecutablePath was not found under $runtimeDir" }
    }

    val productJsonDir = context.paths.tempDir.resolve("linux.dist.product-info.json${suffix}")
    generateProductJson(productJsonDir, arch, withRuntime = runtimeDir != null)
    dirs.add(productJsonDir)

    spanBuilder("build Linux tar.gz")
      .setAttribute("runtimeDir", runtimeDir?.toString() ?: "")
      .useWithScope {
        val executableFileMatchers = generateExecutableFilesMatchers(includeRuntime = runtimeDir != null, arch).keys
        tar(tarPath, tarRoot, dirs, executableFileMatchers, context.options.buildDateInSeconds)
        checkInArchive(tarPath, tarRoot, context)
        context.notifyArtifactBuilt(tarPath)
        checkExecutablePermissions(tarPath, rootDirectoryName, includeRuntime = runtimeDir != null, arch)
      }
    tarPath
  }

  private val snapVersion: String by lazy {
    val appInfo = context.applicationInfo
    val versionSuffix = appInfo.versionSuffix?.replace(' ', '-') ?: ""
    "${appInfo.majorVersion}.${appInfo.minorVersion}${if (versionSuffix.isEmpty()) "" else "-${versionSuffix}"}"
  }

  private val snapArtifactName: String? by lazy {
    "${customizer.snapName ?: return@lazy null}_${snapVersion}_amd64.snap"
  }

  private suspend fun buildSnapPackage(runtimeDir: Path, unixDistPath: Path, arch: JvmArchitecture) {
    val snapName = customizer.snapName
    val snapArtifactName = this.snapArtifactName
    if (snapName == null || snapArtifactName == null) {
      Span.current().addEvent("Linux .snap package build skipped because of missing snapName in ${customizer::class.java.simpleName}")
      return
    }
    if (!context.options.buildUnixSnaps) {
      Span.current().addEvent("Linux .snap package build is disabled")
      return
    }

    val snapDir = context.paths.buildOutputDir.resolve("dist.snap")
    spanBuilder("build Linux .snap package")
      .setAttribute("snapName", snapName)
      .use { span ->
        if (SystemInfoRt.isWindows) {
          span.addEvent(".snap cannot be built on Windows, skipped")
          return@use
        }
        check(Docker.isAvailable) { "Docker is required to build snaps" }
        check(iconPngPath != null) { context.messages.error("'iconPngPath' not set") }
        check(!customizer.snapDescription.isNullOrBlank()) { context.messages.error("'snapDescription' not set") }

        span.addEvent("prepare files")
        val appInfo = context.applicationInfo
        val productName = appInfo.productNameWithEdition
        substituteTemplatePlaceholders(
          inputFile = context.paths.communityHomeDir.resolve("platform/platform-resources/src/entry.desktop"),
          outputFile = snapDir.resolve("$snapName.desktop"),
          placeholder = "$",
          values = listOf(
            "NAME" to productName,
            "ICON" to "\${SNAP}/bin/${context.productProperties.baseFileName}.png",
            "SCRIPT" to snapName,
            "COMMENT" to (appInfo.motto ?: ""),
            "WM_CLASS" to getLinuxFrameClass(context)
          )
        )
        copyFile(iconPngPath, snapDir.resolve("$snapName.png"))
        val snapcraftTemplate = context.paths.communityHomeDir.resolve(
          "platform/build-scripts/resources/linux/snap/snapcraft-template.yaml"
        )
        val snapcraftConfig = snapDir.resolve("snapcraft.yaml")
        substituteTemplatePlaceholders(
          inputFile = snapcraftTemplate,
          outputFile = snapcraftConfig,
          placeholder = "$",
          values = listOf(
            "NAME" to snapName,
            "VERSION" to snapVersion,
            "SUMMARY" to productName,
            "DESCRIPTION" to (customizer.snapDescription ?: ""),
            "GRADE" to if (appInfo.isEAP) "devel" else "stable",
            "LAUNCHER" to "bin/${launcherFileName}"
          )
        )
        context.messages.info(
          """
          |# <${snapcraftConfig.name}>
          |${Files.readString(snapcraftConfig)}
          |# </${snapcraftConfig.name}>
        """.trimMargin()
        )
        val productJsonDir = context.paths.tempDir.resolve("linux.dist.snap.product-info.json")
        val jsonText = generateProductJson(productJsonDir, arch)
        validateProductJson(
          jsonText,
          relativePathToProductJson = "",
          installationDirectories = listOf(context.paths.distAllDir, unixDistPath, runtimeDir),
          installationArchives = listOf(),
          context
        )
        val resultDir = snapDir.resolve("result")
        Files.createDirectories(resultDir)

        span.addEvent("build package")
        runProcess(
          args = listOf(
            "docker", "run", "--rm",
            "--volume=$snapcraftConfig:/build/snapcraft.yaml:ro",
            "--volume=$snapDir/$snapName.desktop:/build/snap/gui/$snapName.desktop:ro",
            "--volume=$snapDir/$snapName.png:/build/prime/meta/gui/icon.png:ro",
            "--volume=$snapDir/result:/build/result",
            "--volume=${context.paths.distAllDir}:/build/dist.all:ro",
            "--volume=$productJsonDir:/build/dist.product-json:ro",
            "--volume=$unixDistPath:/build/dist.unix:ro",
            "--volume=$runtimeDir:/build/jre:ro",
            "--workdir=/build",
            context.options.snapDockerImage,
            "snapcraft",
            "snap", "-o", "result/$snapArtifactName"
          ),
          workingDir = snapDir,
          timeout = context.options.snapDockerBuildTimeoutMin.minutes,
        )
        val snapArtifactPath = moveFileToDir(resultDir.resolve(snapArtifactName), context.paths.artifactDir)
        context.notifyArtifactBuilt(snapArtifactPath)
        checkExecutablePermissions(unSquashSnap(snapArtifactPath), root = "", includeRuntime = true, arch)
      }
  }

  private suspend fun unSquashSnap(snap: Path): Path {
    val unSquashed = context.paths.tempDir.resolve("unSquashed-${snap.nameWithoutExtension}")
    NioFiles.deleteRecursively(unSquashed)
    Files.createDirectories(unSquashed)
    runProcess(listOf("unsquashfs", "$snap"), workingDir = unSquashed, inheritOut = true)
    return unSquashed.resolve("squashfs-root")
  }

  override fun distributionFilesBuilt(arch: JvmArchitecture): List<Path> {
    val archSuffix = suffix(arch)
    return sequenceOf("${archSuffix}.tar.gz", "${NO_RUNTIME_SUFFIX}${archSuffix}.tar.gz")
      .map { suffix -> context.productProperties.getBaseArtifactName(context) + suffix }
      .plus(snapArtifactName)
      .filterNotNull()
      .map(context.paths.artifactDir::resolve)
      .filter { it.exists() }
      .toList()
  }

  override fun isRuntimeBundled(file: Path): Boolean = !file.name.contains(NO_RUNTIME_SUFFIX)

  private fun generateProductJson(targetDir: Path, arch: JvmArchitecture, withRuntime: Boolean = true): String {
    val jetbrainsClientCustomLaunchData = generateJetBrainsClientLaunchData(context, arch, OsFamily.LINUX) {
      "bin/${it.productProperties.baseFileName}64.vmoptions"
    }
    val qodanaCustomLaunchData = generateQodanaLaunchData(context, arch, OsFamily.LINUX)

    val json = generateProductInfoJson(
      relativePathToBin = "bin",
      builtinModules = context.builtinModule,
      launch = listOf(
        ProductInfoLaunchData(
          OsFamily.LINUX.osName,
          arch.dirName,
          launcherPath = "bin/${launcherFileName}",
          javaExecutablePath = if (withRuntime) "jbr/bin/java" else null,
          vmOptionsFilePath = "bin/${context.productProperties.baseFileName}64.vmoptions",
          startupWmClass = getLinuxFrameClass(context),
          bootClassPathJarNames = context.bootClassPathJarNames,
          additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.LINUX, arch),
          mainClass = context.ideMainClassName,
          customCommands = listOfNotNull(jetbrainsClientCustomLaunchData, qodanaCustomLaunchData)
        )
      ),
      context
    )
    writeProductInfoJson(targetDir.resolve(PRODUCT_INFO_FILE_NAME), json, context)
    return json
  }

  private fun generateVersionMarker(unixDistPath: Path) {
    val targetDir = unixDistPath.resolve("lib")
    Files.createDirectories(targetDir)
    Files.writeString(targetDir.resolve("build-marker-" + context.fullBuildNumber), context.fullBuildNumber)
  }

  private fun generateScripts(distBinDir: Path, arch: JvmArchitecture) {
    Files.createDirectories(distBinDir)

    val sourceScriptDir = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/scripts")
    Files.newDirectoryStream(sourceScriptDir).use {
      for (file in it) {
        val fileName = file.fileName.toString()
        if (fileName != EXECUTABLE_TEMPLATE_NAME) {
          copyScript(file, distBinDir.resolve(fileName), additionalTemplateValues = emptyList(), context)
        }
      }
    }

    copyInspectScript(context, distBinDir)

    generateLauncherScript(distBinDir, arch, nonCustomizableJvmArgs = emptyList(), context)
  }

  private suspend fun addNativeLauncher(distBinDir: Path, targetPath: Path, arch: JvmArchitecture) {
    if (customizer.useXPlatLauncher) {
      val (execPath, licensePath) = NativeBinaryDownloader.getLauncher(context, OsFamily.LINUX, arch)
      copyFile(execPath, distBinDir.resolve(context.productProperties.baseFileName))
      copyFile(licensePath, targetPath.resolve("license/launcher-third-party-libraries.html"))
    }
  }

  private fun generateLauncherScript(distBinDir: Path, arch: JvmArchitecture, nonCustomizableJvmArgs: List<String>, context: BuildContext) {
    val vmOptionsPath = distBinDir.resolve("${context.productProperties.baseFileName}64.vmoptions")

    val defaultXmxParameter = try {
      Files.readAllLines(vmOptionsPath).firstOrNull { it.startsWith("-Xmx") }
      ?: throw IllegalStateException("-Xmx was not found in '$vmOptionsPath'")
    }
    catch (e: NoSuchFileException) {
      throw IllegalStateException("File '$vmOptionsPath' should be already generated at this point", e)
    }

    val classPathJars = context.bootClassPathJarNames
    var classPath = "CLASS_PATH=\"\$IDE_HOME/lib/${classPathJars[0]}\""
    for (i in 1 until classPathJars.size) {
      classPath += "\nCLASS_PATH=\"\$CLASS_PATH:\$IDE_HOME/lib/${classPathJars[i]}\""
    }

    val additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.LINUX, arch, isScript = true).toMutableList()
    additionalJvmArguments.addAll(nonCustomizableJvmArgs)
    if (!context.xBootClassPathJarNames.isEmpty()) {
      val bootCp = context.xBootClassPathJarNames.joinToString(separator = ":") { "\$IDE_HOME/lib/${it}" }
      additionalJvmArguments.add("\"-Xbootclasspath/a:$bootCp\"")
    }
    val additionalJvmArgs = additionalJvmArguments.joinToString(separator = " ")
    val additionalTemplateValues = listOf(
      Pair("vm_options", context.productProperties.baseFileName),
      Pair("system_selector", context.systemSelector),
      Pair("ide_jvm_args", additionalJvmArgs),
      Pair("ide_default_xmx", defaultXmxParameter.trim()),
      Pair("class_path", classPath),
      Pair("main_class_name", context.ideMainClassName),
    )

    val template = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/scripts/${EXECUTABLE_TEMPLATE_NAME}")
    copyScript(template, distBinDir.resolve("${context.productProperties.baseFileName}.sh"), additionalTemplateValues, context)
  }

  private fun copyScript(sourceFile: Path, targetFile: Path, additionalTemplateValues: List<Pair<String, String>>, context: BuildContext) {
    // Until CR (\r) will be removed from the repository checkout, we need to filter it out from Unix-style scripts
    // https://youtrack.jetbrains.com/issue/IJI-526/Force-git-to-use-LF-line-endings-in-working-copy-of-via-gitattri
    substituteTemplatePlaceholders(
      inputFile = sourceFile,
      outputFile = targetFile,
      placeholder = "__",
      values = listOf(
        Pair("product_full", context.applicationInfo.fullProductName),
        Pair("product_uc", context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo)),
        Pair("product_vendor", context.applicationInfo.shortCompanyName),
        Pair("product_code", context.applicationInfo.productCode),
        Pair("script_name", "${context.productProperties.baseFileName}.sh"),
      ) + additionalTemplateValues,
      mustUseAllPlaceholders = false,
      convertToUnixLineEndings = true,
    )
  }

  private fun writeLinuxVmOptions(distBinDir: Path, context: BuildContext): Path {
    val vmOptionsPath = distBinDir.resolve("${context.productProperties.baseFileName}64.vmoptions")

    @Suppress("SpellCheckingInspection")
    val vmOptions = VmOptionsGenerator.computeVmOptions(context) + listOf("-Dsun.tools.attach.tmp.only=true", "-Dawt.lock.fair=true")
    writeVmOptions(vmOptionsPath, vmOptions, separator = "\n")

    return vmOptionsPath
  }
}
