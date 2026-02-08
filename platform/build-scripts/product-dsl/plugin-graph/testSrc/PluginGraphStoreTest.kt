// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.pluginGraph

import androidx.collection.IntList
import androidx.collection.MutableIntList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [PluginGraphStore] edge storage optimization.
 *
 * Verifies:
 * - Edge map key packing/unpacking round trips
 * - Edge storage API (getOrCreateSuccessors, getOrCreatePredecessors, hasInEdge)
 * - Edge type independence in consolidated map storage
 */
class PluginGraphStoreTest {
  /** Convert IntList to Kotlin List for assertions */
  private fun IntList.asList(): List<Int> = buildList { this@asList.forEach { add(it) } }

  private fun addTarget(store: MutablePluginGraphStore, name: String): Int {
    val id = store.names.size
    store.names.add(name)
    store.kinds.add(NODE_TARGET)
    store.mutableNameIndex(NODE_TARGET).set(name, id)
    return id
  }

  private fun addTargetDependency(
    store: MutablePluginGraphStore,
    sourceId: Int,
    targetId: Int,
    scope: TargetDependencyScope?,
  ) {
    val outKey = packEdgeMapKey(EDGE_TARGET_DEPENDS_ON, sourceId)
    val outList = store.outEdges.get(outKey) ?: MutableIntList(1).also { store.outEdges.set(outKey, it) }
    outList.add(packTargetDependencyEntry(targetId, scope))

    val inKey = packEdgeMapKey(EDGE_TARGET_DEPENDS_ON, targetId)
    val inList = store.inEdges.get(inKey) ?: MutableIntList(1).also { store.inEdges.set(inKey, it) }
    inList.add(packTargetDependencyEntry(sourceId, scope))
  }

  private fun addTargetDependencyOutOnly(
    store: MutablePluginGraphStore,
    sourceId: Int,
    targetId: Int,
    scope: TargetDependencyScope?,
  ) {
    val outKey = packEdgeMapKey(EDGE_TARGET_DEPENDS_ON, sourceId)
    val outList = store.outEdges.get(outKey) ?: MutableIntList(1).also { store.outEdges.set(outKey, it) }
    outList.add(packTargetDependencyEntry(targetId, scope))
  }

  @Nested
  inner class EdgeKeyPackingTest {
    @Test
    fun `pack and unpack edge key`() {
      val edgeType = EDGE_BUNDLES
      val nodeId = 12345
      val packed = packEdgeMapKey(edgeType, nodeId)

      assertThat(unpackEdgeType(packed)).isEqualTo(edgeType)
      assertThat(unpackEdgeNodeId(packed)).isEqualTo(nodeId)
    }

    @Test
    fun `supports max node ID`() {
      val maxNodeId = 0xFFFFFF // 16M - 1
      val packed = packEdgeMapKey(EDGE_TARGET_DEPENDS_ON, maxNodeId)

      assertThat(unpackEdgeNodeId(packed)).isEqualTo(maxNodeId)
    }

    @Test
    fun `supports all edge types`() {
      for (edgeType in 0 until EDGE_TYPE_COUNT) {
        val packed = packEdgeMapKey(edgeType, 42)
        assertThat(unpackEdgeType(packed)).isEqualTo(edgeType)
      }
    }

    @Test
    fun `different edge types produce different keys`() {
      val nodeId = 100
      val keys = (0 until EDGE_TYPE_COUNT).map { edgeType ->
        packEdgeMapKey(edgeType, nodeId)
      }

      assertThat(keys.distinct()).hasSize(EDGE_TYPE_COUNT)
    }

    @Test
    fun `same edge type different nodes produce different keys`() {
      val edgeType = EDGE_CONTAINS_CONTENT
      val keys = (0..100).map { nodeId ->
        packEdgeMapKey(edgeType, nodeId)
      }

      assertThat(keys.distinct()).hasSize(101)
    }
  }

