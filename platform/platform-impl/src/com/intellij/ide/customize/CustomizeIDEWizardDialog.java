/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.customize;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBCardLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CustomizeIDEWizardDialog extends DialogWrapper implements ActionListener {
  private final JButton mySkipButton = new JButton("Skip All and Set Defaults");
  private final JButton myBackButton = new JButton("Back");
  private final JButton myNextButton = new JButton("Next");

  private final JBCardLayout myCardLayout = new JBCardLayout();
  protected final List<AbstractCustomizeWizardStep> mySteps = new ArrayList<AbstractCustomizeWizardStep>();
  private int myIndex = 0;
  private final JLabel myNavigationLabel = new JLabel();
  private final JLabel myHeaderLabel = new JLabel();
  private final JLabel myFooterLabel = new JLabel();
  private JPanel myContentPanel;

  public CustomizeIDEWizardDialog() {
    super(null, true, true);
    setTitle("Customize " + ApplicationNamesInfo.getInstance().getProductName());
    initSteps();
    mySkipButton.addActionListener(this);
    myBackButton.addActionListener(this);
    myNextButton.addActionListener(this);
    myNavigationLabel.setEnabled(false);
    myFooterLabel.setEnabled(false);
    init();
    initCurrentStep();
  }

  protected void initSteps() {
    mySteps.add(new CustomizeUIThemeStepPanel());
    if (SystemInfo.isMac) {
      mySteps.add(new CustomizeKeyboardSchemeStepPanel());
    }
    mySteps.add(new CustomizePluginsStepPanel());
    mySteps.add(new CustomizeFeaturedPluginsStepPanel());
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel result = new JPanel(new BorderLayout(5, 5));
    myContentPanel = new JPanel(myCardLayout);
    for (AbstractCustomizeWizardStep step : mySteps) {
      myContentPanel.add(step, step.getTitle());
    }
    JPanel topPanel = new JPanel(new BorderLayout(5, 5));
    topPanel.add(myNavigationLabel, BorderLayout.NORTH);
    topPanel.add(myHeaderLabel, BorderLayout.CENTER);
    result.add(topPanel, BorderLayout.NORTH);
    result.add(myContentPanel, BorderLayout.CENTER);
    result.add(myFooterLabel, BorderLayout.SOUTH);
    result.setPreferredSize(new Dimension(700, 600));
    result.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    return result;
  }

  @Override
  protected JComponent createSouthPanel() {
    final JPanel result = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets.right = 5;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 0;
    gbc.gridy = 0;
    result.add(mySkipButton, gbc);
    gbc.gridx++;
    result.add(myBackButton, gbc);
    gbc.gridx++;
    gbc.weightx = 1;
    result.add(Box.createHorizontalGlue(), gbc);
    gbc.gridx++;
    gbc.weightx = 0;
    result.add(myNextButton, gbc);
    result.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    return result;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == mySkipButton) {
      doOKAction();
      return;
    }
    if (e.getSource() == myBackButton) {
      myIndex--;
      initCurrentStep();
      return;
    }
    if (e.getSource() == myNextButton) {
      if (myIndex >= mySteps.size() - 1) {
        doOKAction();
        return;
      }
      myIndex++;
      initCurrentStep();
    }
  }

  @Override
  protected void doOKAction() {
    try {
      PluginManager.saveDisabledPlugins(PluginGroups.getInstance().getDisabledPluginIds(), false);
    }
    catch (IOException ignored) {
    }
    super.doOKAction();
  }

  private void initCurrentStep() {
    final AbstractCustomizeWizardStep myCurrentStep = mySteps.get(myIndex);
    myCardLayout.swipe(myContentPanel, myCurrentStep.getTitle(), JBCardLayout.SwipeDirection.AUTO, new Runnable() {
      @Override
      public void run() {
        Component component = myCurrentStep.getDefaultFocusedComponent();
        if (component != null) {
          component.requestFocus();
        }
      }
    });

    myBackButton.setVisible(myIndex > 0);
    if (myIndex > 0) {
      myBackButton.setText("Back to " + mySteps.get(myIndex - 1).getTitle());
    }
    mySkipButton.setText("Skip " + (myIndex > 0 ? "Remaining" : "All") + " and Set Defaults");

    myNextButton.setText(myIndex < mySteps.size() - 1
                         ? "Next: " + mySteps.get(myIndex + 1).getTitle()
                         : "Start using " + ApplicationNamesInfo.getInstance().getFullProductName());
    myHeaderLabel.setText(myCurrentStep.getHTMLHeader());
    myFooterLabel.setText(myCurrentStep.getHTMLFooter());
    StringBuilder navHTML = new StringBuilder("<html><body>");
    for (int i = 0; i < mySteps.size(); i++) {
      if (i > 0) navHTML.append("&nbsp;&#8594;&nbsp;");
      if (i == myIndex) navHTML.append("<b>");
      navHTML.append(mySteps.get(i).getTitle());
      if (i == myIndex) navHTML.append("</b>");
    }
    myNavigationLabel.setText(navHTML.toString());
  }


  private static <T extends Component> void getChildren(@NotNull Component c, Class<? extends T> cls, List<T> accumulator) {
    if (cls.isAssignableFrom(c.getClass())) accumulator.add((T)c);
    if (c instanceof Container) {
      Component[] components = ((Container)c).getComponents();
      for (Component component : components) {
        getChildren(component, cls, accumulator);
      }
    }
  }
}
