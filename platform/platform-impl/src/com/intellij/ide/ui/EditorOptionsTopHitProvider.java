// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Konstantin Bulenkov
 */
public class EditorOptionsTopHitProvider extends OptionsTopHitProvider {
  public static final String ID = "editor";

  private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
    editorUI("Appearance: " + messageIde("checkbox.use.antialiased.font.in.editor"), "ANTIALIASING_IN_EDITOR"),
    editorUI("Appearance: " + messageIde("checkbox.use.lcd.rendered.font.in.editor"), "USE_LCD_RENDERING_IN_EDITOR"),
    editorApp("Appearance: Caret blinking", "IS_CARET_BLINKING"),
    editorApp("Appearance: " + messageApp("checkbox.use.block.caret"), "IS_BLOCK_CURSOR"),
    editorApp("Appearance: Show right margin", "IS_RIGHT_MARGIN_SHOWN"),
    editorCode("Appearance: " + messageApp("checkbox.show.method.separators"), "SHOW_METHOD_SEPARATORS"),
    editorApp("Appearance: " + messageApp("checkbox.show.whitespaces"), "IS_WHITESPACES_SHOWN"),
    editorApp("Appearance: Show leading whitespaces", "IS_LEADING_WHITESPACES_SHOWN"),
    editorApp("Appearance: Show inner whitespaces", "IS_INNER_WHITESPACES_SHOWN"),
    editorApp("Appearance: Show trailing whitespaces", "IS_TRAILING_WHITESPACES_SHOWN"),
    editorApp("Appearance: Show vertical indent guides", "IS_INDENT_GUIDES_SHOWN"),
    editorCode("Appearance: " + messageApp("checkbox.show.small.icons.in.gutter"), "SHOW_SMALL_ICONS_IN_GUTTER"),
    editorTabs(messageApp("group.tab.closing.policy") + ": " +
               messageApp("radio.close.non.modified.files.first"), "CLOSE_NON_MODIFIED_FILES_FIRST")
  );

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@Nullable Project project) {
    return ourOptions;
  }

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

  static BooleanOptionDescription editorUI(String option, String field) {
    return AppearanceOptionsTopHitProvider.option(option, field, "editor.preferences.appearance");
  }

  static BooleanOptionDescription editorCode(String option, String field) {
    return new DaemonCodeAnalyzerOptionDescription(field, option, "editor.preferences.appearance");
  }

  public static class Ex extends OptionsTopHitProvider implements CoveredByToggleActions {
    private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
      editorApp("Appearance: " + messageApp("checkbox.show.line.numbers"), "ARE_LINE_NUMBERS_SHOWN"),
      editorApp("Appearance: " + messageApp("checkbox.show.gutter.icons"), "ARE_GUTTER_ICONS_SHOWN")
    );

    @NotNull
    @Override
    public Collection<OptionDescription> getOptions(@Nullable Project project) {
      return ourOptions;
    }

    @Override
    public String getId() {
      return ID;
    }
  }
}
