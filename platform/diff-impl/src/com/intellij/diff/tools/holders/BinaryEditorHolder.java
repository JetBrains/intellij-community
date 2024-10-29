// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.holders;

import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.UnknownFileTypeDiffRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.FileEditorBase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UIBasedFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusListener;
import java.util.List;

@ApiStatus.Internal
public final class BinaryEditorHolder extends EditorHolder {
  @NotNull private final FileEditor myEditor;
  @Nullable private final FileEditorProvider myEditorProvider;

  public BinaryEditorHolder(@NotNull FileEditor editor, @Nullable FileEditorProvider editorProvider) {
    myEditor = editor;
    myEditorProvider = editorProvider;
  }

  @NotNull
  public FileEditor getEditor() {
    return myEditor;
  }

  @Override
  public void dispose() {
    if (myEditorProvider != null) {
      myEditorProvider.disposeEditor(myEditor);
    }
    else {
      Disposer.dispose(myEditor);
    }
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

        List<FileEditorProvider> providers = FileEditorProviderManager.getInstance().getProviderList(project, file);
        if (providers.size() == 0) {
          JComponent component = FileTypeRegistry.getInstance().isFileOfType(file, UnknownFileType.INSTANCE)
                                 ? UnknownFileTypeDiffRequest.createComponent(file.getName(), context)
                                 : DiffUtil.createMessagePanel(DiffBundle.message("error.cant.show.file"));
          return new BinaryEditorHolder(new DumbFileEditor(file, component), null);
        }

        FileEditorProvider provider = providers.get(0);
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
        VirtualFile file = ((FileContent)content).getFile();
        if (!file.isValid()) return false;
        if (DiffUtil.isFileWithoutContent(file)) return false;
        return true;
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

  private static final class DumbFileEditor extends FileEditorBase {
    @NotNull private final VirtualFile myFile;
    @NotNull private final JComponent myComponent;

    private DumbFileEditor(@NotNull VirtualFile file, @NotNull JComponent component) {
      myFile = file;
      myComponent = component;
    }

    @NotNull
    @Override
    public JComponent getComponent() {
      return myComponent;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @NotNull
    @Override
    public VirtualFile getFile() {
      return myFile;
    }

    @NotNull
    @Override
    public String getName() {
      return "Dumb"; //NON-NLS
    }
  }
}
