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
package com.intellij.util.xml;

import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.openapi.project.Project;
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

  private static final UserDataCache<JavaClassReferenceProvider, Project, Object> REFERENCE_PROVIDER = new UserDataCache<JavaClassReferenceProvider, Project, Object>("CanonicalPsiTypeConverterImpl") {
    @Override
    protected JavaClassReferenceProvider compute(Project project, Object p) {
      return new JavaClassReferenceProvider(project);
    }
  };

  @NonNls private static final String[] PRIMITIVES = new String[]{"boolean", "byte",
    "char", "double", "float", "int", "long", "short"};
  @NonNls private static final String ARRAY_PREFIX = "[L";

  public PsiType fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    try {
      return JavaPsiFacade.getInstance(context.getFile().getProject()).getElementFactory().createTypeFromText(s.replace('$', '.'), null);
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
      final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
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
      return new JavaClassReferenceSet(trimmed, element, offset, false, REFERENCE_PROVIDER.get(genericDomValue.getManager().getProject(), null)) {
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
              if (processor instanceof JavaCompletionProcessor) {
                ((JavaCompletionProcessor)processor).setCompletionElements(getVariants());
              } else {
                super.processVariants(processor);
              }
            }

            @NotNull
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
