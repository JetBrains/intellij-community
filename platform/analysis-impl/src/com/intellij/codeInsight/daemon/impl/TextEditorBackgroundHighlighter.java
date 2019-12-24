// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TextEditorBackgroundHighlighter implements BackgroundEditorHighlighter {
  private static final Logger LOG = Logger.getInstance(TextEditorBackgroundHighlighter.class);
  private static final int[] EXCEPT_OVERRIDDEN = {
    Pass.UPDATE_FOLDING,
    Pass.POPUP_HINTS,
    Pass.UPDATE_ALL,
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
  List<TextEditorHighlightingPass> getPasses(@NotNull int[] passesToIgnore) {
    if (myProject.isDisposed()) return Collections.emptyList();

    LOG.assertTrue(PsiDocumentManager.getInstance(myProject).isCommitted(myDocument));

    PsiFile file = renewFile();
    if (file == null) return Collections.emptyList();

    boolean compiled = file instanceof PsiCompiledFile;
    if (compiled) {
      file = ((PsiCompiledFile)file).getDecompiledPsiFile();
    }

    if (compiled) {
      passesToIgnore = EXCEPT_OVERRIDDEN;
    }
    else if (!DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(file)) {
      return Collections.emptyList();
    }

    TextEditorHighlightingPassRegistrarEx passRegistrar = TextEditorHighlightingPassRegistrarEx.getInstanceEx(myProject);
    return passRegistrar.instantiatePasses(file, myEditor, passesToIgnore);
  }

  @Override
  @NotNull
  public TextEditorHighlightingPass[] createPassesForVisibleArea() {
    return createPassesForEditor();
  }

  @Override
  @NotNull
  public TextEditorHighlightingPass[] createPassesForEditor() {
    List<TextEditorHighlightingPass> passes = getPasses(ArrayUtilRt.EMPTY_INT_ARRAY);
    return passes.isEmpty() ? TextEditorHighlightingPass.EMPTY_ARRAY : passes.toArray(TextEditorHighlightingPass.EMPTY_ARRAY);
  }
}