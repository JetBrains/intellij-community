// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.impl.nodes.LibraryGroupNode
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal fun getNodeElement(userObject: Any?): Any? {
  return when (userObject) {
    is AbstractTreeNode<*> -> userObject.value
    is NodeDescriptor<*> -> userObject.element
    else -> null
  }
}

@ApiStatus.Internal
fun moduleContexts(project: Project, elements: Array<out Any>): List<Module> {
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

@ApiStatus.Internal
fun moduleContext(project: Project, element: Any?): Module? {
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

internal fun unloadedModules(project: Project, elements: Array<out Any>): List<UnloadedModuleDescription> {
  val result = SmartList<UnloadedModuleDescription>()
  for (element in elements) {
    val file = when (element) {
      is PsiDirectory -> element.virtualFile
      is VirtualFile -> element
      else -> continue
    }
    result += getUnloadedModuleByContentRoot(project, file) ?: continue
  }
  return result
}

private fun getUnloadedModuleByContentRoot(project: Project, file: VirtualFile): UnloadedModuleDescription? {
  val moduleName = ProjectRootsUtil.findUnloadedModuleByContentRoot(file, project) ?: return null
  return ModuleManager.getInstance(project).getUnloadedModuleDescription(moduleName)
}

internal fun getSelectedLibrary(userObjectsPath: Array<out Any?>?): LibraryOrderEntry? {
  if (userObjectsPath == null) {
    return null
  }
  val parentObject = userObjectsPath.getOrNull(userObjectsPath.size - 2)
  if (parentObject !is LibraryGroupNode) {
    return null
  }
  val userObject = userObjectsPath.last()
  if (userObject is NamedLibraryElementNode) {
    return userObject.value.orderEntry as? LibraryOrderEntry
  }
  val directory = (userObject as PsiDirectoryNode).value
  val grandParentObject = userObjectsPath.getOrNull(userObjectsPath.size - 3) as? AbstractTreeNode<*>
  val module = grandParentObject!!.value as Module?
               ?: return null
  return ModuleRootManager.getInstance(module).fileIndex.getOrderEntryForFile(directory.virtualFile) as? LibraryOrderEntry
}

internal fun getFileAttributes(file: VirtualFile?): BasicFileAttributes? {
  val ioFile = try {
    getFilePath(file)
  }
  catch (ignored: Exception) {
    // some implementations don't support this, pretend attributes don't exist
    null
  }
  return getFileAttributes(ioFile)
}

private fun getFileAttributes(ioFile: Path?): BasicFileAttributes? {
  val fileAttributes = try {
    if (ioFile == null) null else Files.readAttributes(ioFile, BasicFileAttributes::class.java)
  }
  catch (ignored: Exception) {
    null
  }
  return fileAttributes
}

internal fun getFileTimestamp(file: VirtualFile?): Long? {
  val ioFile = try {
    getFilePath(file)
  }
  catch (ignored: Exception) {
    // some implementations don't support this, use whatever time stamp VirtualFile provides
    return file?.timeStamp
  }
  return getFileAttributes(ioFile)?.lastModifiedTime()?.toMillis()
}

private fun getFilePath(file: VirtualFile?) =
  if (file == null || file.isDirectory || !file.isInLocalFileSystem) null else file.toNioPath()