  @Nested
  inner class TargetDependencyPackingTest {
    @Test
    fun `pack and unpack target dependency scope`() {
      val targetId = 4242
      val packedNull = packTargetDependencyEntry(targetId, null)

      assertThat(unpackNodeId(packedNull)).isEqualTo(targetId)
      assertThat(unpackTargetDependencyScope(packedNull)).isNull()

      for (scope in TargetDependencyScope.entries) {
        val packed = packTargetDependencyEntry(targetId, scope)
        assertThat(unpackNodeId(packed)).isEqualTo(targetId)
        assertThat(unpackTargetDependencyScope(packed)).isEqualTo(scope)
      }
    }

    @Test
    fun `target dependency traversal decodes packed scope`() {
      val store = MutablePluginGraphStore()
      val sourceId = addTarget(store, "source")
      val targetId = addTarget(store, "target")
      val targetId2 = addTarget(store, "target2")
      addTargetDependency(store, sourceId, targetId, TargetDependencyScope.TEST)
      addTargetDependency(store, sourceId, targetId2, null)

      val graph = PluginGraph(store.freeze())
      graph.query {
        val source = target("source")
        assertThat(source).isNotNull

        val seenScopes = LinkedHashMap<String, TargetDependencyScope?>()
        source!!.dependsOn { dep ->
          seenScopes[dep.target().name()] = dep.scope()
        }

        assertThat(seenScopes["target"]).isEqualTo(TargetDependencyScope.TEST)
        assertThat(seenScopes["target2"]).isNull()
      }
    }
  }

  @Nested
  inner class EdgeStorageApiTest {
    @Test
    fun `getOrCreateSuccessors creates list on first access`() {
      val store = MutablePluginGraphStore()
      val list = store.getOrCreateSuccessors(EDGE_BUNDLES, 100)
      list.add(200)

      assertThat(store.successors(EDGE_BUNDLES, 100)?.asList()).containsExactly(200)
    }

    @Test
    fun `getOrCreateSuccessors returns same list on subsequent access`() {
      val store = MutablePluginGraphStore()
      val list1 = store.getOrCreateSuccessors(EDGE_BUNDLES, 100)
      val list2 = store.getOrCreateSuccessors(EDGE_BUNDLES, 100)

      assertThat(list1).isSameAs(list2)
    }

    @Test
    fun `getOrCreatePredecessors creates list on first access`() {
      val store = MutablePluginGraphStore()
      val list = store.getOrCreatePredecessors(EDGE_BUNDLES, 100)
      list.add(50)

      assertThat(store.predecessors(EDGE_BUNDLES, 100)?.asList()).containsExactly(50)
    }

    @Test
    fun `getOrCreatePredecessors returns same list on subsequent access`() {
      val store = MutablePluginGraphStore()
      val list1 = store.getOrCreatePredecessors(EDGE_BUNDLES, 100)
      val list2 = store.getOrCreatePredecessors(EDGE_BUNDLES, 100)

      assertThat(list1).isSameAs(list2)
    }

    @Test
    fun `hasInEdge returns false for missing edge`() {
      val store = MutablePluginGraphStore()
      assertThat(store.hasInEdge(EDGE_MAIN_TARGET, 999)).isFalse()
    }

    @Test
    fun `hasInEdge returns true for existing edge`() {
      val store = MutablePluginGraphStore()
      store.getOrCreatePredecessors(EDGE_MAIN_TARGET, 100).add(50)

      assertThat(store.hasInEdge(EDGE_MAIN_TARGET, 100)).isTrue()
    }

    @Test
    fun `successors returns null for missing edge`() {
      val store = MutablePluginGraphStore()
      assertThat(store.successors(EDGE_BUNDLES, 999)).isNull()
    }

    @Test
    fun `predecessors returns null for missing edge`() {
      val store = MutablePluginGraphStore()
      assertThat(store.predecessors(EDGE_BUNDLES, 999)).isNull()
    }
  }

