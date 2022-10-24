// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("BlockingMethodInNonBlockingContext")

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntryPredicate
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.productInfo.validateProductJson
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.io.writeNewFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.Deflater
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.hours

internal val BuildContext.publishSitArchive: Boolean
  get() = !options.buildStepsToSkip.contains(BuildOptions.MAC_SIT_PUBLICATION_STEP)

internal suspend fun signAndBuildDmg(builder: MacDistributionBuilder,
                                     builtinModule: BuiltinModulesFileData?,
                                     context: BuildContext,
                                     customizer: MacDistributionCustomizer,
                                     macHostProperties: MacHostProperties?,
                                     macZip: Path,
                                     isRuntimeBundled: Boolean,
                                     suffix: String,
                                     arch: JvmArchitecture,
                                     notarize: Boolean) {
  check(macZip.exists()) {
    "Missing $macZip"
  }
  var javaExePath: String? = null
  if (isRuntimeBundled) {
    javaExePath = "../jbr/Contents/Home/bin/java"
  }

  val productJson = generateMacProductJson(builtinModule = builtinModule, arch = arch, javaExecutablePath = javaExePath, context = context)
  val zipRoot = getMacZipRoot(customizer, context)
  val installationDirectories = ArrayList<Path>()
  val installationArchives = ArrayList<Pair<Path, String>>(2)
  installationArchives.add(Pair(macZip, zipRoot))
  validateProductJson(jsonText = productJson,
                      relativePathToProductJson = "Resources/",
                      installationDirectories = installationDirectories,
                      installationArchives = installationArchives,
                      context = context)

  val targetName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber) + suffix
  val sitFile = (if (context.publishSitArchive) context.paths.artifactDir else context.paths.tempDir).resolve("$targetName.sit")

  prepareMacZip(macZip = macZip,
                sitFile = sitFile,
                productJson = productJson,
                zipRoot = zipRoot,
                compress = context.options.compressZipFiles)

  val sign = !context.options.buildStepsToSkip.contains(BuildOptions.MAC_SIGN_STEP)
  if (!sign) {
    Span.current().addEvent("build step '${BuildOptions.MAC_SIGN_STEP}' is disabled")
  }
  if (!sign || macHostProperties?.host == null && SystemInfoRt.isMac) {
    buildLocally(sitFile, targetName, sign, notarize, customizer, context)
  }
  else if (macHostProperties?.host == null ||
           macHostProperties.userName == null ||
           macHostProperties.password == null ||
           macHostProperties.codesignString == null) {
    throw IllegalStateException("Build step '${BuildOptions.MAC_SIGN_STEP}' is enabled, but macHostProperties were not provided. " +
                                "Probably you want to skip BuildOptions.MAC_SIGN_STEP step")
  }
  else {
    buildAndSignWithMacBuilderHost(sitFile, macHostProperties, notarize, customizer, context)
  }

  check(Files.exists(sitFile)) {
    "$sitFile wasn't created"
  }
  builder.checkExecutablePermissions(sitFile, zipRoot, isRuntimeBundled)
  if (isRuntimeBundled) {
    generateIntegrityManifest(sitFile, zipRoot, context, arch)
  }
}

private suspend fun generateIntegrityManifest(sitFile: Path, sitRoot: String, context: BuildContext, arch: JvmArchitecture) {
  if (!context.options.buildStepsToSkip.contains(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP)) {
    val tempSit = Files.createTempDirectory(context.paths.tempDir, "sit-")
    try {
      withContext(Dispatchers.IO) {
        runProcess(args = listOf("7z", "x", "-bd", sitFile.toString()), workingDir = tempSit)
      }
      RepairUtilityBuilder.generateManifest(context, tempSit.resolve(sitRoot), OsFamily.MACOS, arch)
    }
    finally {
      withContext(Dispatchers.IO + NonCancellable) {
        NioFiles.deleteRecursively(tempSit)
      }
    }
  }
}

private suspend fun buildAndSignWithMacBuilderHost(sitFile: Path,
                                                   macHostProperties: MacHostProperties,
                                                   notarize: Boolean,
                                                   customizer: MacDistributionCustomizer,
                                                   context: BuildContext) {
  val dmgImage = if (context.options.buildStepsToSkip.contains(BuildOptions.MAC_DMG_STEP)) {
    null
  }
  else {
    Path.of((if (context.applicationInfo.isEAP) customizer.dmgImagePathForEAP else null) ?: customizer.dmgImagePath)
  }
  val jetSignClient = context.proprietaryBuildTools.signTool.commandLineClient(context, OsFamily.MACOS, macHostProperties.architecture)
  check(jetSignClient != null) {
    "JetSign client is missing, cannot proceed with signing"
  }
  signMacApp(
    host = macHostProperties.host!!,
    user = macHostProperties.userName!!,
    password = macHostProperties.password!!,
    codesignString = macHostProperties.codesignString!!,
    fullBuildNumber = context.fullBuildNumber,
    notarize = notarize,
    bundleIdentifier = customizer.bundleIdentifier,
    appArchiveFile = sitFile,
    communityHome = context.paths.communityHomeDirRoot,
    artifactDir = Path.of(context.paths.artifacts),
    dmgImage = dmgImage,
    artifactBuilt = context::notifyArtifactWasBuilt,
    publishAppArchive = context.publishSitArchive,
    jetSignClient = jetSignClient
  )
}

