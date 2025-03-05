// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions

import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.text.MatcherHolder
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.ListCellRenderer

@ApiStatus.Internal
internal fun cellRenderer(): ListCellRenderer<Any?> = listCellRenderer<Any?> {
  selectionColor = if (selected) JBUI.CurrentTheme.List.Selection.background(true) else null

  when (value) {
    is InspectionElement -> {
      val tool = (value as InspectionElement).toolWrapper

      val matcher = MatcherHolder.getAssociatedMatcher(list) as? MinusculeMatcher

      icon(tool.icon)
      text(tool.displayName) {
        speedSearch {
          ranges = matcher?.matchingFragments(tool.displayName)
        }
      }

      @Suppress("HardCodedStringLiteral")
      val pathText = tool.groupPath.joinToString(" | ")
      text(pathText) {
        align = LcrInitParams.Align.RIGHT
        attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, greyForeground)
        speedSearch {
          ranges = matcher?.matchingFragments(pathText)
        }
      }
    }
    else -> {
      @Suppress("HardCodedStringLiteral")
      text(value?.toString() ?: "")
    }
  }
}

private val InspectionToolWrapper<*, *>.icon: Icon
  get() = Language.findLanguageByID(language)?.associatedFileType?.icon ?: UnknownFileType.INSTANCE.icon