  @Nested
  inner class EdgeTypeIndependenceTest {
    @Test
    fun `different edge types are independent for same node`() {
      val store = MutablePluginGraphStore()
      store.getOrCreateSuccessors(EDGE_BUNDLES, 100).add(200)
      store.getOrCreateSuccessors(EDGE_CONTAINS_CONTENT, 100).add(300)

      assertThat(store.successors(EDGE_BUNDLES, 100)?.asList()).containsExactly(200)
      assertThat(store.successors(EDGE_CONTAINS_CONTENT, 100)?.asList()).containsExactly(300)
    }

    @Test
    fun `modifying one edge type does not affect others`() {
      val store = MutablePluginGraphStore()
      store.getOrCreateSuccessors(EDGE_BUNDLES, 100).add(200)
      store.getOrCreateSuccessors(EDGE_BUNDLES, 100).add(201)

      // EDGE_CONTAINS_CONTENT for same node should still be empty
      assertThat(store.successors(EDGE_CONTAINS_CONTENT, 100)).isNull()
    }

    @Test
    fun `hasInEdge is edge type specific`() {
      val store = MutablePluginGraphStore()
      store.getOrCreatePredecessors(EDGE_MAIN_TARGET, 100).add(50)

      assertThat(store.hasInEdge(EDGE_MAIN_TARGET, 100)).isTrue()
      assertThat(store.hasInEdge(EDGE_BACKED_BY, 100)).isFalse()
    }
  }

  @Nested
  inner class EdgeCopyingTest {
    @Test
    fun `copyEdgeMaps creates independent copies`() {
      val store = MutablePluginGraphStore()
      store.getOrCreateSuccessors(EDGE_BUNDLES, 100).add(200)
      store.getOrCreatePredecessors(EDGE_BUNDLES, 200).add(100)

      val (outCopy, _) = store.copyEdgeMaps()

      // Modify copy
      outCopy.get(packEdgeMapKey(EDGE_BUNDLES, 100))?.add(201)

      // Original should be unchanged
      assertThat(store.successors(EDGE_BUNDLES, 100)?.asList()).containsExactly(200)
    }

    @Test
    fun `toMutableStore creates independent copies`() {
      val store = MutablePluginGraphStore()
      val moduleIndex = store.mutableNameIndex(NODE_CONTENT_MODULE)
      val firstId = store.names.size
      store.names.add("first")
      store.kinds.add(NODE_CONTENT_MODULE)
      moduleIndex.put("first", firstId)

      val secondId = store.names.size
      store.names.add("second")
      store.kinds.add(NODE_CONTENT_MODULE)
      moduleIndex.put("second", secondId)

      store.getOrCreateSuccessors(EDGE_BUNDLES, firstId).add(secondId)
      store.getOrCreatePredecessors(EDGE_BUNDLES, secondId).add(firstId)

      val frozen = store.freeze()
      val copy = frozen.toMutableStore()

      val copyIndex = copy.mutableNameIndex(NODE_CONTENT_MODULE)
      val thirdId = copy.names.size
      copy.names.add("third")
      copy.kinds.add(NODE_CONTENT_MODULE)
      copyIndex.put("third", thirdId)
      copy.getOrCreateSuccessors(EDGE_BUNDLES, firstId).add(thirdId)

      assertThat(frozen.names.size).isEqualTo(2)
      assertThat(frozen.successors(EDGE_BUNDLES, firstId)?.asList()).containsExactly(secondId)
    }

    @Test
    fun `copyEdgesOfTypeTo copies specific edge type only`() {
      val source = MutablePluginGraphStore()
      source.getOrCreateSuccessors(EDGE_BUNDLES, 100).add(200)
      source.getOrCreateSuccessors(EDGE_CONTAINS_CONTENT, 100).add(300)
      source.getOrCreatePredecessors(EDGE_BUNDLES, 200).add(100)

      val target = MutablePluginGraphStore()
      source.copyEdgesOfTypeTo(target, EDGE_BUNDLES)

      // EDGE_BUNDLES should be copied
      assertThat(target.successors(EDGE_BUNDLES, 100)?.asList()).containsExactly(200)
      assertThat(target.predecessors(EDGE_BUNDLES, 200)?.asList()).containsExactly(100)

      // EDGE_CONTAINS_CONTENT should NOT be copied
      assertThat(target.successors(EDGE_CONTAINS_CONTENT, 100)).isNull()
    }
  }

