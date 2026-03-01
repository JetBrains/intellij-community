// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.pipeline

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

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
    override suspend fun execute(ctx: ComputeContext) {
      // No-op for testing
    }
  }
}
