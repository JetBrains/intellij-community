/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.help;

import com.intellij.openapi.application.ApplicationManager;

public abstract class HelpManager {
  public static HelpManager getInstance() {
    return ApplicationManager.getApplication().getComponent(HelpManager.class);
  }

  public abstract void invokeHelp(String id);
}
