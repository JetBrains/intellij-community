// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.text.NameUtilCore
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.maven.model.Dependency
import org.apache.maven.model.Exclusion
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.DirSource
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.buildJar
import org.jetbrains.intellij.build.telemetry.useWithScope
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.BiConsumer

/**
 * Generates Maven artifacts for IDE and plugin modules. Artifacts aren't generated for modules which depend on non-repository libraries.
 *
 * @see [org.jetbrains.intellij.build.ProductProperties.mavenArtifacts]
 * @see [org.jetbrains.intellij.build.BuildOptions.Companion.MAVEN_ARTIFACTS_STEP]
 */
open class MavenArtifactsBuilder(protected val context: BuildContext) {
  companion object {
    @JvmStatic
    fun generateMavenCoordinatesSquashed(moduleName: String, version: String): MavenCoordinates {
      return generateMavenCoordinates("$moduleName.squashed", version)
    }

    @JvmStatic
    fun generateMavenCoordinates(moduleName: String, version: String): MavenCoordinates {
      val names = moduleName.split('.')
      if (names.size < 2) {
        throw RuntimeException("Cannot generate Maven artifacts: incorrect module name '${moduleName}'")
      }

      val groupId = "com.jetbrains.${names.take(2).joinToString(separator = ".")}"
      val firstMeaningful = if (names.size > 2 && COMMON_GROUP_NAMES.contains(names[1])) 2 else 1
      val artifactId = names.drop(firstMeaningful).flatMap { s ->
        splitByCamelHumpsMergingNumbers(s).map { it.lowercase(Locale.US) }
      }.joinToString(separator = "-")
      return MavenCoordinates(groupId, artifactId, version)
    }

    internal fun scopedDependencies(module: JpsModule): Map<JpsDependencyElement, DependencyScope> {
      val result = LinkedHashMap<JpsDependencyElement, DependencyScope>()
      for (dependency in module.dependenciesList.dependencies) {
        val extension = JpsJavaExtensionService.getInstance().getDependencyExtension(dependency) ?: continue
        result[dependency] = when (extension.scope) {
          JpsJavaDependencyScope.COMPILE ->
            //if a dependency isn't exported, transitive dependencies will include it in runtime classpath only
            if (extension.isExported) DependencyScope.COMPILE else DependencyScope.RUNTIME
          JpsJavaDependencyScope.RUNTIME -> DependencyScope.RUNTIME

          JpsJavaDependencyScope.PROVIDED ->
            //'provided' scope is used only for compilation, and it shouldn't be exported
            continue
          JpsJavaDependencyScope.TEST ->
            continue
          else ->
            continue
        }
      }
      return result
    }

    fun isOptionalDependency(library: JpsLibrary?): Boolean {
      //todo: this is a temporary workaround until these libraries are published to Maven repository;
      // it's unlikely that code which depend on these libraries will be used when running tests so skipping these dependencies shouldn't cause real problems.
      //  'microba' contains UI elements which are used in few places (IDEA-200834),
      //  'precompiled_jshell-frontend' is used by "JShell Console" action only (IDEA-222381).
      return library!!.name == "microba" || library.name == "jshell-frontend"
    }

    @JvmStatic
    fun createDependencyTagByLibrary(descriptor: JpsMavenRepositoryLibraryDescriptor): Dependency {
      return createDependencyTag(createArtifactDependencyByLibrary(descriptor, DependencyScope.COMPILE))
    }
  }

  suspend fun generateMavenArtifacts(
    moduleNamesToPublish: Collection<String>,
    moduleNamesToSquashAndPublish: List<String> = emptyList(),
    outputDir: String,
  ) {
    generateMavenArtifacts(
      moduleNamesToPublish,
      moduleNamesToSquashAndPublish,
      outputDir,
      ignoreNonMavenizable = false,
      builtArtifacts = mutableSetOf(),
    )
  }

