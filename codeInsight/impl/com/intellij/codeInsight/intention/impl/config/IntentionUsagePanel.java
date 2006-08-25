/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class IntentionUsagePanel extends JPanel{
  private EditorEx myEditor;
  private static final @NonNls String SPOT_MARKER = "spot";
  private final Alarm myBlinkingAlarm = new Alarm();

  public IntentionUsagePanel() {
    myEditor = (EditorEx)createEditor("", 10, 3, -1);
    setLayout(new BorderLayout());
    add(myEditor.getComponent(), BorderLayout.CENTER);
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
    myEditor.setHighlighter(HighlighterFactory.createHighlighter(fileType, scheme, null));
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
    stopBlinking();
    if (markers.size() != 0) {
      startBlinking(markers, true);
    }
  }

  private void startBlinking(final List<RangeMarker> spotMarkers, final boolean show) {
    if (ApplicationManager.getApplication().isDisposed()) return;

    MarkupModel markupModel = myEditor.getMarkupModel();
    if (show) {
      TextAttributes attr = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES);
      for (final RangeMarker rangeMarker : spotMarkers) {
        markupModel.addRangeHighlighter(rangeMarker.getStartOffset(),
                                        rangeMarker.getEndOffset(),
                                        HighlighterLayer.ADDITIONAL_SYNTAX, attr,
                                        HighlighterTargetArea.EXACT_RANGE);
      }
    }
    else {
      markupModel.removeAllHighlighters();
    }
    stopBlinking();
    myBlinkingAlarm.addRequest(new Runnable() {
      public void run() {
        startBlinking(spotMarkers, !show);
      }
    }, 400);
  }

  public void dispose() {
    stopBlinking();
    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myEditor);
  }

  private void stopBlinking() {
    myBlinkingAlarm.cancelAllRequests();
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
    editor.setHighlighter(HighlighterFactory.createJavaHighlighter(scheme, LanguageLevel.HIGHEST));
    return editor;
  }
}