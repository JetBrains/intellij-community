// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.util.io.NioFiles
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.BuildUtils.assertUnixLineEndings
import org.jetbrains.intellij.build.impl.BundledRuntimeImpl.Companion.getProductPrefix
import org.jetbrains.intellij.build.impl.VmOptionsGenerator.computeVmOptions
import org.jetbrains.intellij.build.impl.productInfo.*
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions

class LinuxDistributionBuilder(private val context: BuildContext,
                               private val customizer: LinuxDistributionCustomizer,
                               private val ideaProperties: Path?) : OsSpecificDistributionBuilder {
  private val iconPngPath: Path?

  override val targetOs: OsFamily
    get() = OsFamily.LINUX

  init {
    val iconPng = (if (context.applicationInfo.isEAP) customizer.iconPngPathForEAP else null) ?: customizer.iconPngPath
    iconPngPath = if (iconPng.isNullOrEmpty()) null else Path.of(iconPng)
  }

  override fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture) {
    spanBuilder("copy files for os distribution").setAttribute("os", targetOs.osName).setAttribute("arch", arch.name).useWithScope {
      val distBinDir = targetPath.resolve("bin")
      copyDir(context.paths.communityHomeDir.resolve("bin/linux"), distBinDir)
      unpackPty4jNative(context, targetPath, "linux")
      generateBuildTxt(context, targetPath)
      copyDistFiles(context, targetPath)
      val extraJarNames = addDbusJava(context, targetPath.resolve("lib"))
      Files.copy(ideaProperties!!, distBinDir.resolve(ideaProperties.fileName), StandardCopyOption.REPLACE_EXISTING)
      //todo[nik] converting line separators to unix-style make sense only when building Linux distributions under Windows on a local machine;
      // for real installers we need to checkout all text files with 'lf' separators anyway
      convertLineSeparators(targetPath.resolve("bin/idea.properties"), "\n")
      if (iconPngPath != null) {
        Files.copy(iconPngPath, distBinDir.resolve(context.productProperties.baseFileName + ".png"), StandardCopyOption.REPLACE_EXISTING)
      }
      generateVMOptions(distBinDir)
      UnixScriptBuilder.generateScripts(context, extraJarNames, distBinDir, OsFamily.LINUX)
      generateReadme(targetPath)
      generateVersionMarker(targetPath, context)
      RepairUtilityBuilder.bundle(context, OsFamily.LINUX, arch, targetPath)
      customizer.copyAdditionalFiles(context = context, targetDir = targetPath, arch = arch)
    }
  }

  override fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    copyFilesForOsDistribution(osAndArchSpecificDistPath, arch)
    context.executeStep(spanBuilder("build linux .tar.gz").setAttribute("arch", arch.name), BuildOptions.LINUX_ARTIFACTS_STEP) {
      if (customizer.buildTarGzWithoutBundledRuntime) {
        context.executeStep("Build Linux .tar.gz without bundled JRE", BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_JRE_STEP) {
          buildTarGz(jreDirectoryPath = null, unixDistPath = osAndArchSpecificDistPath, suffix = NO_JBR_SUFFIX)
        }
      }
      if (customizer.buildOnlyBareTarGz) {
        return@executeStep
      }

      val jreDirectoryPath = context.bundledRuntime.extract(getProductPrefix(context), OsFamily.LINUX, arch)
      val tarGzPath = buildTarGz(jreDirectoryPath.toString(), osAndArchSpecificDistPath, "")
      context.bundledRuntime.checkExecutablePermissions(tarGzPath, rootDirectoryName, OsFamily.LINUX)

      buildSnapPackage(jreDirectoryPath.toString(), osAndArchSpecificDistPath)

      val tempTar = Files.createTempDirectory(context.paths.tempDir, "tar-")
      try {
        ArchiveUtils.unTar(tarGzPath, tempTar)
        val tarRoot = customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)
        RepairUtilityBuilder.generateManifest(context, tempTar.resolve(tarRoot), tarGzPath.fileName.toString())
      }
      finally {
        NioFiles.deleteRecursively(tempTar)
      }
    }
  }

  private fun generateVMOptions(distBinDir: Path) {
    val fileName = "${context.productProperties.baseFileName}64.vmoptions"
    @Suppress("SpellCheckingInspection")
    val vmOptions = computeVmOptions(context.applicationInfo.isEAP, context.productProperties) + listOf("-Dsun.tools.attach.tmp.only=true")
    Files.writeString(distBinDir.resolve(fileName), vmOptions.joinToString(separator = "\n"), StandardCharsets.US_ASCII)
  }

  private fun generateReadme(unixDistPath: Path) {
    val fullName = context.applicationInfo.productName
    val sourceFile = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/Install-Linux-tar.txt")
    assertUnixLineEndings(sourceFile)
    val targetFile = unixDistPath.resolve("Install-Linux-tar.txt")
    substituteTemplatePlaceholders(sourceFile, targetFile, "@@", listOf(
      Pair("product_full", fullName),
      Pair("product", context.productProperties.baseFileName),
      Pair("product_vendor", context.applicationInfo.shortCompanyName),
      Pair("system_selector", context.systemSelector)
    ))
  }

  override fun generateExecutableFilesPatterns(includeJre: Boolean): List<String> {
    val patterns = ArrayList<String>()
    patterns.addAll(listOf("bin/*.sh", "bin/*.py", "bin/fsnotifier*", "bin/remote-dev-server.sh"))
    patterns.addAll(customizer.extraExecutables)
    if (includeJre) {
      patterns.addAll(context.bundledRuntime.executableFilesPatterns(OsFamily.LINUX))
    }
    return patterns
  }

  override fun getArtifactNames(context: BuildContext): List<String> {
    val suffixes = ArrayList<String>()
    if (customizer.buildTarGzWithoutBundledRuntime) {
      suffixes.add(NO_JBR_SUFFIX)
    }
    if (!customizer.buildOnlyBareTarGz) {
      suffixes.add("")
    }
    return suffixes.map { artifactName(context, it) }
  }

  private val rootDirectoryName: String
    get() = customizer.getRootDirectoryName(context.applicationInfo, context.buildNumber)

  private fun buildTarGz(jreDirectoryPath: String?, unixDistPath: Path, suffix: String): Path {
    val tarRoot = rootDirectoryName
    val tarName = artifactName(context, suffix)
    val tarPath = context.paths.artifactDir.resolve(tarName)
    val paths = mutableListOf(context.paths.distAllDir, unixDistPath)
    var javaExecutablePath: String? = null
    if (jreDirectoryPath != null) {
      val jreDir = Path.of(jreDirectoryPath)
      paths.add(jreDir)
      javaExecutablePath = "jbr/bin/java"
      check(Files.exists(jreDir.resolve(javaExecutablePath))) { "$javaExecutablePath was not found under $jreDirectoryPath" }
    }

    val productJsonDir = context.paths.tempDir.resolve("linux.dist.product-info.json$suffix")
    generateProductJson(productJsonDir, javaExecutablePath, context)
    paths.add(productJsonDir)

    val executableFilesPatterns = generateExecutableFilesPatterns(jreDirectoryPath != null)
    spanBuilder("build Linux tar.gz")
      .setAttribute("jreDirectoryPath", jreDirectoryPath ?: "")
      .useWithScope {
        for (dir in paths) {
          updateExecutablePermissions(dir, executableFilesPatterns)
        }
        ArchiveUtils.tar(tarPath, tarRoot, paths.map(Path::toString), context.options.buildDateInSeconds)
        checkInArchive(context, tarPath, tarRoot)
        context.notifyArtifactBuilt(tarPath)
      }
    return tarPath
  }

  private fun buildSnapPackage(jreDirectoryPath: String, unixDistPath: Path) {
    val snapName = customizer.snapName ?: return
    if (!context.options.buildUnixSnaps) {
      return
    }

    val snapDir = context.paths.buildOutputDir.resolve("dist.snap")
    spanBuilder("build Linux .snap package")
      .setAttribute("snapName", snapName)
      .useWithScope { span ->
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
          placeholderChar = "$",
          values = listOf(
            Pair("NAME", productName),
            Pair("ICON", "\${SNAP}/bin/${context.productProperties.baseFileName}.png"),
            Pair("SCRIPT", snapName),
            Pair("COMMENT", appInfo.motto),
            Pair("WM_CLASS", getLinuxFrameClass(context))
          )
        )
        moveFile(iconPngPath, snapDir.resolve("$snapName.png"))
        val snapcraftTemplate = context.paths.communityHomeDir.resolve(
          "platform/build-scripts/resources/linux/snap/snapcraft-template.yaml")
        val versionSuffix = appInfo.versionSuffix.replace(' ', '-')
        val version = "${appInfo.majorVersion}.${appInfo.minorVersion}${if (versionSuffix.isEmpty()) "" else "-${versionSuffix}"}"
        substituteTemplatePlaceholders(
          inputFile = snapcraftTemplate,
          outputFile = snapDir.resolve("snapcraft.yaml"),
          placeholderChar = "$",
          values = listOf(
            Pair("NAME", snapName),
            Pair("VERSION", version),
            Pair("SUMMARY", productName),
            Pair("DESCRIPTION", customizer.snapDescription!!),
            Pair("GRADE", if (appInfo.isEAP) "devel" else "stable")
          )
        )

        FileSet(unixSnapDistPath)
          .include("bin/*.sh")
          .include("bin/*.py")
          .include("bin/fsnotifier*")
          .enumerate().forEach(::makeFileExecutable)

        FileSet(Path.of(jreDirectoryPath))
          .include("jbr/bin/*")
          .enumerate().forEach(::makeFileExecutable)

        if (!customizer.extraExecutables.isEmpty()) {
          for (distPath in listOf(unixSnapDistPath, context.paths.distAllDir)) {
            val fs = FileSet(distPath)
            customizer.extraExecutables.forEach(fs::include)
            fs.enumerateNoAssertUnusedPatterns().forEach(::makeFileExecutable)
          }
        }
        validateProductJson(jsonText = generateProductJson(unixSnapDistPath, "jbr/bin/java", context),
                            relativePathToProductJson = "",
                            installationDirectories = listOf(context.paths.distAllDir, unixSnapDistPath, Path.of(jreDirectoryPath)),
                            installationArchives = listOf(),
                            context = context)
        val resultDir = snapDir.resolve("result")
        Files.createDirectories(resultDir)
        span.addEvent("build package")
        val snapArtifact = snapName + "_" + version + "_amd64.snap"
        runProcess(
          args = listOf(
            "docker", "run", "--rm", "--volume=$snapDir/snapcraft.yaml:/build/snapcraft.yaml:ro",
            "--volume=$snapDir/$snapName.desktop:/build/snap/gui/$snapName.desktop:ro",
            "--volume=$snapDir/$snapName.png:/build/prime/meta/gui/icon.png:ro",
            "--volume=$snapDir/result:/build/result",
            "--volume=${context.paths.getDistAll()}:/build/dist.all:ro",
            "--volume=$unixSnapDistPath:/build/dist.unix:ro",
            "--volume=$jreDirectoryPath:/build/jre:ro",
            "--workdir=/build",
            context.options.snapDockerImage,
            "snapcraft",
            "snap", "-o", "result/$snapArtifact"
          ),
          workingDir = snapDir,
          logger = context.messages
        )
        moveFileToDir(resultDir.resolve(snapArtifact), context.paths.artifactDir)
        context.notifyArtifactWasBuilt(context.paths.artifactDir.resolve(snapArtifact))
      }
  }
}

