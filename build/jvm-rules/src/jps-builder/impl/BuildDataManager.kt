@file:Suppress("PackageDirectoryMismatch", "UnstableApiUsage")

package org.jetbrains.jps.incremental.storage

import org.jetbrains.bazel.jvm.jps.storage.BazelPersistentMapletFactory
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
  depGraph: DependencyGraph,
  private val containerFactory: BazelPersistentMapletFactory,
) {
  @JvmField val depGraph: DependencyGraph = SynchronizedDependencyGraph(depGraph)
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

  @Suppress("unused")
  fun reportUnhandledRelativizerPaths() {
    relativizer.reportUnhandledPaths()
  }
}

private class SynchronizedDependencyGraph(private val delegate: DependencyGraph) : DependencyGraph {
  @Synchronized
  override fun createDelta(sourcesToProcess: Iterable<NodeSource?>?, deletedSources: Iterable<NodeSource?>?, isSourceOnly: Boolean): Delta? {
    return delegate.createDelta(sourcesToProcess, deletedSources, isSourceOnly)
  }

  @Synchronized
  override fun differentiate(delta: Delta?, params: DifferentiateParameters?): DifferentiateResult? {
    return delegate.differentiate(delta, params)
  }

  @Synchronized
  override fun integrate(diffResult: DifferentiateResult) {
    delegate.integrate(diffResult)
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

  @Synchronized
  override fun close() {
    delegate.close()
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
