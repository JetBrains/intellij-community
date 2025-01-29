// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.util.io.CanonicalPathPrefixTree
import com.intellij.util.containers.prefix.map.asMutableMap
import com.intellij.util.containers.prefix.set.asMutableSet
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PrefixTreeTest {

  @Test
  fun `test map filling`() {
    val tree = CanonicalPathPrefixTree.asMutableMap(
      "/a/b/c/3" to 30,
      "/a/b/c/4" to 10,
      "/a/b/1" to 11,
      "/a/b/2" to 21,
      "/a/b/c" to 43,
      "/a" to 13
    )
    Assertions.assertEquals(6, tree.size)
    Assertions.assertEquals(tree["/a/b/c/1"], null)
    Assertions.assertEquals(tree["/a/b/c/2"], null)
    Assertions.assertEquals(tree["/a/b/c/3"], 30)
    Assertions.assertEquals(tree["/a/b/c/4"], 10)
    Assertions.assertEquals(tree["/a/b/1"], 11)
    Assertions.assertEquals(tree["/a/b/2"], 21)
    Assertions.assertEquals(tree["/a/b/3"], null)
    Assertions.assertEquals(tree["/a/b/4"], null)
    Assertions.assertEquals(tree["/a/b/c"], 43)
    Assertions.assertEquals(tree["/a"], 13)
    Assertions.assertFalse("/a/b/c/1" in tree)
    Assertions.assertFalse("/a/b/c/2" in tree)
    Assertions.assertTrue("/a/b/c/3" in tree)
    Assertions.assertTrue("/a/b/c/4" in tree)
    Assertions.assertTrue("/a/b/1" in tree)
    Assertions.assertTrue("/a/b/2" in tree)
    Assertions.assertFalse("/a/b/3" in tree)
    Assertions.assertFalse("/a/b/4" in tree)
    Assertions.assertTrue("/a/b/c" in tree)
    Assertions.assertTrue("/a" in tree)
    tree["/a/b/c/1"] = 10
    tree["/a/b/c/2"] = 20
    tree["/a/b/3"] = 30
    tree["/a/b/4"] = 11
    Assertions.assertEquals(10, tree.size)
    Assertions.assertEquals(tree["/a/b/c/1"], 10)
    Assertions.assertEquals(tree["/a/b/c/2"], 20)
    Assertions.assertEquals(tree["/a/b/c/3"], 30)
    Assertions.assertEquals(tree["/a/b/c/4"], 10)
    Assertions.assertEquals(tree["/a/b/1"], 11)
    Assertions.assertEquals(tree["/a/b/2"], 21)
    Assertions.assertEquals(tree["/a/b/3"], 30)
    Assertions.assertEquals(tree["/a/b/4"], 11)
    Assertions.assertEquals(tree["/a/b/c"], 43)
    Assertions.assertEquals(tree["/a"], 13)
    Assertions.assertTrue("/a/b/c/1" in tree)
    Assertions.assertTrue("/a/b/c/2" in tree)
    Assertions.assertTrue("/a/b/c/3" in tree)
    Assertions.assertTrue("/a/b/c/4" in tree)
    Assertions.assertTrue("/a/b/1" in tree)
    Assertions.assertTrue("/a/b/2" in tree)
    Assertions.assertTrue("/a/b/3" in tree)
    Assertions.assertTrue("/a/b/4" in tree)
    Assertions.assertTrue("/a/b/c" in tree)
    Assertions.assertTrue("/a" in tree)
    Assertions.assertEquals(setOf(10, 20, 30, 10, 11, 21, 30, 11, 43, 13), tree.values.toSet())
    Assertions.assertEquals(
      setOf(
        "/a/b/c/1",
        "/a/b/c/2",
        "/a/b/c/3",
        "/a/b/c/4",
        "/a/b/1",
        "/a/b/2",
        "/a/b/3",
        "/a/b/4",
        "/a/b/c",
        "/a"
      ),
      tree.keys
    )
  }

  @Test
  fun `test map removing`() {
    val tree = CanonicalPathPrefixTree.asMutableMap(
      "/a/b/c/1" to 10,
      "/a/b/c/2" to 20,
      "/a/b/c/3" to 30,
      "/a/b/c/4" to 10,
      "/a/b/1" to 11,
      "/a/b/2" to 21,
      "/a/b/3" to 30,
      "/a/b/4" to 11,
      "/a/b/c" to 43,
      "/a" to 13
    )
    Assertions.assertEquals(10, tree.size)
    Assertions.assertEquals(tree["/a/b/c/1"], 10)
    Assertions.assertEquals(tree["/a/b/c/2"], 20)
    Assertions.assertEquals(tree["/a/b/c/3"], 30)
    Assertions.assertEquals(tree["/a/b/c/4"], 10)
    Assertions.assertEquals(tree["/a/b/1"], 11)
    Assertions.assertEquals(tree["/a/b/2"], 21)
    Assertions.assertEquals(tree["/a/b/3"], 30)
    Assertions.assertEquals(tree["/a/b/4"], 11)
    Assertions.assertEquals(tree["/a/b/c"], 43)
    Assertions.assertEquals(tree["/a"], 13)
    Assertions.assertTrue("/a/b/c/1" in tree)
    Assertions.assertTrue("/a/b/c/2" in tree)
    Assertions.assertTrue("/a/b/c/3" in tree)
    Assertions.assertTrue("/a/b/c/4" in tree)
    Assertions.assertTrue("/a/b/1" in tree)
    Assertions.assertTrue("/a/b/2" in tree)
    Assertions.assertTrue("/a/b/3" in tree)
    Assertions.assertTrue("/a/b/4" in tree)
    Assertions.assertTrue("/a/b/c" in tree)
    Assertions.assertTrue("/a" in tree)
    tree.remove("/a/b/c/1")
    tree.remove("/a/b/c/2")
    tree.remove("/a/b/3")
    tree.remove("/a/b/4")
    Assertions.assertEquals(6, tree.size)
    Assertions.assertEquals(tree["/a/b/c/1"], null)
    Assertions.assertEquals(tree["/a/b/c/2"], null)
    Assertions.assertEquals(tree["/a/b/c/3"], 30)
    Assertions.assertEquals(tree["/a/b/c/4"], 10)
    Assertions.assertEquals(tree["/a/b/1"], 11)
    Assertions.assertEquals(tree["/a/b/2"], 21)
    Assertions.assertEquals(tree["/a/b/3"], null)
    Assertions.assertEquals(tree["/a/b/4"], null)
    Assertions.assertEquals(tree["/a/b/c"], 43)
    Assertions.assertEquals(tree["/a"], 13)
    Assertions.assertFalse("/a/b/c/1" in tree)
    Assertions.assertFalse("/a/b/c/2" in tree)
    Assertions.assertTrue("/a/b/c/3" in tree)
    Assertions.assertTrue("/a/b/c/4" in tree)
    Assertions.assertTrue("/a/b/1" in tree)
    Assertions.assertTrue("/a/b/2" in tree)
    Assertions.assertFalse("/a/b/3" in tree)
    Assertions.assertFalse("/a/b/4" in tree)
    Assertions.assertTrue("/a/b/c" in tree)
    Assertions.assertTrue("/a" in tree)
    Assertions.assertEquals(
      setOf(30, 10, 11, 21, 43, 13),
      tree.values.toSet()
    )
    Assertions.assertEquals(
      setOf(
        "/a/b/c/3",
        "/a/b/c/4",
        "/a/b/1",
        "/a/b/2",
        "/a/b/c",
        "/a"
      ),
      tree.keys
    )
  }

  @Test
  fun `test map containing nullable values`() {
    val tree = CanonicalPathPrefixTree.asMutableMap(
      "/a/b/c/1" to null,
      "/a/b/c/2" to 20,
      "/a/b/c/3" to null,
      "/a/b/c/4" to 10,
      "/a/b/1" to null,
      "/a/b/2" to 21,
      "/a/b/3" to null,
      "/a/b/4" to 11,
      "/a/b/c" to 43,
      "/a" to 13
    )
    Assertions.assertEquals(10, tree.size)
    Assertions.assertEquals(tree["/a/b/c/1"], null)
    Assertions.assertEquals(tree["/a/b/c/2"], 20)
    Assertions.assertEquals(tree["/a/b/c/3"], null)
    Assertions.assertEquals(tree["/a/b/c/4"], 10)
    Assertions.assertEquals(tree["/a/b/1"], null)
    Assertions.assertEquals(tree["/a/b/2"], 21)
    Assertions.assertEquals(tree["/a/b/3"], null)
    Assertions.assertEquals(tree["/a/b/4"], 11)
    Assertions.assertEquals(tree["/a/b/c"], 43)
    Assertions.assertEquals(tree["/a"], 13)
    Assertions.assertTrue("/a/b/c/1" in tree)
    Assertions.assertTrue("/a/b/c/2" in tree)
    Assertions.assertTrue("/a/b/c/3" in tree)
    Assertions.assertTrue("/a/b/c/4" in tree)
    Assertions.assertTrue("/a/b/1" in tree)
    Assertions.assertTrue("/a/b/2" in tree)
    Assertions.assertTrue("/a/b/3" in tree)
    Assertions.assertTrue("/a/b/4" in tree)
    Assertions.assertTrue("/a/b/c" in tree)
    Assertions.assertTrue("/a" in tree)
    tree.remove("/a/b/c/1")
    tree.remove("/a/b/c/2")
    tree.remove("/a/b/3")
    tree.remove("/a/b/4")
    Assertions.assertEquals(6, tree.size)
    Assertions.assertEquals(tree["/a/b/c/1"], null)
    Assertions.assertEquals(tree["/a/b/c/2"], null)
    Assertions.assertEquals(tree["/a/b/c/3"], null)
    Assertions.assertEquals(tree["/a/b/c/4"], 10)
    Assertions.assertEquals(tree["/a/b/1"], null)
    Assertions.assertEquals(tree["/a/b/2"], 21)
    Assertions.assertEquals(tree["/a/b/3"], null)
    Assertions.assertEquals(tree["/a/b/4"], null)
    Assertions.assertEquals(tree["/a/b/c"], 43)
    Assertions.assertEquals(tree["/a"], 13)
    Assertions.assertFalse("/a/b/c/1" in tree)
    Assertions.assertFalse("/a/b/c/2" in tree)
    Assertions.assertTrue("/a/b/c/3" in tree)
    Assertions.assertTrue("/a/b/c/4" in tree)
    Assertions.assertTrue("/a/b/1" in tree)
    Assertions.assertTrue("/a/b/2" in tree)
    Assertions.assertFalse("/a/b/3" in tree)
    Assertions.assertFalse("/a/b/4" in tree)
    Assertions.assertTrue("/a/b/c" in tree)
    Assertions.assertTrue("/a" in tree)
    Assertions.assertEquals(setOf(null, 10, null, 21, 43, 13), tree.values.toSet())
    Assertions.assertEquals(setOf(
      "/a/b/c/3",
      "/a/b/c/4",
      "/a/b/1",
      "/a/b/2",
      "/a/b/c",
      "/a"
    ), tree.keys)
  }

  @Test
  fun `test finding descendants`() {
    val tree = CanonicalPathPrefixTree.asMutableSet(
      "/a/b/c/1",
      "/a/b/c/2",
      "/a/b/c/3",
      "/a/b/c/4",
      "/a/b/1",
      "/a/b/2",
      "/a/b/3",
      "/a/b/4",
      "/a/b/c",
      "/a"
    )
    Assertions.assertEquals(
      setOf(
        "/a/b/c/1",
        "/a/b/c/2",
        "/a/b/c/3",
        "/a/b/c/4",
        "/a/b/c"
      ),
      tree.getDescendants("/a/b/c")
    )
    Assertions.assertEquals(
      setOf(
        "/a/b/c/1",
        "/a/b/c/2",
        "/a/b/c/3",
        "/a/b/c/4",
        "/a/b/1",
        "/a/b/2",
        "/a/b/3",
        "/a/b/4",
        "/a/b/c"
      ),
      tree.getDescendants("/a/b")
    )
    Assertions.assertEquals(
      setOf(
        "/a/b/c/1",
        "/a/b/c/2",
        "/a/b/c/3",
        "/a/b/c/4",
        "/a/b/1",
        "/a/b/2",
        "/a/b/3",
        "/a/b/4",
        "/a/b/c",
        "/a"
      ),
      tree.getDescendants("/a")
    )
    Assertions.assertEquals(
      setOf(
        "/a/b/c/1",
        "/a/b/c/2",
        "/a/b/c/3",
        "/a/b/c/4",
        "/a/b/1",
        "/a/b/2",
        "/a/b/3",
        "/a/b/4",
        "/a/b/c",
        "/a"
      ),
      tree.getDescendants("/")
    )
  }

  @Test
  fun `test finding ancestors`() {
    val tree = CanonicalPathPrefixTree.asMutableSet(
      "/a/b/c/2",
      "/a/b/c/1",
      "/a/b/c/3",
      "/a/b/c/4",
      "/a/b/1",
      "/a/b/2",
      "/a/b/3",
      "/a/b/4",
      "/a/b/c",
      "/a"
    )
    Assertions.assertEquals(setOf("/a", "/a/b/c", "/a/b/c/1"), tree.getAncestors("/a/b/c/1/loc"))
    Assertions.assertEquals(setOf("/a", "/a/b/c", "/a/b/c/1"), tree.getAncestors("/a/b/c/1"))
    Assertions.assertEquals(setOf("/a", "/a/b/c"), tree.getAncestors("/a/b/c"))
    Assertions.assertEquals(setOf("/a"), tree.getAncestors("/a/b"))
    Assertions.assertEquals(setOf("/a"), tree.getAncestors("/a"))
    Assertions.assertEquals(emptySet<String>(), tree.getAncestors("/"))
  }

  @Test
  fun `test finding roots`() {
    val set = CanonicalPathPrefixTree.asMutableSet(
      "/a/b/c",
      "/a/b/c/d",
      "/a/b/c/e",
      "/a/f/g"
    )
    Assertions.assertEquals(setOf("/a/b/c", "/a/f/g"), set.getRoots())
    set.add("/a/b")
    Assertions.assertEquals(setOf("/a/b", "/a/f/g"), set.getRoots())
    set.add("/a")
    Assertions.assertEquals(setOf("/a"), set.getRoots())
    set.add("/")
    Assertions.assertEquals(setOf("/"), set.getRoots())
  }

  @Test
  fun `test trailing slash`() {
    val tree = CanonicalPathPrefixTree.asMutableSet(
      "/a/b/c/d/1",
      "/a/b/c/d/2",
      "/a/b/c/d/3/",
      "/a/b/c/d/4/",
      "/a/b/c/e/1",
      "/a/b/c/e/2",
      "/a/b/c/e/3/",
      "/a/b/c/e/4/",
      "/a/b/c",
      "/a/b"
    )
    Assertions.assertTrue("/a/b/c/d/1/" in tree)
    Assertions.assertTrue("/a/b/c/d/2/" in tree)
    Assertions.assertTrue("/a/b/c/d/3/" in tree)
    Assertions.assertTrue("/a/b/c/d/4/" in tree)
    tree.remove("/a/b/c/d/3/")
    tree.remove("/a/b/c/d/4/")
    Assertions.assertTrue("/a/b/c/d/1/" in tree)
    Assertions.assertTrue("/a/b/c/d/2/" in tree)
    Assertions.assertTrue("/a/b/c/d/3/" !in tree)
    Assertions.assertTrue("/a/b/c/d/4/" !in tree)

    Assertions.assertEquals(setOf("/a/b/c", "/a/b"), tree.getAncestors("/a/b/c/d/e/"))

    Assertions.assertEquals(
      setOf(
        "/a/b/c/d/1",
        "/a/b/c/d/2",
        "/a/b/c/e/1",
        "/a/b/c/e/2",
        "/a/b/c/e/3/",
        "/a/b/c/e/4/",
        "/a/b/c",
      ),
      tree.getDescendants("/a/b/c/")
    )

    Assertions.assertEquals(setOf("/a/b"), tree.getRoots())
  }

  @Test
  fun `test file protocol`() {
    val tree = CanonicalPathPrefixTree.asMutableSet(
      "rd://a/b",
      "rd://a/b/c",
      "rd://a/b/c/d",
      "fsd://a/b",
      "fsd://a/b/c",
      "fsd://a/b/c/d",
    )

    Assertions.assertEquals(setOf("rd://a/b", "fsd://a/b"), tree.getRoots())

    Assertions.assertEquals(
      setOf(
        "rd://a/b",
        "rd://a/b/c",
        "rd://a/b/c/d"
      ),
      tree.getDescendants("rd:")
    )
    Assertions.assertEquals(
      setOf(
        "rd://a/b",
        "rd://a/b/c",
        "rd://a/b/c/d"
      ),
      tree.getDescendants("rd:/")
    )
    Assertions.assertEquals(
      setOf(
        "rd://a/b",
        "rd://a/b/c",
        "rd://a/b/c/d"
      ),
      tree.getDescendants("rd://")
    )

    Assertions.assertEquals(
      setOf(
        "fsd://a/b",
        "fsd://a/b/c",
        "fsd://a/b/c/d"
      ),
      tree.getAncestors("fsd://a/b/c/d")
    )
    Assertions.assertEquals(
      setOf(
        "fsd://a/b",
        "fsd://a/b/c"
      ),
      tree.getAncestors("fsd://a/b/c")
    )
  }
}