package com.intellij.database.connection.throwable.info

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create

interface RuntimeErrorActionProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<RuntimeErrorActionProvider> = create("com.intellij.database.runtimeErrorFixProvider")

    @JvmStatic
    fun getProviders() = EP_NAME.extensionList
  }

  fun createAction(error: ErrorInfo): ErrorInfo.Fix?
}