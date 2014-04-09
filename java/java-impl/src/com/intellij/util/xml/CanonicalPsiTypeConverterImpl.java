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

/**
 * @author peter
 */
public class CanonicalPsiTypeConverterImpl extends CanonicalPsiTypeConverter implements CustomReferenceConverter<PsiType> {
  @NonNls static final String[] PRIMITIVES = {"boolean", "byte", "char", "double", "float", "int", "long", "short"};
  @NonNls private static final String ARRAY_PREFIX = "[L";
  private static final JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  @Override
  public PsiType fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    try {
      return JavaPsiFacade.getInstance(context.getFile().getProject()).getElementFactory().createTypeFromText(s.replace('$', '.'), null);
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
  @NotNull
  public PsiReference[] createReferences(final GenericDomValue<PsiType> genericDomValue, final PsiElement element, ConvertContext context) {
    final String typeText = genericDomValue.getStringValue();
    if (typeText == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    return getReferences(genericDomValue.getValue(), typeText, 0, element);
  }

  public PsiReference[] getReferences(@Nullable PsiType type, String typeText, int startOffsetInText, @NotNull final PsiElement element) {
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    assert manipulator != null;
    String trimmed = typeText.trim();
    int offset = manipulator.getRangeInElement(element).getStartOffset() + startOffsetInText + typeText.indexOf(trimmed);
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

    return new JavaClassReferenceSet(trimmed, element, offset, false, CLASS_REFERENCE_PROVIDER) {
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
          @NotNull
          public Object[] getVariants() {
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
