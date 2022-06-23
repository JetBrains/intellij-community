// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.PosixFilePermissionsUtil
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions
import java.io.BufferedInputStream
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFilePermission.*
import java.util.*
import java.util.zip.GZIPInputStream
import kotlin.streams.toList

class BundledRuntimeImpl(private val context: CompilationContext) : BundledRuntime {
  companion object {
    @JvmStatic
    fun getProductPrefix(context: BuildContext): String {
      return context.options.bundledRuntimePrefix ?: context.productProperties.runtimeDistribution.artifactPrefix
    }
  }

  private val build by lazy {
    context.options.bundledRuntimeBuild ?: context.dependenciesProperties.property("runtimeBuild")
  }

  override fun getHomeForCurrentOsAndArch(): Path {
    var prefix = "jbr_jcef-"
    val os = OsFamily.currentOs
    val arch = JvmArchitecture.currentJvmArch
    if (System.getProperty("intellij.build.jbr.setupSdk", "false").toBoolean()) {
      // required as a runtime for debugger tests
      prefix = "jbrsdk-"
    }
    else {
      context.options.bundledRuntimePrefix?.let {
        prefix = it
      }
    }
    val path = extract(prefix, os, arch)

    val home = if (os == OsFamily.MACOS) path.resolve("jbr/Contents/Home") else path.resolve("jbr")
    val releaseFile = home.resolve("release")
    if (!Files.exists(releaseFile)) {
      throw IllegalStateException("Unable to find release file $releaseFile after extracting JBR at $path")
    }

    return home
  }

  // contract: returns a directory, where only one subdirectory is available: 'jbr', which contains specified JBR
  override fun extract(prefix: String, os: OsFamily, arch: JvmArchitecture): Path {
    val targetDir = context.paths.communityHomeDir.communityRoot.resolve("build/download/${prefix}${build}-${os.jbrArchiveSuffix}-$arch")
    val jbrDir = targetDir.resolve("jbr")

    val archive = findArchiveImpl(prefix, os, arch)
    BuildDependenciesDownloader.extractFile(
      archive, jbrDir,
      context.paths.communityHomeDir,
      BuildDependenciesExtractOptions.STRIP_ROOT,
    )
    fixPermissions(jbrDir, os == OsFamily.WINDOWS)

    val releaseFile = if (os == OsFamily.MACOS) jbrDir.resolve("Contents/Home/release") else jbrDir.resolve("release")

    if (!Files.exists(releaseFile)) {
      throw IllegalStateException("Unable to find release file $releaseFile after extracting JBR at $archive")
    }

    return targetDir
  }

  override fun extractTo(prefix: String, os: OsFamily, destinationDir: Path, arch: JvmArchitecture) {
    doExtract(findArchiveImpl(prefix, os, arch), destinationDir, os)
  }

  override fun findArchive(prefix: String, os: OsFamily, arch: JvmArchitecture): Path {
    return findArchiveImpl(prefix, os, arch)
  }

  private fun findArchiveImpl(prefix: String, os: OsFamily, arch: JvmArchitecture): Path {
    val archiveName = archiveName(prefix, arch, os)
    val url = URI("https://cache-redirector.jetbrains.com/intellij-jbr/$archiveName")
    return BuildDependenciesDownloader.downloadFileToCacheLocation(context.paths.communityHomeDir, url)
  }

  /**
   * Update this method together with:
   *  `com.jetbrains.gateway.downloader.CodeWithMeClientDownloader#downloadClientAndJdk(java.lang.String, java.lang.String, com.intellij.openapi.progress.ProgressIndicator)`
   *  `UploadingAndSigning#getMissingJbrs(java.lang.String)`
   */
  override fun archiveName(prefix: String, arch: JvmArchitecture, os: OsFamily): String {
    val split = build.split('b')
    if (split.size != 2) {
      throw IllegalArgumentException("$build doesn't match '<update>b<build_number>' format (e.g.: 17.0.2b387.1)")
    }
    val version = split[0]
    val buildNumber = "b${split[1]}"
    val archSuffix = getArchSuffix(arch)
    return "${prefix}${version}-${os.jbrArchiveSuffix}-${archSuffix}-${runtimeBuildPrefix()}${buildNumber}.tar.gz"
  }

  private fun runtimeBuildPrefix(): String {
    if (!context.options.runtimeDebug) {
      return ""
    }
    if (!context.options.isTestBuild && !context.options.isInDevelopmentMode) {
      context.messages.error("Either test or development mode is required to use fastdebug runtime build")
    }
    context.messages.info("Fastdebug runtime build is requested")
    return "fastdebug-"
  }

