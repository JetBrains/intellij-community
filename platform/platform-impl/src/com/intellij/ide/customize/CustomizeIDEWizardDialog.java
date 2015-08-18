/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.idea.Main;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBCardLayout;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class CustomizeIDEWizardDialog extends DialogWrapper implements ActionListener {
  private static final String BUTTONS = "BUTTONS";
  private static final String NO_BUTTONS = "NO_BUTTONS";

  private final JButton mySkipButton = new JButton("Skip All and Set Defaults");
  private final JButton myBackButton = new JButton("Back");
  private final JButton myNextButton = new JButton("Next");

  private final JBCardLayout myCardLayout = new JBCardLayout();
  protected final List<AbstractCustomizeWizardStep> mySteps = new ArrayList<AbstractCustomizeWizardStep>();
  private int myIndex = 0;
  private final JLabel myNavigationLabel = new JLabel();
  private final JLabel myHeaderLabel = new JLabel();
  private final JLabel myFooterLabel = new JLabel();
  private final CardLayout myButtonWrapperLayout = new CardLayout();
  private final JPanel myButtonWrapper = new JPanel(myButtonWrapperLayout);
  private JPanel myContentPanel;

  public CustomizeIDEWizardDialog() {
    super(null, true, true);
    setTitle("Customize " + ApplicationNamesInfo.getInstance().getProductName());
    getPeer().setAppIcons();
    initSteps();
    mySkipButton.addActionListener(this);
    myBackButton.addActionListener(this);
    myNextButton.addActionListener(this);
    myNavigationLabel.setEnabled(false);
    myFooterLabel.setEnabled(false);
    init();
    initCurrentStep(true);
    setSize(400, 300);
    System.setProperty(StartupActionScriptManager.STARTUP_WIZARD_MODE, "true");
  }

  public static void showCustomSteps(String stepsProviderName) {
    final CustomizeIDEWizardStepsProvider provider;

    try {
      Class<?> providerClass = Class.forName(stepsProviderName);
      provider = (CustomizeIDEWizardStepsProvider)providerClass.newInstance();
    }
    catch (Throwable e) {
      Main.showMessage("Configuration Wizard Failed", e);
      return;
    }

    new CustomizeIDEWizardDialog() {
      @Override
      protected void initSteps() {
        provider.initSteps(this, mySteps);
      }
    }.show();
  }

  @Override
  protected void dispose() {
    System.clearProperty(StartupActionScriptManager.STARTUP_WIZARD_MODE);
    super.dispose();
  }

  protected void initSteps() {
    mySteps.add(new CustomizeUIThemeStepPanel());
    if (SystemInfo.isMac) {
      mySteps.add(new CustomizeKeyboardSchemeStepPanel());
    }
    if (CustomizeDesktopEntryStep.isAvailable()) {
      mySteps.add(new CustomizeDesktopEntryStep("/UbuntuDesktopEntry.png"));
    }

    PluginGroups pluginGroups = new PluginGroups();
    mySteps.add(new CustomizePluginsStepPanel(pluginGroups));
    mySteps.add(new CustomizeFeaturedPluginsStepPanel(pluginGroups));
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
    result.setPreferredSize(JBUI.size(700, 600));
    result.setBorder(AbstractCustomizeWizardStep.createSmallEmptyBorder());
    return result;
  }

  @Override
  protected JComponent createSouthPanel() {
    final JPanel buttonPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets.right = 5;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 0;
    gbc.gridy = 0;
    if (!PlatformUtils.isCLion()) {
      buttonPanel.add(mySkipButton, gbc);
      gbc.gridx++;
    }
    buttonPanel.add(myBackButton, gbc);
    gbc.gridx++;
    gbc.weightx = 1;
    buttonPanel.add(Box.createHorizontalGlue(), gbc);
    gbc.gridx++;
    gbc.weightx = 0;
    buttonPanel.add(myNextButton, gbc);
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    myButtonWrapper.add(buttonPanel, BUTTONS);
    myButtonWrapper.add(new JLabel(), NO_BUTTONS);
    myButtonWrapperLayout.show(myButtonWrapper, BUTTONS);
    return myButtonWrapper;
  }

  void setButtonsVisible(boolean visible) {
    myButtonWrapperLayout.show(myButtonWrapper, visible ? BUTTONS : NO_BUTTONS);
  }

  @Override
  public void actionPerformed(@NotNull ActionEvent e) {
    if (e.getSource() == mySkipButton) {
      doOKAction();
      return;
    }
    if (e.getSource() == myBackButton) {
      myIndex--;
      initCurrentStep(false);
      return;
    }
    if (e.getSource() == myNextButton) {
      if (myIndex >= mySteps.size() - 1) {
        doOKAction();
        return;
      }
      myIndex++;
      initCurrentStep(true);
    }
  }

  @Nullable
  @Override
  protected ActionListener createCancelAction() {
    return null;//Prevent closing by <Esc>
  }

  @Override
  public void doCancelAction() {
    doOKAction();
  }

  @Override
  protected void doOKAction() {
    for (AbstractCustomizeWizardStep step : mySteps) {
      if (!step.beforeOkAction()) {
        int index = mySteps.indexOf(step);
        if (myIndex != index) {
          myIndex = index;
          initCurrentStep(true);
        }
        return;
      }
    }
    super.doOKAction();
  }

  private void initCurrentStep(boolean forward) {
    final AbstractCustomizeWizardStep myCurrentStep = mySteps.get(myIndex);
    myCurrentStep.beforeShown(forward);
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
}
