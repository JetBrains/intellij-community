/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.codeStyle;

public abstract class SuggestedNameInfo {
  public final String[] names;

  public SuggestedNameInfo(String[] names) {
    this.names = names;
  }

  public abstract void nameChoosen(String name);
}
