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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.*
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
  val useMacHost = !macHostProperties?.host.isNullOrBlank() &&
                   !macHostProperties?.userName.isNullOrBlank() &&
                   !macHostProperties?.password.isNullOrBlank()
  if (useMacHost && (useNotaryXcodeApi || !context.isStepSkipped(BuildOptions.MAC_DMG_STEP))) {
    notarizeAndBuildDmgViaMacBuilderHost(
      sitFile, requireNotNull(macHostProperties),
      notarize = useNotaryXcodeApi,
      staple = notarize,
      customizer, context
    )
    // for testing, to be removed
    val tempDir = Files.createTempDirectory(sitFile.nameWithoutExtension)
    val entrypoint = prepareDmgBuildScripts(context, customizer, tempDir, staple = notarize)
    publishDmgBuildScripts(context, entrypoint, tempDir)
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
    buildDmgLocally(sitFile, tempDir, targetName, customizer, context, staple = notarize)
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
                                    context: BuildContext,
                                    staple: Boolean) {
  context.executeStep(spanBuilder("build .dmg locally"), BuildOptions.MAC_DMG_STEP) {
    context.paths.artifactDir.createDirectories()
    val entrypoint = prepareDmgBuildScripts(context, customizer, tempDir, staple)
    if (!SystemInfoRt.isMac) {
      it.addEvent(".dmg can be built only on macOS")
      publishDmgBuildScripts(context, entrypoint, tempDir)
      return@executeStep
    }
    val tmpSit = Files.move(sitFile, tempDir.resolve(sitFile.fileName))
    runProcess(args = listOf("./${entrypoint.name}"), workingDir = tempDir, inheritOut = true)
    val dmgFile = tempDir.resolve("$targetName.dmg")
    check(dmgFile.exists()) {
      "$dmgFile wasn't created"
    }
    Files.move(dmgFile, context.paths.artifactDir.resolve(dmgFile.name), StandardCopyOption.REPLACE_EXISTING)
    context.notifyArtifactBuilt(dmgFile)
    Files.move(tmpSit, sitFile)
  }
}

private fun prepareDmgBuildScripts(context: BuildContext,
                                   customizer: MacDistributionCustomizer,
                                   tempDir: Path, staple: Boolean): Path {
  NioFiles.deleteRecursively(tempDir)
  tempDir.createDirectories()
  val dmgImageCopy = tempDir.resolve("${context.fullBuildNumber}.png")
  Files.copy(Path.of((if (context.applicationInfo.isEAP) customizer.dmgImagePathForEAP else null) ?: customizer.dmgImagePath),
             dmgImageCopy)
  val scriptsDir = context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
  Files.copy(scriptsDir.resolve("makedmg.sh"), tempDir.resolve("makedmg.sh"),
             StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  NioFiles.setExecutable(tempDir.resolve("makedmg.sh"))
  Files.copy(scriptsDir.resolve("makedmg.py"), tempDir.resolve("makedmg.py"),
             StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  Files.copy(scriptsDir.resolve("staple.sh"), tempDir.resolve("staple.sh"),
             StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
  val entrypoint = tempDir.resolve("build.sh")
  entrypoint.writeText(
    scriptsDir.resolve("build-template.sh").readText()
      .resolveTemplateVar("staple", "$staple")
      .resolveTemplateVar("appName", context.fullBuildNumber)
      .resolveTemplateVar("contentSigned", "${context.signMacOsBinaries}")
      .resolveTemplateVar("buildDateInSeconds", "${context.options.buildDateInSeconds}")
  )
  NioFiles.setExecutable(entrypoint)
  return entrypoint
}

private fun String.resolveTemplateVar(variable: String, value: String): String {
  val reference = "%$variable%"
  check(contains(reference)) {
    "No $reference is found in:\n'$this'"
  }
  return replace(reference, value)
}

private fun publishDmgBuildScripts(context: BuildContext, entrypoint: Path, tempDir: Path) {
  if (context.publishSitArchive) {
    val artifactDir = context.paths.artifactDir.resolve("macos-dmg-build")
    artifactDir.createDirectories()
    synchronized("$artifactDir".intern()) {
      tempDir.listDirectoryEntries().forEach {
        Files.copy(it, artifactDir.resolve(it.name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
      }
      val message = """
        To build .dmg(s):
        1. transfer .sit(s) to macOS host;
        2. transfer ${artifactDir.name}/ content to the same folder;
        3. execute ${entrypoint.name} from Terminal. 
        .dmg(s) will be built in the same folder.
      """.trimIndent()
      artifactDir.resolve("README.txt").writeText(message)
      context.messages.info(message)
      context.notifyArtifactBuilt(artifactDir)
    }
  }
}
