// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction

interface AsyncFilesChangesProvider : FilesChangesProvider, FilesChangesListener {
  /**
   * Subscribes to changes in files that are be defined by [collectRelevantFiles]
   * Functions of [listener] are called on UI thread
   *
   * @see [com.intellij.openapi.application.NonBlockingReadAction.finishOnUiThread]
   */
  override fun subscribe(listener: FilesChangesListener, parentDisposable: Disposable)
}