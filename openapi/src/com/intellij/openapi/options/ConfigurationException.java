/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

public class ConfigurationException extends Exception {
  public static final String DEFAULT_TITLE = "Cannot Save Settings";
  private String myTitle = DEFAULT_TITLE;
  private Runnable myQuickFix;

  public ConfigurationException(String message) {
    super(message);
  }

  public ConfigurationException(String message, String title) {
    super(message);
    myTitle = title;
  }

  public String getTitle() {
    return myTitle;
  }

  public void setQuickFix(Runnable quickFix) {
    myQuickFix = quickFix;
  }

  public Runnable getQuickFix() {
    return myQuickFix;
  }

}