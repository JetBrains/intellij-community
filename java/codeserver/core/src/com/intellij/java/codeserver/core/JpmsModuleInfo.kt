// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaModuleSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.impl.light.LightJavaModule

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
    constructor(use: PsiJavaModule?, element: PsiElement) : this(use, use?.name ?: JavaModuleSystem.ALL_UNNAMED, {
      ModuleUtilCore.findModuleForPsiElement(element)
    })

    override val jpsModule: Module? by lazy { jps() }
  }

  /**
   * Represents the details of a target module
   */
  class TargetModuleInfo(element: PsiElement?, val packageName: String) : JpmsModuleInfo {
    override val jpsModule: Module? by lazy {
      if (element == null) return@lazy null
      ModuleUtilCore.findModuleForPsiElement(element)
    }
    override val module: PsiJavaModule? by lazy {
      JavaPsiModuleUtil.findDescriptorByElement(element)
    }

    /**
     * @return access information when the specified target module is accessed at a given place
     */
    fun accessAt(place: PsiFileSystemItem): JpmsModuleAccessInfo {
      val useModule = JavaPsiModuleUtil.findDescriptorByElement(place).let { if (it is LightJavaModule) null else it }
      val current = CurrentModuleInfo(useModule, place)
      return JpmsModuleAccessInfo(current, this)
    }
  }
}