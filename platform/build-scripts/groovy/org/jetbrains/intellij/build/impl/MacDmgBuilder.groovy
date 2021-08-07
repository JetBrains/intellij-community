// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtilRt
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.tools.ant.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.MacHostProperties
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import static com.intellij.openapi.util.Pair.pair

@CompileStatic
final class MacDmgBuilder {
  private final BuildContext buildContext
  private final AntBuilder ant
  private final String artifactsPath
  private final MacHostProperties macHostProperties
  private final String remoteDir
  private final MacDistributionCustomizer customizer
  private static final def ENV_FOR_MAC_BUILDER = ['ARTIFACTORY_URL']

  private MacDmgBuilder(BuildContext buildContext, MacDistributionCustomizer customizer, String remoteDir, MacHostProperties macHostProperties) {
    this.customizer = customizer
    this.buildContext = buildContext
    ant = buildContext.ant
    artifactsPath = buildContext.paths.artifacts
    this.macHostProperties = macHostProperties
    this.remoteDir = remoteDir
  }

  static void signBinaryFiles(BuildContext buildContext, MacDistributionCustomizer customizer, MacHostProperties macHostProperties, @NotNull Path macDistPath) {
    MacDmgBuilder dmgBuilder = createInstance(buildContext, customizer, macHostProperties)
    dmgBuilder.doSignBinaryFiles(macDistPath)
  }

  static void signAndBuildDmg(BuildContext buildContext, MacDistributionCustomizer customizer,
                              MacHostProperties macHostProperties, String macZipPath, String macAdditionalDirPath,
                              String jreArchivePath, String suffix, boolean notarize) {
    MacDmgBuilder dmgBuilder = createInstance(buildContext, customizer, macHostProperties)
    dmgBuilder.doSignAndBuildDmg(macZipPath, macAdditionalDirPath, jreArchivePath, suffix, notarize)
  }

  private static MacDmgBuilder createInstance(BuildContext buildContext, MacDistributionCustomizer customizer, MacHostProperties macHostProperties) {
    BuildUtils.defineFtpTask(buildContext)
    BuildUtils.defineSshTask(buildContext)

    String currentDateTimeString = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '-')
    int randomSeed = new Random().nextInt(Integer.MAX_VALUE)
    String remoteDir = "intellij-builds/${buildContext.fullBuildNumber}-${currentDateTimeString}-${randomSeed}"

