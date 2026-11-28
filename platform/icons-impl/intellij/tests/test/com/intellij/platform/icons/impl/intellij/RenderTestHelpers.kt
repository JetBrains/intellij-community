// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.swing.createSwingIcon
import java.awt.image.BufferedImage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Renders the icon at [path] with [modifier] through the Swing path into a transparent ARGB image. */
internal fun render(path: String, modifier: IconModifier): BufferedImage {
  IntelliJIconManager.activate()
  return imageIconDescriptor(path, IntelliJIconManager::class.java.classLoader, modifier).createSwingIcon().paintToImage()
}

internal fun assertSameImage(expected: BufferedImage, actual: BufferedImage, message: String) {
  assertTrue(sameImage(expected, actual)) { message }
}

internal fun assertDifferentImage(a: BufferedImage, b: BufferedImage, message: String) {
  assertFalse(sameImage(a, b)) { message }
}
