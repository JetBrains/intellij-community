// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture

import java.util.concurrent.ConcurrentHashMap

/**
 * @author nik
 */
@CompileStatic
class BundledJreManager {
  private final BuildContext buildContext
  String baseDirectoryForJre

  BundledJreManager(BuildContext buildContext, String baseDirectoryForJre) {
    this.buildContext = buildContext
    this.baseDirectoryForJre = baseDirectoryForJre
  }

  /**
   * Extract JRE for Linux distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String extractSecondBundledJreForLinux() {
    return extractSecondBundledJre("linux")
  }

  boolean doBundleSecondJre() {
    return System.getProperty('intellij.build.bundle.second.jre', 'false').toBoolean()
  }

  private String getJreBuild(String osName) {
    loadDependencyVersions()
    return dependencyVersions.get("jreBuild_${osName}" as String, buildContext.options.bundledJreBuild ?: dependencyVersions.get("jdkBuild", ""))
  }

  private int getJreVersion() {
    return buildContext.options.bundledJreVersion
  }

  private int getSecondBundledJreVersion() {
    return 8
  }

  String extractJre(String osName) {
    String targetDir = "$baseDirectoryForJre/secondJre.${osName}_${JvmArchitecture.x64}"
    if (new File(targetDir).exists()) {
      buildContext.messages.info("JRE is already extracted to $targetDir")
      return targetDir
    }
    File archive = findArchive(osName, getJreBuild(osName), getJreVersion(), null, JvmArchitecture.x64, JreVendor.JetBrains)
    if (archive == null) {
      return null
    }
    buildContext.messages.block("Extract $archive.absolutePath JRE") {
      String destination = "$targetDir/jbr"
      def destinationDir = new File(destination)
      if (destinationDir.exists()) destinationDir.deleteDir()
      untar(archive, destination, isBundledJreModular())
    }
    return targetDir
  }

  /**
   * Extract JRE for Windows distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String extractSecondBundledJreForWin(JvmArchitecture arch) {
    return extractSecondBundledJre("windows", arch)
  }

  /**
   * Extract Oracle JRE for Windows distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String extractSecondBundledOracleJreForWin(JvmArchitecture arch) {
    return extractSecondBundledJre("windows", arch, JreVendor.Oracle)
  }

  /**
   * Return path to a .tar.gz archive containing distribution of JRE for macOS which will be bundled with the product
   */
  String findSecondBundledJreArchiveForMac() {
    return findSecondBundledJreArchive("osx")?.absolutePath
  }

  /**
   * Return a .tar.gz archive containing distribution of JRE for Win OS which will be bundled with the product
   */
  File findSecondBundledJreArchiveForWin(JvmArchitecture arch) {
    return findSecondBundledJreArchive("windows", arch)
  }

  File findJreArchive(String osName, JvmArchitecture arch = JvmArchitecture.x64) {
    return findArchive(osName, getJreBuild(osName), getJreVersion(), null, arch, JreVendor.JetBrains)
  }

  String archiveNameJre(BuildContext buildContext) {
    return "jre-for-${buildContext.buildNumber}.tar.gz"
  }

  private String extractSecondBundledJre(String osName, JvmArchitecture arch = JvmArchitecture.x64, JreVendor vendor = JreVendor.JetBrains) {
    String vendorSuffix = vendor == JreVendor.Oracle ? ".oracle" : ""
    String targetDir = arch == JvmArchitecture.x64 ?
                       "$baseDirectoryForJre/jre.$osName$arch.fileSuffix$vendorSuffix" :
                       "$baseDirectoryForJre/jre.${osName}32$vendorSuffix"
    if (new File(targetDir).exists()) {
      buildContext.messages.info("JRE is already extracted to $targetDir")
      return targetDir
    }

    File archive = findSecondBundledJreArchive(osName, arch, vendor)
    if (archive == null) {
      return null
    }
    buildContext.messages.block("Extract $archive.name JRE") {
      String destination = "$targetDir/jbr"
      if (osName == "windows" && arch == JvmArchitecture.x32) {
        destination = "$targetDir/jre32"
      }
      buildContext.messages.progress("Extracting JRE from '$archive.name' archive")
      untar(archive, destination, isSecondBundledJreModular())
    }
    return targetDir
  }

  /**
   * @param archive linux or windows JRE archive
   */
  @CompileDynamic
  private def untar(File archive, String destination, boolean isModular) {
    // strip `jre` root directory for jbr8
    def stripRootDir = !isModular ||
                       // or `jbr` root directory for jbr11+
                       buildContext.bundledJreManager.hasJbrRootDir(archive)
    if (SystemInfo.isWindows) {
      buildContext.ant.untar(src: archive.absolutePath, dest: destination, compression: 'gzip') {
        if (stripRootDir) {
          cutdirsmapper(dirs: 1)
        }
      }
    }
    else {
      // 'tar' command is used instead of Ant task to ensure that executable flags will be preserved
      buildContext.ant.mkdir(dir: destination)
      buildContext.ant.exec(executable: "tar", dir: archive.parent) {
        arg(value: "-xf")
        arg(value: archive.name)
        if (stripRootDir) {
          arg(value: "--strip")
          arg(value: "1")
        }
        arg(value: "--directory")
        arg(value: destination)
      }
    }
  }

