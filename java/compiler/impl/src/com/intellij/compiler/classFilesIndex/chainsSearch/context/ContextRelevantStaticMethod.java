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

import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.StaticMethodSubLookupElement;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.SubLookupElement;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.VariableSubLookupElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class ContextRelevantStaticMethod {
  private final PsiMethod psiMethod;
  @Nullable
  private final TIntObjectHashMap<SubLookupElement> parameters;

  public ContextRelevantStaticMethod(final PsiMethod psiMethod, @Nullable final TIntObjectHashMap<PsiVariable> parameters) {
    this.psiMethod = psiMethod;
    if (parameters == null) {
      this.parameters = null;
    } else {
      this.parameters = new TIntObjectHashMap<>(parameters.size());
      parameters.forEachEntry(new TIntObjectProcedure<PsiVariable>() {
        @SuppressWarnings("ConstantConditions")
        @Override
        public boolean execute(final int pos, final PsiVariable var) {
          ContextRelevantStaticMethod.this.parameters.put(pos, new VariableSubLookupElement(var));
          return true;
        }
      });
    }
  }

  private SubLookupElement cachedLookupElement;

  public SubLookupElement createLookupElement() {
    if (cachedLookupElement == null) {
      cachedLookupElement = new StaticMethodSubLookupElement(psiMethod, parameters);
    }
    return cachedLookupElement;
  }
}
