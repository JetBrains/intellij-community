// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "HardCodedStringLiteral")

package org.jetbrains.intellij.build.impl

import com.intellij.diagnostic.telemetry.use
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.MultiMap
import groovy.transform.CompileStatic
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.build.Standalone
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.gant.Log4jFileLoggerFactory
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType
import org.jetbrains.jps.incremental.artifacts.impl.ArtifactSorter
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil
import org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner
import org.jetbrains.jps.incremental.messages.*
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.artifact.elements.JpsModuleOutputPackagingElement
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.beans.Introspector
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

@CompileStatic
internal class JpsCompilationRunner(private val context: CompilationContext) {
  private val compilationData = context.compilationData

  companion object {
    init {
      // Unset 'groovy.target.bytecode' which was possibly set by outside context
      // to get target bytecode version from corresponding java compiler settings
      System.clearProperty(GroovyRtConstants.GROOVY_TARGET_BYTECODE)
      setSystemPropertyIfUndefined(GlobalOptions.COMPILE_PARALLEL_OPTION, "true")
      setSystemPropertyIfUndefined(DependencyResolvingBuilder.RESOLUTION_PARALLELISM_PROPERTY,
                                   Runtime.getRuntime().availableProcessors().toString())
      setSystemPropertyIfUndefined(GlobalOptions.USE_DEFAULT_FILE_LOGGING_OPTION, "false")
      setSystemPropertyIfUndefined(JpsGroovycRunner.GROOVYC_IN_PROCESS, "true")
      setSystemPropertyIfUndefined(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY, "false")

      // https://youtrack.jetbrains.com/issue/IDEA-269280
      System.setProperty("aether.connector.resumeDownloads", "false")
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

  fun buildModules(modules: List<JpsModule>) {
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
    runBuild(moduleSet = names, allModules = false, artifactNames = emptyList(), includeTests = false, resolveProjectDependencies = false)
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

  fun buildModuleTests(module: JpsModule) {
    runBuild(getModuleDependencies(module = module, includeTests = true),
             allModules = false,
             artifactNames = emptyList(),
             includeTests = true,
             resolveProjectDependencies = false)
  }

  fun buildAll() {
    runBuild(moduleSet = emptyList(),
             allModules = true,
             artifactNames = emptyList(),
             includeTests = true,
             resolveProjectDependencies = false)
  }

  fun buildProduction() {
    runBuild(moduleSet = emptyList(),
             allModules = true,
             artifactNames = emptyList(),
             includeTests = false,
             resolveProjectDependencies = false)
  }

  @Deprecated("use {@link #buildArtifacts(java.util.Set, boolean)} instead")
  fun buildArtifacts(artifactNames: Set<String>) {
    buildArtifacts(artifactNames = artifactNames, buildIncludedModules = true)
  }

  fun buildArtifacts(artifactNames: Set<String>, buildIncludedModules: Boolean) {
    val artifacts = getArtifactsWithIncluded(artifactNames)
    val modules = if (buildIncludedModules) getModulesIncludedInArtifacts(artifacts) else emptyList()
    runBuild(moduleSet = modules,
             allModules = false,
             artifactNames = artifacts.map { it.name },
             includeTests = false,
             resolveProjectDependencies = false)
  }

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

  private fun getArtifactsWithIncluded(artifactNames: Set<String>): Set<JpsArtifact> {
    val artifacts = JpsArtifactService.getInstance().getArtifacts(context.project).filter { it.name in artifactNames }
    return ArtifactSorter.addIncludedArtifacts(artifacts)
  }

  private fun setupAdditionalBuildLogging(compilationData: JpsCompilationData) {
    val categoriesWithDebugLevel = compilationData.categoriesWithDebugLevel
    val buildLogFile = compilationData.buildLogFile
    try {
      val factory = Log4jFileLoggerFactory(buildLogFile.toFile(), categoriesWithDebugLevel)
      AntLoggerFactory.fileLoggerFactory = factory
      context.messages.info("Build log (${if (categoriesWithDebugLevel.isEmpty()) "info" else "debug level for $categoriesWithDebugLevel"}) " +
                            "will be written to $buildLogFile")
    }
    catch (t: Throwable) {
      context.messages.warning("Cannot setup additional logging to $buildLogFile.absolutePath: ${t.message}")
    }
  }

  private fun runBuild(moduleSet: Collection<String>,
                       allModules: Boolean,
                       artifactNames: Collection<String>,
                       includeTests: Boolean,
                       resolveProjectDependencies: Boolean) {
    messageHandler = AntMessageHandler(context)
    if (context.options.compilationLogEnabled) {
      setupAdditionalBuildLogging(compilationData)
    }

    Logger.setFactory(AntLoggerFactory::class.java)
    val forceBuild = !context.options.incrementalCompilation
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
      compilationData.projectDependenciesResolved = true
    }
    val artifactsToBuild = artifactNames - compilationData.builtArtifacts
    if (!artifactsToBuild.isEmpty()) {
      val builder = TargetTypeBuildScope.newBuilder().setTypeId(ArtifactBuildTargetType.INSTANCE.typeId).setForceBuild(forceBuild)
      scopes.add(builder.addAllTargetId(artifactsToBuild).build())
      compilationData.builtArtifacts.addAll(artifactsToBuild)
    }
    val compilationStart = System.nanoTime()
    val messageHandler = messageHandler!!
    spanBuilder("compilation")
      .setAttribute("scope", "${if (allModules) "all" else moduleSet.size} modules")
      .setAttribute("includeTests", includeTests)
      .setAttribute("artifactsToBuild", artifactsToBuild.size.toLong())
      .setAttribute("resolveProjectDependencies", resolveProjectDependencies)
      .setAttribute("modules", moduleSet.joinToString(separator = ", "))
      .setAttribute("incremental", context.options.incrementalCompilation)
      .setAttribute("includeTests", includeTests)
      .setAttribute("cacheDir", compilationData.dataStorageRoot.toString())
      .useWithScope {
        Standalone.runBuild({ context.projectModel }, compilationData.dataStorageRoot.toFile(), messageHandler, scopes, false)
      }
    if (!messageHandler.errorMessagesByCompiler.isEmpty) {
      for ((key, value) in messageHandler.errorMessagesByCompiler.entrySet()) {
        @Suppress("UNCHECKED_CAST")
        context.messages.compilationErrors(key, value as List<String>)
      }
      throw RuntimeException("Compilation failed")
    }
    else if (!compilationData.statisticsReported) {
      messageHandler.printPerModuleCompilationStatistics(compilationStart)
      context.messages.reportStatisticValue("Compilation time, ms",
                                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - compilationStart).toString())
      compilationData.statisticsReported = true
    }
  }
}

internal class AntLoggerFactory : Logger.Factory {
  companion object {
    var fileLoggerFactory: Logger.Factory? = null
  }

