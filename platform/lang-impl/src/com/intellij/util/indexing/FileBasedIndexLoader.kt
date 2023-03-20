// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.ApplicationInitializedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class FileBasedIndexLoader : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    withContext(Dispatchers.IO) {
      (FileBasedIndex.getInstance() as FileBasedIndexImpl).loadIndexes()
    }
  }
}