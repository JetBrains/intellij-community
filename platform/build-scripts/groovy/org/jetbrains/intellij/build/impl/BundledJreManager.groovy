// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture

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
  String extractLinuxJre() {
    return extractJre("linux")
  }

  private static boolean doBundleSecondJre() {
    return System.getProperty('intellij.build.bundle.second.jre', 'false').toBoolean()
  }

  String getSecondJreBuild() {
    if (!doBundleSecondJre()) return null
    def build = System.getProperty("intellij.build.bundled.second.jre.build")
    if (build == null) {
      loadDependencyVersions()
      build = dependencyVersions.get('secondJreBuild')
    }
    return build
  }

  String getSecondJreVersion() {
    if (!doBundleSecondJre()) return null
    def version = System.getProperty("intellij.build.bundled.second.jre.version")
    if (version == null) {
      loadDependencyVersions()
      version = dependencyVersions.get('secondJreVersion')
    }
    return version
  }

  @CompileDynamic
  String extractSecondJre(String osName, String secondJreBuild) {
    String targetDir = "$baseDirectoryForJre/secondJre.${osName}_${JvmArchitecture.x64}"
    if (new File(targetDir).exists()) {
      buildContext.messages.info("JRE is already extracted to $targetDir")
      return targetDir
    }
    def jreArchive = "jbr-${jreArchiveSuffix(secondJreBuild, getSecondJreVersion(), JvmArchitecture.x64, osName)}"
    File archive = new File(jreDir(), jreArchive)
    if (!archive.file || !archive.exists()) {
      def errorMessage = "Cannot extract $osName JRE: file $jreArchive is not found"
      buildContext.messages.warning(errorMessage)
    }
    if (archive == null) {
      return null
    }

    buildContext.messages.block("Extract $archive.absolutePath JRE") {
      String destination = "$targetDir/jre64"
      def destinationDir = new File(destination)
      if (destinationDir.exists()) destinationDir.deleteDir()

      if (SystemInfo.isWindows) {
        buildContext.ant.untar(src: archive.absolutePath, dest: destination, compression: 'gzip')
      }
      else {
        //'tar' command is used instead of Ant task to ensure that executable flags will be preserved
        buildContext.ant.mkdir(dir: destination)
        buildContext.ant.exec(executable: "tar", dir: archive.parent) {
          arg(value: "-xf")
          arg(value: archive.name)
          arg(value: "--directory")
          arg(value: destination)
        }
      }
    }
    return targetDir
  }

  /**
   * Extract JRE for Windows distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String extractWinJre(JvmArchitecture arch) {
    return extractJre("windows", arch)
  }

  /**
   * Extract Oracle JRE for Windows distribution of the product
   * @return path to the directory containing 'jre' subdirectory with extracted JRE
   */
  String extractOracleWinJre(JvmArchitecture arch) {
    return extractJre("windows", arch, JreVendor.Oracle)
  }

  /**
   * Return path to a .tar.gz archive containing distribution of JRE for macOS which will be bundled with the product
   */
  String findMacJreArchive() {
    return findJreArchive("osx")?.absolutePath
  }

  /**
   * Return a .tar.gz archive containing distribution of JRE for Win OS which will be bundled with the product
   */
  File findWinJreArchive(JvmArchitecture arch) {
    return findJreArchive("windows", arch)
  }

  String archiveNameJre(BuildContext buildContext) {
    return "jre-for-${buildContext.buildNumber}.tar.gz"
  }


  @CompileDynamic
  private String extractJre(String osName, JvmArchitecture arch = JvmArchitecture.x64, JreVendor vendor = JreVendor.JetBrains) {
    String vendorSuffix = vendor == JreVendor.Oracle ? ".oracle" : ""
    String targetDir = arch == JvmArchitecture.x64 ?
                       "$baseDirectoryForJre/jre.$osName$arch.fileSuffix$vendorSuffix" :
                       "$baseDirectoryForJre/jre.${osName}32$vendorSuffix"
    if (new File(targetDir).exists()) {
      buildContext.messages.info("JRE is already extracted to $targetDir")
      return targetDir
    }

    File archive = findJreArchive(osName, arch, vendor)
    if (archive == null) {
      return null
    }
    buildContext.messages.block("Extract $archive.name JRE") {
      String destination = "$targetDir/jre64"
      if (osName == "windows" && arch == JvmArchitecture.x32) {
        destination = "$targetDir/jre32"
      }
      buildContext.messages.progress("Extracting JRE from '$archive.name' archive")
      if (SystemInfo.isWindows) {
        buildContext.ant.untar(src: archive.absolutePath, dest: destination, compression: 'gzip') {
          if (!buildContext.isBundledJreModular()) {
            cutdirsmapper(dirs: 1)
          }
        }
      }
      else {
        //'tar' command is used instead of Ant task to ensure that executable flags will be preserved
        buildContext.ant.mkdir(dir: destination)
        buildContext.ant.exec(executable: "tar", dir: archive.parent) {
          arg(value: "-xf")
          arg(value: archive.name)
          if (!buildContext.isBundledJreModular()) {
            arg(value: "--strip")
            arg(value: "1")
          }
          arg(value: "--directory")
          arg(value: destination)
        }
      }
    }
    return targetDir
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
  static def jreArchiveSuffix(String jreBuild, String version, JvmArchitecture arch, String osName) {
    String update, build
    def split = jreBuild.split('b')
    if (split.length > 2) {
      throw new IllegalArgumentException(
        "$jreBuild is expected in format <update>b<build_number>. Examples: u202b1483.24, 11_0_2b140, b96"
      )
    }
    if (split.length == 2) {
      update = split[0]
      if (update.startsWith(version)) update -= version
      // [11_0_2, b140] or [8u202, b1483.24]
      (update, build) = ["$version$update", "b${split[1]}"]
    }
    else {
      // [11, b96]
      (update, build) = [version, jreBuild]
    }
    "${update}-${osName}-${arch == JvmArchitecture.x32 ? 'i586' : 'x64'}-${build}.tar.gz"
  }

  private File findJreArchive(String osName, JvmArchitecture arch = JvmArchitecture.x64, JreVendor vendor = JreVendor.JetBrains) {
    def jreDir = jreDir()
    def jreBuild = getExpectedJreBuild(osName)

    String suffix = jreArchiveSuffix(jreBuild, buildContext.options.bundledJreVersion.toString(), arch, osName)
    String prefix = buildContext.isBundledJreModular() ? vendor.jreNamePrefix :
                    buildContext.productProperties.toolsJarRequired ? vendor.jreWithToolsJarNamePrefix : vendor.jreNamePrefix
    def jreArchive = new File(jreDir, "$prefix$suffix")

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

  private String getExpectedJreBuild(String osName) {
    loadDependencyVersions()
    return dependencyVersions.get("jreBuild_${osName}" as String, buildContext.options.bundledJreBuild ?: dependencyVersions.get("jdkBuild", ""))
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

  def jreSuffix() {
    buildContext.isBundledJreModular() ? "-jbr${buildContext.options.bundledJreVersion}" : ""
  }

  def is32bitArchSupported() {
    !buildContext.isBundledJreModular()
  }
}