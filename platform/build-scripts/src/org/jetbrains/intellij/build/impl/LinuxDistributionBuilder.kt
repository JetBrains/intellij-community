// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.diagnostic.telemetry.useWithScope2
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.BundledRuntimeImpl.Companion.getProductPrefix
import org.jetbrains.intellij.build.impl.productInfo.*
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.*
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

class LinuxDistributionBuilder(override val context: BuildContext,
                               private val customizer: LinuxDistributionCustomizer,
                               private val ideaProperties: Path?) : OsSpecificDistributionBuilder {
  private val iconPngPath: Path?

  override val targetOs: OsFamily
    get() = OsFamily.LINUX

  init {
    val iconPng = (if (context.applicationInfo.isEAP) customizer.iconPngPathForEAP else null) ?: customizer.iconPngPath
    iconPngPath = if (iconPng.isNullOrEmpty()) null else Path.of(iconPng)
  }

  override suspend fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture) {
    spanBuilder("copy files for os distribution").setAttribute("os", targetOs.osName).setAttribute("arch", arch.name).useWithScope2 {
      withContext(Dispatchers.IO) {
        val distBinDir = targetPath.resolve("bin")
        val sourceBinDir = context.paths.communityHomeDir.resolve("bin/linux")
        copyFileToDir(sourceBinDir.resolve("restart.py"), distBinDir)
        if (arch == JvmArchitecture.x64 || arch == JvmArchitecture.aarch64) {
          @Suppress("SpellCheckingInspection")
          listOf("fsnotifier", "libdbm.so").forEach {
            copyFileToDir(sourceBinDir.resolve("${arch.dirName}/${it}"), distBinDir)
          }
        }
        generateBuildTxt(context, targetPath)
        copyDistFiles(context = context, newDir = targetPath, os = OsFamily.LINUX, arch = arch)
        Files.copy(ideaProperties!!, distBinDir.resolve(ideaProperties.fileName), StandardCopyOption.REPLACE_EXISTING)
        //todo[nik] converting line separators to unix-style make sense only when building Linux distributions under Windows on a local machine;
        // for real installers we need to checkout all text files with 'lf' separators anyway
        convertLineSeparators(targetPath.resolve("bin/idea.properties"), "\n")
        if (iconPngPath != null) {
          Files.copy(iconPngPath, distBinDir.resolve("${context.productProperties.baseFileName}.png"), StandardCopyOption.REPLACE_EXISTING)
        }
        generateVMOptions(distBinDir)
        generateScripts(distBinDir, arch)
        generateReadme(targetPath)
        generateVersionMarker(targetPath, context)
        RepairUtilityBuilder.bundle(context = context, os = OsFamily.LINUX, arch = arch, distributionDir = targetPath)
        customizer.copyAdditionalFiles(context = context, targetDir = targetPath, arch = arch)
      }
    }
  }

  override suspend fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    copyFilesForOsDistribution(osAndArchSpecificDistPath, arch)
    setLastModifiedTime(osAndArchSpecificDistPath, context)
    val suffix = if (arch == JvmArchitecture.x64) "" else "-${arch.fileSuffix}"
    context.executeStep(spanBuilder("build linux .tar.gz").setAttribute("arch", arch.name), BuildOptions.LINUX_ARTIFACTS_STEP) {
      if (customizer.buildTarGzWithoutBundledRuntime) {
        context.executeStep(spanBuilder("Build Linux .tar.gz without bundled Runtime").setAttribute("arch", arch.name),
                            BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_RUNTIME_STEP) {
          val tarGzPath = buildTarGz(runtimeDir = null,
                                     unixDistPath = osAndArchSpecificDistPath,
                                     suffix = NO_JBR_SUFFIX + suffix,
                                     arch = arch)
          checkExecutablePermissions(tarGzPath, rootDirectoryName, includeRuntime = false, arch = arch)
        }
      }
      if (customizer.buildOnlyBareTarGz) {
        return@executeStep
      }

      val runtimeDir = context.bundledRuntime.extract(getProductPrefix(context), OsFamily.LINUX, arch)
      val tarGzPath = buildTarGz(arch = arch, runtimeDir = runtimeDir, unixDistPath = osAndArchSpecificDistPath, suffix = suffix)
      checkExecutablePermissions(tarGzPath, rootDirectoryName, includeRuntime = true, arch = arch)

      if (arch == JvmArchitecture.x64) {
        buildSnapPackage(runtimeDir = runtimeDir, unixDistPath = osAndArchSpecificDistPath, arch = arch)
      }
      else {
        // TODO: Add snap for aarch64
        Span.current().addEvent("skip building Snap packages for non-x64 arch")
      }

      if (!context.options.buildStepsToSkip.contains(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP)) {
        val tempTar = Files.createTempDirectory(context.paths.tempDir, "tar-")
        try {
          unTar(tarGzPath, tempTar)
          RepairUtilityBuilder.generateManifest(context = context,
                                                unpackedDistribution = tempTar.resolve(rootDirectoryName),
                                                os = OsFamily.LINUX,
                                                arch = arch)
        }
        finally {
          NioFiles.deleteRecursively(tempTar)
        }
      }
    }
  }

  private fun generateVMOptions(distBinDir: Path) {
    val fileName = "${context.productProperties.baseFileName}64.vmoptions"

    @Suppress("SpellCheckingInspection")
    val vmOptions = VmOptionsGenerator.computeVmOptions(context.applicationInfo.isEAP, context.productProperties) +
                    listOf("-Dsun.tools.attach.tmp.only=true")
    VmOptionsGenerator.writeVmOptions(distBinDir.resolve(fileName), vmOptions, "\n")
  }

  private fun generateReadme(unixDistPath: Path) {
    val fullName = context.applicationInfo.productName
    val sourceFile = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/Install-Linux-tar.txt")
    val targetFile = unixDistPath.resolve("Install-Linux-tar.txt")
    substituteTemplatePlaceholders(sourceFile, targetFile, "@@", listOf(
      Pair("product_full", fullName),
      Pair("product", context.productProperties.baseFileName),
      Pair("product_vendor", context.applicationInfo.shortCompanyName),
      Pair("system_selector", context.systemSelector)
    ), convertToUnixLineEndings = true)
  }

  override fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture): List<String> {
    return customizer.generateExecutableFilesPatterns(context, includeRuntime, arch)
  }

  private val rootDirectoryName: String
    get() = customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)

  private suspend fun buildTarGz(arch: JvmArchitecture, runtimeDir: Path?, unixDistPath: Path, suffix: String): Path = withContext(Dispatchers.IO) {
    val tarRoot = rootDirectoryName
    val tarName = artifactName(context, suffix)
    val tarPath = context.paths.artifactDir.resolve(tarName)
    val paths = mutableListOf(context.paths.distAllDir, unixDistPath)
    var javaExecutablePath: String? = null
    if (runtimeDir != null) {
      paths.add(runtimeDir)
      javaExecutablePath = "jbr/bin/java"
      require(Files.exists(runtimeDir.resolve(javaExecutablePath))) { "$javaExecutablePath was not found under $runtimeDir" }
    }

    val productJsonDir = context.paths.tempDir.resolve("linux.dist.product-info.json$suffix")
    generateProductJson(targetDir = productJsonDir, arch = arch, javaExecutablePath = javaExecutablePath, context = context)
    paths.add(productJsonDir)

    spanBuilder("build Linux tar.gz")
      .setAttribute("runtimeDir", runtimeDir?.toString() ?: "")
      .useWithScope2 {
        val executableFileMatchers = generateExecutableFilesMatchers(runtimeDir != null, arch).keys
        tar(tarPath, tarRoot, paths, executableFileMatchers, context.options.buildDateInSeconds)
        checkInArchive(tarPath, tarRoot, context)
        context.notifyArtifactBuilt(tarPath)
      }
    tarPath
  }

  private suspend fun buildSnapPackage(runtimeDir: Path, unixDistPath: Path, arch: JvmArchitecture) {
    val snapName = customizer.snapName ?: return
    if (!context.options.buildUnixSnaps) {
      return
    }

    val snapDir = context.paths.buildOutputDir.resolve("dist.snap")
    spanBuilder("build Linux .snap package")
      .setAttribute("snapName", snapName)
      .useWithScope { span ->
        if (SystemInfoRt.isWindows) {
          span.addEvent(".snap cannot be built on Windows, skipped")
          return@useWithScope
        }
        check(iconPngPath != null) { context.messages.error("'iconPngPath' not set") }
        check(!customizer.snapDescription.isNullOrBlank()) { context.messages.error("'snapDescription' not set") }

        span.addEvent("prepare files")

        val unixSnapDistPath = context.paths.buildOutputDir.resolve("dist.unix.snap")
        copyDir(unixDistPath, unixSnapDistPath)
        val appInfo = context.applicationInfo
        val productName = appInfo.productNameWithEdition
        substituteTemplatePlaceholders(
          inputFile = context.paths.communityHomeDir.resolve("platform/platform-resources/src/entry.desktop"),
          outputFile = snapDir.resolve("$snapName.desktop"),
          placeholder = "$",
          values = listOf(
            Pair("NAME", productName),
            Pair("ICON", "\${SNAP}/bin/${context.productProperties.baseFileName}.png"),
            Pair("SCRIPT", snapName),
            Pair("COMMENT", appInfo.motto ?: ""),
            Pair("WM_CLASS", getLinuxFrameClass(context))
          )
        )
        copyFile(iconPngPath, snapDir.resolve("$snapName.png"))
        val snapcraftTemplate = context.paths.communityHomeDir.resolve(
          "platform/build-scripts/resources/linux/snap/snapcraft-template.yaml")
        val versionSuffix = appInfo.versionSuffix?.replace(' ', '-') ?: ""
        val version = "${appInfo.majorVersion}.${appInfo.minorVersion}${if (versionSuffix.isEmpty()) "" else "-${versionSuffix}"}"
        val snapcraftConfig = snapDir.resolve("snapcraft.yaml")
        substituteTemplatePlaceholders(
          inputFile = snapcraftTemplate,
          outputFile = snapcraftConfig,
          placeholder = "$",
          values = listOf(
            Pair("NAME", snapName),
            Pair("VERSION", version),
            Pair("SUMMARY", productName),
            Pair("DESCRIPTION", customizer.snapDescription ?: ""),
            Pair("GRADE", if (appInfo.isEAP) "devel" else "stable"),
            Pair("SCRIPT", "bin/${context.productProperties.baseFileName}.sh")
          )
        )
        context.messages.info("""
          |# <${snapcraftConfig.name}>
          |${Files.readString(snapcraftConfig)}
          |# </${snapcraftConfig.name}>
        """.trimMargin())

        FileSet(unixSnapDistPath)
          .include("bin/*.sh")
          .include("bin/*.py")
          .include("bin/fsnotifier*")
          .enumerate().forEach(::makeFileExecutable)

        FileSet(runtimeDir)
          .include("jbr/bin/*")
          .enumerate().forEach(::makeFileExecutable)

        if (!customizer.extraExecutables.isEmpty()) {
          for (distPath in listOf(unixSnapDistPath, context.paths.distAllDir)) {
            val fs = FileSet(distPath)
            customizer.extraExecutables.forEach(fs::include)
            fs.enumerateNoAssertUnusedPatterns().forEach(::makeFileExecutable)
          }
        }
        validateProductJson(jsonText = generateProductJson(unixSnapDistPath, arch = arch, "jbr/bin/java", context),
                            relativePathToProductJson = "",
                            installationDirectories = listOf(context.paths.distAllDir, unixSnapDistPath, runtimeDir),
                            installationArchives = listOf(),
                            context = context)
        val resultDir = snapDir.resolve("result")
        Files.createDirectories(resultDir)
        span.addEvent("build package")
        val snapArtifact = snapName + "_" + version + "_amd64.snap"
        runProcess(
          args = listOf(
            "docker", "run", "--rm", "--volume=$snapcraftConfig:/build/snapcraft.yaml:ro",
            "--volume=$snapDir/$snapName.desktop:/build/snap/gui/$snapName.desktop:ro",
            "--volume=$snapDir/$snapName.png:/build/prime/meta/gui/icon.png:ro",
            "--volume=$snapDir/result:/build/result",
            "--volume=${context.paths.getDistAll()}:/build/dist.all:ro",
            "--volume=$unixSnapDistPath:/build/dist.unix:ro",
            "--volume=$runtimeDir:/build/jre:ro",
            "--workdir=/build",
            context.options.snapDockerImage,
            "snapcraft",
            "snap", "-o", "result/$snapArtifact"
          ),
          workingDir = snapDir,
          timeout = context.options.snapDockerBuildTimeoutMin.minutes,
        )
        moveFileToDir(resultDir.resolve(snapArtifact), context.paths.artifactDir)
        context.notifyArtifactBuilt(context.paths.artifactDir.resolve(snapArtifact))
      }
  }

  private fun generateScripts(distBinDir: Path, arch: JvmArchitecture) {
    val classPathJars = context.bootClassPathJarNames
    var classPath = "CLASS_PATH=\"\$IDE_HOME/lib/${classPathJars[0]}\""
    for (i in 1 until classPathJars.size) {
      classPath += "\nCLASS_PATH=\"\$CLASS_PATH:\$IDE_HOME/lib/${classPathJars[i]}\""
    }

    val additionalJvmArguments = context.getAdditionalJvmArguments(os = OsFamily.LINUX, arch = arch, isScript = true).toMutableList()
    if (!context.xBootClassPathJarNames.isEmpty()) {
      val bootCp = context.xBootClassPathJarNames.joinToString(separator = ":") { "\$IDE_HOME/lib/${it}" }
      additionalJvmArguments.add("\"-Xbootclasspath/a:$bootCp\"")
    }
    val additionalJvmArgs = additionalJvmArguments.joinToString(separator = " ")
    val baseName = context.productProperties.baseFileName

    val vmOptionsPath = distBinDir.resolve("${baseName}64.vmoptions")

    val defaultXmxParameter = try {
      Files.readAllLines(vmOptionsPath).firstOrNull { it.startsWith("-Xmx") }
    }
    catch (e: NoSuchFileException) {
      throw IllegalStateException("File '$vmOptionsPath' should be already generated at this point", e)
    } ?: throw IllegalStateException("-Xmx was not found in '$vmOptionsPath'")

    Files.createDirectories(distBinDir)
    val sourceScriptDir = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/scripts")

    val scriptName = "$baseName.sh"
    Files.newDirectoryStream(sourceScriptDir).use {
      for (file in it) {
        val fileName = file.fileName.toString()
        val target = distBinDir.resolve(if (fileName == "executable-template.sh") scriptName else fileName)
        copyScript(sourceFile = file,
                   targetFile = target,
                   vmOptionsFileName = baseName,
                   additionalJvmArgs = additionalJvmArgs,
                   defaultXmxParameter = defaultXmxParameter,
                   classPath = classPath,
                   scriptName = scriptName,
                   context = context)
      }
    }

    copyInspectScript(context, distBinDir)
  }
}

