// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ui.RangeBlinker;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class ActionUsagePanel extends JPanel implements Disposable {
  private static final @NonNls String SPOT_MARKER = "spot";
  protected final EditorEx myEditor;
  protected final RangeBlinker myRangeBlinker;


  public ActionUsagePanel() {
    myEditor = (EditorEx)createEditor("", 10, 3, -1);
    setLayout(new BorderLayout());
    add(myEditor.getComponent(), BorderLayout.CENTER);
    TextAttributes blinkAttributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
    myRangeBlinker = new RangeBlinker(myEditor, blinkAttributes, Integer.MAX_VALUE);
  }

  public EditorEx getEditor() {
    return myEditor;
  }

  protected static Editor createEditor(String text, int column, int line, int selectedLine) {
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

  public void reset(String usageText, FileType fileType) {
    reinitViews();
    SwingUtilities.invokeLater(() -> {
      if (myEditor.isDisposed()) return;
      DocumentUtil.writeInRunUndoTransparentAction(() -> configureByText(usageText, fileType));
    });
  }

  private void configureByText(String usageText, FileType fileType) {
    Document document = myEditor.getDocument();
    String text = StringUtil.convertLineSeparators(usageText);
    document.replaceString(0, document.getTextLength(), text);
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    myEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, scheme, null));
    setupSpots(document);
  }

  private void setupSpots(Document document) {
    List<Segment> markers = new ArrayList<>();
    while (true) {
      String text = document.getText();
      int spotStart = text.indexOf("<" + SPOT_MARKER + ">");
      if (spotStart < 0) break;
      int spotEnd = text.indexOf("</" + SPOT_MARKER + ">", spotStart);
      if (spotEnd < 0) break;

      document.deleteString(spotEnd, spotEnd + SPOT_MARKER.length() + 3);
      document.deleteString(spotStart, spotStart + SPOT_MARKER.length() + 2);
      Segment spotMarker = new Segment() {
        @Override
        public int getStartOffset() {
          return spotStart;
        }

        @Override
        public int getEndOffset() {
          return spotEnd - SPOT_MARKER.length() - 2;
        }
      };
      markers.add(spotMarker);
    }
    myRangeBlinker.resetMarkers(markers);
    if (!markers.isEmpty()) {
      myRangeBlinker.startBlinking();
    }
  }

  @Override
  public void dispose() {
    myRangeBlinker.stopBlinking();
    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myEditor);
  }

  private void reinitViews() {
    myEditor.reinitSettings();
    myEditor.getMarkupModel().removeAllHighlighters();
  }
}
