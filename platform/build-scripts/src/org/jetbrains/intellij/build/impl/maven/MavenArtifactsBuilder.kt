// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.maven

import com.intellij.util.text.NameUtilCore
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.apache.maven.model.Dependency
import org.apache.maven.model.Developer
import org.apache.maven.model.Exclusion
import org.apache.maven.model.Model
import org.apache.maven.model.Organization
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.DirSource
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.buildJar
import org.jetbrains.intellij.build.impl.commonModuleExcludes
import org.jetbrains.intellij.build.impl.createModuleSourcesNamesFilter
import org.jetbrains.intellij.build.impl.getLibraryFilename
import org.jetbrains.intellij.build.impl.libraries.isLibraryModule
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.function.BiConsumer

/**
 * Generates Maven artifacts for IDE and plugin modules. Artifacts aren't generated for modules which depend on non-repository libraries.
 *
 * @see [org.jetbrains.intellij.build.ProductProperties.mavenArtifacts]
 * @see [org.jetbrains.intellij.build.BuildOptions.Companion.MAVEN_ARTIFACTS_STEP]
 */
open class MavenArtifactsBuilder(protected val context: BuildContext) {
  fun generateMavenCoordinatesSquashed(moduleName: String, version: String): MavenCoordinates {
    return generateMavenCoordinates("$moduleName$SQUASHED_SUFFIX", version)
  }

  fun generateMavenCoordinates(moduleName: String, version: String): MavenCoordinates {
    val names = moduleName.split('.')
    if (names.size < 2) {
      throw RuntimeException("Cannot generate Maven artifacts: incorrect module name '${moduleName}'")
    }
    // handle "fleet.*" modules
    if (names.first() == "fleet") {
      val groupId = "com.jetbrains.intellij.fleet"
      val artifactId = names.drop(1).flatMap { s ->
        splitByCamelHumpsMergingNumbers(s).map { it.lowercase(Locale.US) }
      }.joinToString(separator = "-")
      return MavenCoordinates(groupId, artifactId, version)
    }
    // handle "intellij.*" modules
    val groupId = "com.jetbrains.${names.take(2).joinToString(separator = ".")}"
    val firstMeaningful = if (names.size > 2 && COMMON_GROUP_NAMES.contains(names[1])) 2 else 1
    val artifactId = names.drop(firstMeaningful).flatMap { s ->
      splitByCamelHumpsMergingNumbers(s).map { it.lowercase(Locale.US) }
    }.joinToString(separator = "-")
    return context.productProperties.mavenArtifacts.patchCoordinates(
      context.findRequiredModule(moduleName.removeSuffix(SQUASHED_SUFFIX)),
      MavenCoordinates(groupId, artifactId, version),
    )
  }

  companion object {
    private const val SQUASHED_SUFFIX = ".squashed"

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

    private val FLEET_MODULES_ALLOWED_FOR_PUBLICATION = setOf(
      // region Fleet modules in Community
      "fleet.andel",
      "fleet.kernel",
      "fleet.multiplatform.shims",
      "fleet.reporting.api",
      "fleet.reporting.shared",
      "fleet.rhizomedb",
      "fleet.rpc",
      "fleet.rpc.server",
      "fleet.util.core",
      "fleet.util.logging.api",
      "fleet.util.logging.slf4j",
      "fleet.util.multiplatform",
      "fleet.fastutil",
      "fleet.lsp.protocol", // Fleet Language Server Protocol modules allowed for publication - https://youtrack.jetbrains.com/issue/IJI-2644
      "fleet.ktor.network.tls",
      // endregion
    )
  }

  /**
   * @param outputDir path relative to [org.jetbrains.intellij.build.BuildPaths.artifactDir]
   */
  suspend fun generateMavenArtifacts(
    moduleNamesToPublish: Collection<String>,
    moduleNamesToSquashAndPublish: List<String> = emptyList(),
    outputDir: String,
    validate: Boolean = false,
  ) {
    generateMavenArtifacts(
      moduleNamesToPublish,
      moduleNamesToSquashAndPublish,
      outputDir,
      ignoreNonMavenizable = false,
      builtArtifacts = mutableMapOf(),
      validate = validate,
    )
  }

