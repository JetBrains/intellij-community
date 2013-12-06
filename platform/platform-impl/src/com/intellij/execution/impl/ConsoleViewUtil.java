/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.ui.UISettings;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.execution.ui.ConsoleViewContentType.registerNewConsoleViewType;

/**
 * @author peter
 */
public class ConsoleViewUtil {

  public static final Key<Boolean> EDITOR_IS_CONSOLE_VIEW = Key.create("EDITOR_IS_CONSOLE_VIEW");


  public static EditorEx setupConsoleEditor(Project project, final boolean foldingOutlineShown, final boolean lineMarkerAreaShown) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    EditorEx editor = (EditorEx) editorFactory.createViewer(((EditorFactoryImpl)editorFactory).createDocument(true), project);
    setupConsoleEditor(editor, foldingOutlineShown, lineMarkerAreaShown);
    return editor;
  }

  public static void setupConsoleEditor(final EditorEx editor, final boolean foldingOutlineShown, final boolean lineMarkerAreaShown) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        editor.setSoftWrapAppliancePlace(SoftWrapAppliancePlaces.CONSOLE);

        final EditorSettings editorSettings = editor.getSettings();
        editorSettings.setLineMarkerAreaShown(lineMarkerAreaShown);
        editorSettings.setIndentGuidesShown(false);
        editorSettings.setLineNumbersShown(false);
        editorSettings.setFoldingOutlineShown(foldingOutlineShown);
        editorSettings.setAdditionalPageAtBottom(false);
        editorSettings.setAdditionalColumnsCount(0);
        editorSettings.setAdditionalLinesCount(0);

        editor.putUserData(EDITOR_IS_CONSOLE_VIEW, true);

        final DelegateColorScheme scheme = updateConsoleColorScheme(editor.getColorsScheme());
        if (UISettings.getInstance().PRESENTATION_MODE) {
          scheme.setEditorFontSize(UISettings.getInstance().PRESENTATION_MODE_FONT_SIZE);
        }
        editor.setColorsScheme(scheme);
        scheme.setColor(EditorColors.CARET_ROW_COLOR, null);
        scheme.setColor(EditorColors.RIGHT_MARGIN_COLOR, null);
      }
    });
  }

  public static DelegateColorScheme updateConsoleColorScheme(EditorColorsScheme scheme) {
    return new DelegateColorScheme(scheme) {
      @NotNull
      @Override
      public Color getDefaultBackground() {
        final Color color = getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
        return color == null ? super.getDefaultBackground() : color;
      }

      @NotNull
      @Override
      public FontPreferences getFontPreferences() {
        return getConsoleFontPreferences();
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

      @Override
      public void setEditorFontSize(int fontSize) {
        setConsoleFontSize(fontSize);
      }
    };
  }

  public static boolean isConsoleViewEditor(Editor editor) {
    return editor.getUserData(EDITOR_IS_CONSOLE_VIEW) == Boolean.TRUE;
  }

  // @noinspection MismatchedQueryAndUpdateOfCollection
  private static final Map<List<TextAttributesKey>, Key> ourContentTypes = Collections.synchronizedMap(new FactoryMap<List<TextAttributesKey>, Key>() {
    protected Key create(List<TextAttributesKey> keys) {
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      TextAttributes result = scheme.getAttributes(HighlighterColors.TEXT);
      StringBuilder keyName = new StringBuilder("Generated_");
      for (TextAttributesKey key : keys) {
        TextAttributes attributes = scheme.getAttributes(key);
        if (attributes != null) {
          keyName.append("_").append(key.getExternalName());
          result = TextAttributes.merge(result, attributes);
        }
      }
      Key newKey = new Key(keyName.toString());
      ConsoleViewContentType contentType = new ConsoleViewContentType(keyName.toString(), result);
      registerNewConsoleViewType(newKey, contentType);
      return newKey;
    }
  });

  public static void printWithHighlighting(@NotNull ConsoleView console, @NotNull String text, @NotNull SyntaxHighlighter highlighter) {
    Lexer lexer = highlighter.getHighlightingLexer();
    lexer.start(text, 0, text.length(), 0);

    IElementType tokenType;
    while ((tokenType = lexer.getTokenType()) != null) {
      TextAttributesKey[] keys = highlighter.getTokenHighlights(tokenType);
      ConsoleViewContentType type = keys.length == 0 ? ConsoleViewContentType.NORMAL_OUTPUT :
                                    ConsoleViewContentType.getConsoleViewType(ourContentTypes.get(Arrays.asList(keys)));
      console.print(lexer.getTokenText(), type);
      lexer.advance();
    }
  }

  public static void printAsFileType(@NotNull ConsoleView console, @NotNull String text, @NotNull FileType fileType) {
    SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(fileType, null, null);
    if (highlighter != null) {
      printWithHighlighting(console, text, highlighter);
    }
    else {
      console.print(text, ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }
}
