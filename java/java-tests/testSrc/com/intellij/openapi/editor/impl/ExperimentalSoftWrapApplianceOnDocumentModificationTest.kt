// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.util.registry.Registry

internal class ExperimentalSoftWrapApplianceOnDocumentModificationTest : SoftWrapApplianceOnDocumentModificationTest() {
  override fun setUp() {
    super.setUp()
    Registry.get("editor.use.new.soft.wraps.impl").setValue(true, getTestRootDisposable())
    Registry.get("editor.custom.soft.wraps.support.enabled").setValue(true, getTestRootDisposable())
  }

  fun testCustomWrapWithSoftWrapsDisabled() {
    val text = "0123456789"
    init(100, text)

    val settings = editor.getSettings()
    settings.setUseSoftWraps(false)

    val wrapOffset = 5
    assertNotNull(editor.customWrapModel.addWrap(wrapOffset, 0, 0))
    assertNotNull(editor.softWrapModel.getSoftWrap(wrapOffset))
    (editor as EditorImpl).validateState()
  }
}
