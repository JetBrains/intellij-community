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
import com.intellij.testFramework.PsiTestCase;
import junit.framework.Assert;

/**
 *  @author dsl
 */
public class FindParameterTest extends PsiTestCase {
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
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    final PsiMethod methodFromText = elementFactory.createMethodFromText(text, null);
    final PsiParameter[] parameters = methodFromText.getParameterList().getParameters();
    final PsiReference[] references =
      ReferencesSearch.search(parameters[0], new LocalSearchScope(methodFromText), false).toArray(PsiReference.EMPTY_ARRAY);
    Assert.assertEquals(references.length, 2);
  }
}
