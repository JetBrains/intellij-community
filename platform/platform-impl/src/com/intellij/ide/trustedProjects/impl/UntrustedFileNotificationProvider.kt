// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shows the "safe mode" banner on editors of files opened in the safe mode inside a trusted project's frame
 * (local files outside the project's trusted roots, see [TrustedFiles]).
 *
 * When the hosting project itself isn't trusted, [UntrustedProjectNotificationProvider] owns the editor banner instead.
 */
internal class UntrustedFileNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!TrustedProjects.isProjectTrusted(project)) return null
    if (TrustedFiles.isTrusted(file, project)) return null
    val filePath = file.fileSystem.getNioPath(file) ?: return null

    return Function { fileEditor ->
      UntrustedFileEditorNotificationPanel(project, fileEditor) {
        TrustedProjectsDialog.confirmTrustingUntrustedFile(project, filePath)
      }
    }
  }

  internal class TrustedListener : TrustedProjectsListener {
    // per-file trust is granted for a plain path without a project, so the LocatedProject overrides are needed:
    // the Project-based default implementations are not called when locatedProject.project == null
    override fun onProjectTrusted(locatedProject: LocatedProject) {
      EditorNotifications.updateAll()
    }

    override fun onProjectUntrusted(locatedProject: LocatedProject) {
      EditorNotifications.updateAll()
    }
  }
}
