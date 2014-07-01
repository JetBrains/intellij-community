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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.daemon.impl.analysis.encoding.EncodingReference;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.manipulators.StringLiteralManipulator;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

import static com.intellij.patterns.PsiJavaPatterns.literalExpression;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.StandardPatterns.string;

/**
 * @author peter
 */
public class JavaCharsetReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      literalExpression().methodCallParameter(
        0, psiMethod().withName(string().oneOf("forName", "isSupported")).inClass(Charset.class.getName())),
      new PsiReferenceProvider() {
        @NotNull
        @Override
        public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
          PsiLiteralExpression literal = (PsiLiteralExpression)element;
          Object value = literal.getValue();
          if (value instanceof String) {
            return new PsiReference[]{new EncodingReference(element, (String)value, StringLiteralManipulator.getValueRange(literal))};
          }
          return PsiReference.EMPTY_ARRAY;
        }
      });
  }
}
