package fleet.buildtool.codecache

import com.intellij.util.lang.ImmutableZipFile
import fleet.buildtool.codecache.shadowing.ShadowedJarSpec
import fleet.buildtool.fs.extractZip
import fleet.buildtool.platform.Arch
import fleet.buildtool.platform.Platform
import fleet.buildtool.platform.toS3DistributionSlug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.writeZipUsingTempFile
import org.jetbrains.intellij.build.io.zip
import org.slf4j.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name

private val jnaJarNamePattern = Regex("jna-\\d.*\\.jar")
val kotlinStdlibJarNamePattern = Regex("kotlin-stdlib-\\d.*\\.jar")

private typealias Jar = Path

data class PackedJar(
  val path: Path,
  val needsScrambling: Boolean,
  val nativeFilesByPlatform: Map<Platform, List<Path>>,
)

data class PackedModule(
  val moduleName: String,
  val jarFiles: List<PackedJar>,
)

/**
 * Represents a module that can be packed as one or more jars.
 */
interface ModuleToPack {
  val name: String
  val filesToPack: List<Path>
}

sealed class NativeLibraryExtractor {
  /**
   * Jars would be left untouched.
   */
  object Noop : NativeLibraryExtractor()

  /**
   * Native libraries inside jars will be extracted to [directory]
   */
  data class ExtractTo(val directory: Path) : NativeLibraryExtractor()
}

class ModulePacker(
  private val directory: Path,
  private val nativeLibraryExtractor: NativeLibraryExtractor,
  private val version: String,
  private val logger: Logger,
  private val shadowedJarSpecs: List<ShadowedJarSpec>,
) {
  private val cache = ConcurrentHashMap<Path, CacheEntry>()

  private data class CacheEntry(
    val jar: PackedJar,
    val shadowed: Path?,
  )

  fun packModule(module: ModuleToPack): PackedModule {
    val resolvedShadowedJars = shadowedJarSpecs.mapNotNull { it.resolve(module) }
    require(resolvedShadowedJars.size <= 1) {
      "Module '${module.name}' tries to shadow '${resolvedShadowedJars.map { it.shadowedJar.name }}', this is not allowed by our build tool, contact #fleet-platform"
    }
    val shadowing = resolvedShadowedJars.singleOrNull()
    val toPack = module.filesToPack.filter { it != shadowing?.shadowedJar } // never pack the shadowed jar
    val jars = runBlocking(Dispatchers.IO) {
      toPack.mapConcurrent { path ->
        when (path == shadowing?.consumerJar) {
          true -> pack(path, needsScrambling = shadowing.needsScrambling, shadowed = shadowing.shadowedJar)
          false -> pack(path, needsScrambling = false, shadowed = null)
        }
      }
    }
    return PackedModule(
      moduleName = module.name,
      jarFiles = jars,
    )
  }

  /**
   * Packs the given [file] into an [ImmutableZipFile] jar.
   * Also, shadows the given [shadowed], if any, inside the resulting packed jar.
   * Also, extracts JNA native libraries files from the resulting jar and expose them through [PackedJar.nativeFiles].
   *
   * @param file either a directory of classes representing an output of compilation of a module, or a `jar` to be repacked as immutable
   * @param needsScrambling whether the resulting packed jar will require scrambling in further step of the build tool
   * @param shadowed if specified, a jar to shadow (extract inside) the resulting packed jar
   */
  @OptIn(ExperimentalPathApi::class)
  private fun pack(file: Path, needsScrambling: Boolean, shadowed: Path?): PackedJar {
    logger.info("Packing/repacking '$file' (needsScrambling=$needsScrambling, shadowing=$shadowed)")
    require(shadowedJarSpecs.none { file.name.matches(it.shadowedJarPattern) }) {
      "${file.name} should have been shadowed, contact #fleet-support"
    }
    return cache.compute(file) { fileToPack, existing ->
      when (existing) {
        null -> {
          directory.createDirectories()
          logger.debug("'{}' (needsScrambling={}, shadowing={}) not found in cache, packing it...", file, needsScrambling, shadowed)
          val jar = when {
            Files.isDirectory(fileToPack) -> packToImmutableJar(
              path = fileToPack,
              needsScrambling = needsScrambling,
              shadowed = shadowed,
              target = directory.resolve("${fileToPack.fileName}-$version.jar"),
              logger = logger,
            )

            nativeLibraryExtractor is NativeLibraryExtractor.ExtractTo && jnaJarNamePattern.matches(fileToPack.name) -> repackToImmutableJarExtractingNativeFiles(
              fileToPack,
              needsScrambling,
              shadowed,
              targetDirectory = directory,
              nativeLibrariesTargetDirectory = nativeLibraryExtractor.directory,
              logger = logger,
            )

            nativeLibraryExtractor is NativeLibraryExtractor.ExtractTo && fileToPack.name.contains("kotlin-desktop-toolkit") -> repackToImmutableJarExtractingNativeFiles2(
              fileToPack,
              needsScrambling,
              shadowed,
              targetDirectory = directory,
              nativeLibrariesTargetDirectory = nativeLibraryExtractor.directory,
              logger = logger,
            )

            fileToPack.name.endsWith(".jar") -> packToImmutableJar(
              path = fileToPack,
              needsScrambling = needsScrambling,
              shadowed = shadowed,
              target = directory.resolve(fileToPack.name),
              logger = logger,
            )

            else -> error("Cannot pack file which is neither directory or jar: '$file'")
          }

          CacheEntry(
            jar = jar,
            shadowed = shadowed,
          )
        }

        else -> {
          require(needsScrambling == existing.jar.needsScrambling) {
            """
            |'$file' has already been packed/repacked but with different instructions about scrambling, this is very likely a bug in the build tool, contact #fleet-support
            |Existing: ${existing.jar.needsScrambling}
            |Current: ${needsScrambling}
          """.trimMargin()
          }
          require(shadowed == existing.shadowed) {
            """
            |'$file' has already been packed/repacked but with different instructions about shadowing, this is very likely a bug in the build tool, contact #fleet-support
            |Existing: ${existing.shadowed}
            |Current: ${shadowed}
          """.trimMargin()
          }
          existing
        }
      }
    }?.jar ?: error("must have either found the jar in cache, or pack/repack it")
  }
}

