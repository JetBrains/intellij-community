/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiLiteral;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaReflectionReferenceContributor extends PsiReferenceContributor {
  public static final PsiJavaElementPattern.Capture<PsiLiteral> PATTERN =
    psiLiteral().methodCallParameter(psiMethod().withName(GET_FIELD, GET_DECLARED_FIELD, GET_METHOD, GET_DECLARED_METHOD)
                                       .definedInClass(JAVA_LANG_CLASS));

  public static final PsiJavaElementPattern.Capture<PsiLiteral> CLASS_PATTERN =
    psiLiteral().methodCallParameter(0, or(
      psiMethod().withName(FOR_NAME).definedInClass(JAVA_LANG_CLASS),
      psiMethod().withName(LOAD_CLASS).definedInClass(JAVA_LANG_CLASS_LOADER),
      psiMethod().withName(FIND_CLASS).definedInClass(JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP)));

  private static final ElementPattern<? extends PsiElement> METHOD_HANDLE_PATTERN = psiLiteral()
    .methodCallParameter(1, psiMethod()
      .withName(HANDLE_FACTORY_METHOD_NAMES)
      .definedInClass(JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP));

  public static final PsiJavaElementPattern.Capture<PsiLiteral> ATOMIC_NUMERIC_PATTERN =
    psiLiteral().methodCallParameter(1, psiMethod().withName(NEW_UPDATER)
      .definedInClass(PsiJavaPatterns.psiClass().withQualifiedName(string().oneOf(ATOMIC_LONG_FIELD_UPDATER,
                                                                                  ATOMIC_INTEGER_FIELD_UPDATER))));

  public static final PsiJavaElementPattern.Capture<PsiLiteral> ATOMIC_REFERENCE_PATTERN =
    psiLiteral().methodCallParameter(2, psiMethod().withName(NEW_UPDATER)
      .definedInClass(PsiJavaPatterns.psiClass().withQualifiedName(ATOMIC_REFERENCE_FIELD_UPDATER)));

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PATTERN, new JavaReflectionReferenceProvider() {
      @Nullable
      @Override
      protected PsiReference[] getReferencesByMethod(@NotNull PsiLiteralExpression literalArgument,
                                                     @NotNull PsiReferenceExpression methodReference,
                                                     @NotNull ProcessingContext context) {

        final PsiExpression qualifier = methodReference.getQualifierExpression();
        return qualifier != null ? new PsiReference[]{new JavaLangClassMemberReference(literalArgument, qualifier)} : null;
      }
    });

    registrar.registerReferenceProvider(CLASS_PATTERN, new ReflectionClassNameReferenceProvider());

    registrar.registerReferenceProvider(METHOD_HANDLE_PATTERN, new JavaLangInvokeHandleReference.JavaLangInvokeHandleReferenceProvider());

    JavaReflectionReferenceProvider atomicProvider = new JavaReflectionReferenceProvider() {
      @Override
      protected PsiReference[] getReferencesByMethod(@NotNull PsiLiteralExpression literalArgument,
                                                     @NotNull PsiReferenceExpression methodReference,
                                                     @NotNull ProcessingContext context) {
        PsiElement parent = literalArgument.getParent();
        if (parent instanceof PsiExpressionList) {
          PsiExpression[] arguments = ((PsiExpressionList)parent).getExpressions();
          return new PsiReference[]{new JavaLangClassMemberReference(literalArgument, arguments[0])};
        }
        return PsiReference.EMPTY_ARRAY;
      }
    };
    registrar.registerReferenceProvider(ATOMIC_NUMERIC_PATTERN, atomicProvider);
    registrar.registerReferenceProvider(ATOMIC_REFERENCE_PATTERN, atomicProvider);
  }

  private static class ReflectionClassNameReferenceProvider extends JavaClassReferenceProvider {

    ReflectionClassNameReferenceProvider() {
      setOption(JavaClassReferenceProvider.ALLOW_DOLLAR_NAMES, Boolean.TRUE);
      setOption(JavaClassReferenceProvider.JVM_FORMAT, Boolean.TRUE);
    }

    @NotNull
    @Override
    public PsiReference[] getReferencesByString(String str, @NotNull PsiElement position, int offsetInPosition) {
      if (StringUtil.isEmpty(str)) {
        return PsiReference.EMPTY_ARRAY;
      }
      return new JavaClassReferenceSet(str, position, offsetInPosition, true, this) {
        @Override
        public boolean isAllowDollarInNames() {
          return true;
        }
      }.getAllReferences();
    }
  }
}
