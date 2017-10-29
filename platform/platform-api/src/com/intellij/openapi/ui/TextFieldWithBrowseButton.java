// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
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
    this(field, browseActionListener, null);
  }

  public TextFieldWithBrowseButton(JTextField field, @Nullable ActionListener browseActionListener, @Nullable Disposable parent) {
    super(field, browseActionListener);
    if (!(field instanceof JBTextField)) {
      UIUtil.addUndoRedoActions(field);
    }
    installPathCompletion(FileChooserDescriptorFactory.createSingleLocalFileDescriptor(), parent);
  }

  public TextFieldWithBrowseButton(ActionListener browseActionListener) {
    this(browseActionListener, null);
  }

  public TextFieldWithBrowseButton(ActionListener browseActionListener, Disposable parent) {
    // to prevent field to be infinitely resized in grid-box layouts
    this(Experiments.isFeatureEnabled("inline.browse.button") ? new ExtendableTextField(10) : new JBTextField(10),
         browseActionListener, parent);
  }

  public void addBrowseFolderListener(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
                                      @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                                      @Nullable Project project, FileChooserDescriptor fileChooserDescriptor) {
    addBrowseFolderListener(title, description, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    installPathCompletion(fileChooserDescriptor);
  }

  public void addBrowseFolderListener(@NotNull TextBrowseFolderListener listener) {
    listener.setOwnerComponent(this);
    addActionListener(listener);
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

  @NotNull
  @Override
  public String getText() {
    return StringUtil.notNullize(getTextField().getText());
  }

  @Override
  public void setText(@Nullable String text){
    getTextField().setText(text);
  }

  public boolean isEditable() {
    return getTextField().isEditable();
  }

  public void setEditable(boolean b) {
    getTextField().setEditable(b);
    getButton().setFocusable(!b);
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
    protected void installPathCompletion(FileChooserDescriptor fileChooserDescriptor, @Nullable Disposable parent) {
    }
  }
}
