/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.Function;
import com.intellij.util.execution.ParametersListUtil;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class RawCommandLineEditor extends JPanel implements TextAccessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.RawCommandLineEditor");

  private final TextFieldWithBrowseButton myTextField;
  private String myDialogCaption = "";

  public RawCommandLineEditor() {
    this(ParametersListUtil.DEFAULT_LINE_PARSER, ParametersListUtil.DEFAULT_LINE_JOINER);
  }

  public RawCommandLineEditor(final Function<String, List<String>> lineParser, final Function<List<String>, String> lineJoiner) {
    super(new BorderLayout());
    myTextField = new TextFieldWithBrowseButton(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myDialogCaption == null) {
          Container parent = getParent();
          if (parent instanceof LabeledComponent) {
            parent = parent.getParent();
          }
          LOG.error("Did not call RawCommandLineEditor.setDialogCaption() in " + parent);
          myDialogCaption = "Parameters";
        }
        Messages.showTextAreaDialog(myTextField.getTextField(), myDialogCaption, "EditParametersPopupWindow", lineParser, lineJoiner);
      }
    });
    myTextField.setButtonIcon(AllIcons.Actions.ShowViewer);
    add(myTextField, BorderLayout.CENTER);
    setDescriptor(null);
  }

  public void setDescriptor(FileChooserDescriptor descriptor) {
    InsertPathAction.addTo(myTextField.getTextField(), descriptor);
  }

  public String getDialogCaption() {
    return myDialogCaption;
  }

  public void setDialogCaption(String dialogCaption) {
    myDialogCaption = dialogCaption != null ? dialogCaption : "";
  }

  @Override
  public void setText(String text) {
    myTextField.setText(text);
  }

  @Override
  public String getText() {
    return myTextField.getText();
  }

  public JTextField getTextField() {
    return myTextField.getTextField();
  }

  public Document getDocument() {
    return myTextField.getTextField().getDocument();
  }

  public void attachLabel(JLabel label) {
    label.setLabelFor(myTextField.getTextField());
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myTextField.setEnabled(enabled);
  }
}
