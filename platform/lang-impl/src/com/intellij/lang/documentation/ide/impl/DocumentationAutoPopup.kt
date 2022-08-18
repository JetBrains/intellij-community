// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.QuickDocUtil.isDocumentationV2Enabled
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity

internal class DocumentationAutoPopup : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    project.messageBus.connect().subscribe(LookupManagerListener.TOPIC, DocumentationAutoPopupListener())
  }
}

private class DocumentationAutoPopupListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (newLookup is LookupEx && isDocumentationV2Enabled()) {
      DocumentationManager.instance(newLookup.project).autoShowDocumentationOnItemChange(newLookup)
    }
  }
}
