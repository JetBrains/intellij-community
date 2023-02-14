// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 *  Provider a way to disable particular kind of inlays (code vision, inlay provider, declarative inlays, parameters)
 *
 *  Used in hints toggling action.
 */
interface InlayHintsSwitch {
  companion object {
    private val EP: ExtensionPointName<InlayHintsSwitch> = ExtensionPointName("com.intellij.codeInsight.inlayHintsSwitch")

    fun isEnabled(project: Project) : Boolean {
      return EP.extensionList.any { it.isEnabled(project) }
    }

    fun setEnabled(project: Project, value: Boolean) {
      for (switch in EP.extensionList) {
        switch.setEnabled(project, value)
      }
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }

  /**
   * Whether a given inlay hints kind is enabled.
   */
  fun isEnabled(project: Project) : Boolean

  fun setEnabled(project: Project, value: Boolean)
}