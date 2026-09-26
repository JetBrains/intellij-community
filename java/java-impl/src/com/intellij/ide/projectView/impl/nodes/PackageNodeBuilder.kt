// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage

internal class PackageNodeBuilder(
  private val module: Module?,
  private val inLibrary: Boolean,
) {

  private val subpackagesCache = hashMapOf<PsiPackage, Array<PsiPackage>>()

  fun createPackageViewChildrenOnFiles(
    sourceRoots: List<VirtualFile>,
    project: Project,
    settings: ViewSettings,
  ): Collection<AbstractTreeNode<*>> {
    val psiManager = PsiManager.getInstance(project)

    val children: MutableList<AbstractTreeNode<*>> = ArrayList()
    val topLevelPackages: MutableSet<PsiPackage> = HashSet()

    for (root in sourceRoots) {
      ProgressManager.checkCanceled()
      val directory = psiManager.findDirectory(root)
      if (directory == null) {
        continue
      }
      val directoryPackage = JavaDirectoryService.getInstance().getPackage(directory)
      if (directoryPackage == null || PackageUtil.isPackageDefault(directoryPackage)) {
        val subdirectories: Array<PsiDirectory> = directory.subdirectories
        for (subdirectory in subdirectories) {
          val aPackage = JavaDirectoryService.getInstance().getPackage(subdirectory)
          if (aPackage != null && !PackageUtil.isPackageDefault(aPackage)) {
            topLevelPackages.add(aPackage)
          }
        }
        children.addAll(ProjectViewDirectoryHelper.getInstance(project).getDirectoryChildren(directory, settings, false))
      }
      else {
        topLevelPackages.add(directoryPackage)
      }
    }

    for (topLevelPackage in topLevelPackages) {
      addPackageAsChild(children, topLevelPackage, settings)
    }

    return children
  }

  fun addPackageAsChild(
    children: MutableCollection<in AbstractTreeNode<*>>,
    aPackage: PsiPackage,
    settings: ViewSettings,
  ) {
    val shouldSkipPackage = settings.isHideEmptyMiddlePackages && isPackageEmpty(aPackage, !settings.isFlattenPackages)
    val project = aPackage.project
    if (!shouldSkipPackage) {
      children.add(PackageElementNode(project, PackageElement(module, aPackage, inLibrary), settings))
    }
    if (settings.isFlattenPackages || shouldSkipPackage) {
      val subpackages = getSubpackages(aPackage)
      for (subpackage in subpackages) {
        addPackageAsChild(children, subpackage, settings)
      }
    }
  }

  fun isPackageEmpty(
    aPackage: PsiPackage,
    strictlyEmpty: Boolean,
  ): Boolean {
    val project = aPackage.project
    val scopeToShow = PackageUtil.getScopeToShow(project, module, inLibrary)
    val children: Array<out PsiFile> = aPackage.getFiles(scopeToShow)
    if (children.isNotEmpty()) {
      return false
    }
    val subPackages = getSubpackages(aPackage)
    if (strictlyEmpty) {
      return subPackages.size == 1
    }
    return subPackages.isNotEmpty()
  }

  fun getSubpackages(aPackage: PsiPackage): Array<PsiPackage> = subpackagesCache.computeIfAbsent(aPackage) {
    val scopeToShow = PackageUtil.getScopeToShow(aPackage.project, module, inLibrary)
    val result: MutableList<PsiPackage> = java.util.ArrayList()
    for (psiPackage in aPackage.getSubPackages(scopeToShow)) {
      // skip "default" subpackages as they should be attributed to other modules
      // this is the case when contents of one module are nested into contents of another
      val name = psiPackage.name
      if (!name.isNullOrEmpty()) {
        result.add(psiPackage)
      }
    }
    result.toTypedArray()
  }

}
