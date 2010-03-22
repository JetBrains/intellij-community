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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.FacetEditorImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.NamedConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class FacetConfigurable extends NamedConfigurable<Facet> {
  private final Facet myFacet;
  private final ModulesConfigurator myModulesConfigurator;
  private String myFacetName;

  public FacetConfigurable(final Facet facet, final ModulesConfigurator modulesConfigurator, final Runnable updateTree) {
    super(!facet.getType().isOnlyOneFacetAllowed(), updateTree);
    myFacet = facet;
    myModulesConfigurator = modulesConfigurator;
    myFacetName = myFacet.getName();
  }


  public void setDisplayName(String name) {
    name = name.trim();
    if (!name.equals(myFacetName)) {
      getFacetsConfigurator().getOrCreateModifiableModel(myFacet.getModule()).rename(myFacet, name);
      myFacetName = name;
    }
  }

  private ProjectFacetsConfigurator getFacetsConfigurator() {
    return myModulesConfigurator.getFacetsConfigurator();
  }

  public Facet getEditableObject() {
    return myFacet;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("facet.banner.text", myFacetName);
  }

  public JComponent createOptionsPanel() {
    return getEditor().getComponent();
  }

  public FacetEditorImpl getEditor() {
    return getFacetsConfigurator().getOrCreateEditor(myFacet);
  }

  @Nls
  public String getDisplayName() {
    return myFacetName;
  }

  @Nullable
  public Icon getIcon() {
    return myFacet.getType().getIcon();
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    final FacetEditorImpl facetEditor = getFacetsConfigurator().getEditor(myFacet);
    return facetEditor != null ? facetEditor.getHelpTopic() : null;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }
}
