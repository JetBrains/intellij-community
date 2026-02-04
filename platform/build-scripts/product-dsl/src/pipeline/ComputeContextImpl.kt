// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UNCHECKED_CAST", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.pipeline

import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Implementation that manages slot registry and error collection.
 *
 * **Thread safety:** Uses [ConcurrentHashMap] for all registries to support concurrent
 * node execution. Each slot uses [CompletableDeferred] for safe publish-once semantics.
 *
 * **Lifecycle:**
 * 1. Pipeline creates context with [GenerationModel]
 * 2. Pipeline initializes slots via [initSlot] based on node declarations
 * 3. Nodes execute via node-scoped contexts created with [forNode]
 * 4. Pipeline collects results via [tryGet] and [getAllErrors]
 */
internal class ComputeContextImpl(
  val model: GenerationModel,
) {
  /** Slot registry: DataSlot → CompletableDeferred holding the value */
  private val slots = ConcurrentHashMap<DataSlot<*>, CompletableDeferred<*>>()

  /** Error registry: NodeId → list of errors emitted by that node */
  private val errorsByNode = ConcurrentHashMap<NodeId, MutableList<ValidationError>>()

  /**
   * Initializes a slot for use. Called by the pipeline before execution.
   *
   * @param slot The slot to initialize
   */
  fun initSlot(slot: DataSlot<*>) {
    val previous = slots.putIfAbsent(slot, CompletableDeferred<Any?>())
    check(previous == null) { "Slot '${slot.name}' already initialized" }
  }

  /**
   * Initializes an error slot for a node. Called by the pipeline.
   *
   * @param nodeId The node ID whose errors this slot will provide
   */
  fun initErrorSlot(nodeId: NodeId) {
    errorsByNode.putIfAbsent(nodeId, CopyOnWriteArrayList())
  }

  /**
   * Creates a node-scoped [ComputeContext] for safe error attribution.
   */
  fun forNode(nodeId: NodeId): ComputeContext {
    errorsByNode.putIfAbsent(nodeId, CopyOnWriteArrayList())
    return NodeComputeContext(nodeId)
  }

  private inner class NodeComputeContext(
    private val nodeId: NodeId,
  ) : ComputeContext {
    override val model: GenerationModel
      get() = this@ComputeContextImpl.model

    override suspend fun <T> get(slot: DataSlot<T>): T {
      return this@ComputeContextImpl.get(slot)
    }

    override fun <T> publish(slot: DataSlot<T>, value: T) {
      this@ComputeContextImpl.publish(slot, value)
    }

    override fun emitError(error: ValidationError) {
      errorsByNode.get(nodeId)?.add(error)
        ?: error("Error list not initialized for node '${nodeId.name}'")
    }
  }

  /**
   * Registers errors for a node after execution completes.
   * Also completes the error slot for downstream access.
   */
  fun finalizeNodeErrors(nodeId: NodeId) {
    val errors = errorsByNode.get(nodeId) ?: emptyList()
    val errorSlot = ErrorSlot(nodeId)
    val deferred = slots.get(errorSlot) as? CompletableDeferred<List<ValidationError>>
    deferred?.complete(errors)
  }

  // ============ ComputeContext Delegate Methods ============

  suspend fun <T> get(slot: DataSlot<T>): T {
    val deferred = slots.get(slot) as? CompletableDeferred<T>
      ?: error("Slot '${slot.name}' not initialized. Did you declare it in 'requires'?")
    return deferred.await()
  }

  fun <T> publish(slot: DataSlot<T>, value: T) {
    val deferred = slots.get(slot) as? CompletableDeferred<T>
      ?: error("Slot '${slot.name}' not initialized. Did you declare it in 'produces'?")
    val completed = deferred.complete(value)
    check(completed) { "Slot '${slot.name}' already published" }
  }

  // ============ Pipeline Access Methods ============

  /**
   * Tries to get a slot value without blocking. Returns null if not yet published.
   * Used by the pipeline to collect results after execution.
   */
  fun <T> tryGet(slot: DataSlot<T>): T? {
    val deferred = slots.get(slot) as? CompletableDeferred<T> ?: return null
    return if (deferred.isCompleted) {
      deferred.getCompleted()
    }
    else {
      null
    }
  }

  /**
   * Gets errors emitted by a specific node.
   *
   * @param nodeId The node to get errors for
   * @return List of errors, or empty list if no errors
   */
  fun getNodeErrors(nodeId: NodeId): List<ValidationError> {
    return errorsByNode.get(nodeId)?.toList() ?: emptyList()
  }

  /**
   * Gets all errors from all nodes.
   *
   * @return Map of node ID to error list
   */
  fun getAllErrors(): Map<NodeId, List<ValidationError>> {
    return errorsByNode.mapValues { it.value.toList() }
  }

  /**
   * Gets all errors as a flat list.
   *
   * @return All errors from all nodes
   */
  fun getAllErrorsFlat(): List<ValidationError> {
    return errorsByNode.values.flatten()
  }
}
