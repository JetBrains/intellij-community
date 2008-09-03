/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.psi.PsiBundle;

/**
 * @author dsl
 */
public enum LanguageLevel {
  JDK_1_3 ("1.3 ", false, false),
  JDK_1_4 (PsiBundle.message("jdk.1.4.language.level.description"), true, false), // assert keyword
  JDK_1_5 (PsiBundle.message("jdk.1.5.language.level.description"), true, true), // enums etc.
  JDK_1_6 (PsiBundle.message("jdk.1.6.language.level.description"), true, true); // changed rules for @Override

  public static final LanguageLevel HIGHEST = JDK_1_6;
  private final boolean myHasAssertKeyword;
  private final boolean myHasEnumKeywordAndAutoboxing;
  private final String myPresentableText;


  private LanguageLevel(String presentableText, boolean hasAssertKeyword, boolean hasEnumKeywordAndAutoboxing) {
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

  public String getPresentableText() {
    return myPresentableText;
  }
}
