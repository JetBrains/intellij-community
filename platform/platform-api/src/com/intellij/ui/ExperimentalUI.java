// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.IconPathPatcher;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Temporary utility class for migration to the new UI.
 * This is not public API. For plugin development use {@link NewUI#isEnabled()}
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public abstract class ExperimentalUI {
  public static final String KEY = "ide.experimental.ui";

  public static final String NEW_UI_USED_PROPERTY = "experimental.ui.used.once";
  public static final String NEW_UI_FIRST_SWITCH = "experimental.ui.first.switch";
  public static final String NEW_UI_PROMO_BANNER_DISABLED_PROPERTY = "experimental.ui.promo.banner.disabled";

  private static final String FIRST_PROMOTION_DATE_PROPERTY = "experimental.ui.first.promotion.localdate";

  private final AtomicBoolean isIconPatcherSet = new AtomicBoolean();
  private IconPathPatcher iconPathPatcher;

  @Contract(pure = true)
  public static boolean isNewUI() {
    // The content of this method is duplicated to EmptyIntentionAction.isNewUi (because of modules dependency problem).
    // Please apply any modifications here and there synchronously. Or solve the dependency problem :)
    return EarlyAccessRegistryManager.INSTANCE.getBoolean(KEY);
  }

  public static void setNewUI(boolean newUI) {
    getInstance().setNewUIInternal(newUI, true);
  }

  public void setNewUIInternal(boolean newUI, boolean suggestRestart) {

  }

  public static int getPromotionDaysCount() {
    PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
    String value = propertyComponent.getValue(FIRST_PROMOTION_DATE_PROPERTY);
    LocalDate now = LocalDate.now();

    if (value == null) {
      propertyComponent.setValue(FIRST_PROMOTION_DATE_PROPERTY, now.toString());
      return 0;
    }

    try {
      LocalDate firstDate = LocalDate.parse(value);
      return (int)DAYS.between(firstDate, now);
    }
    catch (DateTimeParseException e) {
      Logger.getInstance(ExperimentalUI.class).warn("Invalid stored date " + value);
      propertyComponent.setValue(FIRST_PROMOTION_DATE_PROPERTY, now.toString());
      return 0;
    }
  }

  public static boolean isNewNavbar() {
    return isNewUI() && Registry.is("ide.experimental.ui.navbar.scroll");
  }

  public static boolean isEditorTabsWithScrollBar() {
    return isNewUI() && Registry.is("ide.experimental.ui.editor.tabs.scrollbar");
  }

  public static ExperimentalUI getInstance() {
    return ApplicationManager.getApplication().getService(ExperimentalUI.class);
  }

  @SuppressWarnings("unused")
  public static class NewUiRegistryListener implements RegistryValueListener {
    protected boolean isApplicable() {
      return !PlatformUtils.isJetBrainsClient(); // JetBrains Client has custom listener
    }

    @Override
    public void afterValueChanged(@NotNull RegistryValue value) {
      if (!isApplicable() || !value.getKey().equals(KEY)) {
        return;
      }

      boolean isEnabled = value.asBoolean();

      patchUIDefaults(isEnabled);
      ExperimentalUI instance = getInstance();
      if (isEnabled) {
        if (instance.isIconPatcherSet.compareAndSet(false, true)) {
          if (instance.iconPathPatcher != null) {
            IconLoader.removePathPatcher(instance.iconPathPatcher);
          }
          instance.iconPathPatcher = instance.createPathPatcher();
          IconLoader.installPathPatcher(instance.iconPathPatcher);
        }
        instance.onExpUIEnabled(true);
      }
      else if (instance.isIconPatcherSet.compareAndSet(true, false)) {
        IconLoader.removePathPatcher(instance.iconPathPatcher);
        instance.iconPathPatcher = null;
        instance.onExpUIDisabled(true);
      }
    }
  }

  public void lookAndFeelChanged() {
    if (isNewUI()) {
      if (isIconPatcherSet.compareAndSet(false, true)) {
        if (iconPathPatcher != null) {
          IconLoader.removePathPatcher(iconPathPatcher);
        }
        iconPathPatcher = createPathPatcher();
        IconLoader.installPathPatcher(iconPathPatcher);
      }
      patchUIDefaults(true);
    }
  }

  private @NotNull IconPathPatcher createPathPatcher() {
    Map<ClassLoader, Map<String, String>> paths = getIconMappings();
    return new IconPathPatcher() {
      @Override
      public @Nullable String patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
        Map<String, String> mappings = paths.get(classLoader);
        return mappings != null ? mappings.get(Strings.trimStart(path, "/")) : null;
      }

      @Override
      public @Nullable ClassLoader getContextClassLoader(@NotNull String path, @Nullable ClassLoader originalClassLoader) {
        return originalClassLoader;
      }
    };
  }

  public abstract @NotNull Map<ClassLoader, Map<String, String>> getIconMappings();

  public abstract void onExpUIEnabled(boolean suggestRestart);

  public abstract void onExpUIDisabled(boolean suggestRestart);

  private static void patchUIDefaults(boolean isNewUiEnabled) {
    if (!isNewUiEnabled) {
      return;
    }

    UIDefaults defaults = UIManager.getDefaults();
    if (defaults.getColor("EditorTabs.hoverInactiveBackground") == null) {
      // avoid getting EditorColorsManager too early
      setUIProperty("EditorTabs.hoverInactiveBackground", (UIDefaults.LazyValue)__ -> {
        EditorColorsScheme editorColorScheme = EditorColorsManager.getInstance().getGlobalScheme();
        return ColorUtil.mix(JBColor.PanelBackground, editorColorScheme.getDefaultBackground(), 0.5);
      }, defaults);
    }

    if (SystemInfo.isJetBrainsJvm && EarlyAccessRegistryManager.INSTANCE.getBoolean("ide.experimental.ui.inter.font")) {
      installInterFont();
    }
  }

  private static void setUIProperty(String key, Object value, UIDefaults defaults) {
    defaults.remove(key);
    defaults.put(key, value);
  }

  private static void installInterFont() {
    if (UISettings.getInstance().getOverrideLafFonts()) {
      //todo[kb] add RunOnce
      UISettings.getInstance().setOverrideLafFonts(false);
    }
  }
}
