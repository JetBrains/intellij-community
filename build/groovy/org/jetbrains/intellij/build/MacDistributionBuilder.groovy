/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.intellij.build

import com.intellij.util.PathUtilRt
import org.apache.tools.ant.types.Path
import org.apache.tools.ant.util.SplitClassLoader
import org.codehaus.gant.GantBuilder
import org.jetbrains.jps.gant.JpsGantProjectBuilder

class MacDistributionBuilder {
  GantBuilder ant
  JpsGantProjectBuilder projectBuilder
  MacHostProperties macHostProperties
  /**
   * Path to a directory where IntelliJ IDEA community sources are located
   */
  String communityHome
  /**
   * Unique number for the current build (e.g. IC-142.239 for IDEA Community), it is used to create unique file name for files transferred to Mac host
   */
  String fullBuildNumber
  /**
   * Path to a directory where artifacts are stored
   */
  String artifactsPath
  /**
   * Path to the JDK 8 tar file to be bundled with the application
   */
  String customJDKTarPath
  /**
   * Path to an image which will be injected into .dmg file
   */
  String dmgImagePath


  private String remoteDir
  /**
   * Converts ${targetFileName}.mac.zip file to ${targetFileName}.dmg installer with signed application inside
   * @return path to created .dmg file
   */
  String signAndBuildDmg(String targetFileName) {
    defineTasks()
    remoteDir = "intellij-builds/$fullBuildNumber"
    def sitFilePath = "$artifactsPath/${targetFileName}.sit"
    ant.copy(file: "$artifactsPath/${targetFileName}.mac.zip", tofile: sitFilePath)
    ftpAction("mkdir") {
    }
    signMacZip(targetFileName, sitFilePath)
    return buildDmg(targetFileName)
  }

  private String buildDmg(String sitFileName) {
    projectBuilder.stage("building .dmg")
    def dmgImageCopy = "$artifactsPath/${fullBuildNumber}.png"
    ant.copy(file: dmgImagePath, tofile: dmgImageCopy)
    ftpAction("put") {
      ant.fileset(file: dmgImageCopy)
    }
    ant.delete(file: dmgImageCopy)

    ftpAction("put", false, "777") {
      ant.fileset(dir: "$communityHome/build/mac") {
        include(name: "makedmg.sh")
        include(name: "makedmg.pl")
      }
    }

    sshExec("$remoteDir/makedmg.sh ${sitFileName} ${fullBuildNumber}")
    ftpAction("get", true, null, 3) {
      ant.fileset(dir: artifactsPath) {
        include(name: "${sitFileName}.dmg")
      }
    }
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
    def dmgFilePath = "$artifactsPath/${sitFileName}.dmg"
    if (!new File(dmgFilePath).exists()) {
      projectBuilder.error("Failed to build .dmg file.")
    }
    return dmgFilePath
  }

  private def signMacZip(String sitFileName, String sitFilePath) {
    projectBuilder.stage("signing .mac.zip")

    if (new File(customJDKTarPath).exists()) {
      ftpAction("put") {
        ant.fileset(file: customJDKTarPath)
      }
    }
    else {
      projectBuilder.info("Custom JDK won't be bundled: $customJDKTarPath doesn't exist")
    }

    projectBuilder.info("Sending $sitFilePath")
    ftpAction("put") {
      ant.fileset(file: sitFilePath)
    }
    ant.delete(file: sitFilePath)
    ftpAction("put", false, "777") {
      ant.fileset(dir: "$communityHome/build/mac") {
        include(name: "signapp.sh")
      }
    }

    sshExec("$remoteDir/signapp.sh ${sitFileName} ${fullBuildNumber} ${macHostProperties.userName} ${macHostProperties.password} \"${macHostProperties.codesignString}\" \"${PathUtilRt.getFileName(customJDKTarPath)}\"")
    ftpAction("get", true, null, 3) {
      ant.fileset(dir: artifactsPath) {
        include(name: "${sitFileName}.sit")
      }
    }
    if (!new File(sitFilePath).exists()) {
      projectBuilder.error("Failed to build .sit file")
    }
  }

  static boolean tasksDefined
  private def defineTasks() {
    if (tasksDefined) return
    tasksDefined = true

    /*
      We need this to ensure that FTP task class isn't loaded by the main Ant classloader, otherwise Ant will try to load FTPClient class
      by the main Ant classloader as well and fail because 'commons-net-*.jar' isn't included to Ant classpath.
      Probably we could call FTPClient directly to avoid this hack.
     */
    def ftpTaskLoaderRef = "FTP_TASK_CLASS_LOADER";
    Path ftpPath = new Path(ant.project)
    ftpPath.createPathElement().setLocation(new File("$communityHome/lib/commons-net-3.3.jar"))
    ftpPath.createPathElement().setLocation(new File("$communityHome/lib/ant/lib/ant-commons-net.jar"))
    ant.project.addReference(ftpTaskLoaderRef, new SplitClassLoader(ant.project.getClass().getClassLoader(), ftpPath, ant.project,
                                                                    ["FTP", "FTPTaskConfig"] as String[]))
    ant.taskdef(name: "ftp", classname: "org.apache.tools.ant.taskdefs.optional.net.FTP", loaderRef: ftpTaskLoaderRef)

    def sshTaskLoaderRef = "SSH_TASK_CLASS_LOADER";
    Path pathSsh = new Path(ant.project)
    pathSsh.createPathElement().setLocation(new File("$communityHome/lib/jsch-0.1.52.jar"))
    pathSsh.createPathElement().setLocation(new File("$communityHome/lib/ant/lib/ant-jsch.jar"))
    ant.project.addReference(sshTaskLoaderRef, new SplitClassLoader(ant.project.getClass().getClassLoader(), pathSsh, ant.project,
                                                                    ["SSHExec", "SSHBase", "LogListener", "SSHUserInfo"] as String[]))
    ant.taskdef(name: "sshexec", classname: "org.apache.tools.ant.taskdefs.optional.ssh.SSHExec", loaderRef: sshTaskLoaderRef)
  }

  private void sshExec(String command) {
    ant.sshexec(
      host: macHostProperties.host,
      username: macHostProperties.userName,
      password: macHostProperties.password,
      trust: "yes",
      command: command
    )
  }

  def ftpAction(String action, boolean binary = true, String chmod = null, int retriesAllowed = 0, String overrideRemoteDir = null, Closure filesets) {
    Map<String, String> args = [
      server        : macHostProperties.host,
      userid        : macHostProperties.userName,
      password      : macHostProperties.password,
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
