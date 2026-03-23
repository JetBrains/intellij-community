// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

internal class EditorSettingsRefactoringOptionsProviderImpl : EditorSettingsRefactoringOptionsProvider {
  override var isShowInlineLocalDialog: Boolean
    get() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog
    set(value) {
      EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog = value
    }
}
