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
package com.intellij.ide.startupWizardV2;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

public class StartupWizard2 extends DialogWrapper implements ActionListener {

  private final JLabel myNavLabel = new JLabel();
  private final JLabel myHeaderLabel = new JLabel();
  private final JLabel myFooterLabel = new JLabel();
  private final JBCardLayout myCardLayout = new JBCardLayout();
  private final JPanel myContentPanel = new JPanel(myCardLayout);

  private final JButton mySkipButton = new JButton();
  private final JButton myBackwardButton = new JButton();
  private final JButton myForwardButton = new JButton();

  private List<AbstractWizardPage> myPages = Arrays.asList(new LafPage(), new KeymapPage(), new PluginsPage(), new FeaturedPluginsPage());
  private int myIndex = -1;

  public StartupWizard2() {
    super(null, true, true);
    IconLoader.activate();
    getPeer().setAppIcons();
    setResizable(false);
    setTitle("Customize" + ApplicationNamesInfo.getInstance().getProductName());
    init();
    showPage(0);
  }

  @Override
  protected void dispose() {
    super.dispose();
    IconLoader.deactivate();
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(700, 700);
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == mySkipButton) {
      doOKAction();
    }
    if (event.getSource() == myBackwardButton) {
      showPage(myIndex - 1);
    }
    if (event.getSource() == myForwardButton) {
      if (myIndex == myPages.size() - 1) {
        doOKAction();
      }
      else {
        showPage(myIndex + 1);
      }
    }
  }

  private void showPage(int index) {
    myIndex = index;
    AbstractWizardPage page = myPages.get(index);
    myCardLayout.swipe(myContentPanel, page.getID(), JBCardLayout.SwipeDirection.AUTO);
    updateNavLabel();
    myHeaderLabel.setText("<html><body><h2>" + page.getTitle() + "</h2>" + page.getHeader() + "</body></html>");
    myFooterLabel.setText("<html><body>" + page.getFooter() + "</body></html>");
    mySkipButton.setText(myIndex>0?"Skip All and Set Defaults" : "Skip Remaining and Set Defaults");
    myBackwardButton.setVisible(myIndex>0);
    if (myIndex > 0) {
      myBackwardButton.setText("Back to " + myPages.get(myIndex-1).getID());
    }
    myForwardButton.setText(myIndex < myPages.size() - 1 ? "Next: " + myPages.get(myIndex+1).getID() : "Start using " + ApplicationNamesInfo
      .getInstance().getFullProductName());
  }

  private void updateNavLabel() {
    StringBuilder sb = new StringBuilder("<html><body>");
    for (int i = 0; i < myPages.size(); i++) {
      AbstractWizardPage wizardPage = myPages.get(i);
      if (i > 0 && i < myPages.size() - 1) sb.append(" &#8594; ");
      if (i == myIndex) sb.append("<b>");
      sb.append(wizardPage.getID());
      if (i == myIndex) sb.append("</b>");
    }
    sb.append("</body></html>");
    myNavLabel.setText(sb.toString());
  }


  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    for (AbstractWizardPage page : myPages) {
      myContentPanel.add(page, page.getID());
    }
    JPanel mainPanel = new JPanel(new VerticalFlowLayout());
    mainPanel.add(myNavLabel);
    mainPanel.add(myHeaderLabel);
    mainPanel.add(
      new JBScrollPane(myContentPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
    mainPanel.add(myFooterLabel);
    return mainPanel;
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    JPanel result = new JPanel(new GridBagLayout());
    result.setBorder(new EmptyBorder(5, 5, 5, 5));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;
    result.add(mySkipButton, gbc);
    result.add(myBackwardButton, gbc);
    gbc.weightx = 1;
    result.add(Box.createHorizontalGlue(), gbc);
    gbc.weightx = 0;
    result.add(myForwardButton, gbc);

    mySkipButton.addActionListener(this);
    myBackwardButton.addActionListener(this);
    myForwardButton.addActionListener(this);

    return result;
  }
}
