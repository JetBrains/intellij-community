/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.find.findUsages;

import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.LightIdeaTestCase;

/**
 * @author dsl
 */
public class FindParameterTest extends LightIdeaTestCase {
  public void testMethod() {
    String text =
      "void method(final int i) {" +
      "  Runnable runnable = new Runnable() {" +
      "    public void run() {" +
      "      System.out.println(i);" +
      "    }" +
      "  };" +
      "  System.out.println(i);" +
      "}";
    PsiManager psiManager = PsiManager.getInstance(getProject());
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiManager.getProject());
    PsiMethod methodFromText = elementFactory.createMethodFromText(text, null);
    PsiParameter[] parameters = methodFromText.getParameterList().getParameters();
    PsiReference[] references =
      ReferencesSearch.search(parameters[0], new LocalSearchScope(methodFromText), false).toArray(PsiReference.EMPTY_ARRAY);
    assertEquals(2, references.length);
  }
}
