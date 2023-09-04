// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
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
          Editor editor = ((TextEditor)fileEditor).getEditor();
          PsiFile file = myPsiDocumentManager.getCachedPsiFile(editor.getDocument());
          repaintErrorStripePanel(editor, file);
        }
      }
    }, this);
    RegistryManager.getInstance().get("ide.highlighting.mode.essential").addListener(new EssentialHighlightingModeListener(), this);
  }

  @Override
  public void dispose() {
  }

  public void repaintErrorStripePanel(@NotNull Editor editor, @Nullable PsiFile psiFile) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myProject.isInitialized()) return;

    EditorMarkupModel markup = (EditorMarkupModel) editor.getMarkupModel();
    markup.setErrorPanelPopupHandler(new DaemonEditorPopup(myProject, editor));
    markup.setErrorStripTooltipRendererProvider(new DaemonTooltipRendererProvider(myProject, editor));
    markup.setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().getErrorStripeMarkMinHeight());
    if (psiFile != null) {
      setOrRefreshErrorStripeRenderer(markup, psiFile);
    }
  }

  void setOrRefreshErrorStripeRenderer(@NotNull EditorMarkupModel editorMarkupModel, @NotNull PsiFile file) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
      if (ReadAction.compute(() -> editor.isDisposed() || !file.isValid() ||
                                   !DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(file))) {
        return null;
      }
      return createRenderer(editor, file);
    });
  }

  private @NotNull TrafficLightRenderer createRenderer(@NotNull Editor editor, @Nullable PsiFile file) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    for (TrafficLightRendererContributor contributor : TrafficLightRendererContributor.EP_NAME.getExtensionList()) {
      TrafficLightRenderer renderer = contributor.createRenderer(editor, file);
      if (renderer != null) return renderer;
    }
    return new TrafficLightRenderer(myProject, editor);
  }
  
  private final class EssentialHighlightingModeListener implements RegistryValueListener {
    @Override
    public void afterValueChanged(@NotNull RegistryValue value) {
      HighlightingSettingsPerFile.getInstance(myProject).incModificationCount();
      for (FileEditor fileEditor : FileEditorManager.getInstance(myProject).getAllEditors()) {
        if (fileEditor instanceof TextEditor) {
          Editor editor = ((TextEditor)fileEditor).getEditor();
          PsiFile file = myPsiDocumentManager.getCachedPsiFile(editor.getDocument());
          repaintErrorStripePanel(editor, file);
        }
      }
      
      // Run all checks after disabling essential highlighting
      if (!value.asBoolean()) {
        DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
        codeAnalyzer.restartToCompleteEssentialHighlighting();
      }
    }
  }
}
