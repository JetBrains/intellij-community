// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import groovy.transform.CompileStatic
import kotlin.Pair
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidatorKt
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.ProcessKt
import org.jetbrains.intellij.build.tasks.SignKt

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@CompileStatic
final class MacDmgBuilder {
  static void signAndBuildDmg(@Nullable BuiltinModulesFileData builtinModule,
                              BuildContext context,
                              MacDistributionCustomizer customizer,
                              @Nullable MacHostProperties macHostProperties,
                              @Nullable Path macZip,
                              @Nullable Path jreArchivePath,
                              String suffix,
                              JvmArchitecture arch,
                              boolean notarize) {
    String javaExePath = null
    if (jreArchivePath != null) {
      String rootDir = BundledRuntimeImplKt.getJbrTopDir(jreArchivePath) ?: "jdk"
      javaExePath = "../${rootDir}/Contents/Home/bin/java"
    }

    String productJson = MacDistributionBuilder.generateProductJson(builtinModule, context, javaExePath)
    String zipRoot = MacDistributionBuilder.getZipRoot(context, customizer)
    List<Path> installationDirectories = new ArrayList<>()
    List<Pair<Path, String>> installationArchives = new ArrayList<>(2)
    installationArchives.add(new Pair<>(macZip, zipRoot))
    if (jreArchivePath != null) {
      installationArchives.add(new Pair<>(jreArchivePath, ""))
    }
    ProductInfoValidatorKt.validateProductJson(productJson, "Resources/", installationDirectories, installationArchives, context)

    String targetName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber) + suffix
    Path sitFile = (customizer.publishArchive ? context.paths.artifactDir : context.paths.tempDir).resolve(targetName + ".sit")

    SignKt.prepareMacZip(macZip, sitFile, productJson, zipRoot)