private const val NO_JBR_SUFFIX = "-no-jbr"

private fun generateProductJson(targetDir: Path, arch: JvmArchitecture, javaExecutablePath: String?, context: BuildContext): String {
  val scriptName = context.productProperties.baseFileName
  val file = targetDir.resolve(PRODUCT_INFO_FILE_NAME)
  Files.createDirectories(targetDir)
  val json = generateMultiPlatformProductJson(
    relativePathToBin = "bin",
    builtinModules = context.builtinModule,
    launch = listOf(ProductInfoLaunchData(
      os = OsFamily.LINUX.osName,
      arch = arch.dirName,
      launcherPath = "bin/$scriptName.sh",
      javaExecutablePath = javaExecutablePath,
      vmOptionsFilePath = "bin/" + scriptName + "64.vmoptions",
      startupWmClass = getLinuxFrameClass(context),
      bootClassPathJarNames = context.bootClassPathJarNames,
      additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.LINUX, arch))),
    context = context
  )
  Files.writeString(file, json)
  return json
}

private fun generateVersionMarker(unixDistPath: Path, context: BuildContext) {
  val targetDir = unixDistPath.resolve("lib")
  Files.createDirectories(targetDir)
  Files.writeString(targetDir.resolve("build-marker-" + context.fullBuildNumber), context.fullBuildNumber)
}

