// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import kotlinx.coroutines.CoroutineScope

private const val IDE_AUTO_POPUP_QUICK_DOC_INITIALIZED = "ide.auto-popup.quick-doc.initialized"

class QuickDocAutoPopupInEAPInitializer : ApplicationInitializedListener {
  init {
    if (ApplicationManager.getApplication().let {
        it.isUnitTestMode || it.isHeadlessEnvironment || !(it.isEAP || it.isInternal)
      }) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    RunOnceUtil.runOnceForApp(IDE_AUTO_POPUP_QUICK_DOC_INITIALIZED) {
      CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO = true
    }
  }
}