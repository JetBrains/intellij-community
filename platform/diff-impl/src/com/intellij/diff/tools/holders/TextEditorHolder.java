// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.holders;

import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.panels.Wrapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusListener;

@ApiStatus.Internal
public class TextEditorHolder extends EditorHolder {
  private final MyPanel myPanel;

  public TextEditorHolder(@Nullable Project project, @NotNull EditorEx editor) {
    myPanel = new MyPanel(project, editor);
  }

  @NotNull
  public EditorEx getEditor() {
    return myPanel.editor;
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myPanel.editor);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void installFocusListener(@NotNull FocusListener listener) {
    myPanel.editor.getContentComponent().addFocusListener(listener);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.editor.getContentComponent();
  }

  //
  // Build
  //

  @NotNull
  public static TextEditorHolder create(@Nullable Project project, @NotNull DocumentContent content) {
    EditorEx editor = DiffUtil.createEditor(content.getDocument(), project, false, true);
    DiffUtil.configureEditor(editor, content, project);
    return new TextEditorHolder(project, editor);
  }

  public static class TextEditorHolderFactory extends EditorHolderFactory<TextEditorHolder> {
    public static final TextEditorHolderFactory INSTANCE = new TextEditorHolderFactory();

    @Override
    @NotNull
    public TextEditorHolder create(@NotNull DiffContent content, @NotNull DiffContext context) {
      return TextEditorHolder.create(context.getProject(), (DocumentContent)content);
    }

    @Override
    public boolean canShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
      if (content instanceof DocumentContent) return true;
      return false;
    }

    @Override
    public boolean wantShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
      if (content instanceof DocumentContent) return true;
      return false;
    }
  }

  private static class MyPanel extends Wrapper implements UiDataProvider {
    final EditorEx editor;
    final Project project;

    private MyPanel(@Nullable Project project, @NotNull EditorEx editor) {
      super(editor.getComponent());
      this.project = project;
      this.editor = editor;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(CommonDataKeys.PROJECT, project);
      sink.set(OpenFileDescriptor.NAVIGATE_IN_EDITOR, editor);
      sink.set(CommonDataKeys.EDITOR, editor);
      sink.set(CommonDataKeys.VIRTUAL_FILE, editor.getVirtualFile());
      sink.set(PlatformCoreDataKeys.FILE_EDITOR,
               TextEditorProvider.getInstance().getTextEditor(editor));
    }
  }
}
