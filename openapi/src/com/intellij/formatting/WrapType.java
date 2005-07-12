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

/**
 * Defines possible types of a wrap.
 *
 * @see Wrap#createWrap(WrapType, boolean)
 * @see Wrap#createChildWrap(Wrap, WrapType, boolean)
 */

public enum WrapType {
  /**
   * A line break is always inserted before the start of the element.
   * This corresponds to the "Wrap always" setting in Global Code Style | Wrapping.
   */
  ALWAYS,

  /**
   * A line break is inserted before the start of the element if the right edge
   * of the element goes beyond the specified wrap margin.
   * This corresponds to the "Wrap if long" setting in Global Code Style | Wrapping.
   */
  NORMAL,

  /**
   * A line break is never inserted before the start of the element.
   * This corresponds to the "Do not wrap" setting in Global Code Style | Wrapping.
   */
  NONE,

  /**
   * A line break is inserted before the start of the element if it is a part
   * of list of elements of the same type and at least one of the elements was wrapped.
   * This corresponds to the "Chop down if long" setting in Global Code Style | Wrapping.
   */
  CHOP_DOWN_IF_LONG
}
