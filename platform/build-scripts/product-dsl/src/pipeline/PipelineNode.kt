// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.pipeline

import org.jetbrains.intellij.build.productLayout.model.error.ValidationError

/**
 * Type-safe slot for inter-node data exchange.
 *
 * Slots are keys that carry type information, enabling type-safe publish/await operations.
 * The pipeline infers execution order from slot dependencies (requires/produces).
 *
 * @param T The type of data this slot carries
 * @param name Unique name for this slot (used for debugging and error messages)
 */
internal open class DataSlot<T>(@JvmField val name: String) {
  override fun toString(): String = "DataSlot($name)"
  override fun equals(other: Any?): Boolean = other is DataSlot<*> && name == other.name
  override fun hashCode(): Int = name.hashCode()
}

/**
 * Special slot for accessing errors from a specific generator.
 *
 * Allows downstream nodes to query errors from upstream nodes.
 * The pipeline automatically populates these slots after each node completes.
 *
 * Usage:
 * ```kotlin
 * val errors = ctx.get(ErrorSlot(NodeIds.CONTENT_MODULE_DEPS))
 * ```
 */
internal class ErrorSlot(@JvmField val generatorId: NodeId) : DataSlot<List<ValidationError>>("${generatorId.name}.errors")

/**
 * Context for compute node execution.
 *
 * Provides type-safe access to:
 * - Upstream slot values via [get]
 * - Publishing values for downstream nodes via [publish]
 * - Emitting validation errors via [emitError]
 * - Shared model with caches and configuration
 *
 * **Thread safety:** Each node gets its own context view. Slot operations are thread-safe
 * for concurrent node execution within the same pipeline run.
 */
internal interface ComputeContext {
  /**
   * Awaits and returns the value from a slot.
   *
   * Blocks until the producing node completes and publishes to this slot.
   * The node declaring this slot in [PipelineNode.requires] is guaranteed to have
   * access once the producing node completes.
   *
   * @param slot The slot to read from
   * @return The value published to this slot
   * @throws IllegalStateException if the slot was never published to
   */
  suspend fun <T> get(slot: DataSlot<T>): T

  /**
   * Publishes a value to a slot for downstream nodes.
   *
   * Must only be called once per slot. The slot should be declared in
   * [PipelineNode.produces] for proper dependency tracking.
   *
   * @param slot The slot to publish to
   * @param value The value to publish
   * @throws IllegalStateException if the slot was already published to
   */
  fun <T> publish(slot: DataSlot<T>, value: T)

  /**
   * Emits a validation error from this node.
   *
   * Errors are collected by the pipeline and aggregated after all nodes complete.
   * Downstream nodes can access these errors via [ErrorSlot].
   *
   * @param error The validation error to emit
   */
  fun emitError(error: ValidationError)

  /**
   * Emits multiple validation errors.
   *
   * @param errors The validation errors to emit
   */
  fun emitErrors(errors: List<ValidationError>) {
    for (error in errors) {
      emitError(error)
    }
  }

  /**
   * The shared generation model with caches, configuration, and utilities.
   */
  val model: GenerationModel
}

/**
 * A compute node in the generation pipeline.
 *
 * Nodes are the building blocks of the pipeline. Each node:
 * - Has a unique [id] for identification
 * - Declares data dependencies via [requires] (slots it reads from)
 * - Declares outputs via [produces] (slots it writes to)
 * - Executes via [execute] with access to [ComputeContext]
 *
 * **Execution model:**
 * - Nodes with no [requires] run immediately (level 0)
 * - Nodes wait for all [requires] slots to be published before running
 * - Nodes at the same dependency level run in parallel
 * - The pipeline infers execution order from slot dependencies
 *
 * **Design principles:**
 * - Nodes are pure functions: same context → same effects
 * - All data access through [ComputeContext]
 * - No return value - all outputs go through [ComputeContext.publish]
 *
 * **Example:**
 * ```kotlin
 * object PluginXmlDepsNode : PipelineNode {
 *   override val id = NodeIds.PLUGIN_XML_DEPS
 *   override val produces = setOf(Slots.PLUGIN_XML)
 *
 *   override suspend fun execute(ctx: ComputeContext) {
 *     val results = generatePluginXmlDeps(ctx.model)
 *     ctx.publish(Slots.PLUGIN_XML, PluginXmlOutput(results))
 *   }
 * }
 * ```
 */
internal interface PipelineNode {
  /**
   * Unique identifier for this node.
   * Used for logging, filtering, and error slot access.
   */
  val id: NodeId

  /**
   * Slots this node reads from.
   *
   * The pipeline ensures all required slots are published before this node runs.
   * Empty set (default) means this node can run immediately.
   *
   * **Note:** Including [ErrorSlot] here creates a dependency on that node's completion.
   */
  val requires: Set<DataSlot<*>> get() = emptySet()

  /**
   * Slots this node writes to.
   *
   * The pipeline uses this to build the dependency graph.
   * Empty set means this node produces no data for other nodes (e.g., validation-only).
   *
   * **Contract:** Must call [ComputeContext.publish] for each declared slot.
   */
  val produces: Set<DataSlot<*>> get() = emptySet()

  /**
   * Executes the node's computation.
   *
   * Use [ctx] to:
   * - Read upstream values: `ctx.get(Slots.SOME_INPUT)`
   * - Publish outputs: `ctx.publish(Slots.MY_OUTPUT, value)`
   * - Emit errors: `ctx.emitError(ValidationError(...))`
   *
   * @param ctx The compute context for this execution
   */
  suspend fun execute(ctx: ComputeContext)
}
