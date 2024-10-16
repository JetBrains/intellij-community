// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class ErrorStripeUpdateManager implements Disposable {
  public static ErrorStripeUpdateManager getInstance(Project project) {
    return project.getService(ErrorStripeUpdateManager.class);
  }

  private static final Logger LOG = Logger.getInstance(ErrorStripeUpdateManager.class);
  private final Project myProject;

  public ErrorStripeUpdateManager(@NotNull Project project) {
    myProject = project;
    TrafficLightRendererContributor.EP_NAME.addChangeListener(() -> {
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myProject);
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
        if (fileEditor instanceof TextEditor textEditor) {
          Editor editor = textEditor.getEditor();
          PsiFile file = psiDocumentManager.getCachedPsiFile(editor.getDocument());
          repaintErrorStripePanel(editor, file);
        }
      }
    }, this);
  }

  @Override
  public void dispose() {
  }

  @RequiresEdt
  public void repaintErrorStripePanel(@NotNull Editor editor, @Nullable PsiFile psiFile) {
    if (!myProject.isInitialized()) {
      return;
    }

    ReadAction.run(() -> {
      EditorMarkupModel markup = (EditorMarkupModel)editor.getMarkupModel();
      markup.setErrorPanelPopupHandler(new DaemonEditorPopup(myProject, editor));
      markup.setErrorStripTooltipRendererProvider(new DaemonTooltipRendererProvider(myProject, editor));
      markup.setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().getErrorStripeMarkMinHeight());
      if (psiFile != null) {
        setOrRefreshErrorStripeRenderer(markup, psiFile);
      }
    });
  }

  @RequiresEdt
  void setOrRefreshErrorStripeRenderer(@NotNull EditorMarkupModel editorMarkupModel, @NotNull PsiFile file) {
    if (!editorMarkupModel.isErrorStripeVisible()) {
      return;
    }
    ErrorStripeRenderer renderer = editorMarkupModel.getErrorStripeRenderer();
    if (renderer instanceof TrafficLightRenderer tlr) {
      EditorMarkupModelImpl markupModelImpl = (EditorMarkupModelImpl) editorMarkupModel;
      tlr.refresh(markupModelImpl);
      markupModelImpl.repaintTrafficLightIcon();
      if (tlr.isValid()) {
        return;
      }
    }

    ModalityState modality = ModalityState.defaultModalityState();
    TrafficLightRenderer.setTrafficLightOnEditor(myProject, editorMarkupModel, modality, () -> {
      Editor editor = editorMarkupModel.getEditor();
      if (isEditorEligible(editor, file)) {
        return null;
      }
      return createRenderer(editor, file);
    });
  }

  @RequiresBackgroundThread
  private @NotNull TrafficLightRenderer createRenderer(@NotNull Editor editor, @Nullable PsiFile file) {
    for (TrafficLightRendererContributor contributor : TrafficLightRendererContributor.EP_NAME.getExtensionList()) {
      TrafficLightRenderer renderer = contributor.createRenderer(editor, file);
      if (renderer != null) return renderer;
    }
    return new TrafficLightRenderer(myProject, editor);
  }

  private boolean isEditorEligible(Editor editor, PsiFile psiFile) {
    return ReadAction.compute(() -> {
      boolean isHighlightingAvailable = DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(psiFile);
      boolean isPsiValid = psiFile.isValid();
      boolean isEditorDispose = editor.isDisposed();

      LOG.debug(
        "Editor params for rendering traffic light: isEditorDispose ", isEditorDispose,
        " isPsiValid ", isPsiValid,
        " isHighlightingAvailable ", isHighlightingAvailable);
      return isEditorDispose
             || !isPsiValid
             || !isHighlightingAvailable;
    });
  }

  static final class EssentialHighlightingModeListener implements RegistryValueListener {
    @Override
    public void afterValueChanged(@NotNull RegistryValue value) {
      if (!"ide.highlighting.mode.essential".equals(value.getKey())) {
        return;
      }

      for (Project project : ProjectManagerEx.Companion.getOpenProjects()) {
        HighlightingSettingsPerFile.getInstance(project).incModificationCount();

        FileEditor[] allEditors = FileEditorManager.getInstance(project).getAllEditors();
        if (allEditors.length == 0) {
          return;
        }

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        ErrorStripeUpdateManager stripeUpdateManager = getInstance(project);
        for (FileEditor fileEditor : allEditors) {
          if (fileEditor instanceof TextEditor textEditor) {
            Editor editor = textEditor.getEditor();
            stripeUpdateManager.repaintErrorStripePanel(editor, psiDocumentManager.getCachedPsiFile(editor.getDocument()));
          }
        }

        // Run all checks after disabling essential highlighting
        if (!value.asBoolean()) {
          ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project)).restartToCompleteEssentialHighlighting();
        }
      }
    }
  }
}
