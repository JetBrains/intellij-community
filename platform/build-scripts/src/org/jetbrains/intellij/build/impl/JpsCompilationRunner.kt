// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "HardCodedStringLiteral")

package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.util.containers.MultiMap
import com.jetbrains.plugin.structure.base.utils.createParentDirs
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.Nls
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
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
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

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
  
  fun generateRuntimeModuleRepository() {
    runBuild(moduleSet = emptyList(),
             allModules = false,
             artifactNames = emptyList(),
             includeTests = false,
             resolveProjectDependencies = false,
             generateRuntimeModuleRepository = true)
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

  @Deprecated("", ReplaceWith("buildArtifacts(artifactNames = artifactNames, buildIncludedModules = true)"))
  fun buildArtifacts(artifactNames: Set<String>) {
    buildArtifacts(artifactNames = artifactNames, buildIncludedModules = true)
  }

  fun buildArtifacts(artifactNames: Set<String>, buildIncludedModules: Boolean) {
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
    val modules = if (buildIncludedModules) includedModules
    else {
      includedModules.filter {
        val module = context.findRequiredModule(it)
        val outputDir = context.getModuleOutputDir(module)
        if (outputDir.exists() &&
            outputDir.isDirectory() &&
            outputDir.listDirectoryEntries().isNotEmpty()) {
          false
        }
        else {
          /**
           * See [compileMissingArtifactsModules]
           */
          Span.current().addEvent("Compilation output of module $it is missing: $outputDir")
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
  private fun compileMissingArtifactsModules(artifacts: Collection<JpsArtifact>) {
    val modules = getModulesIncludedInArtifacts(artifacts)
    require(modules.isNotEmpty()) {
      "No modules found for artifacts ${artifacts.map { it.name }}"
    }
    artifacts.forEach {
      context.compilationData.builtArtifacts.remove(it.name)
    }
    context.messages.block("Compiling modules for missing artifacts: ${modules.joinToString()}") {
      runBuild(moduleSet = modules,
               allModules = false,
               artifactNames = artifacts.map { it.name },
               includeTests = false,
               resolveProjectDependencies = false)
    }
    artifacts.forEach {
      if (it.outputFilePath?.let(Path::of)?.let(Files::exists) == false) {
        context.messages.error("${it.name} is expected to be built at ${it.outputFilePath}")
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

  private fun setupAdditionalBuildLogging(compilationData: JpsCompilationData) {
    val categoriesWithDebugLevel = compilationData.categoriesWithDebugLevel
    val buildLogFile = compilationData.buildLogFile
    try {
      val factory = Log4jFileLoggerFactory(buildLogFile.toFile(), categoriesWithDebugLevel)
      JpsLoggerFactory.fileLoggerFactory = factory
      context.messages.info(
        "Build log (${if (categoriesWithDebugLevel.isEmpty()) "info" else "debug level for $categoriesWithDebugLevel"}) " +
        "will be written to $buildLogFile")
    }
    catch (t: Throwable) {
      context.messages.warning("Cannot setup additional logging to $buildLogFile: ${t.message}")
    }
  }

  private fun runBuild(moduleSet: Collection<String>,
                       allModules: Boolean,
                       artifactNames: Collection<String>,
                       includeTests: Boolean,
                       resolveProjectDependencies: Boolean,
                       generateRuntimeModuleRepository: Boolean = false) {
    synchronized(context.paths.projectHome.toString().intern()) {
      messageHandler = JpsMessageHandler(context)
      if (context.options.compilationLogEnabled) {
        setupAdditionalBuildLogging(compilationData)
      }

      val oldLoggerFactory = Logger.getFactory()
      Logger.setFactory(JpsLoggerFactory::class.java)
      try {
        val forceBuild = !context.options.incrementalCompilation ||
                         !context.compilationData.dataStorageRoot.exists() ||
                         !context.compilationData.dataStorageRoot.isDirectory() ||
                         Files.newDirectoryStream(context.compilationData.dataStorageRoot).use { it.count() } == 0
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
          .setAttribute("includeTests", includeTests)
          .setAttribute("cacheDir", compilationData.dataStorageRoot.toString())
          .useWithScope {
            Standalone.runBuild(
              { context.projectModel }, compilationData.dataStorageRoot.toFile(),
              mapOf(GlobalOptions.BUILD_DATE_IN_SECONDS to "${context.options.buildDateInSeconds}"),
              messageHandler, scopes, false
            )
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
      finally {
        Logger.setFactory(oldLoggerFactory)
      }
    }
  }
}

private class JpsLoggerFactory : Logger.Factory {
  companion object {
    var fileLoggerFactory: Logger.Factory? = null
  }

  override fun getLoggerInstance(category: String): Logger = BackedLogger(category, fileLoggerFactory?.getLoggerInstance(category))
}

private class JpsMessageHandler(private val context: CompilationContext) : MessageHandler {
  val errorMessagesByCompiler = MultiMap.createConcurrent<String, String>()
  val compilationStartTimeForTarget = ConcurrentHashMap<String, Long>()
  val compilationFinishTimeForTarget = ConcurrentHashMap<String, Long>()
  var progress = (-1.0).toFloat()
  override fun processMessage(message: BuildMessage) {
    val text = message.messageText
    when (message.kind) {
      BuildMessage.Kind.ERROR, BuildMessage.Kind.INTERNAL_BUILDER_ERROR -> {
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
      BuildMessage.Kind.INFO, BuildMessage.Kind.JPS_INFO -> if (message is BuilderStatisticsMessage) {
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
        val targetId = "${target.id}${if (targets.size > 1) " and ${targets.size} more" else ""} (${target.targetType.typeId})"
        if (message.eventType == BuildingTargetProgressMessage.Event.STARTED) {
          reportProgress(targets, "")
          compilationStartTimeForTarget.put(targetId, System.nanoTime())
        }
        else {
          compilationFinishTimeForTarget.put(targetId, System.nanoTime())
        }
      }
      BuildMessage.Kind.OTHER, null -> context.messages.warning(text)
    }
  }

  fun printPerModuleCompilationStatistics(compilationStart: Long) {
    if (compilationStartTimeForTarget.isEmpty()) {
      return
    }

    val csvPath = context.paths.logDir.resolve("compilation-time.csv").also { it.createParentDirs() }
    Files.newBufferedWriter(csvPath).use { out ->
      compilationFinishTimeForTarget.forEach(BiConsumer { k, v ->
        val startTime = compilationStartTimeForTarget.getValue(k) - compilationStart
        val finishTime = v - compilationStart
        out.write("$k,$startTime,$finishTime\n")
      })
    }
    val buildMessages = context.messages
    buildMessages.info("Compilation time per target:")
    val compilationTimeForTarget = compilationFinishTimeForTarget.entries.map {
      it.key to (it.value - compilationStartTimeForTarget.getValue(it.key))
    }

    buildMessages.info(" average: ${
      String.format("%.2f", ((compilationTimeForTarget.sumOf { it.second }.toDouble()) / compilationTimeForTarget.size) / 1000000)
    }ms")
    val topTargets = compilationTimeForTarget.sortedBy { it.second }.asReversed().take(10)
    buildMessages.info(" top ${topTargets.size} targets by compilation time:")
    for (entry in topTargets) {
      buildMessages.info("  ${entry.first}: ${TimeUnit.NANOSECONDS.toMillis(entry.second)}ms")
    }
  }

  fun reportProgress(targets: Collection<BuildTarget<*>>, targetSpecificMessage: String) {
    val targetsString = targets.joinToString(separator = ", ") { Introspector.decapitalize(it.presentableName) }
    val progressText = if (progress >= 0) " (${(100 * progress).toInt()}%)" else ""
    val targetSpecificText = if (targetSpecificMessage.isEmpty()) "" else ", $targetSpecificMessage"
    context.messages.progress("Compiling$progressText: $targetsString$targetSpecificText")
  }
}

private class BackedLogger(category: String?, private val fileLogger: Logger?) : DefaultLogger(category) {
  override fun error(@Nls message: String?, t: Throwable?, vararg details: String) {
    if (t == null) {
      messageHandler.processMessage(CompilerMessage(COMPILER_NAME, BuildMessage.Kind.ERROR, message))
    }
    else {
      messageHandler.processMessage(CompilerMessage.createInternalBuilderError(COMPILER_NAME, t))
    }
    fileLogger?.error(message, t, *details)
  }

  override fun warn(message: String?, t: Throwable?) {
    messageHandler.processMessage(CompilerMessage(COMPILER_NAME, BuildMessage.Kind.WARNING, message))
    fileLogger?.warn(message, t)
  }

  override fun info(message: String?, t: Throwable?) {
    messageHandler.processMessage(CompilerMessage(COMPILER_NAME, BuildMessage.Kind.INFO, message + (t?.message?.let { ": $it" } ?: "")))
    fileLogger?.info(message, t)
  }

  override fun isDebugEnabled(): Boolean = fileLogger != null && fileLogger.isDebugEnabled

  override fun debug(message: String?, t: Throwable?) {
    fileLogger?.debug(message, t)
  }

  override fun isTraceEnabled(): Boolean = fileLogger != null && fileLogger.isTraceEnabled

  override fun trace(message: String?) {
    fileLogger?.trace(message)
  }

  override fun trace(t: Throwable?) {
    fileLogger?.trace(t)
  }
}

private lateinit var messageHandler: JpsMessageHandler

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
