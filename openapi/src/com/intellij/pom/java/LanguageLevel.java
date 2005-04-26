/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom.java;

/**
 * @author dsl
 */
public enum LanguageLevel {
  JDK_1_3 ("1.3", "1.3 ", false, false),
  JDK_1_4 ("1.4", "1.4 - 'assert' keyword", true, false), // assert keyword
  JDK_1_5 ("5.0", "5.0 - 'enum' keyword, autoboxing, etc.", true, true); // enums etc.
  
  public static final LanguageLevel HIGHEST = JDK_1_5;
  private final String myId;
  private final boolean myHasAssertKeyword;
  private final boolean myHasEnumKeywordAndAutoboxing;
  private final String myPresentableText;


  private LanguageLevel(String id, String presentableText, boolean hasAssertKeyword, boolean hasEnumKeywordAndAutoboxing) {
    myId = id;
    myHasAssertKeyword = hasAssertKeyword;
    myHasEnumKeywordAndAutoboxing = hasEnumKeywordAndAutoboxing;
    myPresentableText = presentableText;
  }

  public int getIndex() {
    return ordinal() + 3;  //Solely for backward compatibility
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