fun packToImmutableJar(path: Path, needsScrambling: Boolean, shadowed: Path?, target: Path, logger: Logger): PackedJar {
  target.deleteIfExists()
  target.parent.createDirectories()
  val intermediateJar = when {
    path.isDirectory() -> packDirectoriesToImmutableJar(target,
                                                        listOf(path)) // TODO: add direct support for directories packing in `packToImmutableJar` instead
    else -> path
  }
  val jar = packToImmutableJar(target,
                               listOfNotNull(shadowed, intermediateJar),
                               logger) // order of jars list matters, shadowed content overrides original content (reason: DebugProbes.kt of shadowed `:dock-coroutines` subproject must override `kotlin-stdlib` ones)
  return PackedJar(
    path = jar,
    needsScrambling = needsScrambling,
    nativeFilesByPlatform = emptyMap(),
  )
}

// TODO: make it more generic, not only for JNA
@OptIn(ExperimentalPathApi::class)
private fun repackToImmutableJarExtractingNativeFiles(
  jar: Path,
  needsScrambling: Boolean,
  shadowed: Path?,
  targetDirectory: Path,
  nativeLibrariesTargetDirectory: Path,
  logger: Logger,
): PackedJar {
  require(shadowed == null) { "shadowing not supported" }
  val tmp = Files.createTempDirectory("repacking").resolve(jar.name)
  extractZip(archive = jar, destination = tmp, stripTopLevelFolder = false, cleanDestination = false, temporaryDir = tmp, logger = logger)
  val nativeFiles = tmp.resolve("com/sun/jna").listDirectoryEntries()
    .filter { it.isDirectory() }
    .mapNotNull { it.listDirectoryEntries().singleOrNull() }
    .filter { lib -> lib.extension in setOf("jnilib", "dylib", "so", "tbd", "dll", "a") }
    .mapNotNull { lib ->
      val libraryPlatform = detectNativeLibraryPlatform(lib.parent.name)
      if (libraryPlatform != null) {
        val target = nativeLibrariesTargetDirectory.resolve(libraryPlatform.toS3DistributionSlug()).createDirectories()
        val targetFile = lib.moveTo(target.resolve(lib.name), overwrite = true)
        libraryPlatform to targetFile
      }
      else {
        lib.deleteExisting()
        null
      }
    }.groupBy({ (platform, _) -> platform }, { (_, file) -> file })
  val jar = packDirectoriesToImmutableJar(targetDirectory.resolve(jar.name), listOf(tmp))
  tmp.deleteRecursively()
  return PackedJar(
    path = jar,
    needsScrambling = needsScrambling,
    nativeFilesByPlatform = nativeFiles,
  )
}

