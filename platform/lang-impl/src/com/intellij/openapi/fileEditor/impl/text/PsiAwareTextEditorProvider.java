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

/*
 * @author max
 */
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Producer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiAwareTextEditorProvider extends TextEditorProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider");
  @NonNls
  private static final String FOLDING_ELEMENT = "folding";

  @Override
  @NotNull
  public FileEditor createEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    return new PsiAwareTextEditorImpl(project, file, this);
  }

  @Override
  @NotNull
  public FileEditorState readState(@NotNull final Element element, @NotNull final Project project, @NotNull final VirtualFile file) {
    final TextEditorState state = (TextEditorState)super.readState(element, project, file);

    // Foldings
    Element child = element.getChild(FOLDING_ELEMENT);
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (child != null) {
      if (document == null) {
        final Element detachedStateCopy = child.clone();
        state.setDelayedFoldState(() -> {
          Document document1 = FileDocumentManager.getInstance().getCachedDocument(file);
          return document1 == null ? null : CodeFoldingManager.getInstance(project).readFoldingState(detachedStateCopy, document1);
        });
      }
      else {
        //PsiDocumentManager.getInstance(project).commitDocument(document);
        state.setFoldingState(CodeFoldingManager.getInstance(project).readFoldingState(child, document));
      }
    }
    return state;
  }

  @Override
  public void writeState(@NotNull final FileEditorState _state, @NotNull final Project project, @NotNull final Element element) {
    super.writeState(_state, project, element);

    TextEditorState state = (TextEditorState)_state;

    // Foldings
    CodeFoldingState foldingState = state.getFoldingState();
    if (foldingState != null) {
      Element e = new Element(FOLDING_ELEMENT);
      try {
        CodeFoldingManager.getInstance(project).writeFoldingState(foldingState, e);
      }
      catch (WriteExternalException e1) {
        //ignore
      }
      element.addContent(e);
    }
  }

  @Override
  protected TextEditorState getStateImpl(final Project project, @NotNull final Editor editor, @NotNull final FileEditorStateLevel level) {
    final TextEditorState state = super.getStateImpl(project, editor, level);
    // Save folding only on FULL level. It's very expensive to commit document on every
    // type (caused by undo).
    if (FileEditorStateLevel.FULL == level) {
      // Folding
      if (project != null && !project.isDisposed() && !editor.isDisposed() && project.isInitialized()) {
        state.setFoldingState(CodeFoldingManager.getInstance(project).saveFoldingState(editor));
      }
      else {
        state.setFoldingState(null);
      }
    }

    return state;
  }

  @Override
  protected void setStateImpl(final Project project, final Editor editor, final TextEditorState state) {
    super.setStateImpl(project, editor, state);
    // Folding
    final CodeFoldingState foldState = state.getFoldingState();
    if (project != null && foldState != null && AsyncEditorLoader.isEditorLoaded(editor)) {
      if (!PsiDocumentManager.getInstance(project).isCommitted(editor.getDocument())) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        LOG.error("File should be parsed when changing editor state, otherwise UI might be frozen for a considerable time");
      }
      editor.getFoldingModel().runBatchFoldingOperation(
        () -> CodeFoldingManager.getInstance(project).restoreFoldingState(editor, foldState)
      );
    }
  }

  @NotNull
  @Override
  protected EditorWrapper createWrapperForEditor(@NotNull final Editor editor) {
    return new PsiAwareEditorWrapper(editor);
  }

  private final class PsiAwareEditorWrapper extends EditorWrapper {
    private final TextEditorBackgroundHighlighter myBackgroundHighlighter;

    private PsiAwareEditorWrapper(@NotNull Editor editor) {
      super(editor);
      final Project project = editor.getProject();
      myBackgroundHighlighter = project == null
                                ? null
                                : new TextEditorBackgroundHighlighter(project, editor);
    }

    @Override
    public BackgroundEditorHighlighter getBackgroundHighlighter() {
      return myBackgroundHighlighter;
    }

    @Override
    public boolean isValid() {
      return !Registry.is("editor.new.rendering") || !getEditor().isDisposed();
    }
  }
}
