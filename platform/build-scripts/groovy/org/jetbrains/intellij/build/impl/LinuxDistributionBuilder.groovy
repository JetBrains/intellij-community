/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtil
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
      fileset(dir: "$buildContext.paths.communityHome/bin/linux")
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
    buildContext.executeStep("Build Linux .tar.gz", BuildOptions.LINUX_ARTIFACTS_STEP) {
      if (customizer.buildTarGzWithoutBundledJre) {
        buildTarGz(null, osSpecificDistPath)
      }
      def jreDirectoryPath = buildContext.bundledJreManager.extractLinuxJre()
      if (jreDirectoryPath != null) {
        buildTarGz(jreDirectoryPath, osSpecificDistPath)
        buildSnapPackage(jreDirectoryPath, osSpecificDistPath)
      }
      else {
        buildContext.messages.info("Skipping building Linux distribution with bundled JRE because JRE archive is missing")
      }
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
      fileset(dir: "$buildContext.paths.communityHome/platform/build-scripts/resources/linux/scripts")

      filterset(begintoken: "@@", endtoken: "@@") {
        filter(token: "product_full", value: fullName)
        filter(token: "product_uc", value: buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo))
        filter(token: "vm_options", value: vmOptionsFileName)
        filter(token: "system_selector", value: buildContext.systemSelector)
        filter(token: "ide_jvm_args", value: buildContext.additionalJvmArguments)
        filter(token: "class_path", value: classPath)
        filter(token: "script_name", value: name)
      }
    }

    buildContext.ant.move(file: "${unixDistPath}/bin/executable-template.sh", tofile: "${unixDistPath}/bin/$name")

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
      def yourkitSessionName = buildContext.applicationInfo.isEAP && buildContext.productProperties.enableYourkitAgentInEAP ? buildContext.systemSelector : null
      def fileName = "${buildContext.productProperties.baseFileName}${it.fileSuffix}.vmoptions"
      def vmOptions = VmOptionsGenerator.computeVmOptions(it, buildContext.applicationInfo.isEAP, buildContext.productProperties, yourkitSessionName) +
                      " -Dawt.useSystemAAFontSettings=lcd -Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine"
      new File(unixDistPath, "bin/$fileName").text = vmOptions.replace(' ', '\n') + "\n"
    }
  }

  private void generateReadme(String unixDistPath) {
    String fullName = buildContext.applicationInfo.productName
    BuildUtils.copyAndPatchFile("$buildContext.paths.communityHome/platform/build-scripts/resources/linux/Install-Linux-tar.txt", "$unixDistPath/Install-Linux-tar.txt",
                     ["product_full"   : fullName,
                      "product"        : buildContext.productProperties.baseFileName,
                      "system_selector": buildContext.systemSelector], "@@")
    buildContext.ant.fixcrlf(file: "$unixDistPath/bin/Install-Linux-tar.txt", eol: "unix")
  }

  private void buildTarGz(String jreDirectoryPath, String unixDistPath) {
    def tarRoot = customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
    def suffix = jreDirectoryPath != null ? "" : "-no-jdk"
    def tarPath = "$buildContext.paths.artifacts/${buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)}${suffix}.tar"
    def extraBins = customizer.extraExecutables
    def paths = [buildContext.paths.distAll, unixDistPath]
    if (jreDirectoryPath != null) {
      paths += jreDirectoryPath
      extraBins += "jre64/bin/*"
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

  private void buildSnapPackage(String jreDirectoryPath, String unixDistPath) {
    if (!buildContext.options.buildUnixSnaps || customizer.snapName == null) return

    if (StringUtil.isEmpty(customizer.iconPngPath)) buildContext.messages.error("'iconPngPath' not set")
    if (StringUtil.isEmpty(customizer.snapDescription)) buildContext.messages.error("'snapDescription' not set")

    String snapDir = "${buildContext.paths.buildOutputRoot}/dist.snap"

    buildContext.messages.block("Build Linux .snap package") {
      buildContext.messages.progress("Preparing files")

      def desktopTemplate = "${buildContext.paths.communityHome}/platform/platform-resources/src/entry.desktop"
      def productName = buildContext.applicationInfo.productNameWithEdition
      buildContext.ant.copy(file: desktopTemplate, tofile: "${snapDir}/${customizer.snapName}.desktop") {
        filterset(begintoken: '$', endtoken: '$') {
          filter(token: "NAME", value: productName)
          filter(token: "ICON", value: "\${SNAP}/bin/${buildContext.productProperties.baseFileName}.png")
          filter(token: "SCRIPT", value: customizer.snapName)
          filter(token: "WM_CLASS", value: getFrameClass())
        }
      }

      buildContext.ant.copy(file: customizer.iconPngPath, tofile: "${snapDir}/${customizer.snapName}.png")

      def snapcraftTemplate = "${buildContext.paths.communityHome}/platform/build-scripts/resources/linux/snap/snapcraft-template.yaml"
      def version = "${buildContext.applicationInfo.majorVersion}.${buildContext.applicationInfo.minorVersion}${buildContext.applicationInfo.isEAP ? "-EAP" : ""}"
      buildContext.ant.copy(file: snapcraftTemplate, tofile: "${snapDir}/snapcraft.yaml") {
        filterset(begintoken: '$', endtoken: '$') {
          filter(token: "NAME", value: customizer.snapName)
          filter(token: "VERSION", value: version)
          filter(token: "SUMMARY", value: productName)
          filter(token: "DESCRIPTION", value: customizer.snapDescription)
          filter(token: "GRADE", value: buildContext.applicationInfo.isEAP ? "devel" : "stable")
          filter(token: "SCRIPT", value: "bin/${buildContext.productProperties.baseFileName}.sh")
        }
      }

      buildContext.ant.delete(quiet: true) {
        fileset(dir: "${unixDistPath}/bin") {
          include(name: "fsnotifier")
          include(name: "fsnotifier-arm")
          include(name: "libyjpagent-linux.so")
        }
      }

      buildContext.ant.chmod(perm: "755") {
        fileset(dir: unixDistPath) {
          include(name: "bin/*.sh")
          include(name: "bin/*.py")
          include(name: "bin/fsnotifier*")
          customizer.extraExecutables.each { include(name: it) }
        }
        fileset(dir: jreDirectoryPath) {
          include(name: "jre64/bin/*")
        }
      }

      buildContext.ant.mkdir(dir: "${snapDir}/result")
      buildContext.messages.progress("Building package")

      def snapArtifact = "${customizer.snapName}_${version}_amd64.snap"
      buildContext.ant.exec(executable: "docker", dir: snapDir, failonerror: true) {
        arg(value: "run")
        arg(value: "--rm")
        arg(value: "--volume=${snapDir}/snapcraft.yaml:/build/snapcraft.yaml:ro")
        arg(value: "--volume=${snapDir}/${customizer.snapName}.desktop:/build/snap/gui/${customizer.snapName}.desktop:ro")
        arg(value: "--volume=${snapDir}/${customizer.snapName}.png:/build/prime/meta/gui/icon.png:ro")
        arg(value: "--volume=${snapDir}/result:/build/result")
        arg(value: "--volume=${buildContext.paths.distAll}:/build/dist.all:ro")
        arg(value: "--volume=${unixDistPath}:/build/dist.unix:ro")
        arg(value: "--volume=${jreDirectoryPath}:/build/jre:ro")
        arg(value: "--workdir=/build")
        arg(value: "--env=SNAPCRAFT_SETUP_CORE=1")
        arg(value: "snapcore/snapcraft")
        arg(value: "snapcraft")
        arg(value: "snap")
        arg(value: "-o")
        arg(value: "result/$snapArtifact")
      }

      buildContext.ant.move(file: "${snapDir}/result/${snapArtifact}", todir: buildContext.paths.artifacts)
      buildContext.notifyArtifactBuilt("${buildContext.paths.artifacts}/" + snapArtifact)
    }
  }

  // keep in sync with AppUIUtil#getFrameClass
  private String getFrameClass() {
    String name = buildContext.applicationInfo.productNameWithEdition
      .toLowerCase(Locale.US)
      .replace(' ', '-')
      .replace("intellij-idea", "idea").replace("android-studio", "studio")
      .replace("-community-edition", "-ce").replace("-ultimate-edition", "").replace("-professional-edition", "")
    "jetbrains-" + name
  }
}