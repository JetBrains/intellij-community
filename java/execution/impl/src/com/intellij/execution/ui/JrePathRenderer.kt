// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.JrePathEditor.JreComboBoxItem
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.ListCellRenderer

@NlsSafe
private const val JAVA = "java"

internal fun createJrePathRenderer(): ListCellRenderer<JreComboBoxItem?> {
  val monospacedFont = CommonParameterFragments.getMonospaced()

  return listCellRenderer("") {
    font = monospacedFont
    copyWholeRow = true

    val pathOrName = value.getPathOrName()
    val description = value.description

    when {
      BundledJreProvider.BUNDLED == value.getPresentableText() -> {
        if (index == -1) {
          text(JAVA)
        }

        text(ExecutionBundle.message("bundled.jre.name")) {
          foreground = greyForeground
        }
      }

      pathOrName == null && value.version == null -> {
        text(description ?: "")
      }

      index == -1 -> {
        text(JAVA)

        val shortVersion = appendShortVersion(value)
        if (pathOrName != null && pathOrName != shortVersion) {
          text(pathOrName) {
            foreground = greyForeground
          }
        }
        else if (description != null) {
          text(description) {
            foreground = greyForeground
          }
        }
      }

      else -> {
        if (pathOrName != null) {
          text(pathOrName)
        }
        else {
          appendShortVersion(value)
        }

        if (description != null) {
          text(description) {
            foreground = greyForeground
          }
        }
      }
    }
  }
}

private fun LcrRow<JreComboBoxItem>.appendShortVersion(value: JreComboBoxItem): @NlsSafe String? {
  val versionString = value.version ?: return null
  val result = JavaSdkVersion.fromVersionString(versionString)?.description

  if (result != null) {
    text(result)
  }

  return result
}
