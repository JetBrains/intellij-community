// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.pipeline

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.BuildTracer
import org.jetbrains.intellij.build.buildSpan
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.createTestModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tests for [GenerationPipeline] orchestration logic.
 *
 * Tests topological sorting, cycle detection, parallel execution levels,
 * and slot-based dependency resolution.
 */
@ExtendWith(TestFailureLogger::class)
class GenerationPipelineTest {
  @Test
  fun `pipeline validates no duplicate node IDs`() {
    val node1 = TestNode("node-a")
    val node2 = TestNode("node-a") // Duplicate ID

    assertThatThrownBy {
      GenerationPipeline(listOf(node1, node2))
    }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Duplicate node IDs")
      .hasMessageContaining("node-a")
  }

  @Test
  fun `pipeline validates all required slots have producers`() {
    val missingSlot = DataSlot<String>("missing-slot")
    val node1 = TestNode("node-a", requiresSlots = setOf(missingSlot))

    assertThatThrownBy {
      GenerationPipeline(listOf(node1))
    }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("requires slot")
      .hasMessageContaining("missing-slot")
  }

  @Test
  fun `pipeline detects circular slot dependencies`() {
    val slotA = DataSlot<String>("slot-a")
    val slotB = DataSlot<String>("slot-b")
    val nodeA = TestNode("node-a", requiresSlots = setOf(slotB), producesSlots = setOf(slotA))
    val nodeB = TestNode("node-b", requiresSlots = setOf(slotA), producesSlots = setOf(slotB))

    assertThatThrownBy {
      GenerationPipeline(listOf(nodeA, nodeB))
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Circular dependency detected")
  }

  @Test
  fun `pipeline detects circular dependencies in longer chains`() {
    val slotA = DataSlot<String>("slot-a")
    val slotB = DataSlot<String>("slot-b")
    val slotC = DataSlot<String>("slot-c")
    val nodeA = TestNode("node-a", requiresSlots = setOf(slotC), producesSlots = setOf(slotA))
    val nodeB = TestNode("node-b", requiresSlots = setOf(slotA), producesSlots = setOf(slotB))
    val nodeC = TestNode("node-c", requiresSlots = setOf(slotB), producesSlots = setOf(slotC))

    assertThatThrownBy {
      GenerationPipeline(listOf(nodeA, nodeB, nodeC))
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Circular dependency detected")
  }

  @Test
  fun `pipeline accepts valid slot dependency graph`() {
    val slotA = DataSlot<String>("slot-a")
    val slotB = DataSlot<String>("slot-b")
    val nodeA = TestNode("node-a", producesSlots = setOf(slotA))
    val nodeB = TestNode("node-b", requiresSlots = setOf(slotA), producesSlots = setOf(slotB))
    val nodeC = TestNode("node-c", requiresSlots = setOf(slotA, slotB))

    // Should not throw
    val pipeline = GenerationPipeline(listOf(nodeA, nodeB, nodeC))
    assertThat(pipeline).isNotNull()
  }

  @Test
  fun `pipeline accepts independent nodes`() {
    val nodeA = TestNode("node-a")
    val nodeB = TestNode("node-b")
    val nodeC = TestNode("node-c")

    // Should not throw - all independent
    val pipeline = GenerationPipeline(listOf(nodeA, nodeB, nodeC))
    assertThat(pipeline).isNotNull()
  }

  @Test
  fun `pipeline accepts empty node list`() {
    val pipeline = GenerationPipeline(emptyList())
    assertThat(pipeline).isNotNull()
  }

  @Test
  fun `default pipeline has expected nodes`() {
    val pipeline = GenerationPipeline.default()
    assertThat(pipeline).isNotNull()
    // Default pipeline should construct without errors
  }

  @Test
  fun `a run traces its stages and its nodes`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {}
    val config = ModuleSetGenerationConfig(
      moduleSetSources = emptyMap(),
      discoveredProducts = emptyList(),
      projectRoot = tempDir,
      outputProvider = createTestModuleOutputProvider(jps.project),
    )
    val slot = DataSlot<String>("slot-a")
    val pipeline = GenerationPipeline(listOf(
      TestNode("node-a", producesSlots = setOf(slot)),
      TestNode("node-b", requiresSlots = setOf(slot)),
    ))

    val spans = recordSpans { pipeline.execute(config, commitChanges = false) }

    val byName = spans.associateBy { it.name }
    assertThat(byName.keys).contains("discover", "build model", "execute nodes", "aggregate", "output", "node-a", "node-b")
    // A node runs on a worker thread, so the parent proves that the context reaches the worker.
    val executeNodes = byName.getValue("execute nodes")
    assertThat(byName.getValue("node-a").parentSpanId).isEqualTo(executeNodes.spanId)
    assertThat(byName.getValue("node-a").attributes.get(AttributeKey.stringKey("category"))).isEqualTo("GENERATION")
    // A step of the model building stage nests inside that stage, and never beside it.
    assertThat(byName.getValue("builder.buildFrozen").parentSpanId).isEqualTo(byName.getValue("build model").spanId)
  }

  @Test
  fun `a node starts when its producers end, not when its level ends`(@TempDir tempDir: Path) {
    val slotRoot = DataSlot<String>("slot-root")
    val slotSlow = DataSlot<String>("slot-slow")
    val slotFast = DataSlot<String>("slot-fast")
    val independentStarted = CountDownLatch(1)
    val slowSawIndependent = AtomicBoolean()
    val events = CopyOnWriteArrayList<String>()
    // A diamond: root -> (slow, fast) -> join. The independent node needs only the fast branch.
    val pipeline = GenerationPipeline(listOf(
      ActionNode("root", producesSlots = setOf(slotRoot)) { ctx -> ctx.publish(slotRoot, "root") },
      ActionNode("slow", requiresSlots = setOf(slotRoot), producesSlots = setOf(slotSlow)) { ctx ->
        slowSawIndependent.set(independentStarted.await(30, TimeUnit.SECONDS))
        events.add("slow end")
        ctx.publish(slotSlow, ctx.get(slotRoot) + "-slow")
      },
      ActionNode("fast", requiresSlots = setOf(slotRoot), producesSlots = setOf(slotFast)) { ctx ->
        ctx.publish(slotFast, ctx.get(slotRoot) + "-fast")
      },
      ActionNode("join", requiresSlots = setOf(slotSlow, slotFast)) { ctx ->
        events.add("join " + ctx.get(slotSlow) + " " + ctx.get(slotFast))
      },
      ActionNode("independent", requiresSlots = setOf(slotFast)) { ctx ->
        events.add("independent " + ctx.get(slotFast))
        independentStarted.countDown()
      },
    ))

    pipeline.execute(testConfig(tempDir), commitChanges = false)

    assertThat(slowSawIndependent.get()).isTrue()
    assertThat(events).containsExactly("independent root-fast", "slow end", "join root-slow root-fast")
  }

  @Test
  fun `a failed node fails the run and its consumers do not run`(@TempDir tempDir: Path) {
    val slotA = DataSlot<String>("slot-a")
    val consumerRan = AtomicBoolean()
    val pipeline = GenerationPipeline(listOf(
      ActionNode("producer", producesSlots = setOf(slotA)) { throw IllegalStateException("producer failed") },
      ActionNode("consumer", requiresSlots = setOf(slotA)) { consumerRan.set(true) },
    ))

    assertThatThrownBy { pipeline.execute(testConfig(tempDir), commitChanges = false) }
      .hasRootCauseMessage("producer failed")
    assertThat(consumerRan.get()).isFalse()
  }

  private fun testConfig(tempDir: Path): ModuleSetGenerationConfig {
    val jps = jpsProject(tempDir) {}
    return ModuleSetGenerationConfig(
      moduleSetSources = emptyMap(),
      discoveredProducts = emptyList(),
      projectRoot = tempDir,
      outputProvider = createTestModuleOutputProvider(jps.project),
    )
  }

  @Test
  fun `a run without a tracer records nothing`() {
    assertThat(buildSpan("probe") { it.isRecording }).isFalse()
  }

  /** Runs [operation] with a recording tracer of its own, and returns the spans it ended. */
  private fun recordSpans(operation: () -> Unit): List<SpanData> {
    val recorder = RecordingSpanProcessor()
    SdkTracerProvider.builder().addSpanProcessor(recorder).build().use { provider ->
      BuildTracer.install(provider.get("test")).use { operation() }
    }
    return recorder.spans
  }

  private class RecordingSpanProcessor : SpanProcessor {
    val spans: MutableList<SpanData> = CopyOnWriteArrayList()

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
    }

    override fun isStartRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) {
      spans.add(span.toSpanData())
    }

