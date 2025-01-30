// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.util.containers.prefixTree.map.emptyPrefixTreeMap
import com.intellij.util.containers.prefixTree.map.mutablePrefixTreeMapOf
import com.intellij.util.containers.prefixTree.map.prefixTreeMapOf
import com.intellij.util.containers.prefixTree.set.emptyPrefixTreeSet
import com.intellij.util.containers.prefixTree.set.prefixTreeSetOf
import org.assertj.core.api.Assertions.entry
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class PrefixTreeTest {

  @Test
  fun `test PrefixTreeMap#size`() {
    Assertions.assertThat(emptyPrefixTreeMap<String, Int>())
      .hasSize(0)

    Assertions.assertThat(
      prefixTreeMapOf(
        listOf("a", "b", "c", "3") to 30,
        listOf("a", "b", "c", "4") to 10,
        listOf("a", "b", "1") to 11,
        listOf("a", "b", "2") to 21,
        listOf("a", "b", "c") to 43,
        listOf("a") to 13
      )
    ).hasSize(6)
  }

  @Test
  fun `test PrefixTreeSet#size`() {
    Assertions.assertThat(emptyPrefixTreeSet<String>())
      .hasSize(0)

    Assertions.assertThat(
      prefixTreeSetOf(
        listOf("a", "b", "c", "3"),
        listOf("a", "b", "c", "4"),
        listOf("a", "b", "1"),
        listOf("a", "b", "2"),
        listOf("a", "b", "c"),
        listOf("a")
      )
    ).hasSize(6)
  }

  @Test
  fun `test PrefixTreeMap#toMap`() {
    Assertions.assertThat(emptyPrefixTreeMap<String, Int>().toMap())
      .isEqualTo(emptyMap<List<String>, Int>())

    Assertions.assertThat(
      prefixTreeMapOf(
        listOf("a", "b", "c", "3") to 30,
        listOf("a", "b", "c", "4") to 10,
        listOf("a", "b", "1") to 11,
        listOf("a", "b", "2") to 21,
        listOf("a", "b", "c") to 43,
        listOf("a") to 13
      ).toMap()
    ).isEqualTo(
      mapOf(
        listOf("a", "b", "c", "3") to 30,
        listOf("a", "b", "c", "4") to 10,
        listOf("a", "b", "1") to 11,
        listOf("a", "b", "2") to 21,
        listOf("a", "b", "c") to 43,
        listOf("a") to 13
      )
    )
  }

  @Test
  fun `test PrefixTreeSet#toSet`() {
    Assertions.assertThat(emptyPrefixTreeSet<String>().toSet())
      .isEqualTo(emptySet<List<String>>())

    Assertions.assertThat(
      prefixTreeSetOf(
        listOf("a", "b", "c", "3"),
        listOf("a", "b", "c", "4"),
        listOf("a", "b", "1"),
        listOf("a", "b", "2"),
        listOf("a", "b", "c"),
        listOf("a")
      ).toSet()
    ).isEqualTo(
      setOf(
        listOf("a", "b", "c", "3"),
        listOf("a", "b", "c", "4"),
        listOf("a", "b", "1"),
        listOf("a", "b", "2"),
        listOf("a", "b", "c"),
        listOf("a")
      )
    )
  }

  @Test
  fun `test PrefixTreeMap#get`() {
    val tree = prefixTreeMapOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c", "1")]).isEqualTo(null)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c", "2")]).isEqualTo(null)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c", "3")]).isEqualTo(30)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c", "4")]).isEqualTo(10)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "1")]).isEqualTo(11)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "2")]).isEqualTo(21)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "3")]).isEqualTo(null)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "4")]).isEqualTo(null)
    Assertions.assertThat<Int>(tree[listOf("a", "b", "c")]).isEqualTo(43)
    Assertions.assertThat<Int>(tree[listOf("a")]).isEqualTo(13)
  }

  @Test
  fun `test PrefixTreeMap#containKey`() {
    val tree = prefixTreeMapOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat(tree)
      .doesNotContainKey(listOf("a", "b", "c", "1"))
      .doesNotContainKey(listOf("a", "b", "c", "2"))
      .containsKey(listOf("a", "b", "c", "3"))
      .containsKey(listOf("a", "b", "c", "4"))
      .containsKey(listOf("a", "b", "1"))
      .containsKey(listOf("a", "b", "2"))
      .doesNotContainKey(listOf("a", "b", "3"))
      .doesNotContainKey(listOf("a", "b", "4"))
      .containsKey(listOf("a", "b", "c"))
      .containsKey(listOf("a"))
  }

  @Test
  fun `test PrefixTreeMap#containValue`() {
    val tree = prefixTreeMapOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 40,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat(tree)
      .containsValue(30)
      .containsValue(40)
      .containsValue(11)
      .containsValue(21)
      .containsValue(43)
      .containsValue(13)
      .doesNotContainValue(0)
      .doesNotContainValue(10)
  }

  @Test
  fun `test PrefixTreeMap#keys`() {
    Assertions.assertThat(
      prefixTreeMapOf(
        listOf("a", "b", "c", "3") to 30,
        listOf("a", "b", "c", "4") to 10,
        listOf("a", "b", "1") to 11,
        listOf("a", "b", "2") to 21,
        listOf("a", "b", "c") to 43,
        listOf("a") to 13
      )
    ).containsOnlyKeys(
      listOf("a", "b", "c", "3"),
      listOf("a", "b", "c", "4"),
      listOf("a", "b", "1"),
      listOf("a", "b", "2"),
      listOf("a", "b", "c"),
      listOf("a")
    )
  }

  @Test
  fun `test PrefixTreeMap#values`() {
    val tree = prefixTreeMapOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat(tree).values()
      .containsExactlyInAnyOrder(30, 10, 11, 21, 43, 13)
  }

  @Test
  fun `test PrefixTreeMap#put`() {
    val tree = mutablePrefixTreeMapOf(
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )

    tree[listOf("a", "b", "c", "1")] = 10
    tree[listOf("a", "b", "c", "2")] = 20
    tree[listOf("a", "b", "3")] = 30
    tree[listOf("a", "b", "4")] = 11

    Assertions.assertThat(tree)
      .containsOnly(
        entry(listOf("a", "b", "c", "1"), 10),
        entry(listOf("a", "b", "c", "2"), 20),
        entry(listOf("a", "b", "c", "3"), 30),
        entry(listOf("a", "b", "c", "4"), 10),
        entry(listOf("a", "b", "1"), 11),
        entry(listOf("a", "b", "2"), 21),
        entry(listOf("a", "b", "3"), 30),
        entry(listOf("a", "b", "4"), 11),
        entry(listOf("a", "b", "c"), 43),
        entry(listOf("a"), 13)
      )
  }

  @Test
  fun `test PrefixTreeMap#remove`() {
    val tree = mutablePrefixTreeMapOf(
      listOf("a", "b", "c", "1") to 10,
      listOf("a", "b", "c", "2") to 20,
      listOf("a", "b", "c", "3") to 30,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to 11,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "3") to 30,
      listOf("a", "b", "4") to 11,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )

    tree.remove(listOf("a", "b", "c", "1"))
    tree.remove(listOf("a", "b", "c", "2"))
    tree.remove(listOf("a", "b", "3"))
    tree.remove(listOf("a", "b", "4"))

    Assertions.assertThat(tree)
      .containsOnly(
        entry(listOf("a", "b", "c", "3"), 30),
        entry(listOf("a", "b", "c", "4"), 10),
        entry(listOf("a", "b", "1"), 11),
        entry(listOf("a", "b", "2"), 21),
        entry(listOf("a", "b", "c"), 43),
        entry(listOf("a"), 13)
      )
  }

  @Test
  fun `test map containing nullable values`() {
    val tree = mutablePrefixTreeMapOf(
      listOf("a", "b", "c", "1") to null,
      listOf("a", "b", "c", "2") to 20,
      listOf("a", "b", "c", "3") to null,
      listOf("a", "b", "c", "4") to 10,
      listOf("a", "b", "1") to null,
      listOf("a", "b", "2") to 21,
      listOf("a", "b", "3") to null,
      listOf("a", "b", "4") to 11,
      listOf("a", "b", "c") to 43,
      listOf("a") to 13
    )
    Assertions.assertThat(tree)
      .containsOnly(
        entry(listOf("a", "b", "c", "1"), null),
        entry(listOf("a", "b", "c", "2"), 20),
        entry(listOf("a", "b", "c", "3"), null),
        entry(listOf("a", "b", "c", "4"), 10),
        entry(listOf("a", "b", "1"), null),
        entry(listOf("a", "b", "2"), 21),
        entry(listOf("a", "b", "3"), null),
        entry(listOf("a", "b", "4"), 11),
        entry(listOf("a", "b", "c"), 43),
        entry(listOf("a"), 13)
      )

    tree.remove(listOf("a", "b", "c", "1"))
    tree.remove(listOf("a", "b", "c", "2"))
    tree.remove(listOf("a", "b", "3"))
    tree.remove(listOf("a", "b", "4"))

    Assertions.assertThat(tree)
      .containsOnly(
        entry(listOf("a", "b", "c", "3"), null),
        entry(listOf("a", "b", "c", "4"), 10),
        entry(listOf("a", "b", "1"), null),
        entry(listOf("a", "b", "2"), 21),
        entry(listOf("a", "b", "c"), 43),
        entry(listOf("a"), 13)
      )
  }

  @Test
  fun `test PrefixTreeSet#getDescendants`() {
    val tree = prefixTreeSetOf(
      listOf("a", "b", "c", "1"),
      listOf("a", "b", "c", "2"),
      listOf("a", "b", "c", "3"),
      listOf("a", "b", "c", "4"),
      listOf("a", "b", "1"),
      listOf("a", "b", "2"),
      listOf("a", "b", "3"),
      listOf("a", "b", "4"),
      listOf("a", "b", "c"),
      listOf("a")
    )
    Assertions.assertThat(tree.getDescendants(listOf("a", "b", "c")))
      .containsExactlyInAnyOrder(
        listOf("a", "b", "c", "1"),
        listOf("a", "b", "c", "2"),
        listOf("a", "b", "c", "3"),
        listOf("a", "b", "c", "4"),
        listOf("a", "b", "c")
      )
    Assertions.assertThat(tree.getDescendants(listOf("a", "b")))
      .containsExactlyInAnyOrder(
        listOf("a", "b", "c", "1"),
        listOf("a", "b", "c", "2"),
        listOf("a", "b", "c", "3"),
        listOf("a", "b", "c", "4"),
        listOf("a", "b", "1"),
        listOf("a", "b", "2"),
        listOf("a", "b", "3"),
        listOf("a", "b", "4"),
        listOf("a", "b", "c")
      )
    Assertions.assertThat(tree.getDescendants(listOf("a")))
      .containsExactlyInAnyOrder(
        listOf("a", "b", "c", "1"),
        listOf("a", "b", "c", "2"),
        listOf("a", "b", "c", "3"),
        listOf("a", "b", "c", "4"),
        listOf("a", "b", "1"),
        listOf("a", "b", "2"),
        listOf("a", "b", "3"),
        listOf("a", "b", "4"),
        listOf("a", "b", "c"),
        listOf("a")
      )
    Assertions.assertThat(tree.getDescendants(emptyList()))
      .containsExactlyInAnyOrder(
        listOf("a", "b", "c", "1"),
        listOf("a", "b", "c", "2"),
        listOf("a", "b", "c", "3"),
        listOf("a", "b", "c", "4"),
        listOf("a", "b", "1"),
        listOf("a", "b", "2"),
        listOf("a", "b", "3"),
        listOf("a", "b", "4"),
        listOf("a", "b", "c"),
        listOf("a")
      )
  }

  @Test
  fun `test PrefixTreeSet#getAncestors`() {
    val tree = prefixTreeSetOf(
      listOf("a", "b", "c", "2"),
      listOf("a", "b", "c", "1"),
      listOf("a", "b", "c", "3"),
      listOf("a", "b", "c", "4"),
      listOf("a", "b", "1"),
      listOf("a", "b", "2"),
      listOf("a", "b", "3"),
      listOf("a", "b", "4"),
      listOf("a", "b", "c"),
      listOf("a")
    )
    Assertions.assertThat(tree.getAncestors(listOf("a", "b", "c", "1", "loc")))
      .containsExactlyInAnyOrder(
        listOf("a"),
        listOf("a", "b", "c"),
        listOf("a", "b", "c", "1")
      )
    Assertions.assertThat(tree.getAncestors(listOf("a", "b", "c", "1")))
      .containsExactlyInAnyOrder(
        listOf("a"),
        listOf("a", "b", "c"),
        listOf("a", "b", "c", "1")
      )
    Assertions.assertThat(tree.getAncestors(listOf("a", "b", "c")))
      .containsExactlyInAnyOrder(
        listOf("a"),
        listOf("a", "b", "c")
      )
    Assertions.assertThat(tree.getAncestors(listOf("a", "b")))
      .containsExactlyInAnyOrder(
        listOf("a")
      )
    Assertions.assertThat(tree.getAncestors(listOf("a")))
      .containsExactlyInAnyOrder(
        listOf("a")
      )
    Assertions.assertThat(tree.getAncestors(emptyList()))
      .isEmpty()
  }

  @Test
  fun `test PrefixTreeSet#getRoots`() {
    Assertions.assertThat(
      prefixTreeSetOf(
        listOf("a", "b", "c"),
        listOf("a", "b", "c", "d"),
        listOf("a", "b", "c", "e"),
        listOf("a", "f", "g")
      ).getRoots()
    ).containsExactlyInAnyOrder(
      listOf("a", "b", "c"),
      listOf("a", "f", "g")
    )
    Assertions.assertThat(
      prefixTreeSetOf(
        listOf("a", "b"),
        listOf("a", "b", "c"),
        listOf("a", "b", "c", "d"),
        listOf("a", "b", "c", "e"),
        listOf("a", "f", "g")
      ).getRoots()
    ).containsExactlyInAnyOrder(
      listOf("a", "b"),
      listOf("a", "f", "g")
    )
    Assertions.assertThat(
      prefixTreeSetOf(
        listOf("a"),
        listOf("a", "b"),
        listOf("a", "b", "c"),
        listOf("a", "b", "c", "d"),
        listOf("a", "b", "c", "e"),
        listOf("a", "f", "g")
      ).getRoots()
    ).containsExactlyInAnyOrder(
      listOf("a")
    )
    Assertions.assertThat(
      prefixTreeSetOf(
        emptyList(),
        listOf("a"),
        listOf("a", "b"),
        listOf("a", "b", "c"),
        listOf("a", "b", "c", "d"),
        listOf("a", "b", "c", "e"),
        listOf("a", "f", "g")
      ).getRoots()
    ).containsExactlyInAnyOrder(
      emptyList()
    )
  }
}