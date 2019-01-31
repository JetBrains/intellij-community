/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiUtil;

/**
 * @author mike
 */
class SeeDocTagInfo implements JavadocTagInfo {
  private static final String LINKPLAIN_TAG = "linkplain";

  private final String myName;
  private final boolean myInline;

  SeeDocTagInfo(String name, boolean isInline) {
    myName = name;
    myInline = isInline;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isInline() {
    return myInline;
  }


  @Override
  public boolean isValidInContext(PsiElement element) {
    if (myInline && myName.equals(LINKPLAIN_TAG) && element != null) {
      return PsiUtil.getLanguageLevel(element).compareTo(LanguageLevel.JDK_1_4) >= 0;
    }

    return true;
  }

  @Override
  public String checkTagValue(PsiDocTagValue value) {
    return null;
  }

  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }
}