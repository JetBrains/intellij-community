// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.util.NlsContexts.Label;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
final class EditorOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  private static final String ID = "editor";

  @Override
  public @NotNull Collection<OptionDescription> getOptions() {
    return List.of(
      editorApp(IdeBundle.message("label.appearance.caret.blinking"), "IS_CARET_BLINKING"),
      editorApp(IdeBundle.message("label.option.appearance", OptionsTopHitProvider.messageApp("checkbox.use.block.caret")), "IS_BLOCK_CURSOR"),
      editorApp(IdeBundle.message("label.appearance.show.right.margin"), "IS_RIGHT_MARGIN_SHOWN"),
      editorCode(IdeBundle.message("label.option.appearance", OptionsTopHitProvider.messageApp("checkbox.show.method.separators")), "SHOW_METHOD_SEPARATORS"),
      editorApp(IdeBundle.message("label.option.appearance", OptionsTopHitProvider.messageApp("checkbox.show.whitespaces")), "IS_WHITESPACES_SHOWN"),
      editorApp(IdeBundle.message("label.appearance.show.leading.whitespaces"), "IS_LEADING_WHITESPACES_SHOWN"),
      editorApp(IdeBundle.message("label.appearance.show.inner.whitespaces"), "IS_INNER_WHITESPACES_SHOWN"),
      editorApp(IdeBundle.message("label.appearance.show.trailing.whitespaces"), "IS_TRAILING_WHITESPACES_SHOWN"),
      editorApp(IdeBundle.message("label.appearance.show.vertical.indent.guides"), "IS_INDENT_GUIDES_SHOWN"),
      editorTabs(OptionsTopHitProvider.messageApp("group.tab.closing.policy") + ": " +
                 OptionsTopHitProvider.messageApp("radio.close.non.modified.files.first"), "closeNonModifiedFilesFirst")
    );
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  static BooleanOptionDescription editor(@Label String option, @NonNls String field) {
    return option(option, field, "preferences.editor");
  }

  static BooleanOptionDescription editorTabs(@Label String option, @NonNls String field) {
    return AppearanceOptionsTopHitProvider.option(option, field, "editor.preferences.tabs");
  }

  static BooleanOptionDescription option(@Label String option, @NonNls String field, @NonNls String configurableId) {
    return new EditorOptionDescription(field, option, configurableId);
  }

  static BooleanOptionDescription editorApp(@Label String option, @NonNls String field) {
    return option(option, field, "editor.preferences.appearance");
  }

  static BooleanOptionDescription editorCode(@Label String option, @NonNls String field) {
    return new DaemonCodeAnalyzerOptionDescription(field, option, "editor.preferences.appearance");
  }

  static final class Ex implements OptionsTopHitProvider.CoveredByToggleActions, ApplicationLevelProvider {
    @Override
    public @NotNull Collection<OptionDescription> getOptions() {
      return List.of(
        editorApp(IdeBundle.message("label.option.appearance", OptionsTopHitProvider.messageApp("checkbox.show.line.numbers")), "ARE_LINE_NUMBERS_SHOWN"),
        editorApp(IdeBundle.message("label.option.appearance", OptionsTopHitProvider.messageApp("checkbox.show.gutter.icons")), "ARE_GUTTER_ICONS_SHOWN")
      );
    }

    @Override
    public @NotNull String getId() {
      return ID;
    }
  }
}
