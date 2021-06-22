// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoGenerator
import org.jetbrains.intellij.build.impl.productInfo.ProductInfoValidator
import org.tukaani.xz.XZ

import java.nio.charset.StandardCharsets
import java.nio.file.*

@CompileStatic
final class LinuxDistributionBuilder extends OsSpecificDistributionBuilder {
  private final LinuxDistributionCustomizer customizer
  private final Path ideaProperties
  private final String iconPngPath

  LinuxDistributionBuilder(BuildContext buildContext, LinuxDistributionCustomizer customizer, Path ideaProperties) {
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
  @CompileStatic(TypeCheckingMode.SKIP)
  void copyFilesForOsDistribution(@NotNull Path unixDistPath, JvmArchitecture arch = null) {
    buildContext.messages.progress("Building distributions for $targetOs.osName")

    Path distBinDir = unixDistPath.resolve("bin")

    buildContext.ant.copy(todir: distBinDir.toString()) {
      fileset(dir: "$buildContext.paths.communityHome/bin/linux")
    }
    BuildTasksImpl.unpackPty4jNative(buildContext, unixDistPath, "linux")
    BuildTasksImpl.generateBuildTxt(buildContext, unixDistPath)
    BuildTasksImpl.copyDistFiles(buildContext, unixDistPath)
    List<String> extraJars = BuildTasksImpl.addDbusJava(buildContext, unixDistPath)
    if (buildContext.productProperties.addRemoteDevelopmentLibraries) {
      extraJars.addAll(BuildTasksImpl.addProjectorServer(buildContext, unixDistPath))
      prepareSelfContainedRemoteDevelopmentFiles(buildContext, unixDistPath)
    }
    BuildTasksImpl.appendLibsToClasspathJar(buildContext, unixDistPath, extraJars)
    Files.copy(ideaProperties, distBinDir.resolve(ideaProperties.fileName), StandardCopyOption.REPLACE_EXISTING)
    //todo[nik] converting line separators to unix-style make sense only when building Linux distributions under Windows on a local machine;
    // for real installers we need to checkout all text files with 'lf' separators anyway
    BuildUtils.convertLineSeparators(unixDistPath.resolve("bin/idea.properties"), "\n")
    if (iconPngPath != null) {
      Files.copy(Paths.get(iconPngPath), distBinDir.resolve("${buildContext.productProperties.baseFileName}.png"), StandardCopyOption.REPLACE_EXISTING)
    }
    generateScripts(distBinDir)
    generateVMOptions(distBinDir)
    generateReadme(unixDistPath)
    generateVersionMarker(unixDistPath)
    customizer.copyAdditionalFiles(buildContext, unixDistPath)
  }

  @Override
  void buildArtifacts(Path osSpecificDistPath) {
    copyFilesForOsDistribution(osSpecificDistPath)
    buildContext.executeStep("Build Linux .tar.gz", BuildOptions.LINUX_ARTIFACTS_STEP) {
      if (customizer.buildTarGzWithoutBundledJre) {
        buildContext.executeStep("Build Linux .tar.gz without bundled JRE", BuildOptions.LINUX_TAR_GZ_WITHOUT_BUNDLED_JRE_STEP) {
          buildTarGz(null, osSpecificDistPath, "-no-jbr")
        }
      }

      if (customizer.buildOnlyBareTarGz) return

      Path jreDirectoryPath = buildContext.bundledJreManager.extractJre(OsFamily.LINUX)
      buildTarGz(jreDirectoryPath.toString(), osSpecificDistPath, "")

      if (jreDirectoryPath != null) {
        buildSnapPackage(jreDirectoryPath.toString(), osSpecificDistPath)
      }
      else {
        buildContext.messages.info("Skipping building Snap packages because no modular JRE are available")
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void generateScripts(@NotNull Path distBinDir) {
    String fullName = buildContext.applicationInfo.productName
    String baseName = buildContext.productProperties.baseFileName
    String scriptName = "${baseName}.sh"
    String vmOptionsFileName = baseName

    String classPath = "CLASSPATH=\"\$IDE_HOME/lib/${buildContext.bootClassPathJarNames[0]}\"\n"
    classPath += buildContext.bootClassPathJarNames[1..-1].collect { "CLASSPATH=\"\$CLASSPATH:\$IDE_HOME/lib/${it}\"" }.join("\n")
    if (buildContext.productProperties.toolsJarRequired) {
      classPath += "\nCLASSPATH=\"\$CLASSPATH:\$JDK/lib/tools.jar\""
    }

    buildContext.ant.copy(todir: distBinDir.toString()) {
      fileset(dir: "$buildContext.paths.communityHome/platform/build-scripts/resources/linux/scripts")

      filterset(begintoken: "__", endtoken: "__") {
        filter(token: "product_full", value: fullName)
        filter(token: "product_uc", value: buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo))
        filter(token: "product_vendor", value: buildContext.applicationInfo.shortCompanyName)
        filter(token: "vm_options", value: vmOptionsFileName)
        filter(token: "system_selector", value: buildContext.systemSelector)
        filter(token: "ide_jvm_args", value: buildContext.additionalJvmArguments.join(' '))
        filter(token: "class_path", value: classPath)
        filter(token: "script_name", value: scriptName)
      }
    }

    Files.move(distBinDir.resolve("executable-template.sh"), distBinDir.resolve(scriptName), StandardCopyOption.REPLACE_EXISTING)
    BuildTasksImpl.copyInspectScript(buildContext, distBinDir)

    buildContext.ant.fixcrlf(srcdir: distBinDir.toString(), includes: "*.sh", eol: "unix")
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
      Paths.get(buildContext.paths.communityHome, "platform/build-scripts/resources/linux/Install-Linux-tar.txt"),
      unixDistPath.resolve("Install-Linux-tar.txt"),
      ["product_full"   : fullName,
       "product"        : buildContext.productProperties.baseFileName,
       "product_vendor" : buildContext.applicationInfo.shortCompanyName,
       "system_selector": buildContext.systemSelector], "@@", "\n")
  }

  // please keep in sync with `SystemHealthMonitor#checkInstallationIntegrity`
  @CompileStatic
  private void generateVersionMarker(Path unixDistPath) {
    Path targetDir = unixDistPath.resolve("lib")
    Files.writeString(targetDir.resolve("build-marker-${buildContext.fullBuildNumber}"), buildContext.fullBuildNumber)
    Files.createDirectories(targetDir)
  }

  @Override
  List<String> generateExecutableFilesPatterns(boolean includeJre) {
    def patterns = [
      "bin/*.sh",
      "bin/*.py",
      "bin/fsnotifier*",
      "remotedevelopment/launcher.sh",
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

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildTarGz(String jreDirectoryPath, Path unixDistPath, String suffix) {
    def tarRoot = customizer.getRootDirectoryName(buildContext.applicationInfo, buildContext.buildNumber)
    def baseName = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
    def tarPath = "${buildContext.paths.artifacts}/${baseName}${suffix}.tar.gz"
    def paths = [buildContext.paths.distAll, unixDistPath.toString()]

    String javaExecutablePath = null
    if (jreDirectoryPath != null) {
      paths += jreDirectoryPath
      javaExecutablePath = "jbr/bin/java"
    }
    boolean hasPatchedClasspathTxt = Files.exists(unixDistPath.resolve("lib/classpath.txt"))
    def productJsonDir = new File(buildContext.paths.temp, "linux.dist.product-info.json$suffix").absolutePath
    generateProductJson(Paths.get(productJsonDir), javaExecutablePath)
    paths += productJsonDir

    def executableFilesPatterns = generateExecutableFilesPatterns(jreDirectoryPath != null)
    def description = "archive${jreDirectoryPath != null ? "" : " (without JRE)"}"
    buildContext.messages.block("Build Linux tar.gz $description") {
      buildContext.messages.progress("Building Linux tar.gz $description")
      buildContext.ant.tar(tarfile: tarPath, longfile: "gnu", compression: "gzip") {
        paths.each { path ->
          tarfileset(dir: path, prefix: tarRoot) {
            executableFilesPatterns.each {pattern ->
              exclude(name: pattern)
            }
            if (hasPatchedClasspathTxt && path == buildContext.paths.distAll) {
              exclude(name: "lib/classpath.txt")
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
    }
  }

  private void generateProductJson(@NotNull Path targetDir, String javaExecutablePath) {
    def scriptName = buildContext.productProperties.baseFileName
    new ProductInfoGenerator(buildContext).generateProductJson(
      targetDir, "bin", getFrameClass(buildContext), "bin/${scriptName}.sh", javaExecutablePath, "bin/${scriptName}64.vmoptions", OsFamily.LINUX)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private void buildSnapPackage(String jreDirectoryPath, Path unixDistPath) {
    if (!buildContext.options.buildUnixSnaps || customizer.snapName == null) return

    if (StringUtil.isEmpty(iconPngPath)) buildContext.messages.error("'iconPngPath' not set")
    if (StringUtil.isEmpty(customizer.snapDescription)) buildContext.messages.error("'snapDescription' not set")

    String snapDir = "${buildContext.paths.buildOutputRoot}/dist.snap"

    buildContext.messages.block("Build Linux .snap package") {
      buildContext.messages.progress("Preparing files")

      String unixSnapDistPath = "$buildContext.paths.buildOutputRoot/dist.unix.snap"
      buildContext.ant.copy(todir: unixSnapDistPath) {
        fileset(dir: unixDistPath.toString())
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
      def versionSuffix = buildContext.applicationInfo.versionSuffix?.replace(' ', '-') ?: ""
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

      Path unixSnapDistDir = Paths.get(unixSnapDistPath)
      generateProductJson(unixSnapDistDir, "jbr/bin/java")
      new ProductInfoValidator(buildContext).validateInDirectory(unixSnapDistDir, "", [unixSnapDistPath, jreDirectoryPath], [])

      Files.createDirectories(Paths.get(snapDir, "result"))
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
        arg(value: "--volume=${unixSnapDistPath}/lib/classpath.txt:/build/dist.all/lib/classpath.txt:ro")
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

  @CompileStatic(TypeCheckingMode.SKIP)
  static void prepareSelfContainedRemoteDevelopmentFiles(BuildContext buildContext, @NotNull Path distDir) {
    // Create destination directory
    Path remoteDevelopmentDirPath = distDir.resolve("remotedevelopment")
    Path selfContainedDirPath = remoteDevelopmentDirPath.resolve("selfcontained")

    if (Files.notExists(selfContainedDirPath)) {
      Files.createDirectories(selfContainedDirPath)
    }

    // Download self-contained libraries
    downloadSelfContainedLibrariesForRemoteDevelopment(buildContext, selfContainedDirPath)

    // Copy launcher.sh script from sources into the distribution structure
    Path launcherSourcePath = Path.of("${buildContext.paths.communityHome}/platform/build-scripts/resources/linux/remotedevelopment/launcher.sh")
    Path launcherTargetPath = remoteDevelopmentDirPath.resolve(launcherSourcePath.fileName)
    copyFile(launcherSourcePath, launcherTargetPath)
  }

  static void copyFile(Path source, Path target) {
    Path parent = target.parent
    if (parent != null)
      Files.createDirectories(parent)

    if (Files.isSymbolicLink(source))
      Files.copy(source, target, LinkOption.NOFOLLOW_LINKS)
    else
      Files.copy(source, target)
  }

  /**
   * Note: libXext.so.6, libX11.so.6, libxcb.so.1, libXau.so.6 are required in CentOS7 (min supported self-contained version)
   */
  private static void downloadSelfContainedLibrariesForRemoteDevelopment(BuildContext buildContext, @NotNull Path distDir) {
    ArrayList<LinuxLibraryDownloadInfo> failedDownloads = new ArrayList<>()
    ArrayList<LinuxLibraryDownloadInfo> downloadLibsInfo = new ArrayList<>()
    downloadLibsInfo.addAll(
      new LinuxLibraryDownloadInfo("freetype", "2.4.11-9"),
      new LinuxLibraryDownloadInfo("libX11", "1.6.0-2.1"),
      new LinuxLibraryDownloadInfo("libXau", "1.0.8-2.1"),
      new LinuxLibraryDownloadInfo("libxcb", "1.9-5"),
      new LinuxLibraryDownloadInfo("libXext", "1.3.2-2.1"),
      new LinuxLibraryDownloadInfo("libXi", "1.7.2-2.1"),
      new LinuxLibraryDownloadInfo("libXrender", "0.9.8-2.1"),
      new LinuxLibraryDownloadInfo("libXtst", "1.2.2-2.1"),
      new LinuxLibraryDownloadInfo("fontconfig", "2.10.95-7", { Path source, Path target ->
        // Copy Linux .so libraries
        Path sourceLibDirectory = source.resolve("usr/lib64")
        Path targetLibsPath = Files.createDirectories(target.resolve("libs"))
        sourceLibDirectory.eachFileRecurse { Path libEntryPath ->
          if (Files.isSymbolicLink(libEntryPath)) {
            Path relativeLibPath = Files.readSymbolicLink(libEntryPath)
            Path sourceLibPath = libEntryPath.parent.resolve(relativeLibPath)

            if (Files.exists(sourceLibPath)) {
              Path targetLibPath = targetLibsPath.resolve(libEntryPath.fileName.toString())
              copyFile(sourceLibPath, targetLibPath)
            }
          }
        }

        // Copy fonts.config file
        Path sourceConfigFile = source.resolve("etc").resolve("fonts").resolve("fonts.conf")
        if (!Files.exists(sourceConfigFile))
          buildContext.messages.error("Source fonts config file not found: '$sourceConfigFile'")

        Path targetConfigDirectory = Files.createDirectories(target.resolve("fontconfig"))
        Files.copy(sourceConfigFile, targetConfigDirectory.resolve(sourceConfigFile.fileName))
      }),
      new LinuxLibraryDownloadInfo("dejavu-lgc-sans-fonts", "2.33-6", "noarch", { Path source, Path target ->
        String fontName = "DejaVuLGCSans.ttf"
        Path sourceFontPath = source.resolve("usr/share/fonts/dejavu").resolve(fontName)
        if (Files.notExists(sourceFontPath))
          buildContext.messages.error("Unable to find source font: '$sourceFontPath'".toString())

        Path targetFontsDirectory = Files.createDirectories(target.resolve("fontconfig/fonts"))
        Path targetFontPath = targetFontsDirectory.resolve(fontName)

        copyFile(sourceFontPath, targetFontPath)
      })
    )

    Path tempDirectory = Files.createTempDirectory("cwm-remote-development-linux-libs-")
    try {
      downloadLibsInfo.forEach { info ->
        URI uri = info.uri
        try {
          Path targetFile = tempDirectory.resolve("${info.libraryName}-${info.version}.${info.fileExtension}")
          BuildDependenciesDownloader.downloadFile(uri, targetFile)
          info.downloadPath = targetFile
        }
        catch (Throwable ignored) {
          failedDownloads.add(info)
        }
      }

      if (!failedDownloads.isEmpty()) {
        String message = "Download has failed for ${failedDownloads.size()} lib(s): ${StringUtil.join(failedDownloads, { info -> "${info.libraryName}".toString() }, ", ")}"
        buildContext.messages.error(message)
      }

      for (info in downloadLibsInfo) {
        Path downloadPath = info.downloadPath
        if (downloadPath == null) {
          buildContext.messages.error("Unable to get download path for a library: ${info.libraryName}")
        }

        String archiveName = downloadPath.fileName.toString()
        String archiveNameWithoutExtension = archiveName.substring(0, archiveName.lastIndexOf('.'))
        Path archiveContentDirPath = downloadPath.parent.resolve(archiveNameWithoutExtension)

        unrpm(downloadPath, archiveContentDirPath)
        info.getLibraryCopyAction().call(archiveContentDirPath, distDir)
      }
    } finally {
      println("Delete temp Linux libraries directory: '$tempDirectory'")
      tempDirectory.deleteDir()
    }
  }

  static void unrpm(Path rpmFilePath, Path destinationFilePath) {
    Files.newInputStream(rpmFilePath).withCloseable { fileStream ->
      new BufferedInputStream(fileStream).withCloseable { bufferedStream ->
        byte[] xzHeaderMagic = XZ.HEADER_MAGIC
        new PushbackInputStream(bufferedStream, xzHeaderMagic.size()).withCloseable { pbStream ->
          rewindStreamToBytes(pbStream, xzHeaderMagic)
          pbStream.unread(xzHeaderMagic)
          getXzArchiveDataFromRpmStream(pbStream, destinationFilePath)
        }
      }
    }
  }

  private static rewindStreamToBytes(InputStream inputStream, byte[] searchBytes) {
    int searchIndex = 0
    byte searchByte = searchBytes[searchIndex]

    int currentInt = inputStream.read()
    while (currentInt != -1) {
      if (currentInt.byteValue() == searchByte) {
        if (searchIndex == searchBytes.length - 1) {
          return
        }
        searchByte = searchBytes[++searchIndex]
      } else {
        searchIndex = 0
        searchByte = searchBytes[searchIndex]
      }
      currentInt = inputStream.read()
    }
  }

  /**
   * Extract CPIO archive content.
   */
  private static getXzArchiveDataFromRpmStream(InputStream inputStream, Path filePath) {
    XZCompressorInputStream xzStream = new XZCompressorInputStream(inputStream)
    try {
      // Unable to use .withClosable() closure here since it failed on a TC with failures on static call.
      CpioArchiveInputStream cpioStream = new CpioArchiveInputStream(xzStream)
      try {
        CpioArchiveEntry entry = cpioStream.nextCPIOEntry

        while (entry != null) {

          long entrySize = entry.size
          Path entryPath = Path.of(entry.name)
          String entryName = entryPath.fileName.toString()
          Path destination = filePath.resolve(entryPath)

          println("CPIO archive entry: $entryName")

          // Create directories path
          Files.createDirectories(destination.parent)

          if (entry.isDirectory()) {
            Files.createDirectories(destination)
          } else if (entry.isSymbolicLink()) {
            // Entry data contains the relative path to a link target file
            byte[] bytes = new byte[entrySize.toInteger()]
            cpioStream.read(bytes)
            Path targetPath = Path.of(new String(bytes))
            Files.createSymbolicLink(destination, targetPath)
          } else {
            destination.newOutputStream().withCloseable { output ->
              byte[] bytes = new byte[entrySize.toInteger()]
              int readTotal = 0

              while (readTotal < entrySize) {
                int read = cpioStream.read(bytes)
                output.write(bytes, 0, read)
                readTotal += read
              }
            }
          }

          entry = cpioStream.nextCPIOEntry
        }
      } finally {
        cpioStream.close()
      }
    } finally {
      xzStream.close()
    }
  }
}
