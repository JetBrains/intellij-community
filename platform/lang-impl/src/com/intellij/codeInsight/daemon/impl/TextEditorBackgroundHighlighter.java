/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class TextEditorBackgroundHighlighter implements BackgroundEditorHighlighter {
  private static final int[] EXCEPT_OVERRIDDEN = {
    Pass.UPDATE_FOLDING,
    Pass.POPUP_HINTS,
    Pass.UPDATE_ALL,
    Pass.POST_UPDATE_ALL,
    Pass.LOCAL_INSPECTIONS,
    Pass.EXTERNAL_TOOLS,
  };

  private final Editor myEditor;
  private final Document myDocument;
  private PsiFile myFile;
  private final Project myProject;
  private boolean myCompiled;
  private static final int[] EXCEPT_VISIBLE = {
    Pass.POST_UPDATE_ALL,
    Pass.UPDATE_OVERRIDEN_MARKERS,
    Pass.LOCAL_INSPECTIONS,
    Pass.EXTERNAL_TOOLS,
  };

  public TextEditorBackgroundHighlighter(@NotNull Project project, @NotNull Editor editor) {
    myProject = project;
    myEditor = editor;
    myDocument = myEditor.getDocument();
    renewFile();
  }

  private void renewFile() {
    if (myFile == null || !myFile.isValid()) {
      myFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
      myCompiled = myFile instanceof PsiCompiledElement;
      if (myCompiled) {
        myFile = (PsiFile)((PsiCompiledElement)myFile).getMirror();
      }
      if (myFile != null && !myFile.isValid()) {
        myFile = null;
      }
    }

    if (myFile != null) {
      myFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);
    }
  }

  public List<TextEditorHighlightingPass> getPasses(int[] passesToIgnore) {
    if (myProject.isDisposed()) return Collections.emptyList();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    renewFile();
    if (myFile == null || !myFile.isPhysical()) return Collections.emptyList();
    if (myCompiled) {
      passesToIgnore = EXCEPT_OVERRIDDEN;
    }
    else if (!DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(myFile)) {
      return Collections.emptyList();
    }

    TextEditorHighlightingPassRegistrarEx passRegistrar = TextEditorHighlightingPassRegistrarEx.getInstanceEx(myProject);

    return passRegistrar.instantiatePasses(myFile, myEditor, passesToIgnore);
  }

  @NotNull
  public TextEditorHighlightingPass[] createPassesForVisibleArea() {
    List<TextEditorHighlightingPass> passes = getPasses(EXCEPT_VISIBLE);
    return passes.isEmpty() ? TextEditorHighlightingPass.EMPTY_ARRAY : passes.toArray(new TextEditorHighlightingPass[passes.size()]);
  }

  @NotNull
  public TextEditorHighlightingPass[] createPassesForEditor() {
    List<TextEditorHighlightingPass> passes = getPasses(ArrayUtil.EMPTY_INT_ARRAY);
    return passes.isEmpty() ? TextEditorHighlightingPass.EMPTY_ARRAY : passes.toArray(new TextEditorHighlightingPass[passes.size()]);
  }
}
