// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.ide.ui.OptionsTopHitProvider.messageIde;
import static com.intellij.ide.ui.OptionsTopHitProvider.messageKeyMap;

/**
 * @author Sergey.Malenkov
 */
public final class AppearanceOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  public static final String ID = "appearance";

  private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
    appearance("UI: " + messageIde("checkboox.cyclic.scrolling.in.lists"), "cycleScrolling"),
    appearance("UI: " + messageIde("checkbox.show.icons.in.quick.navigation"), "showIconInQuickNavigation"),
    appearance("UI: " + messageIde("checkbox.show.tree.indent.guides"), "showTreeIndentGuides"),
    appearance("UI: " + messageIde("checkbox.compact.tree.indents"), "compactTreeIndents"),
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
  public Collection<OptionDescription> getOptions() {
    return ourOptions;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  static BooleanOptionDescription appearance(String option, String propertyName) {
    return option(option, propertyName, "preferences.lookFeel");
  }

  public static BooleanOptionDescription option(String option, String propertyName, String configurableId) {
    return new PublicMethodBasedOptionDescription(option, configurableId, "get" + StringUtil.capitalize(propertyName), "set" + StringUtil.capitalize(propertyName)) {
      @NotNull
      @Override
      public Object getInstance() {
        return UISettings.getInstance().getState();
      }

      @Override
      protected void fireUpdated() {
        UISettings.getInstance().fireUISettingsChanged();
      }
    };
  }

  static BooleanOptionDescription notifications(String option, String field) {
    return new PublicFieldBasedOptionDescription(option, "reference.settings.ide.settings.notifications", field) {
      @NotNull
      @Override
      public Object getInstance() {
        return NotificationsConfigurationImpl.getInstanceImpl();
      }
    };
  }

  public static class Ex implements OptionsTopHitProvider.CoveredByToggleActions, ApplicationLevelProvider {
    private static final Collection<OptionDescription> ourOptions = ContainerUtil.immutableList(
      appearance("Window: " + "Hide Tool Window Bars", "hideToolStripes"),
      appearance("View: Show Main Toolbar", "showMainToolbar"),
      appearance("View: Show Status Bar", "showStatusBar"),
      appearance("View: Show Navigation Bar", "showNavigationBar")
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