  internal suspend fun generateMavenArtifacts(
    moduleNamesToPublish: Collection<String>,
    moduleNamesToSquashAndPublish: List<String> = emptyList(),
    outputDir: String,
    ignoreNonMavenizable: Boolean = false,
    builtArtifacts: MutableSet<MavenArtifactData>,
  ) {
    val artifactsToBuild = HashMap<MavenArtifactData, List<JpsModule>>()

    generateMavenArtifactData(moduleNamesToPublish, ignoreNonMavenizable).forEach(BiConsumer { aModule, artifactData ->
      artifactsToBuild[artifactData] = listOf(aModule)
    })

    val squashingMavenArtifactsData = generateMavenArtifactData(moduleNamesToSquashAndPublish, ignoreNonMavenizable)
    for (moduleName in moduleNamesToSquashAndPublish) {
      val module = context.findRequiredModule(moduleName)
      val modules = JpsJavaExtensionService.dependencies(module)
        .recursively().withoutSdk().includedIn(JpsJavaClasspathKind.runtime(false)).modules

      val moduleCoordinates = modules.mapTo(HashSet()) { aModule -> generateMavenCoordinatesForModule(aModule) }
      val dependencies = modules
        .asSequence()
        .flatMap { aModule -> squashingMavenArtifactsData.getValue(aModule).dependencies }
        .distinct()
        .filter { !moduleCoordinates.contains(it.coordinates) }
        .toList()

      val coordinates = generateMavenCoordinatesForSquashedModule(module, context)
      artifactsToBuild[MavenArtifactData(coordinates, dependencies)] = modules.toList()
    }
    artifactsToBuild -= builtArtifacts
    spanBuilder("layout maven artifacts")
      .setAttribute(AttributeKey.stringArrayKey("modules"), artifactsToBuild.entries.map { entry ->
        "  [${entry.value.joinToString(separator = ",") { it.name }}] -> ${entry.key.coordinates}"
      }).useWithScope {
        layoutMavenArtifacts(artifactsToBuild, context.paths.artifactDir.resolve(outputDir), context)
      }
    builtArtifacts += artifactsToBuild.keys
  }

  private fun generateMavenArtifactData(moduleNames: Collection<String>, ignoreNonMavenizable: Boolean): Map<JpsModule, MavenArtifactData> {
    val results = HashMap<JpsModule, MavenArtifactData>()
    val nonMavenizableModulesSet = HashSet<JpsModule>()
    val computationInProgressSet = HashSet<JpsModule>()
    for (module in moduleNames.asSequence().map(context::findRequiredModule)) {
      generateMavenArtifactData(module, results, nonMavenizableModulesSet, computationInProgressSet)
    }
    val nonMavenizableModules by lazy { moduleNames.intersect(nonMavenizableModulesSet.asSequence().map { it.name }.toSet()) }
    check(ignoreNonMavenizable || nonMavenizableModules.isEmpty()) {
      nonMavenizableModules.joinToString(prefix = "Modules cannot be published as Maven artifacts:\n", separator = "\n")
    }
    return results
  }

