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

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.actions.RunInspectionAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionsConfigTreeTable;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLabelDecorator;
import com.intellij.ui.components.panels.StatelessCardLayout;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
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

  public InspectionNodeInfo(@NotNull final InspectionTree tree,
                            @NotNull final Project project) {
    setLayout(new GridBagLayout());
    setBorder(IdeBorderFactory.createEmptyBorder(11, 0, 0, 0));
    final InspectionToolWrapper toolWrapper = tree.getSelectedToolWrapper(false);
    LOG.assertTrue(toolWrapper != null);
    InspectionProfileImpl currentProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    final ToolsImpl tools = currentProfile.getTools(toolWrapper.getShortName(), project);
    boolean enabled = tools.isEnabled();

    JPanel titlePanel = new JPanel();
    titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.LINE_AXIS));
    JBLabelDecorator label = JBLabelDecorator.createJBLabelDecorator().setBold(true);
    label.setText(toolWrapper.getDisplayName() + " inspection");
    titlePanel.add(label);
    titlePanel.add(Box.createHorizontalStrut(JBUI.scale(16)));
    if (!enabled) {
      JBLabel enabledLabel = new JBLabel();
      enabledLabel.setForeground(JBColor.GRAY);
      enabledLabel.setText("Disabled");
      titlePanel.add(enabledLabel);
    }

    add(titlePanel,
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new JBInsets(0, 12, 5, 16),
                               0, 0));

    JEditorPane description = new JEditorPane();
    description.setContentType(UIUtil.HTML_MIME);
    description.setEditable(false);
    description.setOpaque(false);
    description.setBackground(UIUtil.getLabelBackground());
    description.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    final String toolDescription = toolWrapper.loadDescription();
    SingleInspectionProfilePanel.readHTML(description, SingleInspectionProfilePanel.toHTML(description, toolDescription == null ? "" : toolDescription, false));
    JScrollPane pane = ScrollPaneFactory.createScrollPane(description, true);
    int maxWidth = getFontMetrics(UIUtil.getLabelFont()).charWidth('f') * 110 - pane.getMinimumSize().width;
    pane.setMaximumSize(new Dimension(maxWidth, Integer.MAX_VALUE));
    pane.setAlignmentX(0);

    add(StatelessCardLayout.wrap(pane),
        new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                               new JBInsets(0, 10, 0, 0), 0, 0));
    JButton enableButton = new JButton((enabled ? "Disable" : "Enable") + " inspection");
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        InspectionsConfigTreeTable.setToolEnabled(!enabled, currentProfile, toolWrapper.getShortName(), project);
        tree.getContext().getView().profileChanged();
        return true;
      }
    }.installOn(enableButton);

    JButton runInspectionOnButton = new JButton(InspectionsBundle.message("run.inspection.on.file.intention.text"));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        RunInspectionAction.runInspection(project, toolWrapper.getShortName(), null, null, null);
        return true;
      }
    }.installOn(runInspectionOnButton);

    JPanel buttons = new JPanel();
    buttons.setLayout(new BoxLayout(buttons, BoxLayout.LINE_AXIS));
    buttons.add(enableButton);
    buttons.add(Box.createHorizontalStrut(JBUI.scale(3)));
    buttons.add(runInspectionOnButton);

    add(buttons,
        new GridBagConstraints(0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                               new JBInsets(15, 9, 9, 0), 0, 0));

  }
}
