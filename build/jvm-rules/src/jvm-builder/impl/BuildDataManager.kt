// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch", "UnstableApiUsage")

package org.jetbrains.jps.incremental.storage

import org.jetbrains.bazel.jvm.worker.storage.BazelPersistentMapletFactory
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.java.dependencyView.Mappings
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.builders.storage.StorageProvider
import org.jetbrains.jps.dependency.DependencyGraph
import org.jetbrains.jps.dependency.GraphConfiguration
import org.jetbrains.jps.dependency.NodeSourcePathMapper
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl
import org.jetbrains.jps.dependency.impl.PathSourceMapper
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.bazel.jvm.mvStore.mvStoreMapFactoryExposer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.function.Consumer
import java.util.function.Function

private val processConstantsIncrementally = !System.getProperty("compiler.process.constants.non.incremental", "false").toBoolean()

internal class BuildDataManager private constructor(
  val dataPaths: BuildDataPaths,
  @get:Deprecated("Use {@link #getTargetStateManager()} or, preferably, avoid using internal APIs.") val targetsState: BuildTargetsState,
  val relativizer: PathRelativizerService,
  private val dataManager: BuildDataProvider,
  @JvmField val depGraph: DependencyGraph,
  @JvmField val containerFactory: BazelPersistentMapletFactory,
) {
  private val depGraphPathMapper: NodeSourcePathMapper

  private val targetToStorages = ConcurrentHashMap<Pair<BuildTarget<*>, StorageProvider<StorageOwner>>, StorageOwner>()

  init {
    val typeAwareRelativizer = relativizer.typeAwareRelativizer!!
    depGraphPathMapper = PathSourceMapper(
      Function { typeAwareRelativizer.toAbsolute(it, RelativePathType.SOURCE) },
      Function { typeAwareRelativizer.toRelative(it, RelativePathType.SOURCE) },
    )
  }

  companion object {
    fun open(
      containerFactory: BazelPersistentMapletFactory,
      dataPaths: BuildDataPaths,
      relativizer: PathRelativizerService,
      targetState: BuildTargetsState,
      dataManager: BuildDataProvider,
    ): BuildDataManager {
      val depGraph = DependencyGraphImpl(containerFactory)
      return BuildDataManager(
        dataPaths = dataPaths,
        targetsState = targetState,
        dataManager = dataManager,
        relativizer = relativizer,
        depGraph = depGraph,
        containerFactory = containerFactory,
      )
    }
  }

  @Suppress("unused")
  fun getMappings(): Mappings? = null

  @Suppress("unused")
  fun clearCache() {
    dataManager.clearCache()
  }

  @Suppress("unused")
  var isProcessConstantsIncrementally: Boolean
    get() = processConstantsIncrementally
    set(processInc) {
      throw UnsupportedOperationException()
    }

  @Suppress("unused")
  fun getTargetStateManager(): BuildTargetStateManager {
    @Suppress("DEPRECATION")
    return targetsState.impl
  }

  @Suppress("unused")
  fun cleanStaleTarget(targetType: BuildTargetType<*>, targetId: String) {
    throw UnsupportedOperationException()
  }

  @Suppress("unused")
  fun getOutputToTargetMapping(): OutputToTargetMapping {
    return dataManager.getOutputToTargetMapping()
  }

  @Suppress("unused")
  fun getSourceToOutputMap(target: BuildTarget<*>): SourceToOutputMapping {
    return dataManager.getSourceToOutputMapping(target)
  }

  @Suppress("unused")
  fun getFileStampStorage(target: BuildTarget<*>): StampsStorage<*>? {
    return dataManager.getFileStampStorage(target)
  }

  @Suppress("unused")
  fun <S : StorageOwner> getStorage(target: BuildTarget<*>, provider: StorageProvider<S>): S {
    @Suppress("UNCHECKED_CAST")
    return targetToStorages.computeIfAbsent(target to provider as StorageProvider<StorageOwner>) {
      mvStoreMapFactoryExposer.set(containerFactory.mvstoreMapFactory)
      try {
        it.second.createStorage(dataPaths.getTargetDataRootDir(it.first), relativizer)
      }
      finally {
        mvStoreMapFactoryExposer.remove()
      }
    } as S
  }

  @Suppress("unused")
  fun getSourceToFormMap(target: BuildTarget<*>): OneToManyPathMapping {
    return dataManager.getSourceToForm(target)
  }

  private val graphConfiguration = object : GraphConfiguration {
    override fun getPathMapper(): NodeSourcePathMapper = depGraphPathMapper

    override fun getGraph(): DependencyGraph = depGraph
  }

  @Suppress("unused")
  fun getDependencyGraph(): GraphConfiguration = graphConfiguration

  @Suppress("unused")
  fun cleanTargetStorages(target: BuildTarget<*>) {
    throw UnsupportedOperationException()
  }

  @Suppress("unused")
  fun clean(asyncTaskCollector: Consumer<Future<*>>) {
    throw UnsupportedOperationException()
  }

  @Suppress("unused")
  fun flush(memoryCachesOnly: Boolean) {
  }

  fun close() {
    runAllCatching(sequence {
      // we do not call dataManager.close() - it is empty for BazelBuildDataProvider
      for (storage in targetToStorages.values) {
        yield { storage.close() }
      }

      yield { containerFactory.close() }
    })
  }

  fun forceClose() {
    runAllCatching(sequence {
      // we do not call dataManager.close() - it is empty for BazelBuildDataProvider
      yield { containerFactory.forceClose() }

      for (storage in targetToStorages.values) {
        yield { storage.close() }
      }
    })
  }

  @Suppress("unused")
  fun saveVersion() {
  }
}

internal fun runAllCatching(tasks: Sequence<() -> Unit>) {
  var exception: Throwable? = null
  for (action in tasks) {
    try {
      action()
    }
    catch (e: Throwable) {
      if (exception == null) {
        exception = e
      }
      else {
        exception.addSuppressed(e)
      }
    }
  }
  exception?.let { throw it }
}
