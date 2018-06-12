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
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithoutContent;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusListener;

public class BinaryEditorHolder extends EditorHolder {
  @NotNull protected final FileEditor myEditor;
  @NotNull protected final FileEditorProvider myEditorProvider;

  public BinaryEditorHolder(@NotNull FileEditor editor, @NotNull FileEditorProvider editorProvider) {
    myEditor = editor;
    myEditorProvider = editorProvider;
  }

  @NotNull
  public FileEditor getEditor() {
    return myEditor;
  }

  @Override
  public void dispose() {
    myEditorProvider.disposeEditor(myEditor);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myEditor.getComponent();
  }

  @Override
  public void installFocusListener(@NotNull FocusListener listener) {
    myEditor.getComponent().addFocusListener(listener);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditor.getPreferredFocusedComponent();
  }

  //
  // Build
  //

  public static class BinaryEditorHolderFactory extends EditorHolderFactory<BinaryEditorHolder> {
    public static final BinaryEditorHolderFactory INSTANCE = new BinaryEditorHolderFactory();

    @Override
    @NotNull
    public BinaryEditorHolder create(@NotNull DiffContent content, @NotNull DiffContext context) {
      Project project = context.getProject();
      if (content instanceof FileContent) {
        if (project == null) project = ProjectManager.getInstance().getDefaultProject();
        VirtualFile file = ((FileContent)content).getFile();

        FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, file);
        if (providers.length == 0) throw new IllegalStateException("Can't find FileEditorProvider: " + file.getFileType());

        FileEditorProvider provider = providers[0];
        FileEditor editor = provider.createEditor(project, file);

        UIUtil.removeScrollBorder(editor.getComponent());

        return new BinaryEditorHolder(editor, provider);
      }
      if (content instanceof DocumentContent) {
        Document document = ((DocumentContent)content).getDocument();
        final Editor editor = DiffUtil.createEditor(document, project, true);

        TextEditorProvider provider = TextEditorProvider.getInstance();
        TextEditor fileEditor = provider.getTextEditor(editor);

        Disposer.register(fileEditor, new Disposable() {
          @Override
          public void dispose() {
            EditorFactory.getInstance().releaseEditor(editor);
          }
        });

        return new BinaryEditorHolder(fileEditor, provider);
      }

      throw new IllegalArgumentException(content.getClass() + " - " + content.toString());
    }

    @Override
    public boolean canShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
      if (content instanceof DocumentContent) return true;
      if (content instanceof FileContent) {
        Project project = context.getProject();
        if (project == null) project = ProjectManager.getInstance().getDefaultProject();
        VirtualFile file = ((FileContent)content).getFile();
        if (!file.isValid()) return false;
        if (file instanceof VirtualFileWithoutContent) return false;
        return FileEditorProviderManager.getInstance().getProviders(project, file).length != 0;
      }
      return false;
    }

    @Override
    public boolean wantShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
      if (content instanceof FileContent) {
        if (content.getContentType() == null) return false;
        if (content.getContentType().isBinary()) return true;
        if (content.getContentType() instanceof UIBasedFileType) return true;
        return false;
      }
      return false;
    }
  }
}