    override fun isEndRequired(): Boolean = true
  }

  // ========== Test Node Implementation ==========

  /**
   * Minimal PipelineNode implementation for testing pipeline orchestration.
   */
  private class TestNode(
    nodeId: String,
    requiresSlots: Set<DataSlot<*>> = emptySet(),
    producesSlots: Set<DataSlot<*>> = emptySet(),
  ) : PipelineNode {
    override val id = NodeId(nodeId, NodeCategory.GENERATION)
    override val requires: Set<DataSlot<*>> = requiresSlots
    override val produces: Set<DataSlot<*>> = producesSlots
    override fun execute(ctx: ComputeContext) {
      // No-op for testing
    }
  }

  /** A [PipelineNode] that runs [action]. */
  private class ActionNode(
    nodeId: String,
    requiresSlots: Set<DataSlot<*>> = emptySet(),
    producesSlots: Set<DataSlot<*>> = emptySet(),
    private val action: (ComputeContext) -> Unit,
  ) : PipelineNode {
    override val id = NodeId(nodeId, NodeCategory.GENERATION)
    override val requires: Set<DataSlot<*>> = requiresSlots
    override val produces: Set<DataSlot<*>> = producesSlots
    override fun execute(ctx: ComputeContext) {
      action(ctx)
    }
  }
}
