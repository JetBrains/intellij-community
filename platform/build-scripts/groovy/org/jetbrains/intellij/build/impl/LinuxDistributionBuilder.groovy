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
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LinuxDistributionCustomizer

/**
 * @author nik
 */
class LinuxDistributionBuilder extends OsSpecificDistributionBuilder {
  private final LinuxDistributionCustomizer customizer
  private final File ideaProperties

  LinuxDistributionBuilder(BuildContext buildContext, LinuxDistributionCustomizer customizer, File ideaProperties) {
    super(BuildOptions.OS_LINUX, "Linux", buildContext)
    this.customizer = customizer
    this.ideaProperties = ideaProperties
  }

  @Override
  String copyFilesForOsDistribution() {
    String unixDistPath = "$buildContext.paths.buildOutputRoot/dist.unix"
    buildContext.messages.progress("Building distributions for Linux")
    buildContext.ant.copy(todir: "$unixDistPath/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/linux") {
        if (!buildContext.includeBreakGenLibraries()) {
          exclude(name: "libbreakgen*")
        }
      }
      if (buildContext.productProperties.yourkitAgentBinariesDirectoryPath != null) {
        fileset(dir: buildContext.productProperties.yourkitAgentBinariesDirectoryPath) {
          include(name: "libyjpagent-linux*.so")
        }
      }
    }
    buildContext.ant.copy(todir: "$unixDistPath/lib/libpty/linux") {
      fileset(dir: "$buildContext.paths.communityHome/lib/libpty/linux")
    }

