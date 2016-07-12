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
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.event.ActionEvent;

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
    InspectionProfile inspectionProfile = view.getCurrentProfile();

    if (view.isSingleInspectionRun()) {
      InspectionToolWrapper tool = inspectionProfile.getInspectionTool(inspectionProfile.getSingleTool(), view.getProject());
      JComponent panel = tool.getTool().createOptionsPanel();
      if (panel != null) {
        final DialogBuilder builder = new DialogBuilder()
          .title(InspectionsBundle.message("inspection.tool.window.inspection.dialog.title", tool.getDisplayName()))
          .centerPanel(panel);
        builder.removeAllActions();
        builder.addOkAction();
        if (view.isRerunAvailable()) {
          builder.addActionDescriptor(new DialogBuilder.DialogActionDescriptor(InspectionsBundle.message("inspection.action.rerun"), 'R') {
            @Override
            protected Action createAction(DialogWrapper dialogWrapper) {
              return new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                  view.rerun();
                  dialogWrapper.close(DialogWrapper.OK_EXIT_CODE);
                }
              };
            }
          });
        }
        builder.show();
      } else {
        Messages.showInfoMessage(view.getProject(),
                                 InspectionsBundle.message("inspection.tool.window.dialog.no.options", tool.getDisplayName()),
                                 InspectionsBundle.message("inspection.tool.window.dialog.title"));
      }
    } else {
      final InspectionToolWrapper toolWrapper = view.getTree().getSelectedToolWrapper(false);
      if (toolWrapper != null) {
        final HighlightDisplayKey key = HighlightDisplayKey.find(toolWrapper.getShortName()); //do not search for dead code entry point tool
        if (key != null) {
          if (new EditInspectionToolsSettingsAction(key).editToolSettings(view.getProject(), (InspectionProfileImpl)inspectionProfile, false)) {
            view.updateCurrentProfile();
          }
          return;
        }
      }

      final String[] path = view.getTree().getSelectedGroupPath();
      if (EditInspectionToolsSettingsAction.editSettings(view.getProject(), inspectionProfile, false, (c) -> {
        if (path != null) {
          c.selectInspectionGroup(path);
        }
      })) {
        view.updateCurrentProfile();
      }
    }
  }
}
