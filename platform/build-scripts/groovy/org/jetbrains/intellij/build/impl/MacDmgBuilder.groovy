// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.MacHostProperties
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Consumer

@CompileStatic
final class MacDmgBuilder {
  static void signAndBuildDmg(BuildContext context,
                              MacDistributionCustomizer customizer,
                              MacHostProperties macHostProperties,
                              @Nullable Path macZip,
                              Path macAdditionalDirPath,
                              @Nullable Path jreArchivePath,
                              String suffix,
                              boolean notarize) {
    Path artifactDir = Path.of(context.paths.artifacts)
    String javaExePath = null
    if (jreArchivePath != null) {
      String rootDir = BundledJreManager.jbrRootDir(jreArchivePath) ?: "jdk"
      javaExePath = "../${rootDir}/Contents/Home/bin/java"
    }

    byte[] productJson = MacDistributionBuilder.generateProductJson(context, javaExePath)
    String zipRoot = MacDistributionBuilder.getZipRoot(context, customizer)
    List<Path> installationDirectories = new ArrayList<>()
    List<Pair<Path, String>> installationArchives = new ArrayList<>(2)
    installationArchives.add(new Pair<>(macZip, zipRoot))
    if (macAdditionalDirPath != null) {
      installationDirectories.add(macAdditionalDirPath)
    }
    if (jreArchivePath != null) {
      installationArchives.add(new Pair<>(jreArchivePath, ""))
    }
    new ProductInfoValidator(context).validateInDirectory(productJson, "Resources/", installationDirectories, installationArchives)

    String targetName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber) + suffix
    Path sitFile = artifactDir.resolve(targetName + ".sit")

    BuildHelper buildHelper = BuildHelper.getInstance(context)
    buildHelper.prepareMacZip.invokeWithArguments(macZip, sitFile, productJson, macAdditionalDirPath, zipRoot)

    boolean signMacArtifacts = !context.options.buildStepsToSkip.contains(BuildOptions.MAC_SIGN_STEP)
    if (!signMacArtifacts && isMac()) {
      buildLocally(sitFile, targetName, jreArchivePath, signMacArtifacts, customizer, context)
      return
    }

    Path dmgImage = context.options.buildStepsToSkip.contains(BuildOptions.MAC_DMG_STEP)
      ? null
      : Path.of((context.applicationInfo.isEAP ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath)

    buildHelper.signMacApp.invokeWithArguments(
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
      }
    )

    if (customizer.publishArchive) {
      context.notifyArtifactBuilt(sitFile)
    }
  }

  private static void buildLocally(Path sitFile,
                                   String targetName,
                                   Path jreArchivePath,
                                   boolean signMacArtifacts,
                                   MacDistributionCustomizer customizer,
                                   BuildContext context) {
    BuildHelper buildHelper = BuildHelper.getInstance(context)
    if (jreArchivePath != null || signMacArtifacts) {
      buildHelper.span(TracerManager.spanBuilder("bundle JBR and sign sit locally")
                         .setAttribute("jreArchive", jreArchivePath.toString())
                         .setAttribute("sitFile", sitFile.toString()), new Runnable() {
        @Override
        void run() {
          bundleJBRAndSignSitLocally(sitFile, jreArchivePath, customizer, context)
        }
      })
    }
    if (customizer.publishArchive) {
      context.notifyArtifactBuilt(sitFile)
    }
    context.executeStep("build DMG locally", BuildOptions.MAC_DMG_STEP, new Runnable() {
      @Override
      void run() {
        buildDmgLocally(sitFile, targetName, customizer, context)
      }
    })
  }

  private static void bundleJBRAndSignSitLocally(Path targetFile,
                                                 Path jreArchivePath,
                                                 MacDistributionCustomizer customizer,
                                                 @NotNull BuildContext context) {
    Path tempDir = context.paths.tempDir.resolve(targetFile.fileName).resolve("mac.dist.bundled.jre")
    Files.createDirectories(tempDir)
    Files.copy(targetFile, tempDir.resolve(targetFile.fileName))
    if (jreArchivePath != null) {
      Files.copy(jreArchivePath, tempDir.resolve(jreArchivePath.fileName))
    }
    //noinspection SpellCheckingInspection
    Path signAppFile = tempDir.resolve("signapp.sh")
    //noinspection SpellCheckingInspection
    Files.copy(context.paths.communityHomeDir.resolve("platform/build-scripts/tools/mac/scripts/signapp.sh"), signAppFile,
               StandardCopyOption.COPY_ATTRIBUTES)
    //noinspection SpellCheckingInspection
    Files.setPosixFilePermissions(signAppFile, PosixFilePermissions.fromString("rwxrwxrwx"))
    List<String> args = [
      "./signapp.sh",
      targetFile.fileName.toString(),
      context.fullBuildNumber,
      "\"\"",
      "\"\"",
      "\"\"",
      (jreArchivePath == null ? "no-jdk" : '"' + jreArchivePath.fileName.toString() + '"'),
      "no",
      customizer.bundleIdentifier,
    ]
    BuildHelper.runProcess(context, args, tempDir)
    Path artifactDir = Path.of(context.paths.artifacts)
    Files.move(tempDir.resolve(targetFile.fileName), artifactDir.resolve(targetFile.fileName), StandardCopyOption.REPLACE_EXISTING)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private static void buildDmgLocally(Path sitFile, String targetFileName, MacDistributionCustomizer customizer, BuildContext context) {
    Path tempDir = context.paths.tempDir.resolve("mac.dist.dmg")
    Files.createDirectories(tempDir)
    String dmgImagePath = (context.applicationInfo.isEAP ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath
    Path dmgImageCopy = tempDir.resolve("${context.fullBuildNumber}.png")
    AntBuilder ant = context.ant
    ant.copy(file: dmgImagePath, tofile: dmgImageCopy.toString())
    ant.copy(file: sitFile.toString(), todir: tempDir)
    ant.copy(todir: tempDir) {
      ant.fileset(dir: "${context.paths.communityHome}/platform/build-scripts/tools/mac/scripts") {
        include(name: "makedmg.sh")
        include(name: "create-dmg.sh")
        include(name: "makedmg-locally.sh")
      }
    }
    //noinspection SpellCheckingInspection
    Files.setPosixFilePermissions(tempDir.resolve("makedmg.sh"), PosixFilePermissions.fromString("rwxrwxrwx"))

    Path artifactDir = Path.of(context.paths.artifacts)
    ant.exec(dir: tempDir, command: "sh ./makedmg-locally.sh ${targetFileName} ${context.fullBuildNumber}")
    Path dmgFile = artifactDir.resolve("${targetFileName}.dmg")
    ant.copy(tofile: dmgFile.toString()) {
      ant.fileset(dir: tempDir) {
        include(name: "**/${targetFileName}.dmg")
      }
    }
    if (Files.notExists(dmgFile)) {
      context.messages.error("Failed to build .dmg file")
    }
    context.notifyArtifactBuilt(dmgFile)
  }

  static boolean isMac() {
    return (System.properties.get("os.name") as String).toLowerCase().startsWith("mac")
  }
}