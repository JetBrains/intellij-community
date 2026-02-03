// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection

/**
 * Provides an action to copy the external tool download command to the clipboard.
 */
public class ExternalJavaConfigurationCopyCommandAction : ExternalJavaConfigurationMissingAction {
  override fun <T> createAction(project: Project, provider: ExternalJavaConfigurationProvider<T>, releaseData: T): AnAction? {
    val command = provider.getDownloadCommandFor(releaseData) ?: return null
    return object : AnAction(JavaBundle.message("external.java.configuration.copy.command") , null, AllIcons.Actions.Copy) {
      override fun actionPerformed(e: AnActionEvent) {
        CopyPasteManager.getInstance().setContents(StringSelection(command))
      }
    }
  }
}
