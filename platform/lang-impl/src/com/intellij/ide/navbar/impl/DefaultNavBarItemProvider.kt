// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.impl

import com.intellij.diagnostic.PluginException
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemProvider
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.PsiUtilCore.getVirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.*


/**
 * Delegates old implementations
 *
 * @see com.intellij.ide.navigationToolbar.NavBarModelExtension
 */
class DefaultNavBarItemProvider : NavBarItemProvider {

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun findParent(item: NavBarItem): NavBarItem? {
    if (item !is PsiNavBarItem) return null
    if (!item.data.isValid) return null

    // TODO: cache all roots? (like passing through NavBarModelBuilder.traverseToRoot)
    // TODO: hash all roots? (Set instead of Sequence)
    val projectFileIndex = ProjectRootManager.getInstance(item.data.project).fileIndex
    val defaultRoots = item.data.project.getBaseDirectories()
    val oldEpRoots = additionalRoots(item.data.project)
    val allRoots = (defaultRoots + oldEpRoots).filter { it.parent == null || !projectFileIndex.isInContent(it.parent) }

    if (getVirtualFile(item.data) in allRoots) return null

    val parent = fromOldExtensions({ ext ->
      try {
        ext.getParent(item.data)
      }
      catch (pce: ProcessCanceledException) {
        // implementations may throw PCE manually, try to replace it with expected exception
        ProgressManager.checkCanceled()
        null
      }
    }, { parent ->
      parent != item.data
    })
    if (parent == null || !parent.isValid) return null

    val containingFile = parent.containingFile
    if (containingFile != null && containingFile.virtualFile == null) return null

    val adjustedParent = adjustedParent(parent, item.ownerExtension)

    if (adjustedParent != null) {
      return PsiNavBarItem(adjustedParent, item.ownerExtension)
    }
    else {
      return null
    }
  }

  private fun originalParent(parent: PsiElement): PsiElement {
    val originalElement = parent.originalElement
    if (originalElement !is PsiCompiledElement || parent is PsiCompiledElement) {
      ensurePsiFromExtensionIsValid(originalElement, "Original parent is invalid", parent.javaClass)
      return originalElement
    }
    else {
      return parent
    }
  }

  private fun adjustedParent(parent: PsiElement, ownerExtension: NavBarModelExtension?): PsiElement? {
    val originalParent = originalParent(parent)
    val adjustedByOwner = ownerExtension?.adjustElement(originalParent)
    if (adjustedByOwner != null) {
      ensurePsiFromExtensionIsValid(adjustedByOwner, "Owner extension returned invalid psi after adjustment", ownerExtension.javaClass)
      return adjustedByOwner
    } else {
      return adjustWithAllExtensions(originalParent)
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun iterateChildren(item: NavBarItem): Iterable<NavBarItem> {

    if (item !is DefaultNavBarItem<*>) return Iterable { Collections.emptyIterator() }

    return NavBarModelExtension.EP_NAME
      .extensionList.asSequence()
      .flatMap { ext ->
        val children = arrayListOf<Any>()
        ext.processChildren(item.data, null /*TODO: think about passing root here*/) {
          children.add(it)
          true
        }
        children.asSequence().map { child -> Pair(child, ext) }
      }
      .mapNotNull { (child, ext) ->
        when (child) {
          is Project -> ProjectNavBarItem(child)
          is Module -> ModuleNavBarItem(child)
          is PsiElement -> {
            if (ext.normalizeChildren()) {
              val adjusted = adjustWithAllExtensions(child)
              adjusted?.let { PsiNavBarItem(it, ownerExtension = null) }
            }
            else {
              PsiNavBarItem(child, ownerExtension = null)
            }
          }
          is OrderEntry -> OrderEntryNavBarItem(child)
          else -> DefaultNavBarItem(child)
        }
      }
      .asIterable()
  }
}


fun <T> fromOldExtensions(selector: (ext: NavBarModelExtension) -> T?): T? {
  for (ext in NavBarModelExtension.EP_NAME.extensionList) {
    val selected = selector(ext)
    if (selected != null) {
      return selected
    }
  }
  return null
}

fun <T> fromOldExtensions(selector: (ext: NavBarModelExtension) -> T?, predicate: (T) -> Boolean): T? {
  for (ext in NavBarModelExtension.EP_NAME.extensionList) {
    val selected = selector(ext)
    if (selected != null && predicate(selected)) {
      return selected
    }
  }
  return null
}

fun adjustWithAllExtensions(element: PsiElement): PsiElement? {
  var result = element

  for (ext in NavBarModelExtension.EP_NAME.extensionList.asReversed()) {
    result = ext.adjustElement(result) ?: return null
    ensurePsiFromExtensionIsValid(result, "Invalid psi returned from ${ext.javaClass} while adjusting", ext.javaClass)
  }
  return result
}

private fun additionalRoots(project: Project): Iterable<VirtualFile> {
  val resultRoots = ArrayList<VirtualFile>()
  for (ext in NavBarModelExtension.EP_NAME.extensionList) {
    resultRoots.addAll(ext.additionalRoots(project))
  }
  return resultRoots
}

internal fun ensurePsiFromExtensionIsValid(psi: PsiElement, message: String, clazz: Class<*>? = null) {
  try {
    PsiUtilCore.ensureValid(psi)
  }
  catch (t: Throwable) {
    if (clazz != null) {
      throw PluginException.createByClass(message, t, clazz)
    }
    else {
      throw IllegalStateException(message, t)
    }
  }
}