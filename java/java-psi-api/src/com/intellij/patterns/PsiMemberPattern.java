// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiMemberPattern<T extends PsiMember, Self extends PsiMemberPattern<T,Self>> extends PsiModifierListOwnerPattern<T,Self> {
  public PsiMemberPattern(final @NotNull InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected PsiMemberPattern(final Class<T> aClass) {
    super(aClass);
  }

  public @NotNull Self inClass(final @NonNls String qname) {
    return inClass(PsiJavaPatterns.psiClass().withQualifiedName(qname));
  }

  public @NotNull Self inClass(final ElementPattern pattern) {
    return with(new PatternConditionPlus<T, PsiClass>("inClass", pattern) {
      @Override
      public boolean processValues(T t, ProcessingContext context, PairProcessor<? super PsiClass, ? super ProcessingContext> processor) {
        return processor.process(t.getContainingClass(), context);
      }
    });
  }

  public static class Capture extends PsiMemberPattern<PsiMember, Capture> {

    protected Capture() {
      super(new InitialPatternCondition<PsiMember>(PsiMember.class) {
        @Override
        public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
          return o instanceof PsiMember;
        }
      });
    }
  }
}
