// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfo
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.ConcurrentHashMap

import static java.nio.file.attribute.PosixFilePermission.*

@CompileStatic
class BundledJreManager {
  private final BuildContext buildContext
  private final Map<File, String> jbrArchiveInspectionCache = new ConcurrentHashMap<>()

  @Lazy private String jreBuild = {
    buildContext.options.bundledJreBuild ?: buildContext.dependenciesProperties.property('jdkBuild')
  }()

  BundledJreManager(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  private File dependenciesDir() {
    new File(buildContext.paths.communityHome, 'build/dependencies')
  }

  Path extractJre(OsFamily os, JvmArchitecture arch = JvmArchitecture.x64) {
    Path targetDir = Paths.get(buildContext.paths.buildOutputRoot, "jre_${os.jbrArchiveSuffix}_$arch")
    if (Files.isDirectory(targetDir)) {
      buildContext.messages.info("JRE is already extracted to $targetDir")
      return targetDir
    }

    File archive = findJreArchive(os, arch)
    if (archive == null) {
      return null
    }

    Path destinationDir = targetDir.resolve("jbr")
    buildContext.messages.block("Extracting $archive into $destinationDir") {
      destinationDir.deleteDir()
      untar(archive, destinationDir.toString())
      fixJbrPermissions(destinationDir, os == OsFamily.WINDOWS)
    }

    return targetDir
  }

  File findJreArchive(OsFamily os, JvmArchitecture arch = JvmArchitecture.x64) {
    def build =
      buildContext.dependenciesProperties.propertyOrNull("jreBuild_${os.jbrArchiveSuffix}_${getJBRArchSuffix(arch)}") ?:
      buildContext.dependenciesProperties.propertyOrNull("jreBuild_${os.jbrArchiveSuffix}") ?:
      jreBuild
    findArchive(os, build, arch)
  }

  private File findArchive(OsFamily os, String jreBuild, JvmArchitecture arch) {
    String archiveName = jbrArchiveName(jreBuild, buildContext.options.bundledJreVersion, arch, os)
    File jreDir = new File(dependenciesDir(), 'build/jbre')
    File jreArchive = new File(jreDir, archiveName)
    if (jreArchive.file) return jreArchive

    def errorMessage = "Cannot extract $os.osName JRE: file $jreArchive is not found (${jreDir.listFiles()})"
    if (buildContext.options.isInDevelopmentMode) {
      buildContext.messages.warning(errorMessage)
    }
    else {
      buildContext.messages.error(errorMessage)
    }
    return null
  }

  @CompileDynamic
  private void untar(File archive, String destination) {
    boolean stripRootDir = buildContext.bundledJreManager.hasJbrRootDir(archive)
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
        arg(value: "xf")
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

  private static void fixJbrPermissions(Path destinationDir, boolean forWin) {
    Set<PosixFilePermission> exeOrDir = EnumSet.noneOf(PosixFilePermission.class)
    Collections.addAll(exeOrDir, OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE)

    Set<PosixFilePermission> regular = EnumSet.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ)

    Files.walkFileTree(destinationDir, new SimpleFileVisitor<Path>() {
      @Override
      FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (dir != destinationDir && SystemInfo.isUnix) {
          Files.setPosixFilePermissions(dir, exeOrDir)
        }
        return FileVisitResult.CONTINUE
      }

      @Override
      FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (SystemInfo.isUnix) {
          boolean noExec = forWin || !(OWNER_EXECUTE in Files.getPosixFilePermissions(file))
          Files.setPosixFilePermissions(file, noExec ? regular : exeOrDir)
        }
        else {
          ((DosFileAttributeView)Files.getFileAttributeView(file, DosFileAttributeView.class)).setReadOnly(false)
        }
        return FileVisitResult.CONTINUE
      }
    })
  }

  /**
   * Update this method together with:
   *  `build/dependencies/setupJbre.gradle`
   *  `build/dependencies/setupJdk.gradle`
  */
  private String jbrArchiveName(String jreBuild, int version, JvmArchitecture arch, OsFamily os) {
    String update, build
    String[] split = jreBuild.split('b')
    if (split.length > 2) {
      throw new IllegalArgumentException("${jreBuild} doesn't match '<update>b<build_number>' format (e.g.: u202b1483.24, 11_0_2b140, b96)")
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

    String prefix
    if (buildContext.options.bundledJrePrefix != null) {
      prefix = buildContext.options.bundledJrePrefix
    }
    else if (arch == JvmArchitecture.x32 || buildContext.productProperties.jbrDistribution.classifier.isEmpty()) {
      prefix = 'jbr-'
    }
    else {
      prefix = "jbr_${buildContext.productProperties.jbrDistribution.classifier}-"
    }

    def archSuffix = getJBRArchSuffix(arch)
    "${prefix}${update}-${os.jbrArchiveSuffix}-${archSuffix}-${build}.tar.gz"
  }

  private static String getJBRArchSuffix(JvmArchitecture arch) {
    switch (arch) {
      case JvmArchitecture.x32:
        return 'x86'
      case JvmArchitecture.x64:
        return 'x64'
      case JvmArchitecture.aarch64:
        return 'aarch64'
      default:
        throw new IllegalStateException("Unsupported arch: $arch")
    }
  }

  /**
   * If {@code true} then JRE top directory was renamed to JBR, see JBR-1295
   */
  boolean hasJbrRootDir(File archive) {
    String rootDir = jbrRootDir(archive)
    rootDir != null && rootDir.startsWith('jbr')
  }

  /**
   * @return JBR top directory, see JBR-1295
   */
  String jbrRootDir(File archive) {
    jbrArchiveInspectionCache.computeIfAbsent(archive) {
      new TarArchiveInputStream(new CompressorStreamFactory().createCompressorInputStream(archive.newInputStream())).withStream {
        it.nextTarEntry?.name ?: { throw new IllegalStateException("Unable to read $archive") }()
      }
    }
  }

  String x86JreDownloadUrl(OsFamily os) {
    String patchesUrl = buildContext.applicationInfo.patchesUrl
    patchesUrl != null ? "${patchesUrl}${x86JbrArtifactName(os)}" : null
  }

  @CompileDynamic
  void repackageX86Jre(OsFamily osFamily) {
    if (x86JreDownloadUrl(osFamily) == null) {
      buildContext.messages.warning("... skipped: download URL is unknown")
      return
    }

    Path jreDirectoryPath = extractJre(osFamily, JvmArchitecture.x32)
    if (jreDirectoryPath == null) {
      buildContext.messages.warning("... skipped: JRE archive not found")
      return
    }

    String rootDir = "${jreDirectoryPath}/jbr"
    String artifactPath = "${buildContext.paths.artifacts}/${x86JbrArtifactName(osFamily)}"
    if (SystemInfo.isWindows) {
      buildContext.ant.tar(tarfile: artifactPath, longfile: "gnu", compression: "gzip") {
        tarfileset(dir: rootDir) {
          include(name: "**/**")
        }
      }
    }
    else {
      buildContext.ant.exec(executable: "tar", dir: rootDir, failonerror: true) {
        arg(value: "czf")
        arg(value: artifactPath)
        for (f in new File(rootDir).list()) {
          arg(value: f)
        }
      }
    }
    buildContext.notifyArtifactBuilt(artifactPath)
  }

  private String x86JbrArtifactName(OsFamily os) { "jbr-for-${buildContext.buildNumber}-${os.jbrArchiveSuffix}-x86.tar.gz" }
}