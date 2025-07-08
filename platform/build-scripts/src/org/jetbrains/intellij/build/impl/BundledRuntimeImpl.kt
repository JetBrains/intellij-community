// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JetBrainsRuntimeDistribution
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LinuxLibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFilePermission.*
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

class BundledRuntimeImpl(
  private val options: BuildOptions,
  private val paths: BuildPaths,
  private val dependenciesProperties: DependenciesProperties,
  private val productProperties: ProductProperties?,
  private val info: (String) -> Unit,
) : BundledRuntime {
  constructor(context: CompilationContext) : this(
    context.options, context.paths, context.dependenciesProperties, (context as? BuildContext)?.productProperties, context.messages::info
  )

  override val prefix: String
    get() {
      val bundledRuntimePrefix = options.bundledRuntimePrefix
      return when {
        // no JCEF distribution for musl, see https://github.com/JetBrains/JetBrainsRuntime/releases
        LibcImpl.current(OsFamily.currentOs) == LinuxLibcImpl.MUSL -> JetBrainsRuntimeDistribution.LIGHTWEIGHT.artifactPrefix
        // required as a runtime for debugger tests
        System.getProperty("intellij.build.jbr.setupSdk", "false").toBoolean() -> "jbrsdk-"
        bundledRuntimePrefix != null -> bundledRuntimePrefix
        productProperties != null -> productProperties.runtimeDistribution.artifactPrefix
        else -> JetBrainsRuntimeDistribution.JCEF.artifactPrefix
      }
    }

  override val build: String
    get() = System.getenv("JBR_DEV_SERVER_VERSION") ?: dependenciesProperties.property("runtimeBuild")

  private val homeForCurrentOsAndArchMutex = Mutex()
  private val homeForCurrentOsAndArchValue = AtomicReference<Path>(null)

  override suspend fun getHomeForCurrentOsAndArch(): Path {
    val result = homeForCurrentOsAndArchValue.get()
    if (result != null) return result
    homeForCurrentOsAndArchMutex.withLock {
      val result = homeForCurrentOsAndArchValue.get()
      if (result != null) return result
      val os = OsFamily.currentOs
      val arch = JvmArchitecture.currentJvmArch
      val libc = LibcImpl.current(os)
      val path = extract(os, arch, libc)
      val home = if (os == OsFamily.MACOS) path.resolve("jbr/Contents/Home") else path.resolve("jbr")
      val releaseFile = home.resolve("release")
      check(Files.exists(releaseFile)) {
        "Unable to find release file $releaseFile after extracting JBR at $path"
      }
      homeForCurrentOsAndArchValue.set(home)
      return home
    }
  }

  override suspend fun extract(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, prefix: String): Path {
    val isMusl = os == OsFamily.LINUX && libc == LinuxLibcImpl.MUSL
    val effectivePrefix = if (libc == LinuxLibcImpl.MUSL) JetBrainsRuntimeDistribution.LIGHTWEIGHT.artifactPrefix else prefix
    val targetDir = paths.communityHomeDir.resolve("build/download/${effectivePrefix}${build}-${os.jbrArchiveSuffix}-${if (isMusl) "musl-" else ""}$arch")
    val jbrDir = targetDir.resolve("jbr")

    val archive = findArchive(os, arch, libc, effectivePrefix)
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

  override suspend fun extractTo(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, destinationDir: Path) {
    doExtract(findArchive(os, arch, libc, prefix), destinationDir, os)
  }

  override fun downloadUrlFor(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, prefix: String): String =
    "https://cache-redirector.jetbrains.com/intellij-jbr/${archiveName(os, arch, libc, prefix)}"

  override suspend fun findArchive(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, prefix: String): Path =
    downloadFileToCacheLocation(downloadUrlFor(os, arch, libc, prefix), paths.communityHomeDirRoot)

  /**
   * Update this method together with:
   * - `UploadingAndSigning.getMissingJbrs`
   * - [org.jetbrains.intellij.build.dependencies.JdkDownloader.getUrl]
   */
  override fun archiveName(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, prefix: String, forceVersionWithUnderscores: Boolean): String {
    val split = build.split('b')
    if (split.size != 2) {
      throw IllegalArgumentException("$build doesn't match '<update>b<build_number>' format (e.g.: 17.0.2b387.1)")
    }
    val version = if (forceVersionWithUnderscores) split[0].replace(".", "_") else split[0]
    val buildNumber = "b${split[1]}"
    val archSuffix = getArchSuffix(arch)
    val muslSuffix = if (libc == LinuxLibcImpl.MUSL) "-musl" else ""
    return "${prefix}${version}-${os.jbrArchiveSuffix}${muslSuffix}-${archSuffix}-${runtimeBuildPrefix()}${buildNumber}.tar.gz"
  }

  private fun runtimeBuildPrefix(): String {
    if (!options.runtimeDebug) {
      return ""
    }
    check(options.isTestBuild || options.isInDevelopmentMode) {
      "Either test or development mode is required to use fastdebug runtime build"
    }
    info("Fastdebug runtime build is requested")
    return "fastdebug-"
  }

  /**
   * When changing this list of patterns, also change patch_bin_file in launcher.sh (for remote dev)
   */
  override fun executableFilesPatterns(os: OsFamily, distribution: JetBrainsRuntimeDistribution): Sequence<String> {
    val pathPrefix = if (os == OsFamily.MACOS) "jbr/Contents/Home" else "jbr"
    @Suppress("SpellCheckingInspection")
    return sequence {
      yield("$pathPrefix/bin/*")
      if (os == OsFamily.LINUX) {
        yield("$pathPrefix/lib/jexec")
        yield("$pathPrefix/lib/jspawnhelper")
        if (distribution == JetBrainsRuntimeDistribution.JCEF) {
          yield("$pathPrefix/lib/chrome-sandbox")
          yield("$pathPrefix/lib/jcef_helper")
        }
      }
    }
  }
  private fun getArchSuffix(arch: JvmArchitecture): String = when (arch) {
    JvmArchitecture.x64 -> "x64"
    JvmArchitecture.aarch64 -> "aarch64"
  }

  private suspend fun doExtract(archive: Path, destinationDir: Path, os: OsFamily) {
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
      it.nextEntry?.name
    }
    if (rootDir == null) {
      throw IllegalStateException("Unable to detect root dir of $archive")
    }

    unTar(archive, destination, if (rootDir.startsWith("jbr")) rootDir else null)
  }

  private fun createTarGzInputStream(archive: Path): TarArchiveInputStream =
    TarArchiveInputStream(GZIPInputStream(Files.newInputStream(archive), 64 * 1024))

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
}
