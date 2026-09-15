// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.retype

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

interface RetypeFileAssistant {
  fun acceptLookupElement(element: LookupElement): Boolean
  fun retypeDone(editor: Editor) {
  }

  @ApiStatus.Internal
  companion object {
    val EP_NAME: ExtensionPointName<RetypeFileAssistant> = ExtensionPointName.create("com.intellij.retypeFileAssistant")
  }
}
