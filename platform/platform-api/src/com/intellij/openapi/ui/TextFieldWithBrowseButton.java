// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.UIUtil;
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
    this(new ExtendableTextField(10), // to prevent field to be infinitely resized in grid-box layouts
         browseActionListener, parent);
  }

  public void addBrowseFolderListener(@Nullable @NlsContexts.DialogTitle String title,
                                      @Nullable @NlsContexts.Label String description,
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

  @NotNull
  public JTextField getTextField() {
    return getChildComponent();
  }

  @NotNull
  @Override
  public String getText() {
    return StringUtil.notNullize(getTextField().getText());
  }

  @Override
  public void setText(@NlsSafe @Nullable String text){
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
