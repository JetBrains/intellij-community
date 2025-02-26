@file:Suppress("PackageDirectoryMismatch", "UnstableApiUsage")

package org.jetbrains.jps.incremental.storage

import org.jetbrains.bazel.jvm.jps.impl.BazelPersistentMapletFactory
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.builders.java.dependencyView.Mappings
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.builders.storage.StorageProvider
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.Delta
import org.jetbrains.jps.dependency.DependencyGraph
import org.jetbrains.jps.dependency.DifferentiateParameters
import org.jetbrains.jps.dependency.DifferentiateResult
import org.jetbrains.jps.dependency.GraphConfiguration
import org.jetbrains.jps.dependency.Node
import org.jetbrains.jps.dependency.NodeSource
import org.jetbrains.jps.dependency.NodeSourcePathMapper
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.impl.DependencyGraphImpl
import org.jetbrains.jps.dependency.impl.PathSourceMapper
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.BuildDataManager.Companion.PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import java.util.function.Function
import kotlin.concurrent.read
import kotlin.concurrent.write

private val processConstantsIncrementally = !System.getProperty(PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY, "false").toBoolean()

class BuildDataManager internal constructor(
  val dataPaths: BuildDataPaths,
  @get:Deprecated("Use {@link #getTargetStateManager()} or, preferably, avoid using internal APIs.") val targetsState: BuildTargetsState,
  val relativizer: PathRelativizerService,
  private val dataManager: BuildDataProvider,
  containerFactory: BazelPersistentMapletFactory,
) {
  private val depGraph: DependencyGraph
  private val depGraphPathMapper: NodeSourcePathMapper

  private val targetToStorages = ConcurrentHashMap<Pair<BuildTarget<*>, StorageProvider<StorageOwner>>, StorageOwner>()

  init {
    try {
      depGraph = SynchronizedDependencyGraph(DependencyGraphImpl(containerFactory))
    }
    catch (e: Throwable) {
      try {
        close()
      }
      catch (_: Throwable) {
      }
      throw e
    }

    val typeAwareRelativizer = relativizer.typeAwareRelativizer!!
    depGraphPathMapper = PathSourceMapper(
      Function { typeAwareRelativizer.toAbsolute(it, RelativePathType.SOURCE) },
      Function { typeAwareRelativizer.toRelative(it, RelativePathType.SOURCE) },
    )
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
      it.second.createStorage(dataPaths.getTargetDataRootDir(it.first), relativizer)
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
    if (!memoryCachesOnly) {
      dataManager.commit()
    }
  }

  fun close() {
    runAllCatching(sequence {
      yield { dataManager.close() }

      for (storage in targetToStorages.values) {
        yield { storage.close() }
      }

      yield { depGraph.close() }
    })
  }

  @Suppress("unused")
  fun saveVersion() {
  }

  @Suppress("unused")
  fun reportUnhandledRelativizerPaths() {
    relativizer.reportUnhandledPaths()
  }

  companion object {
    const val PROCESS_CONSTANTS_NON_INCREMENTAL_PROPERTY: String = "compiler.process.constants.non.incremental"
  }
}

private class SynchronizedDependencyGraph(private val delegate: DependencyGraph) : DependencyGraph {
  private val lock = ReentrantReadWriteLock()

  override fun createDelta(sourcesToProcess: Iterable<NodeSource?>?, deletedSources: Iterable<NodeSource?>?, isSourceOnly: Boolean): Delta? {
    lock.read {
      return delegate.createDelta(sourcesToProcess, deletedSources, isSourceOnly)
    }
  }

  override fun differentiate(delta: Delta?, params: DifferentiateParameters?): DifferentiateResult? {
    lock.read {
      return delegate.differentiate(delta, params)
    }
  }

  override fun integrate(diffResult: DifferentiateResult) {
    lock.write {
      delegate.integrate(diffResult)
    }
  }

  override fun getIndices(): Iterable<BackDependencyIndex?>? {
    return delegate.indices
  }

  override fun getIndex(name: String?) = delegate.getIndex(name)

  override fun getSources(id: ReferenceID): Iterable<NodeSource> = delegate.getSources(id)

  override fun getRegisteredNodes(): Iterable<ReferenceID> = delegate.registeredNodes

  override fun getSources(): Iterable<NodeSource> = delegate.sources

  override fun getNodes(source: NodeSource): Iterable<Node<*, *>> = delegate.getNodes(source)

  override fun <T : Node<T, *>> getNodes(src: NodeSource?, nodeSelector: Class<T>): Iterable<T> = delegate.getNodes(src, nodeSelector)

  override fun getDependingNodes(id: ReferenceID) = delegate.getDependingNodes(id)

  override fun close() {
    lock.write {
      delegate.close()
    }
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
