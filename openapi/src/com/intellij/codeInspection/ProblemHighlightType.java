/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 26, 2003
 * Time: 5:24:04 PM
 * To change this template use Options | File Templates.
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection;

public class ProblemHighlightType {
  public static final ProblemHighlightType GENERIC_ERROR_OR_WARNING = new ProblemHighlightType("GENERIC_ERROR_OR_WARNING");
  public static final ProblemHighlightType LIKE_DEPRECATED = new ProblemHighlightType("LIKE_DEPRECATED");
  public static final ProblemHighlightType LIKE_UNKNOWN_SYMBOL = new ProblemHighlightType("LIKE_UNKNOWN_SYMBOL");
  public static final ProblemHighlightType LIKE_UNUSED_SYMBOL = new ProblemHighlightType("LIKE_UNUSED_SYMBOL");

  private final String myName; // for debug only

  private ProblemHighlightType(String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }
}
