// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.buildData.productInfo.ProductInfoLaunchData
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.OhosDistributionCustomizer
import org.jetbrains.intellij.build.OhosLibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.add64IfNeeded
import org.jetbrains.intellij.build.executeStep
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.productInfo.generateProductInfoJson
import org.jetbrains.intellij.build.impl.productInfo.jsonEncoder
import org.jetbrains.intellij.build.impl.productInfo.validateProductJson
import org.jetbrains.intellij.build.impl.productInfo.writeProductInfoJson
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * There is no installer for OHOS. The artifact is a directory that a host JVM application on OHOS takes as an input.
 * The directory contains:
 * - the `ide` directory: the IDE home;
 * - the `java.args` file: the JVM arguments with which the host application starts the IDE, one per line. They refer
 *   to the directory of the app as `$APP_DIR`, because the host application decides where it goes;
 * - the `metadata` directory: the name, the icon and the version that the host application shows for the IDE, see [writeMetadata].
 *
 * As on other OSes, the JVM arguments come from three places:
 * - the `*.vmoptions` file in `bin`, with the defaults that a user can override: [vmOptions];
 * - the launch section of `product-info.json`, with the arguments that the IDE needs: [BuildContext.getAdditionalJvmArguments];
 * - the launcher, which adds its own arguments and combines all of them. On OHOS, [writeJavaArgs] does this at build time.
 */
