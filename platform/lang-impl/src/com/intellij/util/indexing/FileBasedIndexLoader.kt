// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import kotlinx.coroutines.CoroutineScope

private class FileBasedIndexLoader : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      serviceAsync<FileBasedIndex>().loadIndexes()
    }
  }
}