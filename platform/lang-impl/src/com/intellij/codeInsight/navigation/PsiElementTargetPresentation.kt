// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation

import com.intellij.icons.AllIcons
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.JBColor
import java.awt.Font
import java.io.File
import java.util.regex.Pattern
import javax.swing.Icon

class PsiElementTargetPresentation(private val myElement: PsiElement) : TargetPresentation {

  companion object {
    private val CONTAINER_PATTERN = Pattern.compile("(\\(in |\\()?([^)]*)(\\))?")
  }

  private val myItemPresentation: ItemPresentation? = (myElement as? NavigationItem)?.presentation
  private val myProject: Project = myElement.project
  private val myVirtualFile: VirtualFile? by lazy { myElement.containingFile?.virtualFile }
  private val module: Module? by lazy { myVirtualFile?.let { ModuleUtil.findModuleForFile(it, myProject) } }

  override fun getIcon(): Icon? = myElement.getIcon(Iconable.ICON_FLAG_VISIBILITY or Iconable.ICON_FLAG_READ_STATUS)

  override fun getPresentableText(): String {
    return myItemPresentation?.presentableText
           ?: (myElement as? PsiNamedElement)?.name
           ?: myElement.text
  }


  override fun getPresentableAttributes(): TextAttributes? {
    val key = (myItemPresentation as? ColoredItemPresentation)?.textAttributesKey
    val attributes: TextAttributes? = EditorColorsManager.getInstance().schemeForCurrentUITheme.getAttributes(key)
    val fileColor = myVirtualFile?.let { EditorTabPresentationUtil.getFileBackgroundColor(myProject, it) } ?: return attributes
    if (attributes?.backgroundColor != null) return attributes
    val result = attributes?.clone() ?: TextAttributes()
    return result.apply {
      backgroundColor = fileColor
    }
  }

  override fun getLocationText(): String? {
    val locationString = myItemPresentation?.locationString ?: return null
    val matcher = CONTAINER_PATTERN.matcher(locationString)
    return if (matcher.matches()) matcher.group(2) else locationString
  }

  override fun getLocationAttributes(): TextAttributes? {
    val file = myVirtualFile ?: return null
    val locationColor = FileStatusManager.getInstance(myProject).getStatus(file)?.color
    val hasProblem = WolfTheProblemSolver.getInstance(myProject).isProblemFile(file)
    return when {
      hasProblem -> TextAttributes(locationColor, null, JBColor.red, EffectType.WAVE_UNDERSCORE, Font.PLAIN)
      locationColor != null -> TextAttributes(locationColor, null, null, null, Font.PLAIN)
      else -> null
    }
  }

  override fun getRightText(): String? {
    val file = myVirtualFile ?: return null
    val fileIndex = ProjectFileIndex.getInstance(myProject)
    if (fileIndex.isInLibrarySource(file) || fileIndex.isInLibraryClasses(file)) {
      val text = orderEntryText(fileIndex, file) ?: sdkText(file) ?: ""
      return JarFileSystem.getInstance().getVirtualFileForJar(file)?.let { jar ->
        val name = jar.name
        if (text == name) {
          text
        }
        else {
          "$text ($name)"
        }
      }
    }
    else return module?.let {
      if (Registry.`is`("ide.show.folder.name.instead.of.module.name")) {
        val path = ModuleUtilCore.getModuleDirPath(it)
        if (path.isEmpty()) it.name else File(path).name
      }
      else {
        it.name
      }
    }
  }

  override fun getRightIcon(): Icon? {
    val file = myVirtualFile ?: return null
    val fileIndex = ProjectFileIndex.getInstance(myProject)
    return when {
      fileIndex.isInLibrarySource(file) || fileIndex.isInLibraryClasses(file) -> AllIcons.Nodes.PpLibFolder
      fileIndex.isInTestSourceContent(file) -> AllIcons.Modules.TestSourceFolder
      else -> module?.let { ModuleType.get(it).icon }
    }
  }
}
