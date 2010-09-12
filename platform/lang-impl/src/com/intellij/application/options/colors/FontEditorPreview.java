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

package com.intellij.application.options.colors;

import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nls;

import java.awt.*;

public class FontEditorPreview implements PreviewPanel{
  private final EditorEx myEditor;

  private final ColorAndFontOptions myOptions;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public FontEditorPreview(final ColorAndFontOptions options) {
    myOptions = options;

    @Nls String text = getIDEDemoText();

    myEditor = (EditorEx)createPreviewEditor(text, 10, 3, -1, myOptions);

    installTrafficLights(myEditor);
  }

  public static String getIDEDemoText() {
    String name = ApplicationNamesInfo.getInstance().getFullProductName();
    String language = name.contains("RubyMine") ? "Ruby" : "Java";   // HACK
    return
      name + " is a full-featured " + language + " IDE\n" +
      "with a high level of usability and outstanding\n" +
      "advanced code editing and refactoring support.\n";
  }

  static void installTrafficLights(EditorEx editor) {
    ErrorStripeRenderer renderer = new TrafficLightRenderer(null,null,null,null){
      protected DaemonCodeAnalyzerStatus getDaemonCodeAnalyzerStatus(boolean fillErrorsCount) {
        DaemonCodeAnalyzerStatus status = new DaemonCodeAnalyzerStatus();
        status.errorAnalyzingFinished = true;
        status.errorCount = new int[]{1, 2};
        return status;
      }
    };
    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(renderer);
    ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);
  }

  static Editor createPreviewEditor(String text, int column, int line, int selectedLine, ColorAndFontOptions options) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument(text);
    EditorEx editor = (EditorEx)editorFactory.createViewer(editorDocument);
    editor.setColorsScheme(options.getSelectedScheme());
    EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(true);
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
