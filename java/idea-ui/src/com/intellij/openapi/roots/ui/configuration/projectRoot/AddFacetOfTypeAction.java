// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.ChooseElementsDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

class AddFacetOfTypeAction extends DumbAwareAction {
  private final FacetType<?, ?> myFacetType;
  private final StructureConfigurableContext myContext;

  AddFacetOfTypeAction(final FacetType type, final StructureConfigurableContext context) {
    super(type.getPresentableName(), null, type.getIcon());
    myFacetType = type;
    myContext = context;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final FacetType<?, ?> type = myFacetType;
    if (type == null) return;

    final FacetTypeId<?> underlyingFacetType = type.getUnderlyingFacetType();
    if (underlyingFacetType == null) {
      addFacetToModule(type);
    }
    else {
      addSubFacet(type, underlyingFacetType);
    }
  }

  private void addSubFacet(FacetType<?, ?> type, FacetTypeId<?> underlyingType) {
    final ProjectFacetsConfigurator facetsConfigurator = myContext.getModulesConfigurator().getFacetsConfigurator();
    List<Facet> suitableParents = new ArrayList<>();
    for (Module module : myContext.getModules()) {
      if (type.isSuitableModuleType(ModuleType.get(module))) {
        suitableParents.addAll(facetsConfigurator.getFacetsByType(module, underlyingType));
      }
    }

    final Iterator<Facet> iterator = suitableParents.iterator();
    while (iterator.hasNext()) {
      Facet<?> parent = iterator.next();
      if (type.isOnlyOneFacetAllowed() && facetsConfigurator.hasFacetOfType(parent.getModule(), parent, type.getId())) {
        iterator.remove();
      }
    }

    final Project project = myContext.getProject();
    if (suitableParents.isEmpty()) {
      final String parentType = FacetTypeRegistry.getInstance().findFacetType(underlyingType).getPresentableName();
      Messages.showErrorDialog(project, JavaUiBundle.message("dialog.message.no.suitable.parent.0.facets.found", parentType),
                               JavaUiBundle.message("dialog.title.cannot.create.0.facet", type.getPresentableName()));
      return;
    }

    ChooseParentFacetDialog dialog = new ChooseParentFacetDialog(project, suitableParents);
    dialog.show();
    final List<Facet> chosen = dialog.getChosenElements();
    if (!dialog.isOK() || chosen.size() != 1) return;
    final Facet<?> parent = chosen.get(0);
    ProjectStructureConfigurable structureConfigurable = myContext.getModulesConfigurator().getProjectStructureConfigurable();
    final Facet<?> facet =
      structureConfigurable.getModulesConfig().getFacetEditorFacade().createAndAddFacet(type, parent.getModule(), parent);
    structureConfigurable.select(facet, true);
  }

  private void addFacetToModule(@NotNull FacetType<?, ?> type) {
    final ProjectFacetsConfigurator facetsConfigurator = myContext.getModulesConfigurator().getFacetsConfigurator();
    List<Module> suitableModules = new ArrayList<>(Arrays.asList(myContext.getModules()));
    final Iterator<Module> iterator = suitableModules.iterator();
    while (iterator.hasNext()) {
      Module module = iterator.next();
      if (!type.isSuitableModuleType(ModuleType.get(module)) || (type.isOnlyOneFacetAllowed() && facetsConfigurator.hasFacetOfType(module, null, type.getId()))) {
        iterator.remove();
      }
    }
    final Project project = myContext.getProject();
    if (suitableModules.isEmpty()) {
      Messages.showErrorDialog(project, JavaUiBundle.message("dialog.message.no.suitable.modules.for.0.facet.found", type.getPresentableName()),
                               JavaUiBundle.message("dialog.title.cannot.create.facet"));
      return;
    }

    final ChooseModulesDialog dialog = new ChooseModulesDialog(project, suitableModules, JavaUiBundle.message("choose.module"),
                                                               JavaUiBundle.message("facet.will.be.added.to.selected.module",
                                                                                    type.getPresentableName()));
    dialog.setSingleSelectionMode();
    dialog.show();
    final List<Module> elements = dialog.getChosenElements();
    if (!dialog.isOK() || elements.size() != 1) return;

    final Module module = elements.get(0);
    ProjectStructureConfigurable structureConfigurable = myContext.getModulesConfigurator().getProjectStructureConfigurable();
    final Facet<?> facet = structureConfigurable.getModulesConfig().getFacetEditorFacade().createAndAddFacet(type, module, null);
    structureConfigurable.select(facet, true);
  }

  public static AnAction[] createAddFacetActions(FacetStructureConfigurable configurable) {
    final List<AnAction> result = new ArrayList<>();
    final StructureConfigurableContext context = configurable.myContext;
    for (FacetType<?, ?> type : FacetTypeRegistry.getInstance().getSortedFacetTypes()) {
      if (hasSuitableModules(context, type)) {
        result.add(new AddFacetOfTypeAction(type, context));
      }
    }
    return result.toArray(AnAction.EMPTY_ARRAY);
  }

  private static boolean hasSuitableModules(StructureConfigurableContext context, FacetType<?, ?> type) {
    for (Module module : context.getModules()) {
      if (type.isSuitableModuleType(ModuleType.get(module))) {
        return true;
      }
    }
    return false;
  }

  private static final class ChooseParentFacetDialog extends ChooseElementsDialog<Facet> {
    private ChooseParentFacetDialog(Project project, List<? extends Facet> items) {
      super(project, items, JavaUiBundle.message("select.parent.facet"), null, true);
      myChooser.setSingleSelectionMode();
    }

    @Override
    protected String getItemText(Facet item) {
      return JavaUiBundle.message("item.name.with.module", item.getName(), item.getModule().getName());
    }

    @Override
    protected Icon getItemIcon(Facet item) {
      return item.getType().getIcon();
    }
  }
}