  /**
   * @param outputDir path relative to [org.jetbrains.intellij.build.BuildPaths.artifactDir]
   */
  internal suspend fun generateMavenArtifacts(
    moduleNamesToPublish: Collection<String>,
    moduleNamesToSquashAndPublish: List<String> = emptyList(),
    outputDir: String,
    ignoreNonMavenizable: Boolean = false,
    builtArtifacts: MutableMap<MavenArtifactData, List<Path>>,
    validate: Boolean = false,
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

      val coordinates = generateMavenCoordinatesSquashed(module.name, context.buildNumber)
      artifactsToBuild[MavenArtifactData(module, coordinates, dependencies)] = modules.toList()
    }
    artifactsToBuild -= builtArtifacts.keys
    builtArtifacts += spanBuilder("layout maven artifacts")
      .setAttribute(AttributeKey.stringArrayKey("modules"), artifactsToBuild.entries.map { entry ->
        "  [${entry.value.joinToString(separator = ",") { it.name }}] -> ${entry.key.coordinates}"
      }).use {
        layoutMavenArtifacts(artifactsToBuild, context.paths.artifactDir.resolve(outputDir), context)
      }
    if (validate) {
      validate(builtArtifacts)
    }
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
          if (depArtifact.module.isLibraryModule()) {
            check(depArtifact.dependencies.any()) {
              "A library module ${depArtifact.module.name} is expected to have some library dependencies"
            }
            dependencies += depArtifact.dependencies
          }
          else {
            dependencies.add(MavenArtifactDependency(depArtifact.coordinates, true, ArrayList(), scope))
          }
        }
      }
      else if (dependency is JpsLibraryDependency) {
        val library = dependency.library!!
        val typed = library.asTyped(JpsRepositoryLibraryType.INSTANCE)
        if (typed != null) {
          dependencies.add(createArtifactDependencyByLibrary(typed.properties.data, scope))
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
    val patchedDependencies = context.productProperties.mavenArtifacts.patchDependencies(module, dependencies)
    computationInProgress.remove(module)
    if (!mavenizable) {
      nonMavenizableModules.add(module)
      return null
    }

    val artifactData = MavenArtifactData(module, generateMavenCoordinatesForModule(module), patchedDependencies)
    if (!module.isLibraryModule()) {
      results[module] = artifactData
    }
    return artifactData
  }

  protected open fun shouldSkipModule(moduleName: String, moduleIsDependency: Boolean): Boolean {
    val moduleShouldBePublished = moduleIsDependency || moduleName in FLEET_MODULES_ALLOWED_FOR_PUBLICATION || moduleName.startsWith("intellij.")
    return !moduleShouldBePublished
  }

  protected open fun generateMavenCoordinatesForModule(module: JpsModule): MavenCoordinates {
    return generateMavenCoordinates(module.name, context.buildNumber)
  }

  internal fun validate(builtArtifacts: Map<MavenArtifactData, List<Path>>) {
    context.productProperties.mavenArtifacts.validate(context, builtArtifacts.map { (data, files) ->
      GeneratedMavenArtifacts(data.module, data.coordinates, files)
    })
  }
}

enum class DependencyScope {
  COMPILE, RUNTIME
}

data class MavenCoordinates(
  val groupId: String,
  val artifactId: String,
  val version: String,
) {
  override fun toString(): String = "$groupId:$artifactId:$version"

  val directoryPath: String
    get() = "${groupId.replace('.', '/')}/$artifactId/$version"

  val filesPrefix: String
    get() = "$artifactId-$version"

  fun getFileName(classifier: String = "", packaging: String): String {
    return "$filesPrefix${if (classifier.isEmpty()) "" else "-$classifier"}.$packaging"
  }
}

internal data class MavenArtifactData(
  val module: JpsModule,
  val coordinates: MavenCoordinates,
  val dependencies: List<MavenArtifactDependency>
)

data class MavenArtifactDependency(
  val coordinates: MavenCoordinates,
  val includeTransitiveDeps: Boolean,
  val excludedDependencies: List<String>,
  val scope: DependencyScope?
)

private fun Model.setOrFailIfAlreadySet(name: String, value: String, getter: Model.() -> String?, setter: Model.(String) -> Unit) {
  check(getter() == null) {
    "$name should not be specified for $this, will be overridden with $value"
  }
  setter(value)
}

