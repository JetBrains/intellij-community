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

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.Function;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.util.List;

public class RawCommandLineEditor extends JPanel implements TextAccessor {
  private final ExpandableTextField myEditor;
  private String myDialogCaption = "";

  public RawCommandLineEditor() {
    this(ParametersListUtil.DEFAULT_LINE_PARSER, ParametersListUtil.DEFAULT_LINE_JOINER);
  }

  public RawCommandLineEditor(final Function<String, List<String>> lineParser, final Function<List<String>, String> lineJoiner) {
    super(new BorderLayout());
    myEditor = new ExpandableTextField(lineParser, lineJoiner);
    add(myEditor, BorderLayout.CENTER);
    setDescriptor(null);
  }

  public void setDescriptor(FileChooserDescriptor descriptor) {
    setDescriptor(descriptor, true);
  }
  
  public void setDescriptor(FileChooserDescriptor descriptor, boolean insertSystemDependentPaths) {
    InsertPathAction.addTo(myEditor, descriptor, insertSystemDependentPaths);
  }

  @Deprecated
  public String getDialogCaption() {
    return myDialogCaption;
  }

  @Deprecated
  public void setDialogCaption(String dialogCaption) {
    myDialogCaption = dialogCaption != null ? dialogCaption : "";
  }

  @Override
  public void setText(@Nullable String text) {
    myEditor.setText(text);
  }

  @Override
  public String getText() {
    return StringUtil.notNullize(myEditor.getText());
  }

  public JTextField getTextField() {
    return myEditor;
  }

  public Document getDocument() {
    return myEditor.getDocument();
  }

  public void attachLabel(JLabel label) {
    label.setLabelFor(myEditor);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myEditor.setEnabled(enabled);
  }
}
