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

/**
 * @author nik
 */
class LinuxDistributionBuilder {
  private final BuildContext buildContext
  final String unixDistPath

  LinuxDistributionBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
    unixDistPath = "$buildContext.paths.buildOutputRoot/dist.unix"
  }

  //todo[nik] rename
  void layoutUnix(File ideaProperties) {
    buildContext.ant.copy(todir: "$unixDistPath/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/linux")
      if (buildContext.productProperties.yourkitAgentBinariesDirectoryPath != null) {
        fileset(dir: buildContext.productProperties.yourkitAgentBinariesDirectoryPath) {
          include(name: "libyjpagent-linux*.so")
        }
      }
    }
    buildContext.ant.copy(file: ideaProperties.path, todir: "$unixDistPath/bin")
    //todo[nik] converting line separators to unix-style make sense only when building Linux distributions under Windows on a local machine;
    // for real installers we need to checkout all text files with 'lf' separators anyway
    buildContext.ant.fixcrlf(file: "$unixDistPath/bin/idea.properties", eol: "unix")
    buildContext.ant.copy(file: buildContext.productProperties.icon128, tofile: "$unixDistPath/bin/${buildContext.fileNamePrefix}.png")

    unixScripts()
    unixVMOptions()
    unixReadme()
    buildContext.productProperties.customLinLayout(buildContext, unixDistPath)
    buildTarGz(false)
    if (new File(buildContext.paths.linuxJre).exists()) {
      buildTarGz(true)
    }
    else {
      buildContext.messages.info("Skipping building Linux distribution with bundled JRE because JRE directory doesn't exist: $buildContext.paths.linuxJre")
    }
  }

  private void unixScripts() {
    String name = "${buildContext.fileNamePrefix}.sh"
    String fullName = buildContext.applicationInfo.productName
    String productUpperCase = buildContext.applicationInfo.shortProductName.toUpperCase()
    String vmOptionsFileName = buildContext.fileNamePrefix

    String classPath = "CLASSPATH=\"\$IDE_HOME/lib/${buildContext.bootClassPathJarNames[0]}\"\n"
    classPath += buildContext.bootClassPathJarNames[1..-1].collect { "CLASSPATH=\"\$CLASSPATH:\$IDE_HOME/lib/${it}\"" }.join("\n")
    def jvmArgs = buildContext.productProperties.ideJvmArgs
    if (buildContext.productProperties.toolsJarRequired) {
      classPath += "\nCLASSPATH=\"\$CLASSPATH:\$JDK/lib/tools.jar\""
      jvmArgs = "$jvmArgs -Didea.jre.check=true".trim()
    }

    buildContext.ant.copy(todir: "${unixDistPath}/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/scripts/unix")

      filterset(begintoken: "@@", endtoken: "@@") {
        filter(token: "product_full", value: fullName)
        filter(token: "product_uc", value: productUpperCase)
        filter(token: "vm_options", value: vmOptionsFileName)
        filter(token: "isEap", value: buildContext.applicationInfo.isEAP)
        filter(token: "system_selector", value: buildContext.systemSelector)
        filter(token: "ide_jvm_args", value: jvmArgs)
        filter(token: "class_path", value: classPath)
        filter(token: "script_name", value: name)
      }
    }

    if (name != "idea.sh") {
      //todo[nik] rename idea.sh in sources to something more generic
      buildContext.ant.move(file: "${unixDistPath}/bin/idea.sh", tofile: "${unixDistPath}/bin/$name")
    }
    String inspectScript = buildContext.productProperties.customInspectScriptName
    if (inspectScript != null && inspectScript != "inspect") {
      buildContext.ant.move(file: "${unixDistPath}/bin/inspect.sh", tofile: "${unixDistPath}/bin/${inspectScript}.sh")
    }

    buildContext.ant.fixcrlf(srcdir: "${unixDistPath}/bin", includes: "*.sh", eol: "unix")
  }

  private void unixVMOptions() {
    JvmArchitecture.values().each {
      def fileName = "${buildContext.fileNamePrefix}${it.fileSuffix}.vmoptions"
      //todo[nik] why we don't add yourkit agent on unix?
      def options = VmOptionsGenerator.computeVmOptions(it, buildContext.applicationInfo.isEAP, null) + " -Dawt.useSystemAAFontSettings=lcd"
      new File(unixDistPath, "bin/$fileName").text = options.replace(' ', '\n') + "\n"
    }
  }

  private void unixReadme() {
    String fullName = buildContext.applicationInfo.productName
    BuildUtils.copyAndPatchFile("$buildContext.paths.communityHome/build/Install-Linux-tar.txt", "$unixDistPath/Install-Linux-tar.txt",
                     ["product_full"   : fullName,
                      "product"        : buildContext.fileNamePrefix,
                      "system_selector": buildContext.systemSelector], "@@")
    buildContext.ant.fixcrlf(file: "$unixDistPath/bin/Install-Linux-tar.txt", eol: "unix")
  }

  private void buildTarGz(boolean bundleJre) {
    def tarRoot = buildContext.productProperties.linuxAppRoot(buildContext.buildNumber)
    def suffix = bundleJre ? "" : "-no-jdk"
    def tarPath = "$buildContext.paths.artifacts/${buildContext.productProperties.archiveName(buildContext.buildNumber)}${suffix}.tar"
    def extraBins = buildContext.productProperties.linux.extraLinuxBins
    def paths = [buildContext.paths.distAll, unixDistPath]
    if (bundleJre) {
      paths += buildContext.paths.linuxJre
      extraBins += "jre/jre/bin/*"
    }
    buildContext.messages.block("Build Linux tar.gz archive${bundleJre ? "" : " (without JRE)"}") {
      buildContext.ant.tar(tarfile: tarPath, longfile: "gnu") {
        paths.each {
          tarfileset(dir: it, prefix: tarRoot) {
            exclude(name: "bin/*.sh")
            exclude(name: "bin/fsnotifier*")
            extraBins.each {
              exclude(name: it)
            }
            type(type: "file")
          }
        }

        paths.each {
          tarfileset(dir: it, filemode: "755", prefix: tarRoot) {
            include(name: "bin/*.sh")
            include(name: "bin/fsnotifier*")
            extraBins.each {
              include(name: it)
            }
            type(type: "file")
          }
        }
      }

      String gzPath = "${tarPath}.gz"
      buildContext.ant.gzip(src: tarPath, zipfile: gzPath)
      buildContext.ant.delete(file: tarPath)
      buildContext.notifyArtifactBuilt(gzPath)
    }
  }
}