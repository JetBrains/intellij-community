/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @deprecated unused
 */
@Deprecated(forRemoval = true)
class Line {
  private final String myValue;
  private final TextAttributes myTextAttributes;

  Line(String value, TextAttributes textAttributes) {
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

/**
 * @deprecated unused
 */
@Deprecated(forRemoval = true)
public class EditorAdapter {
  private static final Logger LOG = Logger.getInstance(EditorAdapter.class);

  private final Editor myEditor;

  private final Alarm myFlushAlarm = new Alarm();
  private final Collection<Line> myLines = new ArrayList<>();
  private final Project myProject;
  private final boolean myScrollToTheEndOnAppend;

  private synchronized void flushStoredLines() {
    Collection<Line> lines;
    synchronized (myLines) {
      lines = new ArrayList<>(myLines);
      myLines.clear();
    }
    if (myEditor.isDisposed() || myProject != null && myProject.isDisposed()) return;
    ApplicationManager.getApplication().runWriteAction(writingCommand(lines));
  }

  public EditorAdapter(@NotNull Editor editor, Project project, boolean scrollToTheEndOnAppend) {
    myEditor = editor;
    myProject = project;
    myScrollToTheEndOnAppend = scrollToTheEndOnAppend;
    LOG.assertTrue(myEditor.isViewer());
  }
  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  public void appendString(String string, TextAttributes attrs) {
    synchronized (myLines) {
      myLines.add(new Line(string, attrs));
    }

    if (myFlushAlarm.isEmpty()) {
      myFlushAlarm.addRequest(this::flushStoredLines, 200, ModalityState.NON_MODAL);
    }
  }

  @NotNull
  private Runnable writingCommand(@NotNull Collection<? extends Line> lines) {
    final Runnable command = () -> {
      Document document = myEditor.getDocument();

      StringBuilder buffer = new StringBuilder();
      for (Line line : lines) {
        buffer.append(line.getValue());
      }
      int endBefore = document.getTextLength();
      document.insertString(endBefore, buffer.toString());
      int endBeforeLine = endBefore;
      for (Line line : lines) {
        myEditor.getMarkupModel()
            .addRangeHighlighter(endBeforeLine, Math.min(document.getTextLength(), endBeforeLine + line.getValue().length()), HighlighterLayer.ADDITIONAL_SYNTAX,
                                 line.getAttributes(), HighlighterTargetArea.EXACT_RANGE);
        endBeforeLine += line.getValue().length();
        if (endBeforeLine > document.getTextLength()) break;
      }
      shiftCursorToTheEndOfDocument();
    };
    return () -> CommandProcessor.getInstance().executeCommand(myProject, command, "", null, UndoConfirmationPolicy.DEFAULT, myEditor.getDocument());
  }

  private void shiftCursorToTheEndOfDocument() {
    if (myScrollToTheEndOnAppend) {
      myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getTextLength());
      myEditor.getSelectionModel().removeSelection();
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }
}
