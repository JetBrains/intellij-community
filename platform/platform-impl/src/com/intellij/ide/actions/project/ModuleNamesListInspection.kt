/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions.project

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.*
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

internal class ModuleNamesListInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val document = PsiDocumentManager.getInstance(manager.project).getDocument(file) ?: return null

    val lines = file.text.lines()
    val counts = lines.fold(HashMap<String, Int>(), { map, s -> map[s] = map.getOrDefault(s, 0) + 1; map })
    val problems = ArrayList<ProblemDescriptor>()

    fun addProblem(line: Int, message: String) {
      val range = if (line < document.lineCount) {
        TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
      }
      else {
        TextRange(document.textLength, document.textLength)
      }
      val afterEnd = range.isEmpty
      problems += ProblemDescriptorBase(file, file, message, emptyArray(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, afterEnd, range, true, isOnTheFly)
    }

    val modulesCount = ModuleManager.getInstance(manager.project).modules.size
    lines.forEachIndexed { line, s ->
      if (s.isEmpty()) {
        addProblem(line, ProjectBundle.message("module.name.inspection.empty.name.is.not.allowed"))
      }
      else if (counts[s]!! > 1) {
        addProblem(line, ProjectBundle.message("module.name.inspection.duplicate.module.name", s))
      }
      if (line >= modulesCount) {
        addProblem(line, ProjectBundle.message("module.name.inspection.too.many.lines", modulesCount, line+1))
      }
    }
    if (lines.size < modulesCount) {
      addProblem(lines.size, ProjectBundle.message("module.name.inspection.too.few.lines", modulesCount, lines.size))
    }

    return problems.toTypedArray()
  }

  override fun getDisplayName() = ProjectBundle.message("module.name.inspection.display.name")

  override fun getStaticDescription() = ""

  override fun isEnabledByDefault() = true

  override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.ERROR
}