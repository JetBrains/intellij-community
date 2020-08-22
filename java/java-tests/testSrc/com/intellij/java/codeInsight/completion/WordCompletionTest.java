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
import com.intellij.lang.java.JavaLanguage;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class WordCompletionTest extends NormalCompletionTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/word/";
  }

  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for txt, properties and xml")
  public void testKeyWordCompletion() {
    configureByFile("1.txt");
    checkResultByFile("1_after.txt");
    
    configureByFile("1.properties");
    checkResultByFile("1_after.properties");

    configureByFile("1.xml");
    checkResultByFile("1_after.xml");

    configureByFile("2.xml");
    configureByFile("2_after.xml");
  }

  public void testNoWordCompletionForNonSoftReference() {
    final PsiReferenceProvider softProvider = new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new PsiReference[]{new PsiReferenceBase<PsiElement>(element, true) {
          @Override
          public PsiElement resolve() {
            return null;
          }

          @Override
          public Object @NotNull [] getVariants() {
            return new Object[]{"MySoftVariant"};
          }
        }};
      }
    };
    final PsiReferenceProvider hardProvider = new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
        return new PsiReference[]{new PsiReferenceBase<PsiElement>(element, false) {
          @Override
          public PsiElement resolve() {
            return null;
          }

          @Override
          public Object @NotNull [] getVariants() {
            return new Object[]{"MyHardVariant"};
          }
        }};
      }
    };
    PsiReferenceRegistrarImpl registrar =
      (PsiReferenceRegistrarImpl)ReferenceProvidersRegistry.getInstance().getRegistrar(JavaLanguage.INSTANCE);
    try {
      registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), softProvider);
      registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class), hardProvider);

      configureByFile("3.java");
      checkResultByFile("3_after.java");
    }
    finally {
      registrar.unregisterReferenceProvider(PsiLiteralExpression.class, softProvider);
      registrar.unregisterReferenceProvider(PsiLiteralExpression.class, hardProvider);
    }
  }

  public void testInJavaLiterals() { doTest(); }

  public void testComments() {
    configureByFile("4.java");
    checkResultByFile("4_after.java");
  }

  public void testSpaceInComment() { doAntiTest(); }

  public void testTextInComment() { doTest(); }

  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for txt, properties and xml")
  public void testDollarsInPrefix() {
    configureByFile(getTestName(false) + ".txt");
    checkResultByFile(getTestName(false) + "_after.txt");
  }

  public void testCompleteStringLiteralCopy() {
    configureByTestName();
    selectItem(myItems[1]);
    checkResult();
  }

}
