// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CanonicalPsiTypeConverterImpl extends CanonicalPsiTypeConverter implements CustomReferenceConverter<PsiType> {
  @NonNls static final String[] PRIMITIVES = {"boolean", "byte", "char", "double", "float", "int", "long", "short"};
  @NonNls private static final String ARRAY_PREFIX = "[L";

  @Override
  public PsiType fromString(final String s, final ConvertContext context) {
    if (s == null) {
      return null;
    }

    try {
      return JavaPsiFacade.getElementFactory(context.getFile().getProject()).createTypeFromText(s.replace('$', '.'), null);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  public String toString(final PsiType t, final ConvertContext context) {
    return t == null ? null : t.getCanonicalText();
  }

  @Override
  public PsiReference @NotNull [] createReferences(final GenericDomValue<PsiType> genericDomValue, final PsiElement element, ConvertContext context) {
    final String typeText = genericDomValue.getStringValue();
    if (typeText == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    return getReferences(genericDomValue.getValue(), typeText, 0, element);
  }

  public PsiReference[] getReferences(@Nullable PsiType type, String typeText, int startOffsetInText, @NotNull final PsiElement element) {
    String trimmed = typeText.trim();
    int offset = ElementManipulators.getValueTextRange(element).getStartOffset() + startOffsetInText + typeText.indexOf(trimmed);
    if (trimmed.startsWith(ARRAY_PREFIX)) {
      offset += ARRAY_PREFIX.length();
      if (trimmed.endsWith(";")) {
        trimmed = trimmed.substring(ARRAY_PREFIX.length(), trimmed.length() - 1);
      } else {
        trimmed = trimmed.substring(ARRAY_PREFIX.length());
      }
    }

    if (type != null) {
      type = type.getDeepComponentType();
    }
    final boolean isPrimitiveType = type instanceof PsiPrimitiveType;

    return new JavaClassReferenceSet(trimmed, element, offset, false, new JavaClassReferenceProvider()) {
      @Override
      @NotNull
      protected JavaClassReference createReference(int refIndex, @NotNull String subRefText, @NotNull TextRange textRange, boolean staticImport) {
        return new JavaClassReference(this, textRange, refIndex, subRefText, staticImport) {
          @Override
          public boolean isSoft() {
            return true;
          }

          @Override
          @NotNull
          public JavaResolveResult advancedResolve(final boolean incompleteCode) {
            if (isPrimitiveType) {
              return new CandidateInfo(element, PsiSubstitutor.EMPTY, false, false, element);
            }

            return super.advancedResolve(incompleteCode);
          }

          @Override
          public void processVariants(@NotNull final PsiScopeProcessor processor) {
            if (processor instanceof JavaCompletionProcessor) {
              ((JavaCompletionProcessor)processor).setCompletionElements(getVariants());
            } else {
              super.processVariants(processor);
            }
          }

          @Override
          public Object @NotNull [] getVariants() {
            final Object[] variants = super.getVariants();
            if (myIndex == 0) {
              return ArrayUtil.mergeArrays(variants, PRIMITIVES, ArrayUtil.OBJECT_ARRAY_FACTORY);
            }
            return variants;
          }
        };
      }
    }.getAllReferences();
  }
}
