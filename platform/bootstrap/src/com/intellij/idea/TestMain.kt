// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TestMain")
package com.intellij.idea

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.scheduleDescriptorLoading
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
fun main(rawArgs: Array<String>) {
  val testEntryPointModule = System.getProperty("idea.dev.build.test.entry.point.module")
                             ?: error("idea.dev.build.test.entry.point.module property must be defined")
  val testEntryPointClass = System.getProperty("idea.dev.build.test.entry.point.class")
                            ?: error("idea.dev.build.test.entry.point.class property must be defined")
  val testAdditionalModules = System.getProperty("idea.dev.build.test.additional.modules")

  // Capture logs during plugin loading to include in error messages if module is not found.
  // DefaultFactory creates a new logger object on each getLogger call, so we cannot override its log level.
  // We use a factory which caches loggers and captures output to a StringBuilder for error diagnostics.
  val initialFactory = Logger.getFactory()
  val logCapturingFactory = LogCapturingFactory(mirrorToStdout = System.getProperty("intellij.force.plugin.logging.stdout") == "true")
  Logger.setFactory(logCapturingFactory)
  PluginManagerCore.logger.setLevel(LogLevel.INFO)

  @Suppress("SSBasedInspection")
  val pluginSet = runBlocking(Dispatchers.Default) {
    val zipPoolDeferred = async {
      val result = ZipFilePoolImpl()
      ZipFilePool.PATH_CLASSLOADER_POOL = result
      result
    }
    scheduleDescriptorLoading(
      coroutineScope = this@runBlocking,
      zipPoolDeferred = zipPoolDeferred,
      mainClassLoaderDeferred = CompletableDeferred(PluginManagerCore::class.java.classLoader),
      logDeferred = null,
    ).await()
  }

  Logger.setFactory(initialFactory)

  val testModule = pluginSet.findEnabledModule(PluginModuleId(testEntryPointModule, PluginModuleId.JETBRAINS_NAMESPACE))
    ?: error(buildString {
      appendLine("module $testEntryPointModule not found in product layout")

      // Structured loading errors from existing API
      val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
      if (errors.isNotEmpty()) {
        appendLine()
        appendLine("Plugin loading errors:")
        for (error in errors) {
          val msg = error.reason?.logMessage ?: error.htmlMessage.toString()
          appendLine("  - $msg")
        }
      }

      // Verbose log output
      appendLine()
      appendLine("Plugin loading logs:")
      append(logCapturingFactory.capturedLogs)
    })
  val testMainClassLoader = if (!testAdditionalModules.isNullOrEmpty()) {
    PathClassLoader(UrlClassLoader.build().files(testAdditionalModules.split(File.pathSeparator).map(Path::of)).parent(testModule.classLoader))
  }
  else {
    testModule.classLoader
  }
  val testMainClass = testMainClassLoader.loadClass(testEntryPointClass)
  val main = MethodHandles.lookup().findStatic(testMainClass, "main", MethodType.methodType(Void.TYPE, Array<String>::class.java))
  Thread.currentThread().contextClassLoader = testMainClassLoader
  main.invoke(rawArgs)
}

private class LogCapturingFactory(private val mirrorToStdout: Boolean) : Logger.Factory {
  private val loggers = ConcurrentHashMap<String, LogCapturingLogger>()
  val capturedLogs = StringBuilder()

  override fun getLoggerInstance(category: String): Logger {
    return loggers.computeIfAbsent(category) { LogCapturingLogger(it, capturedLogs, mirrorToStdout) }
  }
}

private class LogCapturingLogger(
  private val category: String,
  private val output: StringBuilder,
  private val mirrorToStdout: Boolean,
) : Logger() {
  private var level = LogLevel.WARNING

  override fun isDebugEnabled(): Boolean = level.compareTo(LogLevel.DEBUG) >= 0
  override fun isTraceEnabled(): Boolean = level.compareTo(LogLevel.TRACE) >= 0

  @Synchronized
  private fun log(levelName: String, message: String, t: Throwable? = null) {
    val line = "$levelName[$category]: $message"
    output.appendLine(line)
    if (mirrorToStdout) {
      println(line)
    }
    t?.let {
      val stackTrace = it.stackTraceToString()
      output.append(stackTrace)
      if (mirrorToStdout) {
        print(stackTrace)
      }
    }
  }

  override fun trace(message: String) {
    if (isTraceEnabled) log("TRACE", message)
  }

  override fun trace(t: Throwable?) {
    t?.let { if (isTraceEnabled) log("TRACE", "", it) }
  }

  override fun debug(message: String, t: Throwable?) {
    if (isDebugEnabled) log("DEBUG", message, t)
  }

  override fun info(message: String, t: Throwable?) {
    if (level.compareTo(LogLevel.INFO) >= 0) log("INFO", message, t)
  }

  override fun warn(message: String, t: Throwable?) {
    log("WARN", message, t)
  }

  override fun error(message: String, t: Throwable?, vararg details: String) {
    log("ERROR", message + details.joinToString("\n"), t)
  }

  override fun setLevel(level: LogLevel) {
    this.level = level
  }
}