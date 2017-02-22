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

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Sergey.Malenkov
 */
public class AppearanceOptionsTopHitProvider extends OptionsTopHitProvider {
  public static final String ID = "appearance";

  private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
    appearance2("UI: " + messageIde("checkboox.cyclic.scrolling.in.lists"), "cycleScrolling"),
    appearance("UI: " + messageIde("checkbox.show.icons.in.quick.navigation"), "SHOW_ICONS_IN_QUICK_NAVIGATION"),
    appearance("UI: " + messageIde("checkbox.position.cursor.on.default.button"), "MOVE_MOUSE_ON_DEFAULT_BUTTON"),
    appearance("UI: Hide navigation popups on focus loss", "HIDE_NAVIGATION_ON_FOCUS_LOSS"),
    appearance("UI: Drag-n-Drop with ALT pressed only", "DND_WITH_PRESSED_ALT_ONLY"),
    notifications("UI: Display balloon notifications", "SHOW_BALLOONS"),
    appearance2("Window: " + messageIde("checkbox.animate.windows"), "animateWindows"),
    appearance2("Window: " + messageIde("checkbox.show.memory.indicator"), "showMemoryIndicator"),
    appearance("Window: " + messageKeyMap("disable.mnemonic.in.menu.check.box"), "DISABLE_MNEMONICS"),
    appearance("Window: " + messageKeyMap("disable.mnemonic.in.controls.check.box"), "DISABLE_MNEMONICS_IN_CONTROLS"),
    appearance("Window: " + messageIde("checkbox.show.icons.in.menu.items"), "SHOW_ICONS_IN_MENUS"),
    appearance2("Window: " + messageIde("checkbox.left.toolwindow.layout"), "leftHorizontalSplit"),
    appearance2("Window: " + messageIde("checkbox.show.editor.preview.popup"), "showEditorToolTip"),
    appearance2("Window: " + messageIde("checkbox.show.tool.window.numbers"), "showToolWindowsNumbers"),
    appearance2("Window: Allow merging buttons on dialogs", "allowMergeButtons"),
    appearance("Window: Small labels in editor tabs", "USE_SMALL_LABELS_ON_TABS"),
    appearance2("Window: " + messageIde("checkbox.widescreen.tool.window.layout"), "wideScreenSupport"),
    appearance2("Window: " + messageIde("checkbox.right.toolwindow.layout"), "rightHorizontalSplit"),
    appearance("Window: " + messageIde("checkbox.use.preview.window"), "NAVIGATE_TO_PREVIEW"));

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@Nullable Project project) {
    return ourOptions;
  }

  @Override
  public String getId() {
    return ID;
  }

  static BooleanOptionDescription appearance(String option, String field) {
    return option(option, field, "preferences.lookFeel");
  }

  static BooleanOptionDescription appearance2(@NotNull String option, @NotNull String field) {
    return new PublicMethodBasedOptionDescription(option, "preferences.lookFeel", "get" + StringUtil.capitalize(field), "set" + StringUtil.capitalize(field)) {
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

  static BooleanOptionDescription option(String option, String field, String configurableId) {
    return new PublicFieldBasedOptionDescription(option, configurableId, field) {
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

  static BooleanOptionDescription notifications(String option, String field) {
    return new PublicFieldBasedOptionDescription(option, "reference.settings.ide.settings.notifications", field) {
      @Override
      public Object getInstance() {
        return NotificationsConfigurationImpl.getInstanceImpl();
      }
    };
  }

  public static class Ex extends OptionsTopHitProvider implements CoveredByToggleActions {
    private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
      appearance("Window: " + "Hide Tool Window Bars", "HIDE_TOOL_STRIPES"),
      appearance("View: Show Main Toolbar", "SHOW_MAIN_TOOLBAR"),
      appearance("View: Show Status Bar", "SHOW_STATUS_BAR"),
      appearance("View: Show Navigation Bar", "SHOW_NAVIGATION_BAR")
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