  override fun getLoggerInstance(category: String): Logger = BackedLogger(category, fileLoggerFactory?.getLoggerInstance(category))
}

private class AntMessageHandler(private val context: CompilationContext) : MessageHandler {
  val errorMessagesByCompiler = MultiMap.createConcurrent<String, String>()
  private val compilationStartTimeForTarget = ConcurrentHashMap<String, Long>()
  private val compilationFinishTimeForTarget = ConcurrentHashMap<String, Long>()
  private var progress = -1.0.toFloat()
  override fun processMessage(message: BuildMessage) {
    val text = message.messageText
    when (message.kind) {
      BuildMessage.Kind.ERROR -> {
        val compilerName: String
        val messageText: String
        if (message is CompilerMessage) {
          compilerName = message.compilerName
          val sourcePath = message.sourcePath
          messageText = if (sourcePath != null) {
            """
 $sourcePath${if (message.line != -1L) ":" + message.line else ""}:
 $text
 """.trimIndent()
          }
          else {
            text
          }
        }
        else {
          compilerName = ""
          messageText = text
        }
        errorMessagesByCompiler.putValue(compilerName, messageText)
      }
      BuildMessage.Kind.WARNING -> context.messages.warning(text)
      BuildMessage.Kind.INFO -> if (message is BuilderStatisticsMessage) {
        val buildKind = if (context.options.incrementalCompilation) " (incremental)" else ""
        context.messages.reportStatisticValue("Compilation time '${message.builderName}'$buildKind, ms", message.elapsedTimeMs.toString())
        val sources = message.numberOfProcessedSources
        context.messages.reportStatisticValue("Processed files by '${message.builderName}'$buildKind", sources.toString())
        if (!context.options.incrementalCompilation && sources > 0) {
          context.messages.reportStatisticValue("Compilation time per file for '${message.builderName}', ms",
                                                String.format(Locale.US, "%.2f", message.elapsedTimeMs.toDouble() / sources))
        }
      }
      else if (!text.isEmpty()) {
        context.messages.info(text)
      }
      BuildMessage.Kind.PROGRESS -> if (message is ProgressMessage) {
        progress = message.done
        message.currentTargets?.let {
          reportProgress(it.targets, message.messageText)
        }
      }
      else if (message is BuildingTargetProgressMessage) {
        val targets = message.targets
        val target = targets.first()
        val targetId = "$target.id${if (targets.size > 1) " and ${targets.size} more" else ""} ($target.targetType.typeId)"
        if (message.eventType == BuildingTargetProgressMessage.Event.STARTED) {
          reportProgress(targets, "")
          compilationStartTimeForTarget.put(targetId, System.nanoTime())
        }
        else {
          compilationFinishTimeForTarget.put(targetId, System.nanoTime())
        }
      }
      else -> {
        // ignore
      }
    }
  }

