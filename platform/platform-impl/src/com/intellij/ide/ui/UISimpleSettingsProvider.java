// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public class UISimpleSettingsProvider implements SearchTopHitProvider, OptionsTopHitProvider.CoveredByToggleActions {
  private static final OptionDescription CYCLING_SCROLLING = AppearanceOptionsTopHitProvider.appearance("Cyclic scrolling", "cycleScrolling");
  private static final OptionDescription MEMORY_INDICATOR = AppearanceOptionsTopHitProvider.appearance("Show Memory Indicator",
                                                                                                       "showMemoryIndicator");
  private static final OptionDescription SHOW_MAIN_TOOLBAR = AppearanceOptionsTopHitProvider.appearance("Show Main Toolbar", "showMainToolbar");
  private static final OptionDescription SHOW_NAVIGATION_BAR = AppearanceOptionsTopHitProvider.appearance("Show Navigation Bar",
                                                                                                          "showNavigationBar");
  private static final OptionDescription HIDE_TOOL_STRIPES = AppearanceOptionsTopHitProvider.appearance("Hide Tool Window Bars",
                                                                                                        "hideToolStripes");
  private static final OptionDescription SHOW_STATUS_BAR = AppearanceOptionsTopHitProvider.appearance("Show Status Bar", "showStatusBar");
  private static final OptionDescription IS_BLOCK_CURSOR = EditorOptionsTopHitProvider.editor("Show Block Cursor", "IS_BLOCK_CURSOR");
  private static final OptionDescription IS_WHITESPACES_SHOWN = EditorOptionsTopHitProvider.editor("Show Whitespaces", "IS_WHITESPACES_SHOWN");
  private static final OptionDescription ARE_LINE_NUMBERS_SHOWN = EditorOptionsTopHitProvider.editor("Show Line Numbers",
                                                                                                     "ARE_LINE_NUMBERS_SHOWN");
  private static final OptionDescription SHOW_METHOD_SEPARATORS = new DaemonCodeAnalyzerOptionDescription("SHOW_METHOD_SEPARATORS", "Show Method Separators", "appearance");


  @Override
  public void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
    pattern = StringUtil.toLowerCase(pattern.trim());
    if (StringUtil.isBetween(pattern, "cyc", "cyclic ") || StringUtil.isBetween(pattern, "scr", "scroll ")) {
      collector.accept(CYCLING_SCROLLING);
    }
    else if (patternContains(pattern, "memo")) {
      collector.accept(MEMORY_INDICATOR);
    }
    else if (StringUtil.isBetween(pattern, "nav", "navigation bar ") || StringUtil.isBetween(pattern, "navb", "navbar ")) {
      collector.accept(SHOW_NAVIGATION_BAR);
    }
    else if (StringUtil.isBetween(pattern, "tool", "toolbar ")) {
      collector.accept(SHOW_MAIN_TOOLBAR);
    }
    else if (StringUtil.isBetween(pattern, "tool w", "tool window bars") || StringUtil.isBetween(pattern, "toolw", "toolwindow ")) {
      collector.accept(HIDE_TOOL_STRIPES);
    }
    else if (StringUtil.isBetween(pattern, "stat", "status bar ")) {
      collector.accept(SHOW_STATUS_BAR);
    }
    else if (StringUtil.isBetween(pattern, "curs", "cursor ") || StringUtil.isBetween(pattern, "block ", "block cursor ")
             || StringUtil.isBetween(pattern, "caret", "caret ") || StringUtil.isBetween(pattern, "block ", "block caret ")) {
      collector.accept(IS_BLOCK_CURSOR);
    }
    else if (StringUtil.isBetween(pattern, "whites", "whitespaces ") || StringUtil.isBetween(pattern, "show whi", "show whitespaces ")) {
      collector.accept(IS_WHITESPACES_SHOWN);
    }
    else if (StringUtil.isBetween(pattern, "line ", "line numbers ") || StringUtil.isBetween(pattern, "show li", "show line numbers ")) {
      collector.accept(ARE_LINE_NUMBERS_SHOWN);
    }
    else if (StringUtil.isBetween(pattern, "separa ", "separators ") || StringUtil.isBetween(pattern, "method s", "method separators ")) {
      collector.accept(SHOW_METHOD_SEPARATORS);
    }
  }

  private static boolean patternContains(String pattern, String search) {
    for (String s : pattern.split(" ")) {
      if (s.contains(search)) {
        return true;
      }
    }
    return false;
  }
}
