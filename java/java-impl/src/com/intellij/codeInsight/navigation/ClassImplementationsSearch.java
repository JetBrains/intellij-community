/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.Processor;

import java.util.ArrayList;

public class ClassImplementationsSearch implements QueryExecutor<PsiElement, PsiElement> {
  public boolean execute(final PsiElement sourceElement, final Processor<PsiElement> consumer) {
    if (sourceElement instanceof PsiClass) {
      for (PsiElement implementation : getClassImplementations((PsiClass)sourceElement)) {
        if ( ! consumer.process(implementation) ) {
          return false;
        }
      }
    }
    return true;
  }

  public static PsiClass[] getClassImplementations(final PsiClass psiClass) {
    final ArrayList<PsiClass> list = new ArrayList<PsiClass>();

    ClassInheritorsSearch.search(psiClass, psiClass.getUseScope(), true).forEach(new PsiElementProcessorAdapter<PsiClass>(new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        if (!element.isInterface()) {
          list.add(element);
        }
        return true;
      }
    }));

    return list.toArray(new PsiClass[list.size()]);
  }
}
