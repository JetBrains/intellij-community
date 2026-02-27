package fleet.buildtool.bundles

import fleet.buildtool.fs.sha256
import fleet.bundles.LayerSelector
import org.slf4j.Logger
import java.lang.module.ModuleFinder
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
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
      copyJarToOutputDirectory(jar, layerSelector, outputDirectory, logger)
    }
  }
}

private fun filterConflictingJars(
  alreadyIncludedJars: Iterable<Path>,
  jars: Iterable<Path>,
  logger: Logger,
): Set<Path> {
  val alreadyProvidedModuleFinder = ModuleFinder.of(
    *alreadyIncludedJars.toList().toTypedArray(),
  )

  val (ok, conflicting) = jars.associateWith { jar ->
    ModuleFinder.of(jar).findAll().single().descriptor().name()
  }.entries.partition { (jar, moduleName) ->
    logger.debug("Processing module '{}' from '{}'", moduleName, jar)
    val alreadyExisting = when (moduleName) {
      "annotations", "org.jetbrains.annotations" -> true // already provided by Kotlin?
      // TODO: is it actually an ok assumption?
      "kotlin.stdlib.jdk8", "kotlin.stdlib.jdk7" -> alreadyProvidedModuleFinder.find("kotlin.stdlib").isPresent || alreadyProvidedModuleFinder.find(
        moduleName).isPresent

      else -> alreadyProvidedModuleFinder.find(moduleName).isPresent
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

private fun isEmptyJar(jar: Path): Boolean {
  ZipInputStream(jar.inputStream().buffered()).use { zipInputStream ->
    while (true) {
      val entry = zipInputStream.nextEntry ?: break
      if (!entry.isDirectory && emptyJarContents.none { entry.name.endsWith(it, ignoreCase = true) }) {
        return false // Found real content - return fast
      }
    }
    return true
  }
}

private fun copyJarToOutputDirectory(jar: Path, layerSelector: LayerSelector, outputDirectory: Path, logger: Logger): Path {
  val moduleName = ModuleFinder.of(jar).findAll().single().descriptor().name()
  val target = outputDirectory.resolve("$moduleName.jar")
  logger.info("[${layerSelector.selector}] copying '$jar' to '$target'")
  try {
    jar.copyTo(target = target, overwrite = false)
  }
  catch (e: FileAlreadyExistsException) {
    when {
      sha256(target.readBytes()) == sha256(jar.readBytes()) -> {} // TODO: could we ensure this never has to be called? Technically such jar should be added once in the common layer instead of in frontend and workspace layers for example
      else -> error("two or more layers of this plugin refer to a different jar called '${jar.name}'")
    }
  }
  return target
}
