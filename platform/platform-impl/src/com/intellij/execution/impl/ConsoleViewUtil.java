/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.project.Project;

import java.awt.*;

/**
 * @author peter
 */
public class ConsoleViewUtil {
  public static EditorEx setupConsoleEditor(Project project, final boolean foldingOutlineShown) {
    EditorEx editor = (EditorEx) EditorFactory
      .getInstance().createViewer(((EditorFactoryImpl)EditorFactory.getInstance()).createDocument(true), project);
    editor.setSoftWrapAppliancePlace(SoftWrapAppliancePlaces.CONSOLE);

    final EditorSettings editorSettings = editor.getSettings();
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(foldingOutlineShown);
    editorSettings.setAdditionalPageAtBottom(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(0);

    final DelegateColorScheme scheme = updateConsoleColorScheme(editor.getColorsScheme());
    editor.setColorsScheme(scheme);
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);
    scheme.setColor(EditorColors.RIGHT_MARGIN_COLOR, null);
    return editor;
  }

  public static DelegateColorScheme updateConsoleColorScheme(EditorColorsScheme scheme) {
    return new DelegateColorScheme(scheme) {
      @Override
      public Color getDefaultBackground() {
        final Color color = getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
        return color == null ? super.getDefaultBackground() : color;
      }

      @Override
      public int getEditorFontSize() {
        return getConsoleFontSize();
      }

      @Override
      public String getEditorFontName() {
        return getConsoleFontName();
      }

      @Override
      public float getLineSpacing() {
        return getConsoleLineSpacing();
      }

      @Override
      public Font getFont(EditorFontType key) {
        return super.getFont(EditorFontType.getConsoleType(key));
      }
    };
  }
}
