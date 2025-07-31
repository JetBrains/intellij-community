// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CancellationException;

@ApiStatus.Internal
public final class TextEditorBackgroundHighlighter implements BackgroundEditorHighlighter {
  private static final int[] IGNORE_FOR_COMPILED = {
    Pass.UPDATE_FOLDING,
    Pass.POPUP_HINTS,
    Pass.LOCAL_INSPECTIONS,
    Pass.EXTERNAL_TOOLS};
  private static final Logger LOG = Logger.getInstance(TextEditorBackgroundHighlighter.class);

  private final Project project;
  private final Editor editor;
  private final Document document;

  /**
   * please use {@link FileEditor#getBackgroundHighlighter()} instead of manual instantiation
   */
  @ApiStatus.Internal
  public TextEditorBackgroundHighlighter(@NotNull Project project, @NotNull Editor editor) {
    this.project = project;
    this.editor = editor;
    document = editor.getDocument();
  }

  private @NotNull List<TextEditorHighlightingPass> createPasses() {
    ThreadingAssertions.assertBackgroundThread();
    if (project.isDisposed()) {
      return List.of();
    }

    PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    if (!documentManager.isCommitted(document)) {
      LOG.error(document + documentManager.someDocumentDebugInfo(document));
    }

    CodeInsightContext context = editor != null
                                 ? EditorContextManager.getEditorContext(editor, project)
                                 : CodeInsightContexts.anyContext();

    PsiFile psiFile = renewFile(project, document, context);
    if (psiFile == null) return List.of();

    int[] effectivePassesToIgnore =
    psiFile.getOriginalFile() instanceof PsiCompiledFile ? IGNORE_FOR_COMPILED:
    DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(psiFile) ?
    ArrayUtil.EMPTY_INT_ARRAY : null;
    if (effectivePassesToIgnore == null) {
      return List.of();
    }

    try {
      HighlightingSessionImpl.getFromCurrentIndicator(psiFile);
    }
    catch (IllegalStateException e) {
      // could not find the session for this psi file;
      // maybe the document was modified and the not-quite-incremental reparse has replaced the whole file
      throw new ProcessCanceledException(e);
    }

    return TraceKt.use(HighlightingPassTracer.HIGHLIGHTING_PASS_TRACER.spanBuilder("passes instantiation"), span -> {
      Activity startupActivity = StartUpMeasurer.startActivity("highlighting passes instantiation");
      boolean cancelled = false;
      try {
        TextEditorHighlightingPassRegistrarEx passRegistrar = TextEditorHighlightingPassRegistrarEx.getInstanceEx(project);
        return passRegistrar.instantiatePasses(psiFile, editor, effectivePassesToIgnore);
      }
      catch (CancellationException e) {
        cancelled = true;
        throw e;
      }
      finally {
        startupActivity.end();
        span.setAttribute(HighlightingPassTracer.FILE_ATTR_SPAN_KEY, psiFile.getName());
        span.setAttribute(HighlightingPassTracer.FILE_ATTR_SPAN_KEY, cancelled+"");
      }
    });
  }

  @Override
  public @NotNull TextEditorHighlightingPass @NotNull [] createPassesForEditor() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    GlobalInspectionContextBase.assertUnderDaemonProgress();
    List<TextEditorHighlightingPass> passes = createPasses();
    return passes.isEmpty() ? TextEditorHighlightingPass.EMPTY_ARRAY : passes.toArray(TextEditorHighlightingPass.EMPTY_ARRAY);
  }

  @ApiStatus.Internal
  public static @Nullable PsiFile renewFile(@NotNull Project project, @NotNull Document document, @NotNull CodeInsightContext context)  {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document, context);
    if (psiFile instanceof PsiCompiledFile compiled) {
      psiFile = compiled.getDecompiledPsiFile();
    }
    if (psiFile == null) return null;
    psiFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, true);
    return psiFile;
  }

  /**
   * Returns PSI file associated with {@param document}, if it's exists and cached, or null otherwise.
   * Guarantees no expensive PSI creation/decompilation ops are performed here
   */
  @ApiStatus.Internal
  public static @Nullable PsiFile getCachedFileToHighlight(@NotNull Project project,
                                                           @NotNull VirtualFile virtualFile,
                                                           @NotNull CodeInsightContext context) {
    PsiDocumentManagerBase psiDocumentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    PsiFile psiFile = psiDocumentManager.getRawCachedFile(virtualFile, context);
    if (psiFile instanceof PsiCompiledFile compiled) {
      psiFile = (PsiFile)compiled.getCachedMirror();
    }
    if (psiFile == null) return null;
    psiFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, true);
    return psiFile;
  }
}