  override fun checkExecutablePermissions(distribution: Path, root: String, os: OsFamily) {
    if (os == OsFamily.WINDOWS) {
      return
    }

    val patterns = executableFilesPatterns(os).map {
      FileSystems.getDefault().getPathMatcher("glob:$it")
    }
    val entries: List<String>
    if (Files.isDirectory(distribution)) {
      @Suppress("NAME_SHADOWING") val distribution = distribution.resolve(root)
      entries = Files.walk(distribution).use { files ->
        val expectedExecutables = files.filter { file ->
          val relativePath = distribution.relativize(file)
          !Files.isDirectory(file) && patterns.any {
            it.matches(relativePath)
          }
        }.toList()
        if (expectedExecutables.size < patterns.size) {
          context.messages.error("Executable files patterns:\n" +
                                 executableFilesPatterns(os).joinToString(separator = "\n") +
                                 "\nFound files:\n" +
                                 expectedExecutables.joinToString(separator = "\n"))
        }
        expectedExecutables.stream()
          .filter { OWNER_EXECUTE !in Files.getPosixFilePermissions(it) }
          .map { distribution.relativize(it).toString() }
          .toList()
      }
    }
    else if ("$distribution".endsWith(".tar.gz")) {
      entries = TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(Files.newInputStream(distribution)))).use { stream ->
        val expectedExecutables = mutableListOf<TarArchiveEntry>()
          while (true) {
            val entry = (stream.nextEntry ?: break) as TarArchiveEntry
            var entryPath = Path.of(entry.name)
            if (!root.isEmpty()) {
              entryPath = Path.of(root).relativize(entryPath)
            }
            if (!entry.isDirectory && patterns.any { it.matches(entryPath) }) {
              expectedExecutables.add(entry)
            }
          }
          if (expectedExecutables.size < patterns.size) {
            context.messages.error("Executable files patterns:\n" +
                                   executableFilesPatterns(os).joinToString(separator = "\n") +
                                   "\nFound files:\n" +
                                   expectedExecutables.joinToString(separator = "\n"))
          }
          expectedExecutables
            .filter { OWNER_EXECUTE !in PosixFilePermissionsUtil.fromUnixMode(it.mode) }
            .map { "${it.name}: mode is 0${Integer.toOctalString(it.mode)}" }
        }
    }
    else {
      entries = ZipFile(Files.newByteChannel(distribution)).use { zipFile ->
        val expectedExecutables = zipFile.entries.asSequence().filter { entry ->
          var entryPath = Path.of(entry.name)
          if (!root.isEmpty()) {
            entryPath = Path.of(root).relativize(entryPath)
          }
          !entry.isDirectory && patterns.any { it.matches(entryPath) }
        }.toList()
        if (expectedExecutables.size < patterns.size) {
          context.messages.error("Executable files patterns:\n" +
                                 executableFilesPatterns(os).joinToString(separator = "\n") +
                                 "\nFound files:\n" +
                                 expectedExecutables.joinToString(separator = "\n"))
        }
        expectedExecutables
          .filter { entry -> OWNER_EXECUTE !in PosixFilePermissionsUtil.fromUnixMode(entry.unixMode) }
          .map { "${it.name}: mode is 0${Integer.toOctalString(it.unixMode)}" }
      }
    }
    if (entries.isNotEmpty()) {
      context.messages.error("Missing executable permissions in $distribution for:\n" + entries.joinToString(separator = "\n"))
    }
  }

  /**
   * When changing this list of patterns, also change patch_bin_file in launcher.sh (for remote dev)
   */
  override fun executableFilesPatterns(os: OsFamily): List<String> {
    val pathPrefix = if (os == OsFamily.MACOS) "jbr/Contents/Home/" else "jbr/"
    @Suppress("SpellCheckingInspection")
    return listOf(
      pathPrefix + "bin/*",
      pathPrefix + "lib/jexec",
      pathPrefix + "lib/jcef_helper",
      pathPrefix + "lib/jspawnhelper",
      pathPrefix + "lib/chrome-sandbox"
    )
  }
}

private fun getArchSuffix(arch: JvmArchitecture): String {
  return when (arch) {
    JvmArchitecture.x64 -> "x64"
    JvmArchitecture.aarch64 -> "aarch64"
  }
}

/**
 * @return JBR top directory, see JBR-1295
 */
fun getJbrTopDir(archive: Path): String {
  return createTarGzInputStream(archive).use {
    it.nextTarEntry?.name ?: throw IllegalStateException("Unable to read $archive")
  }
}

private fun doExtract(archive: Path, destinationDir: Path, os: OsFamily) {
  TraceManager.spanBuilder("extract JBR")
    .setAttribute("archive", archive.toString())
    .setAttribute("os", os.osName)
    .setAttribute("destination", destinationDir.toString())
    .use {
      NioFiles.deleteRecursively(destinationDir)
      unTar(archive, destinationDir)
      fixPermissions(destinationDir, os == OsFamily.WINDOWS)
    }
}

private fun unTar(archive: Path, destination: Path) {
  // CompressorStreamFactory requires stream with mark support
  val rootDir = createTarGzInputStream(archive).use {
    it.nextTarEntry?.name
  }
  if (rootDir == null) {
    throw IllegalStateException("Unable to detect root dir of $archive")
  }

  ArchiveUtils.unTar(archive, destination, if (rootDir.startsWith("jbr")) rootDir else null)
}

private fun createTarGzInputStream(archive: Path): TarArchiveInputStream {
  return TarArchiveInputStream(GZIPInputStream(Files.newInputStream(archive), 64 * 1024))
}

private fun fixPermissions(destinationDir: Path, forWin: Boolean) {
  val exeOrDir = EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE)
  val regular = EnumSet.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ)

  Files.walkFileTree(destinationDir, object : SimpleFileVisitor<Path>() {
    @Override
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (dir != destinationDir && SystemInfoRt.isUnix) {
        Files.setPosixFilePermissions(dir, exeOrDir)
      }
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (SystemInfoRt.isUnix) {
        val noExec = forWin || OWNER_EXECUTE !in Files.getPosixFilePermissions(file)
        Files.setPosixFilePermissions(file, if (noExec) regular else exeOrDir)
      }
      else {
        Files.getFileAttributeView(file, DosFileAttributeView::class.java).setReadOnly(false)
      }
      return FileVisitResult.CONTINUE
    }
  })
}