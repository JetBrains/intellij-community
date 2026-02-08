// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object FileEditorClientUtils {
  @JvmStatic
  fun getClientId(fileEditor: FileEditor): ClientId? = CLIENT_ID.get(fileEditor)

  @JvmStatic
  fun assignClientId(fileEditor: FileEditor, clientId: ClientId?): Unit = CLIENT_ID.set(fileEditor, clientId)

  private val CLIENT_ID = Key.create<ClientId>("CLIENT_ID")
}