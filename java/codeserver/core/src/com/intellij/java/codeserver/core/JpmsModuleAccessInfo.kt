// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightJavaModule
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.JavaMultiReleaseUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.indexing.DumbModeAccessType

/**
 * Represents the access details between the current module and the target module.
 *
 * @property current The current module.
 * @property target The target module.
 */
data class JpmsModuleAccessInfo(val current: JpmsModuleInfo.CurrentModuleInfo, val target: JpmsModuleInfo.TargetModuleInfo) {
  enum class JpmsModuleAccessProblem {
    FROM_NAMED,
    FROM_UNNAMED,
    TO_UNNAMED,
    PACKAGE_BAD_NAME,
    BAD_NAME,
    PACKAGE_NOT_IN_GRAPH,
    NOT_IN_GRAPH,
    PACKAGE_DOES_NOT_READ,
    DOES_NOT_READ,
    JPS_DEPENDENCY_PROBLEM
  }

  /**
   * Access mode to determine whether the target is accessible
   */
  enum class JpmsModuleAccessMode {
    /**
     * Consider the target as accessible if the source actually reads the target
     */
    READ,

    /**
     * Consider the target as accessible if it's exported to the source (even if the source doesn't read it)
     */
    EXPORT
  }

  fun checkAccess(
    place: PsiFileSystemItem,
    accessMode: JpmsModuleAccessMode,
  ): JpmsModuleAccessProblem? {
    val targetModule = target.module
    if (targetModule != null) {
      if (targetModule == current.module) {
        return null
      }

      val currentJpsModule = current.jpsModule
      if (current.module == null) {
        val origin = targetModule.containingFile?.virtualFile
        if (origin == null || currentJpsModule == null ||
            ModuleRootManager.getInstance(currentJpsModule).fileIndex.getOrderEntryForFile(origin) !is JdkOrderEntry
        ) {
          return null  // a target is not on the mandatory module path
        }

        if (!accessibleFromJdkModules(place, accessMode) &&
            !inAddedModules(currentJpsModule, targetModule.name) &&
            !hasUpgrade(currentJpsModule, targetModule.name, target.packageName, place)) {
          return JpmsModuleAccessProblem.PACKAGE_NOT_IN_GRAPH
        }
      }

      if (targetModule !is LightJavaModule &&
          !JavaPsiModuleUtil.exports(targetModule, target.packageName, current.module) &&
          (currentJpsModule == null || !inAddedExports(currentJpsModule, targetModule.name, target.packageName, current.name)) &&
          (currentJpsModule == null || !isPatchedModule(targetModule.name, currentJpsModule, place))) {
        return if (current.module == null) JpmsModuleAccessProblem.FROM_UNNAMED else JpmsModuleAccessProblem.FROM_NAMED
      }

      if (current.module != null &&
          targetModule.name != PsiJavaModule.JAVA_BASE &&
          !this.isAccessible(accessMode) &&
          !inAddedReads(current.module, targetModule)) {
        return when {
          PsiNameHelper.isValidModuleName(targetModule.name, current.module) -> JpmsModuleAccessProblem.PACKAGE_DOES_NOT_READ
          else -> JpmsModuleAccessProblem.PACKAGE_BAD_NAME
        }
      }
    }
    else if (current.module != null) {
      val autoModule = JpmsModuleInfo.TargetModuleInfo(detectAutomaticModule(target), target.packageName)
      if (autoModule.module == null) {
        return JpmsModuleAccessProblem.TO_UNNAMED
      }
      else if (!JpmsModuleAccessInfo(current, autoModule).isAccessible(accessMode) &&
               !inAddedReads(current.module, null) &&
               !inSameMultiReleaseModule(current, target)) {
        return JpmsModuleAccessProblem.TO_UNNAMED
      }
    }

    return null
  }

  private fun isAccessible(accessMode: JpmsModuleAccessMode): Boolean {
    return when (accessMode) {
      JpmsModuleAccessMode.READ -> isAccessible()
      JpmsModuleAccessMode.EXPORT -> isExported()
    }
  }

