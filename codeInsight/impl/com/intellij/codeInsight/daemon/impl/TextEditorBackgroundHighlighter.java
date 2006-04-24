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
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;

public class TextEditorBackgroundHighlighter implements BackgroundEditorHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.TextEditorBackgroundHighlighter");

  private static final int[] ALL_PASSES = new int[]{
    Pass.UPDATE_FOLDING,
    Pass.UPDATE_VISIBLE,
    Pass.POPUP_HINTS,
    Pass.UPDATE_ALL,
    Pass.POST_UPDATE_ALL,
    Pass.UPDATE_OVERRIDEN_MARKERS,
    Pass.LOCAL_INSPECTIONS,
    Pass.POPUP_HINTS2,
    Pass.EXTERNAL_TOOLS
  };

  private static final int[] VISIBLE_PASSES = new int[]{
    Pass.UPDATE_FOLDING,
    Pass.UPDATE_VISIBLE,
    Pass.POPUP_HINTS
  };


  private Editor myEditor;
  private Document myDocument;
  private PsiFile myFile;
  private Project myProject;
  private boolean myCompiled;

  public TextEditorBackgroundHighlighter(Project project, Editor editor) {
    myProject = project;
    myEditor = editor;
    myDocument = myEditor.getDocument();
    renewFile();
  }

  private void renewFile() {
    if (myFile == null || !myFile.isValid()) {
      myFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
      if (myFile != null) {
        LOG.assertTrue(myFile.isValid());
      }
      myCompiled = myFile instanceof PsiCompiledElement;
      if (myCompiled) {
        myFile = (PsiFile)((PsiCompiledElement)myFile).getMirror();
      }
    }
  }

  private TextEditorHighlightingPass[] getPasses(int[] passesToPerform) {
    ArrayList<TextEditorHighlightingPass> passes = new ArrayList<TextEditorHighlightingPass>();

    renewFile();
    if (myFile != null && DaemonCodeAnalyzer.getInstance(myProject).isHighlightingAvailable(myFile)) {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      for (int aPassesToPerform : passesToPerform) {
        appendPass(passes, aPassesToPerform);
      }
    }
    return TextEditorHighlightingPassRegistrar.getInstance(myProject).modifyHighlightingPasses(passes, myFile, myEditor);
  }

  private void appendPass(ArrayList<TextEditorHighlightingPass> passes, int currentPass) {
    TextRange range = calculateRangeToProcess(myEditor, currentPass);
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    TextEditorHighlightingPass pass = createDaemonPass(startOffset, endOffset, currentPass);
    if (pass != null) {
      passes.add(pass);
    }
  }

  @Nullable
  private TextEditorHighlightingPass createDaemonPass(int startOffset,
                                                      int endOffset,
                                                      int pass) {
    LOG.assertTrue(endOffset <= myDocument.getTextLength());
    if (startOffset > endOffset) return null;

    switch (pass) {
      case Pass.UPDATE_FOLDING:
        return new CodeFoldingPass(myProject, myEditor);

      case Pass.UPDATE_ALL:
      case Pass.UPDATE_VISIBLE:
        return new GeneralHighlightingPass(myProject, myFile, myDocument, startOffset, endOffset, myCompiled, pass == Pass.UPDATE_ALL);

      case Pass.POST_UPDATE_ALL:
        return new PostHighlightingPass(myProject, myFile, myEditor, startOffset, endOffset, myCompiled);

      case Pass.UPDATE_OVERRIDEN_MARKERS:
        return new OverriddenMarkersPass(myProject, myFile, myDocument, startOffset, endOffset);

      case Pass.LOCAL_INSPECTIONS:
        return myCompiled || !myFile.isPhysical()
               ? null
               : new LocalInspectionsPass(myProject, myFile, myDocument, startOffset, endOffset);

      case Pass.POPUP_HINTS:
      case Pass.POPUP_HINTS2:
        if (!myCompiled) {
          return new ShowIntentionsPass(myProject, myEditor, IntentionManager.getInstance(myProject).getIntentionActions(),
                                        pass == Pass.POPUP_HINTS2);
        }
        else {
          return null;
        }

      case Pass.EXTERNAL_TOOLS:
        return new ExternalToolPass(myFile, myEditor, startOffset, endOffset);

      default:
        LOG.error(Integer.toString(pass));
        return null;
    }
  }


  public TextEditorHighlightingPass[] createPassesForVisibleArea() {
    return getPasses(VISIBLE_PASSES);
  }

  public TextEditorHighlightingPass[] createPassesForEditor() {
    return getPasses(ALL_PASSES);
  }

  private TextRange calculateRangeToProcess(Editor editor, int pass) {
    if (pass == Pass.POPUP_HINTS || pass == Pass.POPUP_HINTS2) {
      Rectangle rect = editor.getScrollingModel().getVisibleArea();
      LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(rect.x, rect.y));
      LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(rect.x + rect.width, rect.y + rect.height));

      int visibleStart = editor.logicalPositionToOffset(startPosition);
      int visibleEnd = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));
      return new TextRange(visibleStart, visibleEnd);
    }

    Document document = editor.getDocument();
    int startOffset;
    int endOffset;

    int part;
    if (pass == Pass.UPDATE_OVERRIDEN_MARKERS) {
      part = FileStatusMap.OVERRIDEN_MARKERS;
    }
    else if (pass == Pass.LOCAL_INSPECTIONS) {
      part = FileStatusMap.LOCAL_INSPECTIONS;
    }
    else {
      part = FileStatusMap.NORMAL_HIGHLIGHTERS;
    }

    PsiElement dirtyScope = DaemonCodeAnalyzer.getInstance(myProject).getFileStatusMap().getFileDirtyScope(document, part);
    if (dirtyScope != null && dirtyScope.isValid()) {
      if (pass != Pass.POST_UPDATE_ALL) {
        PsiFile file = dirtyScope.getContainingFile();
        if (file.getTextLength() != document.getTextLength()) {
          LOG.error("Length wrong! dirtyScope:" + dirtyScope,
                    "file length:" + file.getTextLength(),
                    "document length:" + document.getTextLength(),
                    "file stamp:" + file.getModificationStamp(),
                    "document stamp:" + document.getModificationStamp(),
                    "file text:" + file.getText(),
                    "document text:" + document.getText());
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Dirty block optimization works");
        }
        TextRange range = dirtyScope.getTextRange();
        startOffset = range.getStartOffset();
        endOffset = range.getEndOffset();
      }
      else {
        startOffset = 0;
        endOffset = document.getTextLength();
      }
    }
    else {
      /*
      if (LOG.isDebugEnabled()) {
        LOG.debug("Do not update highlighters - highlighters are up to date");
      }
      */
      startOffset = Integer.MAX_VALUE;
      endOffset = Integer.MIN_VALUE;
    }

    if (pass == Pass.UPDATE_VISIBLE) {
      Rectangle rect = editor.getScrollingModel().getVisibleArea();
      LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(rect.x, rect.y));

      int visibleStart = editor.logicalPositionToOffset(startPosition);
      if (visibleStart > startOffset) {
        startOffset = visibleStart;
      }
      LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(rect.x + rect.width, rect.y + rect.height));

      int visibleEnd = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));
      if (visibleEnd < endOffset) {
        endOffset = visibleEnd;
      }
    }

    return new TextRange(startOffset, endOffset);
  }
}