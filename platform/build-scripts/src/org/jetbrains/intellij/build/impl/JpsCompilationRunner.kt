// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlinx.coroutines.Dispatchers
import org.jetbrains.intellij.bazelEnvironment.BazelRunfiles
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.logging.jps.withJpsLogging
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.jps.build.Standalone
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType
import org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.util.concurrent.TimeUnit

internal class JpsCompilationRunner(private val context: CompilationContext) {
  companion object {
    init {
      setSystemPropertyIfUndefined(GlobalOptions.COMPILE_PARALLEL_OPTION, "true")
      val availableProcessors = Runtime.getRuntime().availableProcessors().toString()
      setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_PARALLELISM_PROPERTY, availableProcessors)
      if (!BuildOptions().isInDevelopmentMode) {
        setSystemPropertyIfUndefined(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, availableProcessors)
      }
      setSystemPropertyIfUndefined(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "false")

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
    setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_RETRY_ENABLED_PROPERTY, (context.options.resolveDependenciesMaxAttempts > 1).toString())
    setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_RETRY_DELAY_MS_PROPERTY, context.options.resolveDependenciesDelayMs.toString())
    setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY, context.options.resolveDependenciesMaxAttempts.toString())
    setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY, TimeUnit.MINUTES.toMillis(15).toString())
  }

  suspend fun buildModules(modules: List<JpsModule>, canceledStatus: CanceledStatus = CanceledStatus.NULL) {
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
      moduleSet = names,
      allModules = false,
      artifactNames = emptyList(),
      includeTests = false,
      canceledStatus = canceledStatus,
    )
  }

  suspend fun resolveProjectDependencies() {
    runBuild(moduleSet = emptyList(), allModules = false, artifactNames = emptyList(), resolveProjectDependencies = true)
  }

  suspend fun buildModuleTests(module: JpsModule, canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    runBuild(
      moduleSet = getModuleDependencies(module = module, includeTests = true).distinct().toList(),
      allModules = false,
      artifactNames = emptyList(),
      includeTests = true,
      canceledStatus = canceledStatus,
    )
  }

  suspend fun buildAll(canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    runBuild(moduleSet = emptyList(),
             allModules = true,
             artifactNames = emptyList(),
             includeTests = true,
             canceledStatus = canceledStatus)
  }

  suspend fun buildProduction(canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    runBuild(
      moduleSet = emptyList(),
      allModules = true,
      artifactNames = emptyList(),
      canceledStatus = canceledStatus,
    )
  }

  private suspend fun runBuild(
    moduleSet: Collection<String>,
    allModules: Boolean,
    artifactNames: Collection<String>,
    includeTests: Boolean = false,
    resolveProjectDependencies: Boolean = false,
    canceledStatus: CanceledStatus = CanceledStatus.NULL,
  ) = context.withCompilationLock {
    require(!BazelRunfiles.isRunningFromBazel) {
      "Running JPS compiler is not supported when running from Bazel."
    }

    val compilationData = context.compilationData

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
      scopes.add(
        TargetTypeBuildScope.newBuilder()
          .setTypeId("project-dependencies-resolving")
          .setForceBuild(false)
          .setAllTargets(true)
          .build()
      )
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
      .setAttribute("modules", moduleSet.joinToString(separator = ", "))
      .setAttribute("incremental", context.options.incrementalCompilation)
      .setAttribute("cacheDir", compilationData.dataStorageRoot.toString())
      .use(Dispatchers.IO) { span ->
        withJpsLogging(context, span) { messageHandler ->
          Standalone.runBuild(
            { context.projectModel },
            compilationData.dataStorageRoot,
            mapOf(GlobalOptions.BUILD_DATE_IN_SECONDS to "${context.options.buildDateInSeconds}"),
            messageHandler,
            scopes,
            false,
            canceledStatus,
          )

          if (!messageHandler.errorMessagesByCompiler.isEmpty()) {
            for ((key, value) in messageHandler.errorMessagesByCompiler) {
              span.addEvent(
                "compilation error",
                Attributes.of(
                  AttributeKey.stringKey("compiler"), key,
                  AttributeKey.stringArrayKey("errors"), value,
                )
              )

              context.messages.compilationErrors(key, value)
            }
            span.recordException(RuntimeException("Compilation failed"))
            TraceManager.scheduleExportPendingSpans()
            throw RuntimeException("Compilation failed")
          }
          else if (!compilationData.statisticsReported && context.options.compilationLogEnabled) {
            messageHandler.printPerModuleCompilationStatistics(compilationStart)
            context.messages.reportStatisticValue(
              "Compilation time, ms",
              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - compilationStart).toString(),
            )
            compilationData.statisticsReported = true
          }
        }
      }

    if (!artifactsToBuild.isEmpty()) {
      compilationData.builtArtifacts.addAll(artifactsToBuild)
    }
    if (resolveProjectDependencies) {
      compilationData.projectDependenciesResolved = true
    }
  }
}

private fun setSystemPropertyIfUndefined(name: String, value: String) {
  if (System.getProperty(name) == null) {
    System.setProperty(name, value)
  }
}

private fun getModuleDependencies(module: JpsModule, includeTests: Boolean): Sequence<String> {
  var enumerator = JpsJavaExtensionService.dependencies(module).recursively()
  if (!includeTests) {
    enumerator = enumerator.productionOnly()
  }
  return enumerator.modules.asSequence().map { it.name }
}
