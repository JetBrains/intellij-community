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

internal fun signAndBuildDmg(builtinModule: BuiltinModulesFileData?,
                             context: BuildContext,
                             customizer: MacDistributionCustomizer,
                             macHostProperties: MacHostProperties?,
                             macZip: Path,
                             jreArchivePath: Path?,
                             suffix: String,
                             notarize: Boolean) {
  var javaExePath: String? = null
  if (jreArchivePath != null) {
    javaExePath = "../${getJbrTopDir(jreArchivePath)}/Contents/Home/bin/java"
  }

  val productJson = generateMacProductJson(builtinModule, context, javaExePath)
  val zipRoot = getMacZipRoot(customizer, context)
  val installationDirectories = ArrayList<Path>()
  val installationArchives = ArrayList<Pair<Path, String>>(2)
  installationArchives.add(Pair(macZip, zipRoot))
  if (jreArchivePath != null) {
    installationArchives.add(Pair(jreArchivePath, ""))
  }
  validateProductJson(productJson, "Resources/", installationDirectories, installationArchives, context)

  val targetName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber) + suffix
  val sitFile = (if (customizer.publishArchive) context.paths.artifactDir else context.paths.tempDir).resolve("$targetName.sit")

  prepareMacZip(macZip, sitFile, productJson, zipRoot)

  val sign = !context.options.buildStepsToSkip.contains(BuildOptions.MAC_SIGN_STEP)
  if ((!sign || macHostProperties?.host == null) && SystemInfoRt.isMac) {
    buildLocally(sitFile, targetName, jreArchivePath, sign, notarize, customizer, context)
  }
  else if (!sign) {
    Span.current().addEvent("build step '${BuildOptions.MAC_SIGN_STEP}' is disabled")
  }
  else if (macHostProperties?.host == null ||
           macHostProperties.userName == null ||
           macHostProperties.password == null ||
           macHostProperties.codesignString == null) {
    throw IllegalStateException("Build step '${BuildOptions.MAC_SIGN_STEP}' is enabled, but macHostProperties were not provided. " +
                                "Probably you want to skip BuildOptions.MAC_SIGN_STEP step")
  }
  else {
    buildAndSignWithMacBuilderHost(sitFile, jreArchivePath, macHostProperties, notarize, customizer, context)
  }

  if (jreArchivePath != null && Files.exists(sitFile)) {
    context.bundledRuntime.checkExecutablePermissions(sitFile, zipRoot, OsFamily.MACOS)
  }
}

private fun buildAndSignWithMacBuilderHost(sitFile: Path,
                                           jreArchivePath: Path?,
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
  val jetSignClient = context.proprietaryBuildTools.signTool?.commandLineClient(context)
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
    appArchiveFile = sitFile, jreArchiveFile = jreArchivePath,
    communityHome = context.paths.communityHomeDir,
    artifactDir = Path.of(context.paths.artifacts),
    dmgImage = dmgImage,
    artifactBuilt = context::notifyArtifactWasBuilt,
    publishAppArchive = customizer.publishArchive,
    jetSignClient = jetSignClient
  )
}

private fun buildLocally(sitFile: Path,
                         targetName: String,
                         jreArchivePath: Path?,
                         sign: Boolean,
                         notarize: Boolean,
                         customizer: MacDistributionCustomizer,
                         context: BuildContext) {
  val tempDir = context.paths.tempDir.resolve(sitFile.fileName.toString().replace(".sit", ""))
  if (jreArchivePath != null || sign) {
    spanBuilder("bundle JBR and sign sit locally")
      .setAttribute("jreArchive", jreArchivePath.toString())
      .setAttribute("sitFile", sitFile.toString()).useWithScope {
        Files.createDirectories(tempDir)
        bundleRuntimeAndSignSitLocally(sitFile, tempDir, jreArchivePath, notarize, customizer, context)
      }
  }
  if (customizer.publishArchive) {
    context.notifyArtifactBuilt(sitFile)
  }
  context.executeStep("build DMG locally", BuildOptions.MAC_DMG_STEP) {
    buildDmgLocally(tempDir, targetName, customizer, context)
  }

  NioFiles.deleteRecursively(tempDir)
}

private fun bundleRuntimeAndSignSitLocally(sourceFile: Path,
                                           tempDir: Path,
                                           jreArchivePath: Path?,
                                           notarize: Boolean,
                                           customizer: MacDistributionCustomizer,
                                           context: BuildContext) {
  val targetFile = tempDir.resolve(sourceFile.fileName)
  Files.copy(sourceFile, targetFile)
  if (jreArchivePath != null) {
    Files.copy(jreArchivePath, tempDir.resolve(jreArchivePath.fileName))
  }

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
      context.proprietaryBuildTools.macHostProperties?.codesignString ?: "",
      (jreArchivePath?.fileName?.toString() ?: "no-jdk"),
      if (notarize) "yes" else "no",
      customizer.bundleIdentifier,
      customizer.publishArchive.toString(), // compress-input
      context.proprietaryBuildTools.signTool?.commandLineClient(context)?.toString() ?: "null"
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
