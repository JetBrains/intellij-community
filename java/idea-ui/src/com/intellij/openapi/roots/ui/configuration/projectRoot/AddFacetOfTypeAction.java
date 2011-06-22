/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.invalid.InvalidFacetType;
import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
* @author nik
*/
class AddFacetOfTypeAction extends DumbAwareAction {
  private FacetStructureConfigurable myFacetStructureConfigurable;

  AddFacetOfTypeAction(FacetStructureConfigurable facetStructureConfigurable) {
    super("New Facet", null, PlatformIcons.ADD_ICON);
    this.myFacetStructureConfigurable = facetStructureConfigurable;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final FacetType type = getSelectedType();
    if (type == null) return;

    final FacetTypeId underlyingFacetType = type.getUnderlyingFacetType();
    if (underlyingFacetType == null) {
      addFacetToModule(type);
    }
    else {
      addSubFacet(type, underlyingFacetType);
    }
  }

  private void addSubFacet(FacetType type, FacetTypeId<?> underlyingType) {
    final StructureConfigurableContext context = myFacetStructureConfigurable.myContext;
    final ProjectFacetsConfigurator facetsConfigurator = context.getModulesConfigurator().getFacetsConfigurator();
    List<Facet> suitableParents = new ArrayList<Facet>();
    for (Module module : context.getModules()) {
      if (type.isSuitableModuleType(module.getModuleType())) {
        suitableParents.addAll(facetsConfigurator.getFacetsByType(module, underlyingType));
      }
    }

    final Iterator<Facet> iterator = suitableParents.iterator();
    while (iterator.hasNext()) {
      Facet parent = iterator.next();
      if (type.isOnlyOneFacetAllowed() && facetsConfigurator.hasFacetOfType(parent.getModule(), parent, type.getId())) {
        iterator.remove();
      }
    }

    final Project project = context.getProject();
    if (suitableParents.isEmpty()) {
      final String parentType = FacetTypeRegistry.getInstance().findFacetType(underlyingType).getPresentableName();
      Messages.showErrorDialog(project, "No suitable parent " + parentType + " facets found", "Cannot Create " + type.getPresentableName() + " Facet");
      return;
    }

    ChooseParentFacetDialog dialog = new ChooseParentFacetDialog(project, suitableParents, "Select Parent Facet", null);
    dialog.show();
    final List<Facet> chosen = dialog.getChosenElements();
    if (!dialog.isOK() || chosen.size() != 1) return;
    final Facet parent = chosen.get(0);
    final Facet facet =
      ModuleStructureConfigurable.getInstance(project).getFacetEditorFacade().createAndAddFacet(type, parent.getModule(), parent);
    ProjectStructureConfigurable.getInstance(project).select(facet, true);
  }

  private void addFacetToModule(@NotNull FacetType type) {
    final StructureConfigurableContext context = myFacetStructureConfigurable.myContext;
    final ProjectFacetsConfigurator facetsConfigurator = context.getModulesConfigurator().getFacetsConfigurator();
    List<Module> suitableModules = new ArrayList<Module>(Arrays.asList(context.getModules()));
    final Iterator<Module> iterator = suitableModules.iterator();
    while (iterator.hasNext()) {
      Module module = iterator.next();
      if (!type.isSuitableModuleType(module.getModuleType()) || (type.isOnlyOneFacetAllowed() && facetsConfigurator.hasFacetOfType(module, null, type.getId()))) {
        iterator.remove();
      }
    }
    final Project project = context.getProject();
    if (suitableModules.isEmpty()) {
      Messages.showErrorDialog(project, "No suitable modules for " + type.getPresentableName() + " facet found.", "Cannot Create Facet");
      return;
    }

    final ChooseModulesDialog dialog = new ChooseModulesDialog(project, suitableModules, "Choose Module",
                                                               type.getPresentableName() + " facet will be added to selected module");
    dialog.setSingleSelectionMode();
    dialog.show();
    final List<Module> elements = dialog.getChosenElements();
    if (!dialog.isOK() || elements.size() != 1) return;

    final Module module = elements.get(0);
    final Facet facet = ModuleStructureConfigurable.getInstance(project).getFacetEditorFacade().createAndAddFacet(type, module, null);
    ProjectStructureConfigurable.getInstance(project).select(facet, true);
  }

  @Override
  public void update(AnActionEvent e) {
    final FacetType type = getSelectedType();
    e.getPresentation().setEnabled(myFacetStructureConfigurable.myContext.getModules().length > 0 && type != null && !(type instanceof InvalidFacetType));
  }

  @Nullable
  private FacetType getSelectedType() {
    final NamedConfigurable configurable = myFacetStructureConfigurable.getSelectedConfugurable();
    if (configurable instanceof FacetTypeConfigurable) {
      return ((FacetTypeConfigurable)configurable).getFacetType();
    }
    else if (configurable instanceof FacetConfigurable) {
      return ((FacetConfigurable)configurable).getEditableObject().getType();
    }
    return null;
  }

  private static class ChooseParentFacetDialog extends ChooseElementsDialog<Facet> {
    private ChooseParentFacetDialog(Project project, List<? extends Facet> items, String title, String description) {
      super(project, items, title, description, true);
      myChooser.setSingleSelectionMode();
    }

    @Override
    protected String getItemText(Facet item) {
      return item.getName() + " (module " + item.getModule().getName() + ")";
    }

    @Override
    protected Icon getItemIcon(Facet item) {
      return item.getType().getIcon();
    }
  }
}
