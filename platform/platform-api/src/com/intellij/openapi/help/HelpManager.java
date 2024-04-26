// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.help;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class HelpManager {
  private static final Logger LOG = Logger.getInstance(HelpManager.class);

  public static HelpManager getInstance() {
    return ApplicationManager.getApplication().getService(HelpManager.class);
  }

  public abstract void invokeHelp(@Nullable @NonNls String id);

  protected static void logWillOpenHelpId(@Nullable String helpId) {
    if (helpId != null && LOG.isDebugEnabled()) {
      LOG.debug("Will open helpId: " + helpId);
    }
  }
}
