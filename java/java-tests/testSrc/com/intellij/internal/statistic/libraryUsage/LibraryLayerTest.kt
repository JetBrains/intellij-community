// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import junit.framework.TestCase

class LibraryLayerTest : TestCase() {
  fun test() {
    val layer = LibraryLayer.create(
      listOf(
        createDescriptor("a"),
        createDescriptor("a.b.c.d"),
        createDescriptor("b.c.d"),
      )
    )

    assertEquals("aLibrary", layer.findSuitableLibrary("a.Class"))
    assertEquals("aLibrary", layer.findSuitableLibrary("a.foo.doo.F"))
    assertEquals("aLibrary", layer.findSuitableLibrary("a.b.Moo"))
    assertEquals("aLibrary", layer.findSuitableLibrary("a.b.c"))
    assertEquals("aLibrary", layer.findSuitableLibrary("a.b.c.e"))
    assertEquals("a.b.c.dLibrary", layer.findSuitableLibrary("a.b.c.d.*"))
    assertEquals("a.b.c.dLibrary", layer.findSuitableLibrary("a.b.c.d"))
    assertEquals("a.b.c.dLibrary", layer.findSuitableLibrary("a.b.c.d.fooo"))
    assertEquals("b.c.dLibrary", layer.findSuitableLibrary("b.c.d.fooo"))
    assertNull(layer.findSuitableLibrary("b.foo"))
    assertNull(layer.findSuitableLibrary(""))
  }
}

private fun createDescriptor(prefix: String) = LibraryDescriptor(libraryName = prefix + "Library", packagePrefix = prefix)