    buildContext.ant.copy(file: ideaProperties.path, todir: "$unixDistPath/bin")
    //todo[nik] converting line separators to unix-style make sense only when building Linux distributions under Windows on a local machine;
    // for real installers we need to checkout all text files with 'lf' separators anyway
    buildContext.ant.fixcrlf(file: "$unixDistPath/bin/idea.properties", eol: "unix")
    if (customizer.iconPngPath != null) {
      buildContext.ant.copy(file: customizer.iconPngPath, tofile: "$unixDistPath/bin/${buildContext.productProperties.baseFileName}.png")
    }
    generateScripts(unixDistPath)
    generateVMOptions(unixDistPath)
    generateReadme(unixDistPath)
    customizer.copyAdditionalFiles(buildContext, unixDistPath)
    return unixDistPath
  }

  @Override
  void buildArtifacts(String osSpecificDistPath) {
    if (customizer.buildTarGzWithoutBundledJre) {
      buildTarGz(null, osSpecificDistPath)
    }
    def jreDirectoryPath = buildContext.bundledJreManager.extractLinuxJre()
    if (jreDirectoryPath != null) {
      buildTarGz(jreDirectoryPath, osSpecificDistPath)
    }
    else {
      buildContext.messages.info("Skipping building Linux distribution with bundled JRE because JRE archive is missing")
    }
  }

  private void generateScripts(String unixDistPath) {
    String name = "${buildContext.productProperties.baseFileName}.sh"
    String fullName = buildContext.applicationInfo.productName
    String vmOptionsFileName = buildContext.productProperties.baseFileName

    String classPath = "CLASSPATH=\"\$IDE_HOME/lib/${buildContext.bootClassPathJarNames[0]}\"\n"
    classPath += buildContext.bootClassPathJarNames[1..-1].collect { "CLASSPATH=\"\$CLASSPATH:\$IDE_HOME/lib/${it}\"" }.join("\n")
    if (buildContext.productProperties.toolsJarRequired) {
      classPath += "\nCLASSPATH=\"\$CLASSPATH:\$JDK/lib/tools.jar\""
    }

    buildContext.ant.copy(todir: "${unixDistPath}/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/scripts/unix")

      filterset(begintoken: "@@", endtoken: "@@") {
        filter(token: "product_full", value: fullName)
        filter(token: "product_uc", value: buildContext.productProperties.environmentVariableBaseName(buildContext.applicationInfo))
        filter(token: "vm_options", value: vmOptionsFileName)
        filter(token: "isEap", value: buildContext.applicationInfo.isEAP)
        filter(token: "system_selector", value: buildContext.systemSelector)
        filter(token: "ide_jvm_args", value: buildContext.additionalJvmArguments)
        filter(token: "class_path", value: classPath)
        filter(token: "script_name", value: name)
      }
    }

    if (name != "idea.sh") {
      //todo[nik] rename idea.sh in sources to something more generic
      buildContext.ant.move(file: "${unixDistPath}/bin/idea.sh", tofile: "${unixDistPath}/bin/$name")
    }
    String inspectScript = buildContext.productProperties.inspectCommandName
    if (inspectScript != "inspect") {
      String targetPath = "${unixDistPath}/bin/${inspectScript}.sh"
      buildContext.ant.move(file: "${unixDistPath}/bin/inspect.sh", tofile: targetPath)
      buildContext.patchInspectScript(targetPath)
    }

    buildContext.ant.fixcrlf(srcdir: "${unixDistPath}/bin", includes: "*.sh", eol: "unix")
  }

  private void generateVMOptions(String unixDistPath) {
    JvmArchitecture.values().each {
      def fileName = "${buildContext.productProperties.baseFileName}${it.fileSuffix}.vmoptions"
      //todo[nik] why we don't add yourkit agent on unix?
      def vmOptions = VmOptionsGenerator.computeVmOptions(it, buildContext.applicationInfo.isEAP, buildContext.productProperties, null) +
                      " -Dawt.useSystemAAFontSettings=lcd -Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine"
      new File(unixDistPath, "bin/$fileName").text = vmOptions.replace(' ', '\n') + "\n"
    }
  }

  private void generateReadme(String unixDistPath) {
    String fullName = buildContext.applicationInfo.productName
    BuildUtils.copyAndPatchFile("$buildContext.paths.communityHome/build/Install-Linux-tar.txt", "$unixDistPath/Install-Linux-tar.txt",
                     ["product_full"   : fullName,
                      "product"        : buildContext.productProperties.baseFileName,
                      "system_selector": buildContext.systemSelector], "@@")
    buildContext.ant.fixcrlf(file: "$unixDistPath/bin/Install-Linux-tar.txt", eol: "unix")
  }

  private void buildTarGz(String jreDirectoryPath, String unixDistPath) {
    def tarRoot = customizer.rootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
    def suffix = jreDirectoryPath != null ? "" : "-no-jdk"
    def tarPath = "$buildContext.paths.artifacts/${buildContext.productProperties.baseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)}${suffix}.tar"
    def extraBins = customizer.extraExecutables
    def paths = [buildContext.paths.distAll, unixDistPath]
    if (jreDirectoryPath != null) {
      paths += jreDirectoryPath
      extraBins += "jre/jre/bin/*"
    }
    def description = "archive${jreDirectoryPath != null ? "" : " (without JRE)"}"
    buildContext.messages.block("Build Linux tar.gz $description") {
      buildContext.messages.progress("Building Linux tar $description")
      buildContext.ant.tar(tarfile: tarPath, longfile: "gnu") {
        paths.each {
          tarfileset(dir: it, prefix: tarRoot) {
            exclude(name: "bin/*.sh")
            exclude(name: "bin/*.py")
            exclude(name: "bin/fsnotifier*")
            extraBins.each {
              exclude(name: it)
            }
            type(type: "file")
          }
        }

        paths.each {
          tarfileset(dir: it, prefix: tarRoot, filemode: "755") {
            include(name: "bin/*.sh")
            include(name: "bin/*.py")
            include(name: "bin/fsnotifier*")
            extraBins.each {
              include(name: it)
            }
            type(type: "file")
          }
        }
      }

      String gzPath = "${tarPath}.gz"
      buildContext.messages.progress("Building Linux tar.gz $description")
      buildContext.ant.gzip(src: tarPath, zipfile: gzPath)
      buildContext.ant.delete(file: tarPath)
      buildContext.notifyArtifactBuilt(gzPath)
    }
  }
}