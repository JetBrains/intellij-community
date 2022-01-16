// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.EmptyDocumentationTarget
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal fun CoroutineScope.updateFromRequests(requests: Flow<DocumentationRequest?>, browser: DocumentationBrowser) {
  launch(Dispatchers.Default) {
    requests.collectLatest {
      val request = it ?: EmptyDocumentationTarget.request
      browser.resetBrowser(request)
    }
  }
}

internal fun CoroutineScope.handleUserAction(action: suspend CoroutineScope.() -> Unit) {
  EDT.assertIsEdt()
  launch(
    context = Dispatchers.EDT + ModalityState.current().asContextElement(),
    start = CoroutineStart.UNDISPATCHED,
    block = action
  )
}
