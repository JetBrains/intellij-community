// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.structureView.StructureViewBundle
import com.intellij.model.Pointer
import com.intellij.model.Pointer.hardPointer
import com.intellij.navigation.NavigationRequest
import com.intellij.navigation.NavigationService
import com.intellij.openapi.editor.colors.CodeInsightColors.ERRORS_ATTRIBUTES
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.*
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.Icon


open class DefaultNavBarItem<out T>(val data: T) : NavBarItem {

  override fun createPointer(): Pointer<out NavBarItem> = hardPointer(this)

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun presentation(): NavBarItemPresentation {

    val icon: Icon? = fromOldExtensions { ext -> ext.getIcon(data) } ?: getIcon()

    val invalidText = StructureViewBundle.message("node.structureview.invalid")
    val text: String = fromOldExtensions { ext -> ext.getPresentableText(data, false) } ?: invalidText
    val popupText: String = fromOldExtensions { ext -> ext.getPresentableText(data, true) } ?: invalidText

    val textAttributes = getTextAttributes(selected = false)
    val selectedTextAttributes = getTextAttributes(selected = true)

    val hasContainingFile = (data as? PsiElement)?.containingFile != null

    return NavBarItemPresentation(
      icon,
      text,
      popupText,
      textAttributes,
      selectedTextAttributes,
      hasContainingFile
    )
  }

  open fun getIcon(): Icon? = null

  open fun getText(forPopup: Boolean): @Nls String = data.toString()

  open fun getTextAttributes(selected: Boolean): SimpleTextAttributes = REGULAR_ATTRIBUTES

}


internal class ProjectNavBarItem(data: Project) : DefaultNavBarItem<Project>(data), Pointer<NavBarItem> {

  override fun createPointer(): Pointer<out NavBarItem> = this

  override fun dereference(): NavBarItem? = if (data.isDisposed) null else this

  override fun getIcon() = AllIcons.Nodes.Project

  override fun getTextAttributes(selected: Boolean): SimpleTextAttributes {
    val problemSolver = WolfTheProblemSolver.getInstance(data)
    val hasProblems = ModuleManager.getInstance(data)
      .modules
      .any(problemSolver::hasProblemFilesBeneath)
    return if (hasProblems) errorAttributes else REGULAR_ATTRIBUTES
  }
}


internal class ModuleNavBarItem(data: Module) : DefaultNavBarItem<Module>(data), Pointer<NavBarItem> {

  override fun createPointer(): Pointer<out NavBarItem> = this

  override fun dereference(): NavBarItem? = if (data.isDisposed) null else this

  override fun navigationRequest(): NavigationRequest? {
    return NavigationService.instance().rawNavigationRequest(object : Navigatable {
      override fun navigate(requestFocus: Boolean) {
        val projectView = ProjectView.getInstance(data.project)
        val projectViewPane = projectView.getProjectViewPaneById(projectView.currentViewId)
        projectViewPane?.selectModule(data, true)

      }

      override fun canNavigate(): Boolean = true
      override fun canNavigateToSource(): Boolean = true
    })
  }

  override fun getIcon() = ModuleType.get(data).icon

  override fun getTextAttributes(selected: Boolean): SimpleTextAttributes {
    val problemSolver = WolfTheProblemSolver.getInstance(data.project)
    val hasProblems = problemSolver.hasProblemFilesBeneath(data)

    return if (hasProblems) errorAttributes else REGULAR_ATTRIBUTES
  }

}

internal class PsiNavBarItem(data: PsiElement, val ownerExtension: NavBarModelExtension?) : DefaultNavBarItem<PsiElement>(
  data) {

  override fun createPointer(): Pointer<out NavBarItem> {
    val data = data
    val ownerExtension = ownerExtension

    return Pointer.delegatingPointer(data.createSmartPointer()) { psi ->
      PsiNavBarItem(psi, ownerExtension)
    }
  }

  override fun navigationRequest(): NavigationRequest? {
    return (data as? Navigatable)?.let(NavigationService.instance()::rawNavigationRequest)
  }

  override fun getIcon(): Icon? =
    try {
      data.getIcon(0)?.let {
        val maxDimension = JBUI.scale(16 * 2)
        IconUtil.cropIcon(it, maxDimension, maxDimension)
      }
    }
    catch (e: IndexNotReadyException) {
      null
    }

  override fun getTextAttributes(selected: Boolean): SimpleTextAttributes {
    val psiFile = data.containingFile

    if (psiFile != null) {
      val virtualFile = psiFile.virtualFile ?: return SimpleTextAttributes(null, null, errorAttributes.waveColor, STYLE_PLAIN)
      val problemSolver = WolfTheProblemSolver.getInstance(data.project)
      val style = if (problemSolver.isProblemFile(virtualFile)) errorAttributes.style else STYLE_PLAIN
      val color = if (!selected) FileStatusManager.getInstance(data.project).getStatus(virtualFile).color else null
      return SimpleTextAttributes(null, color, errorAttributes.waveColor, style)
    }
    else {
      if (data is PsiDirectory) {
        val vDir = data.virtualFile
        if (vDir.parent == null || ProjectRootsUtil.isModuleContentRoot(vDir, data.project)) {
          return REGULAR_BOLD_ATTRIBUTES
        }
      }

      if (wolfHasProblemFilesBeneath(data)) {
        return errorAttributes
      }
    }
    return REGULAR_ATTRIBUTES
  }

  override fun navigateOnClick(): Boolean {

    // TODO remove once DB plugin is rewritten
    val shouldExpandOnClick = fromOldExtensions { ext -> ext.shouldExpandOnClick(data) }
    if (shouldExpandOnClick != null) {
      return !shouldExpandOnClick
    }
    // end of todo

    return data !is PsiDirectory && data !is PsiDirectoryContainer
  }

}

internal class OrderEntryNavBarItem(data: OrderEntry) : DefaultNavBarItem<OrderEntry>(data) {
  override fun getIcon() = when (data) {
    is JdkOrderEntry -> (data.jdk?.sdkType as? SdkType)?.icon
    is LibraryOrderEntry -> AllIcons.Nodes.PpLibFolder
    is ModuleOrderEntry -> data.module?.let { ModuleType.get(it) }?.icon
    else -> super.getIcon()
  }
}


private val errorAttributes =
  EditorColorsManager.getInstance()
    .schemeForCurrentUITheme
    .getAttributes(ERRORS_ATTRIBUTES)
    .let(SimpleTextAttributes::fromTextAttributes)
    .let { schemeAttributes ->
      merge(
        SimpleTextAttributes(STYLE_USE_EFFECT_COLOR, schemeAttributes.fgColor),
        schemeAttributes
      )
    }


private fun wolfHasProblemFilesBeneath(scope: PsiElement): Boolean =
  WolfTheProblemSolver
    .getInstance(scope.project)
    .hasProblemFilesBeneath { virtualFile: VirtualFile? ->
      when (scope) {
        is PsiDirectory -> {
          if (!VfsUtilCore.isAncestor(scope.virtualFile, virtualFile!!, false)) {
            false
          }
          else {
            ModuleUtilCore.findModuleForFile(virtualFile, scope.getProject()) === ModuleUtilCore.findModuleForPsiElement(scope)
          }
        }
        is PsiDirectoryContainer -> { // TODO: remove. It doesn't look like we'll have packages in navbar ever again
          scope.directories.any { VfsUtilCore.isAncestor(it.virtualFile, virtualFile!!, false) }
        }
        else -> false
      }
    }
