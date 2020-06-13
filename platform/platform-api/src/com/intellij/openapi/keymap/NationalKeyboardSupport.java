// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.im.InputContext;
import java.util.Locale;


@State(name = "KeyboardSettings", storages = @Storage("keyboard.xml"))
public class NationalKeyboardSupport implements PersistentStateComponent<NationalKeyboardSupport.OptionSet> {
  private static final String[] MAC_SUPPORTED_LOCALES = {"de", "fr", "it", "no"};
  private static final String[] WIN_SUPPORTED_LOCALES = {"be", "ru", "uk", "bg", "sr"};

  public static final String VMOption = getVMOption();

  public static boolean isSupportedKeyboardLayout(@NotNull Component component) {
    if (SystemInfo.isLinux) {
      return true;
    }

    String[] supportedNonEnglishLanguages;
    if (SystemInfo.isMac) {
      supportedNonEnglishLanguages = MAC_SUPPORTED_LOCALES;
    }
    else if (SystemInfo.isWindows) {
      supportedNonEnglishLanguages = WIN_SUPPORTED_LOCALES;
    }
    else {
      return false;
    }

    String keyboardLayoutLanguage = getLanguageForComponent(component);
    for (String language : supportedNonEnglishLanguages) {
      if (language.equals(keyboardLayoutLanguage)) {
        return true;
      }
    }
    return false;
  }

  public static @NotNull String getVMOption() {
    if (SystemInfo.isMac || SystemInfo.isLinux) {
      return "com.sun.awt.use.national.layouts";
    }
    return "com.sun.awt.useLatinNonAlphaNumKeycodes";
  }

  public static @NotNull String getKeymapBundleKey() {
    if (SystemInfo.isMac || SystemInfo.isLinux) {
      return "use.national.layouts.for.shortcuts";
    }
    return "use.us.non.alpha.num.keys";
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

  public static final class OptionSet {
    public boolean enabled = "true".equals(System.getProperty(VMOption));
  }

  private OptionSet myOptions = new OptionSet();

  public static NationalKeyboardSupport getInstance() {
    if (ApplicationManager.getApplication().isDisposed()) {
      return new NationalKeyboardSupport();
    }
    else {
      return ServiceManager.getService(NationalKeyboardSupport.class);
    }
  }

  @Nullable
  @Override
  public OptionSet getState() {
    return myOptions;
  }

  @Override
  public void loadState(@NotNull OptionSet state) {
    state.enabled
      = state.enabled || "true".equals(System.getProperty(VMOption));

    myOptions = state;
  }

  @Override
  public void noStateLoaded() {
    // on MacOS national keymap support is turned on by default
    if (SystemInfo.isMac) {
      myOptions.enabled = "true".equals(System.getProperty(VMOption, "true"));
    }
  }

  public boolean getEnabled() {
    return myOptions.enabled;
  }

  public void setEnabled(boolean enabled) {
    myOptions.enabled = enabled;
  }
}


