/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    //noinspection HardCodedStringLiteral
    return "java " + myId;
  }

  public String getPresentableText() {
    return myPresentableText;
  }
}
