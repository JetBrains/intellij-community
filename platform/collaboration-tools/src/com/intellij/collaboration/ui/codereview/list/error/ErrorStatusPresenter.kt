// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.error

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import javax.swing.Action

sealed interface ErrorStatusPresenter<T> {
  @Deprecated(
    "Moved to ErrorStatusPresenter.Text",
    ReplaceWith(
      expression = "ErrorStatusPresenter.TextErrorStatusPresenter.getErrorTitle",
      imports = ["com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter.Text"]
    )
  )
  fun getErrorTitle(error: T): @Nls String

  fun getErrorAction(error: T): Action?

  interface Text<T> : ErrorStatusPresenter<T> {
    override fun getErrorTitle(error: T): @Nls String
    fun getErrorDescription(error: T): @Nls String?
  }

  interface HTML<T> : ErrorStatusPresenter<T> {
    fun getHTMLBody(error: T): @NlsSafe String
  }

  companion object {
    const val ERROR_ACTION_HREF: String = "ERROR_ACTION"
  }
}