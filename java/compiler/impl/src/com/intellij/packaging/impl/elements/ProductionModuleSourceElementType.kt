// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements

import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModulePointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import javax.swing.Icon

class ProductionModuleSourceElementType private constructor() : ModuleElementTypeBase<ProductionModuleSourcePackagingElement>(
  "module-source", JavaCompilerBundle.messagePointer("element.type.name.module.source")) {

  override fun isSuitableModule(modulesProvider: ModulesProvider, module: Module): Boolean {
    return modulesProvider.getRootModel(module).getSourceRootUrls(false).isNotEmpty()
  }

  override fun createElement(project: Project, pointer: ModulePointer) = ProductionModuleSourcePackagingElement(project, pointer)
  override fun createEmpty(project: Project) = ProductionModuleSourcePackagingElement(project)
  override fun getCreateElementIcon(): Icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.Package)
  override fun getElementIcon(module: Module?): Icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.Package)
  override fun getElementText(moduleName: String) = JavaCompilerBundle.message("node.text.0.module.sources", moduleName)

  companion object {
    @JvmField
    val ELEMENT_TYPE = ProductionModuleSourceElementType()
  }
}
