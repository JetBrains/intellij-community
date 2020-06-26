// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.impl.ui.actions;

import com.intellij.facet.*;
import com.intellij.facet.impl.ui.FacetEditorFacade;
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.impl.AddFrameworkSupportInProjectStructureAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public final class AddFacetToModuleAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(AddFacetToModuleAction.class);
  private final FacetEditorFacade myEditor;
  private final Project myProject;
  private final FacetType myType;

  private AddFacetToModuleAction(final FacetEditorFacade editor, Project project, final FacetType type) {
    super(type.getPresentableName(), null, type.getIcon());
    myEditor = editor;
    myProject = project;
    myType = type;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FacetInfo parent = myEditor.getSelectedFacetInfo();
    final FacetTypeId<?> underlyingFacetType = myType.getUnderlyingFacetType();
    Facet facet;
    if (parent == null && underlyingFacetType == null || parent != null && parent.getFacetType().getId() == underlyingFacetType) {
      facet = myEditor.createFacet(parent, myType);
    }
    else {
      LOG.assertTrue(parent != null);
      final FacetInfo grandParent = myEditor.getParent(parent);
      LOG.assertTrue(grandParent == null && underlyingFacetType == null ||
                     grandParent != null && grandParent.getFacetType().getId() == underlyingFacetType);
      facet = myEditor.createFacet(grandParent, myType);
    }
    ProjectStructureConfigurable.getInstance(myProject).select(facet, true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(isVisible(myEditor, myType));
  }

  public static boolean isVisible(FacetEditorFacade editor, final FacetType<?, ?> type) {
    final ModuleType moduleType = editor.getSelectedModuleType();
    if (moduleType == null || !type.isSuitableModuleType(moduleType)) {
      return false;
    }

    final FacetTypeId<?> underlyingTypeId = type.getUnderlyingFacetType();
    final FacetInfo selectedFacet = editor.getSelectedFacetInfo();
    if (selectedFacet == null) {
      return underlyingTypeId == null && canAddFacet(null, type, editor);
    }

    final FacetTypeId selectedFacetType = selectedFacet.getFacetType().getId();
    if (selectedFacetType == underlyingTypeId) {
      return canAddFacet(selectedFacet, type, editor);
    }

    final FacetInfo parent = editor.getParent(selectedFacet);
    if (!canAddFacet(parent, type, editor)) {
      return false;
    }
    return parent == null && underlyingTypeId == null || parent != null && parent.getFacetType().getId() == underlyingTypeId;
  }

  private static boolean canAddFacet(final FacetInfo selectedFacet, final FacetType<?, ?> type, final FacetEditorFacade editor) {
    return !(type.isOnlyOneFacetAllowed() && editor.nodeHasFacetOfType(selectedFacet, type.getId()));
  }

  public static Collection<AnAction> createAddFrameworkActions(FacetEditorFacade editor, Project project) {
    SortedMap<String, AnAction> actions = new TreeMap<>();
    List<FrameworkTypeEx> frameworkTypes = DumbService.getInstance(project).filterByDumbAwareness(
      FrameworkTypeEx.EP_NAME.getExtensionList()
    );
    for (FrameworkTypeEx frameworkType : frameworkTypes) {
      final AnAction action = new AddFrameworkSupportInProjectStructureAction(frameworkType, frameworkType.createProvider(),
                                                                              ModuleStructureConfigurable.getInstance(project));
      actions.put(frameworkType.getPresentableName(), action);
    }
    for (FacetType type : FacetTypeRegistry.getInstance().getFacetTypes()) {
      actions.put(type.getPresentableName(), new AddFacetToModuleAction(editor, project, type));
    }
    return actions.values();
  }
}
