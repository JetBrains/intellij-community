// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.TargetTypeRegistry
import org.jetbrains.jps.model.JpsModel
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

private val LOG: Logger = Logger.getInstance(BuildTargetsState::class.java)

@ApiStatus.Internal
interface BuildTargetStateManager {
  fun getBuildTargetId(target: BuildTarget<*>): Int

  fun getLastSuccessfulRebuildDuration(): Long

  fun setLastSuccessfulRebuildDuration(duration: Long)

  fun getTargetConfiguration(target: BuildTarget<*>): BuildTargetConfiguration

  fun getStaleTargetIds(type: BuildTargetType<*>): List<Pair<String, Int>>

  fun cleanStaleTarget(type: BuildTargetType<*>, targetId: String)

  fun getAverageBuildTime(type: BuildTargetType<*>): Long

  fun setAverageBuildTime(type: BuildTargetType<*>, time: Long)

  fun save()

  fun clean()

  fun storeNonExistentOutputRoots(target: BuildTarget<*>, context: CompileContext)

  fun isTargetDirty(target: BuildTarget<*>, projectDescriptor: ProjectDescriptor): Boolean

  fun invalidate(target: BuildTarget<*>)
}

internal class BuildTargetStateManagerImpl(
  private val dataPaths: BuildDataPaths,
  private val model: JpsModel,
) : BuildTargetStateManager {
  private val maxTargetId = AtomicInteger(0)
  private var lastSuccessfulRebuildDuration: Long = -1
  private val typeToState = ConcurrentHashMap<BuildTargetType<*>, BuildTargetTypeState>()

  init {
    val targetTypeListFile = getTargetTypesFile()
    try {
      DataInputStream(BufferedInputStream(Files.newInputStream(targetTypeListFile))).use { input ->
        maxTargetId.set(input.readInt())
        lastSuccessfulRebuildDuration = input.readLong()
      }
    }
    catch (e: IOException) {
      LOG.debug("Cannot load $targetTypeListFile:${e.message}", e)
      LOG.debug("Loading all target types to calculate max target id")
      for (type in TargetTypeRegistry.getInstance().targetTypes) {
        getTypeState(type)
      }
    }
  }

  private fun getTargetTypesFile(): Path = dataPaths.getTargetsDataRoot().resolve("targetTypes.dat")

  override fun save() {
    try {
      val targetTypeListFile = getTargetTypesFile()
      Files.createDirectories(targetTypeListFile.parent)
      DataOutputStream(BufferedOutputStream(Files.newOutputStream(targetTypeListFile))).use { output ->
        output.writeInt(maxTargetId.get())
        output.writeLong(lastSuccessfulRebuildDuration)
      }
    }
    catch (e: IOException) {
      LOG.info("Cannot save targets info: ${e.message}", e)
    }
    for (state in typeToState.values) {
      state.save()
    }
  }

  override fun getBuildTargetId(target: BuildTarget<*>): Int = getTypeState(target.getTargetType()).getTargetId(target)

  override fun getLastSuccessfulRebuildDuration(): Long = lastSuccessfulRebuildDuration

  override fun setLastSuccessfulRebuildDuration(duration: Long) {
    lastSuccessfulRebuildDuration = duration
  }

  override fun getTargetConfiguration(target: BuildTarget<*>): BuildTargetConfiguration {
    return getTypeState(target.getTargetType()).getConfiguration(target, dataPaths)
  }

  override fun getStaleTargetIds(type: BuildTargetType<*>): List<Pair<String, Int>> {
    return getTypeState(type).staleTargetIds
  }

  override fun cleanStaleTarget(type: BuildTargetType<*>, targetId: String) {
    getTypeState(type).removeStaleTarget(targetId)
  }

  override fun setAverageBuildTime(type: BuildTargetType<*>, time: Long) {
    getTypeState(type).averageTargetBuildTime = time
  }

  override fun getAverageBuildTime(type: BuildTargetType<*>): Long = getTypeState(type).averageTargetBuildTime

  override fun storeNonExistentOutputRoots(target: BuildTarget<*>, context: CompileContext) {
    getTargetConfiguration(target).storeNonExistentOutputRoots(context)
  }

  override fun isTargetDirty(target: BuildTarget<*>, projectDescriptor: ProjectDescriptor): Boolean {
    return getTargetConfiguration(target).isTargetDirty(projectDescriptor)
  }

  override fun invalidate(target: BuildTarget<*>) {
    getTargetConfiguration(target).invalidate()
  }

  private fun getTypeState(type: BuildTargetType<*>): BuildTargetTypeState {
    return typeToState.computeIfAbsent(type) { BuildTargetTypeState(it, this, dataPaths, model) }
  }

  fun markUsedId(id: Int) {
    var current: Int
    var max: Int
    do {
      current = maxTargetId.get()
      max = max(id, current)
    }
    while (!maxTargetId.compareAndSet(current, max))
  }

  fun getFreeId(): Int = maxTargetId.incrementAndGet()

  override fun clean() {
    try {
      FileUtilRt.deleteRecursively(dataPaths.getTargetsDataRoot())
    }
    catch (_: IOException) {
    }
  }
}