  fun printPerModuleCompilationStatistics(compilationStart: Long) {
    if (compilationStartTimeForTarget.isEmpty()) {
      return
    }

    Files.newBufferedWriter(context.paths.buildOutputDir.resolve("log/compilation-time.csv")).use { out ->
      compilationFinishTimeForTarget.forEach(BiConsumer { k, v ->
        val startTime = compilationStartTimeForTarget.get(k)!! - compilationStart
        val finishTime = v - compilationStart
        out.write("$k,$startTime,$finishTime\n")
      })
    }
    val buildMessages = context.messages
    buildMessages.info("Compilation time per target:")
    val compilationTimeForTarget = compilationFinishTimeForTarget.entries.map { it.key to (it.value - compilationStartTimeForTarget.get(it.key)!!) }

    buildMessages.info(" average: ${String.format("%.2f", ((compilationTimeForTarget.sumOf { it.second }.toDouble()) / compilationTimeForTarget.size) / 1000000)}ms")
    val topTargets = compilationTimeForTarget.sortedBy { it.second }.asReversed().take(10)
    buildMessages.info(" top ${topTargets.size} targets by compilation time:")
    for (entry in topTargets) {
      buildMessages.info("  ${entry.first}: ${TimeUnit.NANOSECONDS.toMillis(entry.second)}ms")
    }
  }

  private fun reportProgress(targets: Collection<BuildTarget<*>>, targetSpecificMessage: String) {
    val targetsString = targets.joinToString(separator = ", ") { Introspector.decapitalize(it.presentableName) }
    val progressText = if (progress >= 0) " (${(100 * progress).toInt()}%)" else ""
    val targetSpecificText = if (targetSpecificMessage.isEmpty()) "" else ", $targetSpecificMessage"
    context.messages.progress("Compiling$progressText: $targetsString$targetSpecificText")
  }
}

private class BackedLogger constructor(category: String?, private val fileLogger: Logger?) : DefaultLogger(category) {
  override fun error(@Nls message: @NonNls String?, t: Throwable?, vararg details: String) {
    if (t == null) {
      messageHandler!!.processMessage(CompilerMessage(COMPILER_NAME, BuildMessage.Kind.ERROR, message))
    }
    else {
      messageHandler!!.processMessage(CompilerMessage.createInternalBuilderError(COMPILER_NAME, t))
    }
    fileLogger?.error(message, t, *details)
  }

  override fun warn(message: @NonNls String?, t: Throwable?) {
    messageHandler!!.processMessage(CompilerMessage(COMPILER_NAME, BuildMessage.Kind.WARNING, message))
    fileLogger?.warn(message, t)
  }

  override fun info(message: String) {
    fileLogger?.info(message)
  }

  override fun info(message: String, t: Throwable?) {
    fileLogger?.info(message, t)
  }

  override fun isDebugEnabled(): Boolean {
    return fileLogger != null && fileLogger.isDebugEnabled
  }

  override fun debug(message: String) {
    fileLogger?.debug(message)
  }

  override fun debug(t: Throwable?) {
    fileLogger?.debug(t)
  }

  override fun debug(message: String, t: Throwable?) {
    fileLogger?.debug(message, t)
  }

  override fun isTraceEnabled(): Boolean {
    return fileLogger != null && fileLogger.isTraceEnabled
  }

  override fun trace(message: String) {
    fileLogger?.trace(message)
  }

  override fun trace(t: Throwable?) {
    fileLogger?.trace(t)
  }
}

private var messageHandler: AntMessageHandler? = null
@Nls
private const val COMPILER_NAME = "build runner"

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