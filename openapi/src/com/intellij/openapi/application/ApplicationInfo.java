/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

import java.util.Calendar;

public abstract class ApplicationInfo {
  public abstract Calendar getBuildDate();
  public abstract String getBuildNumber();
  public abstract String getMajorVersion();
  public abstract String getMinorVersion();
  public abstract String getVersionName();

  public static ApplicationInfo getInstance() {
    return ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
  }
}
