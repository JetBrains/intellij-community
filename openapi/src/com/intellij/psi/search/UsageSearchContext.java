/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

public class UsageSearchContext {
  public static final short IN_CODE        = 0x1;
  public static final short IN_COMMENTS    = 0x2;
  public static final short IN_STRINGS     = 0x4;
  public static final short IN_ALIEN_LANGUAGES = 0x8;
  public static final short IN_PLAIN_TEXT  = 0x10;
  public static final short ANY            = 0xFF;
}
