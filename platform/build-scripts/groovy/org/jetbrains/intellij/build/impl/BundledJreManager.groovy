// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPInputStream

import static java.nio.file.attribute.PosixFilePermission.*

@CompileStatic
final class BundledJreManager {
  private final BuildContext buildContext

  @Lazy private String jreBuild = {
    buildContext.options.bundledJreBuild ?: buildContext.dependenciesProperties.property('jdkBuild')
  }()

  BundledJreManager(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  Path extractJre(OsFamily os, JvmArchitecture arch = JvmArchitecture.x64) {
    Path targetDir = Path.of(buildContext.paths.buildOutputRoot, "jre_${os.jbrArchiveSuffix}_$arch")
    if (Files.isDirectory(targetDir)) {
      Span.current().addEvent("JRE is already extracted", Attributes.of(AttributeKey.stringKey("dir"), targetDir.toString()))
      return targetDir
    }

    Path archive = findJreArchive(os, arch)
    if (archive == null) {
      return null
    }

    doExtractJbr(archive, targetDir.resolve("jbr"), os, buildContext)
    return targetDir
  }

  void extractJreTo(OsFamily os, Path destinationDir, JvmArchitecture arch) {
    Path archive = findJreArchive(os, arch)
    if (archive != null) {
      doExtractJbr(archive, destinationDir, os, buildContext)
    }
  }

  private static void doExtractJbr(Path archive, Path destinationDir, OsFamily os, BuildContext context) {
    BuildHelper.getInstance(context).span(TracerManager.spanBuilder("extract JBR")
                                            .setAttribute("archive", archive.toString())
                                            .setAttribute("os", os.osName)
                                            .setAttribute("destination", destinationDir.toString())) {
      NioFiles.deleteRecursively(destinationDir)
      unTar(archive, destinationDir, context)
      fixJbrPermissions(destinationDir, os == OsFamily.WINDOWS)
    }
  }

  Path findJreArchive(OsFamily os, JvmArchitecture arch) {
    String jreBuild = buildContext.dependenciesProperties.propertyOrNull("jreBuild_${os.jbrArchiveSuffix}_${getJBRArchSuffix(arch)}")
      ?: buildContext.dependenciesProperties.propertyOrNull("jreBuild_${os.jbrArchiveSuffix}")
                        ?: jreBuild

    //noinspection SpellCheckingInspection
    Path jreDir = buildContext.paths.communityHomeDir.resolve("build/dependencies/build/jbre")
    Path jreArchive = jreDir.resolve(jbrArchiveName(jreBuild, buildContext.options.bundledJreVersion, arch, os, buildContext))
    if (Files.exists(jreArchive)) {
      return jreArchive
    }

    List<String> existingFiles = jreDir.toFile().listFiles().collect { jreDir.relativize(it.toPath()).toString() }
    if (buildContext.options.isInDevelopmentMode) {
      buildContext.messages.warning("Cannot extract $os.osName JRE: file $jreArchive is not found (${ existingFiles})")
    }
    else {
      RuntimeException error = new RuntimeException("cannot extract JRE")
      Span.current().recordException(error, Attributes.of(
        AttributeKey.stringKey("os"), os.osName,
        AttributeKey.stringKey("arch"), arch.name(),
        AttributeKey.stringKey("archiveName"), jreArchive.fileName.toString(),
        AttributeKey.stringArrayKey("existingFiles"), existingFiles,
        ))
      throw error
    }
    return null
  }

  private static void unTar(Path archive, Path destination, BuildContext context) {
    // CompressorStreamFactory requires input stream with mark support
    String rootDir = createTarGzInputStream(archive).withCloseable {
      it.nextTarEntry?.name
    }
    if (rootDir == null) {
      throw new IllegalStateException("Unable to detect root dir of $archive")
    }

    boolean stripRootDir = rootDir.startsWith("jbr")
    if (SystemInfoRt.isWindows) {
      Decompressor.Tar decompressor = new Decompressor.Tar(archive)
      if (stripRootDir) {
        decompressor.removePrefixPath(rootDir)
      }
      decompressor.extract(destination)
    }
    else {
      // 'tar' command is used to ensure that executable flags will be preserved
      Files.createDirectories(destination)
      List<String> args = new ArrayList<>(10)
      args.add("tar")
      args.add("xf")
      args.add(archive.fileName.toString())
      if (stripRootDir) {
        args.add("--strip")
        args.add("1")
      }
      args.add("--directory")
      args.add(destination.toString())

      BuildHelper.runProcess(context, args, archive.parent)
    }
  }

  private static TarArchiveInputStream createTarGzInputStream(@NotNull Path archive) {
    return new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive), 64 * 1024))
  }

  private static void fixJbrPermissions(Path destinationDir, boolean forWin) {
    Set<PosixFilePermission> exeOrDir = EnumSet.noneOf(PosixFilePermission.class)
    Collections.addAll(exeOrDir, OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE)

    Set<PosixFilePermission> regular = EnumSet.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ)

    Files.walkFileTree(destinationDir, new SimpleFileVisitor<Path>() {
      @Override
      FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (dir != destinationDir && SystemInfoRt.isUnix) {
          Files.setPosixFilePermissions(dir, exeOrDir)
        }
        return FileVisitResult.CONTINUE
      }

      @Override
      FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (SystemInfoRt.isUnix) {
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
   *  `com.jetbrains.gateway.downloader.CodeWithMeClientDownloader#downloadClientAndJdk(java.lang.String, java.lang.String, com.intellij.openapi.progress.ProgressIndicator)`
  */
  @SuppressWarnings('SpellCheckingInspection')
  private static String jbrArchiveName(String jreBuild, int version, JvmArchitecture arch, OsFamily os, BuildContext buildContext) {
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
    if (buildContext.productProperties.jbrDistribution.classifier.isEmpty()) {
      prefix = "jbr-"
    }
    else if (buildContext.options.bundledJrePrefix != null) {
      prefix = buildContext.options.bundledJrePrefix
    }
    else {
      prefix = "jbr_${buildContext.productProperties.jbrDistribution.classifier}-"
    }

    String archSuffix = getJBRArchSuffix(arch)
    return "${prefix}${update}-${os.jbrArchiveSuffix}-${archSuffix}-${build}.tar.gz"
  }

  private static String getJBRArchSuffix(JvmArchitecture arch) {
    switch (arch) {
      case JvmArchitecture.x64:
        return "x64"
      case JvmArchitecture.aarch64:
        return "aarch64"
      default:
        throw new IllegalStateException("Unsupported arch: $arch")
    }
  }

  /**
   * @return JBR top directory, see JBR-1295
   */
  static String jbrRootDir(Path archive) {
    return createTarGzInputStream(archive).withCloseable {
      it.nextTarEntry?.name ?: { throw new IllegalStateException("Unable to read $archive") }()
    }
  }
}
