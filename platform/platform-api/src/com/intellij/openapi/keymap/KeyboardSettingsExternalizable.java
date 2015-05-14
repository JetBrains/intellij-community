/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.keymap;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.SystemInfo;
import com.sun.istack.internal.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Denis Fokin
 */

@State(
  name = "KeyboardSettings",
  storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/keyboard.xml")}
)
public class KeyboardSettingsExternalizable implements PersistentStateComponent<KeyboardSettingsExternalizable.OptionSet> {

  private static final String [] supportedNonEnglishLanguages = {"de", "fr", "it"};

  public static boolean isSupportedKeyboardLayout(@NotNull Component component) {
    if (SystemInfo.isMac) return false;
    String keyboardLayoutLanguage = getLanguageForComponent(component);
    for (String language : supportedNonEnglishLanguages) {
      if (language.equals(keyboardLayoutLanguage)) {
        return true;
      }
    }
    return false;
  }

  public static String getLanguageForComponent(@NotNull Component component) {
    return component.getInputContext().getLocale().getLanguage();
  }

  public static String getDisplayLanguageNameForComponent(@NotNull Component component) {
    return component.getInputContext().getLocale().getDisplayLanguage();
  }

  public static final class OptionSet {
    public boolean USE_NON_ENGLISH_KEYBOARD = false;
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
  public void loadState(OptionSet state) {
    myOptions = state;
  }

  public boolean isNonEnglishKeyboardSupportEnabled () {
    return myOptions.USE_NON_ENGLISH_KEYBOARD;
  }

  public void setNonEnglishKeyboardSupportEnabled (boolean enabled) {
    myOptions.USE_NON_ENGLISH_KEYBOARD = enabled;
  }

}
