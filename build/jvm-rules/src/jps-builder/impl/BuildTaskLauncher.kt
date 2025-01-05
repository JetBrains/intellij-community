// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.tracing.Tracer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.impl.BuildTargetChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.CompileScope
import org.jetbrains.jps.incremental.GlobalContextKey
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.BuildProgress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val LOG = Logger.getInstance(JpsProjectBuilder::class.java)

internal class BuildTaskLauncher(
  private val context: CompileContext,
  private val buildProgress: BuildProgress,
  private val builder: JpsProjectBuilder,
) {
  private val tasks: MutableList<BuildChunkTask>

  init {
    val span = Tracer.start("BuildTaskLauncher constructor")

    val targetIndex = context.projectDescriptor.buildTargetIndex
    val chunks = targetIndex.getSortedTargetChunks(context)
    tasks = ArrayList<BuildChunkTask>(chunks.size)
    val targetToTask = HashMap<BuildTarget<*>, BuildChunkTask>(chunks.size)
    for (chunk in chunks) {
      val task = BuildChunkTask(chunk)
      tasks.add(task)
      for (target in chunk.targets) {
        targetToTask.put(target, task)
      }
    }

    val collectTaskDependantsSpan = Tracer.start("IncProjectBuilder.collectTaskDependants")
    var taskCounter = 0
    for (task in tasks) {
      task.index = taskCounter
      taskCounter++
      for (target in task.chunk.targets) {
        for (dependency in targetIndex.getDependencies(target, context)) {
          val depTask = targetToTask.get(dependency)
          if (depTask != null && depTask != task) {
            task.addDependency(depTask)
          }
        }
      }
    }
    collectTaskDependantsSpan.complete()

    val prioritisationSpan = Tracer.start("IncProjectBuilder.prioritisation")
    // bitset stores indexes of transitively dependant tasks
    val chunkToTransitive = HashMap<BuildChunkTask, BitSet>()
    for (task in tasks.asReversed()) {
      val dependantTasks = task.tasksDependsOnThis
      val directDependants = HashSet(dependantTasks)
      val transitiveDependants = BitSet()
      for (directDependant in directDependants) {
        val dependantChunkTransitiveDependants = chunkToTransitive.get(directDependant)
        if (dependantChunkTransitiveDependants != null) {
          transitiveDependants.or(dependantChunkTransitiveDependants)
          transitiveDependants.set(directDependant.index)
        }
      }
      chunkToTransitive.put(task, transitiveDependants)
    }
    prioritisationSpan.complete()

    span.complete()
  }

  suspend fun buildInParallel() {
    val buildSpan = Tracer.start("Parallel build")
    coroutineScope {
      queueTasks(tasks = tasks.filter { it.isReady }, isDebugLogEnabled = LOG.isDebugEnabled, coroutineScope = this)
    }
    buildSpan.complete()
  }

  private fun queueTasks(tasks: List<BuildChunkTask>, isDebugLogEnabled: Boolean, coroutineScope: CoroutineScope) {
    for (task in tasks) {
      coroutineScope.launch {
        executeTask(
          chunkLocalContext = ChunkLocalCompileContext(context),
          task = task,
          isDebugLogEnabled = isDebugLogEnabled,
          coroutineScope = coroutineScope,
        )
      }
    }
  }

  private fun executeTask(
    chunkLocalContext: CompileContext,
    task: BuildChunkTask,
    isDebugLogEnabled: Boolean,
    coroutineScope: CoroutineScope,
  ) {
    try {
      try {
        val isAffectedSpan = Tracer.start("isAffected")
        val affected = isBuildChunkAffected(scope = context.scope, chunk = task.chunk)
        isAffectedSpan.complete()
        if (affected) {
          builder.buildTargetChunk(context = chunkLocalContext, chunk = task.chunk, buildProgress = buildProgress)
        }
      }
      finally {
        context.projectDescriptor.dataManager.closeSourceToOutputStorages(task.chunk)
      }
    }
    finally {
      if (isDebugLogEnabled) {
        LOG.debug("Finished compilation of ${task.chunk}")
      }

      val nextTasks = task.getNextReadyTasks()
      if (!nextTasks.isEmpty()) {
        queueTasks(tasks = nextTasks, isDebugLogEnabled = isDebugLogEnabled, coroutineScope = coroutineScope)
      }
    }
  }
}

internal class BuildChunkTask(@JvmField val chunk: BuildTargetChunk) {
  private val notBuildDependenciesCount = AtomicInteger(0)
  private val notBuiltDependencies = HashSet<BuildChunkTask>()

  @JvmField
  val tasksDependsOnThis = ArrayList<BuildChunkTask>()

  @JvmField
  var index = 0

  val isReady: Boolean
    get() = notBuildDependenciesCount.get() == 0

  fun addDependency(dependency: BuildChunkTask) {
    if (notBuiltDependencies.add(dependency)) {
      notBuildDependenciesCount.incrementAndGet()
      dependency.tasksDependsOnThis.add(this)
    }
  }

  fun getNextReadyTasks(): List<BuildChunkTask> {
    var nextTasks: MutableList<BuildChunkTask>? = null
    for (task in tasksDependsOnThis) {
      val dependenciesCount = task.notBuildDependenciesCount.decrementAndGet()
      if (dependenciesCount == 0) {
        if (nextTasks == null) {
          nextTasks = ArrayList<BuildChunkTask>()
        }
        nextTasks.add(task)
      }
    }
    return nextTasks ?: emptyList()
  }
}

private class ChunkLocalCompileContext(private val sharedContext: CompileContext) : CompileContext by sharedContext {
  private val localDataHolder = UserDataHolderBase()
  private val deletedKeysSet = ConcurrentHashMap.newKeySet<Any?>()

  override fun <T : Any?> getUserData(key: Key<T>): T? {
    return when {
      key is GlobalContextKey -> sharedContext.getUserData(key)
      deletedKeysSet.contains(key) -> null
      else -> localDataHolder.getUserData(key) ?: sharedContext.getUserData(key)
    }
  }

  override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
    if (key is GlobalContextKey) {
      sharedContext.putUserData(key, value)
      return
    }

    if (value == null) {
      deletedKeysSet.add(key)
    }
    else {
      deletedKeysSet.remove(key)
    }
    localDataHolder.putUserData(key, value)
  }

  override fun processMessage(message: BuildMessage) {
    if (message.kind == BuildMessage.Kind.ERROR) {
      localDataHolder.putUserData(Utils.ERRORS_DETECTED_KEY, true)
    }
    sharedContext.processMessage(message)
  }

  override fun isCanceled(): Boolean = sharedContext.isCanceled
}

internal fun isBuildChunkAffected(scope: CompileScope, chunk: BuildTargetChunk): Boolean {
  for (target in chunk.targets) {
    if (scope.isAffected(target)) {
      return true
    }
  }
  return false
}