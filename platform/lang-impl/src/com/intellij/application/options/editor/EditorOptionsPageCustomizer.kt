// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface EditorOptionsPageCustomizer {
  fun shouldHideRefactoringsSection(): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<EditorOptionsPageCustomizer> = ExtensionPointName("com.intellij.generalEditorOptionsCustomizer")
  }
}