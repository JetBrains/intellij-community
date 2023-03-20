// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.patterns;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PsiMethodPattern extends PsiMemberPattern<PsiMethod,PsiMethodPattern> {
  public PsiMethodPattern() {
    super(PsiMethod.class);
  }

  public PsiMethodPattern withParameterCount(@NonNls final int paramCount) {
    return with(new PatternCondition<PsiMethod>("withParameterCount") {
      @Override
      public boolean accepts(@NotNull final PsiMethod method, final ProcessingContext context) {
        return method.getParameterList().getParametersCount() == paramCount;
      }
    });
  }

  /**
   * Selects the corrected method by argument types
   * @param inputTypes the array of FQN of the parameter types or wildcards.
   * The special values are:<bl><li>"?" - means any type</li><li>".." - instructs pattern to accept the rest of the arguments</li></bl>
   */
  public PsiMethodPattern withParameters(@NonNls final String... inputTypes) {
    final String[] types = inputTypes.length == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY : inputTypes;
    return with(new PatternCondition<PsiMethod>("withParameters") {
      @Override
      public boolean accepts(@NotNull final PsiMethod psiMethod, final ProcessingContext context) {
        final PsiParameterList parameterList = psiMethod.getParameterList();
        int dotsIndex = -1;
        while (++dotsIndex <types.length) {
          if (Objects.equals("..", types[dotsIndex])) break;
        }

        if (dotsIndex == types.length && parameterList.getParametersCount() != dotsIndex
          || dotsIndex < types.length && parameterList.getParametersCount() < dotsIndex) {
          return false;
        }
        if (dotsIndex > 0) {
          final PsiParameter[] psiParameters = parameterList.getParameters();
          for (int i = 0; i < dotsIndex; i++) {
            if (!Objects.equals("?", types[i]) && !typeEquivalent(psiParameters[i].getType(), types[i])) {
              return false;
            }
          }
        }
        return true;
      }

      private boolean typeEquivalent(PsiType type, String expectedText) {
        PsiType erasure = TypeConversionUtil.erasure(type);
        return erasure != null && erasure.equalsToText(expectedText);
      }
    });
  }

  @NotNull
  public PsiMethodPattern definedInClass(@NonNls final String qname) {
    return definedInClass(PsiJavaPatterns.psiClass().withQualifiedName(qname));
  }

  @NotNull
  public PsiMethodPattern definedInClass(final ElementPattern<? extends PsiClass> pattern) {
    return with(new PatternConditionPlus<PsiMethod, PsiClass>("definedInClass", pattern) {

      @Override
      public boolean processValues(PsiMethod t, final ProcessingContext context, final PairProcessor<? super PsiClass, ? super ProcessingContext> processor) {
        if (!processor.process(t.getContainingClass(), context)) return false;
        final Ref<Boolean> result = Ref.create(Boolean.TRUE);
        SuperMethodsSearch.search(t, null, true, false).forEach(signature -> {
          if (!processor.process(signature.getMethod().getContainingClass(), context)) {
            result.set(Boolean.FALSE);
            return false;
          }
          return true;
        });
        return result.get();
      }
    });
  }

  public PsiMethodPattern constructor(final boolean isConstructor) {
    return with(new PatternCondition<PsiMethod>("constructor") {
      @Override
      public boolean accepts(@NotNull final PsiMethod method, final ProcessingContext context) {
        return method.isConstructor() == isConstructor;
      }
    });
  }


  public PsiMethodPattern withThrowsList(final ElementPattern<?> pattern) {
    return with(new PatternCondition<PsiMethod>("withThrowsList") {
      @Override
      public boolean accepts(@NotNull final PsiMethod method, final ProcessingContext context) {
        return pattern.accepts(method.getThrowsList());
      }
    });
  }
}