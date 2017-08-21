/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.util.PathUtilRt
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.types.Path
import org.apache.tools.ant.util.SplitClassLoader
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.MacHostProperties

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MacDmgBuilder {
  private final BuildContext buildContext
  private final AntBuilder ant
  private final String artifactsPath
  private final MacHostProperties macHostProperties
  private final String remoteDir
  private final MacDistributionCustomizer customizer

  private MacDmgBuilder(BuildContext buildContext, MacDistributionCustomizer customizer, String remoteDir, MacHostProperties macHostProperties) {
    this.customizer = customizer
    this.buildContext = buildContext
    ant = buildContext.ant
    artifactsPath = buildContext.paths.artifacts
    this.macHostProperties = macHostProperties
    this.remoteDir = remoteDir
  }

  static void signBinaryFiles(BuildContext buildContext, MacDistributionCustomizer customizer, MacHostProperties macHostProperties, String macDistPath) {
    def dmgBuilder = createInstance(buildContext, customizer, macHostProperties)
    dmgBuilder.doSignBinaryFiles(macDistPath)
  }

  static void signAndBuildDmg(BuildContext buildContext, MacDistributionCustomizer customizer, MacHostProperties macHostProperties, String macZipPath) {
    MacDmgBuilder dmgBuilder = createInstance(buildContext, customizer, macHostProperties)
    def jreArchivePath = buildContext.bundledJreManager.findMacJreArchive()
    if (jreArchivePath != null) {
      dmgBuilder.doSignAndBuildDmg(macZipPath, jreArchivePath)
    }
    else {
      buildContext.messages.info("Skipping building macOS distribution with bundled JRE because JRE archive is missing")
    }
    if (buildContext.options.buildDmgWithoutBundledJre) {
      dmgBuilder.doSignAndBuildDmg(macZipPath, null)
    }
  }

  private static MacDmgBuilder createInstance(BuildContext buildContext, MacDistributionCustomizer customizer, MacHostProperties macHostProperties) {
    defineTasks(buildContext.ant, "${buildContext.paths.communityHome}/lib")

    String currentDateTimeString = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '-')
    int randomSeed = new Random().nextInt(Integer.MAX_VALUE)
    String remoteDir = "intellij-builds/${buildContext.fullBuildNumber}-${currentDateTimeString}-${randomSeed}"

    new MacDmgBuilder(buildContext, customizer, remoteDir, macHostProperties)
  }

  private void doSignBinaryFiles(String macDistPath) {
    ftpAction("mkdir") {}
    ftpAction("put", false, "777") {
      ant.fileset(file: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts/signbin.sh")
    }

    String signedFilesDir = "$buildContext.paths.temp/signed-files"
    ant.mkdir(dir: signedFilesDir)

    List<String> failedToSign = []
    customizer.binariesToSign.each { relativePath ->
      buildContext.messages.progress("Signing $relativePath")
      def fullPath = "$macDistPath/$relativePath"
      ftpAction("put") {
        ant.fileset(file: fullPath)
      }
      ant.delete(file: fullPath)
      def fileName = PathUtilRt.getFileName(fullPath)
      sshExec("$remoteDir/signbin.sh \"$fileName\" ${macHostProperties.userName}" +
              " ${macHostProperties.password} \"${this.macHostProperties.codesignString}\"", "signbin.log")

      ftpAction("get", true, null, 3) {
        ant.fileset(dir: signedFilesDir) {
          ant.include(name: fileName)
        }
      }
      if (new File(signedFilesDir, fileName).exists()) {
        ant.move(file: "$signedFilesDir/$fileName", tofile: fullPath)
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

  private void doSignAndBuildDmg(String macZipPath, String jreArchivePath) {
    def suffix = jreArchivePath != null ? "" : "-no-jdk"
    String targetFileName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber) + suffix
    def sitFilePath = "$artifactsPath/${targetFileName}.sit"
    ant.copy(file: macZipPath, tofile: sitFilePath)
    ftpAction("mkdir") {}
    signMacZip(sitFilePath, targetFileName, jreArchivePath)
    buildDmg(targetFileName)
  }

  private void buildDmg(String targetFileName) {
    buildContext.messages.progress("Building ${targetFileName}.dmg")
    def dmgImageCopy = "$artifactsPath/${buildContext.fullBuildNumber}.png"
    def dmgImagePath = (buildContext.applicationInfo.isEAP ? customizer.dmgImagePathForEAP : null) ?: customizer.dmgImagePath
    ant.copy(file: dmgImagePath, tofile: dmgImageCopy)
    ftpAction("put") {
      ant.fileset(file: dmgImageCopy)
    }
    ant.delete(file: dmgImageCopy)

    ftpAction("put", false, "777") {
      ant.fileset(dir: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts") {
        include(name: "makedmg.sh")
        include(name: "makedmg.pl")
      }
    }

    sshExec("$remoteDir/makedmg.sh ${targetFileName} ${buildContext.fullBuildNumber}", "makedmg.log")
    ftpAction("get", true, null, 3) {
      ant.fileset(dir: artifactsPath) {
        include(name: "${targetFileName}.dmg")
      }
    }
    deleteRemoteDir()
    def dmgFilePath = "$artifactsPath/${targetFileName}.dmg"
    if (!new File(dmgFilePath).exists()) {
      buildContext.messages.error("Failed to build .dmg file")
    }
    buildContext.notifyArtifactBuilt(dmgFilePath)
  }

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

  private def signMacZip(String sitFilePath, String targetFileName, String jreArchivePath) {
    buildContext.messages.progress("Signing ${targetFileName}.sit")

    if (jreArchivePath != null) {
      ftpAction("put") {
        ant.fileset(file: jreArchivePath)
      }
    }

    buildContext.messages.progress("Sending $sitFilePath to ${this.macHostProperties.host}")
    ftpAction("put") {
      ant.fileset(file: sitFilePath)
    }
    ant.delete(file: sitFilePath)
    ftpAction("put", false, "777") {
      ant.fileset(dir: "${buildContext.paths.communityHome}/platform/build-scripts/tools/mac/scripts") {
        include(name: "signapp.sh")
      }
    }

    String helpFileName = customizer.helpId != null ? "${customizer.helpId}.help" : "no-help"
    String jreFileNameArgument = jreArchivePath != null ? " \"${PathUtilRt.getFileName(jreArchivePath)}\"" : ""
    sshExec("$remoteDir/signapp.sh ${targetFileName} ${buildContext.fullBuildNumber} ${this.macHostProperties.userName}"
              + " ${this.macHostProperties.password} \"${this.macHostProperties.codesignString}\" $helpFileName$jreFileNameArgument", "signapp.log")
    ftpAction("get", true, null, 3) {
      ant.fileset(dir: artifactsPath) {
        include(name: "${targetFileName}.sit")
      }
    }
    if (!new File(sitFilePath).exists()) {
      buildContext.messages.error("Failed to build .sit file")
    }
  }

  static boolean tasksDefined
  static private def defineTasks(AntBuilder ant, String communityLib) {
    if (tasksDefined) return
    tasksDefined = true

    /*
      We need this to ensure that FTP task class isn't loaded by the main Ant classloader, otherwise Ant will try to load FTPClient class
      by the main Ant classloader as well and fail because 'commons-net-*.jar' isn't included to Ant classpath.
      Probably we could call FTPClient directly to avoid this hack.
     */
    def ftpTaskLoaderRef = "FTP_TASK_CLASS_LOADER"
    Path ftpPath = new Path(ant.project)
    ftpPath.createPathElement().setLocation(new File("$communityLib/commons-net-3.3.jar"))
    ftpPath.createPathElement().setLocation(new File("$communityLib/ant/lib/ant-commons-net.jar"))
    ant.project.addReference(ftpTaskLoaderRef, new SplitClassLoader(ant.project.getClass().getClassLoader(), ftpPath, ant.project,
                                                                    ["FTP", "FTPTaskConfig"] as String[]))
    ant.taskdef(name: "ftp", classname: "org.apache.tools.ant.taskdefs.optional.net.FTP", loaderRef: ftpTaskLoaderRef)

    def sshTaskLoaderRef = "SSH_TASK_CLASS_LOADER"
    Path pathSsh = new Path(ant.project)
    pathSsh.createPathElement().setLocation(new File("$communityLib/jsch-0.1.54.jar"))
    pathSsh.createPathElement().setLocation(new File("$communityLib/ant/lib/ant-jsch.jar"))
    ant.project.addReference(sshTaskLoaderRef, new SplitClassLoader(ant.project.getClass().getClassLoader(), pathSsh, ant.project,
                                                                    ["SSHExec", "SSHBase", "LogListener", "SSHUserInfo"] as String[]))
    ant.taskdef(name: "sshexec", classname: "org.apache.tools.ant.taskdefs.optional.ssh.SSHExec", loaderRef: sshTaskLoaderRef)
  }

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
      buildContext.messages.info("SSH command failed, retrieving log file")
      ftpAction("get", true, null, 3) {
        ant.fileset(dir: artifactsPath) {
          include(name: logFileName)
        }
      }
      buildContext.notifyArtifactBuilt(new File(artifactsPath, logFileName).absolutePath)
      buildContext.messages.error("SSH command failed, details are available in $logFileName: $e.message", e)
    }
  }

  def ftpAction(String action, boolean binary = true, String chmod = null, int retriesAllowed = 0, String overrideRemoteDir = null, Closure filesets) {
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
