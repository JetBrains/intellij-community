// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.error

import org.jetbrains.annotations.Nls
import javax.swing.Action

interface ErrorStatusPresenter<T> {
  fun getErrorTitle(error: T): @Nls String

  fun getErrorDescription(error: T): @Nls String?

  fun getErrorAction(error: T): Action?
}