private const val NO_JBR_SUFFIX = "-no-jbr"

private fun generateProductJson(targetDir: Path, javaExecutablePath: String?, context: BuildContext): String {
  val scriptName = context.productProperties.baseFileName
  val file = targetDir.resolve(PRODUCT_INFO_FILE_NAME)
  Files.createDirectories(targetDir)
  val json = generateMultiPlatformProductJson(
    relativePathToBin = "bin",
    builtinModules = context.getBuiltinModule(),
    launch = listOf(ProductInfoLaunchData(os = OsFamily.LINUX.osName,
                                          launcherPath = "bin/$scriptName.sh",
                                          javaExecutablePath = javaExecutablePath,
                                          vmOptionsFilePath = "bin/" + scriptName + "64.vmoptions",
                                          startupWmClass = getLinuxFrameClass(context))),
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

fun copyFileSymlinkAware(source: Path, target: Path, vararg options: CopyOption) {
  val parent = target.parent
  if (parent != null) {
    Files.createDirectories(parent)
  }

  val optionsList = options.toMutableList()
  if (Files.isSymbolicLink(source)) {
    // Append 'NOFOLLOW_LINKS' copy option to be able to copy symbolic links.
    if (!optionsList.contains(LinkOption.NOFOLLOW_LINKS)) {
      optionsList.add(LinkOption.NOFOLLOW_LINKS)
    }
  }
  Files.copy(source, target, *optionsList.toTypedArray())
}