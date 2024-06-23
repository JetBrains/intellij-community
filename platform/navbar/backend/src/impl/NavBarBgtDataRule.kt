// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend.impl

import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.util.DeleteHandler.DefaultDeleteProvider
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.platform.navbar.impl.extensionData
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.toArray

internal class NavBarBgtDataRule : UiDataRule {

  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val project = snapshot[CommonDataKeys.PROJECT] ?: return
    val selection = snapshot[NavBarVmItem.SELECTED_ITEMS] ?: return
    val pointers = selection.mapNotNull { (it as? IdeNavBarVmItem)?.pointer }
    if (pointers.isEmpty()) return

    sink.lazy(LangDataKeys.IDE_VIEW) {
      NavBarIdeView(pointers)
    }
    sink[PlatformCoreDataKeys.BGT_DATA_PROVIDER] = DataProvider { dataId ->
      val provider = DataProvider {
        getBgData(project, pointers, it)
      }
      extensionData(dataId, provider)
    }
  }
}

private fun getBgData(project: Project, selectedItems: List<Pointer<out NavBarItem>>, dataId: String): Any? {
  val seq = selectedItems.asSequence().mapNotNull { it.dereference() }
  return when (dataId) {
    CommonDataKeys.PROJECT.name -> project
    PlatformDataKeys.SELECTED_ITEM.name -> seq.firstOrNull()
    PlatformDataKeys.SELECTED_ITEMS.name -> seq.toList().toTypedArray()
    PlatformCoreDataKeys.MODULE.name -> {
      seq.firstNotNullOfOrNull { (it as? ModuleNavBarItem)?.data }
      ?: seq.firstNotNullOfOrNull {
        (it as? PsiNavBarItem)?.data?.let { psi ->
          ModuleUtilCore.findModuleForPsiElement(psi)
        }
      }
    }
    LangDataKeys.MODULE_CONTEXT.name -> {
      val dir = seq.firstNotNullOfOrNull { (it as? PsiNavBarItem)?.data as? PsiDirectory }
      if (dir != null && ProjectRootsUtil.isModuleContentRoot(dir.virtualFile, project)) {
        ModuleUtilCore.findModuleForPsiElement(dir)
      }
      else {
        null
      }
    }
    CommonDataKeys.PSI_ELEMENT.name -> seq.firstNotNullOfOrNull { (it as? PsiNavBarItem)?.data }
    PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.name -> seq.mapNotNull { (it as? PsiNavBarItem)?.data }
      .toList()
      .ifEmpty { null }
      ?.toArray(PsiElement.EMPTY_ARRAY)
    CommonDataKeys.VIRTUAL_FILE_ARRAY.name -> seq.mapNotNull {
      (it as? PsiNavBarItem)?.data?.let { psi ->
        PsiUtilCore.getVirtualFile(psi)
      }
    }
      .toSet()
      .ifEmpty { null }
      ?.toArray(VirtualFile.EMPTY_ARRAY)
    CommonDataKeys.NAVIGATABLE_ARRAY.name -> seq.mapNotNull {
      (it as? DefaultNavBarItem<*>)?.data as? Navigatable
    }
      .toList()
      .ifEmpty { null }
      ?.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY)
    PlatformDataKeys.DELETE_ELEMENT_PROVIDER.name -> (
      seq.firstNotNullOfOrNull { (it as? ModuleNavBarItem)?.data } != null).let { hasModule ->
      if (hasModule) {
        ModuleDeleteProvider.getInstance()
      }
      else {
        DefaultDeleteProvider()
      }
    }
    else -> null
  }
}
