// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.im.InputContext;
import java.util.Locale;

/**
 * @author Denis Fokin
 */

@State(name = "KeyboardSettings", storages = @Storage("keyboard.xml"))
public class KeyboardSettingsExternalizable implements PersistentStateComponent<KeyboardSettingsExternalizable.OptionSet> {

  private static final String [] supportedNonEnglishLanguages = {"de", "fr", "it", "uk"};

  public static boolean isSupportedKeyboardLayout(@NotNull Component component) {
    String keyboardLayoutLanguage = getLanguageForComponent(component);
    for (String language : supportedNonEnglishLanguages) {
      if (language.equals(keyboardLayoutLanguage)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String getLanguageForComponent(@NotNull Component component) {
    final Locale locale = getLocaleForComponent(component);
    return locale == null ? null : locale.getLanguage();
  }

  @Nullable
  protected static Locale getLocaleForComponent(@NotNull Component component) {
    final InputContext context = component.getInputContext();
    return context == null ? null : context.getLocale();
  }

  @Nullable
  public static String getDisplayLanguageNameForComponent(@NotNull Component component) {
    final Locale locale = getLocaleForComponent(component);
    return locale == null ? null : locale.getDisplayLanguage();
  }

  public static final class OptionSet {
    public boolean PREFER_KEY_POSITION_OVER_CHAR_OPTION
      = "true".equals(System.getProperty("com.jetbrains.use.old.keyevent.processing"));
  }

  private OptionSet myOptions = new OptionSet();

  public static KeyboardSettingsExternalizable getInstance() {
    if (ApplicationManager.getApplication().isDisposed()) {
      return new KeyboardSettingsExternalizable();
    }
    else {
      return ServiceManager.getService(KeyboardSettingsExternalizable.class);
    }
  }

  @Nullable
  @Override
  public OptionSet getState() {
    return myOptions;
  }

  @Override
  public void loadState(@NotNull OptionSet state) {
    state.PREFER_KEY_POSITION_OVER_CHAR_OPTION
      = state.PREFER_KEY_POSITION_OVER_CHAR_OPTION
        || "true".equals(System.getProperty("com.jetbrains.use.old.keyevent.processing"));

    myOptions = state;
  }

  public boolean isPreferKeyPositionOverCharOption () {
    return myOptions.PREFER_KEY_POSITION_OVER_CHAR_OPTION;
  }

  public void setPreferKeyPositionOverCharOption (boolean enabled) {
    myOptions.PREFER_KEY_POSITION_OVER_CHAR_OPTION = enabled;
  }

}
