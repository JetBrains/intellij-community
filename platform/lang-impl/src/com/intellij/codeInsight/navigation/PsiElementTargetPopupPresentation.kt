// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.TargetPopupPresentation
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Font
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList

@Experimental
class PsiElementTargetPopupPresentation(private val myElement: PsiElement) : TargetPopupPresentation {

  private val myProject: Project = myElement.project
  private val myVirtualFile: VirtualFile? = myElement.containingFile?.virtualFile
  private val myItemPresentation: ItemPresentation? = (myElement as? NavigationItem)?.presentation
  private val myModuleRendererData: Pair<@Nls String, Icon?>? = run {
    val renderer = PsiElementListCellRenderer.getModuleRenderer(myElement)
    val component = renderer?.getListCellRendererComponent(JList<PsiElement>(), myElement, -1, false, false) as? JLabel
    component?.let {
      Pair(component.text, component.icon)
    }
  }

  override fun getBackgroundColor(): Color? {
    val virtualFile = myVirtualFile ?: return null
    return EditorTabPresentationUtil.getFileBackgroundColor(myProject, virtualFile)
  }

  override fun getIcon(): Icon? = myElement.getIcon(Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)

  override fun getPresentableText(): String {
    return myItemPresentation?.presentableText
           ?: (myElement as? PsiNamedElement)?.name
           ?: myElement.text
  }

  override fun getPresentableAttributes(): TextAttributes? = myItemPresentation?.getColoredAttributes()

  override fun getLocationText(): String? = myItemPresentation?.getLocationText()

  override fun getLocationAttributes(): TextAttributes? {
    val virtualFile = myVirtualFile ?: return null
    val locationColor = FileStatusManager.getInstance(myProject).getStatus(virtualFile)?.color
    val hasProblem = WolfTheProblemSolver.getInstance(myProject).isProblemFile(virtualFile)
    return when {
      hasProblem -> TextAttributes(locationColor, null, JBColor.red, EffectType.WAVE_UNDERSCORE, Font.PLAIN)
      locationColor != null -> TextAttributes(locationColor, null, null, null, Font.PLAIN)
      else -> null
    }
  }

  @Suppress("HardCodedStringLiteral") // inspection doesn't support type annotations in Kotlin
  override fun getRightText(): String? = myModuleRendererData?.first

  override fun getRightIcon(): Icon? = myModuleRendererData?.second
}
