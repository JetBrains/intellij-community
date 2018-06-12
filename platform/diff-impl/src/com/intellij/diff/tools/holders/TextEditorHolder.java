/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.holders;

import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.panels.Wrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusListener;

public class TextEditorHolder extends EditorHolder {
  @NotNull protected final EditorEx myEditor;
  @NotNull protected final Wrapper myPanel;

  public TextEditorHolder(@Nullable Project project, @NotNull EditorEx editor) {
    myEditor = editor;
    myPanel = new Wrapper(myEditor.getComponent());

    DataManager.registerDataProvider(myPanel, (dataId) -> {
      if (project != null && !project.isDisposed() && Registry.is("diff.pass.rich.editor.context")) {
        final Object o = FileEditorManager.getInstance(project).getData(dataId, editor, editor.getCaretModel().getCurrentCaret());
        if (o != null) return o;
      }

      if (CommonDataKeys.EDITOR.is(dataId)) {
        return editor;
      }
      else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        return editor.getVirtualFile();
      }
      return null;
    });
  }

  @NotNull
  public EditorEx getEditor() {
    return myEditor;
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void installFocusListener(@NotNull FocusListener listener) {
    myEditor.getContentComponent().addFocusListener(listener);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditor.getContentComponent();
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
}
