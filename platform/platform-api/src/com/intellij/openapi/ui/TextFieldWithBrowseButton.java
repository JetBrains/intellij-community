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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.TextComponentUndoProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;

public class TextFieldWithBrowseButton extends ComponentWithBrowseButton<JTextField> implements TextAccessor {
  public TextFieldWithBrowseButton(){
    this((ActionListener)null);
  }

  public TextFieldWithBrowseButton(JTextField field){
    this(field, null);
  }

  public TextFieldWithBrowseButton(JTextField field, @Nullable ActionListener browseActionListener) {
    super(field, browseActionListener);
    if (ApplicationManager.getApplication() != null) {
      installPathCompletion(FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
      new TextComponentUndoProvider(getTextField());
    }
  }

  public TextFieldWithBrowseButton(ActionListener browseActionListener) {
    this(new JTextField(10/* to prevent field to be infinitely resized in grid-box layouts */), browseActionListener);
  }

  public void addBrowseFolderListener(@Nullable String title, @Nullable String description, @Nullable Project project, FileChooserDescriptor fileChooserDescriptor) {
    addBrowseFolderListener(title, description, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    installPathCompletion(fileChooserDescriptor);
  }

  public void addBrowseFolderListener(@NotNull TextBrowseFolderListener listener) {
    listener.setOwnerComponent(this);
    addBrowseFolderListener(null, listener, true);
    installPathCompletion(listener.getFileChooserDescriptor());
  }

  protected void installPathCompletion(final FileChooserDescriptor fileChooserDescriptor) {
    installPathCompletion(fileChooserDescriptor, null);
  }

  protected void installPathCompletion(final FileChooserDescriptor fileChooserDescriptor,
                                       @Nullable Disposable parent) {
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
  @Override
  public String getText(){
    return getTextField().getText();
  }

  @Override
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

    @Override
    protected void installPathCompletion(final FileChooserDescriptor fileChooserDescriptor) {
    }
  }
}
