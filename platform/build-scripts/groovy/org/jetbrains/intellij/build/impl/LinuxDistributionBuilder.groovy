// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.Strings
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder

import java.nio.charset.StandardCharsets
import java.nio.file.*

@CompileStatic
final class LinuxDistributionBuilder extends OsSpecificDistributionBuilder {
  private final LinuxDistributionCustomizer customizer
  private final Path ideaProperties
  private final Path iconPngPath

  LinuxDistributionBuilder(BuildContext buildContext, LinuxDistributionCustomizer customizer, Path ideaProperties) {
    super(buildContext)
    this.customizer = customizer
    this.ideaProperties = ideaProperties

    String iconPng = (buildContext.applicationInfo.isEAP ? customizer.iconPngPathForEAP : null) ?: customizer.iconPngPath
    iconPngPath = iconPng == null || iconPng.isEmpty() ? null : Path.of(iconPng)
  }

  @Override
  OsFamily getTargetOs() {
    return OsFamily.LINUX
  }

  @Override
  void copyFilesForOsDistribution(@NotNull Path unixDistPath, JvmArchitecture arch = null) {
    BuildHelper buildHelper = BuildHelper.getInstance(buildContext)
    buildHelper.span(TracerManager.spanBuilder("copy files for os distribution")
                       .setAttribute("os", targetOs.osName)
                       .setAttribute("arch", arch?.name()), new Runnable() {
      @Override
      void run() {
        Path distBinDir = unixDistPath.resolve("bin")

        buildHelper.copyDir(buildContext.paths.communityHomeDir.resolve("bin/linux"), distBinDir)
        BuildTasksImpl.unpackPty4jNative(buildContext, unixDistPath, "linux")
        BuildTasksImpl.generateBuildTxt(buildContext, unixDistPath)
        BuildTasksImpl.copyDistFiles(buildContext, unixDistPath)
        List<String> extraJarNames = BuildTasksImpl.addDbusJava(buildContext, unixDistPath.resolve("lib"))
        Files.copy(ideaProperties, distBinDir.resolve(ideaProperties.fileName), StandardCopyOption.REPLACE_EXISTING)
        //todo[nik] converting line separators to unix-style make sense only when building Linux distributions under Windows on a local machine;
        // for real installers we need to checkout all text files with 'lf' separators anyway
        BuildUtils.convertLineSeparators(unixDistPath.resolve("bin/idea.properties"), "\n")
        if (iconPngPath != null) {
          Files.copy(iconPngPath, distBinDir.resolve("${buildContext.productProperties.baseFileName}.png"),
                     StandardCopyOption.REPLACE_EXISTING)
        }
        generateVMOptions(distBinDir)
        UnixScriptBuilder.generateScripts(buildContext, extraJarNames, distBinDir, OsFamily.LINUX)
        generateReadme(unixDistPath)
        generateVersionMarker(unixDistPath, buildContext)
        customizer.copyAdditionalFiles(buildContext, unixDistPath)
      }
    })
  }

