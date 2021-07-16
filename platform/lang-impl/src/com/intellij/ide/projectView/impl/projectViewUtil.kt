// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory

internal fun getNodeElement(userObject: Any?): Any? {
  return when (userObject) {
    is AbstractTreeNode<*> -> userObject.value
    is NodeDescriptor<*> -> userObject.element
    else -> null
  }
}

internal fun moduleContexts(project: Project, elements: Array<Any?>): List<Module> {
  val result = ArrayList<Module>()
  for (selectedValue in elements) {
    result += moduleContexts(project, selectedValue) ?: continue
  }
  return result
}

private fun moduleContexts(project: Project, element: Any?): Collection<Module>? {
  if (element is ModuleGroup) {
    return element.modulesInGroup(project, true)
  }
  else {
    return moduleContext(project, element)?.let(::listOf)
  }
}

internal fun moduleContext(project: Project, element: Any?): Module? {
  return when (element) {
    is Module -> if (element.isDisposed) null else element
    is PsiDirectory -> moduleBySingleContentRoot(project, element.virtualFile)
    is VirtualFile -> moduleBySingleContentRoot(project, element)
    else -> null
  }
}

/**
 * Project view has the same node for module and its single content root
 * => MODULE_CONTEXT data key should return the module when its content root is selected
 * When there are multiple content roots, they have different nodes under the module node
 * => MODULE_CONTEXT should be only available for the module node
 * otherwise VirtualFileArrayRule will return all module's content roots when just one of them is selected
 */
private fun moduleBySingleContentRoot(project: Project, file: VirtualFile): Module? {
  if (!ProjectRootsUtil.isModuleContentRoot(file, project)) {
    return null
  }
  val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file)
  if (module == null || module.isDisposed || ModuleRootManager.getInstance(module).contentRoots.size != 1) {
    return null
  }
  return module
}
