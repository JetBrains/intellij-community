// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.completion

import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

abstract class AbstractTextCompletionContributor<C : JComponent> : TextCompletionContributor<C> {

  private val chooseListeners = CopyOnWriteArrayList<(C, TextCompletionInfo) -> Unit>()

  final override fun fireVariantChosen(owner: C, variant: TextCompletionInfo) {
    chooseListeners.forEach { it(owner, variant) }
  }

  final override fun whenVariantChosen(action: (C, TextCompletionInfo) -> Unit) {
    chooseListeners.add(action)
  }
}