  @Override
  void buildArtifacts(Path osSpecificDistPath) {
    copyFilesForOsDistribution(osSpecificDistPath)
    buildContext.executeStep("build linux .tar.gz", BuildOptions.LINUX_ARTIFACTS_STEP) {
      if (customizer.buildTarGzWithoutBundledRuntime) {
        buildContext.executeStep("Build Linux .tar.gz without bundled JRE", BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_JRE_STEP) {
          buildTarGz(null, osSpecificDistPath, "-no-jbr", buildContext)
        }
      }

      if (customizer.buildOnlyBareTarGz) {
        return
      }

      Path jreDirectoryPath = buildContext.bundledRuntime.extract(BundledRuntime.getProductPrefix(buildContext), OsFamily.LINUX, JvmArchitecture.x64)
      Path tarGzPath = buildTarGz(jreDirectoryPath.toString(), osSpecificDistPath, "", buildContext)

      if (jreDirectoryPath != null) {
        buildSnapPackage(jreDirectoryPath.toString(), osSpecificDistPath)
      }
      else {
        buildContext.messages.info("Skipping building Snap packages because no modular JRE are available")
      }
      Path tempTar = Files.createTempDirectory(buildContext.paths.tempDir, "tar-")
      try {
        BuildHelper.runProcess(buildContext, ["tar", "xzf", tarGzPath.toString(), "--directory", tempTar.toString()])
        String tarRoot = customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
        RepairUtilityBuilder.generateManifest(buildContext, tempTar.resolve(tarRoot), tarGzPath.fileName.toString())
      }
      finally {
        NioFiles.deleteRecursively(tempTar)
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void generateVMOptions(Path distBinDir) {
    String fileName = "${buildContext.productProperties.baseFileName}64.vmoptions"
    List<String> vmOptions = VmOptionsGenerator.computeVmOptions(buildContext.applicationInfo.isEAP, buildContext.productProperties) +
                             ['-Dsun.tools.attach.tmp.only=true']
    Files.writeString(distBinDir.resolve(fileName), String.join('\n', vmOptions) + '\n', StandardCharsets.US_ASCII)
  }

  private void generateReadme(Path unixDistPath) {
    String fullName = buildContext.applicationInfo.productName
    BuildUtils.copyAndPatchFile(
      buildContext.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/Install-Linux-tar.txt"),
      unixDistPath.resolve("Install-Linux-tar.txt"),
      ["product_full"   : fullName,
       "product"        : buildContext.productProperties.baseFileName,
       "product_vendor" : buildContext.applicationInfo.shortCompanyName,
       "system_selector": buildContext.systemSelector], "@@", "\n")
  }

  // please keep in sync with `SystemHealthMonitor#checkInstallationIntegrity`
  @CompileStatic
  private static void generateVersionMarker(Path unixDistPath, BuildContext context) {
    Path targetDir = unixDistPath.resolve("lib")
    Files.createDirectories(targetDir)
    Files.writeString(targetDir.resolve("build-marker-${context.fullBuildNumber}"), context.fullBuildNumber)
  }

  @Override
  List<String> generateExecutableFilesPatterns(boolean includeJre) {
    def patterns = [
      "bin/*.sh",
      "bin/*.py",
      "bin/fsnotifier*",
      "bin/remote-dev-server.sh",
    ] + customizer.extraExecutables
    if (includeJre) {
      // When changing this list of patterns, also change patch_bin_file in launcher.sh (for remote dev)
      patterns += "jbr/bin/*"
      patterns += "jbr/lib/jexec"
      patterns += "jbr/lib/jcef_helper"
      patterns += "jbr/lib/jspawnhelper"
      patterns += "jbr/lib/chrome-sandbox"
    }
    return patterns
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private Path buildTarGz(@Nullable String jreDirectoryPath, Path unixDistPath, String suffix, BuildContext buildContext) {
    def tarRoot = customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
    def baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
    Path tarPath = buildContext.paths.artifactDir.resolve("${baseName}${suffix}.tar.gz")
    List<String> paths = [buildContext.paths.distAll, unixDistPath.toString()]

    String javaExecutablePath = null
    if (jreDirectoryPath != null) {
      paths += jreDirectoryPath
      javaExecutablePath = "jbr/bin/java"
      if (!Files.exists(Path.of(jreDirectoryPath, javaExecutablePath))) {
        throw new IllegalStateException(javaExecutablePath + " was not found under " + jreDirectoryPath)
      }
    }

    def productJsonDir = new File(buildContext.paths.temp, "linux.dist.product-info.json$suffix").absolutePath
    generateProductJson(Paths.get(productJsonDir), javaExecutablePath)
    paths += productJsonDir

    def executableFilesPatterns = generateExecutableFilesPatterns(jreDirectoryPath != null)
    def description = "archive${jreDirectoryPath != null ? "" : " (without JRE)"}"

    buildContext.messages.block("Build Linux tar.gz $description") {
      buildContext.messages.progress("Building Linux tar.gz $description")
      buildContext.ant.tar(tarfile: tarPath.toString(), longfile: "gnu", compression: "gzip") {
        paths.each { path ->
          tarfileset(dir: path, prefix: tarRoot) {
            executableFilesPatterns.each {pattern ->
              exclude(name: pattern)
            }
            type(type: "file")
          }
        }

        paths.each { path ->
          tarfileset(dir: path, prefix: tarRoot, filemode: "755") {
            executableFilesPatterns.each { pattern ->
              include(name: pattern)
            }
            type(type: "file")
          }
        }
      }

      ProductInfoValidator.checkInArchive(buildContext, tarPath, tarRoot)
      buildContext.notifyArtifactBuilt(tarPath)
      return tarPath
    }
  }

  private void generateProductJson(@NotNull Path targetDir, String javaExecutablePath) {
    def scriptName = buildContext.productProperties.baseFileName
    new ProductInfoGenerator(buildContext).generateProductJson(
      targetDir, "bin", getFrameClass(buildContext), "bin/${scriptName}.sh", javaExecutablePath, "bin/${scriptName}64.vmoptions", OsFamily.LINUX)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildSnapPackage(String jreDirectoryPath, Path unixDistPath) {
    if (!buildContext.options.buildUnixSnaps || customizer.snapName == null) {
      return
    }

    if (iconPngPath == null) {
      buildContext.messages.error("'iconPngPath' not set")
    }
    if (Strings.isEmpty(customizer.snapDescription)) {
      buildContext.messages.error("'snapDescription' not set")
    }

    Path snapDir = buildContext.paths.buildOutputDir.resolve("dist.snap")

    buildContext.messages.block("build Linux .snap package") {
      buildContext.messages.progress("Preparing files")

      Path unixSnapDistPath = buildContext.paths.buildOutputDir.resolve("dist.unix.snap")
      BuildHelper.getInstance(buildContext).copyDir(unixDistPath, unixSnapDistPath)

      def desktopTemplate = "${buildContext.paths.communityHome}/platform/platform-resources/src/entry.desktop"
      def productName = buildContext.applicationInfo.productNameWithEdition
      buildContext.ant.copy(file: desktopTemplate, tofile: snapDir.resolve("${customizer.snapName}.desktop").toString()) {
        filterset(begintoken: '$', endtoken: '$') {
          filter(token: "NAME", value: productName)
          filter(token: "ICON", value: "\${SNAP}/bin/${buildContext.productProperties.baseFileName}.png")
          filter(token: "SCRIPT", value: customizer.snapName)
          filter(token: "COMMENT", value: buildContext.applicationInfo.motto)
          filter(token: "WM_CLASS", value: getFrameClass(buildContext))
        }
      }

      BuildHelper.moveFile(iconPngPath, snapDir.resolve(customizer.snapName + ".png"))

      def snapcraftTemplate = "${buildContext.paths.communityHome}/platform/build-scripts/resources/linux/snap/snapcraft-template.yaml"
      def versionSuffix = buildContext.applicationInfo.versionSuffix?.replace(' ', '-') ?: ""
      def version = "${buildContext.applicationInfo.majorVersion}.${buildContext.applicationInfo.minorVersion}${versionSuffix.isEmpty() ? "" : "-${versionSuffix}"}"
      buildContext.ant.copy(file: snapcraftTemplate, tofile: snapDir.resolve("snapcraft.yaml").toString()) {
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
        fileset(dir: unixSnapDistPath.toString()) {
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
      new ProductInfoValidator(buildContext).validateInDirectory(unixSnapDistPath,
                                                                 "",
                                                                 List.of(unixSnapDistPath, Path.of(jreDirectoryPath)),
                                                                 [])

      Path resultDir = snapDir.resolve("result")
      Files.createDirectories(resultDir)
      buildContext.messages.progress("Building package")

      String snapArtifact = "${customizer.snapName}_${version}_amd64.snap"
      buildContext.ant.exec(executable: "docker", dir: snapDir.toString(), failonerror: true) {
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

      BuildHelper.moveFileToDir(resultDir.resolve(snapArtifact), buildContext.paths.artifactDir)
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

  static void copyFile(Path source, Path target, CopyOption... options) {
    Path parent = target.parent
    if (parent != null) {
      Files.createDirectories(parent)
    }

    List<CopyOption> optionsList = options.toList()

    if (Files.isSymbolicLink(source)) {
      // Append 'NOFOLLOW_LINKS' copy option to be able to copy symbolic links.
      if (!optionsList.contains(LinkOption.NOFOLLOW_LINKS)) {
        optionsList.add(LinkOption.NOFOLLOW_LINKS)
      }
    }

    CopyOption[] copyOptions = optionsList.toArray(new CopyOption[optionsList.size()])
    Files.copy(source, target, copyOptions)
  }
}