// TODO: delete once `repackToImmutableJarExtractingNativeFiles` is generic
@OptIn(ExperimentalPathApi::class)
private fun repackToImmutableJarExtractingNativeFiles2(
  jar: Path,
  needsScrambling: Boolean,
  shadowed: Path?,
  targetDirectory: Path,
  nativeLibrariesTargetDirectory: Path,
  logger: Logger,
): PackedJar {
  require(shadowed == null) { "shadowing not supported" }
  val tmp = Files.createTempDirectory("repacking").resolve(jar.name)
  extractZip(archive = jar, destination = tmp, cleanDestination = false, stripTopLevelFolder = false, temporaryDir = tmp, logger = logger)
  val nativeFiles = tmp.listDirectoryEntries()
    .filter { lib -> lib.extension in setOf("dylib", "so", "dll") }
    .mapNotNull { lib ->
      val libName = lib.name
      val libraryPlatform = detectNativeLibraryPlatformBasedOnItsName(libName)
      if (libraryPlatform != null) {
        val target = nativeLibrariesTargetDirectory.resolve(libraryPlatform.toS3DistributionSlug()).createDirectories()
        val targetFile = lib.moveTo(target.resolve(lib.name), overwrite = true)
        libraryPlatform to targetFile
      }
      else {
        lib.deleteExisting()
        null
      }
    }.groupBy({ (platform, _) -> platform }, { (_, file) -> file })
  val jar = packDirectoriesToImmutableJar(targetDirectory.resolve(jar.name), listOf(tmp))
  tmp.deleteRecursively()
  return PackedJar(
    path = jar,
    needsScrambling = needsScrambling,
    nativeFilesByPlatform = nativeFiles,
  )
}

private fun packToImmutableJar(destinationJar: Path, jars: List<Path>, logger: Logger): Jar {
  require(jars.map { it.extension }.all { it == "jar" }) { "only jars are supported, got '$jars'" }
  val uniqueJar = jars.singleOrNull()
  when {
    uniqueJar != null && ImmutableZipFile.load(uniqueJar)
      .use { it is ImmutableZipFile } -> uniqueJar.copyTo(destinationJar) // already an immutable jar, not repacking
    else -> {
      val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.NONE)
      destinationJar.parent.createDirectories()
      writeZipUsingTempFile(destinationJar, packageIndexBuilder = packageIndexBuilder) { zipOut ->
        val uniqueNames = HashMap<String, Path>()

        for (jar in jars) {
          readZipFile(jar) { name, dataSupplier ->
            when { // TODO: instead of fixing the FIXMEs, could we instead write a `META-INF/MANIFEST.MF` merger with heuristics and hard failures in case it's not heuristically possible to merge them?
              name == "META-INF/MANIFEST.MF" && jar.name.contains("fleet.dock.coroutines") && jars.any { it.name.contains("kotlin-stdlib") } -> { // FIXME: remove this hack, we control `fleet.dock.coroutines`, could we remove the `META-INF/MANIFEST.MF` instead to avoid the conflict?
                logger.warn("WARNING: '$name' from '${jar.name}' is ignored, `fleet.dock.coroutines` jar has a `META-INF/MANIFEST.MF` which conflicts with the `kotlin-stdlib` one")
              }
              uniqueNames.contains(name) -> {
                logger.warn("WARNING: '$name' from '${jar.name}' is ignored, reason: tooling already packed the one present in '${uniqueNames[name]}'")
              }
              else -> {
                uniqueNames[name] = jar

                packageIndexBuilder.addFile(name)
                val data = dataSupplier()
                zipOut.uncompressedData(name, data)
              }
            }
            ZipEntryProcessorResult.CONTINUE
          }
        }
      }
    }
  }
  return destinationJar
}

