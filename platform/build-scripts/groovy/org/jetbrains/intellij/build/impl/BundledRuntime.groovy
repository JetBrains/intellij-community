// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

import static java.nio.file.attribute.PosixFilePermission.*

@CompileStatic
final class BundledRuntime {
  private final CompilationContext context

  @Lazy private String build = {
    context.options.bundledRuntimeBuild ?: context.dependenciesProperties.property('runtimeBuild')
  }()

  BundledRuntime(CompilationContext context) {
    this.context = context
  }

  @NotNull
  Path getHomeForCurrentOsAndArch() {
    String prefix = "jbr_dcevm-"
    def os = OsFamily.currentOs
    def arch = JvmArchitecture.currentJvmArch
    if (os == OsFamily.LINUX && arch == JvmArchitecture.aarch64) {
      prefix = "jbr-"
    }
    if (System.getProperty("intellij.build.jbr.setupSdk", "false").toBoolean()) {
      // required as a runtime for debugger tests
      prefix = "jbrsdk-"
    }
    else if (context.options.bundledRuntimePrefix != null) {
      prefix = context.options.bundledRuntimePrefix
    }
    def path = extract(prefix, os, arch)

    Path home
    if (os == OsFamily.MACOS) {
      home = path.resolve("jbr/Contents/Home")
    }
    else {
      home = path.resolve("jbr")
    }

    Path releaseFile = home.resolve("release")
    if (!Files.exists(releaseFile)) {
      throw new IllegalStateException("Unable to find release file " + releaseFile + " after extracting JBR at " + path)
    }

    return home
  }

  // contract: returns a directory, where only one subdirectory is available: 'jbr', which contains specified JBR
  @NotNull
  Path extract(String prefix, OsFamily os, JvmArchitecture arch = JvmArchitecture.x64) {
    Path targetDir = Path.of(context.paths.communityHome, "build", "download", "${prefix}${build}-${os.jbrArchiveSuffix}-$arch")
    def jbrDir = targetDir.resolve("jbr")

    Path archive = findArchiveImpl(prefix, os, build, arch, context.options, context.paths)
    BuildDependenciesDownloader.extractFile(
      archive, jbrDir,
      new BuildDependenciesCommunityRoot(context.paths.communityHomeDir),
      BuildDependenciesExtractOptions.STRIP_ROOT,
    )
    fixPermissions(jbrDir, os == OsFamily.WINDOWS)

    Path releaseFile
    if (os == OsFamily.MACOS) {
      releaseFile = jbrDir.resolve("Contents/Home/release")
    }
    else {
      releaseFile = jbrDir.resolve("release")
    }

    if (!Files.exists(releaseFile)) {
      throw new IllegalStateException("Unable to find release file " + releaseFile + " after extracting JBR at " + archive)
    }

    return targetDir
  }

  void extractTo(String prefix, OsFamily os, Path destinationDir, JvmArchitecture arch) {
    Path archive = findArchiveImpl(prefix, os, build, arch, context.options, context.paths)
    if (archive != null) {
      doExtract(archive, destinationDir, os)
    }
  }

  private static void doExtract(Path archive, Path destinationDir, OsFamily os) {
    Span span = TracerManager.spanBuilder("extract JBR")
      .setAttribute("archive", archive.toString())
      .setAttribute("os", os.osName)
      .setAttribute("destination", destinationDir.toString())
      .startSpan()
    try {
      NioFiles.deleteRecursively(destinationDir)
      unTar(archive, destinationDir)
      fixPermissions(destinationDir, os == OsFamily.WINDOWS)
    }
    catch (Throwable e) {
      span.recordException(e)
      span.setStatus(StatusCode.ERROR)
      throw e
    }
    finally {
      span.end()
    }
  }

  Path findArchive(String prefix, OsFamily os, JvmArchitecture arch) {
    return findArchiveImpl(prefix, os, build, arch, context.options, context.paths)
  }

  private static Path findArchiveImpl(String prefix, OsFamily os, String jreBuild, JvmArchitecture arch, BuildOptions options, BuildPaths paths) {
    String archiveName = archiveName(prefix, jreBuild, options.bundledRuntimeVersion, arch, os)
    URI url = new URI("https://cache-redirector.jetbrains.com/intellij-jbr/$archiveName")
    return BuildDependenciesDownloader.downloadFileToCacheLocation(new BuildDependenciesCommunityRoot(paths.communityHomeDir), url)
  }

  private static void unTar(Path archive, Path destination) {
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

      // return BuildHelper.runProcess(context, args, archive.parent) when we switch to jps-bootstrap

      ProcessBuilder builder = new ProcessBuilder(args).inheritIO().directory(archive.parent.toFile())
      Process process = builder.start()
      if (!process.waitFor(10, TimeUnit.MINUTES)) {
        process.destroyForcibly().waitFor()
        throw new IllegalStateException("Cannot execute $args: 10 min timeout")
      }
      int exitCode = process.exitValue()
      if (exitCode != 0) {
        throw new RuntimeException("Cannot execute $args (exitCode=$exitCode)")
      }
    }
  }

  private static TarArchiveInputStream createTarGzInputStream(@NotNull Path archive) {
    return new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive), 64 * 1024))
  }

  private static void fixPermissions(Path destinationDir, boolean forWin) {
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

  static String getProductPrefix(BuildContext buildContext) {
    if (buildContext.options.bundledRuntimePrefix != null) {
      return buildContext.options.bundledRuntimePrefix
    }
    else if (buildContext.productProperties.runtimeDistribution.classifier.isEmpty()) {
      return "jbr-"
    }
    else {
      return "jbr_${buildContext.productProperties.runtimeDistribution.classifier}-"
    }
  }

  /**
   * Update this method together with:
   *  `build/dependencies/setupJdk.gradle`
   *  `com.jetbrains.gateway.downloader.CodeWithMeClientDownloader#downloadClientAndJdk(java.lang.String, java.lang.String, com.intellij.openapi.progress.ProgressIndicator)`
  */
  @SuppressWarnings('SpellCheckingInspection')
  private static String archiveName(String prefix, String jreBuild, int version, JvmArchitecture arch, OsFamily os) {
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

    String archSuffix = getArchSuffix(arch)
    return "${prefix}${update}-${os.jbrArchiveSuffix}-${archSuffix}-${build}.tar.gz"
  }

  private static String getArchSuffix(JvmArchitecture arch) {
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
  static String rootDir(Path archive) {
    return createTarGzInputStream(archive).withCloseable {
      it.nextTarEntry?.name ?: { throw new IllegalStateException("Unable to read $archive") }()
    }
  }
}
