// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.impl

import com.intellij.diagnostic.PluginException
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemProvider
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
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

    // TODO: cache all roots? (like passing through NavBarModelBuilder.traverseToRoot)
    // TODO: hash all roots? (Set instead of Sequence)
    val projectRootManager = ProjectRootManager.getInstance(item.data.project)
    val projectFileIndex = projectRootManager.fileIndex
    val defaultRoots = projectRootManager.contentRoots.asSequence()
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
      ensurePsiFromExtensionIsValid("Original parent is invalid", originalElement)
      return originalElement
    }
    else {
      return parent
    }
  }

  private fun adjustedParent(parent: PsiElement, ownerExtension: NavBarModelExtension?): PsiElement? {
    val originalParent = originalParent(parent)
    val adjustedByOwner = ownerExtension?.adjustElement(originalParent)
    ensurePsiFromExtensionIsValid("Owner extension returned invalid psi after adjustment", adjustedByOwner, ownerExtension)
    val adjustedWithAll = adjustedByOwner ?: adjustWithAllExtensions(originalParent)
    return adjustedWithAll
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
            child
              .let { if (ext.normalizeChildren()) adjustWithAllExtensions(it) else it }
              ?.let { PsiNavBarItem(it, ownerExtension = null) }
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

fun adjustWithAllExtensions(element: PsiElement?): PsiElement? {
  ensurePsiFromExtensionIsValid("Invalid psi passed to be adjusted", element)
  var result = element
  for (ext in NavBarModelExtension.EP_NAME.extensionList) {
    result = result?.let { ext.adjustElement(it) }
    ensurePsiFromExtensionIsValid("Invalid psi returned from ${ext::class.java} while adjusting", result, ext)
  }
  return null
}

private fun additionalRoots(project: Project): Iterable<VirtualFile> {
  val resultRoots = ArrayList<VirtualFile>()
  for (ext in NavBarModelExtension.EP_NAME.extensionList) {
    resultRoots.addAll(ext.additionalRoots(project))
  }
  return resultRoots
}

private fun ensurePsiFromExtensionIsValid(message: String, psi: PsiElement?, ext: NavBarModelExtension? = null) {
  if (psi == null) {
    return
  }
  try {
    PsiUtilCore.ensureValid(psi)
  }
  catch (t: Throwable) {
    if (ext != null) {
      throw PluginException.createByClass(message, t, ext::class.java)
    }
    else {
      throw t
    }
  }
}