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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;

class AuthorDocTagInfo extends SimpleDocTagInfo {
  AuthorDocTagInfo() {
    super("author", LanguageLevel.JDK_1_3, false, PsiClass.class, PsiPackage.class, PsiMethod.class);
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    if (element instanceof PsiMethod && !element.isPhysical()) {
      return false;
    }
    else {
      return super.isValidInContext(element);
    }
  }
}