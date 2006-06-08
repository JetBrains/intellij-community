/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;

/**
 * @author peter
 */
public class CommittableUtil {
  private static int myCount;
  private static final Application ourApplication = ApplicationManager.getApplication();

  public static void commit(Runnable runnable) {
    ourApplication.assertIsDispatchThread();
    myCount++;
    try {
      runnable.run();
    } finally {
      myCount--;
    }
  }

  public static boolean isCommitting() {
    ourApplication.assertIsDispatchThread();
    return myCount > 0;
  }

}