  @Nested
  inner class FrozenStoreTraversalTest {
    @Test
    fun `freeze preserves adjacency order and counts`() {
      val store = MutablePluginGraphStore()
      store.getOrCreateSuccessors(EDGE_BUNDLES, 1).add(2)
      store.getOrCreateSuccessors(EDGE_BUNDLES, 1).add(3)
      store.getOrCreatePredecessors(EDGE_BUNDLES, 2).add(1)
      store.getOrCreatePredecessors(EDGE_BUNDLES, 3).add(1)
      store.getOrCreateSuccessors(EDGE_CONTAINS_CONTENT, 4).add(packEdgeEntry(10, LOADING_REQUIRED))

      val frozen = store.freeze()

      assertThat(frozen.successorCount(EDGE_BUNDLES, 1)).isEqualTo(2)
      assertThat(frozen.predecessorCount(EDGE_BUNDLES, 2)).isEqualTo(1)
      assertThat(frozen.hasInEdge(EDGE_BUNDLES, 2)).isTrue()

      val successors = ArrayList<Int>()
      frozen.forEachSuccessor(EDGE_BUNDLES, 1) { successors.add(it) }
      assertThat(successors).containsExactly(2, 3)

      val packedSuccessors = ArrayList<Int>()
      frozen.forEachSuccessor(EDGE_CONTAINS_CONTENT, 4) { packedSuccessors.add(it) }
      assertThat(packedSuccessors).containsExactly(packEdgeEntry(10, LOADING_REQUIRED))
    }

    @Test
    fun `freeze supports edges without explicit nodes`() {
      val store = MutablePluginGraphStore()
      store.getOrCreateSuccessors(EDGE_BUNDLES, 10).add(20)
      store.getOrCreatePredecessors(EDGE_BUNDLES, 20).add(10)

      val frozen = store.freeze()

      assertThat(frozen.successorCount(EDGE_BUNDLES, 10)).isEqualTo(1)
      val successors = ArrayList<Int>()
      frozen.forEachSuccessor(EDGE_BUNDLES, 10) { successors.add(it) }
      assertThat(successors).containsExactly(20)

      assertThat(frozen.predecessorCount(EDGE_BUNDLES, 20)).isEqualTo(1)
      val predecessors = ArrayList<Int>()
      frozen.forEachPredecessor(EDGE_BUNDLES, 20) { predecessors.add(it) }
      assertThat(predecessors).containsExactly(10)
    }
  }

  @Nested
  inner class LazyReverseTraversalTest {
    @Test
    fun `reverse edges are built lazily from forward target deps`() {
      val store = MutablePluginGraphStore()
      val sourceId = addTarget(store, "source")
      val targetId = addTarget(store, "target")
      val targetId2 = addTarget(store, "target2")
      addTargetDependencyOutOnly(store, sourceId, targetId, TargetDependencyScope.TEST)
      addTargetDependencyOutOnly(store, sourceId, targetId2, null)

      val frozen = store.freeze()

      assertThat(frozen.hasInEdge(EDGE_TARGET_DEPENDS_ON, targetId)).isTrue()
      assertThat(frozen.predecessorCount(EDGE_TARGET_DEPENDS_ON, targetId)).isEqualTo(1)
      assertThat(frozen.predecessors(EDGE_TARGET_DEPENDS_ON, targetId)?.asList())
        .containsExactly(packTargetDependencyEntry(sourceId, TargetDependencyScope.TEST))

      assertThat(frozen.hasInEdge(EDGE_TARGET_DEPENDS_ON, targetId2)).isTrue()
      assertThat(frozen.predecessorCount(EDGE_TARGET_DEPENDS_ON, targetId2)).isEqualTo(1)
      assertThat(frozen.predecessors(EDGE_TARGET_DEPENDS_ON, targetId2)?.asList())
        .containsExactly(packTargetDependencyEntry(sourceId, null))
    }
  }
}
