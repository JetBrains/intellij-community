// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements

import com.intellij.java.workspace.entities.ModuleSourcePackagingElementEntity
import com.intellij.openapi.module.ModulePointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packaging.elements.PackagingElementOutputKind
import com.intellij.packaging.elements.PackagingElementResolvingContext
import com.intellij.packaging.impl.artifacts.workspacemodel.packaging.mutableElements
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation
import com.intellij.packaging.impl.ui.ModuleElementPresentation
import com.intellij.packaging.ui.ArtifactEditorContext
import com.intellij.packaging.ui.PackagingElementPresentation
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes

class ProductionModuleSourcePackagingElement : ModulePackagingElementBase {
  constructor(project: Project) : super(ProductionModuleSourceElementType.ELEMENT_TYPE, project)

  constructor(project: Project, modulePointer: ModulePointer) : super(ProductionModuleSourceElementType.ELEMENT_TYPE,
                                                                      project,
                                                                      modulePointer)

  override fun getSourceRoots(context: PackagingElementResolvingContext): Collection<VirtualFile> {
    val module = findModule(context) ?: return emptyList()

    val rootModel = context.modulesProvider.getRootModel(module)
    return rootModel.getSourceRoots(JavaModuleSourceRootTypes.PRODUCTION)
  }

  override fun createPresentation(context: ArtifactEditorContext): PackagingElementPresentation {
    return DelegatedPackagingElementPresentation(
      ModuleElementPresentation(myModulePointer, context, ProductionModuleSourceElementType.ELEMENT_TYPE))
  }

  override fun getFilesKind(context: PackagingElementResolvingContext) = PackagingElementOutputKind.OTHER

  override fun getOrAddEntity(diff: MutableEntityStorage, source: EntitySource, project: Project): WorkspaceEntity {
    val existingEntity = getExistingEntity(diff)
    if (existingEntity != null) return existingEntity

    val moduleName = this.moduleName
    val addedEntity = if (moduleName != null) {
      diff addEntity ModuleSourcePackagingElementEntity(source) {
        module = ModuleId(moduleName)
      }
    }
    else {
      diff addEntity ModuleSourcePackagingElementEntity(source)
    }
    diff.mutableElements.addMapping(addedEntity, this)
    return addedEntity
  }

  @NonNls
  override fun toString() = "module sources:" + moduleName!!
}

