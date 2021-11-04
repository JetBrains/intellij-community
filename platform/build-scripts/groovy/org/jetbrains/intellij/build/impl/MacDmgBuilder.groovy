// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.tools.ant.BuildException
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Consumer
import java.util.function.Supplier

@CompileStatic
final class MacDmgBuilder {
  private final BuildContext buildContext
  private final Path artifactDir
  private final MacHostProperties macHostProperties
  private final String remoteDir
  private final MacDistributionCustomizer customizer

  private MacDmgBuilder(BuildContext buildContext,
                        MacDistributionCustomizer customizer,
                        String remoteDir,
                        MacHostProperties macHostProperties) {
    this.customizer = customizer
    this.buildContext = buildContext
    artifactDir = Path.of(buildContext.paths.artifacts)
    this.macHostProperties = macHostProperties
    this.remoteDir = remoteDir
  }

  static void signAndBuildDmg(BuildContext context,
                              MacDistributionCustomizer customizer,
                              MacHostProperties macHostProperties,
                              @Nullable Path macZip,
                              Path macAdditionalDirPath,
                              @Nullable Path jreArchivePath,
                              String suffix,
                              boolean notarize) {
    createInstance(context, customizer, macHostProperties)
      .doSignAndBuildDmg(macZip, macAdditionalDirPath, jreArchivePath, suffix, notarize, macHostProperties, context)
  }

