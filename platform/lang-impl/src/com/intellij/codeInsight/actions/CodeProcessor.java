/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.intellij.codeInsight.actions.TextRangeType.*;

class CodeProcessor {
  private final Editor myEditor;

  private final boolean myShouldOptimizeImports;
  private final boolean myShouldRearrangeCode;
  private final boolean myProcessSelectedText;
  private final boolean myProcessChangesTextOnly;
  private final boolean myShouldNotify;

  private final Project myProject;
  private final PsiFile myFile;
  private final TextRangeType myProcessingType;

  private AbstractLayoutCodeProcessor myProcessor;

  public CodeProcessor(PsiFile file,
                       Editor editor,
                       LayoutCodeOptions runOptions)
  {
    myFile = file;
    myProject = file.getProject();
    myEditor = editor;

    myProcessingType = runOptions.getTextRangeType();

    myShouldOptimizeImports = runOptions.isOptimizeImports();
    myShouldRearrangeCode = runOptions.isRearrangeCode();
    myProcessSelectedText = myEditor != null && runOptions.getTextRangeType() == SELECTED_TEXT;
    myProcessChangesTextOnly = runOptions.getTextRangeType() == VCS_CHANGED_TEXT;

    myShouldNotify = myEditor != null && !myProcessSelectedText;
  }

  public void processCode() {
    if (myShouldOptimizeImports) {
      myProcessor = new OptimizeImportsProcessor(myProject, myFile);
    }

    if (myProcessor != null) {
      if (myProcessSelectedText) {
        myProcessor = new ReformatCodeProcessor(myProcessor, myEditor.getSelectionModel());
      }
      else {
        myProcessor = new ReformatCodeProcessor(myProcessor, myProcessChangesTextOnly);
      }
    }
    else {
      if (myProcessSelectedText) {
        myProcessor = new ReformatCodeProcessor(myFile, myEditor.getSelectionModel());
      }
      else {
        myProcessor = new ReformatCodeProcessor(myFile, myProcessChangesTextOnly);
      }
    }

    if (myShouldRearrangeCode) {
      if (myProcessSelectedText) {
        myProcessor = new RearrangeCodeProcessor(myProcessor, myEditor.getSelectionModel());
      }
      else {
        myProcessor = new RearrangeCodeProcessor(myProcessor);
      }
    }

    if (myShouldNotify) {
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(myFile);
      if (document != null) {
        myProcessor.setPostRunnable(getNotificationCallBack(document));
      }
    }

    myProcessor.run();
  }

  private Runnable getNotificationCallBack(@NotNull final Document document) {
    final CharSequence textBeforeChange = document.getImmutableCharSequence();
    final Runnable calculateChangesAndNotify = new Runnable() {
      @Override
      public void run() {
        final String info = prepareMessage(document, textBeforeChange);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!myEditor.isDisposed() && myEditor.getComponent().isShowing()) {
              showHint(myEditor, info);
            }
          }
        });
      }
    };

    return new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(calculateChangesAndNotify);
      }
    };
  }

  protected static int getProcessedLinesNumber(final Document document, final CharSequence before) {
    int totalLinesProcessed = 0;
    try {
      List<TextRange> ranges = FormatChangedTextUtil.calculateChangedTextRanges(document, before);
      for (TextRange range : ranges) {
        int lineStartNumber = document.getLineNumber(range.getStartOffset());
        int lineEndNumber = document.getLineNumber(range.getEndOffset());

        totalLinesProcessed += lineEndNumber - lineStartNumber + 1;
      }
    }
    catch (FilesTooBigForDiffException e) {
      return -1;
    }
    return totalLinesProcessed;
  }

  @NotNull
  private String prepareMessage(@NotNull Document document, @NotNull CharSequence textBeforeChange) {

    //int totalLinesProcessed = getProcessedLinesNumber(document, textBeforeChange);
    //
    //String linesInfo = "";
    //if (totalLinesProcessed >= 0) {
    //  linesInfo = "Changed " + totalLinesProcessed + " lines";
    //}
    //
    //String scopeInfo = myProcessingType == VCS_CHANGED_TEXT ? ", processed only changed lines since last revision" : null;
    //String actionsInfo = "Performed: formatting";
    //if (myShouldOptimizeImports) {
    //  actionsInfo += ", import optimization";
    //}
    //if (myShouldRearrangeCode) {
    //  actionsInfo += " and code rearrangement";
    //}
    //
    //String shortcutInfo = "Show reformat dialog: " + KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ReformatFile"));
    //
    //String info = linesInfo;
    //if (scopeInfo != null) {
    //  info += scopeInfo;
    //}
    //info += "\n";
    //info += actionsInfo + "\n";
    //info += shortcutInfo;

    StringBuilder builder = new StringBuilder();

    for (String info : myProcessor.getNotificationInfo()) {
      builder.append(info).append('\n');
    }

    return builder.toString();


  }

  private static void showHint(@NotNull Editor editor, @NotNull String info) {
    JComponent component = HintUtil.createInformationLabel(info);
    LightweightHint hint = new LightweightHint(component);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER,
                                                     HintManager.HIDE_BY_ANY_KEY |
                                                     HintManager.HIDE_BY_TEXT_CHANGE |
                                                     HintManager.HIDE_BY_SCROLLING,
                                                     0, false);
  }
}
