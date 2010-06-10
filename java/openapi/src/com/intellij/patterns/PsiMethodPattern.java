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

package com.intellij.patterns;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiMethodPattern extends PsiMemberPattern<PsiMethod,PsiMethodPattern> {
  public PsiMethodPattern() {
    super(PsiMethod.class);
  }

  public PsiMethodPattern withParameterCount(@NonNls final int paramCount) {
    return with(new PatternCondition<PsiMethod>("withParameterCount") {
      public boolean accepts(@NotNull final PsiMethod method, final ProcessingContext context) {
        return method.getParameterList().getParametersCount() == paramCount;
      }
    });
  }

  /**
   * Selects the corrected method by argument types
   * @param types the array of FQN of the parameter types or wildcards.
   * The special values are:<bl><li>"?" - means any type</li><li>".." - instructs pattern to accept the rest of the arguments</li></bl>
   * @return
   */
  public PsiMethodPattern withParameters(@NonNls final String... types) {
    return with(new PatternCondition<PsiMethod>("withParameters") {
      public boolean accepts(@NotNull final PsiMethod psiMethod, final ProcessingContext context) {
        final PsiParameterList parameterList = psiMethod.getParameterList();
        int dotsIndex = -1;
        while (++dotsIndex <types.length) {
          if (Comparing.equal("..", types[dotsIndex])) break;
        }

        if (dotsIndex == types.length && parameterList.getParametersCount() != dotsIndex
          || dotsIndex < types.length && parameterList.getParametersCount() < dotsIndex) {
          return false;
        }
        if (dotsIndex > 0) {
          final PsiParameter[] psiParameters = parameterList.getParameters();
          for (int i = 0; i < dotsIndex; i++) {
            if (!Comparing.equal("?", types[i]) && !typeEquivalent(psiParameters[i].getType(), types[i])) {
              return false;
            }
          }
        }
        return true;
      }

      private boolean typeEquivalent(PsiType type, String expectedText) {
        final PsiType erasure = TypeConversionUtil.erasure(type);
        final String text;
        if (erasure instanceof PsiEllipsisType && expectedText.endsWith("[]")) {
          text = ((PsiEllipsisType)erasure).getComponentType().getCanonicalText() + "[]";
        }
        else if (erasure instanceof PsiArrayType && expectedText.endsWith("...")) {
          text = ((PsiArrayType)erasure).getComponentType().getCanonicalText() +"...";
        }
        else {
          text = erasure.getCanonicalText();
        }
        return expectedText.equals(text);
      }
    });
  }

  public PsiMethodPattern definedInClass(final @NonNls String qname) {
    return definedInClass(PsiJavaPatterns.psiClass().withQualifiedName(qname));
  }

  public PsiMethodPattern definedInClass(final ElementPattern<? extends PsiClass> pattern) {
    return with(new PatternConditionPlus<PsiMethod, PsiClass>("definedInClass", pattern) {

      @Override
      public boolean processValues(PsiMethod t, final ProcessingContext context, final PairProcessor<PsiClass, ProcessingContext> processor) {
        if (!processor.process(t.getContainingClass(), context)) return false;
        final Ref<Boolean> result = Ref.create(Boolean.TRUE);
        SuperMethodsSearch.search(t, null, true, false).forEach(new Processor<MethodSignatureBackedByPsiMethod>() {
          public boolean process(final MethodSignatureBackedByPsiMethod signature) {
            if (!processor.process(signature.getMethod().getContainingClass(), context)) {
              result.set(Boolean.FALSE);
              return false;
            }
            return true;
          }
        });
        return result.get();
      }
    });
  }

  public PsiMethodPattern constructor(final boolean isConstructor) {
    return with(new PatternCondition<PsiMethod>("constructor") {
      public boolean accepts(@NotNull final PsiMethod method, final ProcessingContext context) {
        return method.isConstructor() == isConstructor;
      }
    });
  }


  public PsiMethodPattern withThrowsList(final ElementPattern<?> pattern) {
    return with(new PatternCondition<PsiMethod>("withThrowsList") {
      public boolean accepts(@NotNull final PsiMethod method, final ProcessingContext context) {
        return pattern.accepts(method.getThrowsList());
      }
    });
  }
}