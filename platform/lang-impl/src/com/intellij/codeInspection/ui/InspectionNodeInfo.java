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
package com.intellij.codeInspection.ui;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Dmitry Batkovich
 */
public class InspectionNodeInfo extends JPanel {
  private final static Logger LOG = Logger.getInstance(InspectionNodeInfo.class);

  private final JButton myButton;
  private final SimpleColoredComponent myTitle;
  private final HighlightDisplayKey myKey;
  private final InspectionProfileImpl myCurrentProfile;
  private final String myName;
  private final Project myProject;

  public InspectionNodeInfo(final InspectionToolWrapper toolWrapper, Project project) {
    setLayout(new GridBagLayout());
    setBorder(IdeBorderFactory.createEmptyBorder(0, 3, 0, 0));
    myProject = project;
    myTitle = new SimpleColoredComponent();
    myCurrentProfile = (InspectionProfileImpl)InspectionProjectProfileManager.getInstance(project).getProjectProfileImpl();
    myKey = HighlightDisplayKey.find(toolWrapper.getID());
    myName = toolWrapper.getDisplayName();
    myButton = new JButton();

    add(myTitle,
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new JBInsets(0, 2, 0, 0),
                               0, 0));

    JEditorPane description = new JEditorPane();
    description.setContentType(UIUtil.HTML_MIME);
    description.setEditable(false);
    description.setOpaque(false);
    description.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    final String toolDescription = toolWrapper.loadDescription();
    SingleInspectionProfilePanel.readHTML(description, SingleInspectionProfilePanel.toHTML(description, toolDescription == null ? "" : toolDescription, true));

    add(ScrollPaneFactory.createScrollPane(description, true),
        new GridBagConstraints(0, 1, 1, 1, 0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                               new JBInsets(5, 5, 0, 0), 0, 0));
    add(myButton,
        new GridBagConstraints(0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                               new JBInsets(15, 0, 0, 0), 0, 0));
    updateEnableButtonText(false);

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        updateEnableButtonText(true);
        return true;
      }
    }.installOn(myButton);
  }

  private void updateEnableButtonText(boolean revert) {
    boolean isEnabled = myCurrentProfile.isToolEnabled(myKey);
    if (revert) {
      final boolean isEnabledAsFinal = isEnabled;
      DisableInspectionToolAction.modifyAndCommitProjectProfile(model -> {
        if (isEnabledAsFinal) {
          model.disableTool(myKey.getID(), myProject);
        }
        else {
          ((InspectionProfileImpl)model).enableTool(myKey.getID(), myProject);
        }
      }, myProject);
      isEnabled = !isEnabled;
    }
    myButton.setText((isEnabled ? "Disable" : "Enable") + " inspection");
    myTitle.clear();
    myTitle.append(myName);
    if (!isEnabled) {
      myTitle.append(" Disabled", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }
}
