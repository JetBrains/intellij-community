// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.schemes

import com.intellij.ide.IdeBundle
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.ListCellRenderer

internal fun <T : Scheme?> getRenderer(owner: SchemesCombo<T>): ListCellRenderer<SchemesCombo.MySchemeListItem<T>?> {
  return listCellRenderer("") {
    val scheme = value.getScheme()
    if (scheme == null) {
      text("")
      return@listCellRenderer
    }

    if (owner.schemeSeparators.containsKey(scheme)) {
      separator { text = owner.schemeSeparators[scheme] }
    }

    val indent = if (index < 0) 0 else owner.getIndent(scheme)
    for (i in 1..indent) {
      text(" ")
    }

    text(StringUtil.shortenTextWithEllipsis(value.presentableText, 100, 20)) {
      attributes = owner.getSchemeAttributes(scheme)
    }

    if (owner.isDefaultScheme(scheme)) {
      text(IdeBundle.message("scheme.theme.default")) {
        foreground = greyForeground
      }
    }

    if (index == -1 && owner.supportsProjectSchemes()) {
      text(if (owner.isProjectScheme(scheme)) SchemesCombo.PROJECT_LEVEL.get() else SchemesCombo.IDE_LEVEL.get()) {
        foreground = greyForeground
      }
    }
  }
}
