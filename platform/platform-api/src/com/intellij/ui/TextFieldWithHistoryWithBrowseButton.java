// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.NlsContexts.DialogTitle;
import static com.intellij.openapi.util.NlsContexts.Label;

public class TextFieldWithHistoryWithBrowseButton extends ComponentWithBrowseButton<TextFieldWithHistory> {
  public TextFieldWithHistoryWithBrowseButton() {
    super(new TextFieldWithHistory(), null);
  }

  @Override
  public void addBrowseFolderListener(@Nullable @DialogTitle String title,
                                      @Nullable @Label String description,
                                      @Nullable Project project,
                                      FileChooserDescriptor fileChooserDescriptor,
                                      TextComponentAccessor<? super TextFieldWithHistory> accessor) {
    super.addBrowseFolderListener(title, description, project, fileChooserDescriptor, accessor);
    FileChooserFactory.getInstance().installFileCompletion(getChildComponent().getTextEditor(), fileChooserDescriptor, false, project);
  }

  @Override
  public void addBrowseFolderListener(@Nullable @DialogTitle String title,
                                      @Nullable @Label String description,
                                      @Nullable Project project,
                                      FileChooserDescriptor fileChooserDescriptor,
                                      TextComponentAccessor<? super TextFieldWithHistory> accessor,
                                      boolean autoRemoveOnHide) {
    addBrowseFolderListener(title, description, project, fileChooserDescriptor, accessor);
    FileChooserFactory.getInstance().installFileCompletion(getChildComponent().getTextEditor(), fileChooserDescriptor, false, project);
  }

  public String getText() {
    return getChildComponent().getText();
  }

  public void setText(@NotNull String text) {
    getChildComponent().setText(text);
  }

  public void setTextAndAddToHistory(@NotNull String text) {
    getChildComponent().setTextAndAddToHistory(text);
  }
}
