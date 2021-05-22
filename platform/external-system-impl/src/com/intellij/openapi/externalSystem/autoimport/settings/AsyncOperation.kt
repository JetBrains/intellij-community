// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.impl.CoreProgressManager

interface AsyncOperation<R> {

  fun isBlocking(): Boolean =
    ApplicationManager.getApplication().isHeadlessEnvironment &&
    !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()

  /**
   * Synchronously calculates a operation's result.
   */
  fun calculate(): R

  /**
   * Asynchronously calculates a operation's result.
   * If [isBlocking] is true then [callback] should be called before when [submit] function is ended.
   */
  fun submit(callback: (R) -> Unit, parentDisposable: Disposable)
}