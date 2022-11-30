// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.actions

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.impl.DefaultNavBarItem
import com.intellij.ide.navbar.impl.ModuleNavBarItem
import com.intellij.ide.navbar.impl.PsiNavBarItem
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.ide.util.DeleteHandler.DefaultDeleteProvider
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.toArray

/**
 * Fast extension data without selection (allows to override cut/copy/paste providers)
 *
 * TODO consider a new extension for that OR new API for cut/copy/paste
 */
internal fun extensionData(dataId: String): Any? {
  return extensionData(dataId, emptyDataProvider)
}

private val emptyDataProvider = DataProvider { null }

private fun extensionData(dataId: String, provider: DataProvider): Any? {
  for (modelExtension in NavBarModelExtension.EP_NAME.extensionList) {
    val data = modelExtension.getData(dataId, provider)
    if (data != null) return data
  }
  return provider.getData(dataId)
}

internal fun getBgData(project: Project, selection: List<Pointer<out NavBarItem>>, dataId: String): Any? {
  val selectedItems = lazy(LazyThreadSafetyMode.NONE) {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    selection.mapNotNull {
      it.dereference()
    }
  }
  if (LangDataKeys.IDE_VIEW.`is`(dataId)) {
    return NavBarIdeView(selectedItems)
  }
  val provider = DataProvider {
    getBgData(project, selectedItems, it)
  }
  return extensionData(dataId, provider)
}

private fun getBgData(project: Project, selectedItems: Lazy<List<NavBarItem>>, dataId: String): Any? = when (dataId) {
  CommonDataKeys.PROJECT.name -> {
    project
  }
  PlatformCoreDataKeys.MODULE.name -> {
    selectedItems.value
      .firstNotNullOfOrNull {
        (it as? ModuleNavBarItem)?.data
      }
    ?: selectedItems.value
      .firstNotNullOfOrNull {
        (it as? PsiNavBarItem)?.data?.let { psi ->
          ModuleUtilCore.findModuleForPsiElement(psi)
        }
      }
  }
  LangDataKeys.MODULE_CONTEXT.name -> {
    val dir = selectedItems.value
      .firstNotNullOfOrNull {
        (it as? PsiNavBarItem)?.data as? PsiDirectory
      }
    if (dir != null && ProjectRootsUtil.isModuleContentRoot(dir.virtualFile, project)) {
      ModuleUtilCore.findModuleForPsiElement(dir)
    }
    else {
      null
    }
  }
  PlatformCoreDataKeys.SELECTED_ITEM.name -> {
    (selectedItems.value.firstOrNull() as? DefaultNavBarItem<*>)?.data
  }
  PlatformCoreDataKeys.SELECTED_ITEMS.name -> {
    selectedItems.value
      .mapNotNull {
        (it as? DefaultNavBarItem<*>)?.data
      }
      .ifEmpty { null }
      ?.toArray(ArrayUtil.EMPTY_OBJECT_ARRAY)
  }
  CommonDataKeys.PSI_ELEMENT.name -> {
    selectedItems.value
      .firstNotNullOfOrNull {
        (it as? PsiNavBarItem)?.data
      }
  }
  PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.name -> {
    selectedItems.value
      .mapNotNull {
        (it as? PsiNavBarItem)?.data
      }
      .ifEmpty { null }
      ?.toArray(PsiElement.EMPTY_ARRAY)
  }
  CommonDataKeys.VIRTUAL_FILE_ARRAY.name -> {
    selectedItems.value
      .mapNotNull {
        (it as? PsiNavBarItem)?.data?.let { psi ->
          PsiUtilCore.getVirtualFile(psi)
        }
      }
      .toSet()
      .ifEmpty { null }
      ?.toArray(VirtualFile.EMPTY_ARRAY)
  }
  CommonDataKeys.NAVIGATABLE_ARRAY.name -> {
    selectedItems.value
      .mapNotNull {
        (it as? DefaultNavBarItem<*>)?.data as? Navigatable
      }
      .ifEmpty { null }
      ?.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY)
  }
  PlatformDataKeys.DELETE_ELEMENT_PROVIDER.name -> {
    val hasModule = selectedItems.value.firstNotNullOfOrNull {
      (it as? ModuleNavBarItem)?.data
    } != null
    if (hasModule) {
      ModuleDeleteProvider.getInstance()
    }
    else {
      DefaultDeleteProvider()
    }
  }
  else -> {
    null
  }
}
