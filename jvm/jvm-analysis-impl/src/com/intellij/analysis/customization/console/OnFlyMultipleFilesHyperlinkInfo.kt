// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.customization.console

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.execution.filters.HyperlinkInfoFactory.HyperlinkHandler
import com.intellij.execution.filters.impl.MultipleFilesHyperlinkInfoBase
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class OnFlyMultipleFilesHyperlinkInfo internal constructor(private val myInfoCache: ClassInfoResolver,
                                                           private val probableClassName: ProbableClassName,
                                                           lineNumber: Int,
                                                           project: Project,
                                                           action: HyperlinkHandler?) :
  MultipleFilesHyperlinkInfoBase(lineNumber, project, action) {
  override fun getFiles(project: Project): List<PsiFile> {
    if (DumbService.isDumb(project)) {
      return emptyList()
    }

    val packageName = StringUtil.getPackageName(probableClassName.fullClassName)
    if (packageName.length == probableClassName.fullClassName.length) return emptyList()
    val className = probableClassName.fullClassName.substring(packageName.length + 1)
    val resolvedClasses = myInfoCache.resolveClasses(className, packageName)
    val currentFiles: MutableList<PsiFile> = ArrayList()
    for (file in resolvedClasses.classes.values) {
      if (!file.isValid) continue
      val psiFile = PsiManager.getInstance(project).findFile(file)
      if (psiFile != null) {
        val navigationElement = psiFile.navigationElement // Sources may be downloaded.
        if (navigationElement is PsiFile) {
          currentFiles.add(navigationElement)
          continue
        }
        currentFiles.add(psiFile)
      }
    }
    return currentFiles
  }

  override fun showNotFound(project: Project, hyperlinkLocationPoint: RelativePoint?) {
    if (hyperlinkLocationPoint == null) return
    if (DumbService.isDumb(project)) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
        DumbModeBlockedFunctionality.GotoClass
      )
      return
    }
    val message = JvmAnalysisBundle.message("action.find.similar.stack.call.methods.not.found")
    val label = HintUtil.createWarningLabel(message)
    JBPopupFactory.getInstance().createBalloonBuilder(label)
      .setFadeoutTime(4000)
      .setFillColor(HintUtil.getWarningColor())
      .createBalloon()
      .show(RelativePoint(hyperlinkLocationPoint.screenPoint), Balloon.Position.above)
  }

  override fun getDescriptor(): OpenFileDescriptor? {
    return null
  }
}
