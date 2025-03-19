// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.impl.elements.ProductionModuleOutputElementType;
import com.intellij.packaging.impl.elements.ProductionModuleOutputPackagingElement;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ModuleOutputSourceItem extends PackagingSourceItem {
  private final Module myModule;

  public ModuleOutputSourceItem(@NotNull Module module) {
    myModule = module;
  }

  public Module getModule() {
    return myModule;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ModuleOutputSourceItem && myModule.equals(((ModuleOutputSourceItem)obj).myModule);
  }

  @Override
  public int hashCode() {
    return myModule.hashCode();
  }

  @Override
  public @NotNull SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    final ModulePointer modulePointer = ModulePointerManager.getInstance(context.getProject()).create(myModule);
    return new DelegatedSourceItemPresentation(new ModuleElementPresentation(modulePointer, context, ProductionModuleOutputElementType.ELEMENT_TYPE)) {
      @Override
      public int getWeight() {
        return SourceItemWeights.MODULE_OUTPUT_WEIGHT;
      }
    };
  }

  @Override
  public @NotNull List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    final ModulePointer modulePointer = ModulePointerManager.getInstance(context.getProject()).create(myModule);
    return Collections.singletonList(new ProductionModuleOutputPackagingElement(context.getProject(), modulePointer));
  }

  @Override
  public @NotNull PackagingElementOutputKind getKindOfProducedElements() {
    return PackagingElementOutputKind.DIRECTORIES_WITH_CLASSES;
  }
}
