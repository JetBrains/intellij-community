/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.codeInsight.completion.scope.CompletionProcessor;
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

/**
 * @author peter
*/
public class CanonicalPsiTypeConverterImpl extends CanonicalPsiTypeConverter implements CustomReferenceConverter<PsiType> {

  private static final JavaClassReferenceProvider REFERENCE_PROVIDER = new JavaClassReferenceProvider();
  @NonNls private static final String[] PRIMITIVES = new String[]{"boolean", "byte",
    "char", "double", "float", "int", "long", "short"};
  @NonNls private static final String ARRAY_PREFIX = "[L";

  public PsiType fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    try {
      return JavaPsiFacade.getInstance(context.getFile().getProject()).getElementFactory().createTypeFromText(s, null);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  public String toString(final PsiType t, final ConvertContext context) {
    return t == null? null:t.getCanonicalText();
  }

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue<PsiType> genericDomValue, final PsiElement element, ConvertContext context) {
    final String str = genericDomValue.getStringValue();
    if (str != null) {
      final ElementManipulator<PsiElement> manipulator =
        PsiManager.getInstance(element.getProject()).getElementManipulatorsRegistry().getManipulator(element);
      assert manipulator != null;
      String trimmed = str.trim();
      int offset = manipulator.getRangeInElement(element).getStartOffset() + str.indexOf(trimmed);
      if (trimmed.startsWith(ARRAY_PREFIX)) {
        offset += ARRAY_PREFIX.length();
        if (trimmed.endsWith(";")) {
          trimmed = trimmed.substring(ARRAY_PREFIX.length(), trimmed.length() - 1);
        } else {
          trimmed = trimmed.substring(ARRAY_PREFIX.length());          
        }
      }
      return new JavaClassReferenceSet(trimmed, element, offset, false, REFERENCE_PROVIDER) {
        protected JavaClassReference createReference(final int referenceIndex, final String subreferenceText, final TextRange textRange,
                                                     final boolean staticImport) {
          return new JavaClassReference(this, textRange, referenceIndex, subreferenceText, staticImport) {
            public boolean isSoft() {
              return true;
            }

            @NotNull
            public JavaResolveResult advancedResolve(final boolean incompleteCode) {
              PsiType type = genericDomValue.getValue();
              if (type != null) {
                type = type.getDeepComponentType();
              }
              if (type instanceof PsiPrimitiveType) {
                return new CandidateInfo(element, PsiSubstitutor.EMPTY, false, false, element);
              }

              return super.advancedResolve(incompleteCode);
            }

            public void processVariants(final PsiScopeProcessor processor) {
              if (processor instanceof CompletionProcessor) {
                ((CompletionProcessor)processor).setCompletionElements(getVariants());
              } else {
                super.processVariants(processor);
              }
            }

            public Object[] getVariants() {
              final Object[] variants = super.getVariants();
              if (myIndex == 0) {
                return ArrayUtil.mergeArrays(variants, PRIMITIVES, Object.class);
              }
              return variants;
            }
          };
        }
      }.getAllReferences();
    }
    return PsiReference.EMPTY_ARRAY;
  }
}
