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
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.intellij.codeInsight.actions.TextRangeType.*;

class CodeProcessor {
  private static final String COLOR = "#7D7D7D";

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
      myProcessor.setPostRunnable(new Runnable() {
        @Override
        public void run() {
          String message = prepareMessage();
          if (!myEditor.isDisposed() && myEditor.getComponent().isShowing()) {
            showHint(myEditor, message);
          }
        }
      });
    }

    myProcessor.run();
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
  private String prepareMessage() {

    StringBuilder builder = new StringBuilder("<html>");
    List<String> notifications = myProcessor.getNotificationInfo();
    if (notifications.isEmpty()) {
      builder.append("code looks pretty well").append("<br>");
    } else {
      for (String info : notifications) {
        builder.append(info).append("<br>");
      }
    }

    String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ReformatFile"));
    builder.append("<span style='color:").append(COLOR).append("'>")
           .append("Show reformat dialog: ")
           .append(shortcutText)
           .append("</span>")
           .append("</html>");

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
