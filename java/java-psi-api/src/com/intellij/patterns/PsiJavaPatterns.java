/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class PsiJavaPatterns extends StandardPatterns{

  public static IElementTypePattern elementType() {
    return PlatformPatterns.elementType();
  }

  public static VirtualFilePattern virtualFile() {
    return PlatformPatterns.virtualFile();
  }

  public static PsiJavaElementPattern.Capture<PsiElement> psiElement() {
    return new PsiJavaElementPattern.Capture<>(PsiElement.class);
  }

  public static PsiJavaElementPattern.Capture<PsiElement> psiElement(IElementType type) {
    return psiElement().withElementType(type);
  }

  public static <T extends PsiElement> PsiJavaElementPattern.Capture<T> psiElement(final Class<T> aClass) {
    return new PsiJavaElementPattern.Capture<>(aClass);
  }

  @SafeVarargs
  public static PsiJavaElementPattern.Capture<PsiElement> psiElement(final Class<? extends PsiElement>... classAlternatives) {
    return new PsiJavaElementPattern.Capture<>(new InitialPatternCondition<PsiElement>(PsiElement.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        for (Class<? extends PsiElement> classAlternative : classAlternatives) {
          if (classAlternative.isInstance(o)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public static PsiJavaElementPattern.Capture<PsiLiteralExpression> literalExpression() {
    return literalExpression(null);
  }

  public static PsiJavaElementPattern.Capture<PsiLiteral> psiLiteral() {
    return psiLiteral(null);
  }

  public static PsiJavaElementPattern.Capture<PsiLiteral> psiLiteral(@Nullable final ElementPattern<?> value) {
    return new PsiJavaElementPattern.Capture<>(new InitialPatternConditionPlus<PsiLiteral>(PsiLiteral.class) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof PsiLiteral && (value == null || value.accepts(((PsiLiteral)o).getValue(), context));
      }

      @Override
      public List<ElementPattern<?>> getPatterns() {
        return Collections.singletonList(value);
      }
    });
  }

  public static PsiJavaElementPattern.Capture<PsiNewExpression> psiNewExpression(final String @NotNull ... fqns) {
    return new PsiJavaElementPattern.Capture<>(new InitialPatternCondition<PsiNewExpression>(PsiNewExpression.class) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        if (o instanceof PsiNewExpression) {
          PsiJavaCodeReferenceElement reference = ((PsiNewExpression)o).getClassOrAnonymousClassReference();
          if (reference != null) {
            return ArrayUtil.contains(reference.getQualifiedName(), fqns);
          }
        }
        return false;
      }
    });
  }

  public static PsiJavaElementPattern.Capture<PsiLiteralExpression> literalExpression(@Nullable final ElementPattern<?> value) {
    return new PsiJavaElementPattern.Capture<>(new InitialPatternConditionPlus<PsiLiteralExpression>(PsiLiteralExpression.class) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof PsiLiteralExpression && (value == null || value.accepts(((PsiLiteralExpression)o).getValue(), context));
      }

      @Override
      public List<ElementPattern<?>> getPatterns() {
        return Collections.singletonList(value);
      }
    });
  }

  public static PsiMemberPattern.Capture psiMember() {
    return new PsiMemberPattern.Capture();
  }

  public static PsiMethodPattern psiMethod() {
    return new PsiMethodPattern();
  }

  public static PsiParameterPattern psiParameter() {
    return new PsiParameterPattern();
  }

  public static PsiModifierListOwnerPattern.Capture<PsiModifierListOwner> psiModifierListOwner() {
    return new PsiModifierListOwnerPattern.Capture<>(new InitialPatternCondition<PsiModifierListOwner>(PsiModifierListOwner.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return o instanceof PsiModifierListOwner;
      }
    });
  }
  

  public static PsiFieldPattern psiField() {
    return new PsiFieldPattern();
  }

  public static PsiClassPattern psiClass() {
    return new PsiClassPattern();
  }

  public static PsiAnnotationPattern psiAnnotation() {
    return PsiAnnotationPattern.PSI_ANNOTATION_PATTERN;
  }

  public static PsiNameValuePairPattern psiNameValuePair() {
    return PsiNameValuePairPattern.NAME_VALUE_PAIR_PATTERN;
  }

  public static PsiTypePattern psiType() {
    return new PsiTypePattern();
  }

  public static PsiExpressionPattern.Capture<PsiExpression> psiExpression() {
    return new PsiExpressionPattern.Capture<>(PsiExpression.class);
  }

  public static PsiBinaryExpressionPattern psiBinaryExpression() {
    return new PsiBinaryExpressionPattern();
  }

  public static PsiTypeCastExpressionPattern psiTypeCastExpression() {
    return new PsiTypeCastExpressionPattern();
  }

  public static PsiJavaElementPattern.Capture<PsiReferenceExpression> psiReferenceExpression() {
    return psiElement(PsiReferenceExpression.class);
  }

  public static PsiStatementPattern.Capture<PsiExpressionStatement> psiExpressionStatement() {
    return new PsiStatementPattern.Capture<>(PsiExpressionStatement.class);
  }

  public static PsiStatementPattern.Capture<PsiReturnStatement> psiReturnStatement() {
    return new PsiStatementPattern.Capture<>(PsiReturnStatement.class);
  }
}
