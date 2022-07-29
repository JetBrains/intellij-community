// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.productInfo.validateProductJson
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.tasks.prepareMacZip
import org.jetbrains.intellij.build.tasks.signMacApp
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

internal val BuildContext.publishSitArchive get() = !options.buildStepsToSkip.contains(BuildOptions.MAC_SIT_PUBLICATION_STEP)

internal fun MacDistributionBuilder.signAndBuildDmg(builtinModule: BuiltinModulesFileData?,
                                                    context: BuildContext,
                                                    customizer: MacDistributionCustomizer,
                                                    macHostProperties: MacHostProperties?,
                                                    macZip: Path,
                                                    isRuntimeBundled: Boolean,
                                                    suffix: String,
                                                    notarize: Boolean) {
  require(macZip.exists()) {
    "Missing $macZip"
  }
  var javaExePath: String? = null
  if (isRuntimeBundled) {
    javaExePath = "../jbr/Contents/Home/bin/java"
  }

  val productJson = generateMacProductJson(builtinModule, context, javaExePath)
  val zipRoot = getMacZipRoot(customizer, context)
  val installationDirectories = ArrayList<Path>()
  val installationArchives = ArrayList<Pair<Path, String>>(2)
  installationArchives.add(Pair(macZip, zipRoot))
  validateProductJson(productJson, "Resources/", installationDirectories, installationArchives, context)

  val targetName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber) + suffix
  val sitFile = (if (context.publishSitArchive) context.paths.artifactDir else context.paths.tempDir).resolve("$targetName.sit")

  prepareMacZip(macZip, sitFile, productJson, zipRoot)

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

  require(Files.exists(sitFile)) {
    "$sitFile wasn't created"
  }
  checkExecutablePermissions(sitFile, zipRoot, isRuntimeBundled)
}

private fun buildAndSignWithMacBuilderHost(sitFile: Path,
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
  val jetSignClient = context.proprietaryBuildTools.signTool?.commandLineClient(context, OsFamily.MACOS, macHostProperties.architecture)
  check(jetSignClient != null) {
    "JetSign client is missing, cannot proceed with signing"
  }
  signMacApp(
    host = macHostProperties.host!!,
    user = macHostProperties.userName!!,
    password = macHostProperties.password!!,
    codesignString = macHostProperties.codesignString!!,
    fullBuildNumber = context.fullBuildNumber,
    notarize = notarize, bundleIdentifier = customizer.bundleIdentifier,
    appArchiveFile = sitFile, communityHome = context.paths.communityHomeDir,
    artifactDir = Path.of(context.paths.artifacts),
    dmgImage = dmgImage,
    artifactBuilt = context::notifyArtifactWasBuilt,
    publishAppArchive = context.publishSitArchive,
    jetSignClient = jetSignClient
  )
}

private fun buildLocally(sitFile: Path,
                         targetName: String,
                         sign: Boolean,
                         notarize: Boolean,
                         customizer: MacDistributionCustomizer,
                         context: BuildContext) {
  val tempDir = context.paths.tempDir.resolve(sitFile.fileName.toString().replace(".sit", ""))
  spanBuilder("bundle JBR and sign sit locally")
    .setAttribute("sitFile", sitFile.toString()).useWithScope {
      Files.createDirectories(tempDir)
      signSitLocally(sitFile, tempDir, sign, notarize, customizer, context)
    }
  if (context.publishSitArchive) {
    context.notifyArtifactBuilt(sitFile)
  }
  context.executeStep("build DMG locally", BuildOptions.MAC_DMG_STEP) {
    if (SystemInfoRt.isMac) {
      buildDmgLocally(tempDir, targetName, customizer, context)
    }
    else {
      Span.current().addEvent("DMG can be built only on macOS")
    }
  }

  NioFiles.deleteRecursively(tempDir)
}

private fun signSitLocally(sourceFile: Path,
                           tempDir: Path,
                           sign: Boolean,
                           notarize: Boolean,
                           customizer: MacDistributionCustomizer,
                           context: BuildContext) {
  val targetFile = tempDir.resolve(sourceFile.fileName)
  Files.copy(sourceFile, targetFile)

  val scripts = context.paths.communityHomeDir.communityRoot.resolve("platform/build-scripts/tools/mac/scripts")
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
      context.proprietaryBuildTools.signTool?.takeIf { sign }
        ?.commandLineClient(context, OsFamily.currentOs, JvmArchitecture.currentJvmArch)
        ?.toString() ?: "null"
    ),
    workingDir = tempDir,
    timeoutMillis = TimeUnit.HOURS.toMillis(3)
  )
  Files.move(targetFile, sourceFile, StandardCopyOption.REPLACE_EXISTING)
}

private fun buildDmgLocally(tempDir: Path, targetFileName: String, customizer: MacDistributionCustomizer, context: BuildContext) {
  val dmgImageCopy = tempDir.resolve("${context.fullBuildNumber}.png")
  Files.copy(Path.of((if (context.applicationInfo.isEAP) customizer.dmgImagePathForEAP else null) ?: customizer.dmgImagePath),
             dmgImageCopy)
  val scriptDir = context.paths.communityHomeDir.communityRoot.resolve("platform/build-scripts/tools/mac/scripts")
  sequenceOf("sh", "py").forEach {
    Files.copy(scriptDir.resolve("makedmg.$it"), tempDir.resolve("makedmg.$it"),
               StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  }
  val artifactDir = context.paths.artifactDir
  Files.createDirectories(artifactDir)
  val dmgFile = artifactDir.resolve("${targetFileName}.dmg")
  runProcess(args = listOf("sh", "makedmg.sh", targetFileName, context.fullBuildNumber, dmgFile.toString()), workingDir = tempDir)
  context.notifyArtifactBuilt(dmgFile)
}
