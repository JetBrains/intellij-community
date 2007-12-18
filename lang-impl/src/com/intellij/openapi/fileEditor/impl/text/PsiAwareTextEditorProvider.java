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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiAwareTextEditorProvider extends TextEditorProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider");
  @NonNls
  private static final String FOLDING_ELEMENT = "folding";

  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull final VirtualFile file) {
    LOG.assertTrue(accept(project, file));
    return new PsiAwareTextEditorImpl(project, file, this);
  }

  @NotNull
  public FileEditorState readState(@NotNull final Element element, @NotNull final Project project, @NotNull final VirtualFile file) {
    final TextEditorState state = (TextEditorState)super.readState(element, project, file);

    // Foldings
    Element child = element.getChild(FOLDING_ELEMENT);
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (child != null && document != null) {
      //PsiDocumentManager.getInstance(project).commitDocument(document);
      state.FOLDING_STATE = CodeFoldingManager.getInstance(project).readFoldingState(child, document);
    }
    else {
      state.FOLDING_STATE = null;
    }

    return state;
  }

  public void writeState(@NotNull final FileEditorState _state, @NotNull final Project project, @NotNull final Element element) {
    super.writeState(_state, project, element);

    TextEditorState state = (TextEditorState)_state;

    // Foldings
    if (state.FOLDING_STATE != null) {
      Element e = new Element(FOLDING_ELEMENT);
      try {
        CodeFoldingManager.getInstance(project).writeFoldingState(state.FOLDING_STATE, e);
      }
      catch (WriteExternalException e1) {
        //ignore
      }
      element.addContent(e);
    }
  }

  protected TextEditorState getStateImpl(final Project project, final Editor editor, @NotNull final FileEditorStateLevel level) {
    final TextEditorState state = super.getStateImpl(project, editor, level);
    // Save folding only on FULL level. It's very expensive to commit document on every
    // type (caused by undo).
    if(FileEditorStateLevel.FULL == level){
      // Folding
      if (project != null) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        state.FOLDING_STATE = CodeFoldingManager.getInstance(project).saveFoldingState(editor);
      }
      else {
        state.FOLDING_STATE = null;
      }
    }

    return state;
  }

  protected void setStateImpl(final Project project, final Editor editor, final TextEditorState state) {
    super.setStateImpl(project, editor, state);
    // Folding
    if (project != null && state.FOLDING_STATE != null){
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      editor.getFoldingModel().runBatchFoldingOperation(
        new Runnable() {
          public void run() {
            CodeFoldingManager.getInstance(project).restoreFoldingState(editor, state.FOLDING_STATE);
          }
        }
      );
    }
  }

  protected EditorWrapper createWrapperForEditor(final Editor editor) {
    return new PsiAwareEdtiorWrapper(editor);
  }

  private final class PsiAwareEdtiorWrapper extends EditorWrapper {
    private TextEditorBackgroundHighlighter myBackgroundHighlighter;

    private PsiAwareEdtiorWrapper(final Editor editor) {
      super(editor);
      final Project project = editor.getProject();
      myBackgroundHighlighter = project == null
                                ? null
                                : new TextEditorBackgroundHighlighter(project, editor);
    }

    public BackgroundEditorHighlighter getBackgroundHighlighter() {
      return myBackgroundHighlighter;
    }
  }
}