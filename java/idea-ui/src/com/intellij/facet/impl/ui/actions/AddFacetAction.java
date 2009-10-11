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

package com.intellij.facet.impl.ui.actions;

import com.intellij.facet.FacetInfo;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.impl.ui.FacetEditorFacade;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbAware;

import java.util.Collection;

/**
 * @author nik
*/
public class AddFacetAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ui.actions.AddFacetAction");
  private final FacetEditorFacade myEditor;
  private final FacetType myType;

  public AddFacetAction(final FacetEditorFacade editor, final FacetType type) {
    super(type.getPresentableName(), null, type.getIcon());
    myEditor = editor;
    myType = type;
  }

  public void actionPerformed(AnActionEvent e) {
    FacetInfo parent = myEditor.getSelectedFacetInfo();
    final Collection<FacetInfo> facetInfos = myEditor.getFacetsByType(myType);
    String facetName = myType.getDefaultFacetName();
    int i = 2;
    while (facetExists(facetName, facetInfos)) {
      facetName = myType.getPresentableName() + i;
      i++;
    }
    final FacetTypeId<?> underlyingFacetType = myType.getUnderlyingFacetType();
    if (parent == null && underlyingFacetType == null || parent != null && parent.getFacetType().getId() == underlyingFacetType) {
      myEditor.createFacet(parent, myType, facetName);
    }
    else {
      LOG.assertTrue(parent != null);
      final FacetInfo grandParent = myEditor.getParent(parent);
      LOG.assertTrue(grandParent == null && underlyingFacetType == null ||
                     grandParent != null && grandParent.getFacetType().getId() == underlyingFacetType);
      myEditor.createFacet(grandParent, myType, facetName);
    }
  }

  private static boolean facetExists(final String facetName, final Collection<FacetInfo> facetInfos) {
    for (FacetInfo facetInfo : facetInfos) {
      if (facetInfo.getName().equals(facetName)) {
        return true;
      }
    }
    return false;
  }

  public void update(AnActionEvent e) {
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
}
