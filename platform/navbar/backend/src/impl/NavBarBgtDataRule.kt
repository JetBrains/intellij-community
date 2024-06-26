// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend.impl

import com.intellij.ide.navigationToolbar.NavBarModelExtension
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

    sink[PlatformDataKeys.SELECTED_ITEM] = selection.firstOrNull()
    sink[PlatformDataKeys.SELECTED_ITEMS] = selection.toTypedArray()

    sink.lazy(LangDataKeys.IDE_VIEW) {
      NavBarIdeView(pointers)
    }
    defaultSnapshot(project, sink, pointers)
    NavBarModelExtension.EP_NAME.forEachExtensionSafe {
      it.uiDataSnapshot(sink, snapshot)
    }
  }
}

private fun defaultSnapshot(project: Project,
                            sink: DataSink, pointers:
                            List<Pointer<out NavBarItem>>) {
  val seq = { pointers.asSequence().mapNotNull { it.dereference() } }
  sink.lazy(PlatformCoreDataKeys.MODULE) {
    seq().firstNotNullOfOrNull { (it as? ModuleNavBarItem)?.data }
    ?: seq().firstNotNullOfOrNull {
      (it as? PsiNavBarItem)?.data?.let { psi ->
        ModuleUtilCore.findModuleForPsiElement(psi)
      }
    }
  }
  sink.lazy(LangDataKeys.MODULE_CONTEXT) {
    val dir = seq().firstNotNullOfOrNull { (it as? PsiNavBarItem)?.data as? PsiDirectory }
    if (dir != null && ProjectRootsUtil.isModuleContentRoot(dir.virtualFile, project)) {
      ModuleUtilCore.findModuleForPsiElement(dir)
    }
    else {
      null
    }
  }
  sink.lazy(CommonDataKeys.PSI_ELEMENT) {
    seq().firstNotNullOfOrNull { (it as? PsiNavBarItem)?.data }
  }
  sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY) {
    seq().mapNotNull { (it as? PsiNavBarItem)?.data }
      .toList()
      .ifEmpty { null }
      ?.toArray(PsiElement.EMPTY_ARRAY)
  }
  sink.lazy(CommonDataKeys.VIRTUAL_FILE_ARRAY) {
    seq().mapNotNull {
      (it as? PsiNavBarItem)?.data?.let { psi ->
        PsiUtilCore.getVirtualFile(psi)
      }
    }
      .toSet()
      .ifEmpty { null }
      ?.toArray(VirtualFile.EMPTY_ARRAY)
  }
  sink.lazy(PlatformDataKeys.NAVIGATABLE_ARRAY) {
    seq().mapNotNull {
      (it as? DefaultNavBarItem<*>)?.data as? Navigatable
    }
      .toList()
      .ifEmpty { null }
      ?.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY)
  }
  sink.lazy(PlatformDataKeys.DELETE_ELEMENT_PROVIDER) {
    val hasModule = seq().firstNotNullOfOrNull { (it as? ModuleNavBarItem)?.data } != null
    if (hasModule) {
      ModuleDeleteProvider.getInstance()
    }
    else {
      DefaultDeleteProvider()
    }
  }
}