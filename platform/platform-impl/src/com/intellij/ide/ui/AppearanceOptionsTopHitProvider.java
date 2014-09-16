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
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
public final class AppearanceOptionsTopHitProvider extends OptionsTopHitProvider {
  private static final Collection<BooleanOptionDescription> ourOptions = Collections.unmodifiableCollection(Arrays.asList(
    option("UI: " + messageIde("checkboox.cyclic.scrolling.in.lists"), "CYCLE_SCROLLING"),
    option("UI: " + messageIde("checkbox.show.icons.in.quick.navigation"), "SHOW_ICONS_IN_QUICK_NAVIGATION"),
    option("UI: " + messageIde("checkbox.position.cursor.on.default.button"), "MOVE_MOUSE_ON_DEFAULT_BUTTON"),
    option("UI: Hide navigation popups on focus loss", "HIDE_NAVIGATION_ON_FOCUS_LOSS"),
    option("UI: Drag-n-Drop with ALT pressed only", "DND_WITH_PRESSED_ALT_ONLY"),
    option("View: " + messageIde("checkbox.animate.windows"), "SHOW_MAIN_TOOLBAR"),
    option("View: " + messageIde("checkbox.animate.windows"), "SHOW_STATUS_BAR"),
    option("View: " + messageIde("checkbox.animate.windows"), "SHOW_NAVIGATION_BAR"),
    option("Window: " + messageIde("checkbox.animate.windows"), "ANIMATE_WINDOWS"),
    option("Window: " + messageIde("checkbox.show.memory.indicator"), "SHOW_MEMORY_INDICATOR"),
    option("Window: " + messageKeyMap("disable.mnemonic.in.menu.check.box"), "DISABLE_MNEMONICS"),
    option("Window: " + messageKeyMap("disable.mnemonic.in.controls.check.box"), "DISABLE_MNEMONICS_IN_CONTROLS"),
    option("Window: " + messageIde("checkbox.show.icons.in.menu.items"), "SHOW_ICONS_IN_MENUS"),
    option("Window: " + messageIde("checkbox.left.toolwindow.layout"), "LEFT_HORIZONTAL_SPLIT"),
    option("Window: " + messageIde("checkbox.show.editor.preview.popup"), "SHOW_EDITOR_TOOLTIP"),
    option("Window: " + messageIde("checkbox.show.tool.window.bars"), "HIDE_TOOL_STRIPES"),
    option("Window: " + messageIde("checkbox.show.tool.window.numbers"), "SHOW_TOOL_WINDOW_NUMBERS"),
    option("Window: Allow merging buttons on dialogs", "ALLOW_MERGE_BUTTONS"),
    option("Window: Small labels in editor tabs", "USE_SMALL_LABELS_ON_TABS"),
    option("Window: " + messageIde("checkbox.widescreen.tool.window.layout"), "WIDESCREEN_SUPPORT"),
    option("Window: " + messageIde("checkbox.right.toolwindow.layout"), "RIGHT_HORIZONTAL_SPLIT"),
    option("Window: " + messageIde("checkbox.use.preview.window"), "NAVIGATE_TO_PREVIEW")));

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(Project project) {
    return ourOptions;
  }

  @Override
  public String getId() {
    return "appearance";
  }

  static BooleanOptionDescription option(String option, String field) {
    return new PublicFieldBasedOptionDescription(option, "preferences.lookFeel", field) {
      @Override
      public Object getInstance() {
        return UISettings.getInstance();
      }

      @Override
      protected void fireUpdated() {
        UISettings.getInstance().fireUISettingsChanged();
      }
    };
  }
}