private suspend fun buildLocally(sitFile: Path,
                                 targetName: String,
                                 sign: Boolean,
                                 notarize: Boolean,
                                 customizer: MacDistributionCustomizer,
                                 context: BuildContext) {
  val tempDir = context.paths.tempDir.resolve(sitFile.fileName.toString().replace(".sit", ""))
  if (SystemInfoRt.isWindows) {
    Span.current().addEvent("Currently sit cannot be signed on Windows")
  }
  else {
    spanBuilder("bundle JBR and sign sit locally")
      .setAttribute("sitFile", sitFile.toString()).useWithScope {
        Files.createDirectories(tempDir)
        signSitLocally(sitFile, tempDir, sign, notarize, customizer, context)
      }
  }
  if (context.publishSitArchive) {
    context.notifyArtifactBuilt(sitFile)
  }
  context.executeStep(spanBuilder("build DMG locally"), BuildOptions.MAC_DMG_STEP) {
    if (SystemInfoRt.isMac) {
      buildDmgLocally(tempDir, targetName, customizer, context)
    }
    else {
      Span.current().addEvent("DMG can be built only on macOS")
    }
  }

  NioFiles.deleteRecursively(tempDir)
}

private suspend fun signSitLocally(sourceFile: Path,
                                   tempDir: Path,
                                   sign: Boolean,
                                   notarize: Boolean,
                                   customizer: MacDistributionCustomizer,
                                   context: BuildContext) {
  val targetFile = tempDir.resolve(sourceFile.fileName)
  Files.copy(sourceFile, targetFile)

  val scripts = context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
  Files.walk(scripts).use { stream ->
    stream
      .filter { Files.isRegularFile(it) }
      .forEach {
        val script = tempDir.resolve(it.fileName)
        Files.copy(it, script, StandardCopyOption.COPY_ATTRIBUTES)
        @Suppress("SpellCheckingInspection")
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxrwxrwx"))
      }
  }
  runProcess(
    args = listOf(
      "./signapp.sh",
      targetFile.fileName.toString(),
      context.fullBuildNumber,
      // this host credentials, not required for signing via JetSign
      "",
      "",
      context.proprietaryBuildTools.macHostProperties?.codesignString?.takeIf { sign } ?: "",
      if (notarize) "yes" else "no",
      customizer.bundleIdentifier,
      context.publishSitArchive.toString(), // compress-input
      context.proprietaryBuildTools.signTool.takeIf { sign }
        ?.commandLineClient(context, OsFamily.currentOs, JvmArchitecture.currentJvmArch)
        ?.toString() ?: "null"
    ),
    workingDir = tempDir,
    timeout = 3.hours,
  )
  Files.move(targetFile, sourceFile, StandardCopyOption.REPLACE_EXISTING)
}

private suspend fun buildDmgLocally(tempDir: Path, targetFileName: String, customizer: MacDistributionCustomizer, context: BuildContext) {
  val dmgImageCopy = tempDir.resolve("${context.fullBuildNumber}.png")
  Files.copy(Path.of((if (context.applicationInfo.isEAP) customizer.dmgImagePathForEAP else null) ?: customizer.dmgImagePath),
             dmgImageCopy)
  val scriptDir = context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
  Files.copy(scriptDir.resolve("makedmg.sh"), tempDir.resolve("makedmg.sh"),
             StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  Files.copy(scriptDir.resolve("makedmg.py"), tempDir.resolve("makedmg.py"),
             StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  val artifactDir = context.paths.artifactDir
  Files.createDirectories(artifactDir)
  val dmgFile = artifactDir.resolve("${targetFileName}.dmg")
  runProcess(args = listOf("./makedmg.sh", targetFileName, context.fullBuildNumber, dmgFile.toString()), workingDir = tempDir)
  context.notifyArtifactBuilt(dmgFile)
}

// our zip for JARs, but here we need to support file permissions - that's why apache compress is used
private fun prepareMacZip(macZip: Path, sitFile: Path, productJson: String, zipRoot: String, compress: Boolean) {
  Files.newByteChannel(macZip, StandardOpenOption.READ).use { sourceFileChannel ->
    ZipFile(sourceFileChannel).use { zipFile ->
      writeNewFile(sitFile) { targetFileChannel ->
        NoDuplicateZipArchiveOutputStream(targetFileChannel, compress = compress).use { out ->
          // file is used only for transfer to mac builder
          out.setLevel(Deflater.BEST_SPEED)
          out.setUseZip64(Zip64Mode.Never)

          // exclude existing product-info.json as a custom one will be added
          val productJsonZipPath = "$zipRoot/Resources/product-info.json"
          zipFile.copyRawEntries(out, ZipArchiveEntryPredicate { it.name != productJsonZipPath })

          out.entry(productJsonZipPath, productJson.encodeToByteArray())
        }
      }
    }
  }
}

