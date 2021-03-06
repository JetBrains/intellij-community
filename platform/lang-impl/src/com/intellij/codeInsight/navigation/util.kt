// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation

import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.ide.util.PsiElementModuleRenderer
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import java.awt.Font
import java.util.regex.Pattern
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import com.intellij.openapi.util.Pair as JBPair

private val CONTAINER_PATTERN: Pattern = Pattern.compile("(\\(in |\\()?([^)]*)(\\))?")

internal fun targetPresentation(itemPresentation: ItemPresentation): TargetPresentation {
  return TargetPresentation
    .builder(itemPresentation.presentableText ?: "")
    .icon(itemPresentation.getIcon(false))
    .presentableTextAttributes(itemPresentation.getColoredAttributes())
    .containerText(itemPresentation.getContainerText())
    .presentation()
}

private fun ItemPresentation.getColoredAttributes(): TextAttributes? {
  val coloredPresentation = this as? ColoredItemPresentation
  val textAttributesKey = coloredPresentation?.textAttributesKey ?: return null
  return EditorColorsManager.getInstance().schemeForCurrentUITheme.getAttributes(textAttributesKey)
}

@Nls
private fun ItemPresentation.getContainerText(): String? {
  val locationString = locationString ?: return null
  val matcher = CONTAINER_PATTERN.matcher(locationString)
  return if (matcher.matches()) matcher.group(2) else locationString
}

@ApiStatus.Internal
fun targetPresentation(element: PsiElement): TargetPresentation {
  val project = element.project
  val file = element.containingFile?.virtualFile
  val itemPresentation = (element as? NavigationItem)?.presentation
  val presentableText: String = itemPresentation?.presentableText
                                ?: (element as? PsiNamedElement)?.name
                                ?: element.text
  val moduleRendererComponent = PsiElementListCellRenderer.getModuleRenderer(element)
    ?.getListCellRendererComponent(JList<PsiElement>(), element, -1, false, false) as? JLabel

  return TargetPresentation
    .builder(presentableText)
    .backgroundColor(file?.let { VfsPresentationUtil.getFileBackgroundColor(project, file) })
    .icon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS))
    .presentableTextAttributes(itemPresentation?.getColoredAttributes())
    .containerText(itemPresentation?.getContainerText(), file?.let { fileStatusAttributes(project, file) })
    .locationText(moduleRendererComponent?.text, moduleRendererComponent?.icon)
    .presentation()
}

@ApiStatus.Experimental
fun fileStatusAttributes(project: Project, file: VirtualFile): TextAttributes? {
  val fileColor = FileStatusManager.getInstance(project).getStatus(file)?.color
  val hasProblem = WolfTheProblemSolver.getInstance(project).isProblemFile(file)
  return when {
    hasProblem -> TextAttributes(fileColor, null, JBColor.red, EffectType.WAVE_UNDERSCORE, Font.PLAIN)
    fileColor != null -> TextAttributes(fileColor, null, null, null, Font.PLAIN)
    else -> null
  }
}

@ApiStatus.Experimental
fun fileLocation(project: Project, file: VirtualFile): JBPair<@Nls @NotNull String, @NotNull Icon>? {
  val fileIndex = ProjectRootManager.getInstance(project).fileIndex
  return if (fileIndex.isInLibrary(file)) {
    PsiElementModuleRenderer().libraryLocation(fileIndex, file)
  }
  else {
    val module = ModuleUtilCore.findModuleForFile(file, project)
    if (module != null) {
      PsiElementModuleRenderer.projectLocation(file, module, fileIndex)
    }
    else null
  }
}
