/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.filters;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public class InvalidExpressionException extends IllegalArgumentException {
  public InvalidExpressionException(final String s) {
    super(s);
  }
}
