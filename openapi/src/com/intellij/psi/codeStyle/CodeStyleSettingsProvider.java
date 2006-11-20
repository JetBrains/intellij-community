/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.codeStyle;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CodeStyleSettingsProvider {
  public abstract CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings);

  @NotNull
  public abstract Configurable createSettingsPage(CodeStyleSettings settings);
}
