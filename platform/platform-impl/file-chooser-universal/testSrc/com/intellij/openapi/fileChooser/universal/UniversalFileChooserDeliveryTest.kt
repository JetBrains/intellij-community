// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UniversalFileChooserDeliveryTest {
  private val chosenCalls = mutableListOf<List<String>>()
  private var cancelledCalls = 0

  private fun deliver(chosen: List<String>?) {
    UniversalFileChooser.deliverChosenFiles(chosen, onChosen = { chosenCalls.add(it) }, onCancelled = { cancelledCalls++ })
  }

  @Test
  fun `a non-empty selection goes to the callback`() {
    deliver(listOf("a", "b"))
    assertThat(chosenCalls).containsExactly(listOf("a", "b"))
    assertThat(cancelledCalls).isZero()
  }

  @Test
  fun `an empty selection does not go to the callback`() {
    deliver(emptyList())
    assertThat(chosenCalls).isEmpty()
    assertThat(cancelledCalls).isEqualTo(1)
  }

  @Test
  fun `a cancelled dialog does not go to the callback`() {
    deliver(null)
    assertThat(chosenCalls).isEmpty()
    assertThat(cancelledCalls).isEqualTo(1)
  }
}
