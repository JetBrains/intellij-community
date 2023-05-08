// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeInsight.codeVision.CodeVisionInitializer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter;
import com.intellij.codeInsight.documentation.render.DocRenderManager;
import com.intellij.codeInsight.documentation.render.DocRenderPassFactory;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.hints.HintsBuffer;
import com.intellij.codeInsight.hints.InlayHintsPassFactory;
import com.intellij.codeInsight.hints.codeVision.CodeVisionPassFactory;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.actionSystem.CompositeDataProvider;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class PsiAwareTextEditorImpl extends TextEditorImpl {
  private TextEditorBackgroundHighlighter myBackgroundHighlighter;
  private static final Logger LOG = Logger.getInstance(PsiAwareTextEditorImpl.class);

  public PsiAwareTextEditorImpl(@NotNull Project project, @NotNull VirtualFile file, @NotNull TextEditorProvider provider) {
    super(project, file, provider);
  }

  public PsiAwareTextEditorImpl(@NotNull Project project,
                                @NotNull VirtualFile file,
                                @NotNull TextEditorProvider provider,
                                @NotNull EditorImpl editor) {
    super(project, file, provider, editor);
  }

  @Override
  public @NotNull Runnable loadEditorInBackground() {
    Runnable baseResult = super.loadEditorInBackground();

    PsiFile psiFile = PsiManager.getInstance(project).findFile(myFile);
    EditorEx editor = getEditor();
    Document document = editor.getDocument();
    // loadEditorInBackground is executed in read action with `withDocumentsCommitted` constraint,
    // no need to check that document is committed
    CodeFoldingState foldingState = project.isDefault()
                                    ? null
                                    : catchingExceptions(() -> CodeFoldingManager.getInstance(project).buildInitialFoldings(document));

    List<? extends Segment> focusZones = catchingExceptions(() -> FocusModePassFactory.calcFocusZones(psiFile));

    DocRenderPassFactory.Items items = psiFile != null && DocRenderManager.isDocRenderingEnabled(getEditor())
                                       ? catchingExceptions(() -> DocRenderPassFactory.calculateItemsToRender(editor, psiFile))
                                       : null;

    HintsBuffer buffer = psiFile == null
                         ? null
                         : catchingExceptions(() -> InlayHintsPassFactory.Companion.collectPlaceholders(psiFile, editor));
    var placeholders = catchingExceptions(() -> {
      return CodeVisionInitializer.Companion.getInstance(project).getCodeVisionHost().collectPlaceholders(editor, psiFile);
    });

    return () -> {
      baseResult.run();

      if (foldingState != null) {
        foldingState.setToEditor(editor);
      }

      if (focusZones != null) {
        FocusModePassFactory.setToEditor(focusZones, editor);
        if (editor instanceof EditorImpl) {
          ((EditorImpl)editor).applyFocusMode();
        }
      }

      if (items != null) {
        DocRenderPassFactory.applyItemsToRender(editor, project, items, true);
      }

      if (buffer != null) {
        InlayHintsPassFactory.Companion.applyPlaceholders(psiFile, editor, buffer);
      }

      if (placeholders != null && !placeholders.isEmpty()) {
        CodeVisionPassFactory.applyPlaceholders(editor, placeholders);
      }

      if (psiFile != null && psiFile.isValid()) {
        DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
      }
    };
  }

  private static @Nullable <T> T catchingExceptions(Supplier<T> computable) {
    try {
      return computable.get();
    }
    catch (Throwable e) {
      if (e instanceof ControlFlowException) {
        throw e;
      }
      LOG.warn("Exception during editor loading", e);
    }
    return null;
  }

  @Override
  protected @NotNull TextEditorComponent createEditorComponent(@NotNull Project project, @NotNull VirtualFile file, @NotNull EditorImpl editor) {
    return new PsiAwareTextEditorComponent(project, file, this, editor);
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    if (!AsyncEditorLoader.isEditorLoaded(getEditor())) {
      return null;
    }

    if (myBackgroundHighlighter == null) {
      myBackgroundHighlighter = new TextEditorBackgroundHighlighter(project, getEditor());
    }
    return myBackgroundHighlighter;
  }

  private static final class PsiAwareTextEditorComponent extends TextEditorComponent {
    private final Project myProject;

    private PsiAwareTextEditorComponent(@NotNull Project project,
                                        @NotNull VirtualFile file,
                                        @NotNull TextEditorImpl textEditor,
                                        @NotNull EditorImpl editor) {
      super(project, file, textEditor, editor);
      myProject = project;
    }

    @Override
    public void dispose() {
      super.dispose();

      CodeFoldingManager foldingManager = myProject.getServiceIfCreated(CodeFoldingManager.class);
      if (foldingManager != null) {
        foldingManager.releaseFoldings(getEditor());
      }
    }

    @Override
    public @Nullable DataProvider createBackgroundDataProvider() {
      DataProvider superProvider = super.createBackgroundDataProvider();
      if (superProvider == null) return null;

      return CompositeDataProvider.compose(dataId -> {
        if (PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.is(dataId)) {
          LookupImpl lookup = (LookupImpl)LookupManager.getInstance(myProject).getActiveLookup();
          if (lookup != null && lookup.isVisible()) {
            return lookup.getBounds();
          }
        }
        return null;
      }, superProvider);
    }
  }
}