// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingElementOutputKind
import com.intellij.packaging.impl.elements.ProductionModuleSourceElementType
import com.intellij.packaging.impl.elements.ProductionModuleSourcePackagingElement
import com.intellij.packaging.impl.ui.ModuleElementPresentation
import com.intellij.packaging.ui.ArtifactEditorContext
import com.intellij.packaging.ui.PackagingSourceItem
import com.intellij.packaging.ui.SourceItemPresentation
import com.intellij.packaging.ui.SourceItemWeights

class ModuleSourceSourceItem(val module: Module) : PackagingSourceItem() {
  override fun equals(other: Any?) = other is ModuleSourceSourceItem && module == other.module
  override fun hashCode() = module.hashCode()

  override fun createPresentation(context: ArtifactEditorContext): SourceItemPresentation {
    val modulePointer = ModulePointerManager.getInstance(context.project).create(module)
    return object : DelegatedSourceItemPresentation(ModuleElementPresentation(modulePointer, context, ProductionModuleSourceElementType.ELEMENT_TYPE)) {
      override fun getWeight(): Int = SourceItemWeights.MODULE_SOURCE_WEIGHT
    }
  }

  override fun createElements(context: ArtifactEditorContext): List<PackagingElement<*>> {
    val modulePointer = ModulePointerManager.getInstance(context.project).create(module)
    return listOf(ProductionModuleSourcePackagingElement(context.project, modulePointer))
  }

  override fun getKindOfProducedElements() = PackagingElementOutputKind.OTHER
}

