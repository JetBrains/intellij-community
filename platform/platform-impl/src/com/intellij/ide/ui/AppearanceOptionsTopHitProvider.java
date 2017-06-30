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
    appearance("UI: " + messageIde("checkboox.cyclic.scrolling.in.lists"), "cycleScrolling"),
    appearance("UI: " + messageIde("checkbox.show.icons.in.quick.navigation"), "showIconInQuickNavigation"),
    appearance("UI: " + messageIde("checkbox.position.cursor.on.default.button"), "moveMouseOnDefaultButton"),
    appearance("UI: Hide navigation popups on focus loss", "hideNavigationOnFocusLoss"),
    appearance("UI: Drag-n-Drop with ALT pressed only", "dndWithPressedAltOnly"),
    notifications("UI: Display balloon notifications", "SHOW_BALLOONS"),
    appearance("Window: " + messageIde("checkbox.animate.windows"), "animateWindows"),
    appearance("Window: " + messageIde("checkbox.show.memory.indicator"), "showMemoryIndicator"),
    appearance("Window: " + messageKeyMap("disable.mnemonic.in.menu.check.box"), "disableMnemonics"),
    appearance("Window: " + messageKeyMap("disable.mnemonic.in.controls.check.box"), "disableMnemonicsInControls"),
    appearance("Window: " + messageIde("checkbox.show.icons.in.menu.items"), "showIconsInMenus"),
    appearance("Window: " + messageIde("checkbox.left.toolwindow.layout"), "leftHorizontalSplit"),
    appearance("Window: " + messageIde("checkbox.show.editor.preview.popup"), "showEditorToolTip"),
    appearance("Window: " + messageIde("checkbox.show.tool.window.numbers"), "showToolWindowsNumbers"),
    appearance("Window: Allow merging buttons on dialogs", "allowMergeButtons"),
    appearance("Window: Small labels in editor tabs", "useSmallLabelsOnTabs"),
    appearance("Window: " + messageIde("checkbox.widescreen.tool.window.layout"), "wideScreenSupport"),
    appearance("Window: " + messageIde("checkbox.right.toolwindow.layout"), "rightHorizontalSplit"),
    appearance("Window: " + messageIde("checkbox.use.preview.window"), "navigateToPreview"));

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

  static BooleanOptionDescription option(String option, String field, String configurableId) {
    return new PublicMethodBasedOptionDescription(option, configurableId, "get" + StringUtil.capitalize(field), "set" + StringUtil.capitalize(field)) {
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
      appearance("Window: " + "Hide Tool Window Bars", "hideToolStripes"),
      appearance("View: Show Main Toolbar", "showMainToolbar"),
      appearance("View: Show Status Bar", "showStatusBar"),
      appearance("View: Show Navigation Bar", "showNavigationBar")
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
