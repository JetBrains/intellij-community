/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

/**
 * Represents a separator.
 */
public final class Separator extends AnAction {
  private final static Separator ourInstance = new Separator();

  private Separator() {
  }

  public static Separator getInstance() {
    return ourInstance;
  }

  public void actionPerformed(AnActionEvent e){
    throw new UnsupportedOperationException();
  }
}
