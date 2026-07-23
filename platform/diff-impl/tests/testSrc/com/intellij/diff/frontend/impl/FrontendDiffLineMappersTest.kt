// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.frontend.impl

import com.intellij.diff.frontend.FrontendDiffLineLocation
import com.intellij.diff.frontend.FrontendUnifiedDiffMapping
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.DocumentImpl
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
  }

  fun testDirectMapperUsesOtherSideMapping() {
    val mapper = FrontendDirectDiffLineMapper(DocumentImpl("first\nsecond"), Side.RIGHT) { line -> line + 10 }

    assertEquals(11 to 1, mapper.lineToUnified(1))
  }

  fun testUnifiedMapperDelegatesToUnifiedMapping() {
    val mapping = Mockito.mock(FrontendUnifiedDiffMapping::class.java)
    Mockito.`when`(mapping.sideLineToUnified(Side.RIGHT, 3)).thenReturn(13)
    Mockito.`when`(mapping.unifiedLineToSideLines(1)).thenReturn(4 to 5)
    val mapper = FrontendUnifiedDiffLineMapper(DocumentImpl("first\nsecond"), mapping) { line ->
      FrontendDiffLineLocation(Side.RIGHT, line + 20)
    }

    assertEquals(13, mapper.locationToLine(FrontendDiffLineLocation(Side.RIGHT, 3)))
    assertEquals(FrontendDiffLineLocation(Side.RIGHT, 21), mapper.lineToLocation(1))
    assertNull(mapper.lineToLocation(2))
    assertEquals(4 to 5, mapper.lineToUnified(1))
  }
}
