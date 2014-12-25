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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
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
    myProcessSelectedText = runOptions.getTextRangeType() == SELECTED_TEXT;
    myProcessChangesTextOnly = runOptions.getTextRangeType() == VCS_CHANGED_TEXT;

    myShouldNotify = editor != null && !myProcessSelectedText;
  }

  public void processCode() {
    AbstractLayoutCodeProcessor processor;
    if (myShouldOptimizeImports) {
      processor = new OptimizeImportsProcessor(myProject, myFile);
      new ReformatCodeProcessor(processor, myProcessChangesTextOnly);
    }
    else {
      SelectionModel model = myEditor.getSelectionModel();
      TextRange range = myProcessSelectedText && model.hasSelection()
                        ? new TextRange(model.getSelectionStart(), model.getSelectionEnd())
                        : null;

      processor = new ReformatCodeProcessor(myProject, myFile, range, false);
    }

    if (myShouldRearrangeCode) {
      if (myProcessSelectedText) {
        processor = new RearrangeCodeProcessor(processor, myEditor.getSelectionModel());
      }
      else {
        processor = new RearrangeCodeProcessor(processor);
      }
    }

    if (myShouldNotify) {
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(myFile);
      final CharSequence textBeforeChange = document != null ? document.getImmutableCharSequence() : null;
      processor.setPostRunnable(new Runnable() {
        @Override
        public void run() {
          if (document != null) {
            String info = prepareMessage(document, textBeforeChange);
            showHint(myEditor, info);
          }
        }
      });
    }

    processor.run();
  }

  private int getProcessedLinesNumber(final Document document, final CharSequence before) {
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
    int totalLinesProcessed = getProcessedLinesNumber(document, textBeforeChange);

    String linesInfo = "";
    if (totalLinesProcessed >= 0) {
      linesInfo = "Changed " + totalLinesProcessed + " lines";
    }

    String scopeInfo = myProcessingType == VCS_CHANGED_TEXT ? ", processed only changed lines since last revision" : null;
    String actionsInfo = "Performed: formatting";
    if (myShouldOptimizeImports) {
      actionsInfo += ", import optimization";
    }
    if (myShouldRearrangeCode) {
      actionsInfo += " and code rearrangement";
    }

    String shortcutInfo = "Show reformat dialog: " + KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ReformatFile"));

    String info = linesInfo;
    if (scopeInfo != null) {
      info += scopeInfo;
    }
    info += "\n";
    info += actionsInfo + "\n";
    info += shortcutInfo;

    return info;
  }

  private void showHint(@NotNull Editor editor, @NotNull String info) {
    JComponent component = HintUtil.createInformationLabel(info);
    LightweightHint hint = new LightweightHint(component);
    HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER,
                                                     HintManager.HIDE_BY_ANY_KEY |
                                                     HintManager.HIDE_BY_TEXT_CHANGE |
                                                     HintManager.HIDE_BY_SCROLLING,
                                                     0, false);
  }
}
