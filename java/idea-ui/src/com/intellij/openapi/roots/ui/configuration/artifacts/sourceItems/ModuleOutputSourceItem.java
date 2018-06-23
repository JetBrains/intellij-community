/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.impl.elements.ProductionModuleOutputElementType;
import com.intellij.packaging.impl.elements.ProductionModuleOutputPackagingElement;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.packaging.ui.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleOutputSourceItem extends PackagingSourceItem {
  private final Module myModule;

  public ModuleOutputSourceItem(@NotNull Module module) {
    myModule = module;
  }

  public Module getModule() {
    return myModule;
  }

  public boolean equals(Object obj) {
    return obj instanceof ModuleOutputSourceItem && myModule.equals(((ModuleOutputSourceItem)obj).myModule);
  }

  public int hashCode() {
    return myModule.hashCode();
  }

  @NotNull
  @Override
  public SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    final ModulePointer modulePointer = ModulePointerManager.getInstance(context.getProject()).create(myModule);
    return new DelegatedSourceItemPresentation(new ModuleElementPresentation(modulePointer, context, ProductionModuleOutputElementType.ELEMENT_TYPE)) {
      @Override
      public int getWeight() {
        return SourceItemWeights.MODULE_OUTPUT_WEIGHT;
      }
    };
  }

  @Override
  @NotNull
  public List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    final ModulePointer modulePointer = ModulePointerManager.getInstance(context.getProject()).create(myModule);
    return Collections.singletonList(new ProductionModuleOutputPackagingElement(context.getProject(), modulePointer));
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getKindOfProducedElements() {
    return PackagingElementOutputKind.DIRECTORIES_WITH_CLASSES;
  }
}
