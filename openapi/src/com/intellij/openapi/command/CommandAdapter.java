/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.command;

public abstract class CommandAdapter implements CommandListener{
  public void commandStarted(CommandEvent event) {
  }

  public void beforeCommandFinished(CommandEvent event) {
  }

  public void commandFinished(CommandEvent event) {
  }

  public void undoTransparentActionStarted() {
  }

  public void undoTransparentActionFinished() {
  }
}