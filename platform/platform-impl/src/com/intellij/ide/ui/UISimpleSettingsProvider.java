// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
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
final class UISimpleSettingsProvider implements SearchTopHitProvider, OptionsTopHitProvider.CoveredByToggleActions {
  private static final OptionDescription HIDE_TOOL_STRIPES = AppearanceOptionsTopHitProvider.appearance(IdeBundle.message("option.hide.tool.window.bars"), "hideToolStripes");
  private static final OptionDescription IS_BLOCK_CURSOR = EditorOptionsTopHitProvider.editor(IdeBundle.message("label.show.block.cursor"), "IS_BLOCK_CURSOR");
  private static final OptionDescription IS_WHITESPACES_SHOWN = EditorOptionsTopHitProvider.editor(IdeBundle.message("label.show.whitespaces"), "IS_WHITESPACES_SHOWN");
  private static final OptionDescription ARE_LINE_NUMBERS_SHOWN = EditorOptionsTopHitProvider.editor(IdeBundle.message("label.show.line.numbers"), "ARE_LINE_NUMBERS_SHOWN");

  @Override
  public void consumeTopHits(@NotNull String pattern, @NotNull Consumer<Object> collector, @Nullable Project project) {
    pattern = StringUtil.toLowerCase(pattern.trim());
    if (StringUtil.isBetween(pattern, "tool w", "tool window bars") || StringUtil.isBetween(pattern, "toolw", "toolwindow ")) {
      collector.accept(HIDE_TOOL_STRIPES);
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
  }
}
