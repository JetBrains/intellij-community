/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui;

public interface InputValidator {
  /**
   * Checks whether the <code>inputString</code> is valid. It is invoked each time
   * input changes.
   */
  boolean checkInput(String inputString);

  /**
   * This method is invoked just before message dialog is closed with OK code.
   * If <code>false</code> is returned then then the message dialog will not be closed.
   */
  boolean canClose(String inputString);
}