    boolean sign = !context.options.buildStepsToSkip.contains(BuildOptions.MAC_SIGN_STEP)
    if ((!sign || macHostProperties?.host == null) && SystemInfoRt.isMac) {
      buildLocally(sitFile, targetName, jreArchivePath, sign, notarize, customizer, context)
    }
    else if (!sign) {
      context.messages.info("Build step '${BuildOptions.MAC_SIGN_STEP}' is disabled")
    }
    else if (macHostProperties?.host == null ||
             macHostProperties?.userName == null ||
             macHostProperties?.password == null ||
             macHostProperties?.codesignString == null) {
      context.messages.error("Build step '${BuildOptions.MAC_SIGN_STEP}' is enabled, but machost properties were not provided. " +
                             "Probably you want to skip BuildOptions.MAC_SIGN_STEP step")
    }
    else {
      buildAndSignWithMacBuilderHost(sitFile, jreArchivePath, macHostProperties, notarize, customizer, context)
    }
    if (jreArchivePath != null && Files.exists(sitFile)) {
      context.bundledRuntime.checkExecutablePermissions(sitFile, zipRoot, OsFamily.MACOS)
      generateIntegrityManifest(sitFile, zipRoot, context, arch)
    }
  }

  private static void generateIntegrityManifest(Path sitFile, String sitRoot, BuildContext context, JvmArchitecture arch) {
    if (!context.options.buildStepsToSkip.contains(BuildOptions.REPAIR_UTILITY_BUNDLE_STEP)) {
      def tempSit = Files.createTempDirectory(context.paths.tempDir, "sit-")
      try {
        ProcessKt.runProcess(["7z", "x", "-bd", sitFile.toString()], tempSit, context.messages)
        RepairUtilityBuilder.generateManifest(context, tempSit.resolve(sitRoot), OsFamily.MACOS, arch)
      }
      finally {
        NioFiles.deleteRecursively(tempSit)
      }
    }
  }

  private static void buildAndSignWithMacBuilderHost(Path sitFile,
                                                     Path jreArchivePath,
                                                     @NotNull MacHostProperties macHostProperties, boolean notarize,
                                                     MacDistributionCustomizer customizer,
                                                     BuildContext context) {
    Path dmgImage = context.options.buildStepsToSkip.contains(BuildOptions.MAC_DMG_STEP)
      ? null
      : Path.of((context.applicationInfo.isEAP() ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath)
    Path jetSignClient = context.proprietaryBuildTools.signTool?.commandLineClient(context)
    if (jetSignClient == null) {
      context.messages.error("JetSign client is missing, cannot proceed with signing")
    }
    SignKt.signMacApp(
      macHostProperties.host, macHostProperties.userName, macHostProperties.password,
      macHostProperties.codesignString, context.fullBuildNumber,
      notarize, customizer.bundleIdentifier,
      sitFile, jreArchivePath,
      context.paths.communityHomeDir,
      Path.of(context.paths.artifacts),
      dmgImage,
      new Consumer<Path>() {
        @Override
        void accept(Path file) {
          context.notifyArtifactWasBuilt(file)
        }
      },
      customizer.publishArchive,
      jetSignClient
    )
  }

  private static void buildLocally(Path sitFile,
                                   String targetName,
                                   Path jreArchivePath,
                                   boolean sign, boolean notarize,
                                   MacDistributionCustomizer customizer,
                                   BuildContext context) {
    Path tempDir = context.paths.tempDir.resolve(sitFile.fileName.toString().replace(".sit", ""))
    if (jreArchivePath != null || sign) {
      BuildHelperKt.span(TraceManager.spanBuilder("bundle JBR and sign sit locally")
                         .setAttribute("jreArchive", jreArchivePath?.toString() ?: "no-jdk")
                         .setAttribute("sitFile", sitFile.toString()), new Runnable() {
        @Override
        void run() {
          Files.createDirectories(tempDir)
          bundleRuntimeAndSignSitLocally(sitFile, tempDir, jreArchivePath, notarize, customizer, context)
        }
      })
    }
    if (customizer.publishArchive) {
      context.notifyArtifactBuilt(sitFile)
    }
    context.executeStep("build DMG locally", BuildOptions.MAC_DMG_STEP, new Runnable() {
      @Override
      void run() {
        buildDmgLocally(tempDir, targetName, customizer, context)
      }
    })

    NioFiles.deleteRecursively(tempDir)
  }

  @SuppressWarnings('SpellCheckingInspection')
  private static void bundleRuntimeAndSignSitLocally(Path sourceFile,
                                                     Path tempDir,
                                                     Path jreArchivePath,
                                                     boolean notarize,
                                                     MacDistributionCustomizer customizer,
                                                     @NotNull BuildContext context) {
    Path targetFile = tempDir.resolve(sourceFile.fileName)
    Files.copy(sourceFile, targetFile)
    if (jreArchivePath != null) {
      Files.copy(jreArchivePath, tempDir.resolve(jreArchivePath.fileName))
    }
    Path scripts = context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
    Files.walk(scripts).withCloseable { stream ->
      stream.filter { Files.isRegularFile(it) }.forEach {
        Path script = tempDir.resolve(it.fileName)
        Files.copy(it, script, StandardCopyOption.COPY_ATTRIBUTES)
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxrwxrwx"))
      }
    }
    ProcessKt.runProcess(List.of(
      "./signapp.sh",
      targetFile.fileName.toString(),
      context.fullBuildNumber,
      // this host credentials, not required for signing via JetSign
      "", "",
      context.proprietaryBuildTools.macHostProperties?.codesignString ?: "null",
      (jreArchivePath == null ? "no-jdk" : jreArchivePath.fileName.toString()),
      notarize ? "yes" : "no",
      customizer.bundleIdentifier,
      customizer.publishArchive.toString(), // compress-input
      context.proprietaryBuildTools.signTool?.commandLineClient(context)?.toString() ?: "null"
    ), tempDir, null, TimeUnit.HOURS.toMillis(3))
    Files.move(targetFile, sourceFile, StandardCopyOption.REPLACE_EXISTING)
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static void buildDmgLocally(Path tempDir, String targetFileName, MacDistributionCustomizer customizer, BuildContext context) {
    Path dmgImageCopy = tempDir.resolve("${context.fullBuildNumber}.png")
    Files.copy(Path.of((context.applicationInfo.isEAP() ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath), dmgImageCopy)
    Path scriptDir = context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts")
    ["sh", "py"].each {
      Files.copy(scriptDir.resolve("makedmg.$it"), tempDir.resolve("makedmg.$it"),
                 StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    }
    Path artifactDir = Path.of(context.paths.artifacts)
    Files.createDirectories(artifactDir)
    Path dmgFile = artifactDir.resolve("${targetFileName}.dmg")
    ProcessKt.runProcess(List.of("sh", "makedmg.sh", targetFileName, context.fullBuildNumber, dmgFile.toString()), tempDir)
    context.notifyArtifactBuilt(dmgFile)
  }
}