internal class OhosDistributionBuilder(
  private val customizer: OhosDistributionCustomizer,
  private val ideaProperties: CharSequence?,
  private val context: BuildContext,
) : OsSpecificDistributionBuilder {
  override val targetOs: OsFamily = OsFamily.OHOS

  override val targetLibcImpl: LibcImpl = OhosLibcImpl.DEFAULT

  override fun copyNativeBinFiles(binDir: Path, arch: JvmArchitecture): List<Path> {
    return nativeBinFiles(context.paths.communityHomeDir, OsFamily.OHOS, arch).map { copyNativeBinFileToDir(it, binDir) }
  }

  override fun copyFilesForOsDistribution(targetPath: Path, arch: JvmArchitecture) {
    spanBuilder("copy files for os distribution")
      .setAttribute("os", targetOs.osName)
      .setAttribute("arch", arch.name).use {
        val distBinDir = targetPath.resolve("bin").createDirectories()
        writeVmOptions(distBinDir)
        copyDeclaredOsSpecificFiles(targetPath, arch, context.distributionState().platformLayout, context)
        generateBuildTxt(targetPath, context)
        copyDistFiles(targetPath, OsFamily.OHOS, arch, targetLibcImpl, context)
        Files.writeString(distBinDir.resolve(PROPERTIES_FILE_NAME), ideaProperties!!.lineSequence().joinToString("\n"))
        customizer.copyAdditionalFiles(targetPath, arch, context)
      }
  }

  override fun buildArtifacts(osAndArchSpecificDistPath: Path, arch: JvmArchitecture) {
    copyFilesForOsDistribution(osAndArchSpecificDistPath, arch)
    setLastModifiedTime(osAndArchSpecificDistPath, context)
    context.executeStep(spanBuilder("build OHOS artifacts").setAttribute("arch", arch.name), BuildOptions.OHOS_ARTIFACTS_STEP) {
      val appDir = getOhosAppDirectory(context, arch)
      val ideDir = appDir.resolve("ide")
      NioFiles.deleteRecursively(appDir)
      val distAllDir = context.paths.distAllDir
      // the OHOS-specific files replace the common ones
      copyDir(distAllDir, ideDir, fileFilter = { Files.notExists(osAndArchSpecificDistPath.resolve(distAllDir.relativize(it))) })
      copyDir(osAndArchSpecificDistPath, ideDir)

      val productInfoFile = writeProductInfoFile(ideDir, arch)
      validateProductJson(
        jsonText = Files.readString(productInfoFile),
        installationDirectories = listOf(ideDir),
        installationArchives = emptyList(),
        context = context,
      )
      writeJavaArgs(appDir.resolve("java.args"), arch)
      writeMetadata(appDir.resolve("metadata"))

      setLastModifiedTime(appDir, context)

      context.executeStep(spanBuilder("build OHOS .tar.gz").setAttribute("arch", arch.name), BuildOptions.OHOS_TAR_GZ_STEP) {
        createChecksumAndGpgSignFiles(context) { buildTarGz(appDir, arch) }
      }
    }
  }

  private fun buildTarGz(appDir: Path, arch: JvmArchitecture): Path {
    val tarPath = getTarGzPath(arch)
    // the executable files are those with the bit set in the app directory
    tar(tarPath, rootDir = "${appDir.fileName}", directories = listOf(appDir), executableFileMatchers = emptyList(), context.options.buildDateInSeconds)
    context.notifyArtifactBuilt(tarPath)
    return tarPath
  }

  private fun getTarGzPath(arch: JvmArchitecture): Path = getOhosAppDirectory(context, arch).let { it.resolveSibling("${it.fileName}.tar.gz") }

  override fun writeProductInfoFile(targetDir: Path, arch: JvmArchitecture): Path {
    val json = generateProductInfoJson(
      relativePathToBin = "bin",
      builtinModules = context.builtinModules(),
      launch = listOf(productInfoLaunchData(arch)),
      context = context,
    )
    val file = targetDir.resolve(PRODUCT_INFO_FILE_NAME)
    writeProductInfoJson(file, json, context)
    return file
  }

  private fun productInfoLaunchData(arch: JvmArchitecture): ProductInfoLaunchData = ProductInfoLaunchData.create(
    os = OsFamily.OHOS.osName,
    arch = arch.dirName,
    // there is no launcher: the host application starts the IDE
    launcherPath = "",
    javaExecutablePath = null,
    vmOptionsFilePath = "bin/$vmOptionsFileName",
    bootClassPathJarNames = context.bootClassPathJarNames,
    additionalJvmArguments = context.getAdditionalJvmArguments(OsFamily.OHOS, arch),
    mainClass = context.ideMainClassName,
  )

  // What a launcher does on other OSes, but at build time: the host application only knows how to start a JVM
  private fun writeJavaArgs(file: Path, arch: JvmArchitecture) {
    val launchData = productInfoLaunchData(arch)
    val ideHome = "$APP_DIR_MACRO/ide"
    val launcherArgs = listOf(
      "-Djb.vmOptionsFile=$configDir/$vmOptionsFileName",
      "-Djava.class.path=" + launchData.bootClassPathJarNames.joinToString(":") { "$ideHome/lib/$it" },
    )
    val args = buildList {
      addAll(this@OhosDistributionBuilder.vmOptions())
      launchData.additionalJvmArguments.mapTo(this) { it.replace($$"$IDE_HOME", ideHome) }
      addAll(launcherArgs)
      add(launchData.mainClass!!)
    }
    val invalid = args.filter { arg -> arg.any(Char::isWhitespace) || $$"$IDE_" in arg }
    check(invalid.isEmpty()) { "Arguments with whitespace or unresolved macros can't go into $file: $invalid" }
    Files.writeString(file, args.joinToString("\n", postfix = "\n"))
  }

  private fun writeMetadata(metadataDir: Path) {
    metadataDir.createDirectories()
    val resourcesDir = metadataDir.resolve("resources/base")
    val resourcesElementDir = resourcesDir.resolve("element").createDirectories()
    Files.writeString(resourcesElementDir.resolve("string.json"), jsonEncoder.encodeToString(buildJsonObject {
      putJsonArray("string") {
        addJsonObject {
          put("name", "app_name")
          put("value", context.applicationInfo.fullProductName)
        }
      }
    }))
    val iconPngPath = context.linuxDistributionCustomizer?.let { locateIconForLinuxLauncher(it, context) }
    if (iconPngPath != null) {
      val mediaDir = resourcesDir.resolve("media").createDirectories()
      Files.copy(iconPngPath, mediaDir.resolve("app_icon.png"))
    }
    Files.writeString(metadataDir.resolve("app.json5"), jsonEncoder.encodeToString(buildJsonObject {
      putJsonObject("app") {
        put("versionName", context.applicationInfo.fullVersion)
        put("label", $$"$string:app_name")
        if (iconPngPath != null) {
          put("icon", $$"$media:app_icon")
        }
      }
    }))
  }

  override fun writeVmOptions(distBinDir: Path): Path {
    val vmOptionsFile = distBinDir.resolve(vmOptionsFileName)
    writeVmOptions(vmOptionsFile, vmOptions(), separator = "\n")
    return vmOptionsFile
  }

  // there is no embedded frontend on OHOS
  override fun writeEmbeddedFrontendVmOptions(distBinDir: Path): Path? = null

  private fun vmOptions(): List<String> =
    generateVmOptions(context, extra = ohosVmOptions + customizer.additionalVmOptions + devVmOptions)

  private val productFileName = context.productProperties.baseFileName

  private val vmOptionsFileName: String
    get() = "${context.add64IfNeeded(productFileName)}.vmoptions"

  private val configDir: String
    get() = "/data/storage/el2/base/files/${productFileName}/config/$productFileName"
  private val systemDir: String
    get() = "/data/storage/el2/base/files/${productFileName}/system/$productFileName"

  private val ohosVmOptions: List<String>
    get() = listOf(
      "-Didea.config.path=$configDir",
      "-Didea.plugins.path=$configDir/plugins",
      "-Didea.system.path=$systemDir",
      "-Didea.log.path=$systemDir/log",

      // there is no splash window on OHOS; overrides -Dsplash=true from product-info.json
      "-Dnosplash=true",
      // Skiko has no OHOS natives, so the IDE must not preload them
      "-Dskiko.preload.native.lib=false",
      // libghostty-vt has no OHOS natives, so the reworked terminal uses the JediTerm emulator
      "-Dterminal.use.ghostty.emulator=false",
      "-Dsun.awt.disablegrab=true",
      "-Dapple.laf.useScreenMenuBar=true",
      "-DjbScreenMenuBar.enabled=true",
      // the merged title bar (custom window header), without an IDE-side registry default
      "-Dide.linux.hide.native.title=true",
      "-Djavax.xml.parsers.SAXParserFactory=com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl",
      // TODO this is not something the IDE decides, it should likely be reported by JBR (FileSystem.getRootDirectories)
      "-Didea.ohos.sandbox.roots=/storage/Users/currentUser;/data/storage/el2/base;/data/storage/el1/bundle;/data/service/hnp",
    )

  // The current OHOS setup is for development only, so its distribution includes these
  private val devVmOptions = listOf(
    "-XX:+AllowEnhancedClassRedefinition",
    "-Didea.is.internal=true",
    "-Didea.debug.mode=true",
    "-Dfus.internal.test.mode=true",
    // "-Xint",
    // "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
    // "-XX:PrintIdealGraphLevel=3", // needs a debug build of the JVM
    // "-Dsun.java2d.debugfonts=true",
    // comma-separated FQNs of IDE classes; also 'idea.log.debug.categories' and 'idea.log.trace.categories'
    // "-Didea.log.all.categories=",
  )

  override fun distributionFilesBuilt(arch: JvmArchitecture): List<Path> = listOf(getTarGzPath(arch)).filter { Files.exists(it) }

  override fun isRuntimeBundled(file: Path): Boolean = false
}

/** The app that [OhosDistributionBuilder] produces. */
fun getOhosAppDirectory(context: BuildContext, arch: JvmArchitecture): Path {
  return context.paths.artifactDir.resolve("${context.productProperties.getBaseArtifactName(context)}.ohos.${arch.name}")
}

private const val APP_DIR_MACRO = $$"$APP_DIR"
