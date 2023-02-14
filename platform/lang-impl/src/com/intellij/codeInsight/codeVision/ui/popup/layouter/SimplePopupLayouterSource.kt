package com.intellij.codeInsight.codeVision.ui.popup.layouter

import com.jetbrains.rd.util.lifetime.Lifetime

class SimplePopupLayouterSource(private val createLayouterDelegate: (Lifetime) -> DockingLayouter,
                                val layouterId: String) {
  fun createLayouter(lifetime: Lifetime): DockingLayouter {
    return createLayouterDelegate(lifetime)
  }
}