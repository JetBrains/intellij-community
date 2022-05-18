// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.PosixFilePermissionsUtil
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.TraceManager
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.stream.Collectors
import java.util.zip.GZIPInputStream

import static java.nio.file.attribute.PosixFilePermission.*

@CompileStatic
final class BundledRuntimeImpl implements BundledRuntime {
  private final CompilationContext context

  @Lazy private String build = {
    context.options.bundledRuntimeBuild ?: context.dependenciesProperties.property('runtimeBuild')
  }()

  BundledRuntimeImpl(CompilationContext context) {
    this.context = context
  }

  @NotNull
  Path getHomeForCurrentOsAndArch() {
    String prefix = "jbr_jcef-"
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

    Path archive = findArchiveImpl(prefix, os, arch)
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
    Path archive = findArchiveImpl(prefix, os, arch)
    if (archive != null) {
      doExtract(archive, destinationDir, os)
    }
  }

  private static void doExtract(Path archive, Path destinationDir, OsFamily os) {
    Span span = TraceManager.spanBuilder("extract JBR")
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
    return findArchiveImpl(prefix, os, arch)
  }

  private Path findArchiveImpl(String prefix, OsFamily os, JvmArchitecture arch) {
    String archiveName = archiveName(prefix, arch, os)
    URI url = new URI("https://cache-redirector.jetbrains.com/intellij-jbr/$archiveName")
    return BuildDependenciesDownloader.downloadFileToCacheLocation(new BuildDependenciesCommunityRoot(context.paths.communityHomeDir), url)
  }

  private static void unTar(Path archive, Path destination) {
    // CompressorStreamFactory requires input stream with mark support
    String rootDir = createTarGzInputStream(archive).withCloseable {
      it.nextTarEntry?.name
    }
    if (rootDir == null) {
      throw new IllegalStateException("Unable to detect root dir of $archive")
    }

    ArchiveUtils.unTar(archive, destination, rootDir.startsWith("jbr") ? rootDir : null)
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
    else {
      return buildContext.productProperties.runtimeDistribution.artifactPrefix
    }
  }

  /**
   * Update this method together with:
   *  `com.jetbrains.gateway.downloader.CodeWithMeClientDownloader#downloadClientAndJdk(java.lang.String, java.lang.String, com.intellij.openapi.progress.ProgressIndicator)`
   *  `UploadingAndSigning#getMissingJbrs(java.lang.String)`
  */
  @SuppressWarnings('SpellCheckingInspection')
  private String archiveName(String prefix, JvmArchitecture arch, OsFamily os) {
    String[] split = build.split('b')
    if (split.length != 2) {
      throw new IllegalArgumentException("$build doesn't match '<update>b<build_number>' format (e.g.: 17.0.2b387.1)")
    }
    String version, buildNumber
    (version, buildNumber) = [split[0], "b${split[1]}"]

    String archSuffix = getArchSuffix(arch)
    return "${prefix}${version}-${os.jbrArchiveSuffix}-${archSuffix}-${runtimeBuildPrefix()}${buildNumber}.tar.gz"
  }

  private String runtimeBuildPrefix() {
    if (!context.options.runtimeDebug) {
      return ''
    }
    if (!context.options.isTestBuild() && !context.options.isInDevelopmentMode()) {
      context.messages.error("Either test or development mode is required to use fastdebug runtime build")
    }
    context.messages.info("Fastdebug runtime build is requested")
    return 'fastdebug-'
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

  /**
   * When changing this list of patterns, also change patch_bin_file in launcher.sh (for remote dev)
   */
  List<String> executableFilesPatterns(OsFamily os) {
    def pathPrefix = os == OsFamily.MACOS ? "jbr/Contents/Home/" : "jbr/"
    return [
      pathPrefix + "bin/*",
      pathPrefix + "lib/jexec",
      pathPrefix + "lib/jcef_helper",
      pathPrefix + "lib/jspawnhelper",
      pathPrefix + "lib/chrome-sandbox"
    ]
  }

  void checkExecutablePermissions(Path distribution, String root = "", OsFamily os) {
    if (os == OsFamily.WINDOWS) return
    List<PathMatcher> patterns = executableFilesPatterns(os).collect {
      FileSystems.getDefault().getPathMatcher("glob:$it")
    }
    List<String> entries
    if (Files.isDirectory(distribution)) {
      distribution = distribution.resolve(root)
      entries = Files.walk(distribution).withCloseable { files ->
        List<Path> expectedExecutables = files.filter { file ->
          def relativePath = distribution.relativize(file)
          !Files.isDirectory(file) && patterns.any {
            it.matches(relativePath)
          }
        }.collect(Collectors.toList())
        if (expectedExecutables.size() < patterns.size()) {
          context.messages.error("Executable files patterns:\n" +
                                 executableFilesPatterns(os).join("\n") +
                                 "\nFound files:\n" +
                                 expectedExecutables.join("\n"))
        }
        expectedExecutables.stream()
          .filter { !(OWNER_EXECUTE in Files.getPosixFilePermissions(it)) }
          .map { distribution.relativize(it).toString() }
          .collect(Collectors.toList())
      }
    }
    else if ("$distribution".endsWith(".tar.gz")) {
      entries = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(distribution))))
        .withCloseable {
          List<TarArchiveEntry> expectedExecutables = []
          while (true) {
            def entry = (TarArchiveEntry)it.getNextEntry()
            if (entry === null) break
            def entryPath = Path.of(entry.name)
            if (!root.isEmpty()) entryPath = Path.of(root).relativize(entryPath)
            if (!entry.directory && patterns.any {
              it.matches(entryPath)
            }) {
              expectedExecutables += entry
            }
          }
          if (expectedExecutables.size() < patterns.size()) {
            context.messages.error("Executable files patterns:\n" +
                                   executableFilesPatterns(os).join("\n") +
                                   "\nFound files:\n" +
                                   expectedExecutables.join("\n"))
          }
          expectedExecutables
            .findAll {!(OWNER_EXECUTE in PosixFilePermissionsUtil.fromUnixMode(it.mode)) }
            .collect { "${it.name}: mode is ${it.mode}".toString() }
        }
    }
    else {
      entries = new ZipFile(Files.newByteChannel(distribution)).withCloseable {
        def expectedExecutables = it.getEntries().toList().findAll { entry ->
          def entryPath = Path.of(entry.name)
          if (!root.isEmpty()) entryPath = Path.of(root).relativize(entryPath)
          !entry.directory && patterns.any {
            it.matches(entryPath)
          }
        }
        if (expectedExecutables.size() < patterns.size()) {
          context.messages.error("Executable files patterns:\n" +
                                 executableFilesPatterns(os).join("\n") +
                                 "\nFound files:\n" +
                                 expectedExecutables.join("\n"))
        }
        expectedExecutables
          .findAll {!(OWNER_EXECUTE in PosixFilePermissionsUtil.fromUnixMode(it.unixMode)) }
          .collect { "${it.name}: mode is ${it.unixMode}".toString() }
      }
    }
    if (!entries.isEmpty()) {
      context.messages.error("Missing executable permissons in $distribution for:\n" + entries.join("\n"))
    }
  }
}
