/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class EditSettingsAction extends InspectionViewActionBase {
  public EditSettingsAction() {
    super(InspectionsBundle.message("inspection.action.edit.settings"),
          InspectionsBundle.message("inspection.action.edit.settings"),
          AllIcons.General.Settings);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final InspectionResultsView view = getView(e);
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(view.getProject());
    final InspectionToolWrapper toolWrapper = view.getTree().getSelectedToolWrapper();
    InspectionProfile inspectionProfile = view.getCurrentProfile();
    final boolean profileIsDefined = view.isProfileDefined();
    if (!profileIsDefined) {
      inspectionProfile = guessProfileToSelect(view, profileManager);
    }

    if (toolWrapper != null) {
      final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName()); //do not search for dead code entry point tool
      if (key != null) {
        if (new EditInspectionToolsSettingsAction(key)
              .editToolSettings(view.getProject(), (InspectionProfileImpl)inspectionProfile, profileIsDefined)
            && profileIsDefined) {
          view.updateCurrentProfile();
        }
        return;
      }
    }
    if (EditInspectionToolsSettingsAction.editToolSettings(view.getProject(), inspectionProfile, profileIsDefined, null) && profileIsDefined) {
      view.updateCurrentProfile();
    }
  }

  private static InspectionProfile guessProfileToSelect(final InspectionResultsView view,
                                                        final InspectionProjectProfileManager profileManager) {
    final Set<InspectionProfile> profiles = new HashSet<InspectionProfile>();
    final RefEntity[] selectedElements = view.getTree().getSelectedElements();
    for (RefEntity selectedElement : selectedElements) {
      if (selectedElement instanceof RefElement) {
        final RefElement refElement = (RefElement)selectedElement;
        final PsiElement element = refElement.getElement();
        if (element != null) {
          profiles.add(profileManager.getInspectionProfile());
        }
      }
    }
    if (profiles.isEmpty()) {
      return (InspectionProfile)profileManager.getProjectProfileImpl();
    }
    return profiles.iterator().next();
  }
}
