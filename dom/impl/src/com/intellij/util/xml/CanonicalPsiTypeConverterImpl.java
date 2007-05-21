/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import com.intellij.codeInsight.completion.scope.CompletionProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
*/
public class CanonicalPsiTypeConverterImpl extends CanonicalPsiTypeConverter implements CustomReferenceConverter<PsiType> {

  private static final JavaClassReferenceProvider REFERENCE_PROVIDER = new JavaClassReferenceProvider();
  @NonNls private static final String[] PRIMITIVES = new String[]{"boolean", "byte",
    "char", "double", "float", "int", "long", "short"};

  public PsiType fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    try {
      return context.getFile().getManager().getElementFactory().createTypeFromText(s, null);
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
      final String trimmed = str.trim();
      final int offset = manipulator.getRangeInElement(element).getStartOffset() + str.indexOf(trimmed);
      return new JavaClassReferenceSet(trimmed, element, offset, false, REFERENCE_PROVIDER) {
        protected JavaClassReference createReference(final int referenceIndex, final String subreferenceText, final TextRange textRange,
                                                     final boolean staticImport) {
          return new JavaClassReference(this, textRange, referenceIndex, subreferenceText, staticImport) {
            public boolean isSoft() {
              return true;
            }

            @NotNull
            public JavaResolveResult advancedResolve(final boolean incompleteCode) {
              final PsiType type = genericDomValue.getValue();
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
