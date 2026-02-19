// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.diagnostic.PluginException
import com.intellij.ide.util.DefaultModuleRendererFactory
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.PomTargetPsiElement
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.JBColor
import com.intellij.util.TextWithIcon
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Font
import java.util.regex.Pattern

internal val codeInsightLogger: Logger = Logger.getInstance("#com.intellij.codeInsight.navigation")
private val CONTAINER_PATTERN: Pattern = Pattern.compile("(\\(in |\\()?([^)]*)(\\))?")

/**
 * **DO NOT USE**
 *
 * This method is internal so that it can be re-used by the platform code in different modules.
 *
 * This method exists to abstract the computation of [TargetPresentation] by a generic [ItemPresentation].
 *
 * Plugins have access to their own classes, they should know how to provide the presentation by themselves.
 * Instead of calling this method, plugins should [build their own presentation][TargetPresentation.builder].
 * [fileStatusAttributes] and [fileLocation] should help with building.
 */
@ApiStatus.Internal
fun targetPresentation(itemPresentation: ItemPresentation): TargetPresentation {
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

/**
 * **DO NOT USE**
 *
 * This method is internal so that it can be re-used by the platform code in different modules.
 *
 * This method exists to abstract the computation of [TargetPresentation] by a generic [PsiElement]
 * following the previous incorrect assumptions and conventions, e.g. that a `PsiElement` has a name,
 * or that the container text matches the `"(in ...)"` pattern, or that a `PsiElement` has a containing file.
 * This presentation was also reused in various contexts incorrectly.
 *
 * Plugins have access to their own classes, they should know how to provide the presentation by themselves.
 * Instead of calling this method, plugins should [build their own presentation][TargetPresentation.builder].
 * [fileStatusAttributes] and [fileLocation] should help with building.
 */
@ApiStatus.Internal
fun targetPresentation(element: PsiElement): TargetPresentation {
  val project = element.project
  val file = element.containingFile?.virtualFile
  val itemPresentation = (element as? NavigationItem)?.presentation
  val presentableText: String = itemPresentation?.presentableText
                                ?: (element as? PsiNamedElement)?.name
                                ?: element.text
                                ?: run {
                                  presentationError(element)
                                  element.toString()
                                }
  val moduleTextWithIcon = PsiElementListCellRenderer.getModuleTextWithIcon(element)
  return TargetPresentation
    .builder(presentableText)
    .backgroundColor(file?.let { VfsPresentationUtil.getFileBackgroundColor(project, file) })
    .icon(element.getIcon(Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS))
    .presentableTextAttributes(itemPresentation?.getColoredAttributes())
    .containerText(itemPresentation?.getContainerText(), file?.let { fileStatusAttributes(project, file) })
    .locationText(moduleTextWithIcon?.text, moduleTextWithIcon?.icon)
    .presentation()
}

private fun presentationError(element: PsiElement) {
  val instance = (element as? PomTargetPsiElement)?.target ?: element
  val clazz = instance.javaClass
  codeInsightLogger.error(PluginException.createByClass("${clazz.name} cannot be presented", null, clazz))
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
fun fileLocation(project: Project, file: VirtualFile): TextWithIcon? {
  val fileIndex = ProjectRootManager.getInstance(project).fileIndex
  return if (fileIndex.isInLibrary(file)) {
    DefaultModuleRendererFactory().libraryLocation(project, fileIndex, file)
  }
  else {
    val module = ModuleUtilCore.findModuleForFile(file, project)
    if (module != null) {
      DefaultModuleRendererFactory.projectLocation(file, module, fileIndex)
    }
    else {
      null
    }
  }
}
