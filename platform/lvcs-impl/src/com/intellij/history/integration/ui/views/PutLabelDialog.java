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

package com.intellij.history.integration.ui.views;

import com.intellij.history.LocalHistory;
import com.intellij.history.integration.IdeaGateway;
import static com.intellij.history.integration.LocalHistoryBundle.message;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class PutLabelDialog extends DialogWrapper {
  private IdeaGateway myGateway;
  private final VirtualFile myFile;

  private JTextField myNameField;
  private JRadioButton myProjectButton;
  private JRadioButton myFileButton;

  public PutLabelDialog(IdeaGateway gw, VirtualFile f) {
    super(gw.getProject(), false);
    setTitle(message("put.label.dialog.title"));

    myGateway = gw;
    myFile = f;
    init();

    updateOkStatus();
  }

  @Override
  public void dispose() {
    myGateway = null;
    super.dispose();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    initNameField();

    JLabel l = new JLabel(message("put.label.name"));
    l.setLabelFor(myNameField);

    panel.add(l, atCell(0, 0));
    panel.add(myNameField, atCell(1, 0));

    if (canPutLabelOnSelectedFile()) {
      initGroupButtons();
      panel.add(new JLabel(message("put.label.on")), atCell(0, 1));
      panel.add(myProjectButton, atCell(1, 1));
      panel.add(myFileButton, atCell(1, 2));
    }

    panel.setPreferredSize(new Dimension(300, 50));
    return panel;
  }

  private void initNameField() {
    myNameField = new JTextField();
    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateOkStatus();
      }
    });
  }

  private void initGroupButtons() {
    myProjectButton = new JRadioButton(message("put.label.on.project"));
    myFileButton = new JRadioButton(message("put.label.on.file", myFile.getPath()));

    ButtonGroup group = new ButtonGroup();
    group.add(myProjectButton);
    group.add(myFileButton);

    myProjectButton.setSelected(true);
  }

  private GridBagConstraints atCell(int x, int y) {
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(4, 4, 4, 4);
    c.anchor = GridBagConstraints.EAST;
    c.fill = GridBagConstraints.BOTH;
    c.gridx = x;
    c.gridy = y;
    c.weightx = x;
    return c;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private void updateOkStatus() {
    setOKActionEnabled(getLabelName().trim().length() > 0);
  }

  @Override
  public void doOKAction() {
    if (canPutLabelOnSelectedFile() && myFileButton.isSelected()) {
      LocalHistory.putUserLabel(myGateway.getProject(), myFile, getLabelName());
    }
    else {
      LocalHistory.putUserLabel(myGateway.getProject(), getLabelName());
    }
    close(0);
  }

  private String getLabelName() {
    return myNameField.getText();
  }

  // test-support
  public void selectFileLabel() {
    myFileButton.setSelected(true);
  }

  public boolean canPutLabelOnSelectedFile() {
    return myFile != null && myGateway.getFileFilter().isAllowedAndUnderContentRoot(myFile);
  }
}
