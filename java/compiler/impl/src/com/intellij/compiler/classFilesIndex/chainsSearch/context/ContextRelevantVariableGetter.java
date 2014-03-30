/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch.context;

import com.intellij.codeInsight.completion.JavaChainLookupElement;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.GetterLookupSubLookupElement;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.SubLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ContextRelevantVariableGetter {
  private final PsiVariable myVariable;
  private final PsiMethod myMethod;

  public ContextRelevantVariableGetter(final PsiVariable variable, final PsiMethod method) {
    myVariable = variable;
    myMethod = method;
  }

  public SubLookupElement createSubLookupElement() {
    return new GetterLookupSubLookupElement(myVariable.getName(), myMethod.getName());
  }

  public LookupElement createLookupElement() {
    return new JavaChainLookupElement(new VariableLookupItem(myVariable), new JavaMethodCallElement(myMethod));
  }
}
