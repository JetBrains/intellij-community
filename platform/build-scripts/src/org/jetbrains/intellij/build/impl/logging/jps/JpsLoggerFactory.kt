// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "HardCodedStringLiteral")

package org.jetbrains.intellij.build.impl.logging.jps

import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.MultiMap
import com.jetbrains.plugin.structure.base.utils.createParentDirs
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.*
import java.beans.Introspector
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

@Internal
fun withJpsLogging(context: CompilationContext, action: (JpsMessageHandler) -> Unit) {
  val messageHandler = JpsMessageHandler(context)
  JpsLoggerFactory.messageHandler = messageHandler
  if (context.options.compilationLogEnabled) {
    val categoriesWithDebugLevel = context.compilationData.categoriesWithDebugLevel
    val buildLogFile = context.compilationData.buildLogFile
    try {
      JpsLoggerFactory.fileLoggerFactory = JpsFileLoggerFactory(buildLogFile, categoriesWithDebugLevel)
      context.messages.info(
        "Build log (${if (categoriesWithDebugLevel.isEmpty()) "info" else "debug level for $categoriesWithDebugLevel"}) " +
        "will be written to $buildLogFile"
      )
    }
    catch (t: Throwable) {
      context.messages.warning("Cannot setup additional logging to $buildLogFile: ${t.message}")
    }
  }
  val defaultLoggerFactory = Logger.getFactory()
  Logger.setFactory(JpsLoggerFactory::class.java)
  try {
    action(messageHandler)
  }
  finally {
    Logger.setFactory(defaultLoggerFactory)
  }
}

@VisibleForTesting
class JpsLoggerFactory : Logger.Factory {
  companion object {
    var messageHandler: JpsMessageHandler? = null
    var fileLoggerFactory: JpsFileLoggerFactory? = null
  }

  override fun getLoggerInstance(category: String): Logger {
    return JpsLogger(category, fileLoggerFactory?.getLoggerInstance(category))
  }
}

@Internal
class JpsMessageHandler(private val context: CompilationContext) : MessageHandler {
  val errorMessagesByCompiler = MultiMap.createConcurrent<String, String>()
  private val compilationStartTimeForTarget = ConcurrentHashMap<String, Long>()
  private val compilationFinishTimeForTarget = ConcurrentHashMap<String, Long>()
  private var progress = (-1.0).toFloat()
  lateinit var span: Span
  override fun processMessage(message: BuildMessage) = span.use<Unit> {
    val text = message.messageText
    when (message.kind) {
      BuildMessage.Kind.ERROR, BuildMessage.Kind.INTERNAL_BUILDER_ERROR -> {
        val compilerName: String
        val messageText: String
        if (message is CompilerMessage) {
          compilerName = message.compilerName
          val sourcePath = message.sourcePath
          messageText = buildString {
            if (sourcePath != null) {
              append(sourcePath)
              if (message.line != -1L) append(":" + message.line)
              appendLine(":")
            }
            append(text)
            val moduleNames = message.moduleNames
            if (moduleNames.any()) {
              append(moduleNames.joinToString(prefix = " (", postfix = ")"))
            }
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

  private fun reportProgress(targets: Collection<BuildTarget<*>>, targetSpecificMessage: String) {
    val targetsString = targets.joinToString(separator = ", ") { Introspector.decapitalize(it.presentableName) }
    val progressText = if (progress >= 0) " (${(100 * progress).toInt()}%)" else ""
    val targetSpecificText = if (targetSpecificMessage.isEmpty()) "" else ", $targetSpecificMessage"
    context.messages.progress("Compiling$progressText: $targetsString$targetSpecificText")
  }
}

private class JpsLogger(category: String?, val fileLogger: Logger?) : DefaultLogger(category) {
  companion object {
    @Nls
    const val COMPILER_NAME = "build runner"
  }

  init {
    require(messageHandler != null || fileLogger != null) {
      "Jps logging is not initialized"
    }
  }

  val messageHandler get() = JpsLoggerFactory.messageHandler

  override fun error(@Nls message: String?, t: Throwable?, vararg details: String) {
    if (t == null) {
      messageHandler?.processMessage(CompilerMessage(COMPILER_NAME, BuildMessage.Kind.ERROR, message))
    }
    else {
      messageHandler?.processMessage(CompilerMessage.createInternalBuilderError(COMPILER_NAME, t))
    }
    fileLogger?.error(message, t, *details)
  }

  override fun warn(message: String?, t: Throwable?) {
    messageHandler?.processMessage(CompilerMessage(COMPILER_NAME, BuildMessage.Kind.WARNING, message))
    fileLogger?.warn(message, t)
  }

  override fun info(message: String?, t: Throwable?) {
    messageHandler?.processMessage(CompilerMessage(COMPILER_NAME, BuildMessage.Kind.INFO, message + (t?.message?.let { ": $it" } ?: "")))
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