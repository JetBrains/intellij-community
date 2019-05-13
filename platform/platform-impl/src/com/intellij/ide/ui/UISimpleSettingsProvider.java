/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;

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
  public void consumeTopHits(String pattern, Consumer<Object> collector, Project project) {
    pattern = pattern.trim().toLowerCase();
    if (StringUtil.isBetween(pattern, "cyc", "cyclic ") || StringUtil.isBetween(pattern, "scr", "scroll ")) {
      collector.consume(CYCLING_SCROLLING);
    } else if (patternContains(pattern, "memo")) {
      collector.consume(MEMORY_INDICATOR);
    } else if (StringUtil.isBetween(pattern, "nav", "navigation bar ") || StringUtil.isBetween(pattern, "navb", "navbar ")) {
      collector.consume(SHOW_NAVIGATION_BAR);
    } else if (StringUtil.isBetween(pattern, "tool", "toolbar ")) {
      collector.consume(SHOW_MAIN_TOOLBAR);
    } else if (StringUtil.isBetween(pattern, "tool w", "tool window bars") || StringUtil.isBetween(pattern, "toolw", "toolwindow ")) {
      collector.consume(HIDE_TOOL_STRIPES);
    } else if (StringUtil.isBetween(pattern, "stat", "status bar ")) {
      collector.consume(SHOW_STATUS_BAR);
    } else if (StringUtil.isBetween(pattern, "curs", "cursor ") || StringUtil.isBetween(pattern, "block ", "block cursor ")
      || StringUtil.isBetween(pattern, "caret", "caret ") || StringUtil.isBetween(pattern, "block ", "block caret ")) {
      collector.consume(IS_BLOCK_CURSOR);
    } else if (StringUtil.isBetween(pattern, "whites", "whitespaces ") || StringUtil.isBetween(pattern, "show whi", "show whitespaces ")) {
      collector.consume(IS_WHITESPACES_SHOWN);
    } else if (StringUtil.isBetween(pattern, "line ", "line numbers ") || StringUtil.isBetween(pattern, "show li", "show line numbers ")) {
      collector.consume(ARE_LINE_NUMBERS_SHOWN);
    } else if (StringUtil.isBetween(pattern, "separa ", "separators ") || StringUtil.isBetween(pattern, "method s", "method separators ")) {
      collector.consume(SHOW_METHOD_SEPARATORS);
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
