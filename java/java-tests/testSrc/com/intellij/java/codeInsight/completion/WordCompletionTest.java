/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.lang.StdLanguages;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class WordCompletionTest extends CompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/word/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testKeyWordCompletion() throws Exception {
    configureByFile(BASE_PATH + "1.txt");
    checkResultByFile(BASE_PATH + "1_after.txt");
    
    configureByFile(BASE_PATH + "1.properties");
    checkResultByFile(BASE_PATH + "1_after.properties");

    configureByFile(BASE_PATH + "1.xml");
    checkResultByFile(BASE_PATH + "1_after.xml");

    configureByFile(BASE_PATH + "2.xml");
    configureByFile(BASE_PATH + "2_after.xml");
  }

  public void testNoWordCompletionForNonSoftReference() throws Throwable {
    final PsiReferenceProvider softProvider = new PsiReferenceProvider() {
      @Override
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new PsiReference[]{new PsiReferenceBase<PsiElement>(element, true) {
          @Override
          public PsiElement resolve() {
            return null;
          }

          @Override
          @NotNull
          public Object[] getVariants() {
            return new Object[]{"MySoftVariant"};
          }
        }};
      }
    };
    final PsiReferenceProvider hardProvider = new PsiReferenceProvider() {
      @Override
      @NotNull
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new PsiReference[]{new PsiReferenceBase<PsiElement>(element, false) {
          @Override
          public PsiElement resolve() {
            return null;
          }

          @Override
          @NotNull
          public Object[] getVariants() {
            return new Object[]{"MyHardVariant"};
          }
        }};
      }
    };
    PsiReferenceRegistrarImpl registrar =
      (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(StdLanguages.JAVA);
    try {
      registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), softProvider);
      registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), hardProvider);

      configureByFile(BASE_PATH + "3.java");
      checkResultByFile(BASE_PATH + "3_after.java");
    }
    finally {
      registrar.unregisterReferenceProvider(PsiLiteralExpression.class, softProvider);
      registrar.unregisterReferenceProvider(PsiLiteralExpression.class, hardProvider);
    }
  }

  public void testInJavaLiterals() throws Exception {
    configureByFile(BASE_PATH + "InJavaLiterals.java");
    checkResultByFile(BASE_PATH + "InJavaLiterals_after.java");
  }

  public void testComments() throws Throwable {
    configureByFile(BASE_PATH + "4.java");
    checkResultByFile(BASE_PATH + "4_after.java");
  }

  public void testSpaceInComment() throws Throwable {
    configureByFile(BASE_PATH + "SpaceInComment.java");
    checkResultByFile(BASE_PATH + "SpaceInComment.java");
  }

  public void testTextInComment() throws Throwable {
    configureByFile(BASE_PATH + "TextInComment.java");
    checkResultByFile(BASE_PATH + "TextInComment_after.java");
  }

  public void testDollarsInPrefix() throws Throwable {
    configureByFile(BASE_PATH + getTestName(false) + ".txt");
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.txt");
  }

  public void testCompleteStringLiteralCopy() throws Throwable {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

}
