// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.imageResIconDescriptor
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [SwingIcon]s of the same descriptor and scale are equal: `Presentation.getIcon` creates a new one on every call, and its
 * callers compare icons across calls (e.g. whether an action still shows its default icon).
 */
@TestApplication
class SwingIconEqualityTest {
  private val classLoader = SwingIconEqualityTest::class.java.classLoader

  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `icons of the same descriptor and scale are equal`() {
    val descriptor = imageResIconDescriptor("expui/general/add.svg", classLoader)

    assertEquals(SwingIcon(descriptor), SwingIcon(descriptor))
    assertEquals(SwingIcon(descriptor).hashCode(), SwingIcon(descriptor).hashCode())
    assertEquals(SwingIcon(descriptor, factor(2f)), SwingIcon(descriptor, factor(2f)))
    // the identity scale is no scale
    assertEquals(SwingIcon(descriptor), SwingIcon(descriptor, IconScale.Default))
  }

  @Test
  fun `icons of another descriptor or scale differ`() {
    val add = imageResIconDescriptor("expui/general/add.svg", classLoader)
    val remove = imageResIconDescriptor("expui/general/remove.svg", classLoader)

    assertNotEquals(SwingIcon(add), SwingIcon(remove))
    assertNotEquals(SwingIcon(add), SwingIcon(add, factor(2f)))
  }
}
