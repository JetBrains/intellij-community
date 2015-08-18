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

import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PsiJavaPatterns.psiLiteral;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.StandardPatterns.string;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;

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

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PATTERN, new JavaReflectionReferenceProvider());
  }
}
