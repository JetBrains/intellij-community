// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily

import java.util.concurrent.ConcurrentHashMap

@CompileStatic
class BundledJreManager {
  private final BuildContext buildContext
  String baseDirectoryForJre

  BundledJreManager(BuildContext buildContext, String baseDirectoryForJre) {
    this.buildContext = buildContext
    this.baseDirectoryForJre = baseDirectoryForJre
  }

  private String getJreBuild(OsFamily os) {
    loadDependencyVersions()
    return dependencyVersions.get("jreBuild_${os.jbrArchiveSuffix}" as String, buildContext.options.bundledJreBuild ?: dependencyVersions.get("jdkBuild", ""))
  }

  private int getJreVersion() {
    return buildContext.options.bundledJreVersion
  }

  /** @deprecated use {@link #extractJre(org.jetbrains.intellij.build.OsFamily)} instead to avoid hard-coding OS name */
  @Deprecated
  String extractJre(String osName) {
    return extractJre(OsFamily.ALL.find { it.jbrArchiveSuffix == osName })
  }

  String extractJre(OsFamily os, JvmArchitecture arch = JvmArchitecture.x64) {
    String targetDir = "$baseDirectoryForJre/secondJre.${os.jbrArchiveSuffix}_$arch"
    if (new File(targetDir).exists()) {
      buildContext.messages.info("JRE is already extracted to $targetDir")
      return targetDir
    }

    File archive = findArchive(os, getJreBuild(os), arch)
    if (archive == null) return null

    String destination = "${targetDir}/${arch == JvmArchitecture.x32 ? "jre32" : "jbr"}"
    buildContext.messages.block("Extracting ${archive} into ${destination}") {
      def destinationDir = new File(destination)
      if (destinationDir.exists()) destinationDir.deleteDir()
      untar(archive, destination, isBundledJreModular())
    }

    return targetDir
  }

  /** @deprecated use {@link #findJreArchive(org.jetbrains.intellij.build.OsFamily, org.jetbrains.intellij.build.JvmArchitecture)} instead */
  @Deprecated
  File findJreArchive(String osName, JvmArchitecture arch = JvmArchitecture.x64) {
    return findJreArchive(OsFamily.ALL.find { it.jbrArchiveSuffix == osName }, arch)
  }

  File findJreArchive(OsFamily os, JvmArchitecture arch = JvmArchitecture.x64) {
    return findArchive(os, getJreBuild(os), arch)
  }

  String x86JreDownloadUrl(OsFamily os) {
    def patchesUrl = buildContext.applicationInfo.patchesUrl
    return patchesUrl != null ? "${patchesUrl}${x86JreArchiveName(os)}" : null
  }

  private String x86JreArchiveName(OsFamily os) { "jbr-for-${buildContext.buildNumber}-${os.jbrArchiveSuffix}-x86.tar.gz" }

  /**
   * @param archive linux or windows JRE archive
   */
  @CompileDynamic
  private void untar(File archive, String destination, boolean isModular) {
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
      buildContext.ant.exec(executable: "tar", dir: archive.parent, failonerror: true) {
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
  private String jreArchiveSuffix(String jreBuild, int version, JvmArchitecture arch, OsFamily os) {
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
    "${update}-${os.jbrArchiveSuffix}-${arch == JvmArchitecture.x32 ? 'x86' : 'x64'}-${build}.tar.gz"
  }

  /**
   * Update this method together with:
   *  `build/dependencies/setupJbre.gradle`
   */
  private def prefix(JvmArchitecture arch) {
    if (forcedPrefix != null) {
      forcedPrefix
    }
    else if (jreVersion < 9 && buildContext.productProperties.toolsJarRequired) {
      'jbrx-'
    }
    else if (arch == JvmArchitecture.x32 || buildContext.productProperties.jbrDistribution.classifier.isEmpty()) {
      'jbr-'
    }
    else {
      "jbr_${buildContext.productProperties.jbrDistribution.classifier}-"
    }
  }

  private File findArchive(OsFamily os, String jreBuild, JvmArchitecture arch) {
    def jreDir = jreDir()
    String suffix = jreArchiveSuffix(jreBuild, jreVersion, arch, os)
    String prefix = prefix(arch)
    def jreArchive = new File(jreDir, "$prefix$suffix")
    if (!jreArchive.file) {
      def errorMessage = "Cannot extract $os.osName JRE: file $jreArchive is not found (${jreDir.listFiles()})"
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

  private static String getForcedPrefix() {
    return System.getProperty("intellij.build.bundled.jre.prefix")
  }

  /**
   *  If {@code true} then bundled JRE version is 9+
   */
  boolean isBundledJreModular() {
    return buildContext.options.bundledJreVersion >= 9
  }

  private final Map<File, String> jbrArchiveInspectionCache = new ConcurrentHashMap<>()

  /**
   * If {@code true} then JRE top directory was renamed to JBR, see JBR-1295
   */
  boolean hasJbrRootDir(File archive) {
    jbrArchiveInspectionCache.computeIfAbsent(archive) {
      def tarArchive = new TarArchiveInputStream(
        new CompressorStreamFactory().createCompressorInputStream(
          new BufferedInputStream(new FileInputStream(archive))
        ))
      tarArchive.nextTarEntry?.name ?: {
        throw new IllegalStateException("Unable to read $archive")
      }()
    }.startsWith('jbr')
  }

  /**
   * @return JBR top directory, see JBR-1295
   */
  String jbrRootDir(File archive) {
    hasJbrRootDir(archive) ? jbrArchiveInspectionCache[archive] : null
  }

  @CompileDynamic
  void repackageX86Jre(OsFamily osFamily) {
    buildContext.messages.info("Packaging x86 JRE for ${osFamily}")

    if (x86JreDownloadUrl(osFamily) == null) {
      buildContext.messages.warning("... skipped: download URL is unknown")
      return
    }

    def jreDirectoryPath = extractJre(osFamily, JvmArchitecture.x32)
    if (jreDirectoryPath == null) {
      buildContext.messages.warning("... skipped: JRE archive not found")
      return
    }

    def artifactPath = "${buildContext.paths.artifacts}/${x86JreArchiveName(osFamily)}"
    if (SystemInfo.isWindows) {
      buildContext.ant.tar(tarfile: artifactPath, longfile: "gnu", compression: "gzip") {
        tarfileset(dir: "${jreDirectoryPath}/jre32") {
          include(name: "**/**")
        }
      }
    }
    else {
      buildContext.ant.exec(executable: "tar", dir: "${jreDirectoryPath}/jre32", failonerror: true) {
        arg(value: "czf")
        arg(value: artifactPath)
        for (f in new File("${jreDirectoryPath}/jre32").list()) {
          arg(value: f)
        }
      }
    }
    buildContext.notifyArtifactBuilt(artifactPath)
  }
}