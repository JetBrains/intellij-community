// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.EarlyAccessRegistryManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Temporary utility class for migration to the new UI.
 * Do not use this class for plugin development.
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public abstract class ExperimentalUI {
  private final AtomicBoolean isIconPatcherSet = new AtomicBoolean();
  private final IconPathPatcher iconPathPatcher = createPathPatcher();
  private static final String KEY = "ide.experimental.ui";

  public static boolean isNewUI() {
    return EarlyAccessRegistryManager.INSTANCE.getBoolean(KEY);
  }

  public static ExperimentalUI getInstance() {
    return ApplicationManager.getApplication().getService(ExperimentalUI.class);
  }

  @SuppressWarnings("unused")
  private final class NewUiRegistryListener implements RegistryValueListener {
    @Override
    public void afterValueChanged(@NotNull RegistryValue value) {
      if (!value.getKey().equals(KEY)) {
        return;
      }

      boolean isEnabled = value.asBoolean();

      patchUIDefaults(isEnabled);
      if (isEnabled) {
        int tabPlacement = UISettings.getInstance().getEditorTabPlacement();
        if (tabPlacement == SwingConstants.LEFT
            || tabPlacement == SwingConstants.RIGHT
            || tabPlacement == SwingConstants.BOTTOM) {
          UISettings.getInstance().setEditorTabPlacement(SwingConstants.TOP);
        }

        if (isIconPatcherSet.compareAndSet(false, true)) {
          IconLoader.installPathPatcher(iconPathPatcher);
        }
      }
      else if (isIconPatcherSet.compareAndSet(true, false)) {
        IconLoader.removePathPatcher(iconPathPatcher);
      }
    }
  }

  public void lookAndFeelChanged() {
    if (isNewUI()) {
      if (isIconPatcherSet.compareAndSet(false, true)) {
        IconLoader.installPathPatcher(iconPathPatcher);
      }
      patchUIDefaults(true);
    }
  }

  private IconPathPatcher createPathPatcher() {
    Map<String, String> paths = loadIconMappings();
    return new IconPathPatcher() {
      @Override
      public @Nullable String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
        return paths.get(Strings.trimStart(path, "/"));
      }

      @Override
      public @Nullable ClassLoader getContextClassLoader(@NotNull String path, @Nullable ClassLoader originalClassLoader) {
        return getClass().getClassLoader();
      }
    };
  }

  public abstract Map<String, String> loadIconMappings();

  private static void patchUIDefaults(boolean isNewUiEnabled) {
    if (!isNewUiEnabled) {
      return;
    }

    UIDefaults defaults = UIManager.getDefaults();
    setUIProperty("EditorTabs.underlineArc", 4, defaults);
    setUIProperty("ToolWindow.Button.selectedBackground", new ColorUIResource(0x3573f0), defaults);
    setUIProperty("ToolWindow.Button.selectedForeground", new ColorUIResource(0xffffff), defaults);

    if (defaults.getColor("EditorTabs.hoverInactiveBackground") == null) {
      // avoid getting EditorColorsManager too early
      setUIProperty("EditorTabs.hoverInactiveBackground", (UIDefaults.LazyValue)__ -> {
        EditorColorsScheme editorColorScheme = EditorColorsManager.getInstance().getGlobalScheme();
        return ColorUtil.mix(JBColor.PanelBackground, editorColorScheme.getDefaultBackground(), 0.5);
      }, defaults);
    }

    if (SystemInfo.isJetBrainsJvm && EarlyAccessRegistryManager.INSTANCE.getBoolean("ide.experimental.ui.inter.font")) {
      installInterFont(defaults);
    }
  }

  private static void setUIProperty(String key, Object value, UIDefaults defaults) {
    defaults.remove(key);
    defaults.put(key, value);
  }

  private static void installInterFont(UIDefaults defaults) {
    if (UISettings.getInstance().getOverrideLafFonts()) {
      //todo[kb] add RunOnce
      UISettings.getInstance().setOverrideLafFonts(false);
    }
    //List<String> keysToPatch = List.of("CheckBoxMenuItem.acceleratorFont",
    //                                   "CheckBoxMenuItem.font",
    //                                   "Menu.acceleratorFont",
    //                                   "Menu.font",
    //                                   //"MenuBar.font",
    //                                   "MenuItem.acceleratorFont",
    //                                   "MenuItem.font",
    //                                   "PopupMenu.font",
    //                                   "RadioButtonMenuItem.acceleratorFont",
    //                                   "RadioButtonMenuItem.font");
    //for (String key : keysToPatch) {
    //  Font font = defaults.getFont(key);
    //  defaults.put(key, new FontUIResource("Inter", font.getStyle(), font.getSize()));
    //}

    //if (JBColor.isBright()) {
    //  Color menuBg = new ColorUIResource(0x242933);
    //  Color menuFg = new ColorUIResource(0xFFFFFF);
    //  setUIProperty("PopupMenu.background", menuBg, defaults);
    //  setUIProperty("MenuItem.background", menuBg, defaults);
    //  setUIProperty("MenuItem.foreground", menuFg, defaults);
    //  setUIProperty("Menu.background", menuBg, defaults);
    //  setUIProperty("Menu.foreground", menuFg, defaults);
    //  setUIProperty("CheckBoxMenuItem.acceleratorForeground", menuFg, defaults);
    //  setUIProperty("Menu.acceleratorForeground", menuFg, defaults);
    //  setUIProperty("MenuItem.acceleratorForeground", menuFg, defaults);
    //  setUIProperty("RadioButtonMenuItem.acceleratorForeground", menuFg, defaults);
    //}
  }
}
