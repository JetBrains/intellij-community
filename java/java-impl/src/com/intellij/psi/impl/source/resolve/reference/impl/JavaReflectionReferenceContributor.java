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

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiLiteral;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaLangInvokeHandleReference.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaReflectionReferenceContributor extends PsiReferenceContributor {
  public static final PsiJavaElementPattern.Capture<PsiLiteral> PATTERN =
    psiLiteral().methodCallParameter(psiMethod().withName(string().oneOf("getDeclaredField",
                                                                         "getField",
                                                                         "getMethod",
                                                                         "getDeclaredMethod"))
                                                     .definedInClass(JAVA_LANG_CLASS));

  public static final PsiJavaElementPattern.Capture<PsiLiteral> CLASS_PATTERN =
    psiLiteral().methodCallParameter(or(
      psiMethod().withName(string().equalTo("forName")).definedInClass(JAVA_LANG_CLASS),
      psiMethod().withName(string().equalTo("loadClass")).definedInClass("java.lang.ClassLoader")));

  private static final ElementPattern<? extends PsiElement> METHOD_HANDLE_PATTERN = psiLiteral()
    .methodCallParameter(1, psiMethod()
      .withName(FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL,
                FIND_GETTER, FIND_SETTER,
                FIND_STATIC_GETTER, FIND_STATIC_SETTER,
                FIND_VAR_HANDLE, FIND_STATIC_VAR_HANDLE)
      .definedInClass(JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP));

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PATTERN, new JavaReflectionReferenceProvider() {
      @Nullable
      @Override
      protected PsiReference[] getReferencesByMethod(@NotNull PsiLiteralExpression literalArgument,
                                                     @NotNull PsiReferenceExpression methodReference,
                                                     @NotNull ProcessingContext context) {

        PsiExpression qualifier = methodReference.getQualifierExpression();
        return qualifier != null ? new PsiReference[]{new JavaLangClassMemberReference(literalArgument, qualifier)} : null;
      }
    });

    registrar.registerReferenceProvider(CLASS_PATTERN, new JavaReflectionReferenceProvider() {
      @Nullable
      @Override
      protected PsiReference[] getReferencesByMethod(@NotNull PsiLiteralExpression literalArgument,
                                                     @NotNull PsiReferenceExpression methodReference,
                                                     @NotNull ProcessingContext context) {

        String referenceName = methodReference.getReferenceName();
        if ("forName".equals(referenceName) || "loadClass".equals(referenceName)) {
          return new JavaClassReferenceProvider().getReferencesByElement(literalArgument, context);
        }
        return null;
      }
    });

    registrar.registerReferenceProvider(METHOD_HANDLE_PATTERN, new JavaLangInvokeHandleReferenceProvider());
  }
}
