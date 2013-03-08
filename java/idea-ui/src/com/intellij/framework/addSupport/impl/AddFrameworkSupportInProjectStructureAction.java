/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.addSupport.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.IdeaModifiableModelsProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class AddFrameworkSupportInProjectStructureAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.framework.addSupport.impl.AddFrameworkSupportInProjectStructureAction");
  private final FrameworkTypeEx myFrameworkType;
  private final FrameworkSupportInModuleProvider myProvider;
  @NotNull private final ModuleStructureConfigurable myModuleStructureConfigurable;

  public AddFrameworkSupportInProjectStructureAction(@NotNull FrameworkTypeEx frameworkType, @NotNull FrameworkSupportInModuleProvider provider,
                                                     @NotNull ModuleStructureConfigurable moduleStructureConfigurable) {
    super(frameworkType.getPresentableName(), "Add " + frameworkType.getPresentableName() + " support", frameworkType.getIcon());
    myFrameworkType = frameworkType;
    myProvider = provider;
    myModuleStructureConfigurable = moduleStructureConfigurable;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(isVisible());
  }

  private boolean isVisible() {
    final Module module = getSelectedModule();
    if (module == null || !myProvider.isEnabledForModuleType(ModuleType.get(module))) {
      return false;
    }
    final ProjectFacetsConfigurator facetsProvider = myModuleStructureConfigurable.getFacetConfigurator();
    if (!myProvider.canAddSupport(module, facetsProvider)) {
      return false;
    }

    final String underlyingFrameworkTypeId = myFrameworkType.getUnderlyingFrameworkTypeId();
    if (underlyingFrameworkTypeId == null) return true;

    final FrameworkSupportInModuleProvider underlyingProvider = FrameworkSupportUtil.findProvider(underlyingFrameworkTypeId, FrameworkSupportUtil.getAllProviders());
    if (underlyingProvider == null) {
      LOG.error("framework not found by id " + underlyingFrameworkTypeId);
    }
    return underlyingProvider.isSupportAlreadyAdded(module, facetsProvider);
  }

  @Nullable
  private Module getSelectedModule() {
    final Object selected = myModuleStructureConfigurable.getSelectedObject();
    if (selected instanceof Module) {
      return (Module)selected;
    }
    final Facet facet = getSelectedFacet();
    return facet != null ? facet.getModule() : null;
  }

  @Nullable
  private Facet getSelectedFacet() {
    final Object selected = myModuleStructureConfigurable.getSelectedObject();
    if (selected instanceof Facet) {
      return ((Facet)selected);
    }
    return null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Module module = getSelectedModule();
    if (module == null) return;

    final LibrariesContainer librariesContainer = LibrariesContainerFactory.createContainer(myModuleStructureConfigurable.getContext());
    new AddSupportForSingleFrameworkDialog(module, myFrameworkType, myProvider, librariesContainer, new IdeaModifiableModelsProvider()).show();
  }
}
