/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.application;

/**
 * Provides access to the <code>Application</code>.
 */
public class ApplicationManager {
  protected static Application ourApplication = null;

  /**
   * Gets Application.
   *
   * @return <code>Application</code>
   */
  public static Application getApplication(){
    return ourApplication;
  }
}
