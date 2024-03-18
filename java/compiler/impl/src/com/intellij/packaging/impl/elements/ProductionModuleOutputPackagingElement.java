// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.java.workspace.entities.ModuleOutputPackagingElementEntity;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.elements.PackagingExternalMapping;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.platform.workspace.jps.entities.ModuleId;
import com.intellij.platform.workspace.storage.EntitySource;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.Collection;
import java.util.Collections;

public class ProductionModuleOutputPackagingElement extends ModuleOutputPackagingElementBase {
  public ProductionModuleOutputPackagingElement(@NotNull Project project) {
    super(ProductionModuleOutputElementType.ELEMENT_TYPE, project);
  }

  public ProductionModuleOutputPackagingElement(@NotNull Project project, @NotNull ModulePointer modulePointer) {
    super(ProductionModuleOutputElementType.ELEMENT_TYPE, project, modulePointer);
  }

  @NonNls @Override
  public String toString() {
    return "module:" + getModuleName();
  }

  @NotNull
  @Override
  public Collection<VirtualFile> getSourceRoots(PackagingElementResolvingContext context) {
    Module module = findModule(context);
    if (module == null) return Collections.emptyList();

    ModuleRootModel rootModel = context.getModulesProvider().getRootModel(module);
    return rootModel.getSourceRoots(JavaModuleSourceRootTypes.PRODUCTION);
  }

  @Override
  @NotNull
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DelegatedPackagingElementPresentation(new ModuleElementPresentation(myModulePointer, context, ProductionModuleOutputElementType.ELEMENT_TYPE));
  }

  @Override
  public WorkspaceEntity getOrAddEntity(@NotNull MutableEntityStorage diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    String moduleName = this.getModuleName();
    ModuleOutputPackagingElementEntity addedEntity;
    if (moduleName != null) {
      addedEntity = diff.addEntity(ModuleOutputPackagingElementEntity.create(source, entityBuilder -> {
        entityBuilder.setModule(new ModuleId(moduleName));
        return Unit.INSTANCE;
      }));
    }
    else {
      addedEntity = diff.addEntity(ModuleOutputPackagingElementEntity.create(source));
    }
    diff.getMutableExternalMapping(PackagingExternalMapping.key).addMapping(addedEntity, this);
    return addedEntity;
  }
}
