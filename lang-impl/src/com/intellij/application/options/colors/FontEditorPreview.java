package com.intellij.application.options.colors;

import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.util.EventDispatcher;

import java.awt.*;
import java.util.ArrayList;

public class FontEditorPreview implements PreviewPanel{
  private final EditorEx myEditor;

  private final ColorAndFontOptions myOptions;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public FontEditorPreview(final ColorAndFontOptions options) {
    myOptions = options;

    String text = "IntelliJ IDEA is a full-featured Java IDE\n" +
                  "with a high level of usability and outstanding\n" +
                  "advanced code editing and refactoring support.";

    myEditor = (EditorEx)createEditor(text, 10, 3, -1);

    ErrorStripeRenderer renderer = new TrafficLightRenderer(null,null,null,null){
      protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus(boolean fillErrorsCount) {
        DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
        status.errorAnalyzingFinished = true;
        status.passStati = new ArrayList<DaemonCodeAnalyzerStatus.PassStatus>();
        status.errorCount = new int[]{1, 2};
        return status;
      }
    };
    ((EditorMarkupModel)myEditor.getMarkupModel()).setErrorStripeRenderer(renderer);
    ((EditorMarkupModel)myEditor.getMarkupModel()).setErrorStripeVisible(true);

  }

  private Editor createEditor(String text, int column, int line, int selectedLine) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument(text);
    EditorEx editor = (EditorEx)editorFactory.createViewer(editorDocument);
    editor.setColorsScheme(myOptions.getSelectedScheme());
    EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(true);
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

    return editor;
  }

  public Component getPanel() {
    return myEditor.getComponent();
  }

  public void updateView() {
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    myEditor.setColorsScheme(scheme);
    myEditor.reinitSettings();

  }

  public void blinkSelectedHighlightType(Object description) {
  }

  public void addListener(final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  public void disposeUIResources() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    editorFactory.releaseEditor(myEditor);
  }
}
