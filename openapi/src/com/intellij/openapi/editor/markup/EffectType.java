/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

public class EffectType {
  public static final EffectType LINE_UNDERSCORE = new EffectType("LINE_UNDERSCORE");
  public static final EffectType WAVE_UNDERSCORE = new EffectType("WAVE_UNDERSCORE");
  public static final EffectType BOXED = new EffectType("BOXED");
  public static final EffectType STRIKEOUT = new EffectType("STRIKEOUT");

  private final String myDebugName;
  private EffectType(String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
