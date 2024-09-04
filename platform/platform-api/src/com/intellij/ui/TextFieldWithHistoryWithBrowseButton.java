// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextFieldWithHistoryWithBrowseButton extends ComponentWithBrowseButton<TextFieldWithHistory> {
  private final Disposable myDisposable = Disposer.newDisposable();

  public TextFieldWithHistoryWithBrowseButton() {
    super(new TextFieldWithHistory(), null);
  }

  @Override
  public void addBrowseFolderListener(
    @Nullable Project project,
    FileChooserDescriptor fileChooserDescriptor,
    TextComponentAccessor<? super TextFieldWithHistory> accessor
  ) {
    super.addBrowseFolderListener(project, fileChooserDescriptor, accessor);
    FileChooserFactory.getInstance().installFileCompletion(getChildComponent().getTextEditor(), fileChooserDescriptor, false, myDisposable);
  }

  /**
   * @deprecated use {@link #addBrowseFolderListener(Project, FileChooserDescriptor, TextComponentAccessor)}
   * together with {@link FileChooserDescriptor#withTitle} and {@link FileChooserDescriptor#withDescription}
   */
  @Deprecated(forRemoval = true)
  @Override
  @SuppressWarnings("removal")
  public void addBrowseFolderListener(
    @Nullable String title,
    @Nullable String description,
    @Nullable Project project,
    FileChooserDescriptor fileChooserDescriptor,
    TextComponentAccessor<? super TextFieldWithHistory> accessor
  ) {
    super.addBrowseFolderListener(title, description, project, fileChooserDescriptor, accessor);
  }

  /**
   * @deprecated use {@link #addBrowseFolderListener(Project, FileChooserDescriptor, TextComponentAccessor)}
   * together with {@link FileChooserDescriptor#withTitle} and {@link FileChooserDescriptor#withDescription}
   */
  @Deprecated(forRemoval = true)
  @Override
  @SuppressWarnings("removal")
  public void addBrowseFolderListener(
    @Nullable String title,
    @Nullable String description,
    @Nullable Project project,
    FileChooserDescriptor fileChooserDescriptor,
    TextComponentAccessor<? super TextFieldWithHistory> accessor,
    boolean autoRemoveOnHide
  ) {
    super.addBrowseFolderListener(title, description, project, fileChooserDescriptor, accessor, autoRemoveOnHide);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    Disposer.dispose(myDisposable);
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
