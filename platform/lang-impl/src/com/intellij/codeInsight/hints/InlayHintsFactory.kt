// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus


class InlayHintsFactory {

  companion object {
    @ApiStatus.Internal
    fun clearModificationStamp(editor: Editor) {
      InlayHintsPassFactoryInternal.clearModificationStamp(editor)
    }

    @ApiStatus.Internal
    fun forceHintsUpdateOnNextPass() {
      InlayHintsPassFactoryInternal.forceHintsUpdateOnNextPass()
    }
  }
}