  private File dependenciesDir() {
    new File(buildContext.paths.communityHome, 'build/dependencies')
  }

  File jreDir() {
    def dependenciesDir = dependenciesDir()
    new File(dependenciesDir, 'build/jbre')
  }

  /**
   * Update this method together with:
   *  `build/dependencies/setupJbre.gradle`
   *  `build/dependencies/setupJdk.gradle`
  */
  private String jreArchiveSuffix(String jreBuild, int version, JvmArchitecture arch, String osName) {
    String update, build
    def split = jreBuild.split('b')
    if (split.length > 2) {
      throw new IllegalArgumentException(
        "$jreBuild is expected in format <update>b<build_number>. Examples: u202b1483.24, 11_0_2b140, b96"
      )
    }
    if (split.length == 2) {
      update = split[0]
      if (update.startsWith(version.toString())) update -= version
      // [11_0_2, b140] or [8u202, b1483.24]
      (update, build) = ["$version$update", "b${split[1]}"]
    }
    else {
      // [11, b96]
      (update, build) = [version.toString(), jreBuild]
    }
    "${update}-${osName}-${arch == JvmArchitecture.x32 ? 'i586' : 'x64'}-${build}.tar.gz"
  }

  private File findSecondBundledJreArchive(String osName, JvmArchitecture arch = JvmArchitecture.x64, JreVendor vendor = JreVendor.JetBrains) {
    return findArchive(osName, secondBundledJreBuild, secondBundledJreVersion,
                       System.getProperty("intellij.build.bundled.jre.prefix"),
                       arch, vendor)
  }

  private File findArchive(String osName, String jreBuild,
                           int jreVersion, String jrePrefix,
                           JvmArchitecture arch, JreVendor vendor) {
    def jreDir = jreDir()
    String suffix = jreArchiveSuffix(jreBuild, jreVersion, arch, osName)
    if (jrePrefix == null) {
      jrePrefix = jreVersion >= 9 ? vendor.jreNamePrefix :
               buildContext.productProperties.toolsJarRequired ? vendor.jreWithToolsJarNamePrefix : vendor.jreNamePrefix
    }
    def jreArchive = new File(jreDir, "$jrePrefix$suffix")
    if (!jreArchive.file || !jreArchive.exists()) {
      def errorMessage = "Cannot extract $osName JRE: file $jreArchive is not found (${jreDir.listFiles()})"
      if (buildContext.options.isInDevelopmentMode) {
        buildContext.messages.warning(errorMessage)
      }
      else {
        buildContext.messages.error(errorMessage)
      }
      return null
    }
    return jreArchive
  }

  private Properties dependencyVersions
  private synchronized void loadDependencyVersions() {
    if (dependencyVersions == null) {
      buildContext.gradle.run('Preparing dependencies file', 'dependenciesFile')

      def stream = new File(dependenciesDir(), 'build/dependencies.properties').newInputStream()
      try {
        Properties properties = new Properties()
        properties.load(stream)
        dependencyVersions = properties
      }
      finally {
        stream.close()
      }
    }
  }

  private String getSecondBundledJreBuild() {
    if (!doBundleSecondJre()) {
      throw new IllegalArgumentException("Second JBR won't be bundled, unable to determine build")
    }
    def build = System.getProperty("intellij.build.bundled.second.jre.build")
    if (build == null) {
      loadDependencyVersions()
      build = dependencyVersions.get('secondJreBuild')
    }
    return build
  }

  private enum JreVendor {
    Oracle("jre", "jdk"),
    JetBrains("jbr-", "jbrx-")

    final String jreNamePrefix
    final String jreWithToolsJarNamePrefix

    JreVendor(String jreNamePrefix, String jreWithToolsJarNamePrefix) {
      this.jreNamePrefix = jreNamePrefix
      this.jreWithToolsJarNamePrefix = jreWithToolsJarNamePrefix
    }
  }

  String secondJreSuffix() {
    return "-jbr8"
  }

  /**
   *  If {@code true} then bundled JRE version is 9+
   */
  boolean isBundledJreModular() {
    return buildContext.options.bundledJreVersion >= 9
  }

  /**
   *  If {@code true} then second bundled JRE version is 9+
   */
  boolean isSecondBundledJreModular() {
    return secondBundledJreVersion.toInteger() >= 9
  }

  private final Map<File, Boolean> jbrArchiveInspectionCache = new ConcurrentHashMap<>()

  /**
   * If {@code true} then JRE top directory was renamed to JBR, see JBR-1295
   */
  boolean hasJbrRootDir(File archive) {
    jbrArchiveInspectionCache.computeIfAbsent(archive) {
      def tarArchive = new TarArchiveInputStream(
        new CompressorStreamFactory().createCompressorInputStream(
          new BufferedInputStream(new FileInputStream(archive))
        ))
      def entry = tarArchive.nextTarEntry?.name
      if (entry == null) throw new IllegalStateException("Unable to read $archive")
      entry.startsWith('jbr')
    }
  }
}