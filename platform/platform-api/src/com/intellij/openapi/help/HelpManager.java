// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.help;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class HelpManager {
  public static HelpManager getInstance() {
    return ApplicationManager.getApplication().getService(HelpManager.class);
  }

  public abstract void invokeHelp(@Nullable @NonNls String id);
}
