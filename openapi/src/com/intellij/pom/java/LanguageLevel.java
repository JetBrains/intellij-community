/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom.java;

/**
 * @author dsl
 */
public final class LanguageLevel implements Comparable<LanguageLevel> {
  public static final LanguageLevel JDK_1_3 = new LanguageLevel("1.3", "1.3 ", 3, false, false);
  public static final LanguageLevel JDK_1_4 = new LanguageLevel("1.4", "1.4 - 'assert' keyword", 4, true, false); // assert keyword
  public static final LanguageLevel JDK_1_5 = new LanguageLevel("5.0", "5.0 - 'enum' keyword, autoboxing, etc.", 5, true, true); // enums etc.
  public static final LanguageLevel HIGHEST = JDK_1_5;
  private final String myId;
  private final int myIndex;
  private final boolean myHasAssertKeyword;
  private final boolean myHasEnumKeywordAndAutoboxing;
  private final String myPresentableText;


  private LanguageLevel(String id, String presentableText, int index, boolean hasAssertKeyword, boolean hasEnumKeywordAndAutoboxing) {
    myId = id;
    myIndex = index;
    myHasAssertKeyword = hasAssertKeyword;
    myHasEnumKeywordAndAutoboxing = hasEnumKeywordAndAutoboxing;
    myPresentableText = presentableText;
  }

  public int compareTo(LanguageLevel languageLevel) {
    return myIndex - languageLevel.myIndex;
  }

  public int getIndex() {
    return myIndex;
  }

  public boolean hasAssertKeyword() {
    return myHasAssertKeyword;
  }

  public boolean hasEnumKeywordAndAutoboxing() {
    return myHasEnumKeywordAndAutoboxing;
  }

  public String toString() {
    return "java " + myId;
  }

  public String getPresentableText() {
    return myPresentableText;
  }
}
