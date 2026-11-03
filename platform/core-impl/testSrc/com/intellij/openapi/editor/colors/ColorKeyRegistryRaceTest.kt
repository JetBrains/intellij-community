// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A key that a color scheme reads by name, with [ColorKey.find], and that a class creates with its default color at the
 * same time. `ScrollBarPainter.BACKGROUND` lost its default color this way while the IDE loaded its schemes, and every
 * scroll pane of that process then failed its assertion.
 */
internal class ColorKeyRegistryRaceTest {
  @Test
  fun `a key created with a default color keeps it while a scheme finds the key by name`() {
    repeat(ROUNDS) { round ->
      val name = "ColorKeyRegistryRaceTest.$round"
      // The scheme read the key first, so the registry holds it with no default color.
      ColorKey.find(name)
      val stop = AtomicBoolean()
      val reader = thread(name = "scheme reader") {
        while (!stop.get()) ColorKey.find(name)
      }
      try {
        assertEquals(DEFAULT, ColorKey.createColorKey(name, DEFAULT).defaultColor, "the default color of '$name'")
      }
      finally {
        stop.set(true)
        reader.join()
      }
    }
  }

  @Test
  fun `a key created with a fallback keeps it while a scheme finds the key by name`() {
    val fallback = ColorKey.createColorKey("ColorKeyRegistryRaceTest.fallback", DEFAULT)
    repeat(ROUNDS) { round ->
      val name = "ColorKeyRegistryRaceTest.withFallback.$round"
      ColorKey.find(name)
      val stop = AtomicBoolean()
      val reader = thread(name = "scheme reader") {
        while (!stop.get()) ColorKey.find(name)
      }
      try {
        assertEquals(fallback, ColorKey.createColorKeyWithFallback(name, fallback).fallbackColorKey, "the fallback of '$name'")
      }
      finally {
        stop.set(true)
        reader.join()
      }
    }
  }

  private companion object {
    const val ROUNDS = 2_000
    val DEFAULT: Color = Color(0xF5, 0xF5, 0xF5)
  }
}
