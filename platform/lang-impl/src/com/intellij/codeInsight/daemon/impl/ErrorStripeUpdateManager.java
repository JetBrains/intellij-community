// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.ErrorStripTooltipRendererProvider;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.UIController;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ErrorStripeUpdateManager implements Disposable {
  public static ErrorStripeUpdateManager getInstance(Project project) {
    return project.getService(ErrorStripeUpdateManager.class);
  }

  private final Project myProject;
  private final PsiDocumentManager myPsiDocumentManager;

  public ErrorStripeUpdateManager(Project project) {
    myProject = project;
    myPsiDocumentManager = PsiDocumentManager.getInstance(myProject);
    TrafficLightRendererContributor.EP_NAME.addChangeListener(() -> {
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
        if (fileEditor instanceof TextEditor) {
          TextEditor textEditor = (TextEditor)fileEditor;
          repaintErrorStripePanel(textEditor.getEditor());
        }
      }
    }, this);
  }

  @Override
  public void dispose() {
  }

  @SuppressWarnings("WeakerAccess") // Used in Rider
  public void repaintErrorStripePanel(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myProject.isInitialized()) return;

    PsiFile file = myPsiDocumentManager.getPsiFile(editor.getDocument());
    EditorMarkupModel markup = (EditorMarkupModel) editor.getMarkupModel();
    markup.setErrorPanelPopupHandler(new DaemonEditorPopup(myProject, editor));
    markup.setErrorStripTooltipRendererProvider(createTooltipRenderer(editor));
    markup.setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().getErrorStripeMarkMinHeight());
    setOrRefreshErrorStripeRenderer(markup, file);
  }

  @SuppressWarnings("WeakerAccess") // Used in Rider
  void setOrRefreshErrorStripeRenderer(@NotNull EditorMarkupModel editorMarkupModel, @Nullable PsiFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!editorMarkupModel.isErrorStripeVisible() || file == null || !DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(file)) {
      return;
    }
    ErrorStripeRenderer renderer = editorMarkupModel.getErrorStripeRenderer();
    if (renderer instanceof TrafficLightRenderer) {
      TrafficLightRenderer tlr = (TrafficLightRenderer) renderer;
      EditorMarkupModelImpl markupModelImpl = (EditorMarkupModelImpl) editorMarkupModel;
      tlr.refresh(markupModelImpl);
      markupModelImpl.repaintTrafficLightIcon();
      if (tlr.isValid()) return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Editor editor = editorMarkupModel.getEditor();
      if (editor.isDisposed()) return;

      TrafficLightRenderer tlRenderer = createRenderer(editor, file);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (editor.isDisposed()) {
          Disposer.dispose(tlRenderer); // would be registered in setErrorStripeRenderer() below
          return;
        }
        editorMarkupModel.setErrorStripeRenderer(tlRenderer);
      });
    });
  }

  @NotNull
  private ErrorStripTooltipRendererProvider createTooltipRenderer(Editor editor) {
    return new DaemonTooltipRendererProvider(myProject, editor);
  }

  private @NotNull TrafficLightRenderer createRenderer(@NotNull Editor editor, @Nullable PsiFile file) {
    for (TrafficLightRendererContributor contributor : TrafficLightRendererContributor.EP_NAME.getExtensionList()) {
      TrafficLightRenderer renderer = contributor.createRenderer(editor, file);
      if (renderer != null) return renderer;
    }
    return createFallbackRenderer(editor);
  }

  private @NotNull TrafficLightRenderer createFallbackRenderer(@NotNull Editor editor) {
    return new TrafficLightRenderer(myProject, editor.getDocument()) {
      @Override
      protected @NotNull UIController createUIController() {
        return super.createUIController(editor);
      }
    };
  }
}
