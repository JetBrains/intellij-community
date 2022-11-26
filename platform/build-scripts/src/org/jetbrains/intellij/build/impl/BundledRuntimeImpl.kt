// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import kotlinx.collections.immutable.persistentListOf
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFilePermission.*
import java.util.*
import java.util.zip.GZIPInputStream

class BundledRuntimeImpl(
  private val options: BuildOptions,
  private val paths: BuildPaths,
  private val dependenciesProperties: DependenciesProperties,
  private val error: (String) -> Unit,
  private val info: (String) -> Unit) : BundledRuntime {
  companion object {
    fun getProductPrefix(context: BuildContext): String {
      return context.options.bundledRuntimePrefix ?: context.productProperties.runtimeDistribution.artifactPrefix
    }
  }

  private val build by lazy {
    options.bundledRuntimeBuild ?: dependenciesProperties.property("runtimeBuild")
  }

  override suspend fun getHomeForCurrentOsAndArch(): Path {
    var prefix = "jbr_jcef-"
    val os = OsFamily.currentOs
    val arch = JvmArchitecture.currentJvmArch
    if (System.getProperty("intellij.build.jbr.setupSdk", "false").toBoolean()) {
      // required as a runtime for debugger tests
      prefix = "jbrsdk-"
    }
    else {
      options.bundledRuntimePrefix?.let {
        prefix = it
      }
    }
    val path = extract(prefix, os, arch)

    val home = if (os == OsFamily.MACOS) path.resolve("jbr/Contents/Home") else path.resolve("jbr")
    val releaseFile = home.resolve("release")
    check(Files.exists(releaseFile)) {
      "Unable to find release file $releaseFile after extracting JBR at $path"
    }

    return home
  }

  // contract: returns a directory, where only one subdirectory is available: 'jbr', which contains specified JBR
  override suspend fun extract(prefix: String, os: OsFamily, arch: JvmArchitecture): Path {
    val targetDir = paths.communityHomeDir.resolve("build/download/${prefix}${build}-${os.jbrArchiveSuffix}-$arch")
    val jbrDir = targetDir.resolve("jbr")

    val archive = findArchive(prefix, os, arch)
    BuildDependenciesDownloader.extractFile(
      archive, jbrDir,
      paths.communityHomeDirRoot,
      BuildDependenciesExtractOptions.STRIP_ROOT,
    )
    fixPermissions(jbrDir, os == OsFamily.WINDOWS)

    val releaseFile = if (os == OsFamily.MACOS) jbrDir.resolve("Contents/Home/release") else jbrDir.resolve("release")

    check(Files.exists(releaseFile)) {
      "Unable to find release file $releaseFile after extracting JBR at $archive"
    }

    return targetDir
  }

  override suspend fun extractTo(prefix: String, os: OsFamily, destinationDir: Path, arch: JvmArchitecture) {
    doExtract(findArchive(prefix, os, arch), destinationDir, os)
  }

  private suspend fun findArchive(prefix: String, os: OsFamily, arch: JvmArchitecture): Path {
    val archiveName = archiveName(prefix = prefix, arch = arch, os = os)
    val url = "https://cache-redirector.jetbrains.com/intellij-jbr/$archiveName"
    return downloadFileToCacheLocation(url = url, communityRoot = paths.communityHomeDirRoot)
  }

  /**
   * Update this method together with:
   *  [com.intellij.remoteDev.downloader.CodeWithMeClientDownloader.downloadClientAndJdk]
   *  [UploadingAndSigning.getMissingJbrs]
   *  [org.jetbrains.intellij.build.dependencies.JdkDownloader.getUrl]
   */
  override fun archiveName(prefix: String, arch: JvmArchitecture, os: OsFamily, forceVersionWithUnderscores: Boolean): String {
    val split = build.split('b')
    if (split.size != 2) {
      throw IllegalArgumentException("$build doesn't match '<update>b<build_number>' format (e.g.: 17.0.2b387.1)")
    }
    val version = if (forceVersionWithUnderscores) split[0].replace(".", "_") else split[0]
    val buildNumber = "b${split[1]}"
    val archSuffix = getArchSuffix(arch)
    return "${prefix}${version}-${os.jbrArchiveSuffix}-${archSuffix}-${runtimeBuildPrefix()}${buildNumber}.tar.gz"
  }

  private fun runtimeBuildPrefix(): String {
    if (!options.runtimeDebug) {
      return ""
    }
    if (!options.isTestBuild && !options.isInDevelopmentMode) {
      error("Either test or development mode is required to use fastdebug runtime build")
    }
    info("Fastdebug runtime build is requested")
    return "fastdebug-"
  }

  /**
   * When changing this list of patterns, also change patch_bin_file in launcher.sh (for remote dev)
   */
  override fun executableFilesPatterns(os: OsFamily): List<String> {
    val pathPrefix = if (os == OsFamily.MACOS) "jbr/Contents/Home/" else "jbr/"
    @Suppress("SpellCheckingInspection")
    var executableFilesPatterns = persistentListOf(
      pathPrefix + "bin/*",
      pathPrefix + "lib/jexec",
      pathPrefix + "lib/jspawnhelper",
      pathPrefix + "lib/chrome-sandbox"
    )
    if (os == OsFamily.LINUX) {
      executableFilesPatterns = executableFilesPatterns.add("jbr/lib/jcef_helper")
    }
    return executableFilesPatterns
  }
}

private fun getArchSuffix(arch: JvmArchitecture): String {
  return when (arch) {
    JvmArchitecture.x64 -> "x64"
    JvmArchitecture.aarch64 -> "aarch64"
  }
}

private fun doExtract(archive: Path, destinationDir: Path, os: OsFamily) {
  spanBuilder("extract JBR")
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