/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

public interface UsageSearchContext {
  /**
   * Element's usages in its language code are requested
   */
  public static final short IN_CODE        = 0x1;

  /**
   * Usages in comments are requested
   */
  public static final short IN_COMMENTS    = 0x2;

  /**
   * Usages in string literals are requested
   */
  public static final short IN_STRINGS     = 0x4;

  /**
   * Element's usages in other languages are requested,
   * e.g. usages of java class in jsp attribute value
   */
  public static final short IN_ALIEN_LANGUAGES = 0x8;

  /**
   * Plain text occurences are requested
   */
  public static final short IN_PLAIN_TEXT  = 0x10;

  /**
   * Any of above
   */
  public static final short ANY            = 0xFF;
}
