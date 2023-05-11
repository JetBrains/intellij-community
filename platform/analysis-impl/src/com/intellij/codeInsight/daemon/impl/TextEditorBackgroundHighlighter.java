// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.platform.diagnostic.telemetry.impl.TraceUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

public class TextEditorBackgroundHighlighter implements BackgroundEditorHighlighter {
  private static final Logger LOG = Logger.getInstance(TextEditorBackgroundHighlighter.class);
  private static final int[] IGNORE_FOR_COMPILED = {
    Pass.UPDATE_FOLDING,
    Pass.POPUP_HINTS,
    Pass.LOCAL_INSPECTIONS,
    Pass.WHOLE_FILE_LOCAL_INSPECTIONS,
    Pass.EXTERNAL_TOOLS,
  };

  private final Project myProject;
  private final Editor myEditor;
  private final Document myDocument;

  public TextEditorBackgroundHighlighter(@NotNull Project project, @NotNull Editor editor) {
    myProject = project;
    myEditor = editor;
    myDocument = myEditor.getDocument();
  }

  @Nullable
  private PsiFile renewFile() {
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (file != null) {
      file.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);
    }
    return file;
  }

  @NotNull
  List<TextEditorHighlightingPass> getPasses(int @NotNull [] passesToIgnore) {
    if (myProject.isDisposed()) return Collections.emptyList();

    PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
    if (!documentManager.isCommitted(myDocument)) {
      LOG.error(myDocument + "; " + documentManager.someDocumentDebugInfo(myDocument));
    }

    PsiFile file = renewFile();
    if (file == null) return Collections.emptyList();

    boolean compiled = file instanceof PsiCompiledFile;
    if (compiled) {
      file = ((PsiCompiledFile)file).getDecompiledPsiFile();
      passesToIgnore = IGNORE_FOR_COMPILED;
    }
    else if (!DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(file)) {
      return Collections.emptyList();
    }

    int @NotNull [] finalPassesToIgnore = passesToIgnore;
    PsiFile finalFile = file;
    return TraceUtil.computeWithSpanThrows(HighlightingPassTracer.HIGHLIGHTING_PASS_TRACER, "passes instantiation", span -> {
      Activity startupActivity = StartUpMeasurer.startActivity("highlighting passes instantiation");
      boolean cancelled = false;
      try {
        TextEditorHighlightingPassRegistrarEx passRegistrar = TextEditorHighlightingPassRegistrarEx.getInstanceEx(myProject);
        return passRegistrar.instantiatePasses(finalFile, myEditor, finalPassesToIgnore);
      }
      catch (ProcessCanceledException | CancellationException e) {
        cancelled = true;
        throw e;
      }
      finally {
        startupActivity.end();
        span.setAttribute(HighlightingPassTracer.FILE_ATTR_SPAN_KEY, finalFile.getName());
        span.setAttribute(HighlightingPassTracer.FILE_ATTR_SPAN_KEY, Boolean.toString(cancelled));
      }
    });
  }

  @Override
  public TextEditorHighlightingPass @NotNull [] createPassesForEditor() {
    List<TextEditorHighlightingPass> passes = getPasses(ArrayUtilRt.EMPTY_INT_ARRAY);
    return passes.isEmpty() ? TextEditorHighlightingPass.EMPTY_ARRAY : passes.toArray(TextEditorHighlightingPass.EMPTY_ARRAY);
  }
}