private fun packDirectoriesToImmutableJar(destinationJar: Path, directories: List<Path>): Jar {
  require(directories.all { it.isDirectory() }) { "'$directories' must be a directory" }
  val tmpFile = Files.createTempFile(destinationJar.fileName.toString(), "")
  zip(tmpFile, directories.associateWith { "" }, overwrite = true)
  return tmpFile.moveTo(destinationJar)
}

private suspend inline fun <T, U> Iterable<T>.mapConcurrent(crossinline transform: suspend (T) -> U): List<U> =
  coroutineScope {
    map {
      async { transform(it) }
    }.awaitAll()
  }

internal fun <T> Iterable<T>.singleOrNullOrThrow(p: (T) -> Boolean = { true }): T? {
  var single: T? = null
  var found = false
  for (element in this) {
    if (p(element)) {
      if (found) {
        throw IllegalArgumentException("Collection contains more than one matching element: $single, $element")
      }
      single = element
      found = true
    }
  }
  return single
}

private fun detectNativeLibraryPlatform(path: String): Platform? {
  return when {
    path.contains("x86-64") -> Arch.X64
    path.contains("aarch64") -> Arch.AARCH64
    else -> null
  }?.let { arch ->
    when {
      path.startsWith("win32") -> Platform.windows(arch)
      path.startsWith("linux") -> Platform.linux(arch)
      path.startsWith("darwin") -> Platform.macos(arch)
      else -> null
    }
  }
}

/**
 * Skiko and KotlinDesktopToolkit follow this pattern
 */
private fun detectNativeLibraryPlatformBasedOnItsName(libName: String): Platform? {
  return when {
    libName.contains("x64") -> Arch.X64
    libName.contains("arm64") -> Arch.AARCH64
    else -> null
  }?.let { arch ->
    when {
      libName.contains("windows") -> Platform.windows(arch)
      libName.contains("linux") -> Platform.linux(arch)
      libName.contains("macos") -> Platform.macos(arch)
      else -> null
    }
  }
}

fun Iterable<File>.patchKotlinStdlibDebugProbes(dockCoroutinesJar: Path, repackedJarDir: Path, logger: Logger): List<File> {
  // This is a dirty hack to avoid bytebuddy injection at runtime
  // Business wise, it's valid, implementation wise, it's hacky as it breaks encapsulation.
  // All of that is supposed to change soon with the Gradle/Bazel plans.
  val (stdlibJars, others) = this.partition { kotlinStdlibJarNamePattern.matches(it.name) }
  val stdlibJar = stdlibJars.singleOrNull()?.toPath()
  return when {
    stdlibJar != null -> {
      val repackedStdlibJar = repackedJarDir.resolve(stdlibJar.name)
      repackedStdlibJar.deleteIfExists()
      repackedStdlibJar.parent.createDirectories()
      val jar = packToImmutableJar(repackedStdlibJar, listOf(dockCoroutinesJar, stdlibJar), logger)
      others + jar.toFile()
    }

    else -> others
  }
}
