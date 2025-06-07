// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCompilerApi::class)

package org.jetbrains.bazel.jvm.worker.core

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import org.jetbrains.bazel.jvm.kotlin.CompilerPluginProvider
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.BuildListener
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.FileDeletedEvent
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.coroutines.CoroutineContext

@Suppress("SpellCheckingInspection")
class BazelCompileContext(
  @JvmField val scope: BazelCompileScope,
  private val projectDescriptor: ProjectDescriptor,
  private val delegateMessageHandler: RequestLog,
  private val coroutineContext: CoroutineContext,
) : UserDataHolderBase(), CompileContext {
  private val cancelStatus = CanceledStatus { !coroutineContext.isActive }

  @JvmField
  val pluginProvider: CompilerPluginProvider = object : CompilerPluginProvider {
    // todo configure via build setting label
    private val expects by getConstructor("fleet.multiplatform.expects.ExpectsPluginRegistrar", null)
    private val rhizomeDb by getConstructor(
      registrar = "com.jetbrains.rhizomedb.plugin.RhizomedbComponentRegistrar",
      commandLineProcessor = "com.jetbrains.rhizomedb.plugin.RhizomedbCommandLineProcessor",
      )

    @Suppress("SpellCheckingInspection")
    override fun provide(id: String): PluginCliParser.RegisteredPluginInfo {
      return when (id) {
        "jetbrains.fleet.expects-compiler-plugin" -> createPluginInfo(expects)
        "org.jetbrains.fleet.rhizomedb-compiler-plugin" -> createPluginInfo(rhizomeDb)
        else -> throw IllegalArgumentException("plugin requires classpath: $id")
      }
    }
  }

  private var isMarkedAsNonIncremental = false
  @Volatile
  private var compilationStartStamp = 0L

  @Volatile
  private var done = -1.0f
  private val listeners = EventDispatcher.create(BuildListener::class.java)

  override fun getCompilationStartStamp(target: BuildTarget<*>): Long = compilationStartStamp

  override fun setCompilationStartStamp(targets: Collection<BuildTarget<*>>, stamp: Long) {
    compilationStartStamp = stamp
  }

  override fun getLoggingManager(): BuildLoggingManager = projectDescriptor.loggingManager

  override fun getBuilderParameter(paramName: String?): String? = null

  override fun addBuildListener(listener: BuildListener) {
    listeners.addListener(listener)
  }

  override fun removeBuildListener(listener: BuildListener) {
    listeners.removeListener(listener)
  }

  override fun markNonIncremental(target: ModuleBuildTarget) {
    isMarkedAsNonIncremental = true
  }

  override fun shouldDifferentiate(chunk: ModuleChunk): Boolean = shouldDifferentiate()

  fun shouldDifferentiate(): Boolean = scope.isIncrementalCompilation && !isMarkedAsNonIncremental

  override fun getCancelStatus(): CanceledStatus = cancelStatus

  override fun checkCanceled() {
    coroutineContext.ensureActive()
  }

  override fun clearNonIncrementalMark(target: ModuleBuildTarget) {
    isMarkedAsNonIncremental = false
  }

  override fun getScope(): BazelCompileScope = scope

  fun compilerMessage(kind: BuildMessage.Kind, message: String, sourcePath: String? = null, line: Int = -1, column: Int = -1) {
    if (kind == BuildMessage.Kind.ERROR) {
      Utils.ERRORS_DETECTED_KEY.set(this, true)
    }
    delegateMessageHandler.compilerMessage(kind = kind, message = message, sourcePath = sourcePath, line = line, column = column)
  }

  override fun processMessage(message: BuildMessage) {
    if (message.kind == BuildMessage.Kind.ERROR) {
      Utils.ERRORS_DETECTED_KEY.set(this, true)
    }
    if (message is ProgressMessage) {
      message.done = done
    }
    delegateMessageHandler.processMessage(message)
    if (message is FileGeneratedEvent) {
      listeners.getMulticaster().filesGenerated(message)
    }
    else if (message is FileDeletedEvent) {
      listeners.getMulticaster().filesDeleted(message)
    }
  }

  override fun setDone(done: Float) {
    this.done = done
  }

  override fun getProjectDescriptor(): ProjectDescriptor = projectDescriptor
}

private fun createPluginInfo(data: Pair<MethodHandle, MethodHandle?>): PluginCliParser.RegisteredPluginInfo {
  return PluginCliParser.RegisteredPluginInfo(
    componentRegistrar = null,
    compilerPluginRegistrar = data.first.invoke() as CompilerPluginRegistrar,
    commandLineProcessor = data.second?.invoke() as CommandLineProcessor?,
    pluginOptions = emptyList(),
  )
}

private fun getConstructor(registrar: String, commandLineProcessor: String?): Lazy<Pair<MethodHandle, MethodHandle?>> {
  return lazy {
    findConstructor(registrar) to commandLineProcessor?.let { findConstructor(it) }
  }
}

private fun findConstructor(name: String): MethodHandle {
  val aClass = BazelCompileContext::class.java.classLoader.loadClass(name)
  return MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE))
}