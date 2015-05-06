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
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TextEditorHolder extends EditorHolder {
  @NotNull protected final EditorEx myEditor;

  public TextEditorHolder(@NotNull EditorEx editor) {
    myEditor = editor;
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
    return myEditor.getComponent();
  }

  @Nullable
  @Override
  public JComponent getFocusedComponent() {
    return myEditor.getContentComponent();
  }

  //
  // Build
  //

  @NotNull
  public static TextEditorHolder create(@Nullable Project project, @NotNull DocumentContent content) {
    return create(project, content, false);
  }

  @NotNull
  public static TextEditorHolder create(@Nullable Project project, @NotNull DocumentContent content, boolean forceReadOnly) {
    EditorEx editor = DiffUtil.createEditor(content.getDocument(), project, forceReadOnly, true);
    DiffUtil.configureEditor(editor, content, project);
    return new TextEditorHolder(editor);
  }

  public static boolean canShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
    if (content instanceof DocumentContent) return true;
    return false;
  }

  public static boolean wantShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
    if (content instanceof DocumentContent) return true;
    return false;
  }
}
