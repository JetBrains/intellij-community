/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.invalid.InvalidFacet;
import com.intellij.facet.impl.ui.FacetEditorImpl;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.FacetProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class FacetConfigurable extends ProjectStructureElementConfigurable<Facet> {
  private final Facet myFacet;
  private final ModulesConfigurator myModulesConfigurator;
  private @NlsSafe String myFacetName;
  private final FacetProjectStructureElement myProjectStructureElement;
  private final Map<Facet, FacetConfigurable> myFacetConfigurables;

  public FacetConfigurable(Map<Facet, FacetConfigurable> facetConfigurables,
                           Facet facet,
                           StructureConfigurableContext context,
                           Runnable updateTree) {
    super(!facet.getType().isOnlyOneFacetAllowed() && !(facet instanceof InvalidFacet), updateTree);
    myFacet = facet;
    myModulesConfigurator = context.getModulesConfigurator();
    myFacetName = myFacet.getName();
    myProjectStructureElement = new FacetProjectStructureElement(context, facet);
    myFacetConfigurables = facetConfigurables;
  }


  @Override
  public void setDisplayName(String name) {
    var module = myFacet.getModule();
    // facet names must be unique within the same module and facet type
    var facetNameExists = myFacetConfigurables.keySet().stream()
      .filter(facet -> facet.getModule() == module && facet.getTypeId() == myFacet.getTypeId())
      .map(facet -> myFacetConfigurables.get(facet))
      .anyMatch(facetConfigurable -> name.equals(facetConfigurable.getDisplayName()));
    if (!facetNameExists) {
      getFacetsConfigurator().getOrCreateModifiableModel(module).rename(myFacet, name);
      myFacetName = name;
    }
  }

  @Override
  public ProjectStructureElement getProjectStructureElement() {
    return myProjectStructureElement;
  }

  private ProjectFacetsConfigurator getFacetsConfigurator() {
    return myModulesConfigurator.getFacetsConfigurator();
  }

  @Override
  public Facet getEditableObject() {
    return myFacet;
  }

  @Override
  public String getBannerSlogan() {
    return JavaUiBundle.message("facet.banner.text", myFacetName);
  }

  @Override
  public JComponent createOptionsPanel() {
    return getEditor().getComponent();
  }

  public FacetEditorImpl getEditor() {
    return getFacetsConfigurator().getOrCreateEditor(myFacet);
  }

  @Override
  @Nls
  public String getDisplayName() {
    return myFacetName;
  }

  @Override
  @Nullable
  public Icon getIcon(boolean open) {
    return myFacet.getType().getIcon();
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    final FacetEditorImpl facetEditor = getFacetsConfigurator().getEditor(myFacet);
    return facetEditor != null ? facetEditor.getHelpTopic() : null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
  }
}
