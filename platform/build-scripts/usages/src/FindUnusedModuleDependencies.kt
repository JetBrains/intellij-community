// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOfSystemOutOrSystemErr")

package com.intellij.platform.buildScripts.usages

import org.jetbrains.jps.dependency.impl.DependencyGraphImpl
import org.jetbrains.jps.dependency.impl.PathSourceMapper
import org.jetbrains.jps.dependency.java.JvmClass
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.BufferedInputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.system.exitProcess

internal object FindUnusedModuleDependencies {
  @JvmStatic
  fun main(args: Array<String>) {
    val result = run(args.toList())
    if (result.output.isNotEmpty()) {
      println(result.output)
    }
    exitProcess(result.exitCode)
  }

  internal fun run(args: List<String>): CliRunResult {
    val options = parseArgs(args)
      ?: return CliRunResult(
        output = buildString {
          appendLine("Usage: FindUnusedModuleDependencies [--project-root <path>] (--all | --module <name> [--module <name> ...])")
        }.trimEnd(),
        exitCode = 2,
      )

    val project = loadProject(options.projectRoot)
    val report = UnusedModuleDependencyAnalyzer(options.projectRoot).analyze(project, options)
    return CliRunResult(renderReport(report), report.exitCode)
  }

  private fun parseArgs(args: List<String>): CliOptions? {
    var projectRoot = Path.of("").toAbsolutePath().normalize()
    val moduleNames = LinkedHashSet<String>()
    var analyzeAll = false

    var index = 0
    while (index < args.size) {
      when (args[index]) {
        "--project-root" -> {
          val value = args.getOrNull(index + 1) ?: return null
          projectRoot = Path.of(value).toAbsolutePath().normalize()
          index += 2
        }
        "--all" -> {
          analyzeAll = true
          index += 1
        }
        "--module" -> {
          val value = args.getOrNull(index + 1) ?: return null
          moduleNames.add(value)
          index += 2
        }
        else -> return null
      }
    }

    if (analyzeAll == moduleNames.isNotEmpty()) {
      return null
    }

    return CliOptions(
      projectRoot = projectRoot,
      analyzeAll = analyzeAll,
      moduleNames = moduleNames,
    )
  }

  private fun loadProject(projectRoot: Path): JpsProject {
    val mavenRepository = JpsMavenSettings.getMavenRepositoryPath()
    return JpsSerializationManager.getInstance().loadProject(projectRoot.toString(), mapOf("MAVEN_REPOSITORY" to mavenRepository), true)
  }

  private fun renderReport(report: AnalysisReport): String {
    if (report.failures.isEmpty() && report.moduleResults.none { it.removableDependencies.isNotEmpty() }) {
      return "No removable module dependencies found."
    }

    return buildString {
      if (report.failures.isNotEmpty()) {
        appendLine("not analyzed:")
        for (failure in report.failures.sortedBy { it.moduleName }) {
          appendLine("  ${failure.moduleName}: ${failure.reason}")
        }
      }

      val modulesWithFindings = report.moduleResults.filter { it.removableDependencies.isNotEmpty() }
      if (modulesWithFindings.isNotEmpty()) {
        if (isNotEmpty()) {
          appendLine()
        }
        appendLine("removable dependencies:")
        for (moduleResult in modulesWithFindings.sortedBy { it.moduleName }) {
          appendLine(moduleResult.moduleName)
          for (dependency in moduleResult.removableDependencies.sortedWith(compareBy(RemovableDependency::scope, RemovableDependency::dependencyName))) {
            appendLine("  ${dependency.scope.name}: ${dependency.dependencyName}")
          }
        }
      }
    }.trimEnd()
  }
}

internal data class CliRunResult(
  val output: String,
  val exitCode: Int,
)

internal data class CliOptions(
  val projectRoot: Path,
  val analyzeAll: Boolean,
  val moduleNames: Set<String>,
)

internal data class AnalysisReport(
  val moduleResults: List<ModuleAnalysisResult>,
  val failures: List<ModuleAnalysisFailure>,
) {
  val exitCode: Int
    get() = when {
      failures.isNotEmpty() -> 2
      moduleResults.any { it.removableDependencies.isNotEmpty() } -> 1
      else -> 0
    }
}

internal data class ModuleAnalysisResult(
  val moduleName: String,
  val removableDependencies: List<RemovableDependency>,
)

internal data class RemovableDependency(
  val dependencyName: String,
  val scope: JpsJavaDependencyScope,
)

