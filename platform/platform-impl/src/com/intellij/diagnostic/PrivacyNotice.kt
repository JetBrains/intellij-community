// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.CollapsibleRow
import com.intellij.ui.dsl.builder.cleanupHtml
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import javax.swing.JEditorPane

class PrivacyNotice(@NlsContexts.Label label: String, @NlsContexts.Label privacyPolicy: String) {

  @JvmField
  val panel = panel {
    collapsibleRow = collapsibleGroup(label) {
      row {
        privacyPolicyPane = comment(privacyPolicy).component
      }
    }
  }

  var expanded: Boolean by collapsibleRow::expanded

  fun setPrivacyPolicy(@Nls text: String) {
    privacyPolicyPane.text = cleanupHtml(text)
  }

  private lateinit var collapsibleRow: CollapsibleRow
  private lateinit var privacyPolicyPane: JEditorPane
}
