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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Producer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiAwareTextEditorProvider extends TextEditorProvider implements AsyncFileEditorProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider");
  @NonNls
  private static final String FOLDING_ELEMENT = "folding";

  @Override
  @NotNull
  public FileEditor createEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    return createEditorAsync(project, file).build();
  }

  @NotNull
  @Override
  public Builder createEditorAsync(@NotNull final Project project, @NotNull final VirtualFile file) {
    if (!accept(project, file)) {
      LOG.error("Cannot open text editor for " + file);
    }
    CodeFoldingState state = null;
    if (!project.isDefault()) { // There's no CodeFoldingManager for default project (which is used in diff command-line application)
      try {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
          state = CodeFoldingManager.getInstance(project).buildInitialFoldings(document);
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error("Error building initial foldings", e);
      }
    }
    final CodeFoldingState finalState = state;
    return new Builder() {
      @Override
      public FileEditor build() {
        final PsiAwareTextEditorImpl editor = new PsiAwareTextEditorImpl(project, file, PsiAwareTextEditorProvider.this);
        if (finalState != null) {
          finalState.setToEditor(editor.getEditor());
        }
        return editor;
      }
    };
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
        state.setDelayedFoldState(new Producer<CodeFoldingState>() {
          @Override
          public CodeFoldingState produce() {
            Document document = FileDocumentManager.getInstance().getCachedDocument(file);
            return document == null ? null : CodeFoldingManager.getInstance(project).readFoldingState(detachedStateCopy, document);
          }
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
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
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
    if (project != null && foldState != null) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
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
