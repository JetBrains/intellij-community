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

/*
 * @author: Eugene Zhuravlev
 * Date: Jan 22, 2003
 * Time: 2:41:11 PM
 */
package com.intellij.compiler.progress;

import com.intellij.CommonBundle;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CompilerProgressDialog extends DialogWrapper{
  private final JLabel myStatusLabel = new JLabel();
  private final JLabel myStatisticsLabel = new JLabel();
  private final JButton myCancelButton = new JButton(CommonBundle.getCancelButtonText());
  private final JButton myBackgroundButton = new JButton(CommonBundle.getBackgroundButtonText());
  private final JPanel myFunPanel = new JPanel(new BorderLayout());
  private final CompilerTask myTask;

  public CompilerProgressDialog(final CompilerTask task, Project project){
    super(project, false);
    myTask = task;
    setTitle(CompilerBundle.message("compile.progress.title"));
    init();
    setCrossClosesWindow(false);
  }

  public Container getContentPane() {
    return getRootPane() != null? super.getContentPane() : null;
  }

  public void setStatusText(String text) {
    myStatusLabel.setText(text);
  }

  public void setStatisticsText(String text) {
    myStatisticsLabel.setText(text);
  }

  protected JComponent createCenterPanel(){
    return myFunPanel;
  }

  protected Border createContentPaneBorder(){
    return null;
  }

  protected JComponent createNorthPanel(){
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    myStatisticsLabel.setHorizontalAlignment(SwingConstants.LEFT);
    myStatisticsLabel.setPreferredSize(new Dimension(380,20));
    myStatisticsLabel.setMinimumSize(new Dimension(380,20));
    panel.add(
      myStatisticsLabel,
      new GridBagConstraints(0,0,1,1,1,1,GridBagConstraints.SOUTHWEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,2,0),0,0)
    );

    myStatusLabel.setHorizontalAlignment(SwingConstants.LEFT);
    myStatusLabel.setPreferredSize(new Dimension(380,20));
    myStatusLabel.setMinimumSize(new Dimension(380,20));
    panel.add(
      myStatusLabel,
      new GridBagConstraints(0,1,1,1,1,1,GridBagConstraints.NORTHWEST,GridBagConstraints.HORIZONTAL,new Insets(2,0,0,0),0,0)
    );

    myCancelButton.setFocusPainted(false);
    panel.add(
      myCancelButton,
      new GridBagConstraints(1,0,1,1,0,1,GridBagConstraints.SOUTH,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0)
    );

    myBackgroundButton.setFocusPainted(false);
    panel.add(
      myBackgroundButton,
      new GridBagConstraints(1,1,1,1,0,1,GridBagConstraints.NORTH,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0)
    );

    myCancelButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myCancelButton.setEnabled(false);
          myTask.cancel();
        }
      }
    );

    myBackgroundButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myBackgroundButton.setEnabled(false);
          myTask.sendToBackground();
        }
      }
    );

    return panel;
  }

  protected JComponent createSouthPanel() {
    return null;
  }

  public void doCancelAction() {
    myCancelButton.setEnabled(false);
    myTask.cancel();
  }

  protected boolean isProgressDialog() {
    return true;
  }

  public String getStatusText() {
    return myStatusLabel.getText();
  }

  public String getStatistics() {
    return myStatisticsLabel.getText();
  }

}