  /**
   * @param place place where the target module is accessed
   * @return access problem, or null if the target module is accessible without any problem
   */
  fun checkModuleAccess(place: PsiElement): JpmsModuleAccessProblem? {
    val targetModule = target.module
    if (targetModule != null) {
      if (targetModule == current.module) {
        return null
      }

      val currentJpsModule = current.jpsModule
      if (current.module == null) {
        var origin = targetModule.containingFile?.virtualFile
        if (origin == null && targetModule is LightJavaModule) origin = targetModule.rootVirtualFile
        if (origin == null || currentJpsModule == null) return null

        if (ModuleRootManager.getInstance(currentJpsModule).fileIndex.getOrderEntryForFile(origin) !is JdkOrderEntry) {
          val searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(currentJpsModule)
          if (searchScope.contains(origin)) return null
          return JpmsModuleAccessProblem.JPS_DEPENDENCY_PROBLEM
        }

        if (!accessibleFromJdkModules(place, JpmsModuleAccessMode.READ) &&
            !inAddedModules(currentJpsModule, targetModule.name)) {
          return JpmsModuleAccessProblem.NOT_IN_GRAPH
        }
      }

      if (current.module != null &&
          targetModule.name != PsiJavaModule.JAVA_BASE &&
          !isAccessible() &&
          !inAddedReads(current.module, targetModule)) {
        return if (PsiNameHelper.isValidModuleName(targetModule.name, current.module)) JpmsModuleAccessProblem.DOES_NOT_READ
        else JpmsModuleAccessProblem.BAD_NAME
      }
    }
    else if (current.module != null) {
      val autoModule = JpmsModuleInfo.TargetModuleInfo(detectAutomaticModule(target), target.packageName)
      if (autoModule.module != null &&
          !JpmsModuleAccessInfo(current, autoModule).isAccessible() &&
          !inAddedReads(current.module, null) &&
          !inSameMultiReleaseModule(current, target)) {
        return JpmsModuleAccessProblem.TO_UNNAMED
      }
    }

    return null
  }

  fun isExported(): Boolean {
    val targetModule = target.module ?: return false
    if (!targetModule.isPhysical || JavaPsiModuleUtil.exports(targetModule, target.packageName, current.module)) return true
    val currentJpsModule = current.jpsModule ?: return false
    return inAddedExports(currentJpsModule, targetModule.name, target.packageName, current.name)
  }

  fun isAccessible(): Boolean {
    val currentModule = current.module ?: return false
    val targetModule = target.module ?: return false
    return JavaPsiModuleUtil.reads(currentModule, targetModule)
  }

  private fun accessibleFromJdkModules(
    place: PsiElement,
    accessMode: JpmsModuleAccessMode,
  ): Boolean {
    val jpsModule = current.jpsModule ?: return false
    val targetModule = target.module ?: return false
    if (targetModule.name == PsiJavaModule.JAVA_BASE) return true

    if (!isJdkModule(jpsModule, targetModule)) return false
    // https://bugs.openjdk.org/browse/JDK-8197532
    val jdkModulePred: (PsiJavaModule) -> Boolean = if (PsiUtil.isAvailable(JavaFeature.AUTO_ROOT_MODULES, place)) {
      { module -> module.exports.any { e -> e.moduleNames.isEmpty() } }
    }
    else {
      val javaSE = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode<PsiJavaModule, Throwable> {
        JavaPsiFacade.getInstance(place.project).findModule("java.se", jpsModule.moduleWithLibrariesScope)
      }

      if (javaSE != null) {
        { module ->
          (!module.name.startsWith("java.") && module.exports.any { e -> e.moduleNames.isEmpty() }) ||
          JpmsModuleAccessInfo(JpmsModuleInfo.CurrentModuleInfo(javaSE, current.name) { jpsModule }, target).isAccessible(accessMode)
        }
      }
      else {
        { _ -> true }
      }
    }
    val noIncubatorPred: (PsiJavaModule) -> Boolean = { module -> !module.doNotResolveByDefault() }
    return jdkModulePred(targetModule) && noIncubatorPred(targetModule)
  }

  private fun isJdkModule(jpsModule: Module, psiModule: PsiJavaModule): Boolean {
    val sdkHomePath = toLocalVirtualFile(ModuleRootManager.getInstance(jpsModule).getSdk()?.homeDirectory)
    val moduleFilePath = toLocalVirtualFile(psiModule.containingFile?.virtualFile)

    if (sdkHomePath != null && moduleFilePath != null) {
      return VfsUtilCore.isAncestor(sdkHomePath, moduleFilePath, false)
    }
    else {
      return psiModule.name.startsWith("java.") ||
             psiModule.name.startsWith("jdk.")
    }
  }

