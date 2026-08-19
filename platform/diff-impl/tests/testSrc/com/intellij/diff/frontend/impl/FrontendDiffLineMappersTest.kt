// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.diff.frontend.FrontendDiffLineLocation
import com.intellij.diff.frontend.FrontendUnifiedDiffMapping
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.mockito.Mockito

internal class FrontendDiffLineMappersTest : TestCase() {
  fun testDirectMapperWithoutOtherSide() {
    val document = DocumentImpl("first\nsecond")
    val leftMapper = FrontendDirectDiffLineMapper(document, Side.LEFT)
    val rightMapper = FrontendDirectDiffLineMapper(document, Side.RIGHT)

    assertEquals(1, leftMapper.locationToLine(FrontendDiffLineLocation(Side.LEFT, 1)))
    assertNull(leftMapper.locationToLine(FrontendDiffLineLocation(Side.RIGHT, 1)))
    assertEquals(FrontendDiffLineLocation(Side.LEFT, 1), leftMapper.lineToLocation(1))
    assertNull(leftMapper.lineToLocation(-1))
    assertNull(leftMapper.lineToLocation(2))
    assertEquals(1 to -1, leftMapper.lineToUnified(1))
    assertEquals(-1 to 1, rightMapper.lineToUnified(1))
    assertTrue(leftMapper.isAvailable)
  }

  fun testDirectMapperUsesOtherSideMapping() {
    val mapping = TestTwoSideDiffMapping(isAvailable = true) { line -> line + 10 }
    val mapper = FrontendDirectDiffLineMapper(DocumentImpl("first\nsecond"), Side.RIGHT, mapping)

    assertTrue(mapper.isAvailable)
    assertEquals(11 to 1, mapper.lineToUnified(1))
  }

  fun testDirectMapperWithUnavailableOtherSideMapping() {
    val mapping = TestTwoSideDiffMapping(isAvailable = false) { line -> line + 10 }
    val mapper = FrontendDirectDiffLineMapper(DocumentImpl("first\nsecond"), Side.RIGHT, mapping)

    assertFalse(mapper.isAvailable)
    assertEquals(-1 to 1, mapper.lineToUnified(1))
    // the own side of the mapper does not depend on the other side being in sync
    assertEquals(FrontendDiffLineLocation(Side.RIGHT, 1), mapper.lineToLocation(1))
    assertEquals(1, mapper.locationToLine(FrontendDiffLineLocation(Side.RIGHT, 1)))
  }

  fun testDirectMapperNotifiesAboutOtherSideMappingChanges() {
    val mapping = TestTwoSideDiffMapping(isAvailable = true) { line -> line }
    val mapper = FrontendDirectDiffLineMapper(DocumentImpl("first\nsecond"), Side.LEFT, mapping)
    val disposable = Disposer.newDisposable()
    try {
      var notifications = 0
      mapper.addListener(disposable) { notifications++ }

      mapping.notifyListeners()
      assertEquals(1, notifications)

      Disposer.dispose(disposable)
      mapping.notifyListeners()
      assertEquals(1, notifications)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  fun testUnifiedMapperDelegatesToUnifiedMapping() {
    val mapping = Mockito.mock(FrontendUnifiedDiffMapping::class.java)
    Mockito.`when`(mapping.isAvailable).thenReturn(true)
    Mockito.`when`(mapping.sideLineToUnified(Side.RIGHT, 3)).thenReturn(13)
    Mockito.`when`(mapping.unifiedLineToSideLines(1)).thenReturn(4 to 5)
    val mapper = FrontendUnifiedDiffLineMapper(DocumentImpl("first\nsecond"), mapping) { line ->
      FrontendDiffLineLocation(Side.RIGHT, line + 20)
    }

    assertTrue(mapper.isAvailable)
    assertEquals(13, mapper.locationToLine(FrontendDiffLineLocation(Side.RIGHT, 3)))
    assertEquals(FrontendDiffLineLocation(Side.RIGHT, 21), mapper.lineToLocation(1))
    assertNull(mapper.lineToLocation(2))
    assertEquals(4 to 5, mapper.lineToUnified(1))
  }
}

private class TestTwoSideDiffMapping(
  override val isAvailable: Boolean,
  private val otherSideLine: (Int) -> Int,
) : FrontendTwoSideDiffMapping {
  private val listeners = mutableListOf<() -> Unit>()

  override val revision: Long = 1

  override fun addListener(parentDisposable: Disposable, listener: () -> Unit) {
    listeners += listener
    Disposer.register(parentDisposable, Disposable { listeners.remove(listener) })
  }

  override fun mapOtherSide(side: Side, line: Int): Int = if (isAvailable) otherSideLine(line) else -1

  fun notifyListeners() {
    listeners.toList().forEach { it() }
  }
}
