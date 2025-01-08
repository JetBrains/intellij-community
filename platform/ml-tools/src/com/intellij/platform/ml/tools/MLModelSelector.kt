package com.intellij.platform.ml.tools

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.ml.building.blocks.model.MLModel
import com.jetbrains.ml.building.blocks.model.MLModelLoader
import com.jetbrains.ml.building.blocks.task.MLTask
import com.jetbrains.ml.features.api.MLUnit
import com.jetbrains.ml.features.api.feature.FeatureDeclaration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists


/**
 * A utility class that orchestrates ML models for a machine learning task.
 *
 * It is aware of a default [defaultLoader] and when [modelPathRegistryKey]
 * is not empty, it loads a model from the corresponding path
 */
@ApiStatus.Internal
open class MLModelSelector<M : MLModel<P>, P : Any>(
  private val defaultLoader: MLModelLoader<M, P>,
  registryModelLoaderFactory: (Path) -> MLModelLoader<M, P>,
  private val modelPathRegistryKey: String,
  private val coroutineScope: CoroutineScope,
) {
  private val isUsingRegistry by lazy { Registry.`is`(modelPathRegistryKey) }
  private val registryLoader = RegistryModelLoader(modelPathRegistryKey, defaultLoader, registryModelLoaderFactory)
  private val modelLoader by lazy {
    if (isUsingRegistry) registryLoader else defaultLoader
  }
  private var state: AtomicReference<State<M, P>> = AtomicReference(State.NotLoaded)

  /**
   * Starts loading the ML model if it has not been loaded yet.
   * Call this function before calling [getModel] to make it
   * return not null on the first call, solving the problem of
   * cold start.
   */
  fun prepareModel(
    features: Map<MLUnit<*>, List<FeatureDeclaration<*>>>,
    parameters: Map<String, Any>? = null,
  ) {
    when (state.get()) {
      is State.Loaded -> return
      is State.Loading -> return
      is State.NotLoaded -> startLoading(features, parameters)
    }
  }

  fun prepareModel(task: MLTask, parameters: Map<String, Any>? = null) {
    prepareModel(task.treeFeaturesFlattened, parameters)
  }

  /**
   * Returns an instance of an ML model if it has been loaded. Null otherwise.
   * The model starts to load during the first
   *
   * If registry value of [modelPathRegistryKey] is not empty, the model is loaded from the specified path
   */
  fun getModel(
    features: Map<MLUnit<*>, List<FeatureDeclaration<*>>>,
    parameters: Map<String, Any>? = null,
  ): M? {
    return when (val s = state.get()) {
      is State.Loaded -> s.model
      is State.Loading -> null
      is State.NotLoaded -> {
        startLoading(features, parameters)
        null
      }
    }
  }

  fun getModel(
    task: MLTask,
    parameters: Map<String, Any>? = null,
  ): M? {
    return getModel(task.treeFeaturesFlattened, parameters)
  }


  private fun startLoading(
    features: Map<MLUnit<*>, List<FeatureDeclaration<*>>>,
    parameters: Map<String, Any>?,
  ) {
    if (!state.compareAndSet(State.NotLoaded, State.Loading)) return
    coroutineScope.async(Dispatchers.IO) {
      try {
        val model = modelLoader.loadModel(features, parameters)
        assert(state.compareAndSet(State.Loading, State.Loaded(model)))
      } catch (e: Throwable) {
        thisLogger().error("Unable to load ML model", e)
        state.compareAndSet(State.Loading, State.NotLoaded)
      }
    }
  }

  private sealed interface State<in M : MLModel<@UnsafeVariance P>, in P : Any> {
    data object NotLoaded : State<MLModel<Any>, Any>
    data object Loading : State<MLModel<Any>, Any>
    class Loaded<M : MLModel<P>, P : Any>(val model: M) : State<M, P>
  }
}

private class RegistryModelLoader<M : MLModel<P>, P : Any>(
  private val registryKey: String,
  defaultLoader: MLModelLoader<M, P>,
  private val registryModelLoaderFactory: (Path) -> MLModelLoader<M, P>,
) : MLModelLoader<M, P> {
  override val predictionClass: Class<P> = defaultLoader.predictionClass

  override suspend fun loadModel(features: Map<MLUnit<*>, List<FeatureDeclaration<*>>>, parameters: Map<String, Any>?): M {
    val key = Registry.get(registryKey)
    require(key.isRestartRequired()) {
      "Registry key `$registryKey` must require restart"
    }
    val path = key.asString().takeUnless { it.isEmpty() }?.let { Path.of(it) }
    requireNotNull(path) { "Registry key `$registryKey` must contain non-empty string" }
    require(path.isAbsolute) { "Registry key `$registryKey` must be an absolute path" }
    require(path.exists()) { "Registry key `$registryKey` be an existing path" }
    val loader = registryModelLoaderFactory(path)
    return loader.loadModel(features, parameters)
  }
}

private val MLTask.treeFeaturesFlattened: Map<MLUnit<*>, List<FeatureDeclaration<*>>>
  get() = treeFeatures.flatMap { it.entries }.associateBy({ it.key }, { it.value })
