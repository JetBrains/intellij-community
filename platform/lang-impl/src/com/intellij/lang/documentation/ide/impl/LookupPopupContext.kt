// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import java.awt.Component

internal class LookupPopupContext(val lookup: LookupEx) : SecondaryPopupContext() {

  override val referenceComponent: Component get() = lookup.component

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    super.setUpPopup(popup, popupUI)
    popupUI.coroutineScope.updateFromLookup(popupUI.browser, lookup)
    cancelPopupWhenLookupIsClosed(lookup, popup)
  }
}

private fun cancelPopupWhenLookupIsClosed(lookup: Lookup, popup: AbstractPopup) {
  val listener = object : LookupListener {
    override fun itemSelected(event: LookupEvent): Unit = lookupClosed()
    override fun lookupCanceled(event: LookupEvent): Unit = lookupClosed()
    private fun lookupClosed(): Unit = popup.cancel()
  }
  lookup.addLookupListener(listener)
  Disposer.register(popup) {
    lookup.removeLookupListener(listener)
  }
}
