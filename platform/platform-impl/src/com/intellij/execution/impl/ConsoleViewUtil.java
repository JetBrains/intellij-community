// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.filters.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConsoleViewUtil {
  public static final Key<Boolean> EDITOR_IS_CONSOLE_HISTORY_VIEW = Key.create("EDITOR_IS_CONSOLE_HISTORY_VIEW");

  private static final Key<Boolean> REPLACE_ACTION_ENABLED = Key.create("REPLACE_ACTION_ENABLED");

  public static @NotNull EditorEx setupConsoleEditor(@Nullable Project project, boolean foldingOutlineShown, boolean lineMarkerAreaShown) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document document = ((EditorFactoryImpl)editorFactory).createDocument(true);
    UndoUtil.disableUndoFor(document);
    EditorEx editor = (EditorEx)editorFactory.createViewer(document, project, EditorKind.CONSOLE);
    setupConsoleEditor(editor, foldingOutlineShown, lineMarkerAreaShown);
    return editor;
  }

  public static void setupConsoleEditor(final @NotNull EditorEx editor, final boolean foldingOutlineShown, final boolean lineMarkerAreaShown) {
    ApplicationManager.getApplication().runReadAction(() -> {
      EditorSettings editorSettings = editor.getSettings();
      editorSettings.setLineMarkerAreaShown(lineMarkerAreaShown);
      editorSettings.setIndentGuidesShown(false);
      editorSettings.setLineNumbersShown(false);
      editorSettings.setFoldingOutlineShown(foldingOutlineShown);
      editorSettings.setAdditionalPageAtBottom(false);
      editorSettings.setAdditionalColumnsCount(0);
      editorSettings.setAdditionalLinesCount(0);
      editorSettings.setRightMarginShown(false);
      editorSettings.setCaretRowShown(false);
      editorSettings.setShowingSpecialChars(false);
      editor.getGutterComponentEx().setPaintBackground(false);

      EditorColorsScheme scheme = updateConsoleColorScheme(editor.getColorsScheme(), editor);
      scheme.setEditorFontSize(UISettingsUtils.getInstance().getScaledConsoleFontSize());
      editor.setColorsScheme(scheme);
      editor.setHighlighter(new NullEditorHighlighter());
    });
  }

  public static void setupLanguageConsoleEditor(final @NotNull EditorEx editor) {
    setupConsoleEditor(editor, false, false);

    editor.getContentComponent().setFocusCycleRoot(false);
    editor.setBorder(null);

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setAdditionalLinesCount(1);
    editorSettings.setAdditionalColumnsCount(1);
  }

  private static final class NullEditorHighlighter extends EmptyEditorHighlighter {
    private static final TextAttributes NULL_ATTRIBUTES = new TextAttributes();

    NullEditorHighlighter() {
      super(NULL_ATTRIBUTES);
    }

    @Override
    public void setColorScheme(@NotNull EditorColorsScheme scheme) {}
  }

  public static @NotNull EditorColorsScheme updateConsoleColorScheme(@NotNull EditorColorsScheme scheme, EditorEx editor) {
    // Bounded to the editor delegate color scheme helps to reflect console font size settings changes during custom IDE scale
    return editor.createBoundColorSchemeDelegate(updateConsoleColorScheme(scheme));
  }

  public static @NotNull DelegateColorScheme updateConsoleColorScheme(@NotNull EditorColorsScheme scheme) {
    return new DelegateColorScheme(scheme) {
      @Override
      public @NotNull Color getDefaultBackground() {
        final Color color = getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
        return color == null ? super.getDefaultBackground() : color;
      }

      @Override
      public @NotNull FontPreferences getFontPreferences() {
        return getConsoleFontPreferences();
      }

      @Override
      public int getEditorFontSize() {
        return getConsoleFontSize();
      }

      @Override
      public float getEditorFontSize2D() {
        return getConsoleFontSize2D();
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
      public @NotNull Font getFont(EditorFontType key) {
        return super.getFont(EditorFontType.getConsoleType(key));
      }

      @Override
      public void setEditorFontSize(int fontSize) {
        setConsoleFontSize(fontSize);
      }

      @Override
      public void setEditorFontSize(float fontSize) {
        setConsoleFontSize(fontSize);
      }
    };
  }

  public static boolean isConsoleViewEditor(@NotNull Editor editor) {
    return editor.getEditorKind() == (EditorKind.CONSOLE);
  }

  public static boolean isReplaceActionEnabledForConsoleViewEditor(@NotNull Editor editor) {
    return editor.getUserData(REPLACE_ACTION_ENABLED) == Boolean.TRUE;
  }

  public static void enableReplaceActionForConsoleViewEditor(@NotNull Editor editor) {
    editor.putUserData(REPLACE_ACTION_ENABLED, true);
  }

  @Service(Service.Level.APP)
  private static final class ColorCache {
    static final ColorCache INSTANCE = new ColorCache();

    private ColorCache() {
      ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(LafManagerListener.TOPIC, new LafManagerListener() {
        @Override
        public void lookAndFeelChanged(@NotNull LafManager source) {
          mergedTextAttributes.clear();
        }
      });
    }

    private final Map<Key<?>, List<TextAttributesKey>> textAttributeKeys = new ConcurrentHashMap<>();
    private final Map<Key<?>, TextAttributes> mergedTextAttributes = ConcurrentFactoryMap.createMap(contentKey-> {
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
    );

    private final Map<List<TextAttributesKey>, Key<?>> keys = new ConcurrentHashMap<>();

    private Key<?> getKey(List<TextAttributesKey> keyList) {
      return keys.computeIfAbsent(keyList, keys -> {
        StringBuilder keyName = new StringBuilder("ConsoleViewUtil_");
        for (TextAttributesKey key : keys) {
          keyName.append("_").append(key.getExternalName());
        }
        Key<?> newKey = new Key<>(keyName.toString());
        textAttributeKeys.put(newKey, keys);
        ConsoleViewContentType contentType = new ConsoleViewContentType(keyName.toString(), new TextAttributes()) {
          @Override
          public TextAttributes getAttributes() {
            return mergedTextAttributes.get(newKey);
          }
        };

        ConsoleViewContentType.registerNewConsoleViewType(newKey, contentType);
        return newKey;
      });
    }
  }

  public static void printWithHighlighting(@NotNull ConsoleView console, @NotNull String text, @NotNull SyntaxHighlighter highlighter) {
    printWithHighlighting(console, text, highlighter, null);
  }

  public static void printWithHighlighting(@NotNull ConsoleView console, @NotNull String text,
                                           @NotNull SyntaxHighlighter highlighter,
                                           Runnable doOnNewLine) {
    Lexer lexer = highlighter.getHighlightingLexer();
    lexer.start(text, 0, text.length(), 0);

    IElementType tokenType;
    while ((tokenType = lexer.getTokenType()) != null) {
      ConsoleViewContentType contentType = getContentTypeForToken(tokenType, highlighter);
      StringTokenizer eolTokenizer = new StringTokenizer(lexer.getTokenText(), "\n", true);
      while (eolTokenizer.hasMoreTokens()){
        String tok = eolTokenizer.nextToken();
        console.print(tok, contentType);
        if (doOnNewLine != null && "\n".equals(tok)) {
            doOnNewLine.run();
        }
      }

      lexer.advance();
    }
  }

  public static @NotNull ConsoleViewContentType getContentTypeForToken(@NotNull IElementType tokenType,
                                                                       @NotNull SyntaxHighlighter highlighter) {
    TextAttributesKey[] keys = highlighter.getTokenHighlights(tokenType);
    if (keys.length == 0) {
      return ConsoleViewContentType.NORMAL_OUTPUT;
    }
    return ConsoleViewContentType.getConsoleViewType(ColorCache.INSTANCE.getKey(List.of(keys)));
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

  public static @NotNull List<Filter> computeConsoleFilters(@NotNull Project project,
                                                            @Nullable ConsoleView consoleView,
                                                            @NotNull GlobalSearchScope searchScope) {
    List<Filter> result = new ArrayList<>();
    for (ConsoleFilterProvider eachProvider : ConsoleFilterProvider.FILTER_PROVIDERS.getExtensions()) {
      Filter[] filters;
      if (consoleView != null && eachProvider instanceof ConsoleDependentFilterProvider) {
        filters = ((ConsoleDependentFilterProvider)eachProvider).getDefaultFilters(consoleView, project, searchScope);
      }
      else if (eachProvider instanceof ConsoleFilterProviderEx) {
        filters = ((ConsoleFilterProviderEx)eachProvider).getDefaultFilters(project, searchScope);
      }
      else {
        filters = eachProvider.getDefaultFilters(project);
      }
      ContainerUtil.addAll(result, filters);
    }
    return result;
  }

  public static @NotNull InputFilter computeInputFilter(@NotNull ConsoleView consoleView,
                                                        @NotNull Project project,
                                                        @NotNull GlobalSearchScope searchScope) {
    List<ConsoleInputFilterProvider> inputFilters = ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS.getExtensionList();
    if (inputFilters.isEmpty()) {
      return (text, contentType) -> null;
    }
    List<InputFilter> allFilters = new ArrayList<>();
    for (ConsoleInputFilterProvider eachProvider : inputFilters) {
      if (eachProvider instanceof ConsoleDependentInputFilterProvider) {
        allFilters.addAll(((ConsoleDependentInputFilterProvider)eachProvider).getDefaultFilters(consoleView, project, searchScope));
      }
      else {
        allFilters.addAll(Arrays.asList(eachProvider.getDefaultFilters(project)));
      }
    }
    return new CompositeInputFilter(project, allFilters);
  }
}
