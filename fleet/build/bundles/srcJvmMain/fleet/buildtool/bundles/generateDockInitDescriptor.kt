package fleet.buildtool.bundles

import fleet.buildtool.codecache.HashedJar
import fleet.buildtool.codecache.ModulePacker
import fleet.buildtool.codecache.PluginPart
import fleet.buildtool.codecache.detectNativeLibraryPlatform
import fleet.buildtool.codecache.detectNativeLibraryPlatformBasedOnItsName
import fleet.buildtool.codecache.specs.NativeLibrariesExtractorSpec
import fleet.buildtool.codecache.specs.NativeLibraryExtractor
import fleet.buildtool.codecache.specs.ShadowedJarSpec
import fleet.buildtool.scrambling.JarScrambler
import fleet.codecache.CodeCacheHasher
import org.slf4j.Logger
import java.lang.module.ModuleFinder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText

suspend fun generateDockInitDescriptor(
  moduleName: String,
  runtimeClasspath: List<Path>,
  targetJdkVersionFeature: Int,
  scrambler: JarScrambler,
  logger: Logger,

  outputModuleDescriptor: Path,
  outputImmutableJars: Path?,
  outputNativeLibs: Path?,
) {
  outputModuleDescriptor.deleteIfExists()

  val descriptorClassPath = when {
    outputImmutableJars != null -> {
      requireNotNull(outputNativeLibs) {
        "output-immutable-jars is specified, but output-native-libs is not. Both options must be specified when generating immutableJars"
      }
      val temporaryDir = createTempDirectory("init-descriptor-jars")
      val uniqueJarsDir = temporaryDir.resolve("unique-jars")
      uniqueJarsDir.deleteIfExists()
      uniqueJarsDir.createDirectories()
      val uniqueJarsClassPath = runtimeClasspath.map {
        val uniqueJarName = ModuleFinder.of(it).findAll().single().descriptor().name() + ".jar"
        it.copyTo(uniqueJarsDir.resolve(uniqueJarName), overwrite = true)
      }
      val packedModulesDir = temporaryDir.resolve("packed-modules")
      val modulePacker = ModulePacker(
        directory = packedModulesDir,
        nativeLibraryExtractor = NativeLibraryExtractor.ExtractTo(
          directory = outputNativeLibs,
          specifications = listOf(extractJnaLibrarySpec, extractKotlinDesktopLibrarySpec)
        ),
        version = null,
        logger = logger,
        shadowedJarSpecs = listOf(dockCoroutinesShadowedJarSpec),
        scrambledJarSpecs = emptyList(),
      )

      val packedJars = packModule(
        moduleName = moduleName,
        jars = uniqueJarsClassPath,
        packer = modulePacker,
        fullClassPath = uniqueJarsClassPath,
        outputDirectory = packedModulesDir,
        logger = logger,
        scrambler = scrambler,
      )
      packedJars.map { jar -> jar.copyTo(outputImmutableJars.resolve(jar.fileName), overwrite = true) }
    }
    else -> runtimeClasspath
  }

  val classpath = descriptorClassPath.map { file ->
    PluginPart.Bundled(HashedJar.fromFile(
      hash = CodeCacheHasher().hash(file),
      file = file,
      jdkVersionFeature = targetJdkVersionFeature,
    ))
  }

  outputModuleDescriptor.writeText(classpath.toModulePathFormat())
}

private fun List<PluginPart.Bundled>.toModulePathFormat(): String =
  sortedBy { it.codeCacheFile.name }.joinToString(separator = "\n") { part ->
    "${part.relativePathInCodeCache.invariantSeparatorsPathString}\n${part.jar.moduleDescriptor}"
  }

private val dockCoroutinesShadowedJarSpec = ShadowedJarSpec(
  allowedConsumerModule = "fleet.dock.runtime",
  consumerJarPattern = Regex("kotlin.stdlib.*\\.jar"),
  shadowedJarPattern = Regex("fleet\\.dock\\.coroutines.*\\.jar"), // fleet.dock,coroutines-$version.jar in Gradle
  jpmsModuleName = "fleet.dock.coroutines",
)

private val extractJnaLibrarySpec = NativeLibrariesExtractorSpec(
  jarNamePattern = Regex("com\\.sun\\.jna\\.jar"),
  allowedExtensions = setOf("jnilib", "dylib", "so", "tbd", "dll", "a"),
  nativeLibrariesSelector = { extractedRoot ->
    val nativeLibrariesRoot = extractedRoot.resolve("com/sun/jna")
    if (!Files.exists(nativeLibrariesRoot)) {
      emptyList()
    }
    else {
      nativeLibrariesRoot.listDirectoryEntries()
        .filter { it.isDirectory() }
        .mapNotNull { it.listDirectoryEntries().singleOrNull() }
    }
  },
  platformDetector = { library -> detectNativeLibraryPlatform(library.parent.name) },
)

private val extractKotlinDesktopLibrarySpec = NativeLibrariesExtractorSpec(
  jarNamePattern = Regex("kotlin\\.desktop\\.toolkit.*\\.jar"),
  allowedExtensions = setOf("dylib", "so", "dll"),
  nativeLibrariesSelector = { extractedRoot -> extractedRoot.listDirectoryEntries() },
  platformDetector = { library -> detectNativeLibraryPlatformBasedOnItsName(library.name) },
)