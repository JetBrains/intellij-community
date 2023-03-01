// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.jps.api.GlobalOptions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.time.Duration.Companion.hours

internal val BuildContext.publishSitArchive: Boolean
  get() = !isStepSkipped(BuildOptions.MAC_SIT_PUBLICATION_STEP)

internal val BuildContext.signMacOsBinaries: Boolean
  get() = !isStepSkipped(BuildOptions.MAC_SIGN_STEP)

internal suspend fun signAndBuildDmg(builder: MacDistributionBuilder,
                                     context: BuildContext,
                                     customizer: MacDistributionCustomizer,
                                     macHostProperties: MacHostProperties?,
                                     macZip: Path,
                                     isRuntimeBundled: Boolean,
                                     suffix: String,
                                     arch: JvmArchitecture,
                                     notarize: Boolean) {
  require(Files.isRegularFile(macZip))

  val targetName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber) + suffix
  val sitFile = (if (context.publishSitArchive) context.paths.artifactDir else context.paths.tempDir).resolve("$targetName.sit")
  Files.move(macZip, sitFile, StandardCopyOption.REPLACE_EXISTING)

  if (context.isMacCodeSignEnabled) {
    context.proprietaryBuildTools.signTool.signFiles(files = listOf(sitFile),
                                                     context = context,
                                                     options = signingOptions("application/x-mac-app-zip", context))
  }

  val useNotaryRestApi = useNotaryRestApi()
  val useNotaryXcodeApi = notarize && !useNotaryRestApi
  if (notarize && useNotaryRestApi) {
    notarize(sitFile, context)
  }
  val useMacHost = macHostProperties?.host != null &&
                   macHostProperties.userName != null &&
                   macHostProperties.password != null
  if (useMacHost && (useNotaryXcodeApi || !context.isStepSkipped(BuildOptions.MAC_DMG_STEP))) {
    notarizeAndBuildDmgViaMacBuilderHost(
      sitFile, requireNotNull(macHostProperties),
      notarize = useNotaryXcodeApi,
      staple = notarize,
      customizer, context
    )
  }
  else {
    buildLocally(sitFile = sitFile, targetName = targetName, notarize = useNotaryXcodeApi, customizer = customizer, context = context)
  }
  check(Files.exists(sitFile)) {
    "$sitFile wasn't created"
  }
  if (context.publishSitArchive) {
    context.notifyArtifactBuilt(sitFile)
  }
  val zipRoot = getMacZipRoot(customizer, context)
  builder.checkExecutablePermissions(sitFile, zipRoot, isRuntimeBundled, arch)
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

private fun notarizeAndBuildDmgViaMacBuilderHost(sitFile: Path,
                                                 macHostProperties: MacHostProperties,
                                                 notarize: Boolean,
                                                 staple: Boolean,
                                                 customizer: MacDistributionCustomizer,
                                                 context: BuildContext) {
  val dmgImage = if (context.options.buildStepsToSkip.contains(BuildOptions.MAC_DMG_STEP)) {
    null
  }
  else {
    Path.of((if (context.applicationInfo.isEAP) customizer.dmgImagePathForEAP else null) ?: customizer.dmgImagePath)
  }
  notarizeAndBuildDmg(
    context = context,
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
    artifactBuilt = context::notifyArtifactBuilt,
    publishAppArchive = context.publishSitArchive,
    staple = staple
  )
}

private suspend fun buildLocally(sitFile: Path,
                                 targetName: String,
                                 notarize: Boolean,
                                 customizer: MacDistributionCustomizer,
                                 context: BuildContext) {
  val tempDir = context.paths.tempDir.resolve(sitFile.name.replace(".sit", ""))
  NioFiles.deleteRecursively(tempDir)
  Files.createDirectories(tempDir)
  try {
    if (notarize) notarizeSitLocally(sitFile, tempDir, customizer, context)
    buildDmgLocally(sitFile, tempDir, targetName, customizer, context)
  }
  finally {
    NioFiles.deleteRecursively(tempDir)
  }
}

private suspend fun notarizeSitLocally(sitFile: Path,
                                       tempDir: Path,
                                       customizer: MacDistributionCustomizer,
                                       context: BuildContext) {
  context.executeStep(spanBuilder("notarizing .sit locally").setAttribute("sitFile", "$sitFile"), BuildOptions.MAC_NOTARIZE_STEP) { span ->
    if (!SystemInfoRt.isMac) {
      span.addEvent(".sit can be notarized only on macOS")
      return@executeStep
    }

    val targetFile = tempDir.resolve(sitFile.fileName)
    Files.copy(sitFile, targetFile)
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
        targetFile.name,
        context.fullBuildNumber,
        "yes", // notarize
        customizer.bundleIdentifier,
        context.proprietaryBuildTools.macHostProperties?.codesignString ?: "",
        context.publishSitArchive.toString() // compress-input
      ),
      workingDir = tempDir,
      timeout = 3.hours,
    )
    Files.move(targetFile, sitFile, StandardCopyOption.REPLACE_EXISTING)
  }
}

private suspend fun buildDmgLocally(sitFile: Path,
                                    tempDir: Path,
                                    targetName: String,
                                    customizer: MacDistributionCustomizer,
                                    context: BuildContext) {
  context.executeStep(spanBuilder("build .dmg locally"), BuildOptions.MAC_DMG_STEP) {
    if (!SystemInfoRt.isMac) {
      it.addEvent(".dmg can be built only on macOS")
      return@executeStep
    }
    val exploded = tempDir.resolve("${context.fullBuildNumber}.exploded")
    if (!exploded.exists()) {
      exploded.createDirectories()
      runProcess(listOf("unzip", "-q", "-o", "$sitFile", "-d", "$exploded"))
    }
    val dmgImageCopy = tempDir.resolve("${context.fullBuildNumber}.png")
    Files.copy(Path.of((if (context.applicationInfo.isEAP) customizer.dmgImagePathForEAP else null) ?: customizer.dmgImagePath),
               dmgImageCopy)
    val scriptDir = context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
    Files.copy(scriptDir.resolve("makedmg.sh"), tempDir.resolve("makedmg.sh"),
               StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    NioFiles.setExecutable(tempDir.resolve("makedmg.sh"))
    Files.copy(scriptDir.resolve("makedmg.py"), tempDir.resolve("makedmg.py"),
               StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    val artifactDir = context.paths.artifactDir
    Files.createDirectories(artifactDir)
    val mountName = "$targetName-${UUID.randomUUID().toString().substring(1..4)}"
    val dmgFile = artifactDir.resolve("$targetName.dmg")
    val cleanUpExploded = true
    runProcess(
      args = listOf(
        "./makedmg.sh", mountName, context.fullBuildNumber,
        "$dmgFile", "${context.fullBuildNumber}.exploded",
        "$cleanUpExploded",
        "${context.signMacOsBinaries}" // isContentSigned
      ),
      additionalEnvVariables = mapOf(GlobalOptions.BUILD_DATE_IN_SECONDS to "${context.options.buildDateInSeconds}"),
      workingDir = tempDir
    )
    context.notifyArtifactBuilt(dmgFile)
  }
}