private fun artifactName(buildContext: BuildContext, suffix: String?): String {
  val baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
  return "$baseName$suffix.tar.gz"
}

private fun makeFileExecutable(file: Path) {
  Span.current().addEvent("set file permission to 0755", Attributes.of(AttributeKey.stringKey("file"), file.toString()))
  @Suppress("SpellCheckingInspection")
  Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"))
}

private fun copyScript(sourceFile: Path,
                       targetFile: Path,
                       vmOptionsFileName: String,
                       additionalJvmArgs: String,
                       defaultXmxParameter: String,
                       classPath: String,
                       scriptName: String,
                       context: BuildContext) {
  // Until CR (\r) will be removed from the repository checkout, we need to filter it out from Unix-style scripts
  // https://youtrack.jetbrains.com/issue/IJI-526/Force-git-to-use-LF-line-endings-in-working-copy-of-via-gitattri
  substituteTemplatePlaceholders(
    inputFile = sourceFile,
    outputFile = targetFile,
    placeholder = "__",
    values = listOf(
      Pair("product_full", context.applicationInfo.productName),
      Pair("product_uc", context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo)),
      Pair("product_vendor", context.applicationInfo.shortCompanyName),
      Pair("product_code", context.applicationInfo.productCode),
      Pair("vm_options", vmOptionsFileName),
      Pair("system_selector", context.systemSelector),
      Pair("ide_jvm_args", additionalJvmArgs),
      Pair("ide_default_xmx", defaultXmxParameter.trim()),
      Pair("class_path", classPath),
      Pair("script_name", scriptName),
      Pair("main_class_name", context.productProperties.mainClassName),
    ),
    mustUseAllPlaceholders = false,
    convertToUnixLineEndings = true,
  )
}
