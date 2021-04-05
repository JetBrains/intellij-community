// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.actions

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.navigation.JBProtocolNavigateCommandBase
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiManager

private const val INSPECTION_PARAMETER = "inspection"
class JBProtocolInspectCommand: JBProtocolNavigateCommandBase("inspect") {
  override fun navigateByPath(project: Project, parameters: Map<String, String>, pathText: String) {
    InspectNavigator(project, parameters, pathText).navigate()
  }

  class InspectNavigator(project: Project, parameters: Map<String, String>, pathText: String)
    : JBProtocolNavigateCommandBase.PathNavigator(project, parameters, pathText) {
    override fun performEditorAction(textEditor: TextEditor, line: String?, column: String?) {
      super.performEditorAction(textEditor, line, column)
      runInspection(project, parameters[INSPECTION_PARAMETER], textEditor.file)
    }

    private fun runInspection(project: Project, shortName: String?, virtualFile: VirtualFile?) {
      virtualFile ?: return
      shortName ?: return
      val managerEx = InspectionManager.getInstance(project) as InspectionManagerEx
      val scope = AnalysisScope(project, listOf(virtualFile))

      val currentProfile: InspectionProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
      val toolWrapper = currentProfile.getInspectionTool(shortName, project)

      if (toolWrapper == null) {
        return
      }

      val psiManager = PsiManager.getInstance(project)
      val psiFile = (if (virtualFile.isValid) psiManager.findFile(virtualFile) else null) ?: return

      HighlightLevelUtil.forceRootHighlighting(psiFile, FileHighlightingSetting.FORCE_HIGHLIGHTING)

      val context = RunInspectionIntention.createContext(toolWrapper, managerEx, psiFile)
      context.isForceInspectAllScope = true
      context.doInspections(scope)
    }

  }
}