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
package com.intellij.lang.annotation;

/**
 * Defines a highlighting severity level for an annotation.
 *
 * @author max
 * @see Annotation
 */

public class HighlightSeverity implements Comparable<HighlightSeverity> {
  private final String myName; // for debug only
  private final int myVal;

  /**
   * The standard severity level for information annotations.
   */
  public static final HighlightSeverity INFORMATION = new HighlightSeverity("INFORMATION", 0);

  /**
   * The standard severity level for warning annotations.
   */
  public static final HighlightSeverity WARNING = new HighlightSeverity("WARNING", 100);

  /**
   * The standard severity level for error annotations.
   */
  public static final HighlightSeverity ERROR = new HighlightSeverity("ERROR", 200);

  /**
   * Creates a new highlighting severity level with the specified name and value.
   *
   * @param name the name of the highlighting level.
   * @param val  the value of the highlighting level. Used for comparing the annotations -
   *             if two annotations with different severity levels cover the same text range, only
   *             the annotation with a higher severity level is displayed.
   */
  public HighlightSeverity(String name, int val) {
    myName = name;
    myVal = val;
  }

  public String toString() {
    return myName;
  }

  public int compareTo(final HighlightSeverity highlightSeverity) {
    return myVal - highlightSeverity.myVal;
  }
}
