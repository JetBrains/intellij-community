/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.formatting;

import com.intellij.openapi.diagnostic.Logger;

/**
 * The wrap setting for a formatting model block. Indicates the conditions under which a line break
 * is inserted before the block when formatting, if the block extends beyond the
 * right margin.
 *
 * @see com.intellij.formatting.Block#getWrap()
 */

public abstract class Wrap {
  /**
   * Converts a low-priority wrap setting to a regular wrap setting.
   * @see #createChildWrap(Wrap, WrapType, boolean)
   */
  public abstract void ignoreParentWraps();

  private static final Logger LOG = Logger.getInstance("#com.intellij.formatting.Wrap");

  private static WrapFactory myFactory;

  public static WrapType ALWAYS = WrapType.ALWAYS;
  public static WrapType NORMAL = WrapType.NORMAL;
  public static WrapType NONE = WrapType.NONE;
  public static WrapType CHOP_DOWN_IF_LONG = WrapType.CHOP_DOWN_IF_LONG;

  static void setFactory(WrapFactory factory) {
    LOG.assertTrue(myFactory == null);
    if (myFactory == null) {
      myFactory = factory;
    }
  }

  /**
   * Creates a block wrap setting of the specified type.
   *
   * @param type             the type of the wrap setting.
   * @param wrapFirstElement if true, the first element in a sequence of elements of the same type
   *                         is also wrapped.
   * @return the wrap setting instance.
   */
  public static Wrap createWrap(final WrapType type, final boolean wrapFirstElement) {
    return myFactory.createWrap(type, wrapFirstElement);
  }

  /**
   * Creates a low priority wrap setting of the specified type. If the formatter detects that
   * the line should be wrapped at a location of the child wrap, the line is wrapped at the
   * position of its parent wrap instead.
   *
   * @param parentWrap       the parent for this wrap setting.
   * @param wrapType         the type of the wrap setting.
   * @param wrapFirstElement if true, the first element in a sequence of elements of the same type
   *                         is also wrapped.
   * @return the wrap setting instance.
   * @see #ignoreParentWraps() 
   */
  public static Wrap createChildWrap(final Wrap parentWrap, final WrapType wrapType, final boolean wrapFirstElement) {
    return myFactory.createChildWrap(parentWrap, wrapType, wrapFirstElement);    
  }

}