  private fun toLocalVirtualFile(file: VirtualFile?): VirtualFile? {
    if (file == null) return null
    return VfsUtilCore.getVirtualFileForJar(file) ?: file
  }

  private fun inSameMultiReleaseModule(current: JpmsModuleInfo, target: JpmsModuleInfo): Boolean {
    val placeModule = current.jpsModule ?: return false
    val targetModule = target.jpsModule ?: return false
    return JavaMultiReleaseUtil.areMainAndAdditionalMultiReleaseModules(targetModule, placeModule)
  }

  private fun detectAutomaticModule(current: JpmsModuleInfo): PsiJavaModule? {
    val module = current.jpsModule ?: return null
    return JavaPsiFacade.getInstance(module.project)
      .findModule(LightJavaModule.moduleName(module.name),
                  GlobalSearchScope.moduleScope(module))
  }

  private fun hasUpgrade(module: Module, targetName: String, packageName: String, place: PsiFileSystemItem): Boolean {
    if (PsiJavaModule.UPGRADEABLE.contains(targetName)) {
      val target = JavaPsiFacade.getInstance(module.project).findPackage(packageName)
      if (target != null) {
        val useVFile = place.virtualFile
        if (useVFile != null) {
          val index = ModuleRootManager.getInstance(module).fileIndex
          val test = index.isInTestSourceContent(useVFile)
          val dirs = target.getDirectories(module.getModuleWithDependenciesAndLibrariesScope(test))
          return dirs.any { index.getOrderEntryForFile(it.virtualFile) !is JdkOrderEntry }
        }
      }
    }

    return false
  }

  private fun isPatchedModule(targetModuleName: String, module: Module, place: PsiFileSystemItem): Boolean {
    val virtualFile = place.virtualFile ?: return false
    val rootForFile = ProjectRootManager.getInstance(place.project).fileIndex.getSourceRootForFile(virtualFile) ?: return false
    return JavaCompilerConfigurationProxy.isPatchedModuleRoot(targetModuleName, module, rootForFile)
  }

  private fun inAddedExports(module: Module, targetName: String, packageName: String, useName: String): Boolean {
    val options = JavaCompilerConfigurationProxy.getAdditionalOptions(module.project, module)
    if (options.isEmpty()) return false
    val prefix = "${targetName}/${packageName}="
    return JavaCompilerConfigurationProxy.optionValues(options, ADD_EXPORTS_OPTION)
      .filter { it.startsWith(prefix) }
      .map { it.substring(prefix.length) }
      .flatMap { it.splitToSequence(",") }
      .any { it == useName }
  }

  private fun inAddedModules(module: Module, moduleName: String): Boolean {
    val options = JavaCompilerConfigurationProxy.getAdditionalOptions(module.project, module)
    return JavaCompilerConfigurationProxy.optionValues(options, ADD_MODULES_OPTION)
      .flatMap { it.splitToSequence(",") }
      .any { it == moduleName || it == ALL_SYSTEM || it == ALL_MODULE_PATH }
  }

  private fun inAddedReads(fromJavaModule: PsiJavaModule, toJavaModule: PsiJavaModule?): Boolean {
    val fromModule = ModuleUtilCore.findModuleForPsiElement(fromJavaModule) ?: return false
    val options = JavaCompilerConfigurationProxy.getAdditionalOptions(fromModule.project, fromModule)
    return JavaCompilerConfigurationProxy.optionValues(options, ADD_READS_OPTION)
      .flatMap { it.splitToSequence(",") }
      .any {
        val (optFromModuleName, optToModuleName) = it.split("=").apply { it.first() to it.last() }
        fromJavaModule.name == optFromModuleName &&
        (toJavaModule?.name == optToModuleName || (optToModuleName == ALL_UNNAMED && isUnnamedModule(toJavaModule)))
      }
  }

  private fun isUnnamedModule(module: PsiJavaModule?) = module == null || module is LightJavaModule

  companion object {
    const val ALL_UNNAMED: String = "ALL-UNNAMED"
    const val ALL_SYSTEM: String = "ALL-SYSTEM"
    const val ALL_MODULE_PATH: String = "ALL-MODULE-PATH"
    const val ADD_EXPORTS_OPTION: String = "--add-exports"
    const val ADD_MODULES_OPTION: String = "--add-modules"
    const val ADD_READS_OPTION: String = "--add-reads"
  }
}