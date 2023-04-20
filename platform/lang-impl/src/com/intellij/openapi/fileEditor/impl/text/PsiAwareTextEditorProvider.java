// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.SlowOperations;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class PsiAwareTextEditorProvider extends TextEditorProvider {
  private static final @NonNls String FOLDING_ELEMENT = "folding";

  @Override
  public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307300, EA-680898")) {
      return new PsiAwareTextEditorImpl(project, file, this);
    }
  }

  @Override
  public @NotNull FileEditorState readState(@NotNull Element element, @NotNull Project project, @NotNull VirtualFile file) {
    TextEditorState state = (TextEditorState)super.readState(element, project, file);

    // Foldings
    Element child = element.getChild(FOLDING_ELEMENT);
    if (child != null) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document == null) {
        state.setDelayedFoldState(new MyDelayedFoldingState(project, file, child));
      }
      else {
        //PsiDocumentManager.getInstance(project).commitDocument(document);
        state.setFoldingState(CodeFoldingManager.getInstance(project).readFoldingState(child, document));
      }
    }
    return state;
  }

  @Override
  public void writeState(@NotNull FileEditorState _state, @NotNull Project project, @NotNull Element element) {
    super.writeState(_state, project, element);

    TextEditorState state = (TextEditorState)_state;

    // Foldings
    CodeFoldingState foldingState = state.getFoldingState();
    if (foldingState != null) {
      Element e = new Element(FOLDING_ELEMENT);
      try {
        CodeFoldingManager.getInstance(project).writeFoldingState(foldingState, e);
      }
      catch (WriteExternalException ignored) {
      }
      if (!JDOMUtil.isEmpty(e)) {
        element.addContent(e);
      }
    }
    else {
      Supplier<? extends CodeFoldingState> delayedProducer = state.getDelayedFoldState();
      if (delayedProducer instanceof MyDelayedFoldingState) {
        element.addContent(((MyDelayedFoldingState)delayedProducer).getSerializedState());
      }
    }
  }

  @Override
  protected @NotNull TextEditorState getStateImpl(Project project, @NotNull Editor editor, @NotNull FileEditorStateLevel level) {
    TextEditorState state = super.getStateImpl(project, editor, level);
    // Save folding only on FULL level. It's very expensive to commit document on every type (caused by undo).
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
  protected void setStateImpl(@Nullable Project project, @NotNull Editor editor, @NotNull TextEditorState state, boolean exactState) {
    super.setStateImpl(project, editor, state, exactState);

    // folding
    CodeFoldingState foldState = state.getFoldingState();
    // folding state is restored by PsiAwareTextEditorImpl.loadEditorInBackground, that's why here we check isEditorLoaded
    if (project != null && foldState != null && AsyncEditorLoader.isEditorLoaded(editor)) {
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
      if (!psiDocumentManager.isCommitted(editor.getDocument())) {
        psiDocumentManager.commitDocument(editor.getDocument());
        LOG.error("File should be parsed when changing editor state, otherwise UI might be frozen for a considerable time");
      }

      editor.getFoldingModel().runBatchFoldingOperation(() -> {
        CodeFoldingManager.getInstance(project).restoreFoldingState(editor, foldState);
      });
    }
  }

  @Override
  protected @NotNull EditorWrapper createWrapperForEditor(@NotNull Editor editor) {
    return new PsiAwareEditorWrapper(editor);
  }

  private final class PsiAwareEditorWrapper extends EditorWrapper {
    private final TextEditorBackgroundHighlighter myBackgroundHighlighter;

    private PsiAwareEditorWrapper(@NotNull Editor editor) {
      super(editor);
      Project project = editor.getProject();
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
      return !getEditor().isDisposed();
    }
  }

  private static final class MyDelayedFoldingState implements Supplier<CodeFoldingState> {
    private final Project myProject;
    private final VirtualFile myFile;
    private final Element mySerializedState;

    private MyDelayedFoldingState(Project project, VirtualFile file, Element state) {
      myProject = project;
      myFile = file;
      mySerializedState = JDOMUtil.internElement(state);
    }

    @Override
    public CodeFoldingState get() {
      Document document = FileDocumentManager.getInstance().getCachedDocument(myFile);
      return document == null ? null : CodeFoldingManager.getInstance(myProject).readFoldingState(mySerializedState, document);
    }

    private Element getSerializedState() {
      return mySerializedState.clone();
    }
  }
}
