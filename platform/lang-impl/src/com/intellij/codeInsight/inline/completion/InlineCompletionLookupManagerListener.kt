// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener

class InlineCompletionLookupManagerListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    newLookup?.addLookupListener(object : LookupListener {
      override fun currentItemChanged(event: LookupEvent) {
        if (!event.lookup.isFocused || event.item == null) {
          return
        }
        val listener = event.lookup.editor.getUserData(InlineCompletionHandler.KEY) ?: return
        listener.invoke(event)
      }

      override fun lookupCanceled(event: LookupEvent) {
        // TODO: Try to continue inline completion
      }

      override fun lookupShown(event: LookupEvent) {
        // TODO: Strip current inline completion text to one-line
      }
    })
  }
}
