/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

/**
 * @author Alexander Kireyev
 */
public interface LogProvider {
  void error(String message);
  void error(String message, Throwable t);
  void error(Throwable t);

  void warn(String message);
  void warn(String message, Throwable t);
  void warn(Throwable t);
}
