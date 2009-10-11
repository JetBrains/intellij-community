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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.module.Module;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.SourceItemPresentation;
import com.intellij.packaging.ui.SourceItemWeights;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleSourceItemGroup extends PackagingSourceItem {
  private final Module myModule;

  public ModuleSourceItemGroup(Module module) {
    super(false);
    myModule = module;
  }

  public SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new ModuleSourceItemPresentation(myModule);
  }

  public boolean equals(Object obj) {
    return obj instanceof ModuleSourceItemGroup && myModule.equals(((ModuleSourceItemGroup)obj).myModule);
  }

  public int hashCode() {
    return myModule.hashCode();
  }

  @NotNull
  public List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    return Collections.emptyList();
  }

  public Module getModule() {
    return myModule;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.append(myModule.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.setIcon(myModule.getModuleType().getNodeIcon(false));
  }

  private static class ModuleSourceItemPresentation extends SourceItemPresentation {
    private final Module myModule;

    public ModuleSourceItemPresentation(Module module) {
      myModule = module;
    }

    @Override
    public String getPresentableName() {
      return myModule.getName();
    }

    @Override
    public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                       SimpleTextAttributes commentAttributes) {
      presentationData.setClosedIcon(myModule.getModuleType().getNodeIcon(false));
      presentationData.setOpenIcon(myModule.getModuleType().getNodeIcon(true));
      presentationData.addText(myModule.getName(), mainAttributes);
    }

    @Override
    public int getWeight() {
      return SourceItemWeights.MODULE_WEIGHT;
    }
  }
}
