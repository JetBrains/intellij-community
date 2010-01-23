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
package com.intellij.openapi.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionListener;

public class TextFieldWithBrowseButton extends ComponentWithBrowseButton<JTextField> {
  public TextFieldWithBrowseButton(){
    this((ActionListener)null);
  }

  public TextFieldWithBrowseButton(JTextField field){
    this(field, null);
  }

  public TextFieldWithBrowseButton(JTextField field, ActionListener browseActionListener) {
    super(field, browseActionListener);
    if (ApplicationManager.getApplication() != null) {
      final DataManager manager = DataManager.getInstance();
      if (manager != null) {
        installPathCompletion(PlatformDataKeys.PROJECT.getData(manager.getDataContext()),
                              FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
      }
    }
  }

  public TextFieldWithBrowseButton(ActionListener browseActionListener) {
    this(new JTextField(), browseActionListener);
  }

  public void addBrowseFolderListener(String title, String description, Project project, FileChooserDescriptor fileChooserDescriptor) {
    addBrowseFolderListener(title, description, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    installPathCompletion(project, fileChooserDescriptor);
  }

  protected void installPathCompletion(final Project project, final FileChooserDescriptor fileChooserDescriptor) {
    installPathCompletion(project, fileChooserDescriptor, null);
  }

  protected void installPathCompletion(final Project project, final FileChooserDescriptor fileChooserDescriptor, Disposable parent) {
    final Application application = ApplicationManager.getApplication();
     if (application == null || application.isUnitTestMode() || application.isHeadlessEnvironment()) return;
     FileChooserFactory.getInstance().installFileCompletion(getChildComponent(), fileChooserDescriptor, true, parent);
   }
        
  public JTextField getTextField() {
    return getChildComponent();
  }

  /**
   * @return trimmed text
   */
  public String getText(){
    return getTextField().getText();
  }

  public void setText(final String text){
    getTextField().setText(text);
  }

  public boolean isEditable() {
    return getTextField().isEditable();
  }

  public void setEditable(boolean b) {
    getTextField().setEditable(b);

    getButton().setFocusable(!b);
    getTextField().setFocusable(b);
  }

  public static class NoPathCompletion extends TextFieldWithBrowseButton {
    public NoPathCompletion() {
    }

    public NoPathCompletion(final JTextField field) {
      super(field);
    }

    public NoPathCompletion(final JTextField field, final ActionListener browseActionListener) {
      super(field, browseActionListener);
    }

    public NoPathCompletion(final ActionListener browseActionListener) {
      super(browseActionListener);
    }

    protected void installPathCompletion(final Project project, final FileChooserDescriptor fileChooserDescriptor) {
    }
  }
}
