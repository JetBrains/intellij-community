/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;

import java.util.ArrayList;
import java.util.Collection;

class Line {
  private final String myValue;
  private final TextAttributes myTextAttributes;

  public Line(String value, TextAttributes textAttributes) {
    myValue = value.replaceAll("\r", "") + "\n";
    myTextAttributes = textAttributes;
  }

  public String getValue() {
    return myValue;
  }

  public TextAttributes getAttributes() {
    return myTextAttributes;
  }
}

public class EditorAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.EditorAdapter");

  private final Editor myEditor;

  private Alarm myFlushAlarm = new Alarm();
  private final Collection<Line> myLines = new ArrayList<Line>();
  private final Project myProject;

  private final Runnable myFlushDeferredRunnable = new Runnable() {
    public void run() {
      flushStoredLines();
    }
  };

  private synchronized void flushStoredLines() {
    Collection<Line> lines;
    synchronized (myLines) {
      lines = new ArrayList<Line>(myLines);
      myLines.clear();
    }
    ApplicationManager.getApplication().runWriteAction(writingCommand(lines));
  }


  public EditorAdapter(Editor editor, Project project) {
    myEditor = editor;
    myProject = project;
    LOG.assertTrue(myEditor.isViewer());
  }

  public void appendString(String string, TextAttributes attrs) {
    synchronized (myLines) {
      myLines.add(new Line(string, attrs));
    }

    if (myFlushAlarm.getActiveRequestCount() == 0) {
      myFlushAlarm.addRequest(myFlushDeferredRunnable, 200, ModalityState.NON_MODAL);
    }
  }

  private Runnable writingCommand(final Collection<Line> lines) {
    final Runnable command = new Runnable() {
          public void run() {

            Document document = myEditor.getDocument();

            StringBuffer buffer = new StringBuffer();
            for (Line line : lines) {
              buffer.append(line.getValue());
            }
            int endBefore = document.getTextLength();
            int endBeforeLine = endBefore;
            document.insertString(endBefore, buffer.toString());
            for (Line line : lines) {
              myEditor.getMarkupModel().addRangeHighlighter(endBeforeLine, endBeforeLine + line.getValue().length(),
                                                            HighlighterLayer.ADDITIONAL_SYNTAX, line.getAttributes(),
                                                            HighlighterTargetArea.EXACT_RANGE);
              endBeforeLine += line.getValue().length();
            }
            shiftCursorToTheEndOfDocument();
          }
        };
    return new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(myProject, command, "", null);
      }
    };
  }

  private void shiftCursorToTheEndOfDocument() {
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
    myEditor.getSelectionModel().removeSelection();
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }
}
