// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.java.workspace.entities.ModuleOutputPackagingElementEntity;
import com.intellij.java.workspace.entities.PackagingElementEntity;
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

  @Override
  public @NonNls String toString() {
    return "module:" + getModuleName();
  }

  @Override
  public @NotNull Collection<VirtualFile> getSourceRoots(PackagingElementResolvingContext context) {
    Module module = findModule(context);
    if (module == null) return Collections.emptyList();

    ModuleRootModel rootModel = context.getModulesProvider().getRootModel(module);
    return rootModel.getSourceRoots(JavaModuleSourceRootTypes.PRODUCTION);
  }

  @Override
  public @NotNull PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DelegatedPackagingElementPresentation(new ModuleElementPresentation(myModulePointer, context, ProductionModuleOutputElementType.ELEMENT_TYPE));
  }

  @Override
  public PackagingElementEntity.Builder<? extends PackagingElementEntity> getOrAddEntityBuilder(@NotNull MutableEntityStorage diff,
                                                                                                @NotNull EntitySource source,
                                                                                                @NotNull Project project) {
    PackagingElementEntity existingEntity = (PackagingElementEntity)this.getExistingEntity(diff);
    if (existingEntity != null) return getBuilder(diff, existingEntity);

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
    return getBuilder(diff, addedEntity);
  }
}