  private fun generateMavenArtifactData(module: JpsModule,
                                        results: MutableMap<JpsModule, MavenArtifactData>,
                                        nonMavenizableModules: MutableSet<JpsModule>,
                                        computationInProgress: MutableSet<JpsModule>): MavenArtifactData? {
    if (results.containsKey(module)) {
      return results[module]
    }
    if (nonMavenizableModules.contains(module)) {
      return null
    }

    if (shouldSkipModule(moduleName = module.name, moduleIsDependency = false)) {
      Span.current().addEvent("module doesn't belong to IntelliJ project so it cannot be published", Attributes.of(
        AttributeKey.stringKey("module"), module.name
      ))
      return null
    }
    val scrambleTool = context.proprietaryBuildTools.scrambleTool
    if (scrambleTool != null && scrambleTool.namesOfModulesRequiredToBeScrambled.contains(module.name)) {
      Span.current().addEvent("module must be scrambled so it cannot be published", Attributes.of(
        AttributeKey.stringKey("module"), module.name
      ))
      return null
    }

    var mavenizable = true
    computationInProgress.add(module)
    val dependencies = ArrayList<MavenArtifactDependency>()
    for ((dependency, scope) in scopedDependencies(module)) {
      if (dependency is JpsModuleDependency) {
        val depModule = dependency.module
        if (depModule == null || shouldSkipModule(depModule.name, true)) {
          continue
        }

        if (computationInProgress.contains(depModule)) {
          /*
           It's forbidden to have compile-time circular dependencies in the IntelliJ project, but there are some cycles with runtime scope
            (e.g. intellij.platform.ide.impl depends on (runtime scope) intellij.platform.configurationStore.impl which depends on intellij.platform.ide.impl).
           It's convenient to have such dependencies to allow running tests in classpath of their modules, so we can just ignore them while
           generating pom.xml files.
          */
          Span.current().addEvent("skip recursive dependency on", Attributes.of(
            AttributeKey.stringKey("module"), module.name,
            AttributeKey.stringKey("dependencyModule"), depModule.name,
          ))
        }
        else {
          val depArtifact = generateMavenArtifactData(depModule, results, nonMavenizableModules, computationInProgress)
          if (depArtifact == null) {
            Span.current().addEvent("module depends on non-mavenizable module so it cannot be published", Attributes.of(
              AttributeKey.stringKey("module"), module.name,
              AttributeKey.stringKey("dependencyModule"), depModule.name,
            ))
            mavenizable = false
            continue
          }
          dependencies.add(MavenArtifactDependency(depArtifact.coordinates, true, ArrayList<String>(), scope as DependencyScope?))
        }
      }
      else if (dependency is JpsLibraryDependency) {
        val library = dependency.library!!
        val typed = library.asTyped(JpsRepositoryLibraryType.INSTANCE)
        if (typed != null) {
          dependencies.add(createArtifactDependencyByLibrary(typed.properties.data, scope as DependencyScope?))
        }
        else if (!isOptionalDependency(library)) {
          Span.current().addEvent("module depends on non-maven library", Attributes.of(
            AttributeKey.stringKey("module"), module.name,
            AttributeKey.stringKey("library"), getLibraryFilename(library),
          ))
          mavenizable = false
        }
      }
    }
    computationInProgress.remove(module)
    if (!mavenizable) {
      nonMavenizableModules.add(module)
      return null
    }

    val artifactData = MavenArtifactData(generateMavenCoordinatesForModule(module), dependencies)
    results[module] = artifactData
    return artifactData
  }

  protected open fun shouldSkipModule(moduleName: String, moduleIsDependency: Boolean): Boolean {
    return if (moduleIsDependency) false else !moduleName.startsWith("intellij.")
  }

  protected open fun generateMavenCoordinatesForModule(module: JpsModule): MavenCoordinates {
    return generateMavenCoordinates(module.name, context.buildNumber)
  }
}

internal enum class DependencyScope {
  COMPILE, RUNTIME
}

private fun generateMavenCoordinatesForSquashedModule(module: JpsModule, context: BuildContext): MavenCoordinates {
  return MavenArtifactsBuilder.generateMavenCoordinatesSquashed(module.name, context.buildNumber)
}

data class MavenCoordinates(
  val groupId: String,
  val artifactId: String,
  val version: String,
) {
  override fun toString() = "$groupId:$artifactId:$version"

  val directoryPath: String
    get() = "${groupId.replace('.', '/')}/$artifactId/$version"

  fun getFileName(classifier: String, packaging: String): String {
    return "$artifactId-$version${if (classifier.isEmpty()) "" else "-$classifier"}.$packaging"
  }
}

internal data class MavenArtifactData(
  val coordinates: MavenCoordinates,
  val dependencies: List<MavenArtifactDependency>
)

internal data class MavenArtifactDependency(
  val coordinates: MavenCoordinates,
  val includeTransitiveDeps: Boolean,
  val excludedDependencies: List<String>,
  val scope: DependencyScope?
)

private fun generatePomXmlData(artifactData: MavenArtifactData, file: Path) {
  val pomModel = Model()
  pomModel.modelVersion = "4.0.0"
  pomModel.groupId = artifactData.coordinates.groupId
  pomModel.artifactId = artifactData.coordinates.artifactId
  pomModel.version = artifactData.coordinates.version
  artifactData.dependencies.forEach {
    pomModel.addDependency(createDependencyTag(it))
  }

  Files.newBufferedWriter(file).use {
    MavenXpp3Writer().write(it, pomModel)
  }
}

