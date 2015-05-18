/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Konstantin Bulenkov
 */
public class EditorOptionsTopHitProvider extends OptionsTopHitProvider {
  private static final String ID = "editor";
  
  private static final Collection<BooleanOptionDescription> ourOptions = ContainerUtil.immutableList(
    editor("Mouse: " + messageApp("checkbox.honor.camelhumps.words.settings.on.double.click"),
           "IS_MOUSE_CLICK_SELECTION_HONORS_CAMEL_WORDS"),
    editor("Mouse: " + messageApp(SystemInfo.isMac
                                  ? "checkbox.enable.ctrl.mousewheel.changes.font.size.macos"
                                  : "checkbox.enable.ctrl.mousewheel.changes.font.size"), "IS_WHEEL_FONTCHANGE_ENABLED"),
    editor("Mouse: " + messageApp("checkbox.enable.drag.n.drop.functionality.in.editor"), "IS_DND_ENABLED"),
    new EditorOptionDescription(null, messageApp("checkbox.show.softwraps.only.for.caret.line.action.text"), "preferences.editor") {
      @Override
      public boolean isOptionEnabled() {
        return !EditorSettingsExternalizable.getInstance().isAllSoftWrapsShown();
      }

      @Override
      public void setOptionState(boolean enabled) {
        EditorSettingsExternalizable.getInstance().setAllSoftwrapsShown(!enabled);
        fireUpdated();
      }
    },
    editor("Virtual Space: " + messageApp("checkbox.allow.placement.of.caret.after.end.of.line"), "IS_VIRTUAL_SPACE"),
    editor("Virtual Space: " + messageApp("checkbox.allow.placement.of.caret.inside.tabs"), "IS_CARET_INSIDE_TABS"),
    editor("Virtual Space: " + messageApp("checkbox.show.virtual.space.at.file.bottom"), "ADDITIONAL_PAGE_AT_BOTTOM"),
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
    option("Appearance: " + messageApp("checkbox.show.code.folding.outline"), "IS_FOLDING_OUTLINE_SHOWN", "editor.preferences.folding"),
    editorTabs("Tabs: " + messageApp("checkbox.editor.tabs.in.single.row"), "SCROLL_TAB_LAYOUT_IN_EDITOR"),
    editorTabs("Tabs: " + messageApp("checkbox.hide.file.extension.in.editor.tabs"), "HIDE_KNOWN_EXTENSION_IN_TABS"),
    editorTabs("Tabs: Show directory in editor tabs for non-unique filenames", "SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES"),
    editorTabs("Tabs: " + messageApp("checkbox.editor.tabs.show.close.button"), "SHOW_CLOSE_BUTTON"),
    editorTabs("Tabs: " + messageApp("checkbox.mark.modified.tabs.with.asterisk"), "MARK_MODIFIED_TABS_WITH_ASTERISK"),
    editorTabs("Tabs: " + messageApp("checkbox.show.tabs.tooltips"), "SHOW_TABS_TOOLTIPS"),
    editorTabs("Tabs: " + messageApp("radio.close.non.modified.files.first"), "CLOSE_NON_MODIFIED_FILES_FIRST")
  );

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
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
    private static final Collection<BooleanOptionDescription> ourOptions = ContainerUtil.immutableList(
      editorApp("Appearance: " + messageApp("checkbox.show.line.numbers"), "ARE_LINE_NUMBERS_SHOWN")
    );

    @NotNull
    @Override
    public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
      return ourOptions;
    }

    @Override
    public String getId() {
      return ID;
    }
  }
}
