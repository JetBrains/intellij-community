// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.Strings
import groovy.transform.CompileStatic
import kotlin.Pair
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoLaunchData
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator
import org.jetbrains.intellij.build.impl.support.RepairUtilityBuilder
import org.jetbrains.intellij.build.io.FileKt

import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions

import static org.jetbrains.intellij.build.impl.TracerManager.spanBuilder

@CompileStatic
final class LinuxDistributionBuilder extends OsSpecificDistributionBuilder {
  private static final String NO_JBR_SUFFIX = "-no-jbr"
  private final LinuxDistributionCustomizer customizer
  private final Path ideaProperties
  private final Path iconPngPath

  LinuxDistributionBuilder(BuildContext buildContext, LinuxDistributionCustomizer customizer, Path ideaProperties) {
    super(buildContext)
    this.customizer = customizer
    this.ideaProperties = ideaProperties

    String iconPng = (buildContext.applicationInfo.isEAP() ? customizer.iconPngPathForEAP : null) ?: customizer.iconPngPath
    iconPngPath = iconPng == null || iconPng.isEmpty() ? null : Path.of(iconPng)
  }

  @Override
  OsFamily getTargetOs() {
    return OsFamily.LINUX
  }

  @Override
  void copyFilesForOsDistribution(@NotNull Path unixDistPath, JvmArchitecture arch) {
    BuildHelper buildHelper = BuildHelper.getInstance(buildContext)
    buildHelper.span(spanBuilder("copy files for os distribution")
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
        RepairUtilityBuilder.bundle(buildContext, OsFamily.LINUX, arch, unixDistPath)
        customizer.copyAdditionalFiles(buildContext, unixDistPath, arch)
      }
    })
  }

  @Override
  void buildArtifacts(@NotNull Path osAndArchSpecificDistPath, @NotNull JvmArchitecture arch) {
    copyFilesForOsDistribution(osAndArchSpecificDistPath, arch)
    buildContext.executeStep(spanBuilder("build linux .tar.gz")
                               .setAttribute("arch", arch.name()), BuildOptions.LINUX_ARTIFACTS_STEP) {
      if (customizer.buildTarGzWithoutBundledRuntime) {
        buildContext.executeStep("Build Linux .tar.gz without bundled JRE", BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_JRE_STEP) {
          buildTarGz(null, osAndArchSpecificDistPath, NO_JBR_SUFFIX)
        }
      }

      if (customizer.buildOnlyBareTarGz) {
        return
      }

      Path jreDirectoryPath = buildContext.bundledRuntime.extract(BundledRuntimeImpl.getProductPrefix(buildContext), OsFamily.LINUX, arch)
      Path tarGzPath = buildTarGz(jreDirectoryPath.toString(), osAndArchSpecificDistPath, "")
      buildContext.bundledRuntime.checkExecutablePermissions(tarGzPath, getRootDirectoryName(), OsFamily.LINUX)

      if (jreDirectoryPath != null) {
        buildSnapPackage(jreDirectoryPath.toString(), osAndArchSpecificDistPath)
      }
      else {
        buildContext.messages.info("Skipping building Snap packages because no modular JRE are available")
      }
      Path tempTar = Files.createTempDirectory(buildContext.paths.tempDir, "tar-")
      try {
        ArchiveUtils.unTar(tarGzPath, tempTar)
        String tarRoot = customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
        RepairUtilityBuilder.generateManifest(buildContext, tempTar.resolve(tarRoot), tarGzPath.fileName.toString())
      }
      finally {
        NioFiles.deleteRecursively(tempTar)
      }
    }
  }

  private void generateVMOptions(Path distBinDir) {
    String fileName = "${buildContext.productProperties.baseFileName}64.vmoptions"
    List<String> vmOptions = VmOptionsGenerator.computeVmOptions(buildContext.applicationInfo.isEAP(), buildContext.productProperties) +
                             ['-Dsun.tools.attach.tmp.only=true']
    Files.writeString(distBinDir.resolve(fileName), String.join('\n', vmOptions) + '\n', StandardCharsets.US_ASCII)
  }

  private void generateReadme(Path unixDistPath) {
    String fullName = buildContext.applicationInfo.productName

    Path sourceFile = buildContext.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/Install-Linux-tar.txt")
    BuildUtils.assertUnixLineEndings(sourceFile)

    Path targetFile = unixDistPath.resolve("Install-Linux-tar.txt")

    FileKt.substituteTemplatePlaceholders(
      sourceFile,
      targetFile,
      "@@",
      [
        new Pair<String, String>("product_full", fullName),
        new Pair<String, String>("product", buildContext.productProperties.baseFileName),
        new Pair<String, String>("product_vendor", buildContext.applicationInfo.shortCompanyName),
        new Pair<String, String>("system_selector", buildContext.systemSelector),
      ]
    )
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
      patterns += buildContext.bundledRuntime.executableFilesPatterns(OsFamily.LINUX)
    }
    return patterns
  }

  @Override
  List<String> getArtifactNames(BuildContext context) {
    List<String> suffixes = []
    if (customizer.buildTarGzWithoutBundledRuntime) {
      suffixes += NO_JBR_SUFFIX
    }
    if (!customizer.buildOnlyBareTarGz) {
      suffixes += ""
    }
    return suffixes.collect { artifactName(context, it) }
  }

  private static String artifactName(BuildContext buildContext, String suffix) {
    def baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
    return "${baseName}${suffix}.tar.gz"
  }

  private String getRootDirectoryName() {
    return customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
  }

  private Path buildTarGz(@Nullable String jreDirectoryPath, Path unixDistPath, String suffix) {
    def tarRoot = getRootDirectoryName()
    def tarName = artifactName(buildContext, suffix)
    Path tarPath = buildContext.paths.artifactDir.resolve(tarName)
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
      paths.each {
        BuildTasksImpl.updateExecutablePermissions(Paths.get(it), executableFilesPatterns)
      }
      ArchiveUtils.tar(tarPath, tarRoot, paths, buildContext.options.buildDateInSeconds)
      ProductInfoValidator.checkInArchive(buildContext, tarPath, tarRoot)
      buildContext.notifyArtifactBuilt(tarPath)
      return tarPath
    }
  }

  private void generateProductJson(@NotNull Path targetDir, String javaExecutablePath) {
    def scriptName = buildContext.productProperties.baseFileName

    Path file = targetDir.resolve(ProductInfoGenerator.FILE_NAME)
    Files.createDirectories(targetDir)
    Files.write(file, new ProductInfoGenerator(buildContext).generateMultiPlatformProductJson(
      "bin",
      buildContext.builtinModule,
      [
        new ProductInfoLaunchData(
          os: OsFamily.LINUX.osName,
          startupWmClass: getFrameClass(buildContext),
          launcherPath: "bin/${scriptName}.sh",
          javaExecutablePath: javaExecutablePath,
          vmOptionsFilePath: "bin/${scriptName}64.vmoptions",
          )])
    )
  }

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

      String productName = buildContext.applicationInfo.productNameWithEdition

      FileKt.substituteTemplatePlaceholders(
        buildContext.paths.communityHomeDir.resolve("platform/platform-resources/src/entry.desktop"),
        snapDir.resolve("${customizer.snapName}.desktop"),
        "\$",
        [
          new Pair<String, String>("NAME", productName),
          new Pair<String, String>("ICON", "\${SNAP}/bin/${buildContext.productProperties.baseFileName}.png".toString()),
          new Pair<String, String>("SCRIPT", customizer.snapName),
          new Pair<String, String>("COMMENT", buildContext.applicationInfo.motto),
          new Pair<String, String>("WM_CLASS", getFrameClass(buildContext)),
        ]
      )

      BuildHelper.moveFile(iconPngPath, snapDir.resolve(customizer.snapName + ".png"))

      Path snapcraftTemplate = buildContext.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/snap/snapcraft-template.yaml")
      String versionSuffix = buildContext.applicationInfo.versionSuffix?.replace(' ', '-') ?: ""
      String version = "${buildContext.applicationInfo.majorVersion}.${buildContext.applicationInfo.minorVersion}${versionSuffix.isEmpty() ? "" : "-${versionSuffix}"}"

      FileKt.substituteTemplatePlaceholders(
        snapcraftTemplate,
        snapDir.resolve("snapcraft.yaml"),
        "\$",
        [
          new Pair<String, String>("NAME", customizer.snapName),
          new Pair<String, String>("VERSION", version),
          new Pair<String, String>("SUMMARY", productName),
          new Pair<String, String>("DESCRIPTION", customizer.snapDescription),
          new Pair<String, String>("GRADE", buildContext.applicationInfo.isEAP() ? "devel" : "stable"),
          new Pair<String, String>("SCRIPT", "bin/${buildContext.productProperties.baseFileName}.sh".toString()),
        ]
      )

      new FileSet(unixSnapDistPath)
        .include("bin/*.sh")
        .include("bin/*.py")
        .include("bin/fsnotifier*")
        .enumerate().each {makeFileExecutable(it) }

      new FileSet(Path.of(jreDirectoryPath))
        .include("jbr/bin/*")
        .enumerate().each {makeFileExecutable(it) }

      for (Path distPath: [unixSnapDistPath, buildContext.paths.distAllDir]) {
        new FileSet(distPath)
          .tap {
            customizer.extraExecutables.each { include(it) }
          }
          .enumerateNoAssertUnusedPatterns().each {makeFileExecutable(it) }
      }

      generateProductJson(unixSnapDistPath, "jbr/bin/java")
      new ProductInfoValidator(buildContext).validateInDirectory(unixSnapDistPath,
                                                                 "",
                                                                 List.of(unixSnapDistPath, Path.of(jreDirectoryPath)),
                                                                 [])

      Path resultDir = snapDir.resolve("result")
      Files.createDirectories(resultDir)
      buildContext.messages.progress("Building package")

      String snapArtifact = "${customizer.snapName}_${version}_amd64.snap".toString()
      BuildHelper.runProcess(
        buildContext,
        [
          "docker",
          "run",
          "--rm",
          "--volume=${snapDir}/snapcraft.yaml:/build/snapcraft.yaml:ro".toString(),
          "--volume=${snapDir}/${customizer.snapName}.desktop:/build/snap/gui/${customizer.snapName}.desktop:ro".toString(),
          "--volume=${snapDir}/${customizer.snapName}.png:/build/prime/meta/gui/icon.png:ro".toString(),
          "--volume=${snapDir}/result:/build/result".toString(),
          "--volume=${buildContext.paths.distAll}:/build/dist.all:ro".toString(),
          "--volume=${unixSnapDistPath}:/build/dist.unix:ro".toString(),
          "--volume=${jreDirectoryPath}:/build/jre:ro".toString(),
          "--workdir=/build",
          buildContext.options.snapDockerImage,
          "snapcraft",
          "snap",
          "-o",
          "result/$snapArtifact".toString(),
        ],
        snapDir
      )

      BuildHelper.moveFileToDir(resultDir.resolve(snapArtifact), buildContext.paths.artifactDir)
      buildContext.notifyArtifactWasBuilt(buildContext.paths.artifactDir.resolve(snapArtifact))
    }
  }

  private void makeFileExecutable(Path file) {
    buildContext.messages.debug("Setting file permission of $file to 0755")
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"))
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
