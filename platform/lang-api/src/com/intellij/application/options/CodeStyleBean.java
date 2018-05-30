// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.formatting.WrapType;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Wraps language code style options defined in {@code CommonCodeStyleSettings} and {@code CustomCodeStyleSettings} with getter/setter
 * methods for external serialization.
 *
 * @see CommonCodeStyleSettings
 * @see CustomCodeStyleSettings
 */
@SuppressWarnings("unused")
public abstract class CodeStyleBean implements Serializable {

  private @NotNull CodeStyleSettings myRootSettings;

  public CodeStyleBean() {
    myRootSettings = new CodeStyleSettings();
  }

  public void setRootSettings(@NotNull CodeStyleSettings settings) {
    myRootSettings = settings;
  }

  @NotNull
  protected abstract Language getLanguage();

  public int getRightMargin() {
    return myRootSettings.getRightMargin(getLanguage());
  }

  public void setRightMargin(int rightMargin) {
    myRootSettings.setRightMargin(getLanguage(), rightMargin);
  }

  @NotNull
  public CommonCodeStyleSettings.WrapOnTyping getWrapOnTyping() {
    for (CommonCodeStyleSettings.WrapOnTyping c : CommonCodeStyleSettings.WrapOnTyping.values()) {
      if (c.intValue == getCommonSettings().WRAP_ON_TYPING) return c;
    }
    return CommonCodeStyleSettings.WrapOnTyping.NO_WRAP;
  }

  public void setWrapOnTyping(@NotNull CommonCodeStyleSettings.WrapOnTyping value) {
    getCommonSettings().WRAP_ON_TYPING = value.intValue;
  }

  @NotNull
  protected final CommonCodeStyleSettings getCommonSettings() {
    return myRootSettings.getCommonSettings(getLanguage());
  }

  @NotNull
  protected final <T extends CustomCodeStyleSettings> T getCustomSettings(@NotNull Class<T> settingsClass) {
    return myRootSettings.getCustomSettings(settingsClass);
  }

  protected static WrapType intToWrapType(int wrap) {
    return WrapType.byLegacyRepresentation(wrap);
  }

  protected static int wrapTypeToInt(@NotNull WrapType wrapType) {
    return wrapType.getLegacyRepresentation();
  }
}