private fun generatePomXmlData(artifactData: MavenArtifactData, file: Path, context: BuildContext) {
  val pomModel = Model()
  pomModel.organization = Organization().apply {
    name = "JetBrains"
    url = "https://jetbrains.team"
  }
  pomModel.addDeveloper(Developer().apply {
    id = "JetBrains"
    name = "JetBrains Team"
    organization = "JetBrains"
    organizationUrl = "https://www.jetbrains.com"
  })
  context.productProperties.mavenArtifacts.addPomMetadata(artifactData.module, pomModel)
  pomModel.setOrFailIfAlreadySet("Model version", value = "4.0.0", { modelVersion }, { modelVersion = it })
  pomModel.setOrFailIfAlreadySet("GroupId", value = artifactData.coordinates.groupId, { groupId }, { groupId = it })
  pomModel.setOrFailIfAlreadySet("ArtifactId", value = artifactData.coordinates.artifactId, { artifactId }, { artifactId = it })
  pomModel.setOrFailIfAlreadySet("Version", value = artifactData.coordinates.version, { version }) { version = it }
  // From https://central.sonatype.org/publish/requirements/#project-name-description-and-url:
  // A common and acceptable practice for name is to assemble it from the coordinates using Maven properties
  pomModel.name = "${pomModel.groupId}:${pomModel.artifactId}"
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

private suspend fun layoutMavenArtifacts(
  modulesToPublish: Map<MavenArtifactData, List<JpsModule>>,
  outputDir: Path,
  context: BuildContext,
): Map<MavenArtifactData, List<Path>> {
  return coroutineScope {
    modulesToPublish.entries.map { (artifactData, modules) ->
      async(CoroutineName("layout maven artifact ${artifactData.coordinates}")) {
        val artifacts = mutableListOf<Path>()
        val modulesWithSources = modules.filter {
          it.getSourceRoots(JavaSourceRootType.SOURCE).any() || it.getSourceRoots(JavaResourceRootType.RESOURCE).any()
        }

        val dirPath = artifactData.coordinates.directoryPath
        val artifactDir = outputDir.resolve(dirPath)
        Files.createDirectories(artifactDir)
        val pom = artifactDir.resolve(artifactData.coordinates.getFileName(packaging = "pom"))
        generatePomXmlData(
          context = context,
          artifactData = artifactData,
          file = pom,
        )
        artifacts.add(pom)
        val jar = artifactDir.resolve(artifactData.coordinates.getFileName(packaging = "jar"))
        buildJar(
          targetFile = jar,
          sources = modulesWithSources.flatMap {
            context.getModuleOutputRoots(it).map { moduleOutput ->
              check(Files.exists(moduleOutput)) {
                "$it module output directory doesn't exist: $moduleOutput"
              }
              if (moduleOutput.toString().endsWith(".jar")) {
                ZipSource(file = moduleOutput, distributionFileEntryProducer = null, filter = createModuleSourcesNamesFilter(commonModuleExcludes))
              }
              else {
                DirSource(dir = moduleOutput, excludes = commonModuleExcludes)
              }
            }
          },
        )
        artifacts.add(jar)

        val publishSourcesForModules = modules.filter {
          context.productProperties.mavenArtifacts.publishSourcesFilter(it, context)
        }
        if (!publishSourcesForModules.isEmpty() && !modulesWithSources.isEmpty()) {
          val sources = artifactDir.resolve(artifactData.coordinates.getFileName("sources", "jar"))
          buildJar(
            targetFile = sources,
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
          artifacts.add(sources)
        }
        if (context.productProperties.mavenArtifacts.isJavadocJarRequired(artifactData.module)) {
          check(modulesWithSources.any()) {
            "No modules with sources found in $modules, a documentation cannot be generated"
          }
          val docsFolder = Dokka(context).generateDocumentation(modules = modulesWithSources)
          val javadoc = artifactDir.resolve(artifactData.coordinates.getFileName("javadoc", "jar"))
          buildJar(
            targetFile = javadoc,
            sources = listOf(DirSource(docsFolder)),
            compress = true,
          )
          artifacts.add(javadoc)
        }
        artifactData to artifacts
      }
    }.associate { it.await() }
  }
}

@ApiStatus.Internal
data class GeneratedMavenArtifacts(
  val module: JpsModule,
  val coordinates: MavenCoordinates,
  val files: List<Path>,
)