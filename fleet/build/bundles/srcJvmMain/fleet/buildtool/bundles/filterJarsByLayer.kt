package fleet.buildtool.bundles

import fleet.buildtool.fs.sha256
import fleet.bundles.LayerSelector
import org.slf4j.Logger
import java.lang.module.ModuleFinder
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.readBytes

@OptIn(ExperimentalPathApi::class)
internal fun Map<LayerSelector, Set<Path>>.filterJarsByLayer(
  alreadyIncludedJarsByLayer: Map<LayerSelector, Set<Path>>,
  outputDirectory: Path,
  logger: Logger,
): Map<LayerSelector, Collection<Path>> {
  outputDirectory.deleteRecursively()
  outputDirectory.createDirectories()

  val moduleJarsByLayer = this.mapNotNull { (layerSelector, jarsCollection) ->
    val jars = jarsCollection.unwrapJarFiles()
    require(jars.all { it.extension == "jar" }) {
      "must have only jar files in runtime classpath for layer '${layerSelector.selector}', but got: ${jars}"
    }

    if (jars.isEmpty()) return@mapNotNull null

    layerSelector to filterConflictingJars(
      alreadyIncludedJars = alreadyIncludedJarsByLayer[layerSelector] ?: emptySet(),
      jars = jars,
      logger = logger,
    )
  }.toMap()


  logger.info("Copying jars used in descriptor to '$outputDirectory'")
  return moduleJarsByLayer.mapValues { (layerSelector, moduleJars) ->
    moduleJars.map { jar ->
      val targetFileName = "${jar.moduleName}.jar"
      logger.info("[${layerSelector.selector}] copying '$jar' to '$targetFileName'")
      copyJarToOutputDirectory(jar, outputDirectory, targetFileName)
    }
  }
}


private fun filterConflictingJars(
  alreadyIncludedJars: Iterable<Path>,
  jars: Iterable<Path>,
  logger: Logger,
): Set<Path> {
  val alreadyIncludedJarModuleNames = alreadyIncludedJars.mapTo(mutableSetOf()) { jar -> jar.moduleName }
  val (ok, conflicting) = jars.associateWith { jar -> jar.moduleName }.entries.partition { (jar, moduleName) ->
    logger.debug("Processing module '{}' from '{}'", moduleName, jar)
    val alreadyExisting = when (moduleName) {
      "annotations", "org.jetbrains.annotations" -> true // already provided by Kotlin?
      // TODO: is it actually an ok assumption?
      "kotlin.stdlib.jdk8", "kotlin.stdlib.jdk7" -> "kotlin.stdlib" in alreadyIncludedJarModuleNames || moduleName in alreadyIncludedJarModuleNames

      else -> moduleName in alreadyIncludedJarModuleNames
    }
    !alreadyExisting
  }
  val filteredByModuleNameJars = ok.map { (jar, _) -> jar }
  val conflictingModules = conflicting.map { (_, moduleName) -> moduleName }

  if (conflictingModules.isNotEmpty()) { // TODO: better logging including maybe which dep brought the module
    //  no need to provide `exclude` blocks or equivalent as it will be done transparently for the plugin developer
    //  indeed only granular jars will be stripped out, not the dependency itself, so this code has the same value of an
    //  exclude statement, but done automatically
    logger.debug("WARNING: found ${conflictingModules.size} module(s) already provided by either a plugin on which your plugin depends, or Fleet itself." + " They will *not* be added to your plugin module path causing them to be potentially resolved to a different version at runtime." + " List of module name(s): ${
      conflictingModules.joinToString(", ")
    }")
  }

  val filteredJars = filteredByModuleNameJars.filterNot(::isEmptyJar)

  return filteredJars.toSet()
}

internal fun isEmptyJar(jar: Path): Boolean {
  ZipInputStream(jar.inputStream().buffered()).use { zipInputStream ->
    while (true) {
      val entry = zipInputStream.nextEntry ?: break
      if (!entry.isDirectory && emptyJarContents.none { entry.name.startsWith(it, ignoreCase = true) }) {
        return false // Found real content - return fast
      }
    }
    return true
  }
}

internal fun copyJarToOutputDirectory(
  jar: Path,
  outputDirectory: Path,
  targetFileName: String,
): Path {
  val target = outputDirectory.resolve(targetFileName)
  try {
    jar.copyTo(target = target, overwrite = false)
  }
  catch (_: FileAlreadyExistsException) {
    when {
      sha256(target.readBytes()) == sha256(jar.readBytes()) -> {} // TODO: could we ensure this never has to be called? Technically such jar should be added once in the common layer instead of in frontend and workspace layers for example
      else -> error("two or more layers of this plugin refer to a different jar called '${jar.name}'")
    }
  }
  return target
}

private val jarToModuleNameCache: MutableMap<Path, String> = ConcurrentHashMap<Path, String>()

internal val Path.moduleName: String
  get() = jarToModuleNameCache.computeIfAbsent(this) { ModuleFinder.of(this).findAll().single().descriptor().name() }