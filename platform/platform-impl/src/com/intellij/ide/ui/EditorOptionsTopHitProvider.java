// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.util.NlsContexts.Label;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.ide.ui.OptionsTopHitProvider.messageApp;

/**
 * @author Konstantin Bulenkov
 */
public final class EditorOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  public static final String ID = "editor";

  private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
    editorApp(IdeBundle.message("label.appearance.caret.blinking"), "IS_CARET_BLINKING"),
    editorApp(IdeBundle.message("label.option.appearance", messageApp("checkbox.use.block.caret")), "IS_BLOCK_CURSOR"),
    editorApp(IdeBundle.message("label.appearance.show.right.margin"), "IS_RIGHT_MARGIN_SHOWN"),
    editorCode(IdeBundle.message("label.option.appearance", messageApp("checkbox.show.method.separators")), "SHOW_METHOD_SEPARATORS"),
    editorApp(IdeBundle.message("label.option.appearance", messageApp("checkbox.show.whitespaces")), "IS_WHITESPACES_SHOWN"),
    editorApp(IdeBundle.message("label.appearance.show.leading.whitespaces"), "IS_LEADING_WHITESPACES_SHOWN"),
    editorApp(IdeBundle.message("label.appearance.show.inner.whitespaces"), "IS_INNER_WHITESPACES_SHOWN"),
    editorApp(IdeBundle.message("label.appearance.show.trailing.whitespaces"), "IS_TRAILING_WHITESPACES_SHOWN"),
    editorApp(IdeBundle.message("label.appearance.show.vertical.indent.guides"), "IS_INDENT_GUIDES_SHOWN"),
    editorTabs(messageApp("group.tab.closing.policy") + ": " +
               messageApp("radio.close.non.modified.files.first"), "closeNonModifiedFilesFirst")
  );

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions() {
    return ourOptions;
  }

  @NotNull
  @Override
  public String getId() {
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

  public static class Ex implements OptionsTopHitProvider.CoveredByToggleActions, ApplicationLevelProvider {
    private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
      editorApp(IdeBundle.message("label.option.appearance", messageApp("checkbox.show.line.numbers")), "ARE_LINE_NUMBERS_SHOWN"),
      editorApp(IdeBundle.message("label.option.appearance", messageApp("checkbox.show.gutter.icons")), "ARE_GUTTER_ICONS_SHOWN")
    );

    @NotNull
    @Override
    public Collection<OptionDescription> getOptions() {
      return ourOptions;
    }

    @NotNull
    @Override
    public String getId() {
      return ID;
    }
  }
}
