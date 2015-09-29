/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.*;
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
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.intellij.execution.ui.ConsoleViewContentType.registerNewConsoleViewType;

/**
 * @author peter
 */
public class ConsoleViewUtil {

  public static final Key<Boolean> EDITOR_IS_CONSOLE_VIEW = Key.create("EDITOR_IS_CONSOLE_VIEW");


  public static EditorEx setupConsoleEditor(Project project, final boolean foldingOutlineShown, final boolean lineMarkerAreaShown) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = ((EditorFactoryImpl)editorFactory).createDocument(true);
    UndoUtil.disableUndoFor(document);
    EditorEx editor = (EditorEx) editorFactory.createViewer(document, project);
    setupConsoleEditor(editor, foldingOutlineShown, lineMarkerAreaShown);
    return editor;
  }

  public static void setupConsoleEditor(@NotNull final EditorEx editor, final boolean foldingOutlineShown, final boolean lineMarkerAreaShown) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
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
        editorSettings.setRightMarginShown(false);
        editorSettings.setCaretRowShown(false);
        editor.getGutterComponentEx().setPaintBackground(false);

        editor.putUserData(EDITOR_IS_CONSOLE_VIEW, true);

        final DelegateColorScheme scheme = updateConsoleColorScheme(editor.getColorsScheme());
        if (UISettings.getInstance().PRESENTATION_MODE) {
          scheme.setEditorFontSize(UISettings.getInstance().PRESENTATION_MODE_FONT_SIZE);
        }
        editor.setColorsScheme(scheme);
      }
    });
  }

  @NotNull
  public static DelegateColorScheme updateConsoleColorScheme(@NotNull EditorColorsScheme scheme) {
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

  public static boolean isConsoleViewEditor(@NotNull Editor editor) {
    return editor.getUserData(EDITOR_IS_CONSOLE_VIEW) == Boolean.TRUE;
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static class ColorCache {
    static {
      LafManager.getInstance().addLafManagerListener(new LafManagerListener() {
        @Override
        public void lookAndFeelChanged(LafManager source) {
          mergedTextAttributes.clear();
        }
      });
    }
    static final Map<Key, List<TextAttributesKey>> textAttributeKeys = ContainerUtil.newConcurrentMap();
    static final Map<Key, TextAttributes> mergedTextAttributes = new ConcurrentFactoryMap<Key, TextAttributes>() {
      @Nullable
      @Override
      protected TextAttributes create(Key contentKey) {
        EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
        TextAttributes result = scheme.getAttributes(HighlighterColors.TEXT);
        for (TextAttributesKey key : textAttributeKeys.get(contentKey)) {
          TextAttributes attributes = scheme.getAttributes(key);
          if (attributes != null) {
            result = TextAttributes.merge(result, attributes);
          }
        }
        return result;
      }
    };

    static final Map<List<TextAttributesKey>, Key> keys = new ConcurrentFactoryMap<List<TextAttributesKey>, Key>() {
      @Override
      protected Key create(List<TextAttributesKey> keys) {
        StringBuilder keyName = new StringBuilder("ConsoleViewUtil_");
        for (TextAttributesKey key : keys) {
          keyName.append("_").append(key.getExternalName());
        }
        final Key newKey = new Key(keyName.toString());
        textAttributeKeys.put(newKey, keys);
        ConsoleViewContentType contentType = new ConsoleViewContentType(keyName.toString(), HighlighterColors.TEXT) {
          @Override
          public TextAttributes getAttributes() {
            return mergedTextAttributes.get(newKey);
          }
        };

        registerNewConsoleViewType(newKey, contentType);
        return newKey;
      }
    };
  }

  public static void printWithHighlighting(@NotNull ConsoleView console, @NotNull String text, @NotNull SyntaxHighlighter highlighter) {
    Lexer lexer = highlighter.getHighlightingLexer();
    lexer.start(text, 0, text.length(), 0);

    IElementType tokenType;
    while ((tokenType = lexer.getTokenType()) != null) {
      console.print(lexer.getTokenText(), getContentTypeForToken(tokenType, highlighter));
      lexer.advance();
    }
  }

  @NotNull
  public static ConsoleViewContentType getContentTypeForToken(@NotNull IElementType tokenType, @NotNull SyntaxHighlighter highlighter) {
    TextAttributesKey[] keys = highlighter.getTokenHighlights(tokenType);
    return keys.length == 0 ? ConsoleViewContentType.NORMAL_OUTPUT :
           ConsoleViewContentType.getConsoleViewType(ColorCache.keys.get(Arrays.asList(keys)));
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
