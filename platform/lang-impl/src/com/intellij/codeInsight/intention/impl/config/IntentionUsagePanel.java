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

/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.RangeBlinker;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class IntentionUsagePanel extends JPanel{
  private final EditorEx myEditor;
  @NonNls private static final String SPOT_MARKER = "spot";
  private final RangeBlinker myRangeBlinker;

  public IntentionUsagePanel() {
    myEditor = (EditorEx)createEditor("", 10, 3, -1);
    setLayout(new BorderLayout());
    add(myEditor.getComponent(), BorderLayout.CENTER);
    TextAttributes blinkAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
    myRangeBlinker = new RangeBlinker(myEditor, blinkAttributes, Integer.MAX_VALUE);
  }

  public void reset(final String usageText, final FileType fileType) {
    reinitViews();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                configureByText(usageText, fileType);
              }
            });
          }
        });
      }
    });
  }

  private void configureByText(final String usageText, FileType fileType) {
    Document document = myEditor.getDocument();
    String text = StringUtil.convertLineSeparators(usageText);
    document.replaceString(0, document.getTextLength(), text);
    final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    myEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, scheme, null));
    setupSpots(document);
  }

  private void setupSpots(Document document) {
    List<RangeMarker> markers = new ArrayList<RangeMarker>();
    while (true) {
      String text = document.getText();
      int spotStart = text.indexOf("<" + SPOT_MARKER + ">");
      if (spotStart < 0) break;
      int spotEnd = text.indexOf("</" + SPOT_MARKER + ">", spotStart);
      if (spotEnd < 0) break;

      document.deleteString(spotEnd, spotEnd + SPOT_MARKER.length() + 3);
      document.deleteString(spotStart, spotStart + SPOT_MARKER.length() + 2);
      final RangeMarker spotMarker = document.createRangeMarker(spotStart, spotEnd - SPOT_MARKER.length() - 2);
      if (spotMarker == null) {
        break;
      }
      else {
        markers.add(spotMarker);
      }
    }
    myRangeBlinker.resetMarkers(markers);
    if (!markers.isEmpty()) {
      myRangeBlinker.startBlinking();
    }
  }

  public void dispose() {
    myRangeBlinker.stopBlinking();
    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myEditor);
  }

  private void reinitViews() {
    myEditor.reinitSettings();
    myEditor.getMarkupModel().removeAllHighlighters();
  }

  private static Editor createEditor(String text, int column, int line, int selectedLine) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument(text);
    EditorEx editor = (EditorEx)editorFactory.createViewer(editorDocument);
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    editor.setColorsScheme(scheme);
    EditorSettings settings = editor.getSettings();
    settings.setWhitespacesShown(true);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setRightMarginShown(true);
    settings.setRightMargin(60);

    LogicalPosition pos = new LogicalPosition(line, column);
    editor.getCaretModel().moveToLogicalPosition(pos);
    if (selectedLine >= 0) {
      editor.getSelectionModel().setSelection(editorDocument.getLineStartOffset(selectedLine),
                                              editorDocument.getLineEndOffset(selectedLine));
    }

    return editor;
  }
}

