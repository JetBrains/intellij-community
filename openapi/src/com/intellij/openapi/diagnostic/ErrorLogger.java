/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diagnostic;

/**
 * @author kir
 */
public interface ErrorLogger {

  boolean canHandle(IdeaLoggingEvent event);

  /** Error event handling occures in a separate thread. */
  void handle(IdeaLoggingEvent event);
}
