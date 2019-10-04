// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.ide.ui.OptionsTopHitProvider.messageApp;

/**
 * @author Konstantin Bulenkov
 */
public final class EditorOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  public static final String ID = "editor";

  private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
    editorApp("Appearance: Caret blinking", "IS_CARET_BLINKING"),
    editorApp("Appearance: " + messageApp("checkbox.use.block.caret"), "IS_BLOCK_CURSOR"),
    editorApp("Appearance: Show right margin", "IS_RIGHT_MARGIN_SHOWN"),
    editorCode("Appearance: " + messageApp("checkbox.show.method.separators"), "SHOW_METHOD_SEPARATORS"),
    editorApp("Appearance: " + messageApp("checkbox.show.whitespaces"), "IS_WHITESPACES_SHOWN"),
    editorApp("Appearance: Show leading whitespaces", "IS_LEADING_WHITESPACES_SHOWN"),
    editorApp("Appearance: Show inner whitespaces", "IS_INNER_WHITESPACES_SHOWN"),
    editorApp("Appearance: Show trailing whitespaces", "IS_TRAILING_WHITESPACES_SHOWN"),
    editorApp("Appearance: Show vertical indent guides", "IS_INDENT_GUIDES_SHOWN"),
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

  static BooleanOptionDescription editor(String option, String field) {
    return option(option, field, "preferences.editor");
  }

  static BooleanOptionDescription editorTabs(String option, String field) {
    return AppearanceOptionsTopHitProvider.option(option, field, "editor.preferences.tabs");
  }

  static BooleanOptionDescription option(String option, String field, String configurableId) {
    return new EditorOptionDescription(field, option, configurableId);
  }

  static BooleanOptionDescription editorApp(String option, String field) {
    return option(option, field, "editor.preferences.appearance");
  }

  static BooleanOptionDescription editorCode(String option, String field) {
    return new DaemonCodeAnalyzerOptionDescription(field, option, "editor.preferences.appearance");
  }

  public static class Ex implements OptionsTopHitProvider.CoveredByToggleActions, ApplicationLevelProvider {
    private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
      editorApp("Appearance: " + messageApp("checkbox.show.line.numbers"), "ARE_LINE_NUMBERS_SHOWN"),
      editorApp("Appearance: " + messageApp("checkbox.show.gutter.icons"), "ARE_GUTTER_ICONS_SHOWN")
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
