// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.bazel.runfiles.BazelRunfiles
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlinx.coroutines.Dispatchers
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
import org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder
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
    throw NotImplementedError("JPS compilation was broken on purpose, for details see MRI-3677")
  }

  suspend fun resolveProjectDependencies() {
    runBuild()
  }

  suspend fun buildModuleTests(module: JpsModule, canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    throw NotImplementedError("JPS compilation was broken on purpose, for details see MRI-3677")
  }

  suspend fun buildAll(canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    throw NotImplementedError("JPS compilation was broken on purpose, for details see MRI-3677")
  }

  suspend fun buildProduction(canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    throw NotImplementedError("JPS compilation was broken on purpose, for details see MRI-3677")
  }

  private suspend fun runBuild() = context.withCompilationLock {
    val mavenLibrariesDownloading = context.options.mavenLibrariesDownloadLocation != null

    require(!BazelRunfiles.isRunningFromBazel || mavenLibrariesDownloading) {
      "Running JPS compiler is not supported when running from Bazel."
    }

    val compilationData = context.compilationData

    val scopes = ArrayList<TargetTypeBuildScope>()
    if (!compilationData.projectDependenciesResolved) {
      scopes.add(
        TargetTypeBuildScope.newBuilder()
          .setTypeId("project-dependencies-resolving")
          .setForceBuild(false)
          .setAllTargets(true)
          .build()
      )
    }

    val compilationStart = System.nanoTime()
    spanBuilder("compilation")
      .setAttribute("scope", "0 modules")
      .setAttribute("includeTests", false)
      .setAttribute("artifactsToBuild", 0L)
      .setAttribute("resolveProjectDependencies", true)
      .setAttribute("modules", "")
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
            CanceledStatus.NULL,
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

    compilationData.projectDependenciesResolved = true
  }
}

private fun setSystemPropertyIfUndefined(name: String, value: String) {
  if (System.getProperty(name) == null) {
    System.setProperty(name, value)
  }
}