    new MacDmgBuilder(buildContext, customizer, remoteDir, macHostProperties)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void doSignBinaryFiles(@NotNull Path macDistPath) {
    ftpAction("mkdir") {}
    ftpAction("put", false, "777") {
      ant.fileset(file: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts/signbin.sh")
    }

    Path signedFilesDir = Paths.get("$buildContext.paths.temp/signed-files")
    Files.createDirectories(signedFilesDir)

    List<String> failedToSign = []
    customizer.binariesToSign.each { relativePath ->
      buildContext.messages.progress("Signing $relativePath")
      Path fullPath = macDistPath.resolveSibling(relativePath)
      ftpAction("put") {
        ant.fileset(file: fullPath.toString())
      }
      FileUtil.delete(fullPath)
      String fileName = fullPath.fileName.toString()
      sshExec("$remoteDir/signbin.sh \"$fileName\" ${macHostProperties.userName}" +
              " ${macHostProperties.password} \"${this.macHostProperties.codesignString}\"", "signbin-${fileName}.log")

      ftpAction("get", true, null, 3) {
        ant.fileset(dir: signedFilesDir.toString()) {
          ant.include(name: '**/' + fileName)
        }
      }

      Path file = signedFilesDir.resolve(fileName)
      if (Files.exists(file)) {
        ant.move(file: file.toString(), tofile: fullPath)
      }
      else {
        failedToSign << relativePath
      }
    }

    deleteRemoteDir()
    if (!failedToSign.empty) {
      buildContext.messages.error("Failed to sign files: $failedToSign")
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void doSignAndBuildDmg(String macZipPath, String macAdditionalDirPath, String jreArchivePath, String suffix, boolean notarize) {
    String javaExePath = null
    if (jreArchivePath != null) {
      String rootDir = buildContext.bundledJreManager.jbrRootDir(new File(jreArchivePath)) ?: 'jdk'
      javaExePath = "../${rootDir}/Contents/Home/bin/java"
    }

    Path productJsonDir = buildContext.paths.tempDir.resolve("mac.dist.product-info.json.dmg$suffix")
    MacDistributionBuilder.generateProductJson(buildContext, productJsonDir, javaExePath)

    def zipRoot = MacDistributionBuilder.getZipRoot(buildContext, customizer)
    def installationDirectories = []
    def installationArchives = [pair(macZipPath, zipRoot)]
    if (macAdditionalDirPath != null) {
      installationDirectories += macAdditionalDirPath
    }
    if (jreArchivePath != null) {
      installationArchives += pair(jreArchivePath, "")
    }
    new ProductInfoValidator(buildContext).validateInDirectory(productJsonDir, "Resources/", installationDirectories, installationArchives)

    def targetName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber) + suffix
    def sitFile = new File(artifactsPath, "${targetName}.sit")
    ant.copy(file: macZipPath, tofile: sitFile.path)
    ant.zip(destfile: sitFile.path, update: true) {
      zipfileset(dir: productJsonDir.toString(), prefix: zipRoot)

      if (macAdditionalDirPath != null) {
        zipfileset(dir: macAdditionalDirPath, prefix: zipRoot)
      }
    }

    def signMacArtifacts = !buildContext.options.buildStepsToSkip.contains(BuildOptions.MAC_SIGN_STEP)
    if (signMacArtifacts || !isMac()) {
      ftpAction("mkdir") {}
      try {
        signMacZip(sitFile, jreArchivePath, notarize)

        if (customizer.publishArchive) {
          buildContext.notifyArtifactBuilt(sitFile.path)
        }
        buildContext.executeStep("Build .dmg artifact for macOS", BuildOptions.MAC_DMG_STEP) {
          buildDmg(targetName)
        }
      }
      finally {
        deleteRemoteDir()
      }
    }
    else {
      if (jreArchivePath != null || signMacArtifacts) {
        bundleJBRAndSignSitLocally(sitFile, jreArchivePath)
      }
      if (customizer.publishArchive) {
        buildContext.notifyArtifactBuilt(sitFile.path)
      }
      buildContext.executeStep("Build .dmg artifact for macOS", BuildOptions.MAC_DMG_STEP) {
        buildDmgLocally(sitFile, targetName)
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void bundleJBRAndSignSitLocally(File targetFile, String jreArchivePath) {
    buildContext.messages.progress("Bundling JBR")
    File tempDir = new File(new File(buildContext.paths.temp, targetFile.getName()), "mac.dist.bundled.jre")
    tempDir.mkdirs()
    ant.copy(todir: tempDir) {
      ant.fileset(file: targetFile.path)
      if (jreArchivePath != null) {
        ant.fileset(file: jreArchivePath)
      }
    }
    ant.copy(todir: tempDir, file: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts/signapp.sh")
    ant.chmod(file: new File(tempDir, "signapp.sh"), perm: "777")
    List<String> args = [targetFile.name,
                         buildContext.fullBuildNumber,
                         "\"\"",
                         "\"\"",
                         "\"\"",
                         jreArchivePath != null ? '"' + PathUtilRt.getFileName(jreArchivePath) + '"' : "no-jdk",
                         "no",
                         customizer.bundleIdentifier,
    ]
    ant.exec(dir: tempDir, command: "./signapp.sh ${args.join(" ")}")
    ant.move(todir: artifactsPath, file: new File(tempDir, targetFile.name))
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildDmgLocally(File sitFile, String targetFileName){
    File tempDir = new File(buildContext.paths.temp, "mac.dist.dmg")
    tempDir.mkdirs()
    buildContext.messages.progress("Building ${targetFileName}.dmg")
    def dmgImagePath = (buildContext.applicationInfo.isEAP ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath
    def dmgImageCopy = "$tempDir/${buildContext.fullBuildNumber}.png"
    ant.copy(file: dmgImagePath, tofile: dmgImageCopy)
    ant.copy(file: sitFile, todir: tempDir)
    ant.copy(todir: tempDir) {
      ant.fileset(dir: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts") {
        include(name: "makedmg.sh")
        include(name: "create-dmg.sh")
        include(name: "makedmg-locally.sh")
      }
    }
    ant.chmod(file: new File(tempDir, "makedmg.sh"), perm: "777")

    ant.exec(dir: tempDir, command: "sh ./makedmg-locally.sh ${targetFileName} ${buildContext.fullBuildNumber}")
    def dmgFilePath = "$artifactsPath/${targetFileName}.dmg"
    ant.copy(tofile: dmgFilePath) {
      ant.fileset(dir: tempDir) {
        include(name: "**/${targetFileName}.dmg")
      }
    }
    if (!new File(dmgFilePath).exists()) {
      buildContext.messages.error("Failed to build .dmg file")
    }
    buildContext.notifyArtifactBuilt(dmgFilePath)
  }

  static boolean isMac() {
    final String osName = System.properties['os.name']
    return osName.toLowerCase().startsWith('mac')
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildDmg(String targetFileName) {
    buildContext.messages.progress("Building ${targetFileName}.dmg")
    Path tempDir = buildContext.paths.tempDir.resolve("files-for-dmg-$targetFileName")
    Files.createDirectories(tempDir)

    Path dmgImageCopy = tempDir.resolve("${buildContext.fullBuildNumber}.png")
    String dmgImagePath = (buildContext.applicationInfo.isEAP ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath
    Files.copy(Paths.get(dmgImagePath), dmgImageCopy, StandardCopyOption.REPLACE_EXISTING)
    ftpAction("put") {
      ant.fileset(file: dmgImageCopy.toString())
    }

    ftpAction("put", false, "777") {
      ant.fileset(dir: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts") {
        include(name: "makedmg.sh")
        include(name: "makedmg.pl")
      }
    }

    sshExec("$remoteDir/makedmg.sh ${targetFileName} ${buildContext.fullBuildNumber}", "makedmg-${targetFileName}.log")
    ftpAction("get", true, null, 3) {
      ant.fileset(dir: artifactsPath) {
        include(name: "**/${targetFileName}.dmg")
      }
    }
    def dmgFilePath = "$artifactsPath/${targetFileName}.dmg"
    if (!new File(dmgFilePath).exists()) {
      buildContext.messages.error("Failed to build .dmg file")
    }
    buildContext.notifyArtifactBuilt(dmgFilePath)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void deleteRemoteDir() {
    ftpAction("delete") {
      ant.fileset() {
        include(name: "**")
      }
    }
    ftpAction("rmdir", true, null, 0, PathUtilRt.getParentPath(remoteDir)) {
      ant.fileset() {
        include(name: "${PathUtilRt.getFileName(remoteDir)}/**")
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private def signMacZip(File targetFile, String jreArchivePath, boolean notarize) {
    buildContext.messages.block("Signing ${targetFile.name}") {
      buildContext.messages.progress("Uploading ${targetFile} to ${macHostProperties.host}")
      ftpAction("put") {
        ant.fileset(file: targetFile.path)
        if (jreArchivePath != null) {
          ant.fileset(file: jreArchivePath)
        }
      }
      ftpAction("put", false, "777") {
        ant.fileset(dir: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts") {
          include(name: "entitlements.xml")
          include(name: "sign.sh")
          include(name: "notarize.sh")
          include(name: "signapp.sh")
        }
      }

      buildContext.messages.progress("Signing ${targetFile.name} on ${macHostProperties.host}")
      List<String> args = [targetFile.name,
                           buildContext.fullBuildNumber,
                           macHostProperties.userName,
                           macHostProperties.password,
                           "\"${macHostProperties.codesignString}\"",
                           jreArchivePath != null ? '"' + PathUtilRt.getFileName(jreArchivePath) + '"' : "no-jdk",
                           notarize ? "yes" : "no",
                           customizer.bundleIdentifier,
      ]
      def env = ''
      ENV_FOR_MAC_BUILDER.each {
        def value = System.getenv(it)
        if (value != null && !value.isEmpty()) {
          env += "$it=$value "
        }
      }

      sshExec("$env$remoteDir/signapp.sh ${args.join(" ")}", "signapp-${targetFile.name}.log")

      buildContext.messages.progress("Downloading signed ${targetFile.name} from ${macHostProperties.host}")
      ant.delete(file: targetFile.path)
      ftpAction("get", true, null, 3) {
        ant.fileset(dir: artifactsPath) {
          include(name: "**/${targetFile.name}")
        }
      }
      if (!targetFile.exists()) {
        buildContext.messages.error("Failed to sign ${targetFile.name}")
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void sshExec(String command, String logFileName) {
    try {
      ant.sshexec(
        host: this.macHostProperties.host,
        username: this.macHostProperties.userName,
        password: this.macHostProperties.password,
        trust: "yes",
        command: "set -eo pipefail;$command 2>&1 | tee $remoteDir/$logFileName"
      )
    }
    catch (BuildException e) {
      buildContext.messages.error("SSH command failed, details are available in $logFileName: $e.message", e)
    }
    finally {
      buildContext.messages.info("Retrieving log file from SSH command '$command' to $logFileName")
      ftpAction("get", true, null, 3) {
        ant.fileset(dir: artifactsPath) {
          include(name: '**/' + logFileName)
        }
      }
      buildContext.notifyArtifactWasBuilt(new File(artifactsPath, logFileName).toPath())
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  void ftpAction(String action, boolean binary = true, String chmod = null, int retriesAllowed = 0, String overrideRemoteDir = null, Closure filesets) {
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
    if (chmod != null) {
      args["chmod"] = chmod
    }
    ant.ftp(args, filesets)
  }
}