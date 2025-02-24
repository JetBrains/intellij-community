// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "HardCodedStringLiteral")

package org.jetbrains.intellij.build.impl.logging.jps

import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.plugin.structure.base.utils.createParentDirs
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.logging.TeamCityBuildMessageLogger
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.*
import java.beans.Introspector
import java.nio.file.Files
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

@Internal
internal suspend fun withJpsLogging(context: CompilationContext, span: Span, action: suspend (JpsMessageHandler) -> Unit) {
  val messageHandler = JpsMessageHandler(context, span)
  JpsLoggerFactory.messageHandler = messageHandler
  if (context.options.compilationLogEnabled) {
    val categoriesWithDebugLevel = context.compilationData.categoriesWithDebugLevel
    val buildLogFile = context.compilationData.buildLogFile
    try {
      JpsLoggerFactory.fileLoggerFactory = JpsFileLoggerFactory(buildLogFile, categoriesWithDebugLevel)
      Span.current().addEvent(
        "build log will be written to $buildLogFile",
        Attributes.of(
          AttributeKey.stringKey("level"), if (categoriesWithDebugLevel.isEmpty()) "info" else "debug level for $categoriesWithDebugLevel",
        )
      )
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      Span.current().addEvent(
        "cannot setup additional logging",
        Attributes.of(
          AttributeKey.stringKey("error"), e.message ?: "",
          AttributeKey.stringKey("buildLogFile"), buildLogFile.toString(),
        )
      )
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
    @JvmField
    internal var messageHandler: JpsMessageHandler? = null
    @JvmField
    var fileLoggerFactory: JpsFileLoggerFactory? = null
  }

  override fun getLoggerInstance(category: String): Logger {
    return JpsLogger(category, fileLoggerFactory?.getLoggerInstance(category))
  }
}

internal class JpsMessageHandler(private val context: CompilationContext, private val span: Span) : MessageHandler {
  @JvmField
  val errorMessagesByCompiler = ConcurrentHashMap<String, MutableList<String>>()
  private val compilationStartTimeForTarget = ConcurrentHashMap<String, Long>()
  private val compilationFinishTimeForTarget = ConcurrentHashMap<String, Long>()
  private var progress = -1.0f

  override fun processMessage(message: BuildMessage): Unit = TeamCityBuildMessageLogger.withFlow(span) {
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
              if (message.line != -1L) {
                append(':').append(message.line)
              }
              appendLine(':')
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
        errorMessagesByCompiler.computeIfAbsent(compilerName) { CopyOnWriteArrayList() }.add(messageText)
      }
      BuildMessage.Kind.WARNING -> context.messages.warning(text)
      BuildMessage.Kind.INFO, BuildMessage.Kind.JPS_INFO -> if (message is BuilderStatisticsMessage) {
        val buildKind = if (context.options.incrementalCompilation) " (incremental)" else ""
        context.messages.reportStatisticValue("Compilation time '${message.builderName}'$buildKind, ms", message.elapsedTimeMs.toString())
        val sources = message.numberOfProcessedSources
        context.messages.reportStatisticValue("Processed files by '${message.builderName}'$buildKind", sources.toString())
        if (!context.options.incrementalCompilation && sources > 0) {
          context.messages.reportStatisticValue(
            "Compilation time per file for '${message.builderName}', ms", String.format(Locale.US, "%.2f", message.elapsedTimeMs.toDouble() / sources)
          )
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

    buildMessages.info(
      " average: ${
        String.format("%.2f", ((compilationTimeForTarget.sumOf { it.second }.toDouble()) / compilationTimeForTarget.size) / 1000000)
      }ms"
    )
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

@Nls
private const val COMPILER_NAME = "build runner"

private class JpsLogger(category: String?, val fileLogger: Logger?) : DefaultLogger(category) {
  init {
    require(messageHandler != null || fileLogger != null) {
      "Jps logging is not initialized"
    }
  }

  private val messageHandler: JpsMessageHandler?
    get() = JpsLoggerFactory.messageHandler

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