private fun createDependencyTag(dep: MavenArtifactDependency): Dependency {
  val dependency = Dependency()
  dependency.groupId = dep.coordinates.groupId
  dependency.artifactId = dep.coordinates.artifactId
  dependency.version = dep.coordinates.version
  if (dep.scope == DependencyScope.RUNTIME) {
    dependency.scope = "runtime"
  }
  if (dep.includeTransitiveDeps) {
    for (it in dep.excludedDependencies) {
      val exclusion = Exclusion()
      exclusion.groupId = it.substringBefore(':')
      exclusion.artifactId = it.substringAfter(':')
      dependency.addExclusion(exclusion)
    }
  }
  else {
    val exclusion = Exclusion()
    exclusion.groupId = "*"
    exclusion.artifactId = "*"
    dependency.addExclusion(exclusion)
  }
  return dependency
}

private fun createArtifactDependencyByLibrary(descriptor: JpsMavenRepositoryLibraryDescriptor,
                                              scope: DependencyScope?): MavenArtifactDependency {
  return MavenArtifactDependency(coordinates = MavenCoordinates(groupId = descriptor.groupId,
                                                                artifactId = descriptor.artifactId,
                                                                version = descriptor.version),
                                 includeTransitiveDeps = descriptor.isIncludeTransitiveDependencies,
                                 excludedDependencies = descriptor.excludedDependencies, scope = scope)
}

private fun splitByCamelHumpsMergingNumbers(s: String): List<String> {
  val words = NameUtilCore.splitNameIntoWords(s)
  val result = ArrayList<String>()
  var i = 0
  while (i < words.size) {
    var next: String
    if (i < words.size - 1 && Character.isDigit(words[i + 1][0])) {
      next = words[i] + words[i + 1]
      i++
    }
    else {
      next = words[i]
    }
    result.add(next)
    i++
  }
  return result
}

/**
 * the second component of module names which describes a common group rather than a specific framework
 * and therefore should be excluded from artifactId
 */
private val COMMON_GROUP_NAMES: Set<String> = setOf("platform", "vcs", "tools", "clouds")

private suspend fun layoutMavenArtifacts(modulesToPublish: Map<MavenArtifactData, List<JpsModule>>,
                                         outputDir: Path,
                                         context: BuildContext) {
  val publishSourceFilter = context.productProperties.mavenArtifacts.publishSourcesFilter
  coroutineScope {
    for ((artifactData, modules) in modulesToPublish.entries) {
      launch {
        val modulesWithSources = modules.filter {
          it.getSourceRoots(JavaSourceRootType.SOURCE).any() || it.getSourceRoots(JavaResourceRootType.RESOURCE).any()
        }

        val dirPath = artifactData.coordinates.directoryPath
        val artifactDir = outputDir.resolve(dirPath)
        Files.createDirectories(artifactDir)

        generatePomXmlData(artifactData = artifactData,
                           file = artifactDir.resolve(artifactData.coordinates.getFileName("", "pom")))

        buildJar(
          targetFile = artifactDir.resolve(artifactData.coordinates.getFileName("", "jar")),
          sources = modulesWithSources.map {
            DirSource(dir = context.getModuleOutputDir(it), excludes = commonModuleExcludes)
          },
        )

        val publishSourcesForModules = modules.filter { publishSourceFilter(it, context) }
        if (!publishSourcesForModules.isEmpty() && !modulesWithSources.isEmpty()) {
          buildJar(
            targetFile = artifactDir.resolve(artifactData.coordinates.getFileName("sources", "jar")),
            sources = publishSourcesForModules.flatMap { module ->
              module.getSourceRoots(JavaSourceRootType.SOURCE).asSequence().map {
                DirSource(dir = it.path, prefix = it.properties.packagePrefix.replace('.', '/'), excludes = commonModuleExcludes)
              } +
              module.getSourceRoots(JavaResourceRootType.RESOURCE).asSequence().map {
                DirSource(dir = it.path, prefix = it.properties.relativeOutputPath, excludes = commonModuleExcludes)
              }
            },
            compress = true,
          )
        }
      }
    }
  }
}
