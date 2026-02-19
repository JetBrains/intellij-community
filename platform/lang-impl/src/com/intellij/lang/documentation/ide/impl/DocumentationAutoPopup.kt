// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManagerListener

private class DocumentationAutoPopupListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (newLookup is LookupEx) {
      DocumentationManager.getInstance(newLookup.project).autoShowDocumentationOnItemChange(newLookup)
    }
  }
}
