/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
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
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TextEditorBackgroundHighlighter implements BackgroundEditorHighlighter {
  private static final int[] EXCEPT_OVERRIDDEN = new int[]{
    Pass.UPDATE_FOLDING,
    Pass.UPDATE_VISIBLE,
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
  private static final int[] EXCEPT_VISIBLE = new int[]{
    Pass.UPDATE_ALL,
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
  }

  private TextEditorHighlightingPass[] getPasses(int[] passesToIgnore) {
    if (myCompiled) {
      passesToIgnore = EXCEPT_OVERRIDDEN;
    }
    else if (myProject.isDisposed() || !DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(myFile)) {
      return TextEditorHighlightingPass.EMPTY_ARRAY;
    }
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    renewFile();
    if (myFile == null) return TextEditorHighlightingPass.EMPTY_ARRAY;

    TextEditorHighlightingPassRegistrarEx passRegistrar = TextEditorHighlightingPassRegistrarEx.getInstanceEx(myProject);

    List<TextEditorHighlightingPass> createdPasses = passRegistrar.instantiatePasses(myFile, myEditor, passesToIgnore);
    return createdPasses.toArray(new TextEditorHighlightingPass[createdPasses.size()]);
  }

  @NotNull
  public TextEditorHighlightingPass[] createPassesForVisibleArea() {
    return getPasses(EXCEPT_VISIBLE);
  }

  @NotNull
  public TextEditorHighlightingPass[] createPassesForEditor() {
    return getPasses(ArrayUtil.EMPTY_INT_ARRAY);
  }
}