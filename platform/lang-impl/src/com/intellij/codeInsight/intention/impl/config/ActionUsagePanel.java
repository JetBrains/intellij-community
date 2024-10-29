// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class ActionUsagePanel extends JPanel implements Disposable {
  private static final @NonNls String SPOT_MARKER = "spot";
  protected final EditorEx editor;
  protected final RangeBlinker rangeBlinker;

  public ActionUsagePanel() {
    editor = (EditorEx)createEditor("", 10, 3, -1);
    setLayout(new BorderLayout());
    add(editor.getComponent(), BorderLayout.CENTER);
    TextAttributes blinkAttributes =
      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
    rangeBlinker = new RangeBlinker(editor, blinkAttributes, Integer.MAX_VALUE, this);
  }

  public EditorEx getEditor() {
    return editor;
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
      if (editor.isDisposed()) return;
      DocumentUtil.writeInRunUndoTransparentAction(() -> configureByText(usageText, fileType));
    });
  }

  private void configureByText(String usageText, FileType fileType) {
    Document document = editor.getDocument();
    String text = StringUtil.convertLineSeparators(usageText);
    document.replaceString(0, document.getTextLength(), text);
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    editor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, scheme, null));
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
    rangeBlinker.resetMarkers(markers);
    if (!markers.isEmpty()) {
      rangeBlinker.startBlinking();
    }
  }

  @Override
  public void dispose() {
    rangeBlinker.stopBlinking();
    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(editor);
  }

  private void reinitViews() {
    editor.reinitSettings();
    editor.getMarkupModel().removeAllHighlighters();
  }
}
