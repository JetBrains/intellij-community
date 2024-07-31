// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants
import org.jetbrains.intellij.build.telemetry.use
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.logging.jps.withJpsLogging
import org.jetbrains.intellij.build.telemetry.useWithScope
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.jps.build.Standalone
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType
import org.jetbrains.jps.incremental.artifacts.impl.ArtifactSorter
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil
import org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.artifact.elements.JpsModuleOutputPackagingElement
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal class JpsCompilationRunner(private val context: CompilationContext) {
  private val compilationData: JpsCompilationData
    get() = context.compilationData

  companion object {
    init {
      // Unset 'groovy.target.bytecode' which was possibly set by outside context
      // to get target bytecode version from corresponding java compiler settings
      System.clearProperty("groovy.target.bytecode")
      setSystemPropertyIfUndefined(GlobalOptions.COMPILE_PARALLEL_OPTION, "true")
      setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_PARALLELISM_PROPERTY,
                                   Runtime.getRuntime().availableProcessors().toString())
      setSystemPropertyIfUndefined(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "false")
      setSystemPropertyIfUndefined(JpsGroovycRunner.GROOVYC_IN_PROCESS, "true")
      setSystemPropertyIfUndefined("groovyc.asm.resolving.only", "false")

      // https://youtrack.jetbrains.com/issue/IDEA-269280
      System.setProperty("aether.connector.resumeDownloads", "false")

      // Produces Kotlin compiler incremental cache which can be reused later.
      // Unrelated to force rebuild controlled by JPS.
      setSystemPropertyIfUndefined("kotlin.incremental.compilation", "true")

      // Required for JPS Portable Caches
      setSystemPropertyIfUndefined(JavaBackwardReferenceIndexWriter.PROP_KEY, "true")
    }
  }

  init {
    setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_RETRY_ENABLED_PROPERTY,
                                 (context.options.resolveDependenciesMaxAttempts > 1).toString())
    setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_RETRY_DELAY_MS_PROPERTY,
                                 context.options.resolveDependenciesDelayMs.toString())
    setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY,
                                 context.options.resolveDependenciesMaxAttempts.toString())
    setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY,
                                 TimeUnit.MINUTES.toMillis(15).toString())
  }

  fun buildModules(modules: List<JpsModule>, canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    val names = LinkedHashSet<String>()
    spanBuilder("collect dependencies")
      .setAttribute(AttributeKey.longKey("moduleCount"), modules.size.toLong())
      .use { span ->
        val requiredDependencies = ArrayList<String>()
        for (module in modules) {
          requiredDependencies.clear()
          for (dependency in getModuleDependencies(module = module, includeTests = false)) {
            if (names.add(dependency)) {
              requiredDependencies.add(dependency)
            }
          }

          if (!requiredDependencies.isEmpty()) {
            span.addEvent("required dependencies", Attributes.of(
              AttributeKey.stringKey("module"), module.name,
              AttributeKey.stringArrayKey("module"), java.util.List.copyOf(requiredDependencies)
            ))
          }
        }
      }
    runBuild(
      moduleSet = names, allModules = false, artifactNames = emptyList(),
      includeTests = false, resolveProjectDependencies = false,
      generateRuntimeModuleRepository = true,
      canceledStatus = canceledStatus
    )
  }

  fun buildModulesWithoutDependencies(modules: Collection<JpsModule>, includeTests: Boolean) {
    runBuild(moduleSet = modules.map { it.name },
             allModules = false,
             artifactNames = emptyList(),
             includeTests = includeTests,
             resolveProjectDependencies = false)
  }

  fun resolveProjectDependencies() {
    runBuild(moduleSet = emptyList(),
             allModules = false,
             artifactNames = emptyList(),
             includeTests = false,
             resolveProjectDependencies = true)
  }
  
  fun generateRuntimeModuleRepository() {
    runBuild(moduleSet = emptyList(),
             allModules = false,
             artifactNames = emptyList(),
             includeTests = false,
             resolveProjectDependencies = false,
             generateRuntimeModuleRepository = true)
  }

  fun buildModuleTests(module: JpsModule, canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    runBuild(getModuleDependencies(module = module, includeTests = true),
             allModules = false,
             artifactNames = emptyList(),
             includeTests = true,
             resolveProjectDependencies = false,
             canceledStatus = canceledStatus)
  }

  fun buildAll(canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    runBuild(moduleSet = emptyList(),
             allModules = true,
             artifactNames = emptyList(),
             includeTests = true,
             resolveProjectDependencies = false,
             generateRuntimeModuleRepository = true,
             canceledStatus = canceledStatus)
  }

  fun buildProduction(canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    runBuild(moduleSet = emptyList(),
             allModules = true,
             artifactNames = emptyList(),
             includeTests = false,
             resolveProjectDependencies = false,
             generateRuntimeModuleRepository = true,
             canceledStatus = canceledStatus)
  }

  suspend fun buildArtifacts(artifactNames: Set<String>, buildIncludedModules: Boolean) {
    val artifacts = getArtifactsWithIncluded(artifactNames)
    val missing = artifactNames.filter { name ->
      artifacts.none { it.name == name }
    }
    check(missing.isEmpty()) {
      "Artifacts aren't configured in the project: " + missing.joinToString()
    }
    artifacts.forEach {
      if (context.compilationData.builtArtifacts.contains(it.name) &&
          it.outputFilePath?.let(Path::of)?.let(Files::exists) != true) {
        context.messages.warning("${it.name} is expected to be already built at ${it.outputFilePath} but it's missing")
        context.compilationData.builtArtifacts.remove(it.name)
      }
    }
    val includedModules = getModulesIncludedInArtifacts(artifacts)
    val modules = if (buildIncludedModules) {
      includedModules
    }
    else {
      includedModules.filter {
        val module = context.findRequiredModule(it)
        val outputDir = context.getModuleOutputDir(module)
        if (Files.isDirectory(outputDir) && Files.newDirectoryStream(outputDir).use { stream -> stream.any() }) {
          false
        }
        else {
          /**
           * See [compileMissingArtifactsModules]
           */
          Span.current().addEvent("compilation output of module $it is missing: $outputDir")
          true
        }
      }
    }
    runBuild(moduleSet = modules,
             allModules = false,
             artifactNames = artifacts.map { it.name },
             includeTests = false,
             resolveProjectDependencies = false)
    val failedToBeBuilt = artifacts.filter {
      if (it.outputFilePath?.let(Path::of)?.let(Files::exists) == true) {
        Span.current().addEvent("${it.name} was successfully built at ${it.outputFilePath}")
        false
      }
      else {
        Span.current().addEvent("${it.name} is expected to be built at ${it.outputFilePath}")
        true
      }
    }
    if (failedToBeBuilt.isNotEmpty()) {
      compileMissingArtifactsModules(failedToBeBuilt)
    }
  }

  // FIXME: workaround for sporadically missing build artifacts, to be investigated
  private suspend fun compileMissingArtifactsModules(artifacts: Collection<JpsArtifact>) {
    val modules = getModulesIncludedInArtifacts(artifacts)
    require(modules.isNotEmpty()) {
      "No modules found for artifacts ${artifacts.map { it.name }}"
    }
    for (artifact in artifacts) {
      context.compilationData.builtArtifacts.remove(artifact.name)
    }
    spanBuilder("Compiling modules for missing artifacts: ${modules.joinToString()}").useWithScope {
      runBuild(moduleSet = modules,
               allModules = false,
               artifactNames = artifacts.map { it.name },
               includeTests = false,
               resolveProjectDependencies = false)
    }
    for (artifact in artifacts) {
      if (artifact.outputFilePath?.let(Path::of)?.let(Files::exists) == false) {
        context.messages.error("${artifact.name} is expected to be built at ${artifact.outputFilePath}")
      }
    }
  }

  fun getModulesIncludedInArtifacts(artifactNames: Collection<String>): Collection<String> =
    getModulesIncludedInArtifacts(getArtifactsWithIncluded(artifactNames))

  private fun getModulesIncludedInArtifacts(artifacts: Collection<JpsArtifact>): Set<String> {
    val modulesSet: MutableSet<String> = LinkedHashSet()
    for (artifact in artifacts) {
      JpsArtifactUtil.processPackagingElements(artifact.rootElement) { element ->
        if (element is JpsModuleOutputPackagingElement) {
          modulesSet.addAll(getModuleDependencies(context.findRequiredModule(element.moduleReference.moduleName), false))
        }
        true
      }
    }
    return modulesSet
  }

  private fun getArtifactsWithIncluded(artifactNames: Collection<String>): Set<JpsArtifact> {
    val artifacts = JpsArtifactService.getInstance().getArtifacts(context.project).filter { it.name in artifactNames }
    return ArtifactSorter.addIncludedArtifacts(artifacts)
  }

  private fun runBuild(moduleSet: Collection<String>,
                       allModules: Boolean,
                       artifactNames: Collection<String>,
                       includeTests: Boolean,
                       resolveProjectDependencies: Boolean,
                       generateRuntimeModuleRepository: Boolean = false,
                       canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    synchronized(context.paths.projectHome.toString().intern()) {
      withJpsLogging(context) { messageHandler ->
        val forceBuild = !context.options.incrementalCompilation || !context.compilationData.isIncrementalCompilationDataAvailable()
        val scopes = ArrayList<TargetTypeBuildScope>()
        for (type in JavaModuleBuildTargetType.ALL_TYPES) {
          if (includeTests || !type.isTests) {
            val namesToCompile = if (allModules) context.project.modules.mapTo(mutableListOf()) { it.name } else moduleSet.toMutableList()
            if (type.isTests) {
              namesToCompile.removeAll(compilationData.compiledModuleTests)
              compilationData.compiledModuleTests.addAll(namesToCompile)
            }
            else {
              namesToCompile.removeAll(compilationData.compiledModules)
              compilationData.compiledModules.addAll(namesToCompile)
            }
            if (namesToCompile.isEmpty()) {
              continue
            }
  
            val builder = TargetTypeBuildScope.newBuilder().setTypeId(type.typeId).setForceBuild(forceBuild)
            if (allModules) {
              scopes.add(builder.setAllTargets(true).build())
            }
            else {
              scopes.add(builder.addAllTargetId(namesToCompile).build())
            }
          }
        }
        if (resolveProjectDependencies && !compilationData.projectDependenciesResolved) {
          scopes.add(TargetTypeBuildScope.newBuilder().setTypeId("project-dependencies-resolving")
                       .setForceBuild(false).setAllTargets(true).build())
        }
        if (generateRuntimeModuleRepository && !compilationData.runtimeModuleRepositoryGenerated) {
          scopes.add(TargetTypeBuildScope.newBuilder().setTypeId(RuntimeModuleRepositoryBuildConstants.TARGET_TYPE_ID)
                       .setForceBuild(false).setAllTargets(true).build())
        }
        val artifactsToBuild = artifactNames - compilationData.builtArtifacts
        if (!artifactsToBuild.isEmpty()) {
          val builder = TargetTypeBuildScope.newBuilder().setTypeId(ArtifactBuildTargetType.INSTANCE.typeId).setForceBuild(forceBuild)
          scopes.add(builder.addAllTargetId(artifactsToBuild).build())
        }
        val compilationStart = System.nanoTime()
        spanBuilder("compilation")
          .setAttribute("scope", "${if (allModules) "all" else moduleSet.size} modules")
          .setAttribute("includeTests", includeTests)
          .setAttribute("artifactsToBuild", artifactsToBuild.size.toLong())
          .setAttribute("resolveProjectDependencies", resolveProjectDependencies)
          .setAttribute("generateRuntimeModuleRepository", generateRuntimeModuleRepository)
          .setAttribute("modules", moduleSet.joinToString(separator = ", "))
          .setAttribute("incremental", context.options.incrementalCompilation)
          .setAttribute("cacheDir", compilationData.dataStorageRoot.toString())
          .use {
            messageHandler.span = it
            Standalone.runBuild(
              { context.projectModel }, compilationData.dataStorageRoot.toFile(),
              mapOf(GlobalOptions.BUILD_DATE_IN_SECONDS to "${context.options.buildDateInSeconds}"),
              messageHandler, scopes, false, canceledStatus
            )
          }
        if (!messageHandler.errorMessagesByCompiler.isEmpty) {
          for ((key, value) in messageHandler.errorMessagesByCompiler.entrySet()) {
            @Suppress("UNCHECKED_CAST")
            context.messages.compilationErrors(key, value as List<String>)
          }
          throw RuntimeException("Compilation failed")
        }
        else if (!compilationData.statisticsReported && context.options.compilationLogEnabled) {
          messageHandler.printPerModuleCompilationStatistics(compilationStart)
          context.messages.reportStatisticValue("Compilation time, ms",
                                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - compilationStart).toString())
          compilationData.statisticsReported = true
        }
        if (!artifactsToBuild.isEmpty()) {
          compilationData.builtArtifacts.addAll(artifactsToBuild)
        }
        if (resolveProjectDependencies) {
          compilationData.projectDependenciesResolved = true
        }
        if (generateRuntimeModuleRepository) {
          compilationData.runtimeModuleRepositoryGenerated = true
        }
      }
    }
  }
}

private fun setSystemPropertyIfUndefined(name: String, value: String) {
  if (System.getProperty(name) == null) {
    System.setProperty(name, value)
  }
}

private fun getModuleDependencies(module: JpsModule, includeTests: Boolean): Set<String> {
  var enumerator = JpsJavaExtensionService.dependencies(module).recursively()
  if (!includeTests) {
    enumerator = enumerator.productionOnly()
  }
  return enumerator.modules.mapTo(HashSet()) { it.name }
}
