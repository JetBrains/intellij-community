/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.command;

import java.util.EventListener;

public interface CommandListener extends EventListener{
  void commandStarted(CommandEvent event);
  void beforeCommandFinished(CommandEvent event);
  void commandFinished(CommandEvent event);
  void undoTransparentActionStarted();
  void undoTransparentActionFinished();
}