internal data class ModuleAnalysisFailure(
  val moduleName: String,
  val reason: String,
)

internal class UnusedModuleDependencyAnalyzer(projectRoot: Path) {
  private val javaExtensionService = JpsJavaExtensionService.getInstance()
  private val pathMapper = createProjectRootPathMapper(projectRoot)
  private val targetsInfo = BazelTargetsInfo.fromProjectRoot(projectRoot)
  private val jarOwnerModules = buildJarOwnerModules(targetsInfo)
  private val jarOwnerIndexCache = HashMap<Path, JarOwnerIndex>()
  private val exportedClosureCache = HashMap<Triple<String, String, JpsJavaClasspathKind>, Set<String>>()

  fun analyze(project: JpsProject, options: CliOptions): AnalysisReport {
    val modulesByName = project.modules.associateBy { it.name }
    val selectedModuleNames = if (options.analyzeAll) {
      modulesByName.keys.sorted()
    }
    else {
      options.moduleNames.toList().sorted()
    }

    val invalidModules = selectedModuleNames.filter { it !in modulesByName }
    if (invalidModules.isNotEmpty()) {
      return AnalysisReport(
        moduleResults = emptyList(),
        failures = invalidModules.map { ModuleAnalysisFailure(it, "module not found in JPS project") },
      )
    }

    val moduleResults = ArrayList<ModuleAnalysisResult>(selectedModuleNames.size)
    val failures = ArrayList<ModuleAnalysisFailure>()
    for (moduleName in selectedModuleNames) {
      when (val result = analyzeModule(modulesByName.getValue(moduleName))) {
        is ModuleAnalysisOutcome.Analyzed -> moduleResults.add(result.result)
        is ModuleAnalysisOutcome.Skipped -> failures.add(ModuleAnalysisFailure(moduleName, result.reason))
      }
    }
    return AnalysisReport(moduleResults, failures)
  }

  private fun analyzeModule(module: JpsModule): ModuleAnalysisOutcome {
    val dependencies = module.dependenciesList.dependencies.mapNotNull { element ->
      val moduleDependency = element as? JpsModuleDependency ?: return@mapNotNull null
      val dependencyModule = moduleDependency.module ?: return@mapNotNull null
      val extension = javaExtensionService.getDependencyExtension(moduleDependency)
      DirectModuleDependency(
        dependency = dependencyModule,
        scope = extension.scopeOrDefault(),
        exported = extension?.isExported == true,
      )
    }

    val candidates = dependencies.filter { !it.exported && it.scope != JpsJavaDependencyScope.RUNTIME && it.dependency.name != module.name }
    if (candidates.isEmpty()) {
      return ModuleAnalysisOutcome.Analyzed(ModuleAnalysisResult(module.name, emptyList()))
    }

    val productionJars = getProductionJars(module.name)
      ?: return ModuleAnalysisOutcome.Skipped("module is missing from build/bazel-targets.json")
    val testJars = getTestJars(module.name) ?: emptyList()

    val requiresProductionAnalysis = candidates.any { it.scope != JpsJavaDependencyScope.TEST }
    val productionOwnerModules = when {
      !requiresProductionAnalysis -> emptySet()
      productionJars.isEmpty() -> return ModuleAnalysisOutcome.Skipped("no production Bazel output jars declared")
      else -> analyzeTargets(module.name, productionJars)
        ?: return ModuleAnalysisOutcome.Skipped("missing or unreadable production IC data")
    }

    val testOwnerModules = when {
      testJars.isEmpty() -> emptySet()
      else -> analyzeTargets(module.name, testJars)
        ?: return ModuleAnalysisOutcome.Skipped("missing or unreadable test IC data")
    }

    val removableDependencies = candidates.filterNot { dependency ->
      isRequiredForProduction(module, dependency, productionOwnerModules) || isRequiredForTests(module, dependency, testOwnerModules)
    }.map {
      RemovableDependency(it.dependency.name, it.scope)
    }

    return ModuleAnalysisOutcome.Analyzed(
      ModuleAnalysisResult(module.name, removableDependencies),
    )
  }

  private fun isRequiredForProduction(module: JpsModule, dependency: DirectModuleDependency, usedOwnerModules: Set<String>): Boolean {
    if (dependency.scope == JpsJavaDependencyScope.TEST) {
      return false
    }
    return isDependencyRequired(module, dependency, usedOwnerModules, JpsJavaClasspathKind.PRODUCTION_COMPILE)
  }

  private fun isRequiredForTests(module: JpsModule, dependency: DirectModuleDependency, usedOwnerModules: Set<String>): Boolean {
    if (usedOwnerModules.isEmpty()) {
      return false
    }
    return isDependencyRequired(module, dependency, usedOwnerModules, JpsJavaClasspathKind.TEST_COMPILE)
  }

  private fun isDependencyRequired(
    module: JpsModule,
    dependency: DirectModuleDependency,
    usedOwnerModules: Set<String>,
    classpathKind: JpsJavaClasspathKind,
  ): Boolean {
    val dependencyName = dependency.dependency.name
    if (usedOwnerModules.contains(dependencyName)) {
      return true
    }
    return usedOwnerModules.any { usedOwner -> usedOwner in exportedClosure(module, dependency, classpathKind) }
  }

  private fun exportedClosure(module: JpsModule, dependency: DirectModuleDependency, classpathKind: JpsJavaClasspathKind): Set<String> {
    return exportedClosureCache.computeIfAbsent(Triple(module.name, dependency.dependency.name, classpathKind)) {
      val visited = LinkedHashSet<String>()
      val queue = ArrayDeque<JpsModule>()
      queue.add(dependency.dependency)

      while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (element in current.dependenciesList.dependencies) {
          val moduleDependency = element as? JpsModuleDependency ?: continue
          val depModule = moduleDependency.module ?: continue
          val extension = javaExtensionService.getDependencyExtension(moduleDependency)
          if (extension?.isExported != true || !extension.scopeOrDefault().isIncludedIn(classpathKind)) {
            continue
          }
          if (depModule.name == module.name) {
            continue
          }
          if (visited.add(depModule.name)) {
            queue.add(depModule)
          }
        }
      }
      visited
    }
  }

  private fun analyzeTargets(moduleName: String, outputJars: List<Path>): Set<String>? {
    val ownerModules = LinkedHashSet<String>()
    for (outputJar in outputJars) {
      if (!outputJar.isRegularFile()) {
        return null
      }
      val usedModules = analyzeTarget(moduleName, outputJar) ?: return null
      ownerModules.addAll(usedModules)
    }
    return ownerModules
  }

  private fun analyzeTarget(moduleName: String, outputJar: Path): Set<String>? {
    val dataDir = outputJar.resolveSibling(IcDataPaths.truncateExtension(outputJar.name) + IcDataPaths.DATA_DIR_NAME_SUFFIX)
    val graphFile = dataDir.resolve(IcDataPaths.DEP_GRAPH_FILE_NAME)
    val configFile = dataDir.resolve(IcDataPaths.CONFIG_STATE_FILE_NAME)
    if (!graphFile.isRegularFile() || !configFile.isRegularFile()) {
      return null
    }

    val usedOwnerIds = readExternalOwnerIds(graphFile)
    if (usedOwnerIds.isEmpty()) {
      return emptySet()
    }

    val classpathEntries = IcConfigurationStateIO.loadLibraryClasspath(configFile, pathMapper) ?: return null
    val unresolvedOwners = LinkedHashSet(usedOwnerIds)
    val usedModules = LinkedHashSet<String>()
    for (libraryPath in classpathEntries) {
      val ownerIndex = getJarOwnerIndex(libraryPath) ?: return null
      val matchedOwners = unresolvedOwners.filterTo(LinkedHashSet()) { ownerIndex.ownerIds.contains(it) }
      if (matchedOwners.isEmpty()) {
        continue
      }

      val owningModules = jarOwnerModules[libraryPath]
      when {
        owningModules.isNullOrEmpty() -> {
          unresolvedOwners.removeAll(matchedOwners)
        }
        owningModules.size == 1 -> {
          usedModules.add(owningModules.single())
          unresolvedOwners.removeAll(matchedOwners)
        }
        else -> return null
      }

      if (unresolvedOwners.isEmpty()) {
        break
      }
    }

    if (unresolvedOwners.isNotEmpty()) {
      return null
    }

    usedModules.remove(moduleName)
    return usedModules
  }

  private fun readExternalOwnerIds(graphFile: Path): Set<String> {
    return DependencyGraphImpl(IcPersistentMVStoreMapletFactory(graphFile.toString(), 1)).useGraph { graph ->
      val internalIds = graph.registeredNodes.toHashSet()
      val externalOwners = LinkedHashSet<String>()
      for (source in graph.sources) {
        for (node in graph.getNodes(source)) {
          for (usage in node.usages) {
            val owner = usage.elementOwner as? JvmNodeReferenceID ?: continue
            if (owner !in internalIds) {
              externalOwners.add(owner.nodeName)
            }
          }
        }
      }
      externalOwners
    }
  }

  private fun getJarOwnerIndex(jarPath: Path): JarOwnerIndex? {
    val normalizedPath = jarPath.normalize()
    jarOwnerIndexCache[normalizedPath]?.let { return it }
    if (!normalizedPath.isRegularFile()) {
      return null
    }

    return try {
      val ownerIds = LinkedHashSet<String>()
      ZipInputStream(BufferedInputStream(java.nio.file.Files.newInputStream(normalizedPath))).use { stream ->
        while (true) {
          val entry = stream.nextEntry ?: break
          if (!entry.isDirectory && entry.name.endsWith(".class")) {
            val className = entry.name.removeSuffix(".class")
            if (className != "module-info" && !className.endsWith("/package-info")) {
              ownerIds.add(className)
              ownerIds.add(JvmClass.getPackageName(className))
            }
          }
          stream.closeEntry()
        }
      }
      JarOwnerIndex(ownerIds).also { jarOwnerIndexCache[normalizedPath] = it }
    }
    catch (_: Throwable) {
      null
    }
  }

  private fun getProductionJars(moduleName: String): List<Path>? {
    return targetsInfo.getModuleJars(moduleName, withTests = false)?.map(Path::normalize)
  }

  private fun getTestJars(moduleName: String): List<Path>? {
    val productionJars = getProductionJars(moduleName) ?: return null
    val allJars = targetsInfo.getModuleJars(moduleName, withTests = true)?.map(Path::normalize) ?: return null
    return allJars.drop(productionJars.size)
  }

  private fun buildJarOwnerModules(targetsInfo: BazelTargetsInfo): Map<Path, Set<String>> {
    val jarOwners = LinkedHashMap<Path, MutableSet<String>>()
    for (moduleName in targetsInfo.getAllModules().sorted()) {
      val productionJars = targetsInfo.getModuleJars(moduleName, withTests = false).orEmpty()
      val testJars = targetsInfo.getModuleJars(moduleName, withTests = true).orEmpty().drop(productionJars.size)
      for (jar in productionJars + testJars) {
        addJarOwner(jarOwners, jar.normalize(), moduleName)
        addJarOwner(jarOwners, toAbiJarPath(jar).normalize(), moduleName)
      }
    }
    return jarOwners.mapValues { (_, owners) -> owners.toSet() }
  }

  private fun addJarOwner(jarOwners: MutableMap<Path, MutableSet<String>>, jarPath: Path, moduleName: String) {
    jarOwners.computeIfAbsent(jarPath) { LinkedHashSet() }.add(moduleName)
  }

  private fun toAbiJarPath(jarPath: Path): Path {
    val fileName = jarPath.name
    if (fileName.endsWith(IcDataPaths.ABI_JAR_SUFFIX)) {
      return jarPath
    }
    if (!fileName.endsWith(".jar")) {
      return jarPath
    }
    return jarPath.resolveSibling(fileName.removeSuffix(".jar") + IcDataPaths.ABI_JAR_SUFFIX)
  }
}

internal fun createProjectRootPathMapper(projectRoot: Path): PathSourceMapper {
  val normalizedRoot = projectRoot.toAbsolutePath().normalize()
  val separator = normalizedRoot.fileSystem.separator
  return PathSourceMapper(
    { relativePath ->
      normalizedRoot.resolve(Path.of(relativePath)).normalize().toString().replace(separator, "/")
    },
    { absolutePath ->
      normalizedRoot.relativize(Path.of(absolutePath).toAbsolutePath().normalize()).toString().replace(separator, "/")
    },
  )
}

private fun JpsJavaDependencyExtension?.scopeOrDefault(): JpsJavaDependencyScope {
  return this?.scope ?: JpsJavaDependencyScope.COMPILE
}

private sealed interface ModuleAnalysisOutcome {
  data class Analyzed(val result: ModuleAnalysisResult) : ModuleAnalysisOutcome
  data class Skipped(val reason: String) : ModuleAnalysisOutcome
}

private data class DirectModuleDependency(
  val dependency: JpsModule,
  val scope: JpsJavaDependencyScope,
  val exported: Boolean,
)

private data class JarOwnerIndex(
  val ownerIds: Set<String>,
)
