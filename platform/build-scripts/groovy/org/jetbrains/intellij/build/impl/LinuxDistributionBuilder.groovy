// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator
class LinuxDistributionBuilder extends OsSpecificDistributionBuilder {
  private final LinuxDistributionCustomizer customizer
  private final File ideaProperties
  private final String iconPngPath

  LinuxDistributionBuilder(BuildContext buildContext, LinuxDistributionCustomizer customizer, File ideaProperties) {
    super(buildContext)
    this.customizer = customizer
    this.ideaProperties = ideaProperties
    iconPngPath = (buildContext.applicationInfo.isEAP ? customizer.iconPngPathForEAP : null) ?: customizer.iconPngPath
  }

  @Override
  OsFamily getTargetOs() {
    return OsFamily.LINUX
  }

  @Override
  void copyFilesForOsDistribution(String unixDistPath) {
    buildContext.messages.progress("Building distributions for $targetOs.osName")
    buildContext.ant.copy(todir: "$unixDistPath/bin") {
      fileset(dir: "$buildContext.paths.communityHome/bin/linux")
    }
    BuildTasksImpl.unpackPty4jNative(buildContext, unixDistPath, "linux")
    BuildTasksImpl.generateBuildTxt(buildContext, unixDistPath)

    buildContext.ant.copy(file: ideaProperties.path, todir: "$unixDistPath/bin")
    //todo[nik] converting line separators to unix-style make sense only when building Linux distributions under Windows on a local machine;
    // for real installers we need to checkout all text files with 'lf' separators anyway
    buildContext.ant.fixcrlf(file: "$unixDistPath/bin/idea.properties", eol: "unix")
    if (iconPngPath != null) {
      buildContext.ant.copy(file: iconPngPath, tofile: "$unixDistPath/bin/${buildContext.productProperties.baseFileName}.png")
    }
    generateScripts(unixDistPath)
    generateVMOptions(unixDistPath)
    generateReadme(unixDistPath)
    customizer.copyAdditionalFiles(buildContext, unixDistPath)
  }

  @Override
  void buildArtifacts(String osSpecificDistPath) {
    buildContext.executeStep("Build Linux .tar.gz", BuildOptions.LINUX_ARTIFACTS_STEP) {
      if (customizer.buildTarGzWithoutBundledJre) {
        buildContext.executeStep("Build Linux .tar.gz without bundled JRE", BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_JRE_STEP) {
          buildTarGz(null, osSpecificDistPath, "-no-jbr")
        }
      }

      if (customizer.buildOnlyBareTarGz) return
      
      if (customizer.includeX86Files) {
        buildContext.bundledJreManager.repackageX86Jre(OsFamily.LINUX)
      }

      String jreDirectoryPath = buildContext.bundledJreManager.extractJre(OsFamily.LINUX)
      buildTarGz(jreDirectoryPath, osSpecificDistPath, "")

      if (jreDirectoryPath != null) {
        buildSnapPackage(jreDirectoryPath, osSpecificDistPath)
      }
      else {
        buildContext.messages.info("Skipping building Snap packages because no modular JRE are available")
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

    String linkToX86Jre = (customizer.includeX86Files ? buildContext.bundledJreManager.x86JreDownloadUrl(OsFamily.LINUX) : null) ?: ""

    buildContext.ant.copy(todir: "${unixDistPath}/bin") {
      fileset(dir: "$buildContext.paths.communityHome/platform/build-scripts/resources/linux/scripts")

      filterset(begintoken: "__", endtoken: "__") {
        filter(token: "product_full", value: fullName)
        filter(token: "product_uc", value: buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo))
        filter(token: "product_vendor", value: buildContext.applicationInfo.shortCompanyName)
        filter(token: "vm_options", value: vmOptionsFileName)
        filter(token: "system_selector", value: buildContext.systemSelector)
        filter(token: "ide_jvm_args", value: buildContext.additionalJvmArguments)
        filter(token: "class_path", value: classPath)
        filter(token: "script_name", value: name)
        filter(token: "x86_jre_url", value: linkToX86Jre)
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
      def fileName = "${buildContext.productProperties.baseFileName}${it.fileSuffix}.vmoptions"
      def vmOptions = VmOptionsGenerator.computeVmOptions(it, buildContext.applicationInfo.isEAP, buildContext.productProperties) +
                      ['-Dawt.useSystemAAFontSettings=lcd',
                       '-Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine',
                       '-Dsun.tools.attach.tmp.only=true']
      new File(unixDistPath, "bin/$fileName").text = vmOptions.join("\n") + "\n"
    }
  }

  private void generateReadme(String unixDistPath) {
    String fullName = buildContext.applicationInfo.productName
    BuildUtils.copyAndPatchFile(
      "$buildContext.paths.communityHome/platform/build-scripts/resources/linux/Install-Linux-tar.txt",
      "$unixDistPath/Install-Linux-tar.txt",
      ["product_full"   : fullName,
       "product"        : buildContext.productProperties.baseFileName,
       "product_vendor" : buildContext.applicationInfo.shortCompanyName,
       "system_selector": buildContext.systemSelector], "@@")
    buildContext.ant.fixcrlf(file: "$unixDistPath/bin/Install-Linux-tar.txt", eol: "unix")
  }

  @Override
  List<String> generateExecutableFilesPatterns(boolean includeJre) {
    def patterns = [
      "bin/*.sh",
      "bin/*.py",
      "bin/fsnotifier*"
    ] + customizer.extraExecutables
    if (includeJre) {
      patterns += "jbr/bin/*"
      patterns += "jbr/lib/jexec"
      patterns += "jbr/lib/jcef_helper"
      patterns += "jbr/lib/jspawnhelper"
      patterns += "jbr/lib/chrome-sandbox"
    }
    return patterns
  }

  private void buildTarGz(String jreDirectoryPath, String unixDistPath, String suffix) {
    def tarRoot = customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
    def baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
    def tarPath = "${buildContext.paths.artifacts}/${baseName}${suffix}.tar.gz"
    def paths = [buildContext.paths.distAll, unixDistPath]

    String javaExecutablePath = null
    if (jreDirectoryPath != null) {
      paths += jreDirectoryPath
      javaExecutablePath = "jbr/bin/java"
    }
    def productJsonDir = new File(buildContext.paths.temp, "linux.dist.product-info.json$suffix").absolutePath
    generateProductJson(productJsonDir, javaExecutablePath)
    paths += productJsonDir

    def executableFilesPatterns = generateExecutableFilesPatterns(jreDirectoryPath != null)
    def description = "archive${jreDirectoryPath != null ? "" : " (without JRE)"}"
    buildContext.messages.block("Build Linux tar.gz $description") {
      buildContext.messages.progress("Building Linux tar.gz $description")
      buildContext.ant.tar(tarfile: tarPath, longfile: "gnu", compression: "gzip") {
        paths.each {
          tarfileset(dir: it, prefix: tarRoot) {
            executableFilesPatterns.each {
              exclude(name: it)
            }
            type(type: "file")
          }
        }

        paths.each {
          tarfileset(dir: it, prefix: tarRoot, filemode: "755") {
            executableFilesPatterns.each {
              include(name: it)
            }
            type(type: "file")
          }
        }
      }

      new ProductInfoValidator(buildContext).checkInArchive(tarPath, tarRoot)
      buildContext.notifyArtifactBuilt(tarPath)
    }
  }

  private void generateProductJson(String targetDir, String javaExecutablePath) {
    def scriptName = buildContext.productProperties.baseFileName
    new ProductInfoGenerator(buildContext).generateProductJson(
      targetDir, "bin", getFrameClass(buildContext), "bin/${scriptName}.sh", javaExecutablePath, "bin/${scriptName}64.vmoptions", OsFamily.LINUX)
  }

  private void buildSnapPackage(String jreDirectoryPath, String unixDistPath) {
    if (!buildContext.options.buildUnixSnaps || customizer.snapName == null) return

    if (StringUtil.isEmpty(iconPngPath)) buildContext.messages.error("'iconPngPath' not set")
    if (StringUtil.isEmpty(customizer.snapDescription)) buildContext.messages.error("'snapDescription' not set")

    String snapDir = "${buildContext.paths.buildOutputRoot}/dist.snap"

    buildContext.messages.block("Build Linux .snap package") {
      buildContext.messages.progress("Preparing files")

      String unixSnapDistPath = "$buildContext.paths.buildOutputRoot/dist.unix.snap"
      buildContext.ant.copy(todir: unixSnapDistPath) {
        fileset(dir: unixDistPath) {
          exclude(name: "bin/fsnotifier")
          exclude(name: "bin/libyjpagent-linux.so")
        }
      }

      def desktopTemplate = "${buildContext.paths.communityHome}/platform/platform-resources/src/entry.desktop"
      def productName = buildContext.applicationInfo.productNameWithEdition
      buildContext.ant.copy(file: desktopTemplate, tofile: "${snapDir}/${customizer.snapName}.desktop") {
        filterset(begintoken: '$', endtoken: '$') {
          filter(token: "NAME", value: productName)
          filter(token: "ICON", value: "\${SNAP}/bin/${buildContext.productProperties.baseFileName}.png")
          filter(token: "SCRIPT", value: customizer.snapName)
          filter(token: "COMMENT", value: buildContext.applicationInfo.motto)
          filter(token: "WM_CLASS", value: getFrameClass(buildContext))
        }
      }

      buildContext.ant.copy(file: iconPngPath, tofile: "${snapDir}/${customizer.snapName}.png")

      def snapcraftTemplate = "${buildContext.paths.communityHome}/platform/build-scripts/resources/linux/snap/snapcraft-template.yaml"
      def versionSuffix = buildContext.applicationInfo.versionSuffix ?: ""
      def version = "${buildContext.applicationInfo.majorVersion}.${buildContext.applicationInfo.minorVersion}${versionSuffix.isEmpty() ? "" : "-${versionSuffix}"}"
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

      buildContext.ant.chmod(perm: "755") {
        fileset(dir: unixSnapDistPath) {
          include(name: "bin/*.sh")
          include(name: "bin/*.py")
          include(name: "bin/fsnotifier*")
          customizer.extraExecutables.each { include(name: it) }
        }
        fileset(dir: buildContext.paths.distAll){
          customizer.extraExecutables.each { include(name: it) }
        }
        fileset(dir: jreDirectoryPath) {
          include(name: "jbr/bin/*")
        }
      }

      generateProductJson(unixSnapDistPath, "jbr/bin/java")
      new ProductInfoValidator(buildContext).validateInDirectory(unixSnapDistPath, "", [unixSnapDistPath, jreDirectoryPath], [])

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
        arg(value: "--volume=${unixSnapDistPath}:/build/dist.unix:ro")
        arg(value: "--volume=${jreDirectoryPath}:/build/jre:ro")
        arg(value: "--workdir=/build")
        arg(value: buildContext.options.snapDockerImage)
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
  static String getFrameClass(BuildContext buildContext) {
    String name = buildContext.applicationInfo.productNameWithEdition
      .toLowerCase(Locale.US)
      .replace(' ', '-')
      .replace("intellij-idea", "idea").replace("android-studio", "studio")
      .replace("-community-edition", "-ce").replace("-ultimate-edition", "").replace("-professional-edition", "")
    name.startsWith("jetbrains-") ? name : "jetbrains-" + name
  }
}