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

import com.intellij.openapi.util.SystemInfoRt
import org.codehaus.gant.GantBuilder
import org.jetbrains.jps.gant.JpsGantProjectBuilder
/**
 * @author nik
 */
class WinInstallerBuilder {
  GantBuilder ant
  JpsGantProjectBuilder projectBuilder
  ApplicationInfoProperties applicationInfo
  /**
   * Path to a directory where project sources are located. It will be used to replace 'BASE_DIR' in *.nsi files.
   */
  String baseDirectory
  /**
   * Path to a directory where IntelliJ IDEA community sources are located
   */
  String communityHome
  /**
   * Path to a directory where artifacts are stored
   */
  String artifactsPath
  /**
   * Path to a directory where temporary files can be stored
   */
  String sandboxPath
  /**
   * Prefix for the output file name. The value of buildNumber will be appended to this prefix.
   */
  String outNamePrefix
  /**
   * Short build number without product code (e.g. 142.239)
   */
  String buildNumber
  /**
   * Name which will be used for default config/system paths, should include the product name and version. E.g. for IntelliJ Ultimate 16 it is
   * 'IntelliJIdea16', so the settings and system files will be stored in $USER_HOME/.IntelliJIdea16 by default.
   */
  String systemSelector
  /**
   * Determines whether tools.jar should be included to the bundled JDK
   */
  boolean includeToolsJar = true
  boolean associateIpr = true
  /**
   * Path to a JDK 8 zip file which should be bundled with the application
   */
  String winJDKZipPath

  /**
   * Builds .exe installer. If build/lib/jet-sign.jar exists in baseDirectory it will be used to sign the created .exe file.
   *
   * @param pathsToInclude list of paths to directories which contents should be included to the product distribution
   * @param stringsFile path to *.nsi file where installers variables are defined (use build/conf/nsis/stringsCE.nsi as a reference)
   * @param pathsFile path to *.nsi file where installers path variables are defined (use build/conf/nsis/pathsCE.nsi as a reference)
   * @return path to the created installer file
   */
  def buildInstaller(List<String> pathsToInclude, String stringsFile, String pathsFile) {
    if (!SystemInfoRt.isWindows) {
      projectBuilder.warning("Windows installer can be built only under Windows")
      return null
    }

    projectBuilder.stage("Building Windows installer")
    String outFileName = "${outNamePrefix}${buildNumber}"
    ant.taskdef(name: "nsis", classname: "com.intellij.internalUtilities.ant.NsiFiles", classpath: "$communityHome/build/lib/NsiFiles.jar")

    def box = sandboxPath
    ant.mkdir(dir: "$box/bin")
    ant.mkdir(dir: "$box/nsiconf")

    if (winJDKZipPath != null) {
      ant.mkdir(dir: "$box/jre")
      ant.unzip(dest: "$box/jre", src: winJDKZipPath)

      ant.copy(todir: "$box/bin") {
        fileset(dir: "$box/jre/jre/bin") {
          include(name: "msvcr71.dll")
        }
      }
    }

    ant.copy(todir: "$box/nsiconf") {
      fileset(dir: "$communityHome/build/conf/nsis") {
        include(name: "*")
        exclude(name: "version*")
        exclude(name: "strings*")
        exclude(name: "paths*")
      }
    }

    if (applicationInfo.isEAP) {
      ant.copy(file: "$communityHome/build/conf/nsis/version.eap.nsi",
               tofile: "$box/nsiconf/version.nsi", overwrite: true)
    }
    else {
      ant.copy(file: "$communityHome/build/conf/nsis/version.nsi",
               tofile: "$box/nsiconf/version.nsi", overwrite: true)
    }
    ant.copy(file: pathsFile, toFile: "$box/nsiconf/paths.nsi", overwrite: true)

    ant.nsis(instfile: "$box/nsiconf/idea_win.nsh", uninstfile: "$box/nsiconf/unidea_win.nsh") {
      pathsToInclude.each {
        ant.fileset(dir: it, includes: "**/*") {
          exclude(name: "**/idea.properties")
          exclude(name: "**/*.vmoptions")
        }
      }
      ant.fileset(dir: box, includes: "bin/msvcr71.dll")
      if (winJDKZipPath != null) {
        ant.fileset(dir: box, includes: "jre/**/*")
        if (includeToolsJar) {
          ant.fileset(dir: box) {
            include(name: "jre/lib/tools.jar")
          }
        }
      }
    }

    ant.copy(file: stringsFile, toFile: "$box/nsiconf/strings.nsi", overwrite: true)
    ant.replace(file: "$box/nsiconf/strings.nsi") {
      replacefilter(token: "__VERSION_MAJOR__", value: applicationInfo.majorVersion)
      replacefilter(token: "__VERSION_MINOR__", value: applicationInfo.minorVersion)
    }

    ant.replace(file: "$box/nsiconf/version.nsi") {
      replacefilter(token: "__BUILD_NUMBER__", value: buildNumber)
      replacefilter(token: "__VERSION_MAJOR__", value: applicationInfo.majorVersion)
      replacefilter(token: "__VERSION_MINOR__", value: applicationInfo.minorVersion)
      replacefilter(token: "__MIN_UPGRADE_BUILD__", value: applicationInfo.installOver.minBuild)
      replacefilter(token: "__MAX_UPGRADE_BUILD__", value: applicationInfo.installOver.maxBuild)
      replacefilter(token: "__UPGRADE_VERSION__", value: applicationInfo.installOver.version)
      replacefilter(token: "__PRODUCT_PATHS_SELECTOR__", value: systemSelector)
    }

    ant.unzip(src: "$communityHome/build/tools/NSIS.zip", dest: box)
    ant.exec(command: "\"${box}/NSIS/makensis.exe\"" +
                      " /DBASE_DIR=\"$baseDirectory\"" +
                      " /DCOMMUNITY_DIR=\"$communityHome\"" +
                      " /DIPR=\"${associateIpr}\"" +
                      " /DOUT_FILE=\"${outFileName}\"" +
                      " /DOUT_DIR=\"$artifactsPath\"" +
                      " \"${box}/nsiconf/idea.nsi\"")

    def installerPath = "$artifactsPath/${outFileName}.exe"
    if (!new File(installerPath).exists()) {
      projectBuilder.error("Installer wasn't created.")
    }

    def signJarPath = "$baseDirectory/build/lib/jet-sign.jar"
    if (new File(signJarPath).exists()) {
      projectBuilder.stage("Signing $installerPath")
      ant.taskdef(name: "jet-sign", classname: "jetbrains.sign.JetSignTask") {
        classpath(path: signJarPath)
      }
      ant."jet-sign"() {
        ant.fileset(dir: artifactsPath) {
          include(name: "${outFileName}.exe")
        }
      }
      projectBuilder.stage("Signing done")
    }
    else {
      projectBuilder.warning("$signJarPath not found, installer won't be signed")
    }
    return installerPath
  }
}