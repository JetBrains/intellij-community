// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion.ml

import com.intellij.codeInsight.completion.ml.JavaCompletionFeatures.FqnTrie
import org.junit.Test
import kotlin.test.assertEquals

class JavaCompletionFeaturesTest {
  @Test
  fun `test on fqn trie`() {
    val trie = FqnTrie.create()
    trie.addFqn("com.intellij.java")
    trie.addFqn("com.intellij.completion")
    trie.addFqn("com.intellij.completion.ml")
    trie.addFqn("org.jetbrains.util")

    assertEquals(4, trie.matchedParts("com.intellij.completion.ml"))
    assertEquals(3, trie.matchedParts("com.intellij.java.analysis"))
    assertEquals(2, trie.matchedParts("com.intellij.analysis"))
    assertEquals(1, trie.matchedParts("org.apache.ant"))
    assertEquals(0, trie.matchedParts("java.lang"))
  }

  @Test
  fun `test on corner cases of fqn trie`() {
    assertEquals(0, FqnTrie.create().matchedParts("anything"))
    assertEquals(0, FqnTrie.withFqn("").matchedParts("anything"))
    assertEquals(0, FqnTrie.withFqn("com.intellij.java").matchedParts(""))
    assertEquals(0, FqnTrie.withFqn("com.intellij.java").matchedParts("..."))
    assertEquals(0, FqnTrie.withFqn("com.intellij.java").matchedParts(".com"))
    assertEquals(1, FqnTrie.withFqn("com.intellij.java").matchedParts("com."))
  }
}