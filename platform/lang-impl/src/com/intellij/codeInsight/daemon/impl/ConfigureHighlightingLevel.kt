// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonBundle.message
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil.forceRootHighlighting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.editor.markup.InspectionsLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.FileViewProvider

fun getConfigureHighlightingLevelPopup(context: DataContext): JBPopup? {
  val psi = context.getData(PSI_FILE) ?: return null
  if (!psi.isValid || psi.project.isDisposed) return null

  val provider = psi.viewProvider
  val languages = provider.languages
  if (languages.isEmpty()) return null

  val file = psi.virtualFile ?: return null
  val index = ProjectFileIndex.getInstance(psi.project)
  val isAllInspectionsEnabled = index.isInContent(file) || !index.isInLibrary(file)

  val group = DefaultActionGroup()
  languages.sortedBy { it.displayName }.forEach {
    if (languages.count() > 1) {
      group.add(Separator.create(it.displayName))
    }
    group.add(LevelAction(InspectionsLevel.NONE, provider, it))
    group.add(LevelAction(InspectionsLevel.SYNTAX, provider, it))
    if (isAllInspectionsEnabled) {
      group.add(LevelAction(InspectionsLevel.ESSENTIAL, provider, it))
      group.add(LevelAction(InspectionsLevel.ALL, provider, it))
    }
  }
  group.add(Separator.create())
  group.add(ConfigureInspectionsAction())
  val title = message("popup.title.configure.highlighting.level", psi.virtualFile.presentableName)
  return JBPopupFactory.getInstance().createActionGroupPopup(title, group, context, true, null, 100)
}


private class LevelAction(val level: InspectionsLevel, val provider: FileViewProvider, val language: Language)
  : ToggleAction(level.toString()), DumbAware {
  override fun isSelected(event: AnActionEvent): Boolean {
    val file = provider.getPsi(language) ?: return false
    val manager = HighlightingSettingsPerFile.getInstance(file.project) ?: return false
    val configuredLevel = FileHighlightingSetting.toInspectionsLevel(manager.getHighlightingSettingForRoot(file))
    return level == configuredLevel
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    if (!state) return
    val file = provider.getPsi(language) ?: return;
    forceRootHighlighting(file, FileHighlightingSetting.fromInspectionsLevel(level))
    InjectedLanguageManager.getInstance(file.project).dropFileCaches(file)
    DaemonCodeAnalyzer.getInstance(file.project).restart()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.setDescription(EditorBundle.message("hector.highlighting.level.title")+": "+level.description)
  }
}


internal class ConfigureHighlightingLevelAction : DumbAwareAction() {

  override fun update(event: AnActionEvent) {
    val enabled = event.getData(PSI_FILE)?.viewProvider?.languages?.isNotEmpty()
    event.presentation.isEnabled = enabled == true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val popup = getConfigureHighlightingLevelPopup(event.dataContext)
    popup?.showInBestPositionFor(event.dataContext)
  }
}
