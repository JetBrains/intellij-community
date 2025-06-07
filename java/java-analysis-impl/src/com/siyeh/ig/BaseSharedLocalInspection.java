// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig;

import com.intellij.codeInspection.GlobalInspectionTool;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public abstract class BaseSharedLocalInspection<T extends GlobalInspectionTool> extends BaseInspection {

  protected final T mySettingsDelegate;

  public BaseSharedLocalInspection(T settingsDelegate) {
    mySettingsDelegate = settingsDelegate;
  }

  @Override
  public final @NotNull String getShortName() {
    return mySettingsDelegate.getShortName();
  }
}
