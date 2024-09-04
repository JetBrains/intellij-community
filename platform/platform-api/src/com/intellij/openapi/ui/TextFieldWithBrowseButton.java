// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.SwingUndoUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionListener;

public class TextFieldWithBrowseButton extends ComponentWithBrowseButton<JTextField> implements TextAccessor {
  public TextFieldWithBrowseButton() {
    this((ActionListener)null);
  }

  public TextFieldWithBrowseButton(JTextField field) {
    this(field, null);
  }

  public TextFieldWithBrowseButton(JTextField field, @Nullable ActionListener browseActionListener) {
    this(field, browseActionListener, null);
  }

  public TextFieldWithBrowseButton(JTextField field, @Nullable ActionListener browseActionListener, @Nullable Disposable parent) {
    super(field, browseActionListener);
    if (!(field instanceof JBTextField)) {
      SwingUndoUtil.addUndoRedoActions(field);
    }
    installPathCompletion(FileChooserDescriptorFactory.createSingleLocalFileDescriptor(), parent);
  }

  public TextFieldWithBrowseButton(ActionListener browseActionListener) {
    this(browseActionListener, null);
  }

  public TextFieldWithBrowseButton(ActionListener browseActionListener, Disposable parent) {
    this(new ExtendableTextField(10 /*to prevent infinite resize in grid-box layouts*/), browseActionListener, parent);
  }

  public void addBrowseFolderListener(@Nullable Project project, @NotNull FileChooserDescriptor fileChooserDescriptor) {
    addBrowseFolderListener(project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    installPathCompletion(fileChooserDescriptor);
  }

  /**
   * @deprecated use {@link #addBrowseFolderListener(Project, FileChooserDescriptor)}
   * together with {@link FileChooserDescriptor#withTitle} and {@link FileChooserDescriptor#withDescription}
   */
  @Deprecated(forRemoval = true)
  public void addBrowseFolderListener(
    @Nullable @NlsContexts.DialogTitle String title,
    @Nullable @NlsContexts.Label String description,
    @Nullable Project project,
    FileChooserDescriptor fileChooserDescriptor
  ) {
    addBrowseFolderListener(project, fileChooserDescriptor.withTitle(title).withDescription(description));
  }

  public void addBrowseFolderListener(@NotNull TextBrowseFolderListener listener) {
    listener.setOwnerComponent(this);
    addActionListener(listener);
    installPathCompletion(listener.getFileChooserDescriptor());
  }

  public void addDocumentListener(@NotNull DocumentListener listener) {
    getTextField().getDocument().addDocumentListener(listener);
  }

  protected void installPathCompletion(FileChooserDescriptor fileChooserDescriptor) {
    installPathCompletion(fileChooserDescriptor, null);
  }

  protected void installPathCompletion(FileChooserDescriptor fileChooserDescriptor, @Nullable Disposable parent) {
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode() || application.isHeadlessEnvironment()) return;
    FileChooserFactory instance = FileChooserFactory.getInstance();
    if (instance != null) {
      instance.installFileCompletion(getChildComponent(), fileChooserDescriptor, true, parent);
    }
  }

  public @NotNull JTextField getTextField() {
    return getChildComponent();
  }

  @Override
  public @NotNull String getText() {
    var text = Strings.notNullize(getTextField().getText());
    if (!(this instanceof NoPathCompletion)) {
      text = FileUtil.expandUserHome(text);
    }
    return text;
  }

  @Override
  public void setText(@NlsSafe @Nullable String text) {
    getTextField().setText(text);
  }

  public boolean isEditable() {
    return getTextField().isEditable();
  }

  @SuppressWarnings("removal")
  public void setEditable(boolean b) {
    getTextField().setEditable(b);
    getButton().setFocusable(!b);
  }

  public static class NoPathCompletion extends TextFieldWithBrowseButton {
    public NoPathCompletion() { }

    public NoPathCompletion(JTextField field) {
      super(field);
    }

    public NoPathCompletion(JTextField field, ActionListener browseActionListener) {
      super(field, browseActionListener);
    }

    public NoPathCompletion(ActionListener browseActionListener) {
      super(browseActionListener);
    }

    @Override
    protected void installPathCompletion(FileChooserDescriptor fileChooserDescriptor, @Nullable Disposable parent) { }
  }
}
