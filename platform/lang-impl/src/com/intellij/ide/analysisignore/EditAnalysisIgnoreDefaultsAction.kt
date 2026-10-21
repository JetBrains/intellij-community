// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.CommonBundle
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.EditCustomSettingsAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.IoErrorText
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.SwingUtilities

private const val ACTION_ID: String = "EditAnalysisIgnoreDefaults"

/**
 * Opens the [defaults file][AnalysisIgnoreDefaultsFile] in the editor. If the file is absent, the action first creates it from the
 * [template][AnalysisIgnoreDefaultsFile.template]. The action is visible only while the [defaults][AnalysisIgnoreDefaults.areEnabled]
 * are on.
 */
internal class EditAnalysisIgnoreDefaultsAction : EditCustomSettingsAction() {
  override fun file(): Path = AnalysisIgnoreDefaultsFile.getInstance().path

  override fun template(): String = AnalysisIgnoreDefaultsFile.getInstance().template()

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.presentation.isEnabled && AnalysisIgnoreDefaults.areEnabled()
  }

  override fun actionPerformed(e: AnActionEvent) {
    // The base action writes the file into an existing directory only. A new config directory can lack the options directory.
    val directory = file().parent
    try {
      Files.createDirectories(directory)
    }
    catch (ex: IOException) {
      logger<EditAnalysisIgnoreDefaultsAction>().warn(directory.toString(), ex)
      Messages.showErrorDialog(e.project, IdeBundle.message("file.write.error.details", directory, IoErrorText.message(ex)),
                               CommonBundle.getErrorTitle())
      return
    }
    super.actionPerformed(e)
    // The dialog of the Welcome screen writes the file past the VFS, and thus no listener reads it.
    AnalysisIgnoreDefaultsFile.getInstance().reload()
  }

  /** Lets the user edit the defaults file without the warning about a file outside the project. */
  class AccessExtension : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile): Boolean = AnalysisIgnoreDefaultsFile.getInstance().isDefaultsFile(file)
  }
}

/**
 * Returns a panel with [content] and, below it, a link that opens the [defaults file][AnalysisIgnoreDefaultsFile] in the editor. In a
 * Settings window, the link first applies the changes and closes the window, so that the editor is not behind the window.
 */
@ApiStatus.Internal
fun addEditAnalysisIgnoreDefaultsLink(content: JComponent): JComponent = panel {
  row {
    cell(content).align(Align.FILL)
  }.resizableRow()
  row {
    link(LangBundle.message("analysis.ignore.settings.default.exclusions.link")) { event -> openDefaultsFile(event.source as JComponent) }
  }
}

private fun openDefaultsFile(link: JComponent) {
  // The Settings dialog of the Welcome screen has the default project. The action then shows its own dialog.
  val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(link))?.takeUnless { it.isDefault }

  // The default button of a Settings window is OK: it applies the changes and closes the window. The modal dialog and the non-modal window
  // both have one. The Settings tool window is a part of the IDE frame, and the frame has no default button.
  val okButton = SwingUtilities.getRootPane(link)?.defaultButton
  if (okButton != null) {
    val window = SwingUtilities.getWindowAncestor(link)
    okButton.doClick()
    // A configurable that cannot apply its changes keeps the window open, and the user must fix the error first.
    if (window != null && window.isShowing) return
  }

  ApplicationManager.getApplication().invokeLater {
    if (project != null && project.isDisposed) return@invokeLater
    val action = ActionManager.getInstance().getAction(ACTION_ID) ?: return@invokeLater
    val dataContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build()
    ActionUtil.performAction(action, AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null))
  }
}