  private static MacDmgBuilder createInstance(BuildContext buildContext, MacDistributionCustomizer customizer, MacHostProperties macHostProperties) {
    BuildUtils.defineFtpTask(buildContext)
    BuildUtils.defineSshTask(buildContext)

    String currentDateTimeString = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '-')
    String remoteDir = "intellij-builds/${buildContext.fullBuildNumber}-${currentDateTimeString}-${Integer.toString(new Random().nextInt(), 36)}"
    return new MacDmgBuilder(buildContext, customizer, remoteDir, macHostProperties)
  }

  private void doSignAndBuildDmg(Path macZip,
                                 @Nullable Path macAdditionalDir,
                                 @Nullable Path jreArchive,
                                 String suffix,
                                 boolean notarize,
                                 MacHostProperties macHostProperties,
                                 BuildContext context) {
    String javaExePath = null
    if (jreArchive != null) {
      String rootDir = BundledJreManager.jbrRootDir(jreArchive) ?: "jdk"
      javaExePath = "../${rootDir}/Contents/Home/bin/java"
    }

    byte[] productJson = MacDistributionBuilder.generateProductJson(context, javaExePath)

    String zipRoot = MacDistributionBuilder.getZipRoot(context, customizer)
    List<Path> installationDirectories = new ArrayList<>()
    List<Pair<Path, String>> installationArchives = new ArrayList<>(2)
    installationArchives.add(new Pair<>(macZip, zipRoot))
    if (macAdditionalDir != null) {
      installationDirectories.add(macAdditionalDir)
    }
    if (jreArchive != null) {
      installationArchives.add(new Pair<>(jreArchive, ""))
    }
    new ProductInfoValidator(context).validateInDirectory(productJson, "Resources/", installationDirectories, installationArchives)

    String targetName = context.productProperties.getBaseArtifactName(context.applicationInfo, context.buildNumber) + suffix
    Path sitFile = artifactDir.resolve(targetName + ".sit")

    BuildHelper buildHelper = BuildHelper.getInstance(context)
    buildHelper.prepareMacZip.invokeWithArguments(macZip, sitFile, productJson, macAdditionalDir, zipRoot)

    boolean signMacArtifacts = !context.options.buildStepsToSkip.contains(BuildOptions.MAC_SIGN_STEP)
    if (signMacArtifacts || !isMac()) {
      context.messages.block(TracerManager.spanBuilder("sign").setAttribute("file", sitFile.toString()), new Supplier<Void>() {
        @Override
        Void get() {
          buildHelper.signMacZip.invokeWithArguments(
            macHostProperties.host, macHostProperties.userName, macHostProperties.password,
            macHostProperties.codesignString, context.fullBuildNumber,
            notarize, customizer.bundleIdentifier,
            sitFile, jreArchive,
            context.paths.communityHomeDir,
            Path.of(context.paths.artifacts),
            new Consumer<Path>() {
              @Override
              void accept(Path file) {
                context.notifyArtifactWasBuilt(file)
              }
            }
          )
          return null
        }
      })
      if (customizer.publishArchive) {
        context.notifyArtifactBuilt(sitFile)
      }
      context.executeStep(TracerManager.spanBuilder("build .dmg artifact for macOS")
                            .setAttribute("name", targetName), BuildOptions.MAC_DMG_STEP, new Runnable() {
        @Override
        void run() {
          buildDmg(targetName, context)
        }
      })
    }
    else {
      if (jreArchive != null || signMacArtifacts) {
        buildHelper.span(TracerManager.spanBuilder("bundle JBR and sign sit locally")
                                                     .setAttribute("jreArchive", jreArchive.toString())
                                                     .setAttribute("sitFile", sitFile.toString()), new Runnable() {
          @Override
          void run() {
            bundleJBRAndSignSitLocally(sitFile, jreArchive, context)
          }
        })
      }
      if (customizer.publishArchive) {
        context.notifyArtifactBuilt(sitFile)
      }
      context.executeStep("build .dmg artifact for macOS", BuildOptions.MAC_DMG_STEP, new Runnable() {
        @Override
        void run() {
          buildDmgLocally(sitFile, targetName)
        }
      })
    }
  }

  private void bundleJBRAndSignSitLocally(Path targetFile, Path jreArchivePath, @NotNull BuildContext context) {
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
    Files.move(tempDir.resolve(targetFile.fileName), artifactDir.resolve(targetFile.fileName), StandardCopyOption.REPLACE_EXISTING)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildDmgLocally(Path sitFile, String targetFileName){
    Path tempDir = buildContext.paths.tempDir.resolve("mac.dist.dmg")
    Files.createDirectories(tempDir)
    buildContext.messages.progress("Building ${targetFileName}.dmg")
    String dmgImagePath = (buildContext.applicationInfo.isEAP ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath
    Path dmgImageCopy = tempDir.resolve("${buildContext.fullBuildNumber}.png")
    AntBuilder ant = buildContext.ant
    ant.copy(file: dmgImagePath, tofile: dmgImageCopy.toString())
    ant.copy(file: sitFile.toString(), todir: tempDir)
    ant.copy(todir: tempDir) {
      ant.fileset(dir: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts") {
        include(name: "makedmg.sh")
        include(name: "create-dmg.sh")
        include(name: "makedmg-locally.sh")
      }
    }
    //noinspection SpellCheckingInspection
    Files.setPosixFilePermissions(tempDir.resolve("makedmg.sh"), PosixFilePermissions.fromString("rwxrwxrwx"))

    ant.exec(dir: tempDir, command: "sh ./makedmg-locally.sh ${targetFileName} ${buildContext.fullBuildNumber}")
    Path dmgFile = artifactDir.resolve("${targetFileName}.dmg")
    ant.copy(tofile: dmgFile.toString()) {
      ant.fileset(dir: tempDir) {
        include(name: "**/${targetFileName}.dmg")
      }
    }
    if (Files.notExists(dmgFile)) {
      buildContext.messages.error("Failed to build .dmg file")
    }
    buildContext.notifyArtifactBuilt(dmgFile)
  }

  static boolean isMac() {
    final String osName = System.properties['os.name']
    return osName.toLowerCase().startsWith('mac')
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildDmg(String targetFileName, BuildContext context) {
    Path tempDir = context.paths.tempDir.resolve("files-for-dmg-$targetFileName")
    Files.createDirectories(tempDir)

    Path dmgImageCopy = tempDir.resolve("${context.fullBuildNumber}.png")
    Path dmgImage = Path.of((context.applicationInfo.isEAP ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath)
    Files.copy(dmgImage, dmgImageCopy, StandardCopyOption.REPLACE_EXISTING)
    AntBuilder ant = context.ant
    ftpAction("put") {
      ant.fileset(file: dmgImageCopy.toString())
    }

    ftpAction("put", false, "777") {
      ant.fileset(dir: "${context.paths.communityHome}/platform/build-scripts/tools/mac/scripts") {
        include(name: "makedmg.sh")
        include(name: "makedmg.pl")
      }
    }

    sshExec(context, "$remoteDir/makedmg.sh ${targetFileName} ${context.fullBuildNumber}", "makedmg-${targetFileName}.log")
    ftpAction("get", true, null, 3) {
      ant.fileset(dir: artifactDir.toString()) {
        include(name: "**/${targetFileName}.dmg")
      }
    }
    Path dmgFile = artifactDir.resolve("${targetFileName}.dmg")
    if (Files.notExists(dmgFile)) {
      context.messages.error("Failed to build .dmg file")
    }
    context.notifyArtifactBuilt(dmgFile)
  }


  @CompileStatic(TypeCheckingMode.SKIP)
  private void sshExec(BuildContext context, String command, String logFileName) {
    try {
      context.ant.sshexec(
        host: macHostProperties.host,
        username: macHostProperties.userName,
        password: macHostProperties.password,
        trust: "yes",
        command: "set -eo pipefail;$command 2>&1 | tee $remoteDir/$logFileName"
      )
    }
    catch (BuildException e) {
      context.messages.error("SSH command failed, details are available in $logFileName: $e.message", e)
    }
    finally {
      context.messages.info("Retrieving log file from SSH command '$command' to $logFileName")
      ftpAction("get", true, null, 3) {
        context.ant.fileset(dir: artifactDir.toString()) {
          include(name: '**/' + logFileName)
        }
      }
      context.notifyArtifactWasBuilt(artifactDir.resolve(logFileName))
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  void ftpAction(String action, boolean binary = true, String chmod = null, int retriesAllowed = 0, String overrideRemoteDir = null,
                 Closure filesets) {
    Map<String, String> args = [
      server        : this.macHostProperties.host,
      userid        : this.macHostProperties.userName,
      password      : this.macHostProperties.password,
      action        : action,
      remotedir     : overrideRemoteDir ?: remoteDir,
      binary        : binary ? "yes" : "no",
      passive       : "yes",
      retriesallowed: "$retriesAllowed"
    ]
    if (action == "delete" || action == "rmdir") {
      args["skipFailedTransfers"] = "yes"
    }
    if (chmod != null) {
      args["chmod"] = chmod
    }
    buildContext.ant.ftp(args, filesets)
  }
}