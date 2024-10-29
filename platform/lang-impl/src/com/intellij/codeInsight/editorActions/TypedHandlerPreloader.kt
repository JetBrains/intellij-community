// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions

import com.intellij.ide.ApplicationActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException

private class TypedHandlerPreloader : ApplicationActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    TypedHandlerDelegate.EP_NAME.extensionList
  }
}