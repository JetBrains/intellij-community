// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightJavaModule
import com.intellij.psi.util.PsiUtil

/**
 * Represents a JPMS module and the corresponding module in IntelliJ project model
 */
sealed interface JpmsModuleInfo {
  val module: PsiJavaModule?
  val jpsModule: Module?

  /**
   * Represents the details of a current module.
   *
   * Note: "name" is not always possible to get from "module".
   *       For example, "module" can be "java.se", but the name is from the original module.
   *
   * @property module The PsiJavaModule instance representing the module.
   * @property name original module name
   * @property jpsModule JPS module initialization.
   */
  class CurrentModuleInfo(override val module: PsiJavaModule?, val name: String, jps: () -> Module? = { null }) : JpmsModuleInfo {
    constructor(use: PsiJavaModule?, element: PsiElement) : this(use, use?.name ?: JpmsModuleAccessInfo.ALL_UNNAMED, {
      ModuleUtilCore.findModuleForPsiElement(element)
    })

    override val jpsModule: Module? by lazy { jps() }
  }

  /**
   * Represents the details of a target module
   */
  interface TargetModuleInfo: JpmsModuleInfo {
    val packageName: String
    /**
     * @return access information when the specified target module is accessed at a given place
     */
    fun accessAt(place: PsiFileSystemItem): JpmsModuleAccessInfo {
      val useModule = JavaPsiModuleUtil.findDescriptorByElement(place).let { if (it is LightJavaModule) null else it }
      val current = CurrentModuleInfo(useModule, place)
      return JpmsModuleAccessInfo(current, this)
    }
  }

  class TargetModuleInfoByJavaModule(override val module: PsiJavaModule?, override val packageName: String) : TargetModuleInfo {
    override val jpsModule: Module? by lazy {
      if (module == null) return@lazy null
      ModuleUtilCore.findModuleForPsiElement(module)
    }
  }

  class TargetModuleInfoByFile(virtualFile: VirtualFile, project: Project, override val packageName: String) : TargetModuleInfo {
    override val jpsModule: Module? by lazy {
      ModuleUtilCore.findModuleForFile(virtualFile, project)
    }

    override val module: PsiJavaModule? by lazy {
      JavaPsiModuleUtil.findDescriptorByFile(virtualFile, project)
    }
  }

  companion object {
    /**
     * Find module info structures when accessing a given location.
     * 
     * @param targetPackageName package name which about to be accessed
     * @param targetFile concrete target file which is about to be accessed; null if not known (in this case, 
     * multiple results could be returned, as multiple source roots may define a given package)
     * @param place source place from where the access is requested
     * @return list of TargetModuleInfo structures that describe the possible target; empty list if the target package is empty
     * (which is generally an error), null if not applicable (e.g., modules are not supported at place; 
     * target does not belong to any module; etc.). In this case, no access problem should be reported.
     */
    @JvmStatic
    fun findTargetModuleInfos(targetPackageName: String, targetFile: PsiFile?, place: PsiFile): List<TargetModuleInfo>? {
      if (!PsiUtil.isAvailable(JavaFeature.MODULES, place)) return null

      val useVFile = place.virtualFile
      val project = place.project
      val index = ProjectFileIndex.getInstance(project)
      if (useVFile != null && index.isInLibrarySource(useVFile)) return null
      val targetVirtualFile = targetFile?.virtualFile
      if (targetVirtualFile != null && index.isInProject(targetVirtualFile)) {
        return listOf(TargetModuleInfoByFile(targetVirtualFile, project, targetPackageName))
      }
      if (useVFile == null) return null

      val target = JavaPsiFacade.getInstance(project).findPackage(targetPackageName) ?: return null
      val module = index.getModuleForFile(useVFile) ?: return null
      val test = index.isInTestSourceContent(useVFile)
      val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(test)
      val dirs = target.getDirectories(moduleScope)
      val packageName = target.qualifiedName
      if (dirs.isEmpty()) {
        return if (target.getFiles(moduleScope).isEmpty()) {
          listOf()
        }
        else {
          null
        }
      }

      return dirs.map { dir -> TargetModuleInfoByFile(dir.virtualFile, project, packageName